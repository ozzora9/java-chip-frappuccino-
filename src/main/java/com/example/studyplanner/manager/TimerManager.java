package com.example.studyplanner.manager;

import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.StudySession;
import com.example.studyplanner.model.Subject;
import com.example.studyplanner.model.UserSession;
import com.example.studyplanner.service.DatabaseService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;

public class TimerManager {
    private static TimerManager instance;

    private long dailyTotalSeconds = 0;
    private long currentSessionSeconds = 0;
    private boolean isRunning = false;
    private Subject currentSubject = null;
    private LocalTime startTime;
    private LocalDate today = LocalDate.now();

    private Timeline timeline;
    private final DatabaseService dataService = new DatabaseService();

    private TimerManager() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public static TimerManager getInstance() {
        if (instance == null) instance = new TimerManager();
        return instance;
    }

    // --------------------------------------------------
    // 타이머 동작 로직
    // --------------------------------------------------
    public void startTimer(Subject subject) {
        if (isRunning) return;
        this.currentSubject = subject;
        this.startTime = LocalTime.now();
        this.currentSessionSeconds = 0;
        this.isRunning = true;
        timeline.play();
    }

    public void stopTimer() {
        if (!isRunning) return;

        timeline.stop();
        isRunning = false;

        saveCurrentSession(); // ★ DB 저장 분리

        currentSessionSeconds = 0;
        currentSubject = null;
    }

    // ★ 앱이 꺼질 때 호출될 메서드 (강제 저장)
    public void handleAppShutdown() {
        if (isRunning) {
            System.out.println("⚠️ 앱 종료 감지: 진행 중인 타이머를 저장합니다.");
            timeline.stop();
            saveCurrentSession(); // 현재까지 흐른 시간 저장
        }
    }

    private void tick() {
        if (!LocalDate.now().equals(today)) {
            resetForNewDay();
            return;
        }
        currentSessionSeconds++;
        dailyTotalSeconds++;
    }

    // ★ 저장 로직을 별도로 분리 (중복 제거 및 재사용)
    private void saveCurrentSession() {
        if (currentSubject == null) return;

        String userId = UserSession.getInstance().getUserId();
        // 로그인 정보가 없으면 저장 불가
        if (userId == null) return;

        DailyRecord record = dataService.loadDailyRecord(userId, today);
        LocalTime endTime = LocalTime.now();

        // 세션 추가
        StudySession session = new StudySession(
                currentSubject.getName(),
                startTime,
                endTime,
                currentSessionSeconds
        );
        record.addSession(session);

        // 과목별 총 시간 및 전체 시간 업데이트
        String name = currentSubject.getName();
        DailyRecord.SubjectRecord subRecord = record.getSubjects().get(name);

        if (subRecord == null) {
            subRecord = new DailyRecord.SubjectRecord(0, "#000000", "", false);
        }

        // 기존 공부 시간 + 방금 공부한 시간
        subRecord.setStudiedSeconds(subRecord.getStudiedSeconds() + currentSessionSeconds);
        // 색상 정보 유지 (혹은 갱신)
        if(currentSubject.getColor() != null) {
            // JavaFX Color -> Hex String 변환 로직 필요 시 적용
            // 여기서는 기존 색상을 유지하거나 검정색 기본값
        }

        record.getSubjects().put(name, subRecord);

        // ★ 일일 목표 시간도 덮어씌워지지 않도록 주의 (기존 로직 유지)
        dataService.saveDailyRecord(userId, today, record);

        System.out.println("💾 DB 저장 완료: " + currentSubject.getName() + " (" + currentSessionSeconds + "초)");
    }

    private void resetForNewDay() {
        stopTimer();
        today = LocalDate.now();
        dailyTotalSeconds = 0;
        currentSessionSeconds = 0;
        currentSubject = null;
    }

    // ★ 로그인 직후 또는 앱 시작 시 DB에서 오늘 총 시간을 불러오는 메서드
    public void loadDailyTotalFromDB() {
        String userId = UserSession.getInstance().getUserId();
        if (userId == null) return;

        today = LocalDate.now();
        DailyRecord record = dataService.loadDailyRecord(userId, today);

        long total = 0;
        if (record.getSubjects() != null) {
            for (DailyRecord.SubjectRecord sr : record.getSubjects().values()) {
                total += sr.getStudiedSeconds();
            }
        }

        // ★ 현재 실행 중인 시간이 있다면 그것은 제외하고 DB 값으로만 세팅
        // (앱을 새로 켰을 때는 isRunning이 false이므로 DB 값 = 총 시간이 됨)
        this.dailyTotalSeconds = total;

        System.out.println("🔄 DB 로드 완료: 오늘 총 공부 시간 = " + dailyTotalSeconds + "초");
    }

    // Getters
    public boolean isRunning() { return isRunning; }
    public long getDailyTotalSeconds() { return dailyTotalSeconds; }
    public long getCurrentSessionSeconds() { return currentSessionSeconds; }
    public Subject getCurrentSubject() { return currentSubject; }
}