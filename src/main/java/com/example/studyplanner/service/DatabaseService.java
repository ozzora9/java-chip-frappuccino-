package com.example.studyplanner.service;

import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.User;
import com.google.gson.Gson;

import java.sql.*;
import java.time.LocalDate;

public class DatabaseService {

    private static final String DB_URL = "jdbc:sqlite:planner.db";
    private final Gson gson = new Gson();

    public DatabaseService() {
        createTables();
    }

    private void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // [1] 사용자 테이블 (컬럼명 간소화)
            String sqlUser = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id TEXT PRIMARY KEY, " +
                    "password TEXT NOT NULL, " +
                    "nickname TEXT, " +
                    "planner_date TEXT, " +       // 플래너 씨앗 받은 날짜
                    "timer_date TEXT, " +         // 타이머 씨앗 받은 날짜
                    "planner_flower_date TEXT, " + // 플래너 꽃 받은 날짜
                    "timer_flower_date TEXT, " +   // 타이머 꽃 받은 날짜
                    "planner_seed_id INTEGER DEFAULT 0, " +
                    "timer_seed_id INTEGER DEFAULT 0, " +
                    "timer_stage INTEGER DEFAULT 0, " +
                    "current_percent REAL DEFAULT 0.0" +
                    ")";
            stmt.execute(sqlUser);

            // [2] 일간 기록
            String sqlDaily = "CREATE TABLE IF NOT EXISTS daily_records (" +
                    "user_id TEXT, date TEXT, json_data TEXT, PRIMARY KEY (user_id, date))";
            stmt.execute(sqlDaily);

            // [3] 인벤토리
            String sqlInv = "CREATE TABLE IF NOT EXISTS flower_inventory (" +
                    "user_id TEXT, flower_id INTEGER, seed_qty INTEGER DEFAULT 0, flower_qty INTEGER DEFAULT 0, " +
                    "is_seed_unlocked INTEGER DEFAULT 0, is_card_unlocked INTEGER DEFAULT 0, PRIMARY KEY (user_id, flower_id))";
            stmt.execute(sqlInv);

            // [4] 정원
            String sqlGarden = "CREATE TABLE IF NOT EXISTS garden_layout (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, flower_id INTEGER, pos_x REAL, pos_y REAL)";
            stmt.execute(sqlGarden);

            System.out.println("✅ DB 테이블 초기화 완료");

        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- 유저 관리 ---
    public boolean registerUser(String id, String pw, String nickname) {
        String sql = "INSERT INTO users(user_id, password, nickname) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id); pstmt.setString(2, pw); pstmt.setString(3, nickname);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public String loginUser(String id, String pw) {
        String sql = "SELECT nickname FROM users WHERE user_id = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id); pstmt.setString(2, pw);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("nickname");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // ★ 유저 정보 로드 (매핑 정확하게 확인)
    public User loadUser(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                User u = new User();
                u.setUserId(rs.getString("user_id"));
                u.setPassword(rs.getString("password"));
                u.setNickname(rs.getString("nickname"));

                u.setPlannerDate(rs.getString("planner_date"));
                u.setTimerDate(rs.getString("timer_date"));
                u.setPlannerFlowerDate(rs.getString("planner_flower_date"));
                u.setTimerFlowerDate(rs.getString("timer_flower_date"));

                u.setPlannerSeedId(rs.getInt("planner_seed_id"));
                u.setTimerSeedId(rs.getInt("timer_seed_id"));
                u.setTimerStage(rs.getInt("timer_stage"));
                u.setCurrentPercent(rs.getDouble("current_percent"));

                System.out.println("🔄 DB 로드: P_ID=" + u.getPlannerSeedId() + ", T_ID=" + u.getTimerSeedId());
                return u;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // --- 업데이트 메서드 (컬럼명 주의) ---
    public void updatePlannerDate(String uid, String d) { updateCol(uid, "planner_date", d); }
    public void updateTimerDate(String uid, String d) { updateCol(uid, "timer_date", d); }
    public void updatePlannerFlowerDate(String uid, String d) { updateCol(uid, "planner_flower_date", d); }
    public void updateTimerFlowerDate(String uid, String d) { updateCol(uid, "timer_flower_date", d); }

    public void updatePlannerSeedId(String uid, int id) {
        System.out.println("💾 DB 저장: 플래너 ID -> " + id);
        updateCol(uid, "planner_seed_id", id);
    }
    public void updateTimerSeedId(String uid, int id) {
        System.out.println("💾 DB 저장: 타이머 ID -> " + id);
        updateCol(uid, "timer_seed_id", id);
    }
    public void updateTimerStage(String uid, int s) { updateCol(uid, "timer_stage", s); }
    public void updateCurrentPercent(String uid, double p) { updateCol(uid, "current_percent", p); }

    private void updateCol(String uid, String col, Object val) {
        String sql = "UPDATE users SET " + col + " = ? WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, val);
            pstmt.setString(2, uid);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- Daily & Inventory (기존 유지) ---
    public void saveDailyRecord(String uid, LocalDate d, DailyRecord r) {
        String sql = "INSERT OR REPLACE INTO daily_records (user_id, date, json_data) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid); ps.setString(2, d.toString()); ps.setString(3, gson.toJson(r));
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public DailyRecord loadDailyRecord(String uid, LocalDate d) {
        String sql = "SELECT json_data FROM daily_records WHERE user_id = ? AND date = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid); ps.setString(2, d.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return gson.fromJson(rs.getString("json_data"), DailyRecord.class);
        } catch (SQLException e) { e.printStackTrace(); }
        return new DailyRecord();
    }
    public void initFlowerInventory(String uid) {
        String sql = "INSERT OR IGNORE INTO flower_inventory(user_id, flower_id) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 1; i <= 15; i++) { ps.setString(1, uid); ps.setInt(2, i); ps.addBatch(); }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}