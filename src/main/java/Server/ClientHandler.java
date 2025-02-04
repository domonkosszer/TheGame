package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

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
            broadcastMessage("Server: " + username + " has entered the Chat");

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
                broadcastMessage(messageFromClient);
            } catch (IOException e) {
                closeEverything(socket, in, out);
                break;
            }
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

    public void removeClient() {
        clientHandlers.remove(this);
        broadcastMessage("SERVER: " + username + " has left the Chat");
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
