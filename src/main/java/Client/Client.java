package Client;

import java.io.*;
import java.net.Socket;
import java.sql.SQLOutput;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.transform.Source;

public class Client {

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    private long lastPingSentTime = 0;
    private long pingRoundTripTime = -1;
    private ScheduledExecutorService pingScheduler;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.username = username;

            startPingScheduler();

            out.write(username);
            out.newLine();
            out.flush();

        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void startPingScheduler() {
        pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(this::sendPing, 5, 5,  TimeUnit.SECONDS);
    }

    private void sendPing() {
        try {
            JSONObject pingMessage = new JSONObject();
            pingMessage.put("type", "ping");
            pingMessage.put("sender", username);
            sendJsonMessage(pingMessage);
            lastPingSentTime = System.currentTimeMillis();
        } catch (JSONException | IOException e) {
            System.err.println("Error sending ping: " + e.getMessage());
            closeEverything(socket, in, out);
        }
    }

    public void sendPong(String receiver) throws IOException {
        if (receiver == null || receiver.isEmpty()) {
            System.err.println("Pong receiver is missing!");
            return;
        }

        JSONObject pongMessage = new JSONObject();
        pongMessage.put("type", "pong");
        pongMessage.put("sender", username);
        pongMessage.put("receiver", receiver);

        sendJsonMessage(pongMessage);
        System.out.println("[PONG] Sent to " + receiver);
    }

    private void sendJsonMessage(JSONObject jsonMessage) throws IOException {
        out.write(jsonMessage.toString());
        out.newLine();
        out.flush();
    }

    public void sendMessage() {
        Scanner scanner = new Scanner(System.in);
        while (socket.isConnected()) {
            System.out.print("> ");
            String message = scanner.nextLine();

            if (message == null || message.isEmpty()) {
                continue;
            }

            try {
                JSONObject jsonMessage = createJsonMessage(message);

                if (jsonMessage != null) {
                    out.write(jsonMessage.toString());
                    out.newLine();
                    out.flush();
                }

            } catch (IOException e) {
                closeEverything(socket, in, out);
                break;
            } catch (JSONException e) {
                System.err.println("Error creating JSON message: " + e.getMessage());
                e.printStackTrace();
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
        } else if (message.startsWith("/")) {
            processCommand(message);
            return null;
        } else {
            jsonMessage.put("type", "group");
            jsonMessage.put("sender", username);
            jsonMessage.put("content", message);
        }
        return jsonMessage;
    }

    private void processCommand(String command) {
        if (command.equals("/quit")) {
            try {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("type", "system");
                jsonMessage.put("content", username + " has left the chat.");
                out.write(jsonMessage.toString());
                out.newLine();
                out.flush();
                closeEverything(socket, in, out);
            } catch (IOException e) {
                closeEverything(socket, in, out);
            }
        } else {
            System.out.println("Unknown command: " + command);
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String message;
                while ((message = in.readLine()) != null) {
                    try {
                        JSONObject jsonMessage = new JSONObject(message);
                        String type = jsonMessage.getString("type");
                        String content = jsonMessage.getString("content");
                        String sender = jsonMessage.optString("sender", "SYSTEM");

                        switch (type) {
                            case "system":
                                System.out.println(content);
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
                            case "pong":
                                long currentTime = System.currentTimeMillis();
                                pingRoundTripTime = currentTime - lastPingSentTime;
                                System.out.println("[PING] received " + pingRoundTripTime + " ms");
                            default:
                                System.out.println("Unknown message type: " + type);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing message: " + message);
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                closeEverything(socket, in, out);
            }
        }).start();
    }

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter your username: ");
        String username = scanner.nextLine();

        try {
            Socket socket = new Socket("localhost", 2222);
            Client client = new Client(socket, username);
            client.listenForMessage();
            client.sendMessage();
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }
}