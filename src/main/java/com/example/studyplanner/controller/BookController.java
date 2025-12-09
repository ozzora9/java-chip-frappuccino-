package com.example.studyplanner.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;

public class BookController {

    @FXML
    public void initialize() {
        // 정원 화면 초기화 (테이블 코드 없음!)
    }
// 푸시푸시
    // 네비게이션
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}