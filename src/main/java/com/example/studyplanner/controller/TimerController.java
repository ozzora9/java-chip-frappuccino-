package com.example.studyplanner.controller;

import com.example.studyplanner.PopupHelper;
import com.example.studyplanner.manager.FlowerManager;
import com.example.studyplanner.manager.TimerManager;
import com.example.studyplanner.manager.UserManager;
import com.example.studyplanner.model.*;
import com.example.studyplanner.service.DatabaseService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;

public class TimerController implements Initializable {

    private String userId;

    @FXML private Label timeDisplayLabel;
    @FXML private Label remainingTimeLabel;
    @FXML private Label progressLabel;
    @FXML private Button startStopButton;
    @FXML private Button setGoalButton;
    @FXML private ComboBox<Subject> subjectComboBox;
    @FXML private Group arcGroup;

    private long dailyGoalSeconds = 0;
    private Timeline uiUpdateTimeline;
    private LocalDate today = LocalDate.now();
    private Arc currentArc;

    // ★ 각도 계산을 위한 변수
    private double lastEndAngle = 90.0; // 12시 방향(90도)에서 시작

    private int timerStage = 0;
    private final DatabaseService dataService = new DatabaseService();
    private DailyRecord dailyRecord;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.userId = UserSession.getInstance().getUserId();
        if (this.userId == null) this.userId = "test_user";

        // 1. 데이터 로드 및 초기화
        loadDailyData();

        TimerManager manager = TimerManager.getInstance();
        if (!manager.isRunning()) {
            manager.loadDailyTotalFromDB();
        }

        startUIUpdateLoop();

        // 2. 실행 중이라면 상태 복구
        if (manager.isRunning()) {
            restoreRunningState();
        }

