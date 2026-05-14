import wordscramble.model.GameMessage;
import wordscramble.model.GameMessage.Type;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    public String playerName = "Unknown";
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                GameMessage msg = (GameMessage) in.readObject();
                handleMessage(msg);
            }
        } catch (IOException e) {
            System.out.println("[Server] Player disconnected: " + playerName);
        } catch (ClassNotFoundException e) {
            System.err.println("[Server] Unknown class: " + e.getMessage());
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(GameMessage msg) {
        switch (msg.type) {
            case JOIN -> {
                playerName = msg.playerName;
                server.registerPlayer(playerName, this);
            }
            case SUBMIT_WORD -> server.handleSubmission(this, msg.data);
            case CHAT -> server.broadcastChat(msg.playerName, msg.data);
            case POWERUP_REQUEST -> server.handlePowerup(this, msg.data);
            default -> {}
        }
    }

    public synchronized void send(GameMessage msg) {
        try {
            if (out != null) { out.writeObject(msg); out.flush(); }
        } catch (IOException e) {
            System.err.println("[Server] Send failed to " + playerName + ": " + e.getMessage());
        }
    }
}