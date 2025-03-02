package gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;

public class ChangeUsernameController extends BaseController {

    @FXML
    public TextField usernameField;
    public ButtonType ApplyButton;
    public ButtonType CancelButton;

    @FXML
    public void initialize() {
        Platform.runLater(() -> usernameField.setText(client.getUsername()));
    }
}
