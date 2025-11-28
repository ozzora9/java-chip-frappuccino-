package com.example.studyplanner.model;

import java.time.LocalTime;

public class StudySession {
    private String subjectName;   // 과목명
    private String startTime;     // 시작 시간 (문자열로 저장, 예: "09:30")
    private String endTime;       // 종료 시간
    private long durationSeconds; // 공부한 시간(초)

    // 기본 생성자 (JSON 변환 시 필수)
    public StudySession() {}

    // 편리한 생성자
    public StudySession(String subjectName, LocalTime start, LocalTime end, long duration) {
        this.subjectName = subjectName;
        this.startTime = start.toString(); // LocalTime -> String 변환 저장
        this.endTime = end.toString();
        this.durationSeconds = duration;
    }

    // Getter & Setter
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
}