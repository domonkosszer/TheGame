package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import gui.BaseController;
import org.json.JSONException;
import org.json.JSONObject;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    private long lastPingTime;
    private long lastPongTime;
    private int pongLatency;
    private volatile boolean isRunning = true;
    private ScheduledExecutorService pingScheduler;
    private BaseController baseController;

    public Client(Socket socket) {
        try {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.lastPongTime = System.currentTimeMillis();
            startPingScheduler();
        } catch (IOException e) {
            reconnect();
        }
    }

    public void setBaseController(BaseController baseController) {
        this.baseController = baseController;
    }

    public void selectUsername(String userInput) throws IOException {
        if (!userInput.equals(this.username)) {
            createJsonMessage(new String[] {"username", userInput});
        }
    }

    private void handleSuggestedUsername(String suggestedUsername) {
        Platform.runLater(() -> baseController.setUsername(suggestedUsername));
    }

    public void startPingScheduler() {
        pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(this::sendPing, 0, 2, TimeUnit.SECONDS);
    }

    private void sendPing() {
        try {
            lastPingTime = System.currentTimeMillis();
            createJsonMessage(new String[] {"ping"});
        } catch (IOException e) {
            reconnect();
        }
    }

    private void handlePong() {
        long currentTime = System.currentTimeMillis();
        lastPongTime = currentTime;
        pongLatency = (int) (currentTime - lastPingTime);
        setPing();
    }

    public void setPing() {
        baseController.setPing(pongLatency);
    }

    public void quit() {
        isRunning = false;
        try{
            createJsonMessage(new String[] {"quit"});
        } catch (IOException e) {
            System.err.println("Error sending quit message: " + e.getMessage());
        }
        closeEverything();
        System.exit(0);
    }

    private void createJsonMessage(String[] input) throws IOException {
        JSONObject jsonMessage = new JSONObject();
        String type = input[0];
        jsonMessage.put("type", type);

        switch(type) {
            case "private":
                jsonMessage.put("receiver", input[1]);
                jsonMessage.put("content", input[2]);
                break;
            case "username",
                 "message":
                jsonMessage.put("content", input[1]);
                break;
            case "lobby":
                jsonMessage.put("lobbyName", input[1]);
                break;
        }
        out.write(jsonMessage.toString());
        System.out.println("Sent: " + jsonMessage);
        out.newLine();
        out.flush();
    }

    public void listenForMessage() {
        new Thread(() -> {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                    JSONObject jsonMessage = new JSONObject(message);
                    String type = jsonMessage.getString("type");
                    String content = jsonMessage.optString("content");
                    String sender = jsonMessage.optString("sender", "SYSTEM");
                    switch (type) {
                        case "system":
                            System.out.println(content);
                            break;
                        case "suggested username":
                            handleSuggestedUsername(content);
                            break;
                        case "final username":
                            this.username = content;
                            Platform.runLater(() -> {
                                baseController.switchScene("/fxml/menu.fxml");
                            });
                            break;
                        case "group":
                            System.out.println(sender + ": " + content);
                            break;
                        case "private":
                            System.out.println("[PRIVATE] " + sender + ": " + content);
                            break;
                        case "pong":
                            handlePong();
                            break;
                        default:
                            System.out.println("Unknown message type: " + type);
                    }
                }
            } catch (IOException | JSONException e) {
                reconnect();
            }
        }).start();
    }

    public void reconnect() {
        closeEverything();
        try {
            Socket newSocket = new Socket("localhost", 2222);
            this.socket = newSocket;
            this.in = new BufferedReader(new InputStreamReader(newSocket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream()));
            listenForMessage();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public void closeEverything() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return this.username;
    }
}
