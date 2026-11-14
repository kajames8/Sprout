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

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The Jelly AI chat, shown as a bubble above the plant's head.
 * It closes as soon as you click anywhere else (or press Esc).
 *
 * Type to chat with the plant (powered by a free Groq AI key). Commands:
 *   /note text        save a note to local memory
 *   /notes            list saved notes
 *   /key gsk_...      save your free Groq API key
 *   /clear            start a fresh conversation
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

    // Where the plant's top and base are, measured down from the top of its window
    private static final double HEAD_OFFSET = 48;
    private static final double FEET_OFFSET = 163;

    // Only the most recent messages are sent to the AI.
    private static final int MAX_HISTORY = 20;

    private final Stage stage = new Stage();
    private final MemoryStore memoryStore;

    private final Label nameLabel = new Label();
    private final Label status = new Label();
    private final VBox messages = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();

    private final Polygon tailDown = new Polygon(0, 0, 24, 0, 12, TAIL_HEIGHT);
    private final Polygon tailUp = new Polygon(0, TAIL_HEIGHT, 24, TAIL_HEIGHT, 12, 0);

    // The conversation so far (what the AI sees).
    private final List<AiClient.Turn> history = new ArrayList<>();

    private Label typing;       // the "..." bubble while waiting for a reply
    private boolean waiting = false;
    private Stage anchor;

    public DashboardWindow(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;

        tailDown.setFill(Color.WHITE);
        tailUp.setFill(Color.WHITE);

        // ---------- Header ----------
        nameLabel.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #25445B;");

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
        input.setPromptText("Chat with me...");
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

        send.setOnAction(event -> submit());
        input.setOnAction(event -> submit());

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
     * Tells the dashboard which window is the plant, so the bubble can sit
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
        refreshHeader();

        // First time opening: show the greeting. Later the chat is still there.
        if (messages.getChildren().isEmpty()) {
            addGreeting();
        }

        position();

        if (!stage.isShowing()) {
            stage.show();
        }

        stage.toFront();
        stage.requestFocus();
        input.requestFocus();
    }

    // ==========================================
    // CHAT
    // ==========================================

    private String plantName() {

        // Uses the name chosen with the right-click menu on the plant.
        return Preferences
                .userNodeForPackage(FloatingPetWindow.class)
                .get("plant_name", "Sprout");
    }

    private void refreshHeader() {

        nameLabel.setText(plantName());

        status.setText(AiClient.hasKey()
                ? "● online  |  AI ready"
                : "● AI needs an API key  |  type /key");
    }

    private void addGreeting() {

        String text = "Hi! I'm " + plantName() + ". Chat with me, or type /note " +
                "to save a note.";

        if (!AiClient.hasKey()) {
            text += " To turn on my brain, get a free key at console.groq.com " +
                    "and type: /key gsk_...";
        }

        addBubble(text, false);
    }

    private void submit() {

        String text = input.getText().trim();

        if (text.isEmpty()) {
            return;
        }

        input.clear();

        // ----- commands -----
        if (text.startsWith("/")) {
            runCommand(text);
            return;
        }

        if (waiting) {
            return;   // one question at a time
        }

        if (!AiClient.hasKey()) {
            addBubble(text, true);
            addBubble("I need an API key first. Get a free one at " +
                    "console.groq.com, then type /key gsk_...", false);
            return;
        }

        addBubble(text, true);
        history.add(new AiClient.Turn("user", text));
        trimHistory();

        ask();
    }

    private void runCommand(String text) {

        String lower = text.toLowerCase();

        if (lower.startsWith("/note ")) {

            String note = text.substring(6).trim();

            if (!note.isEmpty()) {
                addBubble(note, true);

                try {
                    memoryStore.saveMemory(note);
                    addBubble("Saved! I'll remember that.", false);
                } catch (Throwable e) {
                    e.printStackTrace();
                    addBubble("I couldn't save that note (my memory database " +
                            "isn't connected).", false);
                }
            }

        } else if (lower.equals("/notes")) {

            String saved = savedNotes();

            if (saved.isBlank()) {
                addBubble("You haven't saved any notes yet. Use /note ...", false);
            } else {
                for (String line : saved.split("\\R")) {

                    if (!line.isBlank()) {
                        addBubble(line.trim(), true);
                    }
                }
            }

        } else if (lower.startsWith("/key ")) {

            String key = text.substring(5).trim();

            if (key.isEmpty()) {
                return;
            }

            AiClient.saveKey(key);
            refreshHeader();
            addBubble("Key saved! Say something and I'll answer.", false);

        } else if (lower.equals("/key")) {

            addBubble(AiClient.hasKey()
                    ? "A key is saved. Use /key gsk_... to replace it."
                    : "Type /key followed by your free Groq API key.", false);

        } else if (lower.equals("/clear")) {

            history.clear();
            messages.getChildren().clear();
            addGreeting();

        } else {
            addBubble("Commands: /note text, /notes, /key gsk_..., /clear", false);
        }
    }

    // Keeps the conversation short and starting with a user message.
    private void trimHistory() {

        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }

        while (!history.isEmpty() && !history.get(0).role().equals("user")) {
            history.remove(0);
        }
    }

    // Reads the saved notes. If the notes database has a problem, chat still
    // works, just without notes.
    private String savedNotes() {

        try {
            String notes = memoryStore.getAllMemories();
            return notes == null ? "" : notes;
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        }
    }

    private String systemPrompt() {

        StringBuilder s = new StringBuilder();

        s.append("You are ").append(plantName()).append(", a small, friendly pixel-art ")
                .append("potted plant who lives on the user's desktop as a study ")
                .append("companion. You are warm, playful and encouraging, with the ")
                .append("occasional plant pun. Keep replies short (1-4 sentences) ")
                .append("unless the user asks for more. Use plain text, no markdown. ")
                .append("Help with homework, coding and planning like a good tutor: ")
                .append("explain and guide rather than just handing over answers.");

        String notes = savedNotes();

        if (!notes.isBlank()) {
            s.append("\n\nThe user saved these notes in your memory:\n").append(notes);
        }

        return s.toString();
    }

    private void ask() {

        waiting = true;

        typing = bubbleLabel("...", false);
        messages.getChildren().add(row(typing, false));

        String system = systemPrompt();
        List<AiClient.Turn> snapshot = new ArrayList<>(history);

        Thread worker = new Thread(() -> {

            String reply;
            boolean failed = false;

            try {
                reply = AiClient.chat(system, snapshot);
            } catch (Throwable e) {
                // Throwable, so even a serious error can't leave "..." stuck on screen.
                e.printStackTrace();
                failed = true;
                reply = e.getMessage() == null
                        ? "Something went wrong (" + e.getClass().getSimpleName() + ")."
                        : e.getMessage();
            }

            String finalReply = reply;
            boolean finalFailed = failed;

            Platform.runLater(() -> {

                messages.getChildren().remove(typing.getParent());
                waiting = false;

                if (finalFailed) {
                    // Don't keep the unanswered question in the history.
                    if (!history.isEmpty()) {
                        history.remove(history.size() - 1);
                    }
                    addBubble(finalReply, false);
                } else {
                    history.add(new AiClient.Turn("assistant", finalReply));
                    trimHistory();
                    addBubble(finalReply, false);
                }

                input.requestFocus();
            });
        }, "jelly-ai-chat");

        worker.setDaemon(true);
        worker.start();
    }

    // ==========================================
    // BUBBLES
    // ==========================================

    private void addBubble(String text, boolean mine) {

        messages.getChildren().add(row(bubbleLabel(text, mine), mine));

        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    // iMessage-style: blue and right-aligned for you, gray and left-aligned for the plant.
    private Label bubbleLabel(String text, boolean mine) {

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

        return label;
    }

    private HBox row(Label label, boolean mine) {

        HBox row = new HBox(label);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        return row;
    }

    // ==========================================
    // POSITIONING
    // ==========================================

    private void position() {

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        // No plant to point at: center on screen with no tail.
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

        // Not enough room above (plant near the top of the screen):
        // go below the plant, tail pointing up.
        if (y < screen.getMinY()) {
            above = false;
            y = feetY - SHADOW;
        }

        // Keep the whole bubble on screen.
        x = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - WINDOW_WIDTH));
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - WINDOW_HEIGHT));

        // Slide the tail so it still points at the plant if the bubble was pushed sideways.
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