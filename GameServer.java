import wordscramble.db.DatabaseManager;
import wordscramble.model.GameMessage;
import wordscramble.model.GameMessage.Type;
import wordscramble.model.Player;
import wordscramble.util.WordUtils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    public static final int PORT = 12345;
    private static final int ROUND_SECONDS = 60;
    private static final int MAX_PLAYERS = 4;

    private final DatabaseManager db = DatabaseManager.getInstance();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final ExecutorService validationPool = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService timerService = Executors.newSingleThreadScheduledExecutor();

    private String currentScrambled = "";
    private String currentWord = "";
    private String sessionId = UUID.randomUUID().toString();
    private String currentTheme = "java";
    private List<String> themeWords;
    private ScheduledFuture<?> roundTimer;
    private int timeLeft = ROUND_SECONDS;
    private boolean roundActive = false;
    private final Set<String> usedWords = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    public void start() throws IOException {
        themeWords = db.getWordsByTheme(currentTheme);
        db.startSession(sessionId, currentTheme);
        System.out.println("[Server] Starting on port " + PORT);

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[Server] Waiting for players...");

        // Accept loop
        while (true) {
            Socket socket = serverSocket.accept();
            if (clients.size() >= MAX_PLAYERS) {
                socket.close();
                continue;
            }
            ClientHandler handler = new ClientHandler(socket, this);
            clients.add(handler);
            new Thread(handler).start();
            System.out.println("[Server] Player connected. Total: " + clients.size());

            if (clients.size() >= 2 && !roundActive) {
                startRound();
            }
        }
    }

    synchronized void startRound() {
        if (themeWords.isEmpty()) {
            broadcast(new GameMessage(Type.GAME_OVER, "Server", "No more words! Game over."));
            return;
        }
        roundActive = true;
        usedWords.clear();
        currentWord = themeWords.remove(new Random().nextInt(themeWords.size()));
        currentScrambled = WordUtils.scramble(currentWord);
        timeLeft = ROUND_SECONDS;

        GameMessage roundStart = new GameMessage(Type.ROUND_START, "Server",
                currentScrambled + "|" + currentTheme + "|" + ROUND_SECONDS);
        broadcast(roundStart);
        System.out.println("[Server] Round started. Word: " + currentWord + " -> " + currentScrambled);

        // Round timer thread
        if (roundTimer != null) roundTimer.cancel(false);
        roundTimer = timerService.scheduleAtFixedRate(() -> {
            timeLeft--;
            if (timeLeft <= 0) {
                endRound("TIME");
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    synchronized void endRound(String reason) {
        if (!roundActive) return;
        roundActive = false;
        if (roundTimer != null) roundTimer.cancel(false);

        String scores = buildScoreString();
        broadcast(new GameMessage(Type.ROUND_END, "Server", reason + "|answer=" + currentWord + "|" + scores));

        // Only 1 round — end the game after a short delay
        timerService.schedule(() -> {
            broadcast(new GameMessage(Type.GAME_OVER, "Server", "Game finished! Final scores: " + scores));
        }, 3, TimeUnit.SECONDS);
    }

    void handleSubmission(ClientHandler handler, String word) {
        String playerName = handler.playerName;
        // Offload validation to ExecutorService — doesn't block other players
        validationPool.submit(() -> {
            boolean inDict = WordUtils.isValidEnglishWord(word);
            boolean notUsed = usedWords.add(word.toLowerCase());

            GameMessage result;
            if (!roundActive) {
                result = new GameMessage(Type.WORD_RESULT, playerName, "INVALID|Round not active", 0);
            } else if (!inDict) {
                result = new GameMessage(Type.WORD_RESULT, playerName, "INVALID|Not a valid word", 0);
            } else if (!notUsed) {
                result = new GameMessage(Type.WORD_RESULT, playerName, "INVALID|Already used", 0);
            } else {
                int pts = Player.calculateScore(word);
                Player p = players.get(playerName);
                if (p != null) {
                    p.addScore(pts);
                    db.saveScore(playerName, p.score, sessionId);
                }
                timeLeft += 5; // +5 seconds for a correct word
                result = new GameMessage(Type.WORD_RESULT, playerName, "VALID|" + word, pts);
                broadcast(new GameMessage(Type.SCORE_UPDATE, playerName, buildScoreString(), pts));
            }
            handler.send(result);
        });
    }

    void handlePowerup(ClientHandler handler, String powerup) {
        Player p = players.get(handler.playerName);
        if (p == null) return;
        if ("EXTRA_TIME".equals(powerup) && !p.hasExtraTime) {
            p.hasExtraTime = true;
            timeLeft += 15;
            handler.send(new GameMessage(Type.POWERUP_GRANT, "Server", "EXTRA_TIME|+15 seconds granted!"));
            broadcast(new GameMessage(Type.CHAT, "Server", handler.playerName + " used Extra Time!"));
        } else if ("HINT".equals(powerup) && p.hintsUsed < 2) {
            p.hintsUsed++;
            String hint = WordUtils.getHint(currentWord);
            handler.send(new GameMessage(Type.POWERUP_GRANT, "Server", "HINT|" + hint));
        }
    }

    void registerPlayer(String name, ClientHandler handler) {
        players.put(name, new Player(name));
        broadcast(new GameMessage(Type.CHAT, "Server", name + " joined the game!"));
        // If round is active, send scramble to new player
        if (roundActive) {
            handler.send(new GameMessage(Type.ROUND_START, "Server",
                    currentScrambled + "|" + currentTheme + "|" + timeLeft));
        }
    }

    void removeClient(ClientHandler handler) {
        clients.remove(handler);
        players.remove(handler.playerName);
        broadcast(new GameMessage(Type.CHAT, "Server", handler.playerName + " left."));
        if (clients.size() < 2 && roundActive) {
            endRound("NOT_ENOUGH_PLAYERS");
        }
    }

    void broadcast(GameMessage msg) {
        for (ClientHandler c : clients) c.send(msg);
    }

    void broadcastChat(String from, String text) {
        broadcast(new GameMessage(Type.CHAT, from, text));
    }

    private String buildScoreString() {
        StringBuilder sb = new StringBuilder();
        players.forEach((name, p) -> sb.append(name).append(":").append(p.score).append(","));
        return sb.toString();
    }
}