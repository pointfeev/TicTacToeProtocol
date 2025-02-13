import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

class Server {
    static ServerSocket serverSocket = null;
    static HashSet<ClientThread> clients = new HashSet<>();

    public static void main(String[] args) throws IOException {
        int port = 9999;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.printf("ERROR: Invalid port number \"%s\"", args[0]);
                System.exit(-1);
            }
        }

        serverSocket = new ServerSocket(port);
        System.out.printf("Server listening on port %d\n", port);
        while (true) {
            try {
                new ClientThread(serverSocket.accept()).start();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}

class ClientThread extends Thread {
    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    ClientThread(Socket socket) {
        this.socket = socket;
    }

    boolean connect() {
        try {
            InputStream inputStream = socket.getInputStream();
            in = new DataInputStream(inputStream);

            OutputStream outputStream = socket.getOutputStream();
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            out = new DataOutputStream(bufferedOutputStream);
        } catch (IOException e) {
            disconnect();
            return false;
        }

        Server.clients.add(this);
        System.out.printf("Client connected: %s\n", socket.getInetAddress().getHostAddress());
        return true;
    }

    void disconnect() {
        if (Server.clients.contains(this)) {
            Server.clients.remove(this);
            System.out.printf("Client disconnected: %s\n", socket.getInetAddress().getHostAddress());
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null;
        }

        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                // ignore
            }
            out = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            socket = null;
        }
    }

    @Override
    public void run() {
        if (!connect()) {
            System.out.printf("Client failed to connect: %s\n", socket.getInetAddress().getHostAddress());
            return;
        }

        while (processIncomingMessage()) ;

        disconnect();
    }

    /*
     * Turn – Client chooses square _ for their move
     *     1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
     * Does the winner want to play again or not?
     *     ‘Y’ = yes, ‘N’ = no
     * Leave/disconnect (can occur mid-game)
     *     ‘Q’ (or just close socket)
     */
    boolean processIncomingMessage() {
        try {
            String message = in.readUTF();

            // TODO

            if (message.equals("Q")) {
                return false;
            }

            System.out.printf("Received unrecognized message from client: %s\n", message);
        } catch (IOException e) {
            // ignore
        }
        return false;
    }
}