package gui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.io.IOException;

/**
 * Controller for the login scene.
 */
public class LoginController {
    private SceneController sceneController;

    @FXML
    private TextField usernameField;

    @FXML
    public void initialize() {
        String systemUsername = getSystemUsername();
        usernameField.setText(systemUsername);
    }

    /**
     * Handles the login action.
     */
    @FXML
    public void handleLogin() {

        String username = usernameField.getText();

        if (username != null && !username.trim().isEmpty()) {
            System.out.println("Username: " + username);
            try {
                sceneController.loadScene("/fxml/menu.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getSystemUsername() {
        return System.getProperty("user.name");
    }
}
