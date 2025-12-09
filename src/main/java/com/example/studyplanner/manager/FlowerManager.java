package com.example.studyplanner.manager;

import com.example.studyplanner.dao.FlowerDAO;
import com.example.studyplanner.dao.FlowerInventoryData;
import com.example.studyplanner.model.Flower;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.IntStream;

public class FlowerManager {
    private static FlowerManager instance;

    private List<Flower> catalog;    // JSON 정보
    private FlowerDAO dao = new FlowerDAO();

    private FlowerManager() {
        System.out.println("🔥 FlowerManager 생성됨!");
        loadCatalogFromJson();
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
    public void loadInventoryFromDB(String userId) {
        Map<Integer, FlowerInventoryData> map = dao.getAllInventory(userId);

        for (Flower f : catalog) {
            FlowerInventoryData inv = map.get(f.getId());

            if (inv != null) {

                // 🌱 DB → Flower 객체 싱크
                f.setSeedQty(inv.seedQty);
                f.setFlowerQty(inv.flowerQty);
                f.setSeedUnlocked(inv.seedUnlocked);
                f.setCardUnlocked(inv.cardUnlocked);

                System.out.println(
                        "[SYNC] " + f.getName() +
                                " | seedQty=" + inv.seedQty +
                                ", flowerQty=" + inv.flowerQty +
                                ", seedUnlocked=" + inv.seedUnlocked +
                                ", cardUnlocked=" + inv.cardUnlocked
                );
            } else {
                System.out.println("[NO RECORD] flowerId=" + f.getId());
            }

        }

        System.out.println("🌱 인벤토리 DB → Flower 객체 동기화 완료");

    }

    public void giveSeed(int flowerId, int amount) {

        String userId = UserManager.getInstance().getUser().getUserId();

        dao.addSeed(userId, flowerId, amount);  // DB 증가

        for (Flower f : catalog) {
            if (f.getId() == flowerId) {
                f.setSeedQty(f.getSeedQty() + amount);
                f.setSeedUnlocked(true);
                break;
            }
        }

        System.out.println("🌱 씨앗 지급 완료: +" + amount + " (flowerId=" + flowerId + ")");
    }


    public void unlockCard(int flowerId) {
        String userId = UserManager.getInstance().getUser().getUserId();

        // 1) DB 업데이트
        dao.unlockCard(userId, flowerId);

        // 2) Flower 객체에도 반영
        for (Flower f : catalog) {
            if (f.getId() == flowerId) {
                f.setCardUnlocked(true);
                break;
            }
        }

        System.out.println("🌺 카드 해금 완료 (flowerId=" + flowerId + ")");
    }



    public int giveRandomSeed() {
        int[] pool = catalog.stream()
                .mapToInt(Flower::getId)
                .toArray();


        int randomId = pool[new Random().nextInt(pool.length)];

        giveSeed(randomId, 1);

        System.out.println("🎁 랜덤 씨앗 지급! → flowerId=" + randomId);

        return randomId;
    }

    public void addFlowerCount(int flowerId, int amount) {
        String userId = UserManager.getInstance().getUser().getUserId();

        dao.addFlower(userId, flowerId, amount);

        for (Flower f : catalog) {
            if (f.getId() == flowerId) {
                f.setFlowerQty(f.getFlowerQty() + amount);
                break;
            }
        }
    }




    public Flower getFlowerById(int flowerId) {
        for (Flower f : catalog) {
            if (f.getId() == flowerId) return f;
        }
        return null;
    }

    public List<Flower> getCatalog() {
        return catalog;
    }
}
