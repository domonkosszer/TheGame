package gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import java.io.IOException;
import java.net.Socket;
import client.Client;

/**
 * Controller for the login scene.
 */
public class LoginController extends BaseController {
    private Client client;

    @FXML
    public void initialize() {
        try {
            Socket socket = new Socket("localhost", 2222);
            client = new Client(socket);
            client.setBaseController(this);
            client.listenForMessage();
            Platform.runLater(() -> sceneController.setClient(client));
        } catch (IOException e) {
            Platform.runLater(() -> client.reconnect());
        }

        String systemUsername = System.getProperty("user.name");
        usernameField.setText(systemUsername);
    }

    /**
     * Handles the login action.
     */
    @FXML
    private void handleLogin() throws IOException {
        String username = usernameField.getText().trim();
        if (!username.isEmpty()) {
            client.selectUsername(username);
        }
    }
}
