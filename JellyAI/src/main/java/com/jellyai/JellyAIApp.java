package com.jellyai;

import javafx.application.Application;
import javafx.stage.Stage;

public class JellyAIApp extends Application {
    private MemoryStore memoryStore;
    private DashboardWindow dashboard;

    @Override
    public void start(Stage primaryStage) {
        memoryStore = new MemoryStore();
        memoryStore.initialize();

        dashboard = new DashboardWindow(memoryStore);
        FloatingPetWindow pet = new FloatingPetWindow(() -> dashboard.show());
        pet.show();
        dashboard.show();
    }

    @Override
    public void stop() {
        if (memoryStore != null) {
            memoryStore.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
