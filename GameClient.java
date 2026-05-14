import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import wordscramble.model.GameMessage;
import wordscramble.model.GameMessage.Type;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class GameClient extends Application {

    // -- Networking --
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;

    // -- UI --
    private Stage primaryStage;
    private BorderPane root;
    private HBox tileArea;
    private HBox answerArea;
    private Label scrambleLabel;
    private Label timerLabel;
    private Label statusLabel;
    private VBox chatBox;
    private ScrollPane chatScroll;
    private TextArea chatInput;
    private VBox scoreBoard;
    private Pane popupLayer;
    private Button submitBtn;
    private Button hintBtn;
    private Button extraTimeBtn;

    private final List<LetterTile> tilesInAnswer = new ArrayList<>();
    private int currentTimeLeft = 0;
    private Timeline roundTimeline;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        showLoginScreen();
    }

    // ====== LOGIN SCREEN ======
    private void showLoginScreen() {
        VBox login = new VBox(15);
        login.setAlignment(Pos.CENTER);
        login.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 60;");

        Label title = new Label("⚡ WORD SCRAMBLE");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 32));
        title.setTextFill(Color.web("#e94560"));

        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name");
        nameField.setMaxWidth(260);
        nameField.setStyle("-fx-background-color:#16213e; -fx-text-fill:white; -fx-border-color:#e94560; -fx-padding:8;");

        Button joinBtn = new Button("JOIN GAME");
        joinBtn.setStyle("-fx-background-color:#e94560; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:10 30;");
        joinBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            playerName = name;
            connectToServer();
        });

        login.getChildren().addAll(title, new Label(" "), nameField, joinBtn);
        primaryStage.setScene(new Scene(login, 480, 360));
        primaryStage.setTitle("Word Scramble - Login");
        primaryStage.show();
    }

    // ====== MAIN GAME SCREEN ======
    private void showGameScreen() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        popupLayer = new Pane();
        popupLayer.setMouseTransparent(true);
        popupLayer.setStyle("-fx-background-color: transparent;");

        // -- TOP: status bar --
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color:#16213e; -fx-padding:12 20;");
        scrambleLabel = new Label("Waiting for round...");
        scrambleLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        scrambleLabel.setTextFill(Color.web("#e94560"));
        timerLabel = new Label("⏱️ --");
        timerLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        timerLabel.setTextFill(Color.web("#ffd460"));
        statusLabel = new Label("Status: Connected");
        statusLabel.setTextFill(Color.LIGHTGRAY);
        topBar.getChildren().addAll(scrambleLabel, new Separator(Orientation.VERTICAL), timerLabel, new Separator(Orientation.VERTICAL), statusLabel);

        // -- CENTER: tile areas --
        VBox center = new VBox(20);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(30));

        Label tileLabel = new Label("Available Letters");
        tileLabel.setTextFill(Color.LIGHTGRAY);
        tileArea = new HBox(8);
        tileArea.setAlignment(Pos.CENTER);
        tileArea.setMinHeight(70);
        tileArea.setStyle("-fx-background-color:#16213e; -fx-padding:15; -fx-border-color:#0f3460; -fx-border-radius:8; -fx-background-radius:8;");

        Label answerLabel = new Label("Your Word");
        answerLabel.setTextFill(Color.LIGHTGRAY);
        answerArea = new HBox(8);
        answerArea.setAlignment(Pos.CENTER);
        answerArea.setMinHeight(70);
        answerArea.setStyle("-fx-background-color:#0f3460; -fx-padding:15; -fx-border-color:#e94560; -fx-border-radius:8; -fx-background-radius:8;");

        // Buttons
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);
        submitBtn = new Button("✓ SUBMIT");
        submitBtn.setStyle("-fx-background-color:#e94560; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:8 20;");
        submitBtn.setOnAction(e -> submitWord());

        Button clearBtn = new Button("✕ CLEAR");
        clearBtn.setStyle("-fx-background-color:#444; -fx-text-fill:white; -fx-padding:8 20;");
        clearBtn.setOnAction(e -> clearAnswer());

        hintBtn = new Button("💡 HINT");
        hintBtn.setStyle("-fx-background-color:#ffd460; -fx-text-fill:#1a1a2e; -fx-font-weight:bold; -fx-padding:8 20;");
        hintBtn.setOnAction(e -> requestPowerup("HINT"));

        extraTimeBtn = new Button("⏱️ +TIME");
        extraTimeBtn.setStyle("-fx-background-color:#4ecca3; -fx-text-fill:#1a1a2e; -fx-font-weight:bold; -fx-padding:8 20;");
        extraTimeBtn.setOnAction(e -> requestPowerup("EXTRA_TIME"));

        btnRow.getChildren().addAll(submitBtn, clearBtn, hintBtn, extraTimeBtn);

        center.getChildren().addAll(tileLabel, tileArea, answerLabel, answerArea, btnRow);

        // -- RIGHT: chat + scoreboard --
        VBox rightPanel = new VBox(10);
        rightPanel.setPrefWidth(220);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setStyle("-fx-background-color:#16213e;");

        Label scoreTitle = new Label("🏆 SCOREBOARD");
        scoreTitle.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        scoreTitle.setTextFill(Color.web("#ffd460"));
        scoreBoard = new VBox(4);

        Label chatTitle = new Label("💬 CHAT");
        chatTitle.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        chatTitle.setTextFill(Color.web("#4ecca3"));
        chatBox = new VBox(4);
        chatBox.setPrefHeight(200);
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setPrefHeight(180);
        chatScroll.setStyle("-fx-background-color: transparent; -fx-background: #1a1a2e;");

        chatInput = new TextArea();
        chatInput.setPromptText("Type message...");
        chatInput.setPrefRowCount(2);
        chatInput.setWrapText(true);
        chatInput.setStyle("-fx-background-color:#0f3460; -fx-text-fill:white; -fx-border-color:#4ecca3;");
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER") && !e.isShiftDown()) {
                sendChat();
                e.consume();
            }
        });

        rightPanel.getChildren().addAll(scoreTitle, scoreBoard, new Separator(), chatTitle, chatScroll, chatInput);

        root.setTop(topBar);
        root.setCenter(center);
        root.setRight(rightPanel);

        StackPane stackRoot = new StackPane(root, popupLayer);
        Scene scene = new Scene(stackRoot, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Word Scramble - " + playerName);
    }

    // ====== LETTER TILES ======
    private void buildTiles(String scrambled) {
        Platform.runLater(() -> {
            tileArea.getChildren().clear();
            answerArea.getChildren().clear();
            tilesInAnswer.clear();

            for (char c : scrambled.toUpperCase().toCharArray()) {
                LetterTile tile = new LetterTile(String.valueOf(c));
                tile.setOnMouseClicked(e -> moveTileToAnswer(tile));
                tileArea.getChildren().add(tile);
            }
        });
    }

    private void moveTileToAnswer(LetterTile tile) {
        if (tileArea.getChildren().contains(tile)) {
            tileArea.getChildren().remove(tile);
            answerArea.getChildren().add(tile);
            tilesInAnswer.add(tile);
            tile.setOnMouseClicked(e -> moveTileBack(tile));
            tile.pulse();
        }
    }

    private void moveTileBack(LetterTile tile) {
        answerArea.getChildren().remove(tile);
        tilesInAnswer.remove(tile);
        tileArea.getChildren().add(tile);
        tile.setOnMouseClicked(e -> moveTileToAnswer(tile));
    }

    private void clearAnswer() {
        new ArrayList<>(tilesInAnswer).forEach(this::moveTileBack);
    }

    private void submitWord() {
        if (tilesInAnswer.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (LetterTile t : tilesInAnswer) sb.append(t.getLetter());
        String word = sb.toString().toLowerCase();
        sendMessage(new GameMessage(Type.SUBMIT_WORD, playerName, word));
        clearAnswer();
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        sendMessage(new GameMessage(Type.CHAT, playerName, text));
        chatInput.clear();
    }

    private void requestPowerup(String type) {
        sendMessage(new GameMessage(Type.POWERUP_REQUEST, playerName, type));
    }

    // ====== SCORE POPUP ======
    private void showScorePopup(int points, boolean good) {
        Platform.runLater(() -> {
            Label popup = new Label((good ? "+" : "") + points + (good ? " pts! 🎉" : " ✗"));
            popup.setFont(Font.font("Monospace", FontWeight.BOLD, 24));
            popup.setTextFill(good ? Color.web("#4ecca3") : Color.web("#e94560"));
            popup.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 10 20; -fx-background-radius: 12;");
            popup.setLayoutX(200);
            popup.setLayoutY(250);
            popupLayer.getChildren().add(popup);

            TranslateTransition move = new TranslateTransition(Duration.millis(800), popup);
            move.setToY(-80);
            FadeTransition fade = new FadeTransition(Duration.millis(800), popup);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            ParallelTransition anim = new ParallelTransition(move, fade);
            anim.setOnFinished(e -> popupLayer.getChildren().remove(popup));
            anim.play();
        });
    }

    // ====== NETWORKING ======
    private void connectToServer() {
        new Thread(() -> {
            try {
                Socket socket = new Socket("localhost", 12345);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                sendMessage(new GameMessage(Type.JOIN, playerName, ""));
                Platform.runLater(this::showGameScreen);
                listenToServer();
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Cannot connect to server: " + e.getMessage()));
            }
        }).start();
    }

    private void listenToServer() {
        try {
            while (true) {
                GameMessage msg = (GameMessage) in.readObject();
                handleMessage(msg);
            }
        } catch (IOException e) {
            Platform.runLater(() -> addChatLine("Server", "Disconnected."));
        } catch (ClassNotFoundException e) {
            System.err.println("[Client] Unknown class: " + e.getMessage());
        }
    }

    private void handleMessage(GameMessage msg) {
        Platform.runLater(() -> {
            switch (msg.type) {
                case ROUND_START -> {
                    String[] parts = msg.data.split("\\|");
                    String scrambled = parts[0];
                    String theme = parts.length > 1 ? parts[1] : "";
                    scrambleLabel.setText("Scramble: " + scrambled + "  [" + theme + "]");
                    buildTiles(scrambled);
                    startTimerDisplay(parts.length > 2 ? Integer.parseInt(parts[2]) : 60);
                    statusLabel.setText("Round in progress!");
                }
                case ROUND_END -> {
                    String[] parts = msg.data.split("\\|");
                    String reason = parts[0];
                    String answer = parts.length > 1 ? parts[1].replace("answer=", "") : "?";
                    statusLabel.setText("Round over! Answer was: " + answer);
                    addChatLine("Server", "Round over! The word was: " + answer);
                    tileArea.getChildren().clear();
                    answerArea.getChildren().clear();
                    tilesInAnswer.clear();
                }
                case WORD_RESULT -> {
                    String[] parts = msg.data.split("\\|");
                    boolean valid = "VALID".equals(parts[0]);
                    String reason = parts.length > 1 ? parts[1] : "";
                    showScorePopup(valid ? msg.score : 0, valid);
                    if (valid) {
                        statusLabel.setText("✓ " + reason + "  +" + msg.score + " pts!  ⏱️ +5s");
                        addTime(5);
                    } else {
                        statusLabel.setText("✗ " + reason);
                    }
                }
                case SCORE_UPDATE -> updateScoreboard(msg.data);
                case CHAT -> addChatLine(msg.playerName, msg.data);
                case POWERUP_GRANT -> {
                    String[] parts = msg.data.split("\\|");
                    addChatLine("PowerUp", parts.length > 1 ? parts[1] : parts[0]);
                    if ("HINT".equals(parts[0]) && parts.length > 1) {
                        statusLabel.setText("Hint: " + parts[1]);
                    }
                }
                case GAME_OVER -> {
                    statusLabel.setText("GAME OVER! " + msg.data);
                    addChatLine("Server", "GAME OVER: " + msg.data);
                    showGameOverScreen(msg.data);
                }
                default -> {}
            }
        });
    }

    private void startTimerDisplay(int seconds) {
        currentTimeLeft = seconds;
        if (roundTimeline != null) roundTimeline.stop();
        
        roundTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            currentTimeLeft--;
            timerLabel.setText("⏱️ " + Math.max(0, currentTimeLeft));
            timerLabel.setTextFill(currentTimeLeft <= 10 ? Color.RED : Color.web("#ffd460"));
        }));
        roundTimeline.setCycleCount(Timeline.INDEFINITE);
        roundTimeline.play();
    }

    private void addTime(int seconds) {
        currentTimeLeft += seconds;
        timerLabel.setText("⏱️ " + currentTimeLeft);
    }

    private void updateScoreboard(String data) {
        scoreBoard.getChildren().clear();
        for (String entry : data.split(",")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                Label lbl = new Label(parts[0] + " — " + parts[1] + " pts");
                lbl.setTextFill(Color.LIGHTGRAY);
                lbl.setFont(Font.font("Monospace", 12));
                scoreBoard.getChildren().add(lbl);
            }
        }
    }

    private void addChatLine(String from, String text) {
        Label lbl = new Label("[" + from + "] " + text);
        lbl.setWrapText(true);
        lbl.setTextFill("Server".equals(from) || "PowerUp".equals(from) ? Color.web("#4ecca3") : Color.LIGHTGRAY);
        lbl.setFont(Font.font("Monospace", 11));
        chatBox.getChildren().add(lbl);
        chatScroll.setVvalue(1.0);
    }

    private synchronized void sendMessage(GameMessage msg) {
        try {
            if (out != null) { out.writeObject(msg); out.flush(); }
        } catch (IOException e) {
            System.err.println("[Client] Send failed: " + e.getMessage());
        }
    }

    // ====== GAME OVER SCREEN ======
    private void showGameOverScreen(String data) {
        VBox screen = new VBox(20);
        screen.setAlignment(Pos.CENTER);
        screen.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 60;");

        Label title = new Label("🏆 GAME OVER");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 36));
        title.setTextFill(Color.web("#ffd460"));

        // Parse and display final scores
        VBox scoreList = new VBox(8);
        scoreList.setAlignment(Pos.CENTER);
        String scoresPart = data.contains("Final scores:") ? data.split("Final scores:")[1].trim() : data;
        for (String entry : scoresPart.split(",")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                Label lbl = new Label(parts[0] + "  —  " + parts[1] + " pts");
                lbl.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
                lbl.setTextFill(Color.LIGHTGRAY);
                scoreList.getChildren().add(lbl);
            }
        }

        Button playAgainBtn = new Button("▶️ PLAY AGAIN");
        playAgainBtn.setStyle("-fx-background-color:#e94560; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:12 30; -fx-font-size:14;");
        playAgainBtn.setOnAction(e -> {
            // Close connection and go back to login
            try { if (out != null) out.close(); } catch (IOException ignored) {}
            out = null;
            in = null;
            tilesInAnswer.clear();
            showLoginScreen();
        });

        Button quitBtn = new Button("✕ QUIT");
        quitBtn.setStyle("-fx-background-color:#444; -fx-text-fill:white; -fx-padding:12 30; -fx-font-size:14;");
        quitBtn.setOnAction(e -> primaryStage.close());

        HBox btnRow = new HBox(20, playAgainBtn, quitBtn);
        btnRow.setAlignment(Pos.CENTER);

        screen.getChildren().addAll(title, new Label(" "), scoreList, new Label(" "), btnRow);
        primaryStage.setScene(new Scene(screen, 480, 400));
        primaryStage.setTitle("Word Scramble - Game Over");
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }
}