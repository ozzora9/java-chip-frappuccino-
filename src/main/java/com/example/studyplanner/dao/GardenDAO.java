package com.example.studyplanner.dao;

import com.example.studyplanner.model.GardenItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GardenDAO {
    private static GardenDAO instance;
    private static final String DB_URL = "jdbc:sqlite:planner.db";

    public static GardenDAO getInstance() {
        if (instance == null) {
            instance = new GardenDAO();
        }
        return instance;
    }

    // 🌱 정원에 꽃 하나 추가 (INSERT)
    public int insertFlower(String userId, int flowerId, double x, double y) {
        String sql = "INSERT INTO garden_layout(user_id, flower_id, pos_x, pos_y) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setInt(2, flowerId);
            stmt.setDouble(3, x);
            stmt.setDouble(4, y);

            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }


    // 🌼 정원에 저장된 꽃 전체 불러오기 (SELECT)
    public List<GardenItem> loadGarden(String userId) {
        String sql = "SELECT id, flower_id, pos_x, pos_y FROM garden_layout WHERE user_id = ?";

        List<GardenItem> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int layoutId = rs.getInt("id");       // 🔥 추가됨
                int flowerId = rs.getInt("flower_id");
                double x = rs.getDouble("pos_x");
                double y = rs.getDouble("pos_y");

                list.add(new GardenItem(layoutId, flowerId, x, y));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public void updateFlowerPosition(int layoutId, double x, double y) {
        String sql = "UPDATE garden_layout SET pos_x = ?, pos_y = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, x);
            stmt.setDouble(2, y);
            stmt.setInt(3, layoutId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void deleteFlower(int layoutId) {
        String sql = "DELETE FROM garden_layout WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, layoutId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<GardenFlowerData> getAllPlacedFlowers(String userId) {
        String sql = "SELECT id, flower_id, pos_x, pos_y FROM garden_layout WHERE user_id = ?";
        List<GardenFlowerData> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new GardenFlowerData(
                        rs.getInt("id"),
                        rs.getInt("flower_id"),
                        rs.getDouble("pos_x"),
                        rs.getDouble("pos_y")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }
    public class GardenFlowerData {
        public int layoutId;
        public int flowerId;
        public double x;
        public double y;

        public GardenFlowerData(int layoutId, int flowerId, double x, double y) {
            this.layoutId = layoutId;
            this.flowerId = flowerId;
            this.x = x;
            this.y = y;
        }
    }

    // 🌼 인벤토리에서 꽃 1개 사용 (flower_count - 1)
    public void useFlower(String userId, int flowerId) {
        String sql = "UPDATE flower_inventory " +
                "SET flower_qty = flower_qty - 1 " +
                "WHERE user_id=? AND flower_id=? AND flower_qty > 0";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setInt(2, flowerId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
