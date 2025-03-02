package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
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
    private long lastPingTime;
    private ScheduledExecutorService pingCheckScheduler;
    private boolean isRunning = true;

    private static final Map<String, ArrayList<ClientHandler>> lobbies = new HashMap<>();
    private String currentLobby = "General";

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.lastPingTime = System.currentTimeMillis();
            startPingCheckScheduler();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
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
                if (!lobbies.get(lobbyName).isEmpty()) {
                    availableLobbies.put(lobbyName);
                }
            }
            if (!availableLobbies.isEmpty()) {
                response.put("content", "Available lobbies: " + availableLobbies);
            } else {
                response.put("content", "No lobbies with players available");
            }
        }
        System.out.println("Sending lobby list: " + response); // Debug print

        try {
            out.write(response.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void handlePlayerList() throws IOException {
        JSONArray lobbyPlayers = new JSONArray();
        JSONArray serverPlayers = new JSONArray();

        for (ClientHandler clientHandler : clientHandlers) {
            serverPlayers.put(clientHandler.getUsername());
            if (currentLobby != null && clientHandler.currentLobby != null && clientHandler.currentLobby.equals(currentLobby)) {
                lobbyPlayers.put(clientHandler.getUsername());
            }
        }

        createJsonMessage(new String[] {"system", "Players in Lobby `" + (currentLobby != null ? currentLobby : "N/A") + "`: " +
                (!lobbyPlayers.isEmpty() ? lobbyPlayers.toString() : "[]") +
                "\nAll players in server: " + (!serverPlayers.isEmpty() ? serverPlayers.toString() : "[]" )});
    }

    public void handleUsername(String tempUsername) throws IOException {
        boolean isNewUser = this.username == null;

        if (!isUsernameTaken(tempUsername)) {
            this.username = tempUsername;
            createJsonMessage(new String[] {"final username", tempUsername});

            if (isNewUser) {
                broadcastSystemMessage(tempUsername + " has joined the server.");
                clientHandlers.add(this);
            } else {
                createJsonMessage(new String[] {"system", "Your username has been changed to: " + tempUsername});
            }
        } else {
            String suggestedUsername = bob001(tempUsername);
            createJsonMessage(new String[] {"suggested username", suggestedUsername});
        }
    }

    public String bob001(String tempUsername) {
        int count = 1;
        String newUsername = tempUsername;
        while (isUsernameTaken(newUsername)) {
            newUsername = tempUsername + "_" + String.format("%03d", count++);
        }
        return newUsername;
    }

    private boolean isUsernameTaken(String username) {
        return clientHandlers.stream().anyMatch(handler -> handler.getUsername().equalsIgnoreCase(username));
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        while (socket.isConnected() && !socket.isClosed() && isRunning) {
            try {
                String messageFromClient = in.readLine();
                System.out.println(messageFromClient);
                if (messageFromClient == null) {
                    break;
                }
                processMessage(messageFromClient);
            } catch (IOException e) {
                System.err.println("Error reading message: " + e.getMessage());
                break;
            }
        }
        closeEverything(socket, in, out);
    }

    private void createJsonMessage(String[] input) throws IOException {
        JSONObject jsonMessage = new JSONObject();
        String type = input[0];
        jsonMessage.put("type", type);

        switch(type) {
            case "pong":
                break;
            case "system",
                 "message",
                 "suggested username",
                 "final username":
                jsonMessage.put("content", input[1]);
                break;
            case "lobby":
                jsonMessage.put("lobbyName", input[1]);
                break;
        }

        out.write(jsonMessage.toString());
        out.newLine();
        out.flush();
    }

    private void processMessage(String messageIn) {
        try {
            JSONObject jsonMessage = new JSONObject(messageIn);
            String type = jsonMessage.getString("type");
            switch (type) {
                case "group":
                    break;
                case "message":
                    broadcastMessage(jsonMessage);
                    break;
                case "private":
                    sendPrivateMessage(jsonMessage);
                    break;
                case "ping":
                    handlePing();
                    break;
                case "username":
                    String content = jsonMessage.optString("content");
                    handleUsername(content);
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
                case "quit":
                    handleQuit();
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
                    break;
            }
        } catch (JSONException | IOException e) {
            System.err.println("Invalid JSON format received: " + messageIn);
        }
    }

    private void handleQuit() {
        isRunning = false;
    }

    private void handlePing() throws IOException {
        lastPingTime = System.currentTimeMillis();
        createJsonMessage(new String[] {"pong"});
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
        if (receiver.isEmpty()) {
            System.err.println("Receiver username is missing or empty.");
            return;
        }
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.getUsername() != null && clientHandler.getUsername().equals(receiver)) {
                try {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                    return;
                } catch (IOException e) {
                    closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
                }
            }
        }

        /*
        if (!message.optString("type").equals("pong")) {
            sendUserNotFoundMessage(sender, receiver);
        }
        */
    }

    private void broadcastSystemMessage(String message) {
        JSONObject jsonMessage = new JSONObject();
        jsonMessage.put("type", "system");
        jsonMessage.put("content", message);
        broadcastMessage(jsonMessage);
        System.out.println(jsonMessage);
    }

    private void startPingCheckScheduler() {
        pingCheckScheduler = Executors.newScheduledThreadPool(1);
        pingCheckScheduler.scheduleAtFixedRate(this::checkPingLatency, 0, 2, TimeUnit.SECONDS);
    }

    private void checkPingLatency() {
        long currentTime = System.currentTimeMillis();
        long pingLatency = currentTime - lastPingTime;

        if (lastPingTime > 0 && pingLatency > 5000) {
            System.out.println("No ping received in 5 seconds. Closing connection for " + socket);
            isRunning = false;
        }
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