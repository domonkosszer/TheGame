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

import javafx.application.Platform;

import gui.BaseController;
import network.Protocol;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    private long lastPingTime;
    private long lastPongTime;
    private int pongLatency;
    private volatile boolean isRunning = true;
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

    public void selectUsername(String userInput) {
        if (!userInput.equals(this.username)) {
            createMessage(new String[] {"username", userInput});
        }
    }

    private void handleSuggestedUsername(String suggestedUsername) {
        Platform.runLater(() -> baseController.setUsername(suggestedUsername));
    }

    public String getUsername() {
        return this.username;
    }

    private void chat(String receiver, String message) {
        if (receiver == null || message == null) {
            System.err.println("Error: receiver and message cannot be null");
        } else if (receiver.equals("DEFAULT")) {
            createMessage(new String[] {"chat", message});
        } else if (receiver.equals("BROADCAST")) {
            createMessage(new String[] {"broadcastChat", message});
        } else {
            createMessage(new String[] {"privateChat", receiver, message});
        }
    }

    public void startPingScheduler() {
        ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(this::sendPing, 0, 2, TimeUnit.SECONDS);
    }

    private void sendPing() {
        lastPingTime = System.currentTimeMillis();
        createMessage(new String[] {"ping"});
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
        createMessage(new String[] {"quit"});
        closeEverything();
        System.exit(0);
    }

    private void createMessage(String[] input) {
        Protocol protocolMessage = new Protocol();
        String type = input[0];
        protocolMessage.put("type", type);

        switch(type) {
            case "privateChat":
                protocolMessage.put("receiver", input[1]);
                protocolMessage.put("content", input[2]);
                break;
            case "chat",
                 "broadcastChat",
                 "username":
                protocolMessage.put("content", input[1]);
                break;
            case "joinLobby":
                protocolMessage.put("lobbyName", input[1]);
                break;
        }

        try {
            out.write(protocolMessage.toString());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            reconnect();
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    Protocol protocolMessage = new Protocol(message);
                    System.out.println("Received: " + protocolMessage);
                    String type = protocolMessage.getString("type");
                    String content = protocolMessage.getString("content");
                    String sender = protocolMessage.getString("sender");
                    switch (type) {
                        case "system":
                            //System.out.println(content);
                            break;
                        case "suggested username":
                            handleSuggestedUsername(content);
                            break;
                        case "final username":
                            this.username = content;
                            Platform.runLater(() -> baseController.switchScene("/fxml/menu.fxml"));
                            break;
                        case "group":
                            //System.out.println(sender + ": " + content);
                            break;
                        case "private":
                            //System.out.println("[PRIVATE] " + sender + ": " + content);
                            break;
                        case "pong":
                            handlePong();
                            break;
                        default:
                            System.err.println("Unknown message type: " + type);
                    }
                }
            } catch (IOException e) {
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

    public void startScanner() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (socket.isConnected()) {
                System.out.print("> ");
                String message = scanner.nextLine().trim();
                if (!message.startsWith("/")) continue;
                handleCommand(message);
            }
        }
    }

    private void handleCommand(String message) {
        String command = message.split("\\s+")[0];
        String input = message.split("\\s+", 2)[1];
        switch (command) {
            case "/username":
                selectUsername(input);
                break;
            case "/joinLobby":
                //joinLobby(input);
                break;
            case "/whisper",
                 "/w":
                String[] whisper = message.split("\\s+", 3);
                if (whisper.length == 3) {
                    String receiver = whisper[1];
                    chat(receiver, whisper[2]);
                } else {
                    System.err.println("Error: Invalid whisper command.");
                }
                break;
            case "/broadcast":
                chat("BROADCAST", input);
                break;
            case "/quit":
                quit();
                break;
            default:
                chat("DEFAULT", message);
        }
    }
}
