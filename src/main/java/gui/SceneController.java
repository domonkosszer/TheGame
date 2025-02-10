package gui;

import java.util.HashMap;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;

public class SceneController {
    private HashMap<String, Pane> screenMap = new HashMap<>();
    private Scene main;

    public SceneController(Scene main) {
        this.main = main;
    }

    protected void addScene(String name, Pane pane){
        screenMap.put(name, pane);
    }

    protected void removeScene(String name){
        screenMap.remove(name);
    }

    protected void activate(String name){
        main.setRoot( screenMap.get(name) );
    }
}