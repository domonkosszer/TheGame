package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

public class ClientHandler implements Runnable {
    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    private long lastPongTime = 0;
    private static final long MAX_PING_LATENCY = 3000; // 3 seconds
    private ScheduledExecutorService pingCheckScheduler;

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = b0b01();
            clientHandlers.add(this);
            broadcastSystemMessage(username + " has entered the chat");

            startPingCheckScheduler();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    // Maybe good for further utilisation
    public String handleUsernameSelection() throws IOException {
        String tempUsername;
        while (true) {
            tempUsername = in.readLine();
            if (isUsernameTaken(tempUsername)) {
                out.write(tempUsername + " is already taken. Please try another username...");
                out.newLine();
                out.flush();
            } else {
                out.write("USERNAME_ACCEPTED");
                out.newLine();
                out.flush();
                break;
            }
        }
        return tempUsername;
    }

    public void handleUsernameChange(JSONObject jsonMessage) {
        String newUsername = jsonMessage.optString("newUsername", "").trim();
        String oldUsername = this.username;

        if (newUsername.isEmpty() || isUsernameTaken(newUsername)) {
            try {
                JSONObject errorMessage = new JSONObject();
                errorMessage.put("type", "system");
                errorMessage.put("content", "Username '" + newUsername + "' is already taken or invalid.");
                out.write(errorMessage.toString());
                out.newLine();
                out.flush();
            } catch (IOException e) {
                closeEverything(socket, in, out);
            }
            return;
        }

        this.username = newUsername;

        try {
            JSONObject successMessage = new JSONObject();
            successMessage.put("type", "system");
            successMessage.put("content", "Your username has been changed to: " + newUsername);
            out.write(successMessage.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }

        broadcastSystemMessage(oldUsername + " has changed their username to " + newUsername);
    }

    public String b0b01() throws IOException {
        String tempUsername = in.readLine(); // Read the initial username attempt

        if (tempUsername == null || tempUsername.trim().isEmpty()) {
            tempUsername = "Guest";
        }

        int count = 1;
        String newUsername = tempUsername;
        while (isUsernameTaken(newUsername)) {
            newUsername = tempUsername + "_" + String.format("%02d", count++);
        }

        out.write("SUGGESTED_USERNAME " + newUsername); // Send the suggested username
        out.newLine();
        out.flush();

        String clientResponse = in.readLine(); // Read the client's final choice
        if (clientResponse != null && !clientResponse.trim().isEmpty() && !isUsernameTaken(clientResponse)) {
            newUsername = clientResponse;
        } else if (isUsernameTaken(clientResponse)) {
            out.write("USERNAME_REJECTED Username is already taken. Please try another username...");
            out.newLine();
            out.flush();
            return b0b01(); // Restart username selection
        } else {
            newUsername = tempUsername; // Keep the original if client sends nothing new
        }

        this.username = newUsername;

        out.write("USERNAME_ACCEPTED " + this.username);
        out.newLine();
        out.flush();
        return this.username;
    }

    private boolean isUsernameTaken(String username) {
        return clientHandlers.stream().anyMatch(handler -> handler.getUsername().equalsIgnoreCase(username));
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        String messageFromClient;
        while (socket.isConnected()) {
            try {
                messageFromClient = in.readLine();
                if (messageFromClient == null) break;
                processMessage(messageFromClient);
            } catch (IOException e) {
                break;
            }
        }
        closeEverything(socket, in, out);
    }

    private void processMessage(String messageIn) {
        try {
            JSONObject jsonMessage = new JSONObject(messageIn);
            String type = jsonMessage.getString("type");
            switch (type) {
                case "group":
                case "system":
                    broadcastMessage(jsonMessage);
                    break;
                case "private":
                    sendPrivateMessage(jsonMessage);
                    break;
                case "ping":
                    handlePing(jsonMessage);
                    break;
                case "pong":
                    handlePong(jsonMessage);
                    break;
                case "changeUsername":
                    handleUsernameChange(jsonMessage);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            System.err.println("Invalid JSON format received: " + messageIn);
        }
    }

    private void handlePing(JSONObject jsonMessage) {
        String sender = jsonMessage.optString("sender");
        if (sender.isEmpty()) {
            System.err.println("Ping sender is missing!");
            return;
        }
        JSONObject pongMessage = new JSONObject();
        pongMessage.put("type", "pong");
        pongMessage.put("sender", username);
        pongMessage.put("receiver", sender);
        sendPrivateMessage(pongMessage);
        long currentTime = System.currentTimeMillis();
        long pingLatency = currentTime - lastPongTime;

        System.out.println("Ping received, Pong sent to " + sender + ", pinglatency: " + pingLatency + "ms");
    }

    private void handlePong(JSONObject jsonMessage) {
        lastPongTime = System.currentTimeMillis();
    }

    public void broadcastMessage(JSONObject message) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                if (!clientHandler.username.equals(username)) {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                }
            } catch (IOException e) {
                closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
            }
        }
    }

    private void sendPrivateMessage(JSONObject message) {
        String receiver = message.optString("receiver");
        String sender = message.optString("sender");
        if (receiver.isEmpty()) {
            System.err.println("Receiver username is missing or empty.");
            return;
        }
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.username.equals(receiver) && !clientHandler.username.equals(sender)) {
                try {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                    return;
                } catch (IOException e) {
                    closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
                    break;
                }
            }
        }
        if (!message.optString("type").equals("pong")) {
            sendUserNotFoundMessage(sender, receiver);
        }
    }

    private void sendUserNotFoundMessage(String sender, String receiver) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.username.equals(sender)) {
                try {
                    JSONObject notFoundMessage = new JSONObject();
                    notFoundMessage.put("type", "system");
                    notFoundMessage.put("content", receiver + " is not online.");
                    clientHandler.out.write(notFoundMessage.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                    break;
                } catch (IOException e) {
                    closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
                    break;
                }
            }
        }
    }

    private void broadcastSystemMessage(String message) {
        JSONObject jsonMessage = new JSONObject();
        jsonMessage.put("type", "system");
        jsonMessage.put("content", message);
        broadcastMessage(jsonMessage);
    }

    private void startPingCheckScheduler() {
        pingCheckScheduler = Executors.newScheduledThreadPool(1);
        pingCheckScheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            long pingLatency = currentTime - lastPongTime;
            if (lastPongTime > 0 && pingLatency > MAX_PING_LATENCY) {
                System.out.println("Kicking " + username + " due to high latency (" + pingLatency + " ms)");
                broadcastSystemMessage(username + " was removed due to high ping.");
                closeEverything(socket, in, out);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        try {
            clientHandlers.remove(this);
            if (pingCheckScheduler != null) {
                pingCheckScheduler.shutdown();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            System.out.println(username + " has been disconnected.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

