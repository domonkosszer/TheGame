package gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class PingBox extends HBox {
    private final Rectangle bar1;
    private final Rectangle bar2;
    private final Rectangle bar3;
    private final Rectangle bar4;
    private final Label pingLabel;
    private int currentPing;

    public PingBox() {
        bar1 = new Rectangle(5, 5);
        bar1.setOpacity(0);
        bar2 = new Rectangle(5, 10);
        bar2.setOpacity(0);
        bar3 = new Rectangle(5, 15);
        bar3.setOpacity(0);
        bar4 = new Rectangle(5, 20);
        bar4.setOpacity(0);
        pingLabel = new Label();
        pingLabel.setRotate(180);
        pingLabel.setPadding(new Insets(0, 5, 0, 0));

        setRotate(180);
        getChildren().addAll(bar1, bar2, bar3, bar4, pingLabel);
        Platform.runLater(() -> setAlignment(Pos.TOP_LEFT));
    }

    public void setPing(int ping) {
        this.currentPing = ping;
        updatePingDisplay();
    }

    private void updatePingDisplay() {
        Platform.runLater(() -> {
            int barsToShow = getBarsToShow(currentPing);
            setBarTransparency(bar1, barsToShow >= 1, barsToShow);
            setBarTransparency(bar2, barsToShow >= 2, barsToShow);
            setBarTransparency(bar3, barsToShow >= 3, barsToShow);
            setBarTransparency(bar4, barsToShow >= 4, barsToShow);
            pingLabel.setText(currentPing + " ms");
        });
    }

    private int getBarsToShow(int ping) {
        if (ping <= 5) return 4;
        else if (ping <= 10) return 3;
        else if (ping <= 15) return 2;
        else return 1;
    }

    private void setBarTransparency(Rectangle bar, boolean isActive, int barsToShow) {
        bar.setOpacity(isActive ? 1 : 0.25);
        if (barsToShow > 1) {
            bar.setFill(Color.BLACK);
        } else {
            bar.setFill(Color.RED);
        }
    }
}
