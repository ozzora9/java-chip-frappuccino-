package com.example.studyplanner.manager;

import com.example.studyplanner.dao.FlowerDAO;
import com.example.studyplanner.model.Flower;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlowerManager {
    private static FlowerManager instance;

    private List<Flower> catalog;    // JSON 정보
    private FlowerDAO flowerDAO = new FlowerDAO();

    private FlowerManager() {
        loadCatalogFromJson();
        mergeDBState();
    }

    public static FlowerManager getInstance() {
        if (instance == null) instance = new FlowerManager();
        return instance;
    }

    // 1) JSON에서 카탈로그 불러오기
    private void loadCatalogFromJson() {
        try {
            Gson gson = new Gson();
            InputStream in = getClass().getResourceAsStream("/com/example/studyplanner/data/flowers.json");
            catalog = Arrays.asList(gson.fromJson(new InputStreamReader(in), Flower[].class));
        } catch (Exception e) {
            e.printStackTrace();
            catalog = new ArrayList<>();
        }
    }

    // 2) DB의 unlocked/quantity 를 합치기
    private void mergeDBState() {
        for (Flower f : catalog) {
            f.setUnlocked(flowerDAO.isUnlocked(f.getId()));
            f.setQuantity(flowerDAO.getQuantity(f.getId()));
        }
    }

    public List<Flower> getCatalog() {
        return catalog;
    }
}
