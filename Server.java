import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

class Server {
    static ServerSocket serverSocket = null;
    static ArrayList<ClientThread> clients = new ArrayList<>();

    static synchronized void shutdown() {
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
        System.out.printf("Server listening on port %d\n", port);
        while (!serverSocket.isClosed()) {
            try {
                new ClientThread(serverSocket.accept()).start();
            } catch (IOException e) {
                // ignore
            }
        }

        shutdown();
    }
}

class ClientThread extends Thread {
    Socket socket;
    InputStream in;
    OutputStream out;

    static final Charset charset = StandardCharsets.US_ASCII;

    ClientThread(Socket socket) {
        this.socket = socket;
    }

    synchronized boolean connect() {
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            disconnect();
            return false;
        }

        if (!Server.clients.add(this)) {
            disconnect();
            return false;
        }
        System.out.printf("Client connected: %s\n", socket.getInetAddress().getHostAddress());
        return true;
    }

    synchronized void disconnect() {
        if (Server.clients.remove(this)) {
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
            // TODO: send disconnect message to client?

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
            System.out.printf("WARNING: Client failed to connect: %s\n", socket.getInetAddress().getHostAddress());
            return;
        }

        while (receiveMessage()) ;

        disconnect();
    }

    /*
     * Turn – Client chooses square _ for their move
     *     1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
     * Does the winner want to play again or not?
     *     `Y` = yes, `N` = no
     * Leave/disconnect (can occur mid-game)
     *     `Q` (or just close socket)
     */
    synchronized boolean receiveMessage() {
        try {
            byte[] bytes = new byte[1];
            int bytesRead = in.read(bytes);
            if (bytesRead != 1) {
                return false;
            }
            String message = new String(bytes, charset);

            // TODO

            if (message.equals("Q")) {
                return false;
            }

            System.out.printf("WARNING: Received unrecognized message from client: \"%s\"\n", message);
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /*
     * Current state of the board
     *     String – each of 9 characters represents one square on the board (`X`, `O` or ` `)
     *         Always sent "square 1", "square 2", …, "square 9"
     * Indicate who plays next
     *     Boolean – `1` = your turn, `0` = other player`s turn
     * You won/lost/tied
     *     `W` = win (followed by byte with length of win streak)
     *     `L` = loss
     *     `T` = tie
     * Indicate winning streak to winner
     *     Send at end of each game to winner
     *     Number – indicates length of the streak
     *         In binary, one byte of 1..255
     *         Streaks longer than 255 will be reported as 255
     * Winner – Play again?
     *     Implied by `W` message
     * Incorrect move
     *     `I`
     *     Only sent if move is incorrect
     *     If correct, message with board and indicating other player`s move
     * How many people before the player in the queue
     *     `Q` followed by byte with value 0…254
     *     255 = lots (255 or more) players ahead of you
     * Waiting for another player to join the game
     *     `w` (lowercase)
     *     Only sent if there is not yet a second player
     *     IF second player arrives, send "game starting" message
     * Game starting
     *     `x` – Game starting, you are X
     *     `o` – Game starting, you are O
     *     Indicate for each player if they are `X` or `O`
     */
    synchronized boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (in != null) {
                System.out.printf("ERROR: Failed to send message to client: \"%s\"\n", new String(bytes, charset));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}