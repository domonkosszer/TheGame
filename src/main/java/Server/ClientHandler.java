package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = in.readLine();

            clientHandlers.add(this);
            broadcastSystemMessage(username + " has entered the chat.");
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = in.readLine();
                if (messageFromClient == null) {
                    break;
                }
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
                    broadcastMessage(jsonMessage);
                    break;
                case "private":
                    sendPrivateMessage(jsonMessage);
                    break;
                case "system":
                    broadcastMessage(jsonMessage);
                    break;
                case "ping":
                    handlePing(jsonMessage);
                    break;
                case "pong":
                    sendPrivateMessage(jsonMessage);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            System.err.println("Invalid JSON format received: " + messageIn);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during message processing: " + e.getMessage());
        }
    }

    private void handlePing(JSONObject jsonMessage) {
        String sender = jsonMessage.optString("sender");

        if (sender == null || sender.isEmpty()) {
            System.err.println("Ping sender is missing!");
            return;
        }

        JSONObject pongMessage = new JSONObject();
        pongMessage.put("type", "pong");
        pongMessage.put("sender", username);
        pongMessage.put("receiver", sender);

        sendPrivateMessage(pongMessage);
        System.out.println("Ping received, Pong sent to " + sender); // Debugging line
    }

    public void broadcastMessage(JSONObject message) {
        Iterator<ClientHandler> iterator = clientHandlers.iterator();
        while (iterator.hasNext()) {
            ClientHandler clientHandler = iterator.next();
            try {
                if (!clientHandler.username.equals(username)) {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                }
            } catch (IOException e) {
                iterator.remove();
                closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
            }
        }
    }

    private void sendPrivateMessage(JSONObject message) {
        String receiver = message.optString("receiver");
        String sender = message.optString("sender");

        if (receiver == null || receiver.isEmpty()) {
            System.err.println("Receiver username is missing or empty.");
            return;
        }

        Iterator<ClientHandler> iterator = clientHandlers.iterator();
        while (iterator.hasNext()) {
            ClientHandler clientHandler = iterator.next();
            if (clientHandler.username.equals(receiver) && !clientHandler.username.equals(sender)) {
                try {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                    return;
                } catch (IOException e) {
                    iterator.remove();
                    closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
                    break;
                }
            }
        }

        if (!message.optString("type").equals("pong")) {
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
                        iterator.remove();
                        closeEverything(clientHandler.socket, clientHandler.in, clientHandler.out);
                        break;
                    }
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

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        if (socket == null || in == null || out == null) return;

        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}