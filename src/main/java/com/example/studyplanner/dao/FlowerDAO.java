package com.example.studyplanner.dao;

import java.sql.*;
import java.util.*;

public class FlowerDAO {

    private static final String DB_URL = "jdbc:sqlite:planner.db";

    // -------------------------------
    // 1) 씨앗 지급
    // -------------------------------
    public void addSeed(String userId, int flowerId, int amount) {
        String sql = "UPDATE flower_inventory " +
                "SET seed_qty = seed_qty + ?, " +
                "    is_seed_unlocked = 1 " +
                "WHERE user_id = ? AND flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, amount);
            stmt.setString(2, userId);
            stmt.setInt(3, flowerId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------
    // 2) 카드 해금
    // -------------------------------
    public void unlockCard(String userId, int flowerId) {
        String sql = "UPDATE flower_inventory " +
                "SET is_card_unlocked = 1 " +
                "WHERE user_id = ? AND flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setInt(2, flowerId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // -------------------------------
    // 3) 모든 꽃 인벤토리 읽기
    // -------------------------------
    public Map<Integer, FlowerInventoryData> getAllInventory(String userId) {

        String sql = "SELECT * FROM flower_inventory WHERE user_id = ?";

        Map<Integer, FlowerInventoryData> map = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("flower_id");
                    int seedQty = rs.getInt("seed_qty");
                    int flowerQty = rs.getInt("flower_qty");
                    boolean seedUnlocked = rs.getInt("is_seed_unlocked") == 1;
                    boolean cardUnlocked = rs.getInt("is_card_unlocked") == 1;

                    map.put(id, new FlowerInventoryData(
                            id, seedQty, flowerQty, seedUnlocked, cardUnlocked
                    ));
                }

            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return map;
    }


    // -------------------------------
    // 4) 꽃 하나 읽기
    // -------------------------------
    public FlowerInventoryData getInventoryForFlower(int flowerId) {
        String sql = "SELECT flower_id, seed_qty, flower_qty is_seed_unlocked, is_card_unlocked " +
                "FROM flower_inventory WHERE flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, flowerId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new FlowerInventoryData(
                        flowerId,
                        rs.getInt("seed_qty"),
                        rs.getInt("flower_qty"),
                        rs.getInt("is_seed_unlocked") == 1,
                        rs.getInt("is_card_unlocked") == 1
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void addFlower(String userId, int flowerId, int amount) {
        String sql = "UPDATE flower_inventory " +
                "SET flower_qty = flower_qty + ? " +
                "WHERE user_id = ? AND flower_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, amount);
            pstmt.setString(2, userId);
            pstmt.setInt(3, flowerId);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
//커밋
