# Jelly AI — Version 0.1

A first JavaFX desktop companion prototype for Windows.

## Included in this version
- A small pixel-art jelly that floats above other windows.
- Click the jelly to open the Jelly AI dashboard.
- Drag the jelly around the desktop.
- A squash-and-stretch landing animation after dragging.
- Local notes stored in a SQLite database.

## Not included yet
- Cloud AI responses.
- Screen capture or OCR.
- Automatic context extraction.
- Proactive reminders.

These are planned next steps. Screen capture should be opt-in and visibly enabled.

## Requirements
- JDK 26
- IntelliJ IDEA
- Maven support in IntelliJ
- Internet connection the first time Maven downloads dependencies

## Open in IntelliJ
1. Extract the `JellyAI` folder.
2. In IntelliJ, choose **File → Open** and select the folder containing `pom.xml`.
3. Allow IntelliJ to load the Maven project.
4. Make sure Project SDK is set to JDK 26.
5. Open the Maven tool window, then run **Plugins → javafx → javafx:run**.

You can also open the IntelliJ terminal in the project folder and run:

```bash
mvn clean javafx:run
```

## Local memory location
The SQLite database is stored at:

`%LOCALAPPDATA%\\JellyAI\\jelly-memory.db`

On Windows, paste `%LOCALAPPDATA%\\JellyAI` into File Explorer's address bar.

## Project structure

```text
JellyAI/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── jellyai/
                    ├── JellyAIApp.java
                    ├── FloatingPetWindow.java
                    ├── DashboardWindow.java
                    └── MemoryStore.java
```

## Next milestone: cloud AI
The next version will add a `CloudAIService` class. Keep any AI API key outside source code, preferably in a Windows environment variable. Never commit API keys to Git or place them in a public project.
