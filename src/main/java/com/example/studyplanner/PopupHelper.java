package com.example.studyplanner;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.net.URL;

public class PopupHelper {

    public static void showAutoPopup(String title, String message) {
        try {
            Stage popupStage = new Stage();
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.initStyle(StageStyle.TRANSPARENT);

            // 1. 배경 이미지
            ImageView bg = new ImageView();
            String imagePath = "/com/example/studyplanner/images/UI_TravelBook_Popup01a.png";
            URL imgUrl = PopupHelper.class.getResource(imagePath);

            if (imgUrl != null) {
                bg.setImage(new Image(imgUrl.toExternalForm()));
                bg.setFitWidth(300);
                bg.setPreserveRatio(true);
                bg.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.3)));
            } else {
                StackPane fallback = new StackPane();
                fallback.setStyle("-fx-background-color: #fdf5e6; -fx-border-color: #b08d74; -fx-border-width: 2; -fx-background-radius: 20; -fx-border-radius: 20;");
                fallback.setPrefSize(300, 180);
            }

            // 2. 내용물 (VBox)
            VBox content = new VBox(10);
            content.setAlignment(Pos.CENTER); // VBox 내부 요소 가운데 정렬

            // [제목 라벨]
            Label titleLbl = new Label(title);
            titleLbl.setAlignment(Pos.CENTER);
            titleLbl.setStyle("-fx-font-family: 'Georgia'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #5c3a21;");

            // [본문 라벨]
            Label msgLbl = new Label(message);
            msgLbl.setWrapText(true);           // 줄바꿈 허용
            msgLbl.setMaxWidth(240);            // 최대 너비 제한
            msgLbl.setAlignment(Pos.CENTER);    // 라벨 자체 정렬
            msgLbl.setTextAlignment(TextAlignment.CENTER); // 텍스트 내부 정렬 (중요!)
            // 폰트를 Georgia로 변경하고 크기를 키움
            msgLbl.setStyle("-fx-font-family: 'Georgia'; -fx-font-size: 14px; -fx-text-fill: #8d6e63;");

            // [확인 버튼]
            Button closeBtn = new Button("확인");
            closeBtn.setStyle("-fx-background-color: #b08d74; -fx-text-fill: white; -fx-font-family: 'Georgia'; -fx-font-size: 12px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 5 15 5 15;");

            closeBtn.setOnAction(e -> popupStage.close());

            content.getChildren().addAll(titleLbl, msgLbl, closeBtn);

            // 3. 합치기
            StackPane root = new StackPane();
            root.setStyle("-fx-background-color: transparent;");
            root.getChildren().addAll(bg, content);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            popupStage.setScene(scene);
            popupStage.show();

            // 4. 2초 뒤 자동 닫기
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(e -> {
                if (popupStage.isShowing()) {
                    popupStage.close();
                }
            });
            delay.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}