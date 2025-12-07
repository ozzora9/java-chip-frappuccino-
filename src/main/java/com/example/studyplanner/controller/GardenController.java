package com.example.studyplanner.controller;

import com.example.studyplanner.PopupHelper;
import com.example.studyplanner.dao.GardenDAO;
import com.example.studyplanner.manager.FlowerManager;
import com.example.studyplanner.manager.TimerManager; // ★ 추가됨
import com.example.studyplanner.manager.UserManager;
import com.example.studyplanner.model.DailyRecord;
import com.example.studyplanner.model.Flower;
import com.example.studyplanner.service.DatabaseService;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Optional;
import java.util.ResourceBundle;

public class GardenController implements Initializable {

    private final GardenDAO gardenDAO = new GardenDAO();
    private final DatabaseService dataService = new DatabaseService();

    @FXML private AnchorPane gardenArea;
    @FXML private VBox inventoryPanel;
    @FXML private FlowPane flowerFlowPane;
    @FXML private Button growthRateBtn;
    @FXML private Button inventoryBtn;

    private boolean isInventoryOpen = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        inventoryPanel.setTranslateY(300);
        inventoryPanel.setVisible(false);

        loadInventoryFromDB();
        setupGardenDragAndDrop();
        loadGardenFromDB();
    }

    // =========================================================================
    // 성장률 확인 버튼 핸들러
    // =========================================================================
    @FXML
    void handleGrowthRate(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("성장률 확인 선택");
        alert.setHeaderText("어떤 씨앗의 상태를 확인하시겠습니까?");ㄹ
        alert.setContentText("확인하고 싶은 대상을 선택해주세요.");

        ButtonType plannerBtn = new ButtonType("플래너 씨앗");
        ButtonType timerBtn = new ButtonType("타이머 씨앗");
        ButtonType cancelBtn = new ButtonType("취소", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(plannerBtn, timerBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == plannerBtn) {
                showPlannerGrowth();
            } else if (result.get() == timerBtn) {
                showTimerGrowth();
            }
        }
    }

    // --- [1] 플래너 성장률 ---
    private void showPlannerGrowth() {
        Integer plannerSeedId = UserManager.getInstance().getTodayPlannerSeedFlowerId();

        if (plannerSeedId == null || plannerSeedId == 0) {
            PopupHelper.showAutoPopup("씨앗 없음", "오늘 플래너 보상으로 받은 씨앗이 없어요!\n계획 3개를 작성해보세요.");
            return;
        }

        String userId = UserManager.getInstance().getUser().getUserId();
        DailyRecord record = dataService.loadDailyRecord(userId, LocalDate.now());

        int total = record.getSubjects().size();
        long doneCount = record.getSubjects().values().stream()
                .filter(DailyRecord.SubjectRecord::isDone).count();

        double percent = (total == 0) ? 0 : ((double) doneCount / total) * 100;

        int stage = calculateStage(percent);

        Flower f = FlowerManager.getInstance().getFlowerById(plannerSeedId);
        showGrowthPopupInternal(f, stage, percent, "플래너");
    }

    // --- [2] 타이머 성장률 (수정됨: TimerManager와 동기화) ---
    private void showTimerGrowth() {
        Integer timerSeedId = UserManager.getInstance().getTodayTimerSeedFlowerId();

        if (timerSeedId == null || timerSeedId == 0) {
            PopupHelper.showAutoPopup("씨앗 없음", "오늘 타이머 보상으로 받은 씨앗이 없어요!\n목표 시간을 설정해보세요.");
            return;
        }

        String userId = UserManager.getInstance().getUser().getUserId();
        DailyRecord record = dataService.loadDailyRecord(userId, LocalDate.now());
        long goal = record.getDailyGoalSeconds();

        // 1. DB에 저장된 시간 합계
        long dbTotal = 0;
        for (DailyRecord.SubjectRecord sr : record.getSubjects().values()) {
            dbTotal += sr.getStudiedSeconds();
        }

        // 2. 현재 실행 중인 TimerManager의 시간
        long managerTotal = TimerManager.getInstance().getDailyTotalSeconds();

        // ★ [핵심] DB값과 Manager값 중 더 큰(최신) 값을 사용
        long finalTotalSeconds = Math.max(dbTotal, managerTotal);

        double percent = 0;
        if (goal > 0) {
            percent = ((double) finalTotalSeconds / goal) * 100;
        }
        if (percent > 100) percent = 100;

        int stage = calculateStage(percent);

        Flower f = FlowerManager.getInstance().getFlowerById(timerSeedId);
        showGrowthPopupInternal(f, stage, percent, "타이머");
    }

    // ★ [수정됨] 단계 계산 기준 변경 (33%, 66%, 100%)
    private int calculateStage(double percent) {
        if (percent < 33) return 1;       // 씨앗 (0~32%)
        else if (percent < 66) return 2;  // 새싹 (33~65%)
        else if (percent < 100) return 3; // 꽃봉오리 (66~99%)
        else return 4;                    // 개화 (100%)
    }

    // --- 팝업 내용 구성 ---
    private void showGrowthPopupInternal(Flower f, int stage, double percent, String type) {
        String stageName;
        String imgPath;
        String desc;

        if (stage >= 4) {
            stageName = "개화 (Bloom)";
            imgPath = f.getBloomIcon();
            desc = f.getName() + "이(가) 활짝 피었습니다! 🌸";
        } else if (stage == 3) {
            stageName = "꽃봉오리 (Bud)";
            imgPath = f.getGrowIcon();
            desc = "꽃봉오리가 맺혔습니다! (" + type + ")";
        } else if (stage == 2) {
            stageName = "새싹 (Sprout)";
            imgPath = f.getSproutIcon();
            desc = "파릇파릇한 새싹이 돋아났습니다. (" + type + ")";
        } else {
            stageName = "씨앗 (Seed)";
            imgPath = f.getSeedIcon();
            desc = "아직 씨앗 상태입니다. (" + type + ")";
        }

        showGrowthPopup(f.getName(), stageName, percent, imgPath, desc);
    }

    private void showGrowthPopup(String flowerName, String stageName, double percent, String imgPath, String description) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("성장률 확인 🌱");

        // 제목에 꽃 이름과 진행률 표시
        alert.setHeaderText(flowerName + ": " + stageName + " (" + (int) percent + "%)");

        alert.setContentText(description);
        alert.initStyle(StageStyle.UTILITY);

        try {
            Image image = new Image(getClass().getResource(imgPath).toExternalForm());
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(100);
            imageView.setFitWidth(100);
            imageView.setPreserveRatio(true);
            alert.setGraphic(imageView);
        } catch (Exception e) {
            System.out.println("❌ 이미지를 찾을 수 없습니다: " + imgPath);
        }
        alert.showAndWait();
    }

    // =========================================================================
    // (기존 코드들 유지)
    // =========================================================================

    private void loadInventoryFromDB() {
        flowerFlowPane.getChildren().clear();
        for (Flower f : FlowerManager.getInstance().getCatalog()) {
            if (f.getFlowerQty() > 0) {
                flowerFlowPane.getChildren().add(createFlowerCard(f));
            }
        }
    }

    private void loadGardenFromDB() {
        var user = UserManager.getInstance().getUser();
        if (user == null) return;

        String userId = user.getUserId();
        var items = gardenDAO.loadGarden(userId);

        for (var item : items) {
            Flower f = FlowerManager.getInstance().getFlowerById(item.flowerId);
            if (f == null) continue;
            placeExistingFlower(item.layoutId, f, item.x, item.y);
        }
    }

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
                try {
                    int draggedFlowerId = Integer.parseInt(db.getString());
                    Flower f = FlowerManager.getInstance().getFlowerById(draggedFlowerId);
                    if (f != null && f.getFlowerQty() > 0) {
                        placeFlowerInGarden(f, event.getX(), event.getY());
                        useFlower(f);
                        success = true;
                    }
                } catch (NumberFormatException e) { e.printStackTrace(); }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void useFlower(Flower f) {
        String userId = UserManager.getInstance().getUser().getUserId();
        GardenDAO.getInstance().useFlower(userId, f.getId());
        f.setFlowerQty(Math.max(0, f.getFlowerQty() - 1));
        loadInventoryFromDB();
    }

    private void placeFlowerInGarden(Flower f, double x, double y) {
        String userId = UserManager.getInstance().getUser().getUserId();
        int layoutId = GardenDAO.getInstance().insertFlower(userId, f.getId(), x, y);
        if (layoutId == -1) return;
        ImageView flowerView = createFlowerImageView(f, layoutId);
        flowerView.setLayoutX(x - 30);
        flowerView.setLayoutY(y - 30);
        gardenArea.getChildren().add(flowerView);
    }

    private void placeExistingFlower(int layoutId, Flower f, double x, double y) {
        ImageView flowerView = createFlowerImageView(f, layoutId);
        flowerView.setLayoutX(x);
        flowerView.setLayoutY(y);
        gardenArea.getChildren().add(flowerView);
    }

    private ImageView createFlowerImageView(Flower f, int layoutId) {
        ImageView flowerView = new ImageView();
        try {
            flowerView.setImage(new Image(getClass().getResource(f.getImagePath()).toExternalForm()));
        } catch (Exception e) { }
        flowerView.setFitWidth(60);
        flowerView.setFitHeight(60);
        flowerView.setPreserveRatio(true);
        flowerView.getProperties().put("layoutId", layoutId);
        setupGardenFlowerInteractions(flowerView);
        return flowerView;
    }

    private void setupGardenFlowerInteractions(ImageView flowerView) {
        final double[] dragDelta = new double[2];
        flowerView.setOnMousePressed(event -> {
            dragDelta[0] = flowerView.getLayoutX() - event.getSceneX();
            dragDelta[1] = flowerView.getLayoutY() - event.getSceneY();
        });
        flowerView.setOnMouseDragged(event -> {
            flowerView.setLayoutX(event.getSceneX() + dragDelta[0]);
            flowerView.setLayoutY(event.getSceneY() + dragDelta[1]);
        });
        flowerView.setOnMouseReleased(event -> {
            GardenDAO.getInstance().updateFlowerPosition((int) flowerView.getProperties().get("layoutId"), flowerView.getLayoutX(), flowerView.getLayoutY());
        });
        ContextMenu menu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("이 꽃 삭제하기");
        deleteItem.setOnAction(e -> {
            gardenArea.getChildren().remove(flowerView);
            GardenDAO.getInstance().deleteFlower((int) flowerView.getProperties().get("layoutId"));
        });
        menu.getItems().add(deleteItem);
        flowerView.setOnContextMenuRequested(e -> menu.show(flowerView, e.getScreenX(), e.getScreenY()));
    }

    private StackPane createFlowerCard(Flower f) {
        VBox cardLayout = new VBox(10);
        cardLayout.getStyleClass().add("flower-card");
        cardLayout.setAlignment(Pos.CENTER);
        cardLayout.setPrefSize(100, 120);

        ImageView flowerImg = new ImageView();
        try { flowerImg.setImage(new Image(getClass().getResource(f.getImagePath()).toExternalForm())); } catch (Exception e) {}
        flowerImg.setFitHeight(80); flowerImg.setFitWidth(80); flowerImg.setPreserveRatio(true);

        Label nameLbl = new Label(f.getName() + " (x" + f.getFlowerQty() + ")");
        nameLbl.getStyleClass().add("flower-name");
        cardLayout.getChildren().addAll(flowerImg, nameLbl);

        StackPane finalCard = new StackPane(cardLayout);
        finalCard.getStyleClass().add("flower-card-container");
        finalCard.setOnDragDetected(event -> {
            if (!f.isCardUnlocked() || f.getFlowerQty() <= 0) return;
            Dragboard db = finalCard.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(f.getId()));
            db.setDragView(flowerImg.getImage());
            db.setContent(content);
            event.consume();
        });
        return finalCard;
    }

    @FXML void navGarden(ActionEvent event) { switchScene(event, "garden-view.fxml"); }
    @FXML void navPlanner(ActionEvent event) { switchScene(event, "planner-view.fxml"); }
    @FXML void navTimer(ActionEvent event) { switchScene(event, "timer-view.fxml"); }
    @FXML void navBook(ActionEvent event) { switchScene(event, "collection-view.fxml"); }

    private void switchScene(ActionEvent event, String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlFileName);
            if (fxmlUrl == null) fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Parent root = fxmlLoader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }
}