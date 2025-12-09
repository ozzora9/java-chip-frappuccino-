package com.example.studyplanner.model;

public class User {
    private String userId;
    private String nickname;
    private String password;

    // 날짜 (YYYY-MM-DD)
    private String plannerDate;
    private String timerDate;
    private String plannerFlowerDate;
    private String timerFlowerDate;

    // 상태 데이터
    private int plannerSeedId;
    private int timerSeedId;
    private int timerStage;
    private double currentPercent;

    public User() {}

    // Getters & Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPlannerDate() { return plannerDate; }
    public void setPlannerDate(String date) { this.plannerDate = date; }

    public String getTimerDate() { return timerDate; }
    public void setTimerDate(String date) { this.timerDate = date; }

    public String getPlannerFlowerDate() { return plannerFlowerDate; }
    public void setPlannerFlowerDate(String date) { this.plannerFlowerDate = date; }

    public String getTimerFlowerDate() { return timerFlowerDate; }
    public void setTimerFlowerDate(String date) { this.timerFlowerDate = date; }

    public int getPlannerSeedId() { return plannerSeedId; }
    public void setPlannerSeedId(int id) { this.plannerSeedId = id; }

    public int getTimerSeedId() { return timerSeedId; }
    public void setTimerSeedId(int id) { this.timerSeedId = id; }

    public int getTimerStage() { return timerStage; }
    public void setTimerStage(int stage) { this.timerStage = stage; }

    public double getCurrentPercent() { return currentPercent; }
    public void setCurrentPercent(double percent) { this.currentPercent = percent; }
}