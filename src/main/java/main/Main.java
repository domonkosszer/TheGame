package main;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import gui.SceneController;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * The main entry point for all JavaFX applications.
     * The start method is called after the init method has returned,
     * and after the system is ready for the application to begin running.
     *
     * <p>
     * NOTE: This method is called on the JavaFX Application Thread.
     * </p>
     *
     * @param primaryStage the primary stage for this application, onto which
     *                     the application scene can be set.
     *                     Applications may create other stages, if needed, but they will not be
     *                     primary stages.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene introScene = new Scene(new javafx.scene.layout.Pane());
        introScene.setFill(Color.TRANSPARENT);

        SceneController sceneController = new SceneController(introScene);
        sceneController.loadScene("/fxml/intro.fxml");

        primaryStage.setScene(introScene);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.show();
    }

    /**
     * This method is called when the application should stop,
     * and provides a convenient place to prepare for application exit and destroy resources.
     * The implementation of this method provided by the Application class does nothing.
     *
     * <p>
     * NOTE: This method is called on the JavaFX Application Thread.
     * </p>
     *
     * @throws Exception if something goes wrong
     */
    @Override
    public void stop() throws Exception{
        //shutdownEverything();
    }
}
