package com.example.studyplanner.controller;

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

public class SignUpController {

    @FXML private TextField newIdField;
    @FXML private TextField emailField; // 여기서는 닉네임 용도로 사용
    @FXML private PasswordField newPwField;
    @FXML private Button signUpButton;

    // DB 서비스 연결
    private final DatabaseService dataService = new DatabaseService();

    @FXML
    public void handleSignUpProcess(ActionEvent event) {
        String id = newIdField.getText();
        String nickname = emailField.getText(); // FXML id가 emailField라서 변수명 유지 (실제론 닉네임)
        String pw = newPwField.getText();

        // 1. 빈칸 검사
        if (id.isEmpty() || nickname.isEmpty() || pw.isEmpty()) {
            showAlert("알림", "모든 필드를 입력해주세요.");
            return;
        }

        // 2. DB에 회원가입 요청
        boolean isSuccess = dataService.registerUser(id, pw, nickname);

        if (isSuccess) {
            showAlert("환영합니다", "회원가입이 완료되었습니다!\n로그인 해주세요.");

            // 성공 시 로그인 화면으로 이동
            moveScene("login-view.fxml");
        } else {
            showAlert("가입 실패", "이미 존재하는 아이디이거나, 시스템 오류입니다.");
        }
    }

    @FXML
    public void handleBackToLogin(ActionEvent event) {
        // 로그인 화면으로 돌아가기
        moveScene("login-view.fxml");
    }

    // 화면 전환 헬퍼 메소드
    private void moveScene(String fxmlFileName) {
        try {
            URL fxmlUrl = getClass().getResource("/com/example/studyplanner/" + fxmlFileName);

            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource(fxmlFileName);
            }

            if (fxmlUrl == null) {
                showAlert("오류", "화면 파일을 찾을 수 없습니다: " + fxmlFileName);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage stage = (Stage) signUpButton.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 720);

            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("오류", "화면 이동 실패: " + e.getMessage());
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