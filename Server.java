import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

class Server {
    static int port = 9876;

    static ServerSocket serverSocket = null;
    static ServerGame game = null;
    static ArrayList<ServerClient> clients = new ArrayList<>();

    /**
     * Closes the server socket.
     */
    static void shutdown() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Initializes the client and sends them their position in queue (if applicable).
     * <p>
     * Should ONLY be called by {@link ServerClient#connect()}; call that method instead.
     *
     * @param client The client attempting to connect.
     * @return Boolean indicating success.
     */
    synchronized static boolean connect(ServerClient client) {
        client.clientId = ++ServerClient.lastClientId;

        if (!Server.clients.add(client)) {
            return false;
        }
        System.out.printf("%s connected: %s\n", client, client.socket.getInetAddress().getHostAddress());

        if (Server.game.state == GameState.PLAYING || Server.game.state == GameState.WAITING_ON_WINNER) {
            client.sendMessage(new byte[]{'Q', (byte) (Server.clients.size() - 1 - Server.game.getPlayerCount())});
        }
        return true;
    }

    /**
     * Removes the client from the game and updates the server/game state accordingly.
     * <p>
     * Should ONLY be called by {@link ServerClient#disconnect()}; call that method instead.
     *
     * @param client The client attempting to disconnect.
     */
    synchronized static void disconnect(ServerClient client) {
        int clientIndex = Server.clients.indexOf(client);
        if (clientIndex == -1) {
            return;
        }
        Server.clients.remove(clientIndex);
        System.out.printf("%s disconnected: %s\n", client, client.socket.getInetAddress().getHostAddress());

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

    /**
     * Attempt to clear the console using control sequences.
     */
    static void clear() {
        System.out.print("\033[H\033[2J\033[3J");
        // System.out.print("\033\143");
    }

    /**
     * Initializes the server, shutdown hook, server socket and server game state.
     * <p>
     * Listens for client connections on the main thread indefinitely; see {@link ServerClient#ServerClient(Socket)}.
     * <p>
     * Runs {@link #shutdown()} once the server socket closes.
     *
     * @param args Takes in a port number as argument #1, which defaults to {@link #port}.
     * @throws IOException From the {@link ServerSocket#ServerSocket(int)} constructor.
     */
    public static void main(String[] args) throws IOException {
        clear();

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
                new ServerClient(serverSocket.accept());
            } catch (IOException e) {
                // ignore
            }
        }
        shutdown();
    }

    /**
     * Parses the received byte(s) and updates the server/game state accordingly.
     * <p>
     * Called by {@link ServerClient#receiveMessage()}; see that method.
     *
     * @param client   The client sending the message.
     * @param nextByte The byte received from the client.
     * @return Boolean indicating success.
     */
    synchronized static boolean receiveMessage(ServerClient client, int nextByte) {
        // Turn – Client chooses square _ for their move
        // 1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
        if (nextByte >= 1 && nextByte <= 9) {
            Server.game.playTurn(client, nextByte - 1); // subtract 1 to convert to array index
            return true;
        }

        // Does the winner want to play again or not?
        // `Y` = yes, `N` = no
        boolean playAgain = nextByte == 'Y';
        if (playAgain || nextByte == 'N') {
            Server.game.restartGame(client, playAgain);
            return true;
        }

        // Leave/disconnect (can occur mid-game)
        // `Q` (or just close socket)
        if (nextByte == 'Q') {
            return false;
        }

        System.out.printf("WARNING: Received unrecognized byte from %s: %s\n", client, nextByte);
        return false;
    }
}