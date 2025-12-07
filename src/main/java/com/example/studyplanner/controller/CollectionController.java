package com.example.studyplanner.controller;

import com.example.studyplanner.manager.FlowerManager;
import com.example.studyplanner.model.Flower;
import javafx.event.ActionEvent;
import javafx.fxml.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class CollectionController {

    @FXML private TilePane itemGrid;
    @FXML private VBox detailContent;

    @FXML
    public void initialize() {
        renderItemGrid();
    }

    private void renderItemGrid() {
        itemGrid.getChildren().clear();

        for (Flower f : FlowerManager.getInstance().getCatalog()) {
            // 슬롯 배경
            ImageView slotBg = new ImageView();
            try {
                slotBg.setImage(new Image(Objects.requireNonNull(getClass().getResource(
                        "/com/example/studyplanner/images/UI_TravelBook_Slot01b.png")).toExternalForm()));
            } catch (Exception e) {
                System.err.println("❌ 슬롯 이미지 로드 실패");
            }
            slotBg.setFitWidth(72);
            slotBg.setFitHeight(72);

            // ★ [수정됨] 꽃 이미지 안전하게 로드
            ImageView img = new ImageView();
            String imgPath = f.getImagePath();
            URL imgUrl = getClass().getResource(imgPath);

            if (imgUrl != null) {
                img.setImage(new Image(imgUrl.toExternalForm()));
            } else {
                System.err.println("❌ [Collection] 이미지 없음: " + imgPath);
                // 이미지가 없을 때 보여줄 빈 투명 이미지나 대체 이미지 설정 가능
            }

            img.setFitWidth(48);
            img.setFitHeight(48);

            StackPane stack = new StackPane(slotBg, img);
            stack.setAlignment(Pos.CENTER);

            // 🔒 잠겨있다면 효과 적용
            if (!f.isSeedUnlocked()) {
                ColorAdjust darken = new ColorAdjust();
                darken.setBrightness(-0.6);
                img.setEffect(darken);
                img.setOpacity(0.4);

                ImageView lock = new ImageView();
                try {
                    lock.setImage(new Image(Objects.requireNonNull(getClass().getResource(
                            "/com/example/studyplanner/images/lock.png")).toExternalForm()));
                } catch (Exception e) {
                    // lock 이미지가 없어도 넘어가도록 처리
                }
                lock.setFitWidth(26);
                lock.setFitHeight(26);
                StackPane.setAlignment(lock, Pos.CENTER);
                stack.getChildren().add(lock);
            }

            stack.setOnMouseClicked(ev -> showDetailCard(f));

            itemGrid.getChildren().add(stack);
        }
    }


    private void showDetailCard(Flower f) {
        detailContent.getChildren().clear();

        // 잠김 카드라면 → 간단한 잠김 UI 표시
        if (!f.isCardUnlocked()) {
            VBox lockedBox = new VBox(10);
            lockedBox.setStyle("-fx-padding: 20;");
            Label locked = new Label("아직 발견되지 않은 꽃입니다.");
            locked.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
            lockedBox.getChildren().add(locked);
            detailContent.getChildren().add(lockedBox);
            return;
        }

        try {
            // FlowerCard.fxml 로드
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/studyplanner/flower/FlowerCard.fxml")
            );

            Parent card = loader.load();

            // 컨트롤러 연결
            FlowerCardController controller = loader.getController();
            controller.setData(f);

            // 오른쪽 detailContent에 카드 표시
            detailContent.getChildren().setAll(card);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @FXML void navGarden(ActionEvent event) { switchScene(event, "garden-view.fxml"); }
    @FXML void navPlanner(ActionEvent event) { switchScene(event, "planner-view.fxml"); }
    @FXML void navTimer(ActionEvent event) { switchScene(event, "timer-view.fxml"); }
    @FXML void navBook(ActionEvent event) { switchScene(event, "collection-view.fxml"); }

    private void switchScene(ActionEvent event, String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlFileName);
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);
            }
            if (fxmlUrl == null) return; // 파일 없으면 무시

            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Parent root = fxmlLoader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}