import javafx.animation.ScaleTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class LetterTile extends StackPane {

    private String letter;

    public LetterTile(String letter) {
        this.letter = letter;

        Rectangle box = new Rectangle(48, 48, Color.DARKBLUE);
        box.setArcWidth(8);
        box.setArcHeight(8);
        box.setStroke(Color.RED);

        Label text = new Label(letter);
        text.setFont(new Font(20));
        text.setTextFill(Color.WHITE);

        getChildren().addAll(box, text);
    }

    public String getLetter() {
        return letter;
    }

    public void pulse() {
        ScaleTransition animation = new ScaleTransition(Duration.millis(150), this);

        animation.setToX(1.2);
        animation.setToY(1.2);
        animation.setCycleCount(2);
        animation.setAutoReverse(true);

        animation.play();
    }
}