        // 3. 콤보박스 리스너 (실행 중이 아닐 때만 작동)
        subjectComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !manager.isRunning()) {
                updateLabels(); // 선택한 과목에 따라 라벨만 갱신 (그래프는 안 건드림)
            }
        });
    }

    private void startUIUpdateLoop() {
        if (uiUpdateTimeline != null) uiUpdateTimeline.stop();
        uiUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            updateLabels();
            updateCurrentArc(); // 1초마다 현재 아크 길이만 늘림
        }));
        uiUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        uiUpdateTimeline.play();
    }

    private void restoreRunningState() {
        Subject runningSubject = TimerManager.getInstance().getCurrentSubject();

        // 콤보박스 복구 (Subject의 equals가 있어야 정확히 작동)
        if (runningSubject != null) {
            subjectComboBox.setValue(runningSubject);
        }

        subjectComboBox.setDisable(true);
        setGoalButton.setDisable(true);
        startStopButton.setText("STOP");
        startStopButton.getStyleClass().removeAll("start-state");
        startStopButton.getStyleClass().add("stop-state");

        // ★ 복구 시점에서도 아크를 새로 생성해줘야 함
        createSessionArc();
    }

    private void loadDailyData() {
        dailyRecord = dataService.loadDailyRecord(userId, today);
        this.dailyGoalSeconds = dailyRecord.getDailyGoalSeconds();

        ObservableList<Subject> subjects = FXCollections.observableArrayList();
        for (Map.Entry<String, DailyRecord.SubjectRecord> entry : dailyRecord.getSubjects().entrySet()) {
            String name = entry.getKey();
            var info = entry.getValue();
            String hex = info.getColorHex();
            Color color = (hex != null) ? Color.web(hex) : Color.PINK;
            subjects.add(new Subject(name, color));
        }
        subjectComboBox.setItems(subjects);

        long total = TimerManager.getInstance().getDailyTotalSeconds();
        if (total > 0) setGoalButton.setDisable(true);
        else setGoalButton.setDisable(false);

        timerStage = UserManager.getInstance().getTimerStage();
        if (dailyGoalSeconds > 0 && timerStage < 1) timerStage = 1;

        // ★ 데이터를 로드할 때 과거 그래프를 그립니다.
        drawPastProgress();
    }

    @FXML
    void handleStartStopButton(ActionEvent event) {
        TimerManager manager = TimerManager.getInstance();

        if (manager.isRunning()) {
            // STOP
            manager.stopTimer();

            startStopButton.setText("START");
            startStopButton.getStyleClass().removeAll("stop-state");
            startStopButton.getStyleClass().add("start-state");
            subjectComboBox.setDisable(false);

            // ★ 정지하면 현재 아크를 확정짓고(null 처리), 전체 다시 그리기(DB 반영 위해)
            currentArc = null;
            loadDailyData();

        } else {
            // START
            Subject selected = subjectComboBox.getValue();
            if (selected == null) {
                selected = new Subject("기타", Color.LIGHTGRAY);
                subjectComboBox.setValue(selected);
            }

            manager.startTimer(selected);

            subjectComboBox.setDisable(true);
            setGoalButton.setDisable(true);
            startStopButton.setText("STOP");
            startStopButton.getStyleClass().removeAll("start-state");
            startStopButton.getStyleClass().add("stop-state");

            createSessionArc();
        }
    }

    private void updateLabels() {
        TimerManager manager = TimerManager.getInstance();
        long totalSeconds = manager.getDailyTotalSeconds();

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        timeDisplayLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

        if (dailyGoalSeconds > 0) {
            long rem = Math.max(0, dailyGoalSeconds - totalSeconds);
            long rh = rem / 3600;
            long rm = (rem % 3600) / 60;
            long rs = rem % 60;
            remainingTimeLabel.setText(String.format("%d시간 %d분 %d초", rh, rm, rs));

            double percentDouble = ((double) totalSeconds / dailyGoalSeconds) * 100;
            int percent = (int) Math.min(100, percentDouble);

            progressLabel.setText(String.format("진행률: %d%%", percent));
            checkGrowthMilestone(percent);
        } else {
            remainingTimeLabel.setText("목표 미설정");
            progressLabel.setText("진행률: 0%");
        }
    }

    // ★ [수정됨] 현재 세션용 아크 생성 (기존 그래프 끝에서 시작)
    private void createSessionArc() {
        Subject currentSubject = subjectComboBox.getValue();
        if (currentSubject == null) return;

        // lastEndAngle은 drawPastProgress()에서 계산된 마지막 각도입니다.
        currentArc = new Arc(0, 0, 115, 115, lastEndAngle, 0);
        currentArc.setType(ArcType.OPEN);
        currentArc.setStroke(currentSubject.getColor());
        currentArc.setStrokeWidth(25);
        currentArc.setStrokeLineCap(StrokeLineCap.BUTT);
        currentArc.setFill(null);

        arcGroup.getChildren().add(currentArc);
    }

    // ★ [수정됨] 현재 아크 길이만 업데이트
    private void updateCurrentArc() {
        TimerManager manager = TimerManager.getInstance();
        if (manager.isRunning() && currentArc != null) {
            double base = getBaseSeconds();
            long sessionSec = manager.getCurrentSessionSeconds();

            // 시계 방향(-)으로 회전
            double angle = (sessionSec / base) * -360.0;
            currentArc.setLength(angle);
        }
    }

    private double getBaseSeconds() {
        return dailyGoalSeconds > 0 ? dailyGoalSeconds : (12 * 3600); // 목표 없으면 12시간 기준
    }

    // ★ [수정됨] 과거 기록 그리기 (DB 데이터 기준)
    private void drawPastProgress() {
        arcGroup.getChildren().clear();

        // 1. 회색 배경 원
        Arc bgArc = new Arc(0, 0, 115, 115, 90, 360);
        bgArc.setType(ArcType.OPEN);
        bgArc.setStroke(Color.rgb(245, 245, 245));
        bgArc.setStrokeWidth(25);
        bgArc.setFill(null);
        arcGroup.getChildren().add(bgArc);

        // 2. 시작 각도 초기화 (12시 방향 = 90도)
        lastEndAngle = 90.0;
        double base = getBaseSeconds();

        // 3. DB에 저장된 기록들을 순회하며 그리기
        for (Map.Entry<String, DailyRecord.SubjectRecord> entry : dailyRecord.getSubjects().entrySet()) {
            String subName = entry.getKey();
            long studied = entry.getValue().getStudiedSeconds();

            if (studied > 0) {
                double ratio = (double) studied / base;
                double length = ratio * -360.0; // 시계 방향
                Color color = getColorForSubjectName(subName);

                Arc arc = new Arc(0, 0, 115, 115, lastEndAngle, length);
                arc.setType(ArcType.OPEN);
                arc.setStroke(color);
                arc.setStrokeWidth(25);
                arc.setFill(null);
                arcGroup.getChildren().add(arc);

                // 다음 아크 시작점 갱신
                lastEndAngle += length;
            }
        }

        // 4. 만약 현재 실행 중이라면, 현재 아크도 이어서 그려줘야 함 (복구 시 필요)
        if (TimerManager.getInstance().isRunning()) {
            createSessionArc();
        }
    }

    private Color getColorForSubjectName(String name) {
        for (Subject s : subjectComboBox.getItems()) {
            if (s.getName().equals(name)) return s.getColor();
        }
        return Color.GRAY;
    }

    // 기존 checkGrowthMilestone 메서드를 이걸로 덮어씌우세요.
    private void checkGrowthMilestone(int percent) {

        // 1. 단계별 팝업 (씨앗 -> 새싹 -> 꽃봉오리)
        if (percent >= 30 && percent < 60 && timerStage < 2) {
            Platform.runLater(() -> PopupHelper.showAutoPopup("레벨 업! 🌿", "집중력 쑥쑥!\n새싹이 자라났습니다."));
            timerStage = 2;
            UserManager.getInstance().setTimerStage(2);
            UserManager.getInstance().setCurrentProgressPercent(percent);
        }
        else if (percent >= 60 && percent < 100 && timerStage < 3) {
            Platform.runLater(() -> PopupHelper.showAutoPopup("레벨 업! 🌷", "거의 다 왔어요!\n꽃봉오리가 맺혔습니다."));
            timerStage = 3;
            UserManager.getInstance().setTimerStage(3);
            UserManager.getInstance().setCurrentProgressPercent(percent);
        }

        // 2. 100% 달성 시 (개화 및 해금 로직)
        else if (percent >= 100) {

            // 아직 단계가 4가 아니었다면 업데이트 (최초 달성 시)
            if (timerStage < 4) {
                timerStage = 4;
                UserManager.getInstance().setTimerStage(4);
                UserManager.getInstance().setCurrentProgressPercent(percent);
                Platform.runLater(() -> PopupHelper.showAutoPopup("목표 달성! 🌸", "오늘의 목표 시간을 채웠습니다!"));
            }

            // ★ [핵심 수정] 꽃 해금 로직 강화
            Integer id = UserManager.getInstance().getTodayTimerSeedFlowerId();

            if (id != null && id > 0) {
                FlowerManager fm = FlowerManager.getInstance();
                Flower f = fm.getFlowerById(id);

                // 조건: 꽃 데이터가 있고, "아직 카드가 잠겨있다면" -> 무조건 해금 실행!
                // (날짜 기록이 꼬였더라도 100%고 잠겨있으면 해금해줍니다)
                if (f != null && !f.isCardUnlocked()) {

                    fm.addFlowerCount(id, 1);
                    fm.unlockCard(id); // DB 및 메모리에 해금 반영

                    String todayStr = LocalDate.now().toString();
                    UserManager.getInstance().updateFlowerGivenFromTimer(todayStr); // 날짜 최신화

                    Platform.runLater(() -> PopupHelper.showAutoPopup(
                            "축하합니다! 🌸",
                            f.getName() + " 꽃과 꽃말 카드가 해금되었습니다!"
                    ));
                }
            }
        }
    }

    @FXML
    void handleSetGoalButton(ActionEvent event) {
        if (TimerManager.getInstance().isRunning()) {
            new Alert(Alert.AlertType.WARNING, "공부 중에는 목표를 변경할 수 없습니다.").showAndWait();
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        URL cssUrl = getClass().getResource("/com/example/studyplanner/planner.css");
        if (cssUrl != null) dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        dialog.setTitle("Set Goal");
        dialog.setHeaderText("하루 목표 공부 시간을 설정하세요");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20, 50, 10, 10));

        ComboBox<Integer> hBox = createRangeComboBox(0, 23);
        ComboBox<Integer> mBox = createRangeComboBox(0, 59);
        ComboBox<Integer> sBox = createRangeComboBox(0, 59);

        long currentH = dailyGoalSeconds / 3600;
        long currentM = (dailyGoalSeconds % 3600) / 60;
        long currentS = dailyGoalSeconds % 60;
        hBox.setValue((int)currentH); mBox.setValue((int)currentM); sBox.setValue((int)currentS);

        grid.add(new Label("Hour:"), 0, 0); grid.add(hBox, 1, 0);
        grid.add(new Label("Min:"), 0, 1);  grid.add(mBox, 1, 1);
        grid.add(new Label("Sec:"), 0, 2);  grid.add(sBox, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                this.dailyGoalSeconds = (hBox.getValue() * 3600L) + (mBox.getValue() * 60L) + sBox.getValue();
                dailyRecord.setDailyGoalSeconds(this.dailyGoalSeconds);
                dataService.saveDailyRecord(userId, today, dailyRecord);
                drawPastProgress();
                updateLabels();

                if (timerStage < 1) {
                    giveTimerSeedIfNeeded();
                    timerStage = 1;
                }
            }
        });
    }

    private ComboBox<Integer> createRangeComboBox(int start, int end) {
        ComboBox<Integer> box = new ComboBox<>();
        ObservableList<Integer> list = FXCollections.observableArrayList();
        for (int i = start; i <= end; i++) list.add(i);
        box.setItems(list);
        return box;
    }

    private void giveTimerSeedIfNeeded() {
        User user = UserManager.getInstance().getUser();
        String todayStr = LocalDate.now().toString();
        if (todayStr.equals(user.getTimerDate())) return;

        Integer plannerSeedId = UserManager.getInstance().getTodayPlannerSeedFlowerId();
        int newFlowerId;
        do {
            newFlowerId = FlowerManager.getInstance().giveRandomSeed();
        } while (plannerSeedId != null && newFlowerId == plannerSeedId);

        UserManager.getInstance().setTodayTimerSeedFlowerId(newFlowerId);
        UserManager.getInstance().updateSeedFromTimer(todayStr);

        Flower f = FlowerManager.getInstance().getFlowerById(newFlowerId);
        Platform.runLater(() -> PopupHelper.showAutoPopup("씨앗 획득! 🌱", f.getName() + " 씨앗을 획득했습니다!"));
    }

    @FXML void navGarden(ActionEvent event) { switchScene(event, "garden-view.fxml"); }
    @FXML void navPlanner(ActionEvent event) { switchScene(event, "planner-view.fxml"); }
    @FXML void navTimer(ActionEvent event) { switchScene(event, "timer-view.fxml"); }
    @FXML void navBook(ActionEvent event) { switchScene(event, "collection-view.fxml"); }

    private void switchScene(ActionEvent event, String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlFileName);
            if (fxmlUrl == null) fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Parent root = fxmlLoader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }
}