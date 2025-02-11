package gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

import java.io.IOException;

/**
 * A controller for managing and dynamically switching between JavaFX scenes.
 */
public class SceneController {
    private final Scene mainScene;

    public SceneController(Scene mainScene) {
        this.mainScene = mainScene;
    }

    public void loadScene(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Pane pane = loader.load();

        mainScene.setRoot(pane);
    }
}
