package Client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import org.json.JSONObject;

public class Client {

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.username = username;
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void sendMessage() {
        try {
            out.write(username);
            out.newLine();
            out.flush();

            Scanner scanner = new Scanner(System.in);
            while (socket.isConnected()) {
                String message = scanner.nextLine();
                out.write(username + ": " + message);
                out.newLine();
                out.flush();
            }
        } catch (IOException e) {
            closeEverything(socket, in, out);
        }
    }

    public void listenForMessage() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                String groupChatMessage;
                try {
                    groupChatMessage = in.readLine();
                    System.out.println(groupChatMessage);
                } catch (IOException e) {
                    closeEverything(socket, in, out);
                }
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
            if(socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {

        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter your username: ");
        String username = scanner.nextLine();
        Socket socket = new Socket("localhost", 2222);
        Client client = new Client(socket, username);
        client.listenForMessage();
        client.sendMessage();

    }
}
