package com.example.studyplanner.controller;

import com.example.studyplanner.PopupHelper;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class GardenController implements Initializable {

    @FXML private AnchorPane gardenArea;
    @FXML private VBox inventoryPanel;
    @FXML private FlowPane flowerFlowPane;
    @FXML private Button growthRateBtn;
    @FXML private Button inventoryBtn;

    private boolean isInventoryOpen = false;

    // ★ [테스트용] 현재 타이머 진행률 (나중에는 TimerController나 DB에서 받아와야 함)
    private double currentProgressPercent = 35.0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 초기 상태: 보관함 닫힘
        inventoryPanel.setTranslateY(300);
        inventoryPanel.setVisible(false);

        // 테스트용 더미 데이터 로드
        loadDummyInventory();

        // 정원 드래그 앤 드롭 설정
        setupGardenDragAndDrop();
    }

    // =========================================================================
    // ★ [수정됨] 1. 씨앗 성장률 버튼 클릭 (이미지 팝업 기능 추가)
    // =========================================================================
    @FXML
    void handleGrowthRate(ActionEvent event) {
        // 1. 진행률에 따른 단계 및 이미지 결정
        String stageName;
        String imagePath;
        String desc;

        if (currentProgressPercent >= 100) {
            stageName = "개화 (Bloom)";
            imagePath = "rose/blood.png"; // 요청하신 파일명
            desc = "축하합니다! 아름다운 꽃이 피었습니다. 🌹";
        } else if (currentProgressPercent >= 60) {
            stageName = "꽃봉오리 (Bud)";
            imagePath = "rose/grow.png";
            desc = "꽃봉오리가 맺혔습니다. 조금만 더 힘내세요!";
        } else if (currentProgressPercent >= 30) {
            stageName = "새싹 (Sprout)";
            imagePath = "rose/sprout.png";
            desc = "파릇파릇한 새싹이 돋아났습니다.";
        } else {
            stageName = "씨앗 (Seed)";
            imagePath = "rose/seed.png";
            desc = "아직은 씨앗 상태입니다. 물(공부)을 주세요!";
        }

        // 2. 이미지 포함된 팝업 띄우기
        showGrowthPopup(stageName, currentProgressPercent, imagePath, desc);
    }

    // 성장률 팝업 전용 메서드
    private void showGrowthPopup(String stage, double percent, String imgName, String description) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("성장률 확인 🌱");
        alert.setHeaderText("현재 단계: " + stage + " (" + (int)percent + "%)");
        alert.setContentText(description);

        // 다이얼로그 스타일 (옵션)
        alert.initStyle(StageStyle.UTILITY);

        // 이미지 로드 및 설정
        try {
            // 경로: /com/example/studyplanner/images/rose/파일명
            String fullPath = "/com/example/studyplanner/images/" + imgName;
            Image image = new Image(getClass().getResource(fullPath).toExternalForm());

            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(100); // 이미지 크기 조절
            imageView.setFitWidth(100);
            imageView.setPreserveRatio(true);

            alert.setGraphic(imageView); // 알림창 왼쪽에 이미지 배치
        } catch (Exception e) {
            System.out.println("❌ 이미지를 찾을 수 없습니다: " + imgName);
        }

        alert.showAndWait();
    }

    // =========================================================================
    // 기존 기능들 (보관함, 드래그앤드롭 등)
    // =========================================================================

    @FXML
    void handleToggleInventory(ActionEvent event) {
        isInventoryOpen = !isInventoryOpen;
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), inventoryPanel);
        if (isInventoryOpen) {
            inventoryPanel.setVisible(true);
            transition.setToY(0);
        } else {
            transition.setToY(300);
            transition.setOnFinished(e -> inventoryPanel.setVisible(false));
        }
        transition.play();
    }

    private void setupGardenDragAndDrop() {
        gardenArea.setOnDragOver(event -> {
            if (event.getGestureSource() != gardenArea && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        gardenArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                placeFlowerInGarden(db.getString(), event.getX(), event.getY());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void placeFlowerInGarden(String imageName, double x, double y) {
        ImageView newFlower = new ImageView();
        try {
            newFlower.setImage(new Image(getClass().getResource("/com/example/studyplanner/images/" + imageName).toExternalForm()));
        } catch (Exception e) { return; }
        double size = 60;
        newFlower.setFitWidth(size);
        newFlower.setFitHeight(size);
        newFlower.setPreserveRatio(true);
        newFlower.setLayoutX(x - (size / 2));
        newFlower.setLayoutY(y - (size / 2));
        gardenArea.getChildren().add(newFlower);
    }

    private void loadDummyInventory() {
        // 이미지 경로 수정: @images/rose.jpg -> flower_rose.png 등으로 실제 파일명에 맞게 사용 필요
        // 예시로 기존 로직 유지
        flowerFlowPane.getChildren().add(createFlowerCard("장미", "flower_rose.png", 5));
        flowerFlowPane.getChildren().add(createFlowerCard("튤립", "flower_tulip.png", 3));
        flowerFlowPane.getChildren().add(createFlowerCard("해바라기", "flower_sunflower.png", 8));
        flowerFlowPane.getChildren().add(createFlowerCard("장미", "flower_rose.png", 2));
    }

    private StackPane createFlowerCard(String name, String imageName, int count) {
        VBox cardLayout = new VBox(10);
        cardLayout.getStyleClass().add("flower-card");
        cardLayout.setAlignment(Pos.CENTER);

        ImageView flowerImg = new ImageView();
        try {
            flowerImg.setImage(new Image(getClass().getResource("/com/example/studyplanner/images/" + imageName).toExternalForm()));
        } catch (Exception e) { }
        flowerImg.setFitHeight(80);
        flowerImg.setFitWidth(80);
        flowerImg.setPreserveRatio(true);

        Label nameLbl = new Label(name + " (x" + count + ")");
        nameLbl.getStyleClass().add("flower-name");

        cardLayout.getChildren().addAll(flowerImg, nameLbl);

        HBox actionsBox = new HBox(15);
        actionsBox.getStyleClass().add("card-actions");
        actionsBox.setAlignment(Pos.CENTER);
        actionsBox.setPrefSize(120, 150);
        actionsBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Button placeBtn = createActionButton("btn_place.png", "정원에 배치하기");
        placeBtn.setOnAction(e -> {
            handlePlaceFlower(name);
            handleToggleInventory(null);
        });

        Button mergeBtn = createActionButton("btn_merge.png", "같은 꽃 3개 모으기 (합성)");
        mergeBtn.setOnAction(e -> handleMergeFlowers(name));

        actionsBox.getChildren().addAll(placeBtn, mergeBtn);

        StackPane finalCard = new StackPane(cardLayout, actionsBox);
        finalCard.getStyleClass().add("flower-card-container");

        finalCard.setOnDragDetected(event -> {
            Dragboard db = finalCard.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(imageName);
            db.setDragView(flowerImg.getImage());
            db.setContent(content);
            event.consume();
        });

        return finalCard;
    }

    private Button createActionButton(String iconName, String tooltipText) {
        Button btn = new Button();
        btn.getStyleClass().add("action-btn");
        try {
            ImageView icon = new ImageView(new Image(getClass().getResource("/com/example/studyplanner/images/" + iconName).toExternalForm()));
            icon.setFitHeight(24);
            icon.setFitWidth(24);
            btn.setGraphic(icon);
        } catch (Exception e) { btn.setText("●"); }
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }

    private void handlePlaceFlower(String flowerName) {
        PopupHelper.showAutoPopup("배치 모드", flowerName + " 배치 (드래그하여 배치하세요)");
    }

    private void handleMergeFlowers(String flowerName) {
        PopupHelper.showAutoPopup("꽃 모으기", flowerName + " 3개를 합쳐서 상위 꽃을 만듭니다!");
    }

    // --- 네비게이션 ---
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