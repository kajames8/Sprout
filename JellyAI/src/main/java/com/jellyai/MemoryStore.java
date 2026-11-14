package com.jellyai;

import java.sql.*;
import java.nio.file.*;

public class MemoryStore {
    private Connection connection;

    public void initialize() {
        try {
            Path appFolder = Path.of(
                    System.getProperty("user.home"),
                    "AppData", "Local", "JellyAI"
            );
            Files.createDirectories(appFolder);

            Path database = appFolder.resolve("jelly-memory.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database);

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        content TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                """);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Could not initialize Jelly AI local memory.", exception);
        }
    }

    public void saveMemory(String content) {
        String sql = "INSERT INTO memories(content) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, content);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Could not save memory.", exception);
        }
    }

    public String getAllMemories() {
        StringBuilder result = new StringBuilder();
        String sql = "SELECT content, created_at FROM memories ORDER BY id DESC";

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {

            while (rows.next()) {
                result.append("• ")
                      .append(rows.getString("content"))
                      .append("\n")
                      .append("  ")
                      .append(rows.getString("created_at"))
                      .append("\n\n");
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Could not load memories.", exception);
        }

        return result.isEmpty() ? "No memories saved yet." : result.toString();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }
}
