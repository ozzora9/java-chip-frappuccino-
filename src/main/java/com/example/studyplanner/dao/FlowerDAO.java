package com.example.studyplanner.dao;

import java.sql.*;

public class FlowerDAO {

    // DatabaseService와 동일한 DB경로 사용
    private static final String DB_URL = "jdbc:sqlite:planner.db";

    public boolean isUnlocked(int flowerId) {
        String sql = "SELECT unlocked FROM flower_unlocks WHERE flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, flowerId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("unlocked") == 1;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getQuantity(int flowerId) {
        String sql = "SELECT quantity FROM flower_inventory WHERE flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, flowerId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("quantity");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
