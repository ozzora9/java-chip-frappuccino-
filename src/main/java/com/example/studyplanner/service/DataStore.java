package com.example.studyplanner.service;

import com.example.studyplanner.model.Flower;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataStore {
    public static List<Flower> inventory = new ArrayList<>();
    public static List<Flower> garden = new ArrayList<>();
    public static List<Integer> unlockedFlowerIds = new ArrayList<>();

    public static List<Flower> allFlowers = new ArrayList<>();  // ✅ 전체 목록 추가

    public static void loadFromJson() {
        try {
            Reader reader = new InputStreamReader(
                    DataStore.class.getResourceAsStream("/com/example/studyplanner/data/flowers.json"),
                    StandardCharsets.UTF_8
            );

            Type listType = new TypeToken<List<Flower>>() {}.getType();
            allFlowers = new Gson().fromJson(reader, listType);
            reader.close();

            System.out.println("✅ flowers.json 로드 완료! (" + allFlowers.size() + "개)");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ flowers.json 로드 실패!");
        }
    }
}