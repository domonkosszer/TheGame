package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
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

    private static final Map<String, ArrayList<ClientHandler>> lobbies = new HashMap<>();
    private String currentLobby = "General";

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

    public void joinLobby(String lobbyName) {
        lobbies.computeIfAbsent(lobbyName, k -> new ArrayList<>()).add(this);
        if (lobbies.containsKey(currentLobby)) {
            lobbies.get(currentLobby).remove(this);
        }
        currentLobby = lobbyName;

        JSONObject response = new JSONObject();
        response.put("type", "system");
        response.put("content", "You joined lobby: " + currentLobby);
        try {
            out.write(response.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void changeLobbyName(String newLobbyName) {
        if (!lobbies.containsKey(currentLobby)) return;

        ArrayList<ClientHandler> members = lobbies.remove(currentLobby);
        lobbies.put(newLobbyName, members);

        for (ClientHandler member : members) {
            member.currentLobby = newLobbyName;
            try {
                JSONObject response = new JSONObject();
                response.put("type", "system");
                response.put("content", "Lobby renamed to: " + newLobbyName);
                member.out.write(response.toString());
                member.out.newLine();
                member.out.flush();
            } catch (IOException e) {
                closeEverything(member.socket, member.in, member.out);
            }
        }
    }

    public void handleLobbyList() {
        JSONObject response = new JSONObject();
        response.put("type", "system");

        if (lobbies.isEmpty()) {
            response.put("content", "No lobbies available");
        } else {
            JSONArray availableLobbies = new JSONArray();
            for (String lobbyName : lobbies.keySet()) {
                if (!lobbies.get(lobbyName).isEmpty()) {  // Only add non-empty lobbies
                    availableLobbies.put(lobbyName);
                }
            }
            if (availableLobbies.length() > 0) {
                response.put("content", "Available lobbies: " + availableLobbies.toString());
            } else {
                response.put("content", "No lobbies with players available");
            }
        }
        System.out.println("Sending lobby list: " + response.toString()); // Debug print

        try {
            out.write(response.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void handlePlayerList() {
        JSONArray lobbyPlayers = new JSONArray();
        JSONArray serverPlayers = new JSONArray();

        for (ClientHandler clientHandler : clientHandlers) {
            serverPlayers.put(clientHandler.getUsername());
            if (currentLobby != null && clientHandler.currentLobby != null && clientHandler.currentLobby.equals(currentLobby)) {
                lobbyPlayers.put(clientHandler.getUsername());
            }
        }

        JSONObject response = new JSONObject();
        response.put("type", "system");
        response.put("content", "Players in Lobby `" + (currentLobby != null ? currentLobby : "N/A") + "`: " +
                (lobbyPlayers.length() > 0 ? lobbyPlayers.toString() : "[]") + // Use [] for empty list
                "\nAll players in server: " +
                (serverPlayers.length() > 0 ? serverPlayers.toString() : "[]")); // Use [] for empty list

        System.out.println("Sending player list: " + response.toString()); // Debug print

        try {
            out.write(response.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
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
        String tempUsername = in.readLine();

        if (tempUsername == null || tempUsername.trim().isEmpty()) {
            tempUsername = "Guest";
        }

        int count = 1;
        String newUsername = tempUsername;
        while (isUsernameTaken(newUsername)) {
            newUsername = tempUsername + "_" + String.format("%02d", count++);
        }

        out.write("SUGGESTED_USERNAME " + newUsername);
        out.newLine();
        out.flush();

        String clientResponse = in.readLine();
        if (clientResponse != null && !clientResponse.trim().isEmpty() && !isUsernameTaken(clientResponse)) {
            newUsername = clientResponse;
        } else if (isUsernameTaken(clientResponse)) {
            out.write("USERNAME_REJECTED Username is already taken. Please try another username...");
            out.newLine();
            out.flush();
            return b0b01();
        } else {
            newUsername = tempUsername;
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
                case "joinLobby":
                    joinLobby(jsonMessage.optString("lobbyName"));
                    break;
                case "changeLobbyName":
                    changeLobbyName(jsonMessage.optString("newLobbyName"));
                    break;
                case "lobbyList":
                    handleLobbyList();
                    break;
                case "playerList":
                    handlePlayerList();
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
        for (ClientHandler client : lobbies.getOrDefault(currentLobby, new ArrayList<>())) {
            try {
                if (!client.username.equals(username)) {
                    client.out.write(message.toString());
                    client.out.newLine();
                    client.out.flush();
                }
            } catch (IOException e) {
                closeEverything(client.socket, client.in, client.out);
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

