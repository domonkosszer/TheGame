package gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import java.io.IOException;

import client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SceneController {
    private final Scene mainScene;
    private Client client;
    private final Logger logger = LoggerFactory.getLogger(SceneController.class);

    public SceneController(Scene mainScene) {
        this.mainScene = mainScene;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void loadScene(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Pane newRoot = loader.load();
        mainScene.setRoot(newRoot);

        Object controller = loader.getController();

        if (controller instanceof BaseController baseController) {
            baseController.setSceneController(this);
            if (client != null) {
                baseController.setClient(client);
                client.setBaseController(baseController);
            }
        }
    }
}
