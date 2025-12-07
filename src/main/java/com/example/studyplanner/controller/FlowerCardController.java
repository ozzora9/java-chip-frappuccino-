package com.example.studyplanner.controller;

import com.example.studyplanner.model.Flower;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.net.URL;

public class FlowerCardController {

    @FXML private ImageView flowerImage;
    @FXML private Label flowerName;
    @FXML private Label flowerMeaning;

    @FXML private ImageView seedStage;
    @FXML private ImageView sproutStage;
    @FXML private ImageView growStage;
    @FXML private ImageView bloomStage;

    public void setData(Flower flower) {
        flowerName.setText(flower.getName());
        flowerMeaning.setText(flower.getMeaning());

        // ★ [수정됨] 안전한 이미지 로딩 메서드 사용
        safeLoadImage(flowerImage, flower.getImagePath());

        safeLoadImage(seedStage, flower.getSeedIcon());
        safeLoadImage(sproutStage, flower.getSproutIcon());
        safeLoadImage(growStage, flower.getGrowIcon());
        safeLoadImage(bloomStage, flower.getBloomIcon());
    }

    // ★ 이미지가 없어도 에러를 띄우지 않고 콘솔에만 로그를 남기는 메서드
    private void safeLoadImage(ImageView view, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        // 경로에서 이미지 URL 찾기
        URL url = getClass().getResource(path);

        if (url != null) {
            view.setImage(new Image(url.toExternalForm()));
        } else {
            // 파일을 못 찾았을 때
            System.err.println("❌ [Card] 이미지 파일 없음: " + path);
            // 필요하다면 view.setImage(null);
        }
    }
}