package com.example.studyplanner.manager;

import com.example.studyplanner.model.User;
import com.example.studyplanner.service.DatabaseService;
import java.time.LocalDate;

public class UserManager {
    private static UserManager instance;
    private final DatabaseService dataService;
    private User user;

    private Integer todayPlannerSeedFlowerId;
    private Integer todayTimerSeedFlowerId;

    private static double currentProgressPercent;
    private int timerStage;

    private UserManager(DatabaseService dataService, String userId) {
        this.dataService = dataService;
        this.user = dataService.loadUser(userId);
        if (this.user == null) throw new RuntimeException("User load failed");

        String today = LocalDate.now().toString();

        // 1. DB에서 로드한 ID 가져오기
        int pId = user.getPlannerSeedId();
        int tId = user.getTimerSeedId();

        // 2. 날짜 체크: DB에 저장된 날짜가 '오늘'과 같으면 ID 유지, 아니면 null (초기화)
        // (단, 여기서는 값을 null로만 만들고 DB를 0으로 덮어쓰지는 않음. 새 씨앗 받을 때 덮어씀)

        if (today.equals(user.getPlannerDate()) && pId > 0) {
            this.todayPlannerSeedFlowerId = pId;
        } else {
            this.todayPlannerSeedFlowerId = null;
        }

        if (today.equals(user.getTimerDate()) && tId > 0) {
            this.todayTimerSeedFlowerId = tId;
        } else {
            this.todayTimerSeedFlowerId = null;
        }

        this.timerStage = user.getTimerStage();
        currentProgressPercent = user.getCurrentPercent();
    }

    public static void initialize(DatabaseService dataService, String userId) {
        if (instance == null) instance = new UserManager(dataService, userId);
    }
    public static UserManager getInstance() { return instance; }
    public User getUser() { return user; }

    // --- Setter (DB 저장 포함) ---
    public void setTodayPlannerSeedFlowerId(int id) {
        this.todayPlannerSeedFlowerId = id;
        dataService.updatePlannerSeedId(user.getUserId(), id);
    }
    public Integer getTodayPlannerSeedFlowerId() { return todayPlannerSeedFlowerId; }

    public void setTodayTimerSeedFlowerId(int id) {
        this.todayTimerSeedFlowerId = id;
        dataService.updateTimerSeedId(user.getUserId(), id);
    }
    public Integer getTodayTimerSeedFlowerId() { return todayTimerSeedFlowerId; }

    // 날짜 업데이트
    public void updateSeedFromPlanner(String today) {
        user.setPlannerDate(today);
        dataService.updatePlannerDate(user.getUserId(), today);
    }
    public void updateSeedFromTimer(String today) {
        user.setTimerDate(today);
        dataService.updateTimerDate(user.getUserId(), today);
    }

    // 꽃 지급 날짜
    public String getLastFlowerGivenPlanner() { return user.getPlannerFlowerDate(); }
    public void updateFlowerGivenFromPlanner(String today) {
        user.setPlannerFlowerDate(today);
        dataService.updatePlannerFlowerDate(user.getUserId(), today);
    }
    public String getLastFlowerGivenTimer() { return user.getTimerFlowerDate(); }
    public void updateFlowerGivenFromTimer(String today) {
        user.setTimerFlowerDate(today);
        dataService.updateTimerFlowerDate(user.getUserId(), today);
    }

    // 진행률
    public static double getCurrentProgressPercent() { return currentProgressPercent; }
    public static void setCurrentProgressPercent(double p) {
        currentProgressPercent = p;
        if(instance != null) instance.dataService.updateCurrentPercent(instance.user.getUserId(), p);
    }

    public int getTimerStage() { return timerStage; }
    public void setTimerStage(int s) {
        this.timerStage = s;
        dataService.updateTimerStage(user.getUserId(), s);
    }

    private int plannerStage = 0;
    public int getPlannerStage() { return plannerStage; }
    public void setPlannerStage(int s) { this.plannerStage = s; }
}