package com.jellyai;

import javafx.animation.AnimationTimer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class FloatingPetWindow {

    private static final String DEFAULT_NAME = "Sprout";
    private static final String FALLBACK_LEAF_COLOR = "#5DBB63";
    private static final String DEFAULT_POT_COLOR = "#D1743F";
    private static final int MAX_NAME_LENGTH = 20;

    private static final double PLANT_AREA_W = 190;
    private static final double PLANT_AREA_H = 180;

    // ---------- Growth and shrinking ----------

    // Every task you finish grows the plant a little (5 tasks = +20%).
    private static final double GROWTH_PER_TASK = 0.04;

    // At midnight, every task that was NOT finished shrinks it a little.
    private static final double SHRINK_PER_MISSED_TASK = 0.05;

    // If the app is closed for days, at most this many days of missed care count.
    private static final int MAX_MISSED_DAYS = 7;

    private static final String[][] POT_PRESETS = {
            {"Terracotta", "#D1743F"},
            {"Sky blue", "#5B9BD5"},
            {"Pink", "#E58BB0"},
            {"Mint", "#6CC5A0"},
            {"Lavender", "#9C8AD6"},
            {"Cream", "#EADFC8"},
            {"Charcoal", "#4A4F5A"}
    };

    // ==========================================
    // DAILY CARE TASKS (each one has a tool)
    // ==========================================

    private enum CareTask {

        WATER("Water me (watering can)", "Water", "Water",
                "Ahh, that's better! Thank you!",
                "I'm thirsty! Use the watering can from my toolbox!",
                "#7FCBFA", ToolArt.CAN),

        SUNLIGHT("Give me sunshine (sun)", "Sun", "Sunshine",
                "Mmm, warm sunshine!",
                "I'd love some sunshine! Check my toolbox.",
                "#F7D154", ToolArt.SUN),

        FERTILIZER("Feed me plant food", "Food", "Feed",
                "Yum! That's some good plant food!",
                "Psst... I'm hungry! Grab the plant food from my toolbox.",
                "#B07A4A", ToolArt.FOOD),

        TRIM("Trim my leaves (clippers)", "Clippers", "Trim",
                "Snip snip! All tidy!",
                "My leaves need a trim! Use the clippers in my toolbox.",
                "#9AD98F", ToolArt.CLIPPERS_CLOSED),

        HELLO("Say hello (heart, or click me)", "Hello", "Say hi",
                "Hi! Thanks for saying hello!",
                "Come say hello to me!",
                "#F4A6A6", ToolArt.HEART);

        final String label;       // shown in the daily care window
        final String toolName;    // shown in the toolbox
        final String button;
        final String message;
        final String hint;
        final String sparkle;
        final String[] icon;

        CareTask(String label, String toolName, String button, String message,
                 String hint, String sparkle, String[] icon) {

            this.label = label;
            this.toolName = toolName;
            this.button = button;
            this.message = message;
            this.hint = hint;
            this.sparkle = sparkle;
            this.icon = icon;
        }
    }

    private static final int TASK_COUNT = CareTask.values().length;

    private final Stage stage;
    private final StackPane root;
    private final HBox row;
    private final StackPane plantArea;
    private final PixelPlant plant;
    private final Shelf tray = new Shelf();
    private final Canvas dragLayer = new Canvas();
    private final Label speechBubble;
    private final ContextMenu menu = new ContextMenu();

    private MenuItem growthItem;
    private MenuItem careItem;
    private MenuItem seedsItem;
    private MenuItem toolsItem;
    private MenuItem recolorItem;
    private MenuItem emptyItem;
    private MenuItem jumpItem;

    private final Runnable dashboardAction;
    private final Random random = new Random();

    // Saved between runs (stored in the user's Windows registry by Java).
    private final Preferences prefs =
            Preferences.userNodeForPackage(FloatingPetWindow.class);

    private String petName;
    private PlantType petType;          // null = empty pot
    private Color petColor;
    private Color potColor;
    private double growth;

    private int doneMask;               // which of today's tasks are finished
    private String pendingMessage;      // shown once the window appears

    private PlantType seedDragType;     // seed bag currently being dragged
    private CareTask toolDragTask;      // tool currently being dragged
    private boolean trayOpen = false;

    private double dragOffsetX;
    private double dragOffsetY;

    private Timeline bubbleTimer;
    private Timeline jumpTimeline;
    private Timeline dayCheck;
    private PauseTransition behaviorTimer;

    private boolean dragging = false;

    // Double-click the plant to stop (or resume) its random hopping around.
    private boolean jumpingEnabled = prefs.getBoolean("jump_enabled", true);

    // Daily care window
    private Stage careStage;
    private final Label careSummary = new Label();
    private final ProgressBar careProgress = new ProgressBar(0);
    private final Map<CareTask, Label> careMarks = new EnumMap<>(CareTask.class);
    private final Map<CareTask, Label> careNames = new EnumMap<>(CareTask.class);
    private final Map<CareTask, Button> careButtons = new EnumMap<>(CareTask.class);

    // {name} is replaced with the plant's current name.
    private final String[] messages = {
            "Hi! I'm {name}!",
            "Need a little help?",
            "You're doing great!",
            "Let's get some work done!",
            "Remember to take a break!",
            "{name} is here if you need me!",
            "What are we learning today?",
            "Stay curious!",
            "Hehe!",
            "Photosynthesis time!",
            "I could use some sunshine!",
            "Don't forget to drink some water!",
            "I'm growing on you, right?",
            "Let's grow something great today!",
            "I believe in you!",
            "Just checking on you!",
            "Leaf it to me!",
            "Did you see that jump?",
            "I'm feeling rooted today!",
            "Hello again!",
            "Right-click me for seeds, tools and pot colors!"
    };

    public FloatingPetWindow(Runnable dashboardAction) {

        this.dashboardAction = dashboardAction;

        petName = prefs.get("plant_name", DEFAULT_NAME);
        petType = loadPlantType();
        petColor = petType == null
                ? parseColor(FALLBACK_LEAF_COLOR)
                : loadColor(petType);
        potColor = parseColor(prefs.get("pot_color", DEFAULT_POT_COLOR));
        growth = Math.max(0.0, Math.min(1.0, prefs.getDouble("plant_growth", 0.0)));
        doneMask = prefs.getInt("care_done", 0) & ((1 << TASK_COUNT) - 1);

        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle(petName);

        plant = new PixelPlant();
        plant.setType(petType);
        plant.setBaseColor(petColor);
        plant.setPotColor(potColor);
        plant.setGrowth(growth, false);

        speechBubble = new Label();
        speechBubble.setVisible(false);
        speechBubble.setMouseTransparent(true);

        speechBubble.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 12;" +
                        "-fx-padding: 9 13 9 13;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-family: 'Arial';" +
                        "-fx-text-fill: #25445B;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);"
        );

        plantArea = new StackPane(plant, speechBubble);
        plantArea.setPrefSize(PLANT_AREA_W, PLANT_AREA_H);
        plantArea.setMinSize(PLANT_AREA_W, PLANT_AREA_H);
        plantArea.setMaxSize(PLANT_AREA_W, PLANT_AREA_H);
        plantArea.setAlignment(Pos.CENTER);
        plantArea.setStyle("-fx-background-color: transparent;");

        StackPane.setAlignment(speechBubble, Pos.TOP_CENTER);
        speechBubble.setTranslateY(5);

        row = new HBox(plantArea);
        row.setAlignment(Pos.TOP_LEFT);
        row.setStyle("-fx-background-color: transparent;");

        // Full-window layer where the seed bag or tool follows the mouse.
        dragLayer.setManaged(false);
        dragLayer.setMouseTransparent(true);

        root = new StackPane(row, dragLayer);
        root.setAlignment(Pos.TOP_LEFT);
        root.setStyle("-fx-background-color: transparent;");

        dragLayer.widthProperty().bind(root.widthProperty());
        dragLayer.heightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, PLANT_AREA_W, PLANT_AREA_H);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.setWidth(PLANT_AREA_W);
        stage.setHeight(PLANT_AREA_H);
        stage.setX(900);
        stage.setY(500);

        configureMenu();
        configureTray();
        configureDragging();
        configureClicking();

        plant.startAnimation();

        // Did a day (or several) go by since the plant was last cared for?
        pendingMessage = checkNewDay();

        // Nothing planted yet: start with the seed bags open.
        if (petType == null) {
            openTray(0);
        }
    }

    public void show() {

        stage.show();
        scheduleRandomBehavior();

        // Look for midnight while the app is running.
        dayCheck = new Timeline(new KeyFrame(Duration.seconds(60), event -> {

            String message = checkNewDay();

            if (message != null) {
                showMessage(message);
            }
        }));

        dayCheck.setCycleCount(Animation.INDEFINITE);
        dayCheck.play();

        if (pendingMessage != null) {
            showMessage(pendingMessage);
            pendingMessage = null;

        } else if (!hasPlant()) {
            showMessage("My pot is empty! Drag a seed bag into it.");
        }
    }

    // Lets the dashboard bubble attach itself above the plant.
    public Stage getStage() {
        return stage;
    }

    public void close() {

        plant.stopAnimation();

        if (bubbleTimer != null) {
            bubbleTimer.stop();
        }

        if (jumpTimeline != null) {
            jumpTimeline.stop();
        }

        if (dayCheck != null) {
            dayCheck.stop();
        }

        if (behaviorTimer != null) {
            behaviorTimer.stop();
        }

        if (careStage != null) {
            careStage.close();
        }

        menu.hide();
        stage.close();
    }

    // ==========================================
    // DAILY CARE: TASKS, GROWTH, SHRINKING
    // ==========================================

    private boolean hasPlant() {
        return petType != null;
    }

    private boolean isDone(CareTask task) {
        return (doneMask & (1 << task.ordinal())) != 0;
    }

    private int doneCount() {
        return Integer.bitCount(doneMask);
    }

    // Runs at startup and every minute. When the date has changed, every task
    // that wasn't finished shrinks the plant, then the checklist starts over.
    // Returns something for the plant to say, or null.
    private String checkNewDay() {

        LocalDate today = LocalDate.now();
        String stored = prefs.get("care_date", "");

        if (stored.isEmpty()) {
            startDay(today);
            return null;
        }

        LocalDate last;

        try {
            last = LocalDate.parse(stored);
        } catch (DateTimeParseException e) {
            startDay(today);
            return null;
        }

        long days = ChronoUnit.DAYS.between(last, today);

        if (days <= 0) {
            return null;   // same day
        }

        // Unfinished tasks from the last day, plus every task of any days skipped.
        int missed = TASK_COUNT - doneCount();
        long skippedDays = Math.min(days - 1, MAX_MISSED_DAYS - 1);
        int totalMissed = missed + (int) skippedDays * TASK_COUNT;

        startDay(today);

        if (!hasPlant()) {
            return null;
        }

        double before = growth;
        growth = Math.max(0.0, growth - totalMissed * SHRINK_PER_MISSED_TASK);

        if (growth < before) {

            prefs.putDouble("plant_growth", growth);
            plant.setGrowth(growth, true);
            refreshCarePanel();

            return days > 1
                    ? "I missed you! I shrank a little. Please take care of me today!"
                    : "I didn't get all my care yesterday, so I shrank a little. Let's do better today!";
        }

        return "A new day! Let's get my daily care done.";
    }

    private void startDay(LocalDate day) {

        doneMask = 0;

        prefs.put("care_date", day.toString());
        prefs.putInt("care_done", 0);

        refreshCarePanel();
    }

    // Marks a task finished and grows the plant. Returns false if it was already done.
    private boolean completeTask(CareTask task) {

        if (isDone(task)) {
            return false;
        }

        doneMask |= 1 << task.ordinal();
        prefs.putInt("care_done", doneMask);

        growth = Math.min(1.0, growth + GROWTH_PER_TASK);
        prefs.putDouble("plant_growth", growth);

        plant.setGrowth(growth, true);

        refreshCarePanel();

        return true;
    }

    private String doneMessage(CareTask task) {

        return doneCount() == TASK_COUNT
                ? "That's everything for today! You're the best!"
                : task.message;
    }

    // Uses a tool on the plant. Dropping a tool on the plant, pressing its
    // button in the daily care window, or (for hello) clicking the plant all end up here.
    private void performTask(CareTask task) {

        if (!hasPlant()) {
            showMessage("Plant a seed first! Open my seed bags.");
            return;
        }

        if (dragging || plant.isBusy()) {
            return;
        }

        hideBubble();

        boolean firstTime = !isDone(task);

        // Runs when the animation for this tool is over.
        Runnable finished = () -> {

            plant.happy();

            if (firstTime && completeTask(task)) {
                plant.burst(Color.web(task.sparkle), 14);
                showMessage(doneMessage(task));
            } else {
                showMessage("Thank you! But I already got that today.");
            }
        };

        switch (task) {
            case WATER:
                plant.startWatering(finished);
                break;

            case SUNLIGHT:
                plant.startSun(finished);
                break;

            case FERTILIZER:
                plant.startFeeding(finished);
                break;

            case TRIM:
                plant.startTrimming(finished);
                break;

            default:
                plant.startHello(finished);
                break;
        }
    }

    // Clears today's check marks so the tasks can be done again. The growth
    // those tasks gave is taken back too, so resetting can't be used to grow
    // the plant for free.
    private void resetTasks() {

        if (doneCount() == 0) {
            showMessage("No tasks to reset yet!");
            return;
        }

        if (!confirm("Reset today's tasks?",
                "Clear all of today's check marks? The growth they gave " +
                        "will be taken back.")) {
            return;
        }

        growth = Math.max(0.0, growth - doneCount() * GROWTH_PER_TASK);
        doneMask = 0;

        prefs.putInt("care_done", 0);
        prefs.putDouble("plant_growth", growth);

        plant.setGrowth(growth, true);
        refreshCarePanel();

        showMessage("Tasks reset! Let's start the day over.");
    }

    private CareTask randomUndoneTask() {

        List<CareTask> undone = new ArrayList<>();

        for (CareTask task : CareTask.values()) {

            if (!isDone(task)) {
                undone.add(task);
            }
        }

        return undone.get(random.nextInt(undone.size()));
    }

    // ---------- Daily care window ----------

    private void showCarePanel() {

        if (careStage == null) {
            buildCarePanel();
        }

        refreshCarePanel();

        if (!careStage.isShowing()) {

            Rectangle2D screen = Screen.getPrimary().getVisualBounds();

            double width = 370;
            double x = stage.getX() + stage.getWidth() + 10;

            if (x + width > screen.getMaxX()) {
                x = stage.getX() - width - 10;
            }

            x = Math.max(screen.getMinX(), x);

            double y = Math.max(screen.getMinY(),
                    Math.min(stage.getY() - 80, screen.getMaxY() - 400));

            careStage.setX(x);
            careStage.setY(y);

            careStage.show();
        }

        careStage.toFront();
    }

    private void buildCarePanel() {

        careStage = new Stage(StageStyle.UTILITY);
        careStage.initOwner(stage);
        careStage.setAlwaysOnTop(true);
        careStage.setTitle("Daily care");
        careStage.setResizable(false);

        Label title = new Label("Daily care");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #25445B;");

        careSummary.setStyle("-fx-text-fill: #55627a; -fx-font-size: 12px;");

        careProgress.setPrefWidth(310);
        careProgress.setMaxWidth(Double.MAX_VALUE);

        VBox rows = new VBox(10);

        for (CareTask task : CareTask.values()) {

            Label mark = new Label();
            mark.setMinWidth(22);

            Label name = new Label(task.label);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setStyle("-fx-font-size: 13px;");
            HBox.setHgrow(name, Priority.ALWAYS);

            Button button = new Button(task.button);
            button.setMinWidth(78);
            button.setOnAction(event -> performTask(task));

            careMarks.put(task, mark);
            careNames.put(task, name);
            careButtons.put(task, button);

            HBox taskRow = new HBox(10, mark, name, button);
            taskRow.setAlignment(Pos.CENTER_LEFT);

            rows.getChildren().add(taskRow);
        }

        Label rule = new Label(
                "Use the tools in my toolbox (or the buttons here) to look " +
                        "after me. Every task you finish helps me grow. At midnight, " +
                        "every task that wasn't finished makes me shrink a little. " +
                        "The list starts over each day.");
        rule.setWrapText(true);
        rule.setStyle("-fx-text-fill: #7b8497; -fx-font-size: 11px;");

        Button resetButton = new Button("Reset tasks");
        resetButton.setOnAction(event -> resetTasks());

        VBox content = new VBox(12, title, careSummary, careProgress, rows,
                resetButton, rule);
        content.setPadding(new Insets(18));
        content.setStyle("-fx-background-color: white;");

        careStage.setScene(new Scene(content, 350, 420));
    }

    private void refreshCarePanel() {

        // The toolbox shows a check mark on tools that are done for today.
        tray.setDoneMask(doneMask);

        if (careStage == null) {
            return;
        }

        boolean planted = hasPlant();

        for (CareTask task : CareTask.values()) {

            boolean done = isDone(task);

            Label mark = careMarks.get(task);
            mark.setText(done ? "✔" : "○");
            mark.setStyle(done
                    ? "-fx-text-fill: #2f9e5b; -fx-font-size: 15px; -fx-font-weight: bold;"
                    : "-fx-text-fill: #b4b9c6; -fx-font-size: 15px;");

            careNames.get(task).setStyle(done
                    ? "-fx-font-size: 13px; -fx-text-fill: #9aa1b2; -fx-strikethrough: true;"
                    : "-fx-font-size: 13px; -fx-text-fill: #25445B;");

            careButtons.get(task).setDisable(done || !planted);
        }

        careProgress.setProgress(doneCount() / (double) TASK_COUNT);

        careSummary.setText(planted
                ? doneCount() + " of " + TASK_COUNT + " done today   •   "
                + petType.label + " is " + Math.round(growth * 100) + "% grown"
                : "Plant a seed first, then come back to take care of it!");
    }

    // ==========================================
    // SEED BAGS + TOOLBOX (DRAG AND DROP)
    // ==========================================

    private void configureTray() {

        tray.setOnMouseMoved(event -> {

            double x = event.getX();
            double y = event.getY();

            tray.setHover(tray.itemAt(x, y));

            tray.setCursor(tray.itemAt(x, y) >= 0 ||
                    tray.tabAt(x, y) >= 0 ||
                    tray.overClose(x, y)
                    ? Cursor.HAND
                    : Cursor.DEFAULT);
        });

        tray.setOnMouseExited(event -> {
            tray.setHover(-1);
            tray.setCursor(Cursor.DEFAULT);
        });

        tray.setOnMousePressed(event -> {

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            menu.hide();

            double x = event.getX();
            double y = event.getY();

            if (tray.overClose(x, y)) {
                closeTray();
                event.consume();
                return;
            }

            int tab = tray.tabAt(x, y);

            if (tab >= 0) {
                tray.setTab(tab);
                event.consume();
                return;
            }

            int item = tray.itemAt(x, y);

            // Empty part of the shelf: let it move the window like anywhere else.
            if (item < 0) {
                return;
            }

            if (tray.getTab() == 0) {
                seedDragType = PlantType.values()[item];
                tray.setHiddenSeed(seedDragType);
            } else {
                toolDragTask = CareTask.values()[item];
                tray.setHiddenTool(toolDragTask);
            }

            updateDragGhost(event.getSceneX(), event.getSceneY());

            event.consume();
        });

        tray.setOnMouseDragged(event -> {

            if (seedDragType == null && toolDragTask == null) {
                return;
            }

            updateDragGhost(event.getSceneX(), event.getSceneY());

            event.consume();
        });

        tray.setOnMouseReleased(event -> {

            if (seedDragType == null && toolDragTask == null) {
                return;
            }

            PlantType seed = seedDragType;
            CareTask tool = toolDragTask;
            boolean overPlant = overPlant(event.getSceneX(), event.getSceneY());

            seedDragType = null;
            toolDragTask = null;
            tray.clearHidden();
            updateDragGhost(0, 0);   // clears the item from the screen

            if (seed != null) {

                if (overPlant) {
                    plantSeed(seed);
                } else {
                    showMessage("Drop the seeds into my pot!");
                }

            } else if (tool != null) {

                if (overPlant) {
                    performTask(tool);
                } else {
                    showMessage("Drop the tool onto me!");
                }
            }

            event.consume();
        });
    }

    private void openTray(int tab) {

        tray.setTab(tab);

        if (trayOpen) {
            return;
        }

        trayOpen = true;

        // Make sure the shelf fits on screen: slide the window left if needed.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        double overflow = stage.getX() + PLANT_AREA_W + Shelf.W - screen.getMaxX();

        if (overflow > 0) {
            stage.setX(stage.getX() - overflow);
        }

        stage.setWidth(PLANT_AREA_W + Shelf.W);

        row.getChildren().add(tray);
    }

    private void closeTray() {

        if (!trayOpen) {
            return;
        }

        trayOpen = false;

        row.getChildren().remove(tray);

        stage.setWidth(PLANT_AREA_W);
    }

    private void toggleTray(int tab) {

        if (trayOpen && tray.getTab() == tab) {
            closeTray();
        } else {
            openTray(tab);
        }
    }

    // True when the point (in window coordinates) is over the plant or pot.
    private boolean overPlant(double sceneX, double sceneY) {

        Point2D local = plant.sceneToLocal(sceneX, sceneY);

        return local != null &&
                local.getX() >= 44 && local.getX() <= 136 &&
                local.getY() >= 50 && local.getY() <= 150;
    }

    // Draws the seed bag or tool under the mouse, and lights up the plant
    // when it's over it.
    private void updateDragGhost(double sceneX, double sceneY) {

        GraphicsContext g = dragLayer.getGraphicsContext2D();

        g.clearRect(0, 0, dragLayer.getWidth(), dragLayer.getHeight());

        if (seedDragType != null || toolDragTask != null) {

            g.save();

            g.translate(sceneX, sceneY);
            g.rotate(-8);

            g.setFill(Color.web("#25445B", 0.18));
            g.fillOval(-22, 26, 44, 8);

            if (seedDragType != null) {
                drawBag(g, seedDragType, -24, -28, 4);
            } else {
                ToolArt.drawCentered(g, toolDragTask.icon, 0, 0, 4, 0, 1.0);
            }

            g.restore();
        }

        plant.setDropHover(
                (seedDragType != null || toolDragTask != null) &&
                        overPlant(sceneX, sceneY));
    }

    private void plantSeed(PlantType type) {

        if (hasPlant()) {

            if (type == petType) {
                showMessage("I'm already a " + type.label.toLowerCase() + "!");
                return;
            }

            boolean replace = confirm("Plant a new seed?",
                    "Replace your " + petType.label.toLowerCase() + " with a " +
                            type.label.toLowerCase() + " seed? The new plant " +
                            "starts over as a seedling.");

            if (!replace) {
                return;
            }
        }

        petType = type;
        petColor = loadColor(type);
        growth = 0.0;

        prefs.put("pot_plant", type.name());
        prefs.putDouble("plant_growth", growth);

        plant.setType(type);
        plant.setBaseColor(petColor);
        plant.setGrowth(growth, false);

        plant.burst(Color.web("#8ED081"), 18);
        plant.happy();
        plant.selfSquish();

        // Now it needs looking after: switch the shelf over to the toolbox.
        tray.setTab(1);
        refreshCarePanel();

        showMessage("Planted! Use the tools in my toolbox to take care of me.");
    }

    private void emptyPot() {

        if (!hasPlant()) {
            return;
        }

        if (!confirm("Empty the pot?",
                "Dig up your " + petType.label.toLowerCase() +
                        "? You can plant a new seed afterwards.")) {
            return;
        }

        petType = null;
        growth = 0.0;

        prefs.remove("pot_plant");
        prefs.putDouble("plant_growth", growth);

        plant.setType(null);
        plant.setGrowth(0.0, false);

        refreshCarePanel();
        openTray(0);

        showMessage("Empty pot! Drag in a new seed bag.");
    }

    private boolean confirm(String title, String text) {

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION, text,
                ButtonType.OK, ButtonType.CANCEL);

        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);

        Optional<ButtonType> result = alert.showAndWait();

        return result.isPresent() && result.get() == ButtonType.OK;
    }

    // ==========================================
    // MENU, NAME AND COLORS
    // ==========================================

    private void configureMenu() {

        growthItem = new MenuItem();
        growthItem.setDisable(true);

        careItem = new MenuItem();
        careItem.setOnAction(event -> showCarePanel());

        seedsItem = new MenuItem();
        seedsItem.setOnAction(event -> toggleTray(0));

        toolsItem = new MenuItem();
        toolsItem.setOnAction(event -> toggleTray(1));

        Menu potMenu = new Menu("Pot color");

        for (String[] preset : POT_PRESETS) {

            MenuItem item = new MenuItem(preset[0]);
            item.setOnAction(event -> setPotColor(parseColor(preset[1])));

            potMenu.getItems().add(item);
        }

        MenuItem customPot = new MenuItem("Custom...");
        customPot.setOnAction(event -> chooseColor(
                "Pot color", "Pick " + petName + "'s pot color",
                potColor, plant::setPotColor)
                .ifPresent(this::setPotColor));

        potMenu.getItems().addAll(new SeparatorMenuItem(), customPot);

        recolorItem = new MenuItem("Change plant color...");
        recolorItem.setOnAction(event -> choosePlantColor());

        MenuItem rename = new MenuItem("Change name...");
        rename.setOnAction(event -> askForName());

        emptyItem = new MenuItem("Empty the pot...");
        emptyItem.setOnAction(event -> emptyPot());

        MenuItem reset = new MenuItem("Reset name and colors");
        reset.setOnAction(event -> resetLook());

        MenuItem notesItem = new MenuItem("Open notes...");
        notesItem.setOnAction(event -> openDashboard());

        MenuItem resetTasksItem = new MenuItem("Reset today's tasks...");
        resetTasksItem.setOnAction(event -> resetTasks());

        jumpItem = new MenuItem();
        jumpItem.setOnAction(event -> toggleJumping());

        menu.getItems().addAll(
                growthItem,
                careItem,
                resetTasksItem,
                notesItem,
                jumpItem,
                new SeparatorMenuItem(),
                toolsItem,
                seedsItem,
                potMenu,
                recolorItem,
                rename,
                new SeparatorMenuItem(),
                emptyItem,
                reset
        );

        menu.setOnShowing(event -> {

            boolean planted = hasPlant();

            growthItem.setText(planted
                    ? petType.label + ": " + Math.round(growth * 100) + "% grown"
                    : "Empty pot - plant a seed!");

            careItem.setText("Daily care tasks (" + doneCount() + "/" + TASK_COUNT + ")...");

            seedsItem.setText(trayOpen && tray.getTab() == 0
                    ? "Close seed bags" : "Open seed bags");
            toolsItem.setText(trayOpen && tray.getTab() == 1
                    ? "Close toolbox" : "Open toolbox");

            recolorItem.setText(planted
                    ? "Change " + petType.colorPart + " color..."
                    : "Change plant color...");
            recolorItem.setDisable(!planted);
            emptyItem.setDisable(!planted);
            jumpItem.setText(jumpingEnabled
                    ? "Stop jumping" : "Resume jumping");
        });

        root.setOnContextMenuRequested(event -> {
            menu.show(root, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void setPotColor(Color color) {

        potColor = color;
        prefs.put("pot_color", toHex(potColor));

        plant.setPotColor(potColor);
        plant.happy();
    }

    private void choosePlantColor() {

        if (!hasPlant()) {
            return;
        }

        Optional<Color> picked = chooseColor(
                "Plant color",
                "Pick " + petName + "'s " + petType.colorPart + " color",
                petColor, plant::setBaseColor);

        if (picked.isPresent()) {

            petColor = picked.get();
            prefs.put(colorKey(petType), toHex(petColor));

            plant.setBaseColor(petColor);
            plant.happy();
        }
    }

    // Color picker with a live preview. Cancelling puts the old color back.
    private Optional<Color> chooseColor(String title, String header,
                                        Color start, Consumer<Color> preview) {

        ColorPicker picker = new ColorPicker(start);

        picker.setOnAction(event -> preview.accept(picker.getValue()));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(picker);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            return Optional.of(picker.getValue());
        }

        preview.accept(start);

        return Optional.empty();
    }

    private void askForName() {

        TextInputDialog dialog = new TextInputDialog(petName);
        dialog.initOwner(stage);
        dialog.setTitle("Name your plant");
        dialog.setHeaderText("What would you like to call me?");
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        String name = result.get().trim();

        if (name.isEmpty()) {
            return;
        }

        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        petName = name;
        prefs.put("plant_name", petName);
        stage.setTitle(petName);

        showMessage("I love my new name, " + petName + "!");
        plant.happy();
    }

    private void resetLook() {

        petName = DEFAULT_NAME;
        potColor = parseColor(DEFAULT_POT_COLOR);

        prefs.put("plant_name", petName);
        prefs.put("pot_color", DEFAULT_POT_COLOR);

        stage.setTitle(petName);
        plant.setPotColor(potColor);

        if (hasPlant()) {

            petColor = parseColor(petType.defaultColor);
            prefs.put(colorKey(petType), petType.defaultColor);

            plant.setBaseColor(petColor);
        }

        showMessage("Back to " + petName + "!");
    }

    private PlantType loadPlantType() {

        String saved = prefs.get("pot_plant", "");

        if (saved.isEmpty()) {
            return null;
        }

        try {
            return PlantType.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Color loadColor(PlantType type) {
        return parseColor(prefs.get(colorKey(type), type.defaultColor));
    }

    private static String colorKey(PlantType type) {
        return "plant_color_" + type.name();
    }

    private static Color parseColor(String hex) {

        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web(FALLBACK_LEAF_COLOR);
        }
    }

    private static String toHex(Color color) {

        return String.format(
                "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255)
        );
    }

    // ==========================================
    // DRAGGING THE WINDOW
    // ==========================================

    private void configureDragging() {

        root.setOnMousePressed(event -> {

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            menu.hide();

            dragging = true;

            if (jumpTimeline != null) {
                jumpTimeline.stop();
            }

            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();

            plant.startDragging();
        });

        root.setOnMouseDragged(event -> {

            if (!dragging) {
                return;
            }

            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        root.setOnMouseReleased(event -> {

            if (!dragging) {
                return;
            }

            dragging = false;

            plant.stopDragging();
            scheduleRandomBehavior();
        });
    }

    // ==========================================
    // CLICKING
    // ==========================================

    private void configureClicking() {

        plant.setOnMouseClicked(event -> {

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (event.getClickCount() >= 2) {

                toggleJumping();

            } else if (hasPlant() && !isDone(CareTask.HELLO)) {

                // Clicking the plant counts as saying hello.
                performTask(CareTask.HELLO);

            } else {

                showRandomMessage();
                plant.happy();
            }

            event.consume();
        });
    }

    // ==========================================
    // SPEECH BUBBLES
    // ==========================================

    private void showRandomMessage() {

        String message = messages[random.nextInt(messages.length)]
                .replace("{name}", petName);

        showMessage(message);
    }

    private void showMessage(String message) {

        speechBubble.setText(message);
        speechBubble.setVisible(true);

        if (bubbleTimer != null) {
            bubbleTimer.stop();
        }

        bubbleTimer = new Timeline(
                new KeyFrame(
                        Duration.seconds(3),
                        event -> hideBubble()
                )
        );

        bubbleTimer.play();
    }

    private void hideBubble() {
        speechBubble.setVisible(false);
    }

    // ==========================================
    // RANDOM BEHAVIORS
    // ==========================================

    private void scheduleRandomBehavior() {

        if (behaviorTimer != null) {
            behaviorTimer.stop();
        }

        if (!stage.isShowing()) {
            return;
        }

        // Wait a random amount of time before doing something.
        int seconds = 5 + random.nextInt(8);

        behaviorTimer = new PauseTransition(
                Duration.seconds(seconds)
        );

        behaviorTimer.setOnFinished(event -> {

            if (!dragging && seedDragType == null && toolDragTask == null &&
                    !plant.isBusy() && stage.isShowing()) {

                int behavior = random.nextInt(4);

                if (behavior == 0) {

                    // Hop to another place.
                    jumpAround();

                } else if (behavior == 1) {

                    // Say something.
                    showRandomMessage();

                } else if (behavior == 3 && !hasPlant()) {

                    showMessage("My pot is empty! Open my seed bags.");

                } else if (behavior == 3 && doneCount() < TASK_COUNT) {

                    // Remind you about a task that isn't done yet.
                    showMessage(randomUndoneTask().hint);

                } else {

                    // Squish and make a little comment.
                    plant.selfSquish();

                    String[] squishMessages = {
                            "Boing!",
                            "Rustle rustle!",
                            "Wobble wobble!",
                            "Hehehe!",
                            "I'm a springy sprout!"
                    };

                    showMessage(
                            squishMessages[
                                    random.nextInt(squishMessages.length)
                                    ]
                    );
                }
            }

            scheduleRandomBehavior();
        });

        behaviorTimer.play();
    }

    // ==========================================
    // RANDOM WINDOW JUMPING
    // ==========================================

    private void toggleJumping() {

        jumpingEnabled = !jumpingEnabled;
        prefs.putBoolean("jump_enabled", jumpingEnabled);

        if (!jumpingEnabled && jumpTimeline != null &&
                jumpTimeline.getStatus() == Animation.Status.RUNNING) {

            // Stop mid-hop and land where it is.
            jumpTimeline.stop();
            plant.stopJump();
            scheduleRandomBehavior();
        }

        plant.happy();
        showMessage(jumpingEnabled
                ? "Okay, I'll hop around again!"
                : "Okay, I'll stay right here!");
    }

    private void openDashboard() {

        hideBubble();

        if (dashboardAction != null) {
            dashboardAction.run();
        }
    }

    private void jumpAround() {

        // Stay put while the shelf is open, or if jumping was turned off.
        if (!jumpingEnabled || dragging || trayOpen || !stage.isShowing()) {
            return;
        }

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

        double padding = 15;

        double minX = bounds.getMinX() + padding;
        double minY = bounds.getMinY() + padding;

        double maxX = Math.max(
                minX,
                bounds.getMaxX() - stage.getWidth() - padding
        );

        double maxY = Math.max(
                minY,
                bounds.getMaxY() - stage.getHeight() - padding
        );

        double targetX = minX + random.nextDouble() * (maxX - minX);
        double targetY = minY + random.nextDouble() * (maxY - minY);

        double startX = stage.getX();
        double startY = stage.getY();

        double middleX = (startX + targetX) / 2;
        double peakY = Math.max(minY, Math.min(startY, targetY) - 110);

        plant.startJump();

        if (jumpTimeline != null) {
            jumpTimeline.stop();
        }

        jumpTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, event -> {
                    stage.setX(startX);
                    stage.setY(startY);
                }),

                new KeyFrame(Duration.millis(280), event -> {
                    stage.setX(middleX);
                    stage.setY(peakY);
                }),

                new KeyFrame(Duration.millis(600), event -> {
                    stage.setX(targetX);
                    stage.setY(targetY);
                })
        );

        jumpTimeline.setOnFinished(event -> {
            plant.stopJump();
            scheduleRandomBehavior();
        });

        jumpTimeline.play();
    }

    // ==========================================
    // SEED BAG ART
    // ==========================================

    // A seed bag: 12 x 14 cells.
    //   k outline   c bag   C bag shade   w label
    // The plant's 8x8 icon is drawn over the label (rows 4-11, columns 2-9).
    private static final String[] BAG_ROWS = {
            "kkkkkkkkkkkk",
            "kcCcCcCcCcck",
            "kkkkkkkkkkkk",
            "kcccccccccCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcwwwwwwwwCk",
            "kcccccccccCk",
            "kkkkkkkkkkkk"
    };

    private static Color bagColor(char c, Color body) {

        switch (c) {
            case 'k': return Color.web("#4A3626");
            case 'c': return body;
            case 'C': return body.deriveColor(0, 1.0, 0.80, 1.0);
            case 'w': return Color.web("#FFF8E6");

            // Icon colors
            case 'g': return Color.web("#5DBB63");
            case 'G': return Color.web("#3E8E46");
            case 'p': return Color.web("#F27BA7");   // pink petal
            case 'P': return Color.web("#F7C531");   // yellow petal
            case 'R': return Color.web("#E5485E");   // red petal
            case 'L': return Color.web("#9B7FD1");   // lavender
            case 'Y': return Color.web("#F7D154");   // flower center
            case 'n': return Color.web("#7A4A21");   // seed head
            case 'b': return Color.web("#8A5A33");   // trunk
            case 'e': return Color.web("#5DB5A4");   // succulent
            case 'E': return Color.web("#3E8E80");   // succulent shade
            case 'm': return Color.web("#E0453A");   // mushroom cap
            case 'W': return Color.web("#FFFFFF");   // mushroom spots
            case 'T': return Color.web("#E3C9A0");   // mushroom stem
            case 'r': return Color.web("#6B4423");   // soil
            default: return null;
        }
    }

    private static void drawBag(GraphicsContext g, PlantType type,
                                double x, double y, double px) {

        Color body = Color.web(type.bagColor);

        for (int r = 0; r < BAG_ROWS.length; r++) {

            String line = BAG_ROWS[r];

            for (int c = 0; c < line.length(); c++) {

                char ch = line.charAt(c);

                if (r >= 4 && r < 12 && c >= 2 && c < 10) {

                    char icon = type.icon[r - 4].charAt(c - 2);

                    if (icon != '.') {
                        ch = icon;
                    }
                }

                Color color = bagColor(ch, body);

                if (color != null) {
                    g.setFill(color);
                    g.fillRect(x + c * px, y + r * px, px, px);
                }
            }
        }
    }

    // ==========================================
    // SHELF: SEED BAGS + TOOLBOX
    // ==========================================

    private static class Shelf extends Canvas {

        static final double W = 264;
        static final double H = 180;

        private static final double ITEM_W = 36;
        private static final double ITEM_H = 42;
        private static final double PITCH = 50;

        private final PlantType[] seeds = PlantType.values();
        private final CareTask[] tools = CareTask.values();

        private int tab = 0;               // 0 = seed bags, 1 = toolbox
        private int hover = -1;
        private PlantType hiddenSeed = null;
        private CareTask hiddenTool = null;
        private int doneMask = 0;

        Shelf() {

            super(W, H);

            draw();
        }

        int getTab() {
            return tab;
        }

        void setTab(int newTab) {

            if (newTab != tab) {
                tab = newTab;
                hover = -1;
                draw();
            }
        }

        private int itemCount() {
            return tab == 0 ? seeds.length : tools.length;
        }

        private double itemX(int i) {
            return 14 + (i % 5) * PITCH;
        }

        private double itemY(int i) {
            return tab == 0 ? 38 + (i / 5) * 68 : 66;
        }

        // Which seed bag or tool (or its label) is at this point, or -1.
        int itemAt(double x, double y) {

            for (int i = 0; i < itemCount(); i++) {

                if (x >= itemX(i) - 6 && x <= itemX(i) + ITEM_W + 6 &&
                        y >= itemY(i) && y <= itemY(i) + ITEM_H + 14) {
                    return i;
                }
            }

            return -1;
        }

        // Which tab is at this point (0 seeds, 1 tools), or -1.
        int tabAt(double x, double y) {

            if (y >= 6 && y <= 26) {

                if (x >= 10 && x <= 76) {
                    return 0;
                }

                if (x >= 80 && x <= 146) {
                    return 1;
                }
            }

            return -1;
        }

        boolean overClose(double x, double y) {
            return x >= W - 28 && x <= W - 6 && y >= 6 && y <= 26;
        }

        void setHover(int index) {

            if (index != hover) {
                hover = index;
                draw();
            }
        }

        // The item being dragged is hidden from the shelf.
        void setHiddenSeed(PlantType type) {
            hiddenSeed = type;
            draw();
        }

        void setHiddenTool(CareTask task) {
            hiddenTool = task;
            draw();
        }

        void clearHidden() {
            hiddenSeed = null;
            hiddenTool = null;
            draw();
        }

        // Tools whose task is finished today get a check mark.
        void setDoneMask(int mask) {

            if (mask != doneMask) {
                doneMask = mask;
                draw();
            }
        }

        private void draw() {

            GraphicsContext g = getGraphicsContext2D();

            g.clearRect(0, 0, W, H);

            // Shelf background
            g.setFill(Color.web("#FFFFFF", 0.93));
            g.fillRoundRect(4, 2, W - 8, H - 4, 14, 14);

            g.setStroke(Color.web("#C9D3E0"));
            g.setLineWidth(2);
            g.strokeRoundRect(4, 2, W - 8, H - 4, 14, 14);

            // Tabs
            String[] tabNames = {"Seed bags", "Toolbox"};

            g.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            g.setTextAlign(TextAlignment.CENTER);

            for (int t = 0; t < 2; t++) {

                double tx = 10 + t * 70;

                g.setFill(t == tab ? Color.web("#25445B") : Color.web("#E8EDF4"));
                g.fillRoundRect(tx, 6, 66, 20, 8, 8);

                g.setFill(t == tab ? Color.WHITE : Color.web("#55627A"));
                g.fillText(tabNames[t], tx + 33, 20);
            }

            g.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            g.setFill(Color.web("#8793A6"));
            g.fillText("✕", W - 17, 21);

            // Items
            g.setFont(Font.font("Arial", 9.5));

            for (int i = 0; i < itemCount(); i++) {

                double x = itemX(i);
                double y = itemY(i);

                boolean hidden = tab == 0
                        ? seeds[i] == hiddenSeed
                        : tools[i] == hiddenTool;

                if (hidden) {

                    // Empty spot while the item is in your hand.
                    g.setStroke(Color.web("#C9D3E0"));
                    g.setLineWidth(2);
                    g.setLineDashes(5, 4);
                    g.strokeRoundRect(x, y, ITEM_W, ITEM_H, 6, 6);
                    g.setLineDashes((double[]) null);

                } else {

                    double lift = i == hover ? -3 : 0;

                    g.setFill(Color.web("#25445B", 0.12));
                    g.fillOval(x + 3, y + ITEM_H - 3, ITEM_W - 6, 6);

                    if (tab == 0) {

                        drawBag(g, seeds[i], x, y + lift, 3);

                    } else {

                        String[] icon = tools[i].icon;
                        double iconH = icon.length * 3;

                        ToolArt.draw(g, icon, x, y + lift + (ITEM_H - iconH) / 2.0, 3);

                        // Check mark when this task is done for today.
                        if ((doneMask & (1 << i)) != 0) {

                            g.setFill(Color.web("#2F9E5B"));
                            g.fillOval(x + ITEM_W - 9, y - 7, 15, 15);

                            g.setFill(Color.WHITE);
                            g.setFont(Font.font("Arial", FontWeight.BOLD, 10));
                            g.fillText("✔", x + ITEM_W - 1.5, y + 4);

                            g.setFont(Font.font("Arial", 9.5));
                        }
                    }
                }

                g.setFill(Color.web("#25445B"));
                g.fillText(tab == 0 ? seeds[i].label : tools[i].toolName,
                        x + ITEM_W / 2, y + ITEM_H + 11);
            }

            g.setFill(Color.web("#8793A6"));
            g.setFont(Font.font("Arial", 10));
            g.fillText(tab == 0
                            ? "Drag a seed bag into the pot"
                            : "Drag a tool onto the plant",
                    W / 2, H - 9);
        }
    }

    // ==========================================
    // PIXEL PLANT
    // ==========================================

    private static class PixelPlant extends Canvas {

        private static final int PIXEL = 4;

        private static final int GRID_HEIGHT = 26;

        // Rows above this are the plant; this row and below is the pot.
        private static final int POT_TOP = 13;

        private static final double START_X = 42;
        private static final double START_Y = 36;

        // The plant squashes and stretches from the bottom of the pot.
        private static final double PIVOT_X = 90;
        private static final double PIVOT_Y = START_Y + GRID_HEIGHT * PIXEL;

        // Where the soil is: plants grow up from here, and drops stop here.
        private static final double SOIL_Y = START_Y + POT_TOP * PIXEL;

        // Float position (bob) that triggers a squish.
        // The float ranges from -3 (top) to +3 (bottom).
        private static final double SQUISH_BOB = 2.5;

        // ---------- Pouring tools (watering can, plant food) ----------

        // Where a tool slides in from and back out to.
        private static final double HOME_X = 154;
        private static final double HOME_Y = 124;

        private static final double MOVE_TIME = 0.55;
        private static final double TILT_TIME = 0.30;

        private static class Pour {

            final String[] rows;
            final double pourX;
            final double pourY;
            final double angle;      // how far it tilts while pouring
            final double spoutDx;    // where the spout is, before rotating
            final double spoutDy;
            final double time;
            final int kind;          // 0 water, 1 plant food

            Pour(String[] rows, double pourX, double pourY, double angle,
                 double spoutDx, double spoutDy, double time, int kind) {

                this.rows = rows;
                this.pourX = pourX;
                this.pourY = pourY;
                this.angle = angle;
                this.spoutDx = spoutDx;
                this.spoutDy = spoutDy;
                this.time = time;
                this.kind = kind;
            }
        }

        private static final Pour WATER_POUR =
                new Pour(ToolArt.CAN, 123, 36, -40, -20, -12, 1.9, 0);

        private static final Pour FOOD_POUR =
                new Pour(ToolArt.FOOD, 128, 30, -125, 0, -22, 1.5, 1);

        private enum PourPhase {
            IDLE,
            MOVE_IN,
            TILT,
            POUR,
            UNTILT,
            MOVE_OUT
        }

        // ---------- Other tools (sun, clippers, hello) ----------

        private enum Action {
            NONE,
            SUN,
            TRIM,
            HELLO
        }

        // The pot is shared by every plant type (left half; the right half is mirrored).
        //   X outline   R rim   P body   Q shade
        private static final String[] POT_HALF_ROWS = {
                "...XXXXXXXXX",
                "...XRRRRRRRR",
                "...XRRRRRRRR",
                "...XXXXXXXXX",
                "....XPPPPPPP",
                "....XPPPPPPP",
                "....XPPPPPPP",
                "....XPPPPPPP",
                "....XPPPPPPP",
                "....XPPPPPPP",
                "....XQQQQQQQ",
                ".....XQQQQQQ",
                ".....XXXXXXX"
        };

        private static class Sparkle {
            double x;
            double y;
            double vy;
            double age;
            double life;
            boolean heart;
            Color color;
        }

        private final String[] topRows = new String[POT_TOP];
        private final String[] potRows = new String[POT_HALF_ROWS.length];

        private final GraphicsContext gc;

        private final Random random = new Random();

        private AnimationTimer animation;

        private PlantType type = null;   // null = empty pot
        private Color baseColor = Color.web("#5DBB63");

        private boolean dragging = false;
        private boolean happy = false;
        private boolean landing = false;
        private boolean jumping = false;
        private boolean dropHover = false;

        private double happyUntil = 0;
        private double nextExpressionChange = 0;
        private double nextBlink = 0;

        private double animationTime = 0;
        private double lastTime = -1;

        // Bounce physics
        private double bounceOffset = 0;
        private double bounceVelocity = 0;

        // Body scaling
        private double squashX = 1.0;
        private double squashY = 1.0;

        // Leaf sway, in sprite cells at the very top of the plant
        private double swayAmount = 0;

        // Growth: 0 = seedling, 1 = fully grown. shownGrowth eases toward growth,
        // so the plant visibly grows (and shrinks).
        private double growth = 0;
        private double shownGrowth = 0;

        // Self-squish
        private double selfSquishStart = -10;
        private double selfSquishUntil = 0;
        private double nextSelfSquish = 6;
        private boolean squishArmed = false;   // timer elapsed, waiting for the position
        private double lastBob = 0;            // float position from the previous frame

        // Pouring tool
        private PourPhase phase = PourPhase.IDLE;
        private Pour pour = null;
        private double phaseStart = 0;
        private double nextDrop = 0;
        private double toolX = HOME_X;
        private double toolY = HOME_Y;
        private double toolAngle = 0;
        private double toolAlpha = 0;
        private Runnable onPourFinished;

        // Sun, clippers and hello
        private Action action = Action.NONE;
        private double actionStart = 0;
        private double actionDuration = 0;
        private int lastSnip = -1;
        private double nextHeart = 0;
        private Runnable onActionFinished;

        // Falling bits: {x, y, speed, kind, sideways speed}
        //   kind 0 water, 1 plant food, 2 clipped leaves
        private final List<double[]> drops = new ArrayList<>();

        private final List<Sparkle> sparkles = new ArrayList<>();

        private enum Expression {
            SLEEPY,
            NORMAL,
            CURIOUS
        }

        private Expression expression = Expression.SLEEPY;

        // Leaf colors (derived from one base color; see applyPalette).
        private Color outline;
        private Color bodyColor;
        private Color shadowColor;
        private Color shineColor;
        private Color stemColor;

        // Petal / cap colors (the color of flowers and mushrooms)
        private Color petalColor;
        private Color petalShade;
        private Color petalOutline;

        // Pot colors (derived from one color; see setPotColor)
        private Color potOutline;
        private Color potRim;
        private Color potBody;
        private Color potShade;

        // Colors that never change
        private final Color centerColor = Color.web("#F7D154");
        private final Color centerShade = Color.web("#D9A62E");
        private final Color seedHeadColor = Color.web("#7A4A21");
        private final Color trunkColor = Color.web("#8A5A33");
        private final Color trunkDark = Color.web("#5C3A1E");
        private final Color mushroomStem = Color.web("#FFF3DC");
        private final Color mushroomStemShade = Color.web("#E3D2B0");

        private final Color soilDark = Color.web("#5C3A1E");
        private final Color soilLight = Color.web("#7A4E2B");

        private final Color darkColor = Color.web("#3B1E10");
        private final Color cheekColor = Color.web("#F4A6A6");

        PixelPlant() {

            super(180, 150);

            gc = getGraphicsContext2D();

            for (int i = 0; i < POT_HALF_ROWS.length; i++) {
                potRows[i] = mirror(POT_HALF_ROWS[i]);
            }

            setPotColor(Color.web("#D1743F"));

            buildTop();
            applyPalette();

            render();
        }

        // ======================================
        // TYPE + COLORS
        // ======================================

        // Mirror the left half to make a full row. The highlight only shows on
        // the left side, as if the light comes from the left.
        private static String mirror(String left) {

            StringBuilder right = new StringBuilder();

            for (int c = left.length() - 1; c >= 0; c--) {

                char ch = left.charAt(c);
                right.append(ch == 'l' ? 'g' : ch);
            }

            return left + right;
        }

        private void buildTop() {

            for (int i = 0; i < POT_TOP; i++) {
                topRows[i] = type == null ? "" : mirror(type.rows[i]);
            }
        }

        // Pass null for an empty pot.
        void setType(PlantType newType) {

            type = newType;

            buildTop();
            applyPalette();
        }

        void setBaseColor(Color base) {

            baseColor = base;

            applyPalette();
        }

        void setPotColor(Color base) {

            potBody = base;
            potRim = base.interpolate(Color.WHITE, 0.30);
            potShade = base.deriveColor(0, 1.0, 0.82, 1.0);
            potOutline = base.deriveColor(0, 1.0, 0.55, 1.0);
        }

        private void applyPalette() {

            // Flowers and mushrooms use the chosen color for petals or cap;
            // their leaves stay green.
            Color leafBase = type != null && type.usesPetals()
                    ? Color.web("#5DBB63")
                    : baseColor;

            bodyColor = leafBase;
            outline = leafBase.deriveColor(0, 1.0, 0.50, 1.0);
            shadowColor = leafBase.deriveColor(0, 1.0, 0.78, 1.0);
            stemColor = leafBase.deriveColor(0, 1.0, 0.72, 1.0);
            shineColor = leafBase.interpolate(Color.WHITE, 0.45);

            petalColor = baseColor;
            petalShade = baseColor.deriveColor(0, 1.0, 0.78, 1.0);
            petalOutline = baseColor.deriveColor(0, 1.0, 0.55, 1.0);
        }

        private Color colorFor(char c) {

            switch (c) {
                case 'o': return outline;
                case 'g': return bodyColor;
                case 'l': return shineColor;
                case 'd': return shadowColor;
                case 's': return stemColor;
                case 'u': return petalOutline;
                case 'f': return petalColor;
                case 'e': return petalShade;
                case 'y': return centerColor;
                case 'z': return centerShade;
                case 'n': return seedHeadColor;
                case 'b': return trunkColor;
                case 'B': return trunkDark;
                case 'w': return mushroomStem;
                case 'W': return mushroomStemShade;
                case 'X': return potOutline;
                case 'R': return potRim;
                case 'P': return potBody;
                case 'Q': return potShade;
                default: return null;
            }
        }

        // ======================================
        // GROWTH
        // ======================================

        // animate = true: the plant visibly grows or shrinks to the new size.
        void setGrowth(double newGrowth, boolean animate) {

            growth = Math.max(0.0, Math.min(1.0, newGrowth));

            if (animate) {
                selfSquish();   // a little pop as it changes size
            } else {
                shownGrowth = growth;
            }
        }

        // ======================================
        // EFFECTS
        // ======================================

        void setDropHover(boolean hover) {
            dropHover = hover;
        }

        // A burst of little sparkles around the plant.
        void burst(Color color, int count) {

            for (int i = 0; i < count; i++) {

                Sparkle s = new Sparkle();

                s.x = 54 + random.nextDouble() * 72;
                s.y = 54 + random.nextDouble() * 64;
                s.vy = -(14 + random.nextDouble() * 26);
                s.life = 0.7 + random.nextDouble() * 0.6;
                s.color = color;

                sparkles.add(s);
            }
        }

        private void spawnHeart() {

            Sparkle s = new Sparkle();

            s.x = 62 + random.nextDouble() * 56;
            s.y = 84;
            s.vy = -(34 + random.nextDouble() * 24);
            s.life = 1.0 + random.nextDouble() * 0.4;
            s.heart = true;
            s.color = random.nextBoolean()
                    ? Color.web("#F2708A")
                    : Color.web("#FF9BB0");

            sparkles.add(s);
        }

        private void updateSparkles(double dt) {

            Iterator<Sparkle> it = sparkles.iterator();

            while (it.hasNext()) {

                Sparkle s = it.next();

                s.age += dt;
                s.y += s.vy * dt;

                if (s.age >= s.life) {
                    it.remove();
                }
            }
        }

        // ======================================
        // TOOLS
        // ======================================

        // True while any tool is being used.
        boolean isBusy() {
            return phase != PourPhase.IDLE || action != Action.NONE;
        }

        boolean startWatering(Runnable onFinished) {
            return startPour(WATER_POUR, onFinished);
        }

        boolean startFeeding(Runnable onFinished) {
            return startPour(FOOD_POUR, onFinished);
        }

        boolean startSun(Runnable onFinished) {
            return startAction(Action.SUN, 2.6, onFinished);
        }

        boolean startTrimming(Runnable onFinished) {
            return startAction(Action.TRIM, 2.2, onFinished);
        }

        boolean startHello(Runnable onFinished) {
            return startAction(Action.HELLO, 1.8, onFinished);
        }

        // Slides a pouring tool over the plant. onFinished runs when the pour ends.
        private boolean startPour(Pour newPour, Runnable onFinished) {

            if (isBusy() || dragging) {
                return false;
            }

            pour = newPour;
            onPourFinished = onFinished;
            nextPhase(PourPhase.MOVE_IN);

            return true;
        }

        private boolean startAction(Action newAction, double duration, Runnable onFinished) {

            if (isBusy() || dragging) {
                return false;
            }

            action = newAction;
            actionStart = animationTime;
            actionDuration = duration;
            onActionFinished = onFinished;
            lastSnip = -1;
            nextHeart = animationTime;

            return true;
        }

        // Stops whatever tool is in use (when the plant is picked up).
        void cancelActivity() {

            phase = PourPhase.IDLE;
            pour = null;
            onPourFinished = null;

            action = Action.NONE;
            onActionFinished = null;

            drops.clear();
        }

        private void nextPhase(PourPhase newPhase) {

            phase = newPhase;
            phaseStart = animationTime;
        }

        private static double ease(double x) {

            double t = Math.max(0.0, Math.min(1.0, x));

            return t * t * (3 - 2 * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private void updateActivity(double dt) {

            // Growth eases toward its target so the plant visibly grows.
            shownGrowth += (growth - shownGrowth) * Math.min(1.0, dt * 2.5);

            updatePour();
            updateAction();
            updateDrops(dt);
        }

        private void updatePour() {

            if (phase == PourPhase.IDLE || pour == null) {
                return;
            }

            double t = animationTime - phaseStart;

            switch (phase) {

                case MOVE_IN: {

                    double p = ease(t / MOVE_TIME);

                    toolX = lerp(HOME_X, pour.pourX, p);
                    toolY = lerp(HOME_Y, pour.pourY, p) - Math.sin(p * Math.PI) * 14;
                    toolAngle = 0;
                    toolAlpha = p;

                    if (t >= MOVE_TIME) {
                        nextPhase(PourPhase.TILT);
                    }
                    break;
                }

                case TILT: {

                    toolX = pour.pourX;
                    toolY = pour.pourY;
                    toolAngle = pour.angle * ease(t / TILT_TIME);
                    toolAlpha = 1;

                    if (t >= TILT_TIME) {
                        nextPhase(PourPhase.POUR);
                        nextDrop = animationTime;
                    }
                    break;
                }

                case POUR: {

                    toolX = pour.pourX;
                    toolY = pour.pourY;
                    toolAngle = pour.angle + Math.sin(t * 14) * 1.5;
                    toolAlpha = 1;

                    // The plant is happy while it drinks or eats.
                    happy = true;
                    happyUntil = animationTime + 0.6;

                    if (nextDrop < animationTime - 0.2) {
                        nextDrop = animationTime;
                    }

                    while (animationTime >= nextDrop) {
                        spawnPourDrop();
                        nextDrop += pour.kind == 0 ? 0.05 : 0.06;
                    }

                    if (t >= pour.time) {

                        nextPhase(PourPhase.UNTILT);

                        if (onPourFinished != null) {

                            Runnable finished = onPourFinished;
                            onPourFinished = null;
                            finished.run();
                        }
                    }
                    break;
                }

                case UNTILT: {

                    toolX = pour.pourX;
                    toolY = pour.pourY;
                    toolAngle = pour.angle * (1.0 - ease(t / TILT_TIME));
                    toolAlpha = 1;

                    if (t >= TILT_TIME) {
                        nextPhase(PourPhase.MOVE_OUT);
                    }
                    break;
                }

                case MOVE_OUT: {

                    double p = ease(t / MOVE_TIME);

                    toolX = lerp(pour.pourX, HOME_X, p);
                    toolY = lerp(pour.pourY, HOME_Y, p) - Math.sin(p * Math.PI) * 14;
                    toolAngle = 0;
                    toolAlpha = 1.0 - p;

                    if (t >= MOVE_TIME) {
                        phase = PourPhase.IDLE;
                        pour = null;
                    }
                    break;
                }

                default:
                    break;
            }
        }

        private void spawnPourDrop() {

            double rad = Math.toRadians(toolAngle);

            // Spout position after the tool is rotated
            double rx = pour.spoutDx * Math.cos(rad) - pour.spoutDy * Math.sin(rad);
            double ry = pour.spoutDx * Math.sin(rad) + pour.spoutDy * Math.cos(rad);

            double spread = pour.kind == 0 ? 4 : 8;

            double x = toolX + rx + (random.nextDouble() - 0.5) * spread;
            double y = toolY + ry + 4;

            drops.add(new double[]{x, y, 40, pour.kind, 0});
        }

        private double actionAlpha(double t) {

            return Math.max(0.0, Math.min(1.0,
                    Math.min(t / 0.25, (actionDuration - t) / 0.25)));
        }

        private double clipperX(double t) {
            return 112 + Math.sin(t * 2.2) * 6;
        }

        private double clipperY(double t) {
            return 62 + Math.cos(t * 2.2) * 4;
        }

        private void updateAction() {

            if (action == Action.NONE) {
                return;
            }

            double t = animationTime - actionStart;

            // The plant is happy while it's being looked after.
            happy = true;
            happyUntil = animationTime + 0.6;

            if (action == Action.TRIM) {

                // Every second snip (when the blades close), clippings fall.
                int snip = (int) (t / 0.16);

                if (snip != lastSnip) {

                    lastSnip = snip;

                    if (snip % 2 == 1) {

                        for (int i = 0; i < 2; i++) {

                            drops.add(new double[]{
                                    clipperX(t) - 8 + random.nextDouble() * 8,
                                    clipperY(t) + 2,
                                    10,
                                    2,
                                    (random.nextDouble() - 0.5) * 40
                            });
                        }
                    }
                }

            } else if (action == Action.HELLO) {

                while (animationTime >= nextHeart) {
                    spawnHeart();
                    nextHeart += 0.14;
                }
            }

            if (t >= actionDuration) {

                action = Action.NONE;

                if (onActionFinished != null) {

                    Runnable finished = onActionFinished;
                    onActionFinished = null;
                    finished.run();
                }
            }
        }

        // Falling water, plant food and clippings stop when they reach the soil.
        private void updateDrops(double dt) {

            Iterator<double[]> it = drops.iterator();

            while (it.hasNext()) {

                double[] drop = it.next();

                double gravity = drop[3] == 2 ? 320 : 520;

                drop[2] += gravity * dt;
                drop[1] += drop[2] * dt;
                drop[0] += drop[4] * dt;

                if (drop[1] >= SOIL_Y) {
                    it.remove();
                }
            }
        }

        // ======================================
        // ANIMATION LOOP
        // ======================================

        void startAnimation() {

            animation = new AnimationTimer() {

                @Override
                public void handle(long now) {

                    animationTime = now / 1_000_000_000.0;

                    double dt = lastTime < 0
                            ? 0
                            : Math.min(0.05, animationTime - lastTime);

                    lastTime = animationTime;

                    updateActivity(dt);
                    updateSparkles(dt);
                    updateAnimation();
                    render();
                }
            };

            animation.start();

            nextExpressionChange = 5;
            nextBlink = 3;
            nextSelfSquish = 6;
            squishArmed = false;
        }

        void stopAnimation() {

            if (animation != null) {
                animation.stop();
            }
        }

        // Idle floating position, from -3 (top) to +3 (bottom).
        private double currentBob() {
            return Math.sin(animationTime * 2.5) * 3;
        }

        private void updateAnimation() {

            if (dragging) {
                lastBob = currentBob();   // avoid a false trigger right after release
                return;
            }

            if (happy && animationTime > happyUntil) {
                happy = false;
            }

            if (!happy && animationTime > nextExpressionChange) {

                int choice = random.nextInt(3);

                if (choice == 0) {
                    expression = Expression.SLEEPY;
                } else if (choice == 1) {
                    expression = Expression.NORMAL;
                } else {
                    expression = Expression.CURIOUS;
                }

                nextExpressionChange =
                        animationTime + 4 + random.nextInt(6);
            }

            if (animationTime > nextBlink) {
                nextBlink = animationTime + 3 + random.nextInt(5);
            }

            // The timer arms the next squish...
            if (!squishArmed && animationTime > nextSelfSquish) {
                squishArmed = true;
            }

            // ...and it fires when the plant floats down to the trigger position.
            double bob = currentBob();
            boolean hitPosition = lastBob < SQUISH_BOB && bob >= SQUISH_BOB;

            if (squishArmed && !landing && !jumping && hitPosition) {

                selfSquish();

                squishArmed = false;
                nextSelfSquish =
                        animationTime + 5 + random.nextInt(7);
            }

            lastBob = bob;   // must run last, every frame
        }

        // ======================================
        // SELF-SQUISH
        // ======================================

        void selfSquish() {

            if (dragging) {
                return;
            }

            selfSquishStart = animationTime;
            selfSquishUntil = animationTime + 0.8;
        }

        private double getSelfSquishAmount() {

            if (animationTime < selfSquishStart ||
                    animationTime > selfSquishUntil) {
                return 0;
            }

            double progress =
                    (animationTime - selfSquishStart) / 0.8;

            return Math.sin(progress * Math.PI);
        }

        // ======================================
        // JUMPING
        // ======================================

        void startJump() {

            jumping = true;

            squashX = 0.85;
            squashY = 1.25;

            happy = true;
            happyUntil = animationTime + 1.5;
        }

        void stopJump() {

            jumping = false;

            squashX = 1.25;
            squashY = 0.70;

            landing = true;
            bounceOffset = -15;
            bounceVelocity = -3;
        }

        // ======================================
        // BOUNCE PHYSICS
        // ======================================

        private void updateBounce() {

            if (dragging) {

                // Stretched tall while held (kept small enough to fit in the canvas).
                squashX = 0.78;
                squashY = 1.30;

                return;
            }

            if (!landing) {

                squashX += (1.0 - squashX) * 0.15;
                squashY += (1.0 - squashY) * 0.15;

                return;
            }

            bounceVelocity += 0.65;
            bounceOffset += bounceVelocity;

            if (bounceOffset >= 0) {

                bounceOffset = 0;

                if (Math.abs(bounceVelocity) > 1.5) {

                    bounceVelocity = -bounceVelocity * 0.62;

                    squashX = 1.42;
                    squashY = 0.48;

                } else {

                    bounceVelocity = 0;
                    landing = false;
                }
            }

            squashX += (1.0 - squashX) * 0.18;
            squashY += (1.0 - squashY) * 0.18;
        }

        // ======================================
        // RENDERING
        // ======================================

        private void render() {

            gc.clearRect(0, 0, getWidth(), getHeight());

            updateBounce();

            // Whole-pixel float so the pixel art stays crisp.
            double bob = 0;

            if (!dragging && !landing && !jumping) {
                bob = Math.round(currentBob());
            }

            // Leaves sway gently, flutter faster when happy, and hold still while dragged.
            if (dragging) {
                swayAmount = 0;
            } else if (happy) {
                swayAmount = Math.sin(animationTime * 8) * 1.4;
            } else {
                swayAmount = Math.sin(animationTime * 1.6) * 1.4;
            }

            double finalX = squashX;
            double finalY = squashY;

            double selfSquish = getSelfSquishAmount();

            finalX *= 1.0 + selfSquish * 0.40;
            finalY *= 1.0 - selfSquish * 0.45;

            if (happy && !dragging) {

                double happyBounce = Math.abs(
                        Math.sin(animationTime * 9)
                );

                finalY *= 1.0 + happyBounce * 0.05;
                finalX *= 1.0 - happyBounce * 0.025;
            }

            // The sun shines from behind the plant.
            if (action == Action.SUN) {
                drawSun();
            }

            gc.save();

            gc.translate(
                    PIVOT_X,
                    PIVOT_Y + bob + bounceOffset
            );

            gc.scale(finalX, finalY);

            gc.translate(-PIVOT_X, -PIVOT_Y);

            drawShadow(bob);

            if (type == null) {
                drawMound(bob);
            } else {
                drawTop(bob);
            }

            drawPot(bob);
            drawFace(bob);

            gc.restore();

            drawDropTarget();

            // Tools, falling bits and sparkles are not squashed with the plant.
            drawDrops();
            drawPourTool();
            drawActionTool();
            drawSparkles();
        }

        // ======================================
        // PIXEL DRAWING
        // ======================================

        // One pixel of the pot (always full size).
        private void pixel(int x, int y, Color color, double bob) {

            gc.setFill(color);

            gc.fillRect(
                    START_X + x * PIXEL,
                    START_Y + y * PIXEL + bob,
                    PIXEL,
                    PIXEL
            );
        }

        // One cell of the plant. The plant grows from the soil, so cells get
        // bigger as it grows. Edges are rounded to whole pixels to stay crisp.
        private void topCell(int col, int row, Color color, double bob, double size) {

            int x0 = (int) Math.round(PIVOT_X + (col - 12) * size);
            int x1 = (int) Math.round(PIVOT_X + (col - 11) * size);
            int y0 = (int) Math.round(SOIL_Y - (POT_TOP - row) * size);
            int y1 = (int) Math.round(SOIL_Y - (POT_TOP - 1 - row) * size);

            gc.setFill(color);

            gc.fillRect(
                    x0,
                    y0 + bob,
                    Math.max(1, x1 - x0),
                    Math.max(1, y1 - y0)
            );
        }

        // Whole-cell sideways shift for plant rows: biggest at the top, none at the soil.
        private int leafShift(int row) {

            if (type == null) {
                return 0;
            }

            double factor = (POT_TOP - row) / (double) POT_TOP;

            return (int) Math.round(swayAmount * type.sway * factor);
        }

        private void drawShadow(double bob) {

            gc.setFill(Color.web("#25445B", 0.16));

            gc.fillRect(
                    56,
                    141 + bob,
                    68,
                    4
            );

            gc.fillRect(
                    64,
                    145 + bob,
                    52,
                    3
            );
        }

        // A little heap of soil for an empty pot.
        private void drawMound(double bob) {

            for (int x = 6; x <= 17; x++) {
                pixel(x, 12, soilDark, bob);
            }

            for (int x = 8; x <= 15; x++) {
                pixel(x, 11, soilDark, bob);
            }

            for (int x = 10; x <= 13; x++) {
                pixel(x, 10, soilDark, bob);
            }

            pixel(9, 11, soilLight, bob);
            pixel(10, 11, soilLight, bob);
            pixel(10, 10, soilLight, bob);
            pixel(8, 12, soilLight, bob);
            pixel(9, 12, soilLight, bob);
            pixel(14, 12, soilLight, bob);
        }

        private void drawTop(double bob) {

            // Cell size: 1.5 pixels for a seedling up to 4 when fully grown.
            double size = 1.5 + 2.5 * shownGrowth;

            for (int y = 0; y < POT_TOP; y++) {

                String row = topRows[y];
                int shift = leafShift(y);

                for (int x = 0; x < row.length(); x++) {

                    Color color = colorFor(row.charAt(x));

                    if (color != null) {
                        topCell(x + shift, y, color, bob, size);
                    }
                }
            }
        }

        private void drawPot(double bob) {

            for (int i = 0; i < potRows.length; i++) {

                String row = potRows[i];

                for (int x = 0; x < row.length(); x++) {

                    Color color = colorFor(row.charAt(x));

                    if (color != null) {
                        pixel(x, POT_TOP + i, color, bob);
                    }
                }
            }
        }

        // Dashed green outline around the plant while a seed bag or tool hovers over it.
        private void drawDropTarget() {

            if (!dropHover) {
                return;
            }

            double pulse = 0.65 + 0.35 * Math.sin(animationTime * 9);

            gc.setLineDashes(7, 5);
            gc.setLineWidth(3);
            gc.setStroke(Color.web("#2F9E5B", pulse));

            gc.strokeRoundRect(46, 54, 88, 92, 12, 12);

            gc.setLineDashes((double[]) null);
        }

        private void drawSparkles() {

            for (Sparkle s : sparkles) {

                double alpha = Math.max(0.0, 1.0 - s.age / s.life);

                int x = (int) Math.round(s.x);
                int y = (int) Math.round(s.y);

                gc.setGlobalAlpha(alpha);

                gc.setFill(s.color);

                if (s.heart) {

                    // A tiny pixel heart, 10 x 8
                    gc.fillRect(x - 5, y - 4, 4, 2);
                    gc.fillRect(x + 1, y - 4, 4, 2);
                    gc.fillRect(x - 5, y - 2, 10, 2);
                    gc.fillRect(x - 3, y, 6, 2);
                    gc.fillRect(x - 1, y + 2, 2, 2);

                } else {

                    gc.fillRect(x - 3, y, 7, 2);
                    gc.fillRect(x, y - 3, 2, 7);

                    gc.setFill(Color.web("#25445B", 0.5));
                    gc.fillRect(x, y, 2, 2);
                }
            }

            gc.setGlobalAlpha(1.0);
        }

        // ======================================
        // TOOLS: DRAWING
        // ======================================

        private void drawSun() {

            double t = animationTime - actionStart;
            double alpha = actionAlpha(t);

            double cx = 90;
            double cy = 28;

            // Soft glow
            gc.setGlobalAlpha(alpha * 0.14);
            gc.setFill(Color.web("#FFE680"));
            gc.fillOval(cx - 46, cy - 46, 92, 92);

            gc.setGlobalAlpha(alpha * 0.16);
            gc.fillOval(cx - 34, cy - 34, 68, 68);

            gc.setGlobalAlpha(1.0);

            // The sun pulses and its rays slowly turn.
            double px = 3.6 + 0.3 * Math.sin(t * 6);

            ToolArt.drawCentered(gc, ToolArt.SUN, cx, cy, px, t * 35, alpha);
        }

        private void drawPourTool() {

            if (phase == PourPhase.IDLE || pour == null) {
                return;
            }

            ToolArt.drawCentered(gc, pour.rows, toolX, toolY, PIXEL, toolAngle, toolAlpha);
        }

        private void drawActionTool() {

            if (action == Action.NONE) {
                return;
            }

            double t = animationTime - actionStart;
            double alpha = actionAlpha(t);

            if (action == Action.TRIM) {

                // The blades open and close as they snip.
                boolean open = ((int) (t / 0.16)) % 2 == 0;

                ToolArt.drawCentered(gc,
                        open ? ToolArt.CLIPPERS_OPEN : ToolArt.CLIPPERS_CLOSED,
                        clipperX(t), clipperY(t), PIXEL, -25, alpha);

            } else if (action == Action.HELLO) {

                double px = 4 + 0.7 * Math.abs(Math.sin(t * 8));

                ToolArt.drawCentered(gc, ToolArt.HEART, 90, 24, px, 0, alpha);
            }
        }

        private void drawDrops() {

            for (double[] drop : drops) {

                int x = (int) Math.round(drop[0]);
                int y = (int) Math.round(drop[1]);

                if (drop[3] == 0) {

                    // Water
                    gc.setFill(Color.web("#7FCBFA"));
                    gc.fillRect(x, y, 3, 5);

                    gc.setFill(Color.web("#D6F1FF"));
                    gc.fillRect(x, y, 1, 2);

                } else if (drop[3] == 1) {

                    // Plant food
                    gc.setFill(Color.web("#8A5A33"));
                    gc.fillRect(x, y, 3, 3);

                    gc.setFill(Color.web("#B07A4A"));
                    gc.fillRect(x, y, 1, 1);

                } else {

                    // Clipped leaves
                    gc.setFill(Color.web("#5DBB63"));
                    gc.fillRect(x, y, 4, 2);

                    gc.setFill(Color.web("#3E8E46"));
                    gc.fillRect(x + 2, y + 1, 2, 1);
                }
            }
        }

        // ======================================
        // FACE (drawn on the pot)
        // ======================================

        private void drawFace(double bob) {

            drawCheeks(bob);

            boolean blink = !dragging &&
                    animationTime > nextBlink - 0.14 &&
                    animationTime < nextBlink;

            if (happy) {
                drawHappyEyes(bob);

            } else if (dragging || blink) {
                drawClosedEyes(bob);

            } else {

                switch (expression) {

                    case SLEEPY:
                        drawSleepyEyes(bob);
                        break;

                    case NORMAL:
                        drawNormalEyes(bob);
                        break;

                    case CURIOUS:
                        drawCuriousEyes(bob);
                        break;
                }
            }

            drawMouth(bob);
        }

        private void drawCheeks(double bob) {

            for (int y = 21; y <= 22; y++) {

                pixel(5, y, cheekColor, bob);
                pixel(6, y, cheekColor, bob);

                pixel(17, y, cheekColor, bob);
                pixel(18, y, cheekColor, bob);
            }
        }

        private void drawSleepyEyes(double bob) {

            pixel(8, 19, darkColor, bob);
            pixel(8, 20, darkColor, bob);
            pixel(9, 20, darkColor, bob);

            pixel(15, 19, darkColor, bob);
            pixel(15, 20, darkColor, bob);
            pixel(14, 20, darkColor, bob);
        }

        private void drawNormalEyes(double bob) {

            for (int y = 18; y <= 20; y++) {

                pixel(8, y, darkColor, bob);
                pixel(9, y, darkColor, bob);

                pixel(14, y, darkColor, bob);
                pixel(15, y, darkColor, bob);
            }
        }

        private void drawCuriousEyes(double bob) {

            for (int y = 17; y <= 20; y++) {

                pixel(8, y, darkColor, bob);
                pixel(9, y, darkColor, bob);

                pixel(14, y, darkColor, bob);
                pixel(15, y, darkColor, bob);
            }
        }

        private void drawClosedEyes(double bob) {

            for (int x = 7; x <= 10; x++) {
                pixel(x, 20, darkColor, bob);
            }

            for (int x = 13; x <= 16; x++) {
                pixel(x, 20, darkColor, bob);
            }
        }

        private void drawHappyEyes(double bob) {

            pixel(7, 20, darkColor, bob);
            pixel(8, 19, darkColor, bob);
            pixel(9, 19, darkColor, bob);
            pixel(10, 20, darkColor, bob);

            pixel(13, 20, darkColor, bob);
            pixel(14, 19, darkColor, bob);
            pixel(15, 19, darkColor, bob);
            pixel(16, 20, darkColor, bob);
        }

        private void drawMouth(double bob) {

            pixel(11, 22, darkColor, bob);
            pixel(12, 22, darkColor, bob);

            if (happy) {

                pixel(10, 21, darkColor, bob);
                pixel(13, 21, darkColor, bob);
            }
        }

        // ======================================
        // USER INTERACTIONS
        // ======================================

        void happy() {

            happy = true;
            happyUntil = animationTime + 2.5;
        }

        void startDragging() {

            // Picking the plant up cancels any tool in use.
            cancelActivity();

            dragging = true;
            landing = false;
            jumping = false;

            bounceOffset = 0;
            bounceVelocity = 0;

            squashX = 0.78;
            squashY = 1.30;

            happy = false;
        }

        void stopDragging() {

            dragging = false;

            // No bounce: just squish flat and ease back to normal.
            landing = false;
            bounceOffset = 0;
            bounceVelocity = 0;

            squashX = 1.42;
            squashY = 0.48;

            happy = true;
            happyUntil = animationTime + 1.5;
        }
    }
}