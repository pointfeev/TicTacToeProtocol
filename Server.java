import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

class Server {
    static ServerSocket serverSocket = null;
    static ServerGame game = null;
    static ArrayList<ServerClient> clients = new ArrayList<>();

    static void shutdown() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    synchronized static boolean connect(ServerClient client) {
        client.clientId = ++ServerClient.lastClientId;

        if (!Server.clients.add(client)) {
            return false;
        }
        System.out.printf("%s connected: %s\n", client.getIdentifier(),
                client.socket.getInetAddress().getHostAddress());

        if (Server.game.state == GameState.PLAYING || Server.game.state == GameState.WAITING_ON_WINNER) {
            client.sendMessage(new byte[]{'Q', (byte) (Server.clients.size() - 1 - Server.game.getPlayerCount())});
        }
        return true;
    }

    synchronized static void disconnect(ServerClient client) {
        int clientIndex = Server.clients.indexOf(client);
        if (clientIndex == -1) {
            return;
        }
        Server.clients.remove(clientIndex);
        System.out.printf("%s disconnected: %s\n", client.getIdentifier(),
                client.socket.getInetAddress().getHostAddress());

        switch (Server.game.state) {
            case PLAYING -> {
                if (Server.game.playerX == client) {
                    Server.game.playerX = null;
                    Server.game.endGame(Server.game.playerO);
                } else if (Server.game.playerO == client) {
                    Server.game.playerO = null;
                    Server.game.endGame(Server.game.playerX);
                } else {
                    Server.game.sendQueueUpdates(clientIndex);
                }
            }
            case WAITING_ON_WINNER -> {
                if (Server.game.lastWinner == client) {
                    Server.game.restartGame(client, false);
                    Server.game.lastWinner = null;
                } else {
                    Server.game.sendQueueUpdates(clientIndex);
                }
            }
        }
    }

    static void clear() {
        System.out.print("\033[H\033[2J\033[3J");
        // System.out.print("\033\143");
    }

    public static void main(String[] args) throws IOException {
        clear();

        int port = 9999;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.printf("ERROR: Invalid port number \"%s\"\n", args[0]);
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

    /*
     * Turn – Client chooses square _ for their move
     *     1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
     * Does the winner want to play again or not?
     *     `Y` = yes, `N` = no
     * Leave/disconnect (can occur mid-game)
     *     `Q` (or just close socket)
     */
    synchronized static boolean receiveMessage(ServerClient client, int nextByte) {
        if (nextByte == 'Q') {
            return false;
        }

        if (nextByte >= 49 && nextByte <= 57) { // decimal values for ASCII number characters 1-9
            Server.game.playTurn(client, nextByte - 49); // subtract 49 to get a board square index (0-8)
            return true;
        }

        boolean playAgain = nextByte == 'Y';
        if (playAgain || nextByte == 'N') {
            Server.game.restartGame(client, playAgain);
            return true;
        }

        System.out.printf("WARNING: Received unrecognized byte from %s: %s\n", client.getIdentifier(), nextByte);
        return false;
    }
}