package com.example.studyplanner.service;

import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.DailyRecord.SubjectRecord;
import com.example.studyplanner.model.StudySession;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class DatabaseService {

    // DB 파일 경로
    private static final String DB_URL = "jdbc:sqlite:planner.db";

    public DatabaseService() {
        // ★ 객체가 생성될 때 테이블을 만듭니다.
        createTables();
    }

    // 1. 테이블 생성 (이 부분이 실행되어야 'no such table' 에러가 안 납니다)
    private void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // [1] 사용자 테이블 (로그인용)
            String sqlUser = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id TEXT PRIMARY KEY, " +
                    "password TEXT NOT NULL, " +
                    "nickname TEXT)";

            // [2] 하루 요약 (목표 시간)
            String sqlDaily = "CREATE TABLE IF NOT EXISTS daily_summary (" +
                    "user_id TEXT, " +
                    "date TEXT, " +
                    "daily_goal_seconds INTEGER, " +
                    "PRIMARY KEY(user_id, date), " +
                    "FOREIGN KEY(user_id) REFERENCES users(user_id))";

            // [3] 과목별 기록
            String sqlSubject = "CREATE TABLE IF NOT EXISTS subject_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT, " +
                    "date TEXT, " +
                    "subject_name TEXT, " +
                    "studied_seconds INTEGER, " +
                    "color TEXT, " +
                    "content TEXT, " +
                    "is_done INTEGER, " +
                    "FOREIGN KEY(user_id) REFERENCES users(user_id))";

            // [4] 공부 세션 (형광펜)
            String sqlSession = "CREATE TABLE IF NOT EXISTS study_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT, " +
                    "date TEXT, " +
                    "subject_name TEXT, " +
                    "start_time TEXT, " +
                    "end_time TEXT, " +
                    "duration INTEGER, " +
                    "FOREIGN KEY(user_id) REFERENCES users(user_id))";

            // 테이블 생성 실행
            stmt.execute(sqlUser);
            stmt.execute(sqlDaily);
            stmt.execute(sqlSubject);
            stmt.execute(sqlSession);

            System.out.println("✅ DB 테이블 생성 완료");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------
    // [기능 1] 로그인 & 회원가입
    // ----------------------------------------------------

    public boolean registerUser(String userId, String password, String nickname) {
        String sql = "INSERT INTO users(user_id, password, nickname) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, password);
            pstmt.setString(3, nickname);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("회원가입 실패: " + e.getMessage());
            return false;
        }
    }

    public String loginUser(String userId, String password) {
        String sql = "SELECT nickname FROM users WHERE user_id = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ----------------------------------------------------
    // [기능 2] 데이터 저장 (플래너/타이머)
    // ----------------------------------------------------

    public void saveDailyRecord(String userId, LocalDate date, DailyRecord record) {
        String dateStr = date.toString();

        String insertSummary = "INSERT OR REPLACE INTO daily_summary (user_id, date, daily_goal_seconds) VALUES (?, ?, ?)";
        String deleteSubjects = "DELETE FROM subject_log WHERE user_id = ? AND date = ?";
        String insertSubject = "INSERT INTO subject_log (user_id, date, subject_name, studied_seconds, color, content, is_done) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String deleteSessions = "DELETE FROM study_sessions WHERE user_id = ? AND date = ?";
        String insertSession = "INSERT INTO study_sessions (user_id, date, subject_name, start_time, end_time, duration) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);

            // 1. 목표 시간
            try (PreparedStatement pstmt = conn.prepareStatement(insertSummary)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, dateStr);
                pstmt.setLong(3, record.getDailyGoalSeconds());
                pstmt.executeUpdate();
            }

            // 2. 과목 기록
            try (PreparedStatement del = conn.prepareStatement(deleteSubjects);
                 PreparedStatement ins = conn.prepareStatement(insertSubject)) {
                del.setString(1, userId);
                del.setString(2, dateStr);
                del.executeUpdate();

                for (Map.Entry<String, SubjectRecord> entry : record.getSubjects().entrySet()) {
                    SubjectRecord sr = entry.getValue();
                    ins.setString(1, userId);
                    ins.setString(2, dateStr);
                    ins.setString(3, entry.getKey());
                    ins.setLong(4, sr.getStudiedSeconds());
                    ins.setString(5, sr.getColorHex());
                    ins.setString(6, sr.getTaskContent());
                    ins.setInt(7, sr.isDone() ? 1 : 0);
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            // 3. 세션 기록
            try (PreparedStatement del = conn.prepareStatement(deleteSessions);
                 PreparedStatement ins = conn.prepareStatement(insertSession)) {
                del.setString(1, userId);
                del.setString(2, dateStr);
                del.executeUpdate();

                for (StudySession s : record.getStudySessions()) {
                    ins.setString(1, userId);
                    ins.setString(2, dateStr);
                    ins.setString(3, s.getSubjectName());
                    ins.setString(4, s.getStartTime());
                    ins.setString(5, s.getEndTime());
                    ins.setLong(6, s.getDurationSeconds());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public DailyRecord loadDailyRecord(String userId, LocalDate date) {
        DailyRecord record = new DailyRecord();
        String dateStr = date.toString();

        String sql1 = "SELECT daily_goal_seconds FROM daily_summary WHERE user_id = ? AND date = ?";
        String sql2 = "SELECT subject_name, studied_seconds, color, content, is_done FROM subject_log WHERE user_id = ? AND date = ?";
        String sql3 = "SELECT subject_name, start_time, end_time, duration FROM study_sessions WHERE user_id = ? AND date = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql1)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, dateStr);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) record.setDailyGoalSeconds(rs.getLong(1));
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, dateStr);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String name = rs.getString("subject_name");
                    long sec = rs.getLong("studied_seconds");
                    String color = rs.getString("color");
                    String content = rs.getString("content");
                    boolean done = rs.getInt("is_done") == 1;
                    record.getSubjects().put(name, new SubjectRecord(sec, color, content, done));
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, dateStr);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    try {
                        record.addSession(new StudySession(
                                rs.getString("subject_name"),
                                LocalTime.parse(rs.getString("start_time")),
                                LocalTime.parse(rs.getString("end_time")),
                                rs.getLong("duration")
                        ));
                    } catch (Exception e) {}
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return record;
    }
}