package gui;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;

/**
 * Controller for the intro scene.
 */
public class IntroController extends BaseController {

    @FXML
    private StackPane root;
    public ImageView imageView;

    @FXML
    public void initialize() {
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(event -> moveToMainScene());
        delay.play();

        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (Objects.requireNonNull(event.getCode()) == KeyCode.SPACE) {
                        delay.stop();
                        moveToMainScene();
                    }
                });
                root.requestFocus();
            }
        });
    }

    /**
     * Loads the next scene (e.g., the login scene).
     */
    private void moveToMainScene() {
        Stage introstage = (Stage) root.getScene().getWindow();
        introstage.close();

        Stage mainstage = new Stage();
        Scene mainScene = getLoginScene(mainstage);

        mainstage.setOnCloseRequest(event -> {
            System.exit(0);
        });

        mainstage.setFullScreen(true);
        mainstage.setFullScreenExitHint("");
        mainstage.setTitle("The Game");
        mainstage.setScene(mainScene);
        mainstage.initStyle(StageStyle.DECORATED);
        mainstage.show();
    }

    private static Scene getLoginScene(Stage mainstage) {
        Scene mainScene = new Scene(new Pane(), 800, 600);
        SceneController sceneController = new SceneController(mainScene);

        try {
            sceneController.loadScene("/fxml/login.fxml");
        } catch (IOException ignored) {}

        mainScene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F && !(mainScene.getFocusOwner() instanceof javafx.scene.control.TextInputControl)) {
                mainstage.setFullScreen(!mainstage.isFullScreen());
            }
        });
        return mainScene;
    }
}
