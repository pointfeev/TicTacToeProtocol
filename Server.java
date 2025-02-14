import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

class Server {
    static ServerSocket serverSocket = null;
    static ServerGame game = null;
    static ArrayList<ServerClient> clients = new ArrayList<>();

    static void shutdown() {
        for (int i = clients.size() - 1; i >= 0; i--) {
            clients.get(i).disconnect();
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

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

        Runtime.getRuntime().addShutdownHook(new Thread(Server::shutdown));

        serverSocket = new ServerSocket(port);
        System.out.printf("Server started on port %d\n", port);
        game = new ServerGame();
        while (!serverSocket.isClosed()) {
            try {
                new ServerClient(serverSocket.accept()).start();
            } catch (IOException e) {
                // ignore
            }
        }
        shutdown();
    }
}