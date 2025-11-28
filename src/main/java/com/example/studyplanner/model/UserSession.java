package com.example.studyplanner.model;

public class UserSession {
    private static UserSession instance;
    private String userId;
    private String nickname;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void login(String userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public void logout() {
        this.userId = null;
        this.nickname = null;
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }
}