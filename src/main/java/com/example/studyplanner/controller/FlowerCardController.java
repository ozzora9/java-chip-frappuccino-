package com.example.studyplanner.controller;

import com.example.studyplanner.model.Flower;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class FlowerCardController {

    @FXML private ImageView flowerImage;
    @FXML private Label flowerName;
    @FXML private Label flowerMeaning;

    @FXML private ImageView seedStage;
    @FXML private ImageView sproutStage;
    @FXML private ImageView growStage;
    @FXML private ImageView bloomStage;

    public void setData(Flower flower) {

        // 디버그 출력
        System.out.println("flower = " + flower.getName());
        System.out.println("image = " + getClass().getResource(flower.getImagePath()));
        System.out.println("seed = " + getClass().getResource(flower.getSeedIcon()));
        System.out.println("sprout = " + getClass().getResource(flower.getSproutIcon()));
        System.out.println("grow = " + getClass().getResource(flower.getGrowIcon()));
        System.out.println("bloom = " + getClass().getResource(flower.getBloomIcon()));

        // 이미지 로딩 (반드시 getResource + toExternalForm)
        flowerImage.setImage(
                new Image(getClass().getResource(flower.getImagePath()).toExternalForm())
        );
        flowerName.setText(flower.getName());
        flowerMeaning.setText(flower.getMeaning());

        seedStage.setImage(
                new Image(getClass().getResource(flower.getSeedIcon()).toExternalForm())
        );
        sproutStage.setImage(
                new Image(getClass().getResource(flower.getSproutIcon()).toExternalForm())
        );
        growStage.setImage(
                new Image(getClass().getResource(flower.getGrowIcon()).toExternalForm())
        );
        bloomStage.setImage(
                new Image(getClass().getResource(flower.getBloomIcon()).toExternalForm())
        );
    }

}
