package gui;

import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class MenuController extends BaseController {

    @FXML
    public HBox usernameHBox;
    public Label usernameLabel;
    public GridPane gridPane;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            usernameLabel.setText(client.getUsername());
            client.setPing();
        });
    }

    public void changeUsername() throws IOException {
        sceneController.loadScene("/fxml/changeUsername.fxml");
    }

    public void handleLobby() {
    }

    public void handlePlayers() {
    }

    public void handleTutorial() {
    }

    public void handleSettings() {
    }

    public void handleCredits() {
    }

    public void handleQuit() {
        client.quit();
    }
}
