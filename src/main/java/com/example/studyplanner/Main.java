package com.example.studyplanner;

import com.example.studyplanner.manager.TimerManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/example/studyplanner/login-view.fxml");
        if (fxmlUrl == null) {
            System.err.println("❌ 오류: FXML 파일을 찾을 수 없습니다.");
            System.exit(1);
        }
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 720);
        stage.setTitle("Focus Garden");
        stage.setScene(scene);
        stage.show();
    }

    // ★ [추가됨] 앱이 종료될 때 호출되는 메서드
    @Override
    public void stop() throws Exception {
        System.out.println("🛑 애플리케이션 종료 중...");
        // 타이머가 돌고 있었다면 저장하도록 요청
        TimerManager.getInstance().handleAppShutdown();
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}