package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    private ScheduledExecutorService pingScheduler;

    public Client(Socket socket) {
        try {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.username = selectUsername();
            startPingScheduler();
        } catch (
                IOException e) {
            closeEverything(socket, in, out);
        }
    }

    private String selectUsername() throws IOException {
        String systemUsername = getSystemUsername();

        out.write(systemUsername);
        out.newLine();
        out.flush();

        while (true) {
            String serverMessage = in.readLine();
            if (serverMessage.startsWith("SUGGESTED_USERNAME")) {
                String[] parts = serverMessage.split(" ", 2);
                String suggestedUsername = parts.length > 1 ? parts[1] : "";

                System.out.println("Suggested username: " + suggestedUsername);
                System.out.print("Press ENTER to accept, or type another: ");

                Scanner scanner = new Scanner(System.in);
                String userInput = scanner.nextLine().trim();

                String finalUsername = userInput.isEmpty() ? suggestedUsername : userInput;

                out.write(finalUsername);
                out.newLine();
                out.flush();

                String response = in.readLine();
                if (response.startsWith("USERNAME_ACCEPTED")) {
                    System.out.println("Your username has been set to: " + finalUsername);
                    return finalUsername;
                } else if (response.startsWith("USERNAME_REJECTED")) {  // Handle username rejection
                    System.out.println(serverMessage.substring("USERNAME_REJECTED ".length())); // Print the rejection message
                }
            }
        }
    }


    private String getSystemUsername() {
        return System.getProperty("user.name");
    }

    public void startPingScheduler() {
        pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(this::sendPing, 5, 5, TimeUnit.SECONDS);
    }

    private void sendPing() {
        try {
            JSONObject pingMessage = new JSONObject();
            pingMessage.put("type", "ping");
            pingMessage.put("sender", username);
            sendJsonMessage(pingMessage);
        } catch (JSONException | IOException e) {
            System.err.println("Error sending ping: " + e.getMessage());
            closeEverything(socket, in, out);
        }
    }

    public void sendPong(String receiver) throws IOException {
        if (receiver.isEmpty()) {
            System.err.println("Pong receiver is missing!");
            return;
        }
        JSONObject pongMessage = new JSONObject();
        pongMessage.put("type", "pong");
        pongMessage.put("sender", username);
        pongMessage.put("receiver", receiver);
        sendJsonMessage(pongMessage);
    }

    private void sendJsonMessage(JSONObject jsonMessage) throws IOException {
        out.write(jsonMessage.toString());
        out.newLine();
        out.flush();
    }

    public void sendMessage() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (socket.isConnected()) {
                System.out.print("> ");
                String message = scanner.nextLine();
                if (message.isEmpty()) continue;
                try {
                    JSONObject jsonMessage = createJsonMessage(message);
                    if (jsonMessage != null) {
                        sendJsonMessage(jsonMessage);
                    }
                } catch (IOException e) {
                    closeEverything(socket, in, out);
                    break;
                } catch (JSONException e) {
                    System.err.println("Error creating JSON message: " + e.getMessage());
                }
            }
        }
    }

    private JSONObject createJsonMessage(String message) {
        JSONObject jsonMessage = new JSONObject();
        if (message.startsWith("/pm")) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                System.out.println("Invalid private message, use /pm <username> <message>");
                return null;
            }
            jsonMessage.put("type", "private");
            jsonMessage.put("sender", username);
            jsonMessage.put("receiver", parts[1]);
            jsonMessage.put("content", parts[2]);
        } else if (message.startsWith("/changeUsername")) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                System.out.println("Usage: /changeUsername <newUsername>");
                return null;
            }
            jsonMessage.put("type", "changeUsername");
            jsonMessage.put("sender", username);
            jsonMessage.put("newUsername", parts[1]);
        } else if (message.equals("/quit")) {
            jsonMessage.put("type", "system");
            jsonMessage.put("content", username + " has left the chat.");
        } else if (message.startsWith("/joinLobby")) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                System.out.println("Usage: /joinLobby <lobbyName>");
                return null;
            }
            jsonMessage.put("type", "joinLobby");
            jsonMessage.put("lobbyName", parts[1]);
        } else if (message.startsWith("/changeLobbyName")) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                System.out.println("Usage: /changeLobbyName <newLobbyName>");
                return null;
            }
            jsonMessage.put("type", "changeLobbyName");
            jsonMessage.put("newLobbyName", parts[1]);
        } else if (message.startsWith("/lobbylist")) {
            jsonMessage.put("type", "lobbyList");
        } else if (message.startsWith("/playerlist")) {
            jsonMessage.put("type", "playerList");
        } else {
            jsonMessage.put("type", "group");
            jsonMessage.put("sender", username);
            jsonMessage.put("content", message);
        }
        return jsonMessage;
    }

    public void listenForMessage() {
        new Thread(() -> {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    JSONObject jsonMessage = new JSONObject(message);
                    String type = jsonMessage.getString("type");
                    String content = jsonMessage.getString("content");
                    String sender = jsonMessage.optString("sender", "SYSTEM");
                    switch (type) {
                        case "system":
                            System.out.println(content);
                            if (content.startsWith("Your username has been changed to: ")) {
                                username = content.split(": ")[1].trim();
                            }
                            break;
                        case "group":
                            System.out.println(sender + ": " + content);
                            break;
                        case "private":
                            System.out.println("[PRIVATE] " + sender + ": " + content);
                            break;
                        case "ping":
                            String pingSender = jsonMessage.optString("sender");
                            sendPong(pingSender);
                            break;
                        default:
                            System.out.println("Unknown message type: " + type);
                    }
                }
            } catch (IOException | JSONException e) {
                closeEverything(socket, in, out);
            }
        }).start();
    }

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            Socket socket = new Socket("localhost", 2222);
            Client client = new Client(socket);
            client.listenForMessage();
            client.sendMessage();
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }
}
