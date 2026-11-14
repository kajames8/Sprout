package com.jellyai;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Starts Jelly AI: the floating plant plus the chat bubble that opens
 * when you right-click the plant and choose "Open notes...".
 */
public class JellyAIApp extends Application {

    private FloatingPetWindow pet;

    @Override
    public void start(Stage primaryStage) {

        MemoryStore memoryStore = new MemoryStore();
        DashboardWindow dashboard = new DashboardWindow(memoryStore);

        pet = new FloatingPetWindow(dashboard::show);

        // Lets the chat bubble sit above the plant and follow it around.
        dashboard.setAnchor(pet.getStage());

        pet.show();
    }

    @Override
    public void stop() {

        if (pet != null) {
            pet.close();
        }

        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}