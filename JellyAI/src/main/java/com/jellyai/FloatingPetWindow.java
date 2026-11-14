package com.jellyai;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Optional;
import java.util.Random;
import java.util.prefs.Preferences;

public class FloatingPetWindow {

    private static final String DEFAULT_NAME = "Jelly";
    private static final String DEFAULT_COLOR = "#62D8E7";
    private static final int MAX_NAME_LENGTH = 20;

    private final Stage stage;
    private final StackPane root;
    private final PixelJelly jelly;
    private final Label speechBubble;
    private final ContextMenu menu = new ContextMenu();

    private final Runnable dashboardAction;
    private final Random random = new Random();

    // Saved between runs (stored in the user's Windows registry by Java).
    private final Preferences prefs =
            Preferences.userNodeForPackage(FloatingPetWindow.class);

    private String petName;
    private Color petColor;

    private double dragOffsetX;
    private double dragOffsetY;

    private Timeline bubbleTimer;
    private Timeline jumpTimeline;
    private PauseTransition behaviorTimer;

    private boolean dragging = false;

    // {name} is replaced with the jelly's current name.
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
            "Boing!",
            "Did you miss me?",
            "I'm feeling bouncy today!",
            "Let's do something fun!",
            "I believe in you!",
            "Just checking on you!",
            "Wheeeee!",
            "Did you see that jump?",
            "I'm a little squishy!",
            "Hello again!",
            "Boop!",
            "Right-click me to change my name or color!"
    };

    public FloatingPetWindow(Runnable dashboardAction) {

        this.dashboardAction = dashboardAction;

        petName = prefs.get("name", DEFAULT_NAME);
        petColor = parseColor(prefs.get("color", DEFAULT_COLOR));

        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle(petName);

        jelly = new PixelJelly();
        jelly.setBaseColor(petColor);

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

        root = new StackPane(jelly, speechBubble);
        root.setPrefSize(190, 180);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: transparent;");

        StackPane.setAlignment(speechBubble, Pos.TOP_CENTER);
        speechBubble.setTranslateY(5);

        Scene scene = new Scene(root, 190, 180);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.setWidth(190);
        stage.setHeight(180);
        stage.setX(900);
        stage.setY(500);

        configureMenu();
        configureDragging();
        configureClicking();

        jelly.startAnimation();
    }

    public void show() {
        stage.show();
        scheduleRandomBehavior();
    }

    // Lets the dashboard bubble attach itself above the jelly.
    public Stage getStage() {
        return stage;
    }

    public void close() {

        jelly.stopAnimation();

        if (bubbleTimer != null) {
            bubbleTimer.stop();
        }

        if (jumpTimeline != null) {
            jumpTimeline.stop();
        }

        if (behaviorTimer != null) {
            behaviorTimer.stop();
        }

        menu.hide();
        stage.close();
    }

    // ==========================================
    // CUSTOMIZATION (NAME + COLOR)
    // ==========================================

    private void configureMenu() {

        MenuItem rename = new MenuItem("Change name...");
        rename.setOnAction(event -> askForName());

        MenuItem recolor = new MenuItem("Change color...");
        recolor.setOnAction(event -> chooseColor());

        MenuItem reset = new MenuItem("Reset name and color");
        reset.setOnAction(event -> resetLook());

        menu.getItems().addAll(rename, recolor, reset);

        root.setOnContextMenuRequested(event -> {
            menu.show(root, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void askForName() {

        TextInputDialog dialog = new TextInputDialog(petName);
        dialog.initOwner(stage);
        dialog.setTitle("Name your jelly");
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
        prefs.put("name", petName);
        stage.setTitle(petName);

        showMessage("I love my new name, " + petName + "!");
        jelly.happy();
    }

    private void chooseColor() {

        Color original = petColor;

        ColorPicker picker = new ColorPicker(original);

        // Live preview while the picker is open.
        picker.setOnAction(event -> jelly.setBaseColor(picker.getValue()));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Jelly color");
        dialog.setHeaderText("Pick " + petName + "'s color");
        dialog.getDialogPane().setContent(picker);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {

            petColor = picker.getValue();
            prefs.put("color", toHex(petColor));

            jelly.setBaseColor(petColor);
            jelly.happy();

        } else {
            // Cancelled: undo the preview.
            jelly.setBaseColor(original);
        }
    }

    private void resetLook() {

        petName = DEFAULT_NAME;
        petColor = parseColor(DEFAULT_COLOR);

        prefs.put("name", petName);
        prefs.put("color", DEFAULT_COLOR);

        stage.setTitle(petName);
        jelly.setBaseColor(petColor);

        showMessage("Back to " + petName + "!");
    }

    private static Color parseColor(String hex) {

        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web(DEFAULT_COLOR);
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
    // DRAGGING
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

            jelly.startDragging();
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

            jelly.stopDragging();
            scheduleRandomBehavior();
        });
    }

    // ==========================================
    // CLICKING
    // ==========================================

    private void configureClicking() {

        jelly.setOnMouseClicked(event -> {

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (event.getClickCount() >= 2) {

                hideBubble();

                if (dashboardAction != null) {
                    dashboardAction.run();
                }

            } else {

                showRandomMessage();
                jelly.happy();
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

            if (!dragging && stage.isShowing()) {

                int behavior = random.nextInt(3);

                if (behavior == 0) {

                    // Jump to another place.
                    jumpAround();

                } else if (behavior == 1) {

                    // Say something.
                    showRandomMessage();

                } else {

                    // Squish and make a little comment.
                    jelly.selfSquish();

                    String[] squishMessages = {
                            "SQUISH!",
                            "Boing!",
                            "I'm so squishy!",
                            "Hehehe!",
                            "Wobble wobble!"
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

    private void jumpAround() {

        if (dragging || !stage.isShowing()) {
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

        jelly.startJump();

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
            jelly.stopJump();
            scheduleRandomBehavior();
        });

        jumpTimeline.play();
    }

    // ==========================================
    // PIXEL JELLY
    // ==========================================

    private static class PixelJelly extends Canvas {

        private static final int PIXEL = 5;

        private static final int GRID_WIDTH = 28;
        private static final int GRID_HEIGHT = 18;

        private static final double START_X = 20;
        private static final double START_Y = 25;

        // Float position (bob) that triggers a squish.
        // The float ranges from -3 (top) to +3 (bottom).
        private static final double SQUISH_BOB = 2.5;

        private final GraphicsContext gc;

        private final Random random = new Random();

        private AnimationTimer animation;

        private boolean dragging = false;
        private boolean happy = false;
        private boolean landing = false;
        private boolean jumping = false;

        private double happyUntil = 0;
        private double nextExpressionChange = 0;
        private double nextBlink = 0;

        private double animationTime = 0;

        // Bounce physics
        private double bounceOffset = 0;
        private double bounceVelocity = 0;

        // Body scaling
        private double squashX = 1.0;
        private double squashY = 1.0;

        // Self-squish
        private double selfSquishStart = -10;
        private double selfSquishUntil = 0;
        private double nextSelfSquish = 6;
        private boolean squishArmed = false;   // timer elapsed, waiting for the position
        private double lastBob = 0;            // float position from the previous frame

        private enum Expression {
            SLEEPY,
            NORMAL,
            CURIOUS
        }

        private Expression expression = Expression.SLEEPY;

        private final int[] leftEdges = {
                12, 10, 8, 6, 4, 3,
                2, 1, 1, 0, 0, 0,
                1, 2, 3, 4, 6, 9
        };

        private final int[] rightEdges = {
                15, 17, 19, 21, 23, 24,
                25, 26, 26, 27, 27, 27,
                26, 25, 24, 23, 21, 18
        };

        // Body colors are all derived from one base color (see setBaseColor).
        private Color outline;
        private Color bodyColor;
        private Color shadowColor;
        private Color shineColor;

        private final Color darkColor = Color.web("#25445B");
        private final Color cheekColor = Color.web("#F59BAF");

        PixelJelly() {

            super(180, 150);

            gc = getGraphicsContext2D();

            setBaseColor(Color.web(DEFAULT_COLOR));

            render();
        }

        // ======================================
        // COLOR
        // ======================================

        void setBaseColor(Color base) {

            bodyColor = base;
            outline = base.deriveColor(0, 1.0, 0.62, 1.0);
            shadowColor = base.deriveColor(0, 1.0, 0.85, 1.0);
            shineColor = base.interpolate(Color.WHITE, 0.65);
        }

        // ======================================
        // ANIMATION LOOP
        // ======================================

        void startAnimation() {

            animation = new AnimationTimer() {

                @Override
                public void handle(long now) {

                    animationTime = now / 1_000_000_000.0;

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

            // ...and it fires when Jelly floats down to the trigger position.
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

                squashX = 0.70;
                squashY = 1.42;

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

            double bob = 0;

            if (!dragging && !landing && !jumping) {
                bob = currentBob();
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

            gc.save();

            gc.translate(
                    90,
                    75 + bob + bounceOffset
            );

            gc.scale(finalX, finalY);

            gc.translate(-90, -75);

            drawShadow(bob);
            drawBody(bob);
            drawHighlight(bob);
            drawFace(bob);

            gc.restore();
        }

        // ======================================
        // PIXEL DRAWING
        // ======================================

        private void pixel(int x, int y, Color color, double bob) {

            gc.setFill(color);

            gc.fillRect(
                    START_X + x * PIXEL,
                    START_Y + y * PIXEL + bob,
                    PIXEL,
                    PIXEL
            );
        }

        private void drawShadow(double bob) {

            gc.setFill(Color.web("#25445B", 0.16));

            gc.fillRect(
                    47,
                    121 + bob,
                    85,
                    5
            );

            gc.fillRect(
                    57,
                    126 + bob,
                    65,
                    3
            );
        }

        private void drawBody(double bob) {

            for (int y = 0; y < GRID_HEIGHT; y++) {

                for (int x = leftEdges[y]; x <= rightEdges[y]; x++) {
                    pixel(x, y, outline, bob);
                }
            }

            for (int y = 1; y < GRID_HEIGHT - 1; y++) {

                for (int x = leftEdges[y] + 1;
                     x < rightEdges[y];
                     x++) {

                    pixel(x, y, bodyColor, bob);
                }
            }

            for (int x = 5; x <= 22; x++) {
                pixel(x, 15, shadowColor, bob);
            }

            for (int x = 8; x <= 19; x++) {
                pixel(x, 16, shadowColor, bob);
            }
        }

        private void drawHighlight(double bob) {

            pixel(8, 5, shineColor, bob);
            pixel(9, 4, shineColor, bob);
            pixel(10, 4, shineColor, bob);

            pixel(7, 6, shineColor, bob);
            pixel(8, 6, shineColor, bob);

            pixel(9, 7, shineColor, bob);
        }

        // ======================================
        // FACE
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

            pixel(5, 12, cheekColor, bob);
            pixel(6, 12, cheekColor, bob);
            pixel(7, 12, cheekColor, bob);

            pixel(4, 13, cheekColor, bob);
            pixel(5, 13, cheekColor, bob);
            pixel(6, 13, cheekColor, bob);

            pixel(21, 12, cheekColor, bob);
            pixel(22, 12, cheekColor, bob);
            pixel(23, 12, cheekColor, bob);

            pixel(21, 13, cheekColor, bob);
            pixel(22, 13, cheekColor, bob);
            pixel(23, 13, cheekColor, bob);
        }

        private void drawSleepyEyes(double bob) {

            pixel(8, 9, darkColor, bob);
            pixel(9, 10, darkColor, bob);
            pixel(10, 10, darkColor, bob);
            pixel(11, 10, darkColor, bob);

            pixel(17, 10, darkColor, bob);
            pixel(18, 10, darkColor, bob);
            pixel(19, 10, darkColor, bob);
            pixel(20, 9, darkColor, bob);
        }

        private void drawNormalEyes(double bob) {

            for (int x = 8; x <= 10; x++) {
                pixel(x, 8, darkColor, bob);
                pixel(x, 9, darkColor, bob);
            }

            for (int x = 17; x <= 19; x++) {
                pixel(x, 8, darkColor, bob);
                pixel(x, 9, darkColor, bob);
            }
        }

        private void drawCuriousEyes(double bob) {

            pixel(8, 7, darkColor, bob);
            pixel(9, 7, darkColor, bob);
            pixel(10, 7, darkColor, bob);
            pixel(9, 8, darkColor, bob);
            pixel(9, 9, darkColor, bob);

            pixel(17, 7, darkColor, bob);
            pixel(18, 7, darkColor, bob);
            pixel(19, 7, darkColor, bob);
            pixel(18, 8, darkColor, bob);
            pixel(18, 9, darkColor, bob);
        }

        private void drawClosedEyes(double bob) {

            for (int x = 8; x <= 11; x++) {
                pixel(x, 10, darkColor, bob);
            }

            for (int x = 17; x <= 20; x++) {
                pixel(x, 10, darkColor, bob);
            }
        }

        private void drawHappyEyes(double bob) {

            pixel(8, 9, darkColor, bob);
            pixel(9, 8, darkColor, bob);
            pixel(10, 8, darkColor, bob);
            pixel(11, 9, darkColor, bob);

            pixel(17, 9, darkColor, bob);
            pixel(18, 8, darkColor, bob);
            pixel(19, 8, darkColor, bob);
            pixel(20, 9, darkColor, bob);
        }

        private void drawMouth(double bob) {

            if (happy) {

                pixel(13, 13, darkColor, bob);
                pixel(14, 13, darkColor, bob);
                pixel(12, 12, darkColor, bob);
                pixel(15, 12, darkColor, bob);

            } else {

                pixel(13, 13, darkColor, bob);
                pixel(14, 13, darkColor, bob);
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

            dragging = true;
            landing = false;
            jumping = false;

            bounceOffset = 0;
            bounceVelocity = 0;

            squashX = 0.70;
            squashY = 1.42;

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