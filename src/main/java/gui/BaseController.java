package gui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import client.Client;

public abstract class BaseController {
    protected SceneController sceneController;
    protected Client client;

    @FXML
    public TextField usernameField;
    public PingBox pingBox;

    public void setSceneController(SceneController sceneController) {
        this.sceneController = sceneController;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setUsername(String username) {
        usernameField.setText(username);
    }

    public void setPing(int ping) {
        pingBox.setPing(ping);
    }

    public void switchScene(String fxmlPath) {
        try {
            sceneController.loadScene(fxmlPath);
        } catch (Exception ignored) {}
    }
}
