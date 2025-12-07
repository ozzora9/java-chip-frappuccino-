package com.example.studyplanner.controller;

import com.example.studyplanner.PopupHelper;
import com.example.studyplanner.manager.FlowerManager;
import com.example.studyplanner.manager.UserManager;
import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.Flower;
import com.example.studyplanner.model.User;
import com.example.studyplanner.model.UserSession;
import com.example.studyplanner.service.DatabaseService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.util.StringConverter;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class PlannerController implements Initializable {

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final DatabaseService dataService = new DatabaseService();
    private String userId;
    // --- FXML 요소 ---
    @FXML
    private Label ddayLabel;
    @FXML
    private DatePicker currentDatePicker;
    @FXML
    private TextField quoteInput;
    @FXML
    private TextField subjectInput;
    @FXML
    private TextField taskInput;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private ColorPicker manualColor;
    @FXML
    private TableView<Row> table;
    @FXML
    private TableColumn<Row, String> colSubject;
    @FXML
    private TableColumn<Row, String> colContent;
    @FXML
    private TableColumn<Row, Boolean> colDone;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private VBox todayList;
    @FXML
    private TextField todayInput;
    @FXML
    private TextField targetHour;
    @FXML
    private TextField todaysum;
    @FXML
    private TextArea memoArea;
    @FXML
    private GridPane timeTableGrid;
    @FXML
    private TextField weatherInput;
    @FXML
    private Label totalTimerLabel;
    private int currentStage = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.userId = UserSession.getInstance().getUserId();
        if (this.userId == null) {
            this.userId = "test_user";
            System.out.println("⚠ 경고: 로그인 없이 접근. 테스트 유저 사용.");
        }

        // 1. 날짜 선택기 초기화
        if (currentDatePicker != null) {
            currentDatePicker.setValue(LocalDate.now());
            String pattern = "yyyy . MM . dd . E";
            currentDatePicker.setConverter(new StringConverter<LocalDate>() {
                final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(pattern);

                @Override
                public String toString(LocalDate date) {
                    return (date != null) ? dateFormatter.format(date) : "";
                }

                @Override
                public LocalDate fromString(String string) {
                    return (string != null && !string.isEmpty()) ? LocalDate.parse(string, dateFormatter) : null;
                }
            });
        }
        if (ddayLabel != null) ddayLabel.setText("D-Day");

        setupTable(); // 테이블 설정 (우클릭 메뉴 포함)

        if (colorPicker != null) setupColorPicker(colorPicker);
        if (manualColor != null) setupColorPicker(manualColor);

        drawGridBackground();
        loadDataFromDB();

        rows.addListener((javafx.collections.ListChangeListener<? super Row>) c -> updateProgress());
        updateProgress();
    }

    @FXML
    void handleDateChanged(ActionEvent event) {
        loadDataFromDB();
    }

    // -------------------------------------------------------------
    // ★ DB 로드
    // -------------------------------------------------------------
    private void loadDataFromDB() {
        if (currentDatePicker == null) return;
        LocalDate selectedDate = currentDatePicker.getValue();
        DailyRecord record = dataService.loadDailyRecord(userId, selectedDate);

        // 1. 시간 표시
        long goalSeconds = record.getDailyGoalSeconds();
        if (targetHour != null) targetHour.setText(formatTime(goalSeconds));

        long totalSeconds = 0;
        for (DailyRecord.SubjectRecord sr : record.getSubjects().values()) {
            totalSeconds += sr.getStudiedSeconds();
        }

        long performedSeconds = (goalSeconds > 0) ? Math.min(totalSeconds, goalSeconds) : 0;

        if (totalTimerLabel != null) totalTimerLabel.setText(formatTime(totalSeconds));
        if (todaysum != null) todaysum.setText(formatTime(performedSeconds));

        // 2. 테이블 복구
        rows.clear();
        for (Map.Entry<String, DailyRecord.SubjectRecord> entry : record.getSubjects().entrySet()) {
            String name = entry.getKey();
            var info = entry.getValue();
            String color = (info.getColorHex() != null) ? info.getColorHex() : "#ffcccc";

            Row row = new Row(name, info.getTaskContent(), info.isDone(), color);
            // 체크박스 변경 시 저장
            row.doneProperty().addListener((o, oldV, newV) -> {
                updateProgress();
                saveAllData();
            });
            rows.add(row);
        }

        // 3. 형광펜 복구
        drawGridBackground();
        for (com.example.studyplanner.model.StudySession session : record.getStudySessions()) {
            try {
                LocalTime start = LocalTime.parse(session.getStartTime());
                int durationMin = (int) (session.getDurationSeconds() / 60);
                int startRow = start.getHour() - 6;
                if (startRow >= 0 && durationMin > 0) {
                    int rowSpan = Math.max(1, durationMin / 60);
                    addManualSchedule(session.getSubjectName(), startRow, rowSpan, "pink");
                }
            } catch (Exception ignored) {
            }
        }
        updateProgress();
    }

    // -------------------------------------------------------------
    // ★ [핵심 수정] 저장 로직 (삭제된 항목이 DB에서도 사라지게 처리)
    // -------------------------------------------------------------
    // [수정됨] 현재 테이블 상태를 DB에 완벽하게 동기화 (저장)
    private void saveAllData() {
        if (currentDatePicker == null) return;
        LocalDate selectedDate = currentDatePicker.getValue();

        // 1. 기존 DB 데이터를 불러옴 (이유: 이미 공부한 시간(studiedSeconds)을 유지하기 위해)
        DailyRecord oldRecord = dataService.loadDailyRecord(userId, selectedDate);

        // 2. 새로운 맵 생성 (현재 테이블에 있는 내용만 담을 그릇)
        Map<String, DailyRecord.SubjectRecord> newSubjects = new HashMap<>();

        // 3. 현재 화면의 테이블(rows)을 기준으로 데이터 다시 포장
        for (Row r : rows) {
            // (A) 옛날 기록에서 '공부 시간'만 찾아옴
            // (과목명이 바뀌면 공부시간은 0부터 시작됨 - 이게 정상)
            DailyRecord.SubjectRecord oldInfo = oldRecord.getSubjects().get(r.getSubject());
            long savedTime = (oldInfo != null) ? oldInfo.getStudiedSeconds() : 0;

            // (B) 새 정보 생성 (화면에 있는 색상, 내용, 완료여부 + 아까 찾은 공부시간)
            DailyRecord.SubjectRecord newInfo = new DailyRecord.SubjectRecord(savedTime,       // 공부 시간 유지
                    r.getColor(),    // 현재 색상
                    r.getContent(),  // 현재 내용
                    r.isDone()       // 현재 완료 여부
            );

            // (C) 새 맵에 추가
            newSubjects.put(r.getSubject(), newInfo);
        }

        // 4. 레코드 교체 (이제 테이블에 없는 과목은 DB에서도 사라짐 = 삭제 구현됨)
        oldRecord.setSubjects(newSubjects);

        // 5. 최종 저장
        dataService.saveDailyRecord(userId, selectedDate, oldRecord);

        System.out.println("저장 완료: " + newSubjects.size() + "개 과목");
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0h 0m";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        return String.format("%dh %dm", h, m);
    }

    // -------------------------------------------------------------
    // ★ [수정됨] 테이블 설정 (우클릭 메뉴 적용)
    // -------------------------------------------------------------
    private void setupTable() {
        table.setItems(rows);
        table.setEditable(true);

        // 완료 체크박스
        colDone.setEditable(true);
        colDone.setCellValueFactory(cellData -> cellData.getValue().doneProperty());
        colDone.setCellFactory(CheckBoxTableCell.forTableColumn(colDone));
        colDone.setStyle("-fx-alignment: CENTER;");

        // 과목 컬럼 (배경색만 표시, 더블클릭 편집 제거)
        colSubject.setCellValueFactory(new PropertyValueFactory<>("subject"));
        colSubject.setCellFactory(column -> new TableCell<Row, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    Row row = getTableView().getItems().get(getIndex());
                    if (row.getColor() != null) {
                        setStyle("-fx-background-color:" + row.getColor() + "; -fx-text-fill:white;");
                    }
                }
            }
        });

        // 내용 컬럼 (더블클릭 편집 제거)
        colContent.setCellValueFactory(new PropertyValueFactory<>("content"));
        colContent.setCellFactory(column -> new TableCell<Row, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });

        // ★ [핵심] 우클릭 메뉴 (수정 / 삭제)
        table.setRowFactory(tv -> {
            TableRow<Row> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();

            MenuItem editItem = new MenuItem("수정하기");
            MenuItem deleteItem = new MenuItem("삭제하기");

            // 수정 기능
            editItem.setOnAction(event -> {
                Row item = row.getItem();
                openEditDialog(item);
            });

            // 삭제 기능
            deleteItem.setOnAction(event -> {
                rows.remove(row.getItem());
                updateProgress();
                saveAllData(); // 삭제 즉시 저장
            });

            menu.getItems().addAll(editItem, deleteItem);

            // 빈 행이 아닐 때만 메뉴 표시
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    // [추가됨] 테이블 행 수정 팝업
    private void openEditDialog(Row row) {
        Dialog<Pair<String, String>> dialog = new Dialog<>();

        URL cssUrl = getClass().getResource("/com/example/studyplanner/planner.css");
        if (cssUrl != null) dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());

        dialog.setTitle("계획 수정");
        dialog.setHeaderText("과목과 내용을 수정하세요");

        ButtonType okButtonType = new ButtonType("수정", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField subjectField = new TextField(row.getSubject());
        TextField contentField = new TextField(row.getContent());

        grid.add(new Label("과목:"), 0, 0);
        grid.add(subjectField, 1, 0);
        grid.add(new Label("내용:"), 0, 1);
        grid.add(contentField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return new Pair<>(subjectField.getText(), contentField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        result.ifPresent(pair -> {
            row.setSubject(pair.getKey());
            row.setContent(pair.getValue());
            saveAllData(); // 수정 즉시 저장
            table.refresh();
        });
    }

    // -------------------------------------------------------------
    // 유틸 메소드
    // -------------------------------------------------------------
    private void setupColorPicker(ColorPicker picker) {
        picker.getCustomColors().clear();
        picker.getCustomColors().addAll(Color.web("#ffcccc"), Color.web("#cce5ff"), Color.web("#fff5cc"), Color.web("#d4edda"), Color.web("#e8daef"));
        picker.setValue(Color.web("#ffcccc"));
    }

    private void drawGridBackground() {
        // (기존과 동일)
        int rowCount = timeTableGrid.getRowConstraints().size();
        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col <= 6; col++) {
                Pane cell = new Pane();
                cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                GridPane.setFillWidth(cell, true);
                GridPane.setFillHeight(cell, true);
                boolean isLastRow = (row == rowCount - 1);
                String cssClass = "grid-cell";
                if (col == 0) cssClass = isLastRow ? "cell-time-last" : "cell-time";
                else if (col == 6) cssClass = isLastRow ? "cell-grid-corner" : "cell-grid-end";
                else cssClass = isLastRow ? "cell-grid-bottom" : "cell-grid-normal";
                cell.getStyleClass().add(cssClass);
                if (col > 0) {
                    cell.setOnMouseClicked(e -> {
                        boolean isPainted = cell.getStyle().contains("-fx-background-color");
                        if (isPainted) cell.setStyle("");
                        else if (manualColor != null) {
                            Color c = manualColor.getValue();
                            String hex = toHexString(c).substring(0, 7);
                            cell.setStyle("-fx-background-color: " + hex + "B3;");
                        }
                    });
                    cell.setStyle("-fx-cursor: hand;");
                }
                timeTableGrid.add(cell, col, row);
                cell.toBack();
            }
        }
    }

    private void addManualSchedule(String title, int startRow, int duration, String colorName) {
        Pane planBlock = new Pane();
        planBlock.getStyleClass().add("plan-block");
        planBlock.getStyleClass().add("color-" + colorName);
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: #555; -fx-padding: 4;");
        planBlock.getChildren().add(label);
        GridPane.setMargin(planBlock, new Insets(2, 2, 2, 2));
        timeTableGrid.add(planBlock, 1, startRow, 6, duration);
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255));
    }

    // -------------------------------------------------------------
    // 이벤트 핸들러
    // -------------------------------------------------------------
    @FXML
    private void handleAddRow() {
        String s = subjectInput.getText();
        String t = taskInput.getText();
        if (s == null || s.trim().isEmpty()) return;
        String colorHex = (colorPicker.getValue() != null) ? toHexString(colorPicker.getValue()) : "#ffcccc";
        Row r = new Row(s, t, false, colorHex);
        // 리스너 연결
        r.doneProperty().addListener((o, oldV, newV) -> {
            updateProgress();
            saveAllData();
        });
        rows.add(r);
        saveAllData();
        subjectInput.clear();
        taskInput.clear();
    }

    @FXML
    private void handleAddToday() {
        String t = todayInput.getText();
        if (t == null || t.trim().isEmpty()) return;

        CheckBox cb = new CheckBox(t);
        cb.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #333333; -fx-cursor: hand;");

        // [수정됨] 수행시간 텍스트 변경 안 함 (그냥 체크만 됨)
        // cb.setOnAction(e -> updateTodaySum());  <-- 이거 삭제함

        // ★ [수정됨] To-Do 우클릭 메뉴 (수정/삭제)
        ContextMenu menu = new ContextMenu();
        MenuItem editItem = new MenuItem("수정");
        MenuItem delItem = new MenuItem("삭제");

        // 수정 기능
        editItem.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(cb.getText());

            URL cssUrl = getClass().getResource("/com/example/studyplanner/planner.css");
            if (cssUrl != null) dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());

            dialog.setTitle("To-Do 수정");
            dialog.setHeaderText(null);
            dialog.setContentText("내용:");
            dialog.showAndWait().ifPresent(text -> cb.setText(text));
        });

        // 삭제 기능
        delItem.setOnAction(e -> todayList.getChildren().remove(cb));

        menu.getItems().addAll(editItem, delItem);
        cb.setContextMenu(menu);

        todayList.getChildren().add(cb);
        todayInput.clear();
    }

    @FXML
    void handleSetDDay(ActionEvent event) {
        // (기존 코드 유지)
        Dialog<ButtonType> dialog = new Dialog<>();
        URL cssUrl = getClass().getResource("/com/example/studyplanner/planner.css");
        if (cssUrl != null) dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        dialog.setTitle("D-Day 설정");
        dialog.setHeaderText("시작일과 목표일을 선택하세요");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        DatePicker startPicker = new DatePicker(LocalDate.now());
        DatePicker endPicker = new DatePicker(LocalDate.now().plusDays(30));
        grid.add(new Label("시작일:"), 0, 0);
        grid.add(startPicker, 1, 0);
        grid.add(new Label("목표일:"), 0, 1);
        grid.add(endPicker, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                LocalDate start = startPicker.getValue();
                LocalDate end = endPicker.getValue();
                if (start != null && end != null) {
                    long diff = ChronoUnit.DAYS.between(start, end);
                    if (ddayLabel != null) {
                        if (diff >= 0) ddayLabel.setText("D-" + diff);
                        else ddayLabel.setText("D+" + Math.abs(diff));
                    }
                }
            }
        });
    }

    private void updateProgress() {
        int total = rows.size();

        // 1. 계획이 3개 미만일 때 -> 성장 멈춤
        if (total < 3) {
            progressBar.setProgress(0);
            progressLabel.setText("계획을 3개 이상 입력해주세요 (" + total + "/3)");
            return;
        }

        // 2. 진행률 계산
        long doneCount = rows.stream().filter(Row::isDone).count();
        double rate = (double) doneCount / total;
        int percent = (int) (rate * 100);

        progressBar.setProgress(rate);

        UserManager um = UserManager.getInstance();
        int stage = um.getPlannerStage(); // 현재 메모리에 저장된 단계

        // 3. 단계별 텍스트 및 팝업 트리거
        if (percent < 30) {
            progressLabel.setText("씨앗이 자라는 중... (" + percent + "%)");

            // ============================================================
            // ★ [수정됨] 씨앗 지급 로직 (팝업 중복 방지)
            // ============================================================
            if (stage < 1) {
                // DB에서 '마지막으로 플래너 씨앗을 받은 날짜'를 가져옴
                String lastDate = um.getUser().getPlannerDate();
                String today = LocalDate.now().toString();

                // 🔥 오늘 날짜와 다를 때만(아직 안 받았을 때만) 팝업 & 지급 실행
                if (!today.equals(lastDate)) {
                    PopupHelper.showAutoPopup("씨앗 획득! 🌱", "계획 3개를 작성하여\n씨앗을 심었습니다!");
                    givePlannerSeedIfNeeded(); // 내부에서 날짜 업데이트 및 씨앗 지급 수행
                }

                // 받았든 안 받았든, 이번 실행에서는 더 이상 체크하지 않도록 단계 업데이트
                stage = 1;
                um.setPlannerStage(1); // UserManager에도 상태 저장
            }
            // ============================================================

        } else if (percent < 60) {
            progressLabel.setText("새싹이 자라는 중... (" + percent + "%)");
            if (stage < 2) {
                PopupHelper.showAutoPopup("레벨 업! 🌿", "새싹이 돋아났습니다!\n조금만 더 힘내세요!");
                stage = 2;
                um.setPlannerStage(2);
            }
        } else if (percent < 100) {
            progressLabel.setText("꽃봉오리가 맺히는 중... (" + percent + "%)");
            if (stage < 3) {
                PopupHelper.showAutoPopup("레벨 업! 🌷", "꽃봉오리가 맺혔습니다.\n곧 꽃이 필 거예요!");
                stage = 3;
                um.setPlannerStage(3);
            }
        } else { // 100%
            progressLabel.setText("꽃이 피었습니다! 🌸");

            if (stage < 4) {
                stage = 4;
                um.setPlannerStage(4);

                // ... (기존 꽃 지급 로직 유지) ...
                Integer id = UserManager.getInstance().getTodayPlannerSeedFlowerId();
                if (id != null) {
                    String today = LocalDate.now().toString();
                    com.example.studyplanner.manager.FlowerManager fm = com.example.studyplanner.manager.FlowerManager.getInstance();

                    if (!today.equals(um.getLastFlowerGivenPlanner())) {
                        fm.addFlowerCount(id, 1);
                        fm.unlockCard(id);
                        com.example.studyplanner.model.Flower f = fm.getFlowerById(id);
                        PopupHelper.showAutoPopup("축하합니다! 🌸", f.getName() + " 꽃과 꽃말 카드가 해금되었습니다!");
                        um.updateFlowerGivenFromPlanner(today);
                    }
                }
            }
        }
    }

    private void givePlannerSeedIfNeeded() {
        User user = UserManager.getInstance().getUser();
        String today = LocalDate.now().toString();

        // [수정] 메서드 이름 변경됨: getPlannerDate()
        if (today.equals(user.getPlannerDate())) return;

        int flowerId = FlowerManager.getInstance().giveRandomSeed();
        UserManager.getInstance().setTodayPlannerSeedFlowerId(flowerId);
        UserManager.getInstance().updateSeedFromPlanner(today);

        Flower f = FlowerManager.getInstance().getFlowerById(flowerId);
        PopupHelper.showAutoPopup("씨앗 획득! 🌱", f.getName() + " 씨앗을 획득했습니다!");
    }


    // --- 네비게이션 ---
    @FXML
    void navGarden(ActionEvent event) {
        switchScene(event, "garden-view.fxml");
    }

    @FXML
    void navPlanner(ActionEvent event) {
        switchScene(event, "planner-view.fxml");
    }

    @FXML
    void navTimer(ActionEvent event) {
        switchScene(event, "timer-view.fxml");
    }

    @FXML
    void navBook(ActionEvent event) {
        switchScene(event, "collection-view.fxml");
    }

    @FXML
    void goToTimer(MouseEvent event) {
        try {
            URL fxmlUrl = getClass().getResource("timer-view.fxml");
            if (fxmlUrl == null) return;
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Parent root = fxmlLoader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void switchScene(ActionEvent event, String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlFileName);
            if (fxmlUrl == null) fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);
            if (fxmlUrl == null) {
                System.out.println("파일 없음: " + fxmlFileName);
                return;
            }
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Parent root = fxmlLoader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Row Class
    public static class Row {
        private final StringProperty subject = new SimpleStringProperty();
        private final StringProperty content = new SimpleStringProperty();
        private final BooleanProperty done = new SimpleBooleanProperty(false);
        private String color;

        public Row(String s, String c, boolean d, String color) {
            setSubject(s);
            setContent(c);
            setDone(d);
            this.color = color;
        }

        public String getSubject() {
            return subject.get();
        }

        public void setSubject(String v) {
            subject.set(v);
        }

        public StringProperty subjectProperty() {
            return subject;
        }

        public String getContent() {
            return content.get();
        }

        public void setContent(String v) {
            content.set(v);
        }

        public StringProperty contentProperty() {
            return content;
        }

        public boolean isDone() {
            return done.get();
        }

        public void setDone(boolean v) {
            done.set(v);
        }

        public BooleanProperty doneProperty() {
            return done;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }
}