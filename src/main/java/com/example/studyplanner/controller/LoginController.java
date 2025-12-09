package com.example.studyplanner.controller;

import com.example.studyplanner.manager.FlowerManager;
import com.example.studyplanner.manager.UserManager;
import com.example.studyplanner.model.UserSession;

import com.example.studyplanner.service.DatabaseService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class LoginController {

    @FXML private TextField idField;
    @FXML private PasswordField pwField;
    @FXML private Button loginButton;

    // DB 서비스 연결
    private final DatabaseService dataService = new DatabaseService();

    @FXML
    public void handleLogin(ActionEvent event) {
        String id = idField.getText();
        String pw = pwField.getText();

        // 1. 빈칸 검사
        if (id.isEmpty() || pw.isEmpty()) {
            showAlert("로그인 실패", "아이디와 비밀번호를 모두 입력해주세요.");
            return;
        }

        // 2. DB에서 로그인 검증 (성공 시 닉네임 반환, 실패 시 null)
        String nickname = dataService.loginUser(id, pw);

        if (nickname != null) {
            // 3. 로그인 성공! -> 앱 전체에서 쓸 수 있게 세션에 저장
            UserSession.getInstance().login(id, nickname);
            UserManager.initialize(dataService, id);
            DatabaseService db = new DatabaseService();
            db.initFlowerInventory(id);
            FlowerManager.getInstance().loadInventoryFromDB(id);

            // ★ [추가] 로그인 직후, 오늘 공부한 시간을 TimerManager에 로드
            com.example.studyplanner.manager.TimerManager.getInstance().loadDailyTotalFromDB();


            System.out.println("✅ 로그인 성공: " + id + " (" + nickname + ")");
            // 4. 정원(홈) 화면으로 이동
            moveScene("garden-view.fxml");
        } else {
            showAlert("로그인 실패", "아이디 또는 비밀번호가 일치하지 않습니다.");
        }
    }
// 변경변경!!!
    @FXML
    public void handleSignUp(ActionEvent event) {
        // 회원가입 화면으로 이동
        moveScene("signup-view.fxml");
    }

    // 화면 전환 헬퍼 메소드
    private void moveScene(String fxmlFileName) {
        try {
            // 경로 찾기 (패키지 구조에 따라 경로가 다를 수 있어 안전장치 추가)
            URL fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);

            if (fxmlUrl == null) {
                // 혹시라도 경로가 다르면 현재 클래스 기준으로 찾기
                fxmlUrl = getClass().getResource(fxmlFileName);
            }

            if (fxmlUrl == null) {
                showAlert("오류", "화면 파일을 찾을 수 없습니다: " + fxmlFileName);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            // 현재 스테이지 가져오기
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // 화면 크기 유지 및 변경
            Scene scene = new Scene(root, 1200, 720);
            stage.setScene(scene);
            stage.centerOnScreen(); // 화면 중앙 정렬
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("시스템 오류", "화면 이동 중 오류가 발생했습니다.\n" + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}