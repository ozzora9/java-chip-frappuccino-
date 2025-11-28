package com.example.studyplanner.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DailyRecord {
    private long dailyGoalSeconds;
    private Map<String, SubjectRecord> subjects = new HashMap<>();
    private List<StudySession> studySessions = new ArrayList<>();

    public long getDailyGoalSeconds() { return dailyGoalSeconds; }
    public void setDailyGoalSeconds(long dailyGoalSeconds) { this.dailyGoalSeconds = dailyGoalSeconds; }

    public Map<String, SubjectRecord> getSubjects() { return subjects; }
    public void setSubjects(Map<String, SubjectRecord> subjects) { this.subjects = subjects; }

    public List<StudySession> getStudySessions() { return studySessions; }
    public void setStudySessions(List<StudySession> studySessions) { this.studySessions = studySessions; }

    public void addSession(StudySession session) {
        this.studySessions.add(session);
    }

    // ★ [수정됨] 내부 클래스: 색상, 내용, 완료여부 필드 추가
    public static class SubjectRecord {
        private long studiedSeconds;
        private String colorHex;     // ★ 색상
        private String taskContent;  // ★ 내용
        private boolean isDone;      // ★ 완료 여부

        public SubjectRecord() {}

        public SubjectRecord(long studiedSeconds, String colorHex, String taskContent, boolean isDone) {
            this.studiedSeconds = studiedSeconds;
            this.colorHex = colorHex;
            this.taskContent = taskContent;
            this.isDone = isDone;
        }

        // Getter & Setter
        public long getStudiedSeconds() { return studiedSeconds; }
        public void setStudiedSeconds(long studiedSeconds) { this.studiedSeconds = studiedSeconds; }

        public String getColorHex() { return colorHex; } // ★ 이 메소드가 없어서 에러가 났던 것임
        public void setColorHex(String colorHex) { this.colorHex = colorHex; }

        public String getTaskContent() { return taskContent; }
        public void setTaskContent(String taskContent) { this.taskContent = taskContent; }

        public boolean isDone() { return isDone; }
        public void setDone(boolean done) { isDone = done; }
    }
}