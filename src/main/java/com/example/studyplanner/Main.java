package com.example.studyplanner;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. 로그인 화면 로드 (언더바 _ 주의)
        URL fxmlUrl = getClass().getResource("/com/example/studyplanner/login-view.fxml");

        if (fxmlUrl == null) {
            System.err.println("❌ 오류: FXML 파일을 찾을 수 없습니다. 경로를 확인해주세요.");
            System.exit(1);
        }

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 720);

        stage.setTitle("Focus Garden");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}