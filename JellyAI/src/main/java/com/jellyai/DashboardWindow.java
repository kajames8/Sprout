package com.jellyai;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.prefs.Preferences;

/**
 * The Jelly AI dashboard, shown as a chat bubble above the jelly's head.
 * It closes as soon as you click anywhere else (or press Esc).
 */
public class DashboardWindow {

    // Size of the bubble itself
    private static final double BUBBLE_WIDTH = 300;
    private static final double BUBBLE_HEIGHT = 380;
    private static final double TAIL_HEIGHT = 12;

    // Transparent margin around the bubble so the drop shadow isn't cut off
    private static final double SHADOW = 14;

    private static final double WINDOW_WIDTH = BUBBLE_WIDTH + SHADOW * 2;
    private static final double WINDOW_HEIGHT = BUBBLE_HEIGHT + TAIL_HEIGHT + SHADOW * 2;

    // Where the jelly's head and feet are, measured down from the top of its window
    private static final double HEAD_OFFSET = 38;
    private static final double FEET_OFFSET = 140;

    private final Stage stage = new Stage();
    private final MemoryStore memoryStore;

    private final Label nameLabel = new Label();
    private final VBox messages = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();

    private final Polygon tailDown = new Polygon(0, 0, 24, 0, 12, TAIL_HEIGHT);
    private final Polygon tailUp = new Polygon(0, TAIL_HEIGHT, 24, TAIL_HEIGHT, 12, 0);

    private Stage anchor;

    public DashboardWindow(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;

        tailDown.setFill(Color.WHITE);
        tailUp.setFill(Color.WHITE);

        // ---------- Header ----------
        nameLabel.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #25445B;");

        Label status = new Label("● online  |  local memory ready");
        status.setStyle("-fx-text-fill: #39866d; -fx-font-size: 10px;");

        VBox header = new VBox(2, nameLabel, status);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(12, 12, 8, 12));
        header.setStyle("-fx-border-color: transparent transparent #ececf2 transparent;");

        // ---------- Messages ----------
        messages.setPadding(new Insets(10));

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Keep the newest message in view
        messages.heightProperty().addListener((obs, oldH, newH) -> scroll.setVvalue(1.0));

        // ---------- Input row ----------
        input.setPromptText("Save a note...");
        input.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: #d3d3dc;" +
                        "-fx-border-radius: 18;" +
                        "-fx-padding: 7 13 7 13;" +
                        "-fx-font-size: 13px;" +
                        "-fx-focus-color: transparent;" +
                        "-fx-faint-focus-color: transparent;");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("↑");
        send.setMinSize(32, 32);
        send.setPrefSize(32, 32);
        send.setMaxSize(32, 32);
        send.setStyle(
                "-fx-background-color: #0b84ff;" +
                        "-fx-background-radius: 50;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 15px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 0;" +
                        "-fx-cursor: hand;");

        send.setOnAction(event -> saveNote());
        input.setOnAction(event -> saveNote());

        HBox inputRow = new HBox(8, input, send);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setPadding(new Insets(8, 12, 12, 12));

        // ---------- The bubble ----------
        VBox bubble = new VBox(header, scroll, inputRow);
        bubble.setPrefSize(BUBBLE_WIDTH, BUBBLE_HEIGHT);
        bubble.setMinSize(BUBBLE_WIDTH, BUBBLE_HEIGHT);
        bubble.setMaxSize(BUBBLE_WIDTH, BUBBLE_HEIGHT);
        bubble.setStyle("-fx-background-color: white; -fx-background-radius: 22;");

        // Bubble + one of the two tails (which one depends on where there's room)
        VBox box = new VBox(tailUp, bubble, tailDown);
        box.setAlignment(Pos.TOP_CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setEffect(new DropShadow(14, 0, 3, Color.rgb(0, 0, 0, 0.22)));

        StackPane root = new StackPane(box);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(SHADOW));
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.hide();
            }
        });

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("Jelly AI");
        stage.setScene(scene);

        // Disappear when you click anywhere else
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                stage.hide();
            }
        });
    }

    /**
     * Tells the dashboard which window is the jelly, so the bubble can sit
     * above its head and follow it when it moves.
     */
    public void setAnchor(Stage anchor) {
        this.anchor = anchor;

        if (anchor == null) {
            return;
        }

        // Owned windows stay above their owner and don't get a taskbar button.
        if (stage.getOwner() == null && !stage.isShowing()) {
            stage.initOwner(anchor);
        }

        anchor.xProperty().addListener((obs, oldX, newX) -> {
            if (stage.isShowing()) {
                position();
            }
        });

        anchor.yProperty().addListener((obs, oldY, newY) -> {
            if (stage.isShowing()) {
                position();
            }
        });
    }

    public void show() {
        refresh();
        position();

        if (!stage.isShowing()) {
            stage.show();
        }

        stage.toFront();
        stage.requestFocus();
        input.requestFocus();
    }

    // ==========================================
    // CONTENT
    // ==========================================

    private void saveNote() {

        String note = input.getText().trim();

        if (note.isEmpty()) {
            return;
        }

        memoryStore.saveMemory(note);
        input.clear();
        refresh();
    }

    private void refresh() {

        // Uses the name chosen with the right-click menu on the jelly.
        String name = Preferences
                .userNodeForPackage(FloatingPetWindow.class)
                .get("name", "Jelly");

        nameLabel.setText(name);

        messages.getChildren().clear();

        messages.getChildren().add(bubbleRow(
                "Hi! I'm " + name + ". Type a note and I'll save it to my local " +
                        "memory. Cloud AI chat is coming soon.",
                false));

        String saved = memoryStore.getAllMemories();

        if (saved != null) {

            for (String line : saved.split("\\R")) {

                String note = line.trim();

                if (!note.isEmpty()) {
                    messages.getChildren().add(bubbleRow(note, true));
                }
            }
        }

        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    // iMessage-style row: blue and right-aligned for your notes,
    // gray and left-aligned for Jelly.
    private HBox bubbleRow(String text, boolean mine) {

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(BUBBLE_WIDTH * 0.72);
        label.setMinHeight(Region.USE_PREF_SIZE);

        label.setStyle(
                (mine
                        ? "-fx-background-color: #0b84ff; -fx-text-fill: white;"
                        : "-fx-background-color: #e9e9ee; -fx-text-fill: #25445B;") +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 8 12 8 12;" +
                        "-fx-font-size: 13px;");

        HBox row = new HBox(label);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        return row;
    }

    // ==========================================
    // POSITIONING
    // ==========================================

    private void position() {

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        // No jelly to point at: center on screen with no tail.
        if (anchor == null) {

            tailUp.setVisible(false);
            tailUp.setManaged(false);
            tailDown.setVisible(false);
            tailDown.setManaged(false);

            stage.setX(screen.getMinX() + (screen.getWidth() - WINDOW_WIDTH) / 2);
            stage.setY(screen.getMinY() + (screen.getHeight() - WINDOW_HEIGHT) / 2);
            return;
        }

        double centerX = anchor.getX() + anchor.getWidth() / 2;
        double headY = anchor.getY() + HEAD_OFFSET;
        double feetY = anchor.getY() + FEET_OFFSET;

        // Preferred spot: above the head, tail pointing down.
        double x = centerX - WINDOW_WIDTH / 2;
        double y = headY - WINDOW_HEIGHT + SHADOW;
        boolean above = true;

        // Not enough room above (jelly near the top of the screen):
        // go below the jelly, tail pointing up.
        if (y < screen.getMinY()) {
            above = false;
            y = feetY - SHADOW;
        }

        // Keep the whole bubble on screen.
        x = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - WINDOW_WIDTH));
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - WINDOW_HEIGHT));

        // Slide the tail so it still points at the jelly if the bubble was pushed sideways.
        double maxShift = BUBBLE_WIDTH / 2 - 28;
        double shift = Math.max(-maxShift,
                Math.min(maxShift, centerX - (x + WINDOW_WIDTH / 2)));

        tailDown.setTranslateX(shift);
        tailUp.setTranslateX(shift);

        tailDown.setVisible(above);
        tailDown.setManaged(above);
        tailUp.setVisible(!above);
        tailUp.setManaged(!above);

        stage.setX(x);
        stage.setY(y);
    }
}