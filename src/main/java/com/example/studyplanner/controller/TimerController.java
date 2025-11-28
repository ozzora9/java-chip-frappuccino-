package com.example.studyplanner.controller;

import com.example.studyplanner.PopupHelper; // ★ 팝업 도구 Import
import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.StudySession;
import com.example.studyplanner.model.Subject;
import com.example.studyplanner.model.UserSession;
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
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.ResourceBundle;

public class TimerController implements Initializable {

    private String userId;

    // --- FXML 요소 ---
    @FXML private Label timeDisplayLabel;
    @FXML private Label remainingTimeLabel;
    @FXML private Label progressLabel;
    @FXML private Button startStopButton;
    @FXML private Button setGoalButton;
    @FXML private ComboBox<Subject> subjectComboBox;
    @FXML private Group arcGroup;

    // --- 변수 ---
    private long dailyTotalSeconds = 0;
    private long currentSubjectSeconds = 0;
    private long dailyGoalSeconds = 0;
    private Timeline timeline;
    private boolean isRunning = false;
    private LocalDate today = LocalDate.now();
    private long currentSessionSeconds = 0;
    private LocalTime startTime;
    private Arc currentArc;

    private double lastEndAngle = 90.0;

    // ★ [추가] 타이머 성장 단계 (0:없음, 1:씨앗, 2:새싹, 3:꽃봉오리, 4:개화)
    private int timerStage = 0;

    private final DatabaseService dataService = new DatabaseService();
    private DailyRecord dailyRecord;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.userId = UserSession.getInstance().getUserId();
        if (this.userId == null) {
            this.userId = "test_user";
            System.out.println("⚠ 타이머: 로그인 정보 없음. 테스트 계정 사용.");
        }

        loadDailyData();
        updateLabels();
        startDailyResetChecker();

        subjectComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadSubjectData(newVal);
        });
    }

    private double getBaseSeconds() {
        return dailyGoalSeconds > 0 ? dailyGoalSeconds : (12 * 3600);
    }

    private void loadDailyData() {
        dailyRecord = dataService.loadDailyRecord(userId, today);
        this.dailyGoalSeconds = dailyRecord.getDailyGoalSeconds();

        ObservableList<Subject> subjects = FXCollections.observableArrayList();
        dailyTotalSeconds = 0;

        for (Map.Entry<String, DailyRecord.SubjectRecord> entry : dailyRecord.getSubjects().entrySet()) {
            String name = entry.getKey();
            var info = entry.getValue();
            dailyTotalSeconds += info.getStudiedSeconds();

            String hex = info.getColorHex();
            Color color = (hex != null) ? Color.web(hex) : Color.PINK;
            subjects.add(new Subject(name, color));
        }
        subjectComboBox.setItems(subjects);

        if (dailyTotalSeconds > 0) setGoalButton.setDisable(true);
        else setGoalButton.setDisable(false);

        // 목표 설정 여부에 따라 초기 단계 설정 (이미 설정했으면 씨앗 단계는 통과)
        if (dailyGoalSeconds > 0 && timerStage < 1) timerStage = 1;

        drawPastProgress();
    }

    private void drawPastProgress() {
        arcGroup.getChildren().clear();

        Arc bgArc = new Arc(0, 0, 115, 115, 90, 360);
        bgArc.setType(ArcType.OPEN);
        bgArc.setStroke(Color.rgb(245, 245, 245));
        bgArc.setStrokeWidth(25);
        bgArc.setStrokeLineCap(StrokeLineCap.BUTT);
        bgArc.setFill(null);
        arcGroup.getChildren().add(bgArc);

        lastEndAngle = 90.0;
        double base = getBaseSeconds();
        double visualAccumulated = 0;

        for (Map.Entry<String, DailyRecord.SubjectRecord> entry : dailyRecord.getSubjects().entrySet()) {
            String subName = entry.getKey();
            long studied = entry.getValue().getStudiedSeconds();

            if (studied > 0) {
                double available = base - visualAccumulated;
                if (available <= 0) break;

                double visualStudied = Math.min(studied, available);
                double ratio = visualStudied / base;
                double length = ratio * -360.0;

                Color color = getColorForSubjectName(subName);

                Arc arc = new Arc(0, 0, 115, 115, lastEndAngle, length);
                arc.setType(ArcType.OPEN);
                arc.setStroke(color);
                arc.setStrokeWidth(25);
                arc.setStrokeLineCap(StrokeLineCap.BUTT);
                arc.setFill(null);

                arcGroup.getChildren().add(arc);

                lastEndAngle += length;
                visualAccumulated += visualStudied;
            }
        }
    }

    private void createSessionArc() {
        Subject currentSubject = subjectComboBox.getValue();

        currentArc = new Arc(0, 0, 115, 115, lastEndAngle, 0);
        currentArc.setType(ArcType.OPEN);
        currentArc.setStroke(currentSubject.getColor());
        currentArc.setStrokeWidth(25);
        currentArc.setStrokeLineCap(StrokeLineCap.BUTT);
        currentArc.setFill(null);

        arcGroup.getChildren().add(currentArc);
    }

    private void updateCurrentArc() {
        if (currentArc != null) {
            double base = getBaseSeconds();
            double pastSeconds = dailyTotalSeconds - currentSessionSeconds;
            double maxVisualTotal = Math.min(dailyTotalSeconds, base);
            double visualSession = Math.max(0, maxVisualTotal - pastSeconds);

            double angle = (visualSession / base) * -360.0;
            currentArc.setLength(angle);
        }
    }

    private Color getColorForSubjectName(String name) {
        for (Subject s : subjectComboBox.getItems()) {
            if (s.getName().equals(name)) return s.getColor();
        }
        return Color.GRAY;
    }

    private void loadSubjectData(Subject subject) {
        if (isRunning) return;
        String subjectName = subject.getName();
        DailyRecord.SubjectRecord record = dailyRecord.getSubjects().get(subjectName);
        this.currentSubjectSeconds = (record != null) ? record.getStudiedSeconds() : 0;
        updateLabels();
    }

    @FXML
    void handleStartStopButton(ActionEvent event) {
        if (!isRunning && subjectComboBox.getValue() == null) {
            PopupHelper.showAutoPopup("알림 🔔", "공부할 과목을\n먼저 선택해주세요!");
            return;
        }

        if (isRunning) stopTimer();
        else startTimer();
    }
    private void startTimer() {
        subjectComboBox.setDisable(true);
        setGoalButton.setDisable(true);
        this.startTime = LocalTime.now();

        createSessionArc();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            currentSessionSeconds++;
            currentSubjectSeconds++;
            dailyTotalSeconds++;
            updateLabels();
            updateCurrentArc();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        isRunning = true;
        startStopButton.setText("STOP");
        startStopButton.getStyleClass().removeAll("start-state");
        startStopButton.getStyleClass().add("stop-state");
    }

    private void stopTimer() {
        if (timeline != null) timeline.stop();
        isRunning = false;
        startStopButton.setText("START");
        startStopButton.getStyleClass().removeAll("stop-state");
        startStopButton.getStyleClass().add("start-state");
        subjectComboBox.setDisable(false);
        if (dailyTotalSeconds > 0) setGoalButton.setDisable(true);
        else setGoalButton.setDisable(false);

        saveStudySession();

        if (currentArc != null) {
            lastEndAngle += currentArc.getLength();
        }
        currentSessionSeconds = 0;
        currentArc = null;
    }

    private void saveStudySession() {
        Subject currentSubject = subjectComboBox.getValue();
        if (currentSubject == null) return;

        LocalTime endTime = LocalTime.now();
        StudySession session = new StudySession(
                currentSubject.getName(),
                startTime,
                endTime,
                currentSessionSeconds
        );
        dailyRecord.addSession(session);

        String name = currentSubject.getName();
        DailyRecord.SubjectRecord record = dailyRecord.getSubjects().get(name);
        if (record != null) {
            record.setStudiedSeconds(currentSubjectSeconds);
            dailyRecord.getSubjects().put(name, record);
        }
        dataService.saveDailyRecord(userId, today, dailyRecord);
    }

    private void updateLabels() {
        long hours = dailyTotalSeconds / 3600;
        long minutes = (dailyTotalSeconds % 3600) / 60;
        long seconds = dailyTotalSeconds % 60;
        timeDisplayLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

        if (dailyGoalSeconds > 0) {
            long rem = Math.max(0, dailyGoalSeconds - dailyTotalSeconds);
            long rh = rem / 3600;
            long rm = (rem % 3600) / 60;
            long rs = rem % 60;
            remainingTimeLabel.setText(String.format("%d시간 %d분 %d초", rh, rm, rs));

            double percentDouble = ((double) dailyTotalSeconds / dailyGoalSeconds) * 100;
            int percent = (int) Math.min(100, percentDouble);

            progressLabel.setText(String.format("진행률: %d%%", percent));

            // ★ 성장 팝업 체크
            checkGrowthMilestone(percent);

        } else {
            remainingTimeLabel.setText("목표 미설정");
            progressLabel.setText("진행률: 0%");
        }
    }

    // ★ [추가] 성장 단계 체크 및 팝업 로직
    private void checkGrowthMilestone(int percent) {
        if (percent >= 30 && percent < 60 && timerStage < 2) {
            Platform.runLater(() -> PopupHelper.showAutoPopup("레벨 업! 🌿", "집중력 쑥쑥!\n새싹이 자라났습니다."));
            timerStage = 2;
        }
        else if (percent >= 60 && percent < 100 && timerStage < 3) {
            Platform.runLater(() -> PopupHelper.showAutoPopup("레벨 업! 🌷", "거의 다 왔어요!\n꽃봉오리가 맺혔습니다."));
            timerStage = 3;
        }
        else if (percent >= 100 && timerStage < 4) {
            Platform.runLater(() -> PopupHelper.showAutoPopup("목표 달성! 🌸", "오늘의 목표 시간을 채웠습니다!\n아름다운 꽃을 피웠네요."));
            timerStage = 4;
        }
    }

    @FXML
    void handleSetGoalButton(ActionEvent event) {
        if (isRunning) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "공부 중에는 목표를 변경할 수 없습니다.");
            alert.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();

        // -----------------------------------------------------------
        // ★ [수정됨] CSS 로딩 안전장치 추가 (에러 방지)
        // -----------------------------------------------------------
        // 경로 앞에 '/'를 붙여서 절대 경로로 찾습니다.
        URL cssUrl = getClass().getResource("/com/example/studyplanner/planner.css");

        if (cssUrl != null) {
            dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        } else {
            // 파일을 못 찾아도 끄지 말고 콘솔에만 경고 출력
            System.out.println("⚠ 경고: planner.css를 찾을 수 없습니다. 기본 스타일로 표시합니다.");
        }
        // -----------------------------------------------------------

        dialog.setTitle("Set Goal");
        dialog.setHeaderText("하루 목표 공부 시간을 설정하세요");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 10));

        ComboBox<Integer> hBox = createRangeComboBox(0, 23);
        ComboBox<Integer> mBox = createRangeComboBox(0, 59);
        ComboBox<Integer> sBox = createRangeComboBox(0, 59);

        long currentH = dailyGoalSeconds / 3600;
        long currentM = (dailyGoalSeconds % 3600) / 60;
        long currentS = dailyGoalSeconds % 60;
        hBox.setValue((int)currentH);
        mBox.setValue((int)currentM);
        sBox.setValue((int)currentS);

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

                // 목표 설정 시 씨앗 지급 팝업 (안전 처리)
                if (timerStage < 1) {
                    try {
                        Platform.runLater(() -> PopupHelper.showAutoPopup("씨앗 획득! 🌱", "목표 시간을 설정하여\n씨앗을 심었습니다.\n이제 집중해볼까요?"));
                        timerStage = 1;
                    } catch (Exception e) {
                        System.out.println("팝업 오류 무시: " + e.getMessage());
                    }
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

    private void startDailyResetChecker() {
        Timeline check = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (!LocalDate.now().equals(today)) {
                today = LocalDate.now();
                Platform.runLater(this::resetTimerForNewDay);
            }
        }));
        check.setCycleCount(Timeline.INDEFINITE);
        check.play();
    }

    private void resetTimerForNewDay() {
        stopTimer();
        dailyTotalSeconds = 0;
        currentSubjectSeconds = 0;
        dailyGoalSeconds = 0;
        currentSessionSeconds = 0;

        // ★ 리셋 시 성장 단계도 초기화
        timerStage = 0;

        loadDailyData();
        setGoalButton.setDisable(false);
        updateLabels();
    }

    @FXML void navGarden(ActionEvent event) { switchScene(event, "garden-view.fxml"); }
    @FXML void navPlanner(ActionEvent event) { switchScene(event, "planner-view.fxml"); }
    @FXML void navTimer(ActionEvent event) { switchScene(event, "timer-view.fxml"); }
    @FXML void navBook(ActionEvent event) { switchScene(event, "collection-view.fxml"); }

    private void switchScene(ActionEvent event, String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlFileName);
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);
            }
            if (fxmlUrl == null) {
                System.out.println("❌ FXML 파일을 찾을 수 없습니다: " + fxmlFileName);
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
}