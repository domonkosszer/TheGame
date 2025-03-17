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
import network.Protocol;

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

    public void handleUsername(String tempUsername) throws IOException {
        boolean isNewUser = this.username == null;

        if (!isUsernameTaken(tempUsername)) {
            this.username = tempUsername;
            createMessage(new String[] {"final username", tempUsername});

            if (isNewUser) {
                broadcastSystemMessage(tempUsername + " has joined the server.");
                clientHandlers.add(this);
            } else {
                createMessage(new String[] {"system", "Your username has been changed to: " + tempUsername});
            }
        } else {
            String suggestedUsername = bob001(tempUsername);
            createMessage(new String[] {"suggested username", suggestedUsername});
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

    private void createMessage(String[] message) throws IOException {
        Protocol protocolMessage = new Protocol();
        String type = message[0];
        protocolMessage.put("type", type);

        switch(type) {
            case "pong":
                break;
            case "system",
                 "message",
                 "suggested username",
                 "final username":
                protocolMessage.put("content", message[1]);
                break;
            case "lobby":
                protocolMessage.put("lobbyName", message[1]);
                break;
        }

        out.write(protocolMessage.toString());
        out.newLine();
        out.flush();
    }

    private void processMessage(String messageIn) {
        try {
            Protocol protocolMessage = new Protocol(messageIn);
            String type = protocolMessage.getString("type");
            switch (type) {
                case "group":
                    break;
                case "chat":
                    sendMessage(protocolMessage);
                    break;
                case "ping":
                    handlePing();
                    break;
                case "username":
                    String content = protocolMessage.getString("content").replaceAll("\\s+","");
                    handleUsername(content);
                    break;
                case "joinLobby":
                    joinLobby(protocolMessage.getString("lobbyName"));
                    break;
                case "changeLobbyName":
                    changeLobbyName(protocolMessage.getString("newLobbyName"));
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
        } catch (IOException e) {
            System.err.println("Invalid format received: " + messageIn);
        }
    }

    public void joinLobby(String lobbyName) {
        lobbies.computeIfAbsent(lobbyName, k -> new ArrayList<>()).add(this);
        if (lobbies.containsKey(currentLobby)) {
            lobbies.get(currentLobby).remove(this);
        }
        currentLobby = lobbyName;

        Protocol response = new Protocol();
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
                Protocol response = new Protocol();
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
        Protocol response = new Protocol();
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

        createMessage(new String[] {"system", "Players in Lobby `" + (currentLobby != null ? currentLobby : "N/A") + "`: " +
                (!lobbyPlayers.isEmpty() ? lobbyPlayers.toString() : "[]") +
                "\nAll players in server: " + (!serverPlayers.isEmpty() ? serverPlayers.toString() : "[]" )});
    }

    private void handleQuit() {
        isRunning = false;
    }

    private void handlePing() throws IOException {
        lastPingTime = System.currentTimeMillis();
        createMessage(new String[] {"pong"});
    }

    public void sendMessage(Protocol message) {
        String receiver = message.getString("receiver");
        if (receiver.isEmpty()) {
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
        } else {
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
        }
    }

    private void broadcastSystemMessage(String input) {
        Protocol protocolMessage = new Protocol();
        protocolMessage.put("type", "system");
        protocolMessage.put("content", input);
        sendMessage(protocolMessage);
        System.out.println(protocolMessage);
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