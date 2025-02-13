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
    boolean receiveMessage() {
        try {
            String message = in.readUTF();

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
    boolean sendMessage(String message) {
        try {
            // TODO

            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            if (in != null) {
                System.out.printf("ERROR: Failed to send message to client: \"%s\"\n", message);
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}