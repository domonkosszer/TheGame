package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

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
                default:
                    System.out.println("Unknown message type: " + type);
                    break;
            }

        } catch (JSONException e) {
            System.err.println("Invalid JSON format received: " + messageIn);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during message processing: " + e.getMessage());
            e.printStackTrace();
        }
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
                closeEverything(socket, in, out);
                break;
            }
        }
    }

    private void sendPrivateMessage(JSONObject message) {
        String receiver = message.getString("receiver");
        String sender = message.getString("sender");

        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.username.equals(receiver) && !clientHandler.username.equals(sender)) {
                try {
                    clientHandler.out.write(message.toString());
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                    return;
                } catch (IOException e) {
                    closeEverything(socket, in, out);
                    break;
                }
            }
        }

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
                    closeEverything(socket, in, out);
                    break;
                }
            }
        }
    }

    private void broadcastSystemMessage(String message) {
        JSONObject jsonMessage = new JSONObject();
        jsonMessage.put("type", "system");
        jsonMessage.put("content", message);

        for (ClientHandler clientHandler : clientHandlers) {
            try {
                clientHandler.out.write(jsonMessage.toString());
                clientHandler.out.newLine();
                clientHandler.out.flush();
            } catch (IOException e) {
                closeEverything(socket, in, out);
                break; // Exit loop if a client disconnects
            }
        }
    }

    public void removeClient() {
        clientHandlers.remove(this);
        broadcastSystemMessage(username + " has left the chat.");
    }

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        if (socket == null || in == null || out == null) return;
        removeClient();
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}