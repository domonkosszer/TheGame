package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = in.readLine();
                processMessage(messageFromClient);
            } catch (IOException e) {
                closeEverything(socket, in, out);
                break;
            }
        }
    }

    private void processMessage(String messageIn){
        try {
            JSONObject jsonMessage = new JSONObject(messageIn);
            String type = jsonMessage.getString("type");
            String sender = jsonMessage.getString("sender");
            String content = jsonMessage.getString("content");

            if (type.equals("group")){
                broadcastMessage(sender + ": " + content);
            } else if (type.equals("private")) {
                String receiver = jsonMessage.getString("receiver");
                sendPrivateMessage(sender, receiver, content);
            }
        } catch (Exception e) {
            System.out.println("Invalid message format recieved!");
        }
    }

    public void broadcastMessage(String message) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                if (!clientHandler.username.equals(username)) {
                    clientHandler.out.write(message);
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                }
            } catch (IOException e) {
                closeEverything(socket, in, out);
            }
        }
    }

    private void sendPrivateMessage(String sender, String receiver, String message) {
        for (ClientHandler clientHandler: clientHandlers){
            if(clientHandler.username.equals(receiver)){
                try {
                    clientHandler.out.write("[PRIVATE] " + sender + ": " + message);
                    clientHandler.out.newLine();
                    clientHandler.out.flush();
                } catch (IOException e) {
                    closeEverything(socket, in, out);
                }
            }
        }
    }

    private void broadcastSystemMessage(String message) {
        for (ClientHandler clientHandler: clientHandlers) {
            try {
                clientHandler.out.write("[SYSTEM]: " + message);
                clientHandler.out.newLine();
                clientHandler.out.flush();
            } catch (IOException e) {
                closeEverything(socket, in, out);
            }
        }
    }

    public void removeClient() {
        clientHandlers.remove(this);
        broadcastSystemMessage(username + " has left the chat.");
    }

    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        removeClient();
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if(socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
