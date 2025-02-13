import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class Client {
    static String host = "127.0.0.1";
    static int port = 9999;

    static Socket socket;
    static InputStream in;
    static OutputStream out;

    static final Charset charset = StandardCharsets.US_ASCII;

    static synchronized boolean connect() {
        try {
            socket = new Socket(host, port);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            disconnect();
            return false;
        }

        System.out.printf("Connected to server at %s:%d\n", socket.getInetAddress().getHostAddress(), socket.getPort());
        return true;
    }

    static synchronized void disconnect() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null;
        }

        if (out != null) {
            sendMessage("Q".getBytes(charset));
            System.out.print("Disconnected from server\n");

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

    public static void main(String[] args) throws IOException {
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.printf("ERROR: Invalid port number \"%s\"", args[1]);
                System.exit(-1);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(Client::disconnect));

        if (!connect()) {
            System.out.printf("ERROR: Failed to connect to server at %s:%d", host, port);
            System.exit(-1);
        }

        // TODO
        System.in.read();
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
    static synchronized boolean receiveMessage() {
        try {
            byte[] bytes = new byte[100];
            // TODO: verify how many bytes we will send and alter the byte array (buffer) length
            int bytesRead = in.read(bytes);
            if (bytesRead == -1) {
                return false;
            }
            String message = new String(bytes, charset);

            // TODO

            System.out.printf("WARNING: Received unrecognized message from server: %s\n", message);
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /*
     * Turn – Client chooses square _ for their move
     *     1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
     * Does the winner want to play again or not?
     *     `Y` = yes, `N` = no
     * Leave/disconnect (can occur mid-game)
     *     `Q` (or just close socket)
     */
    static synchronized boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (in != null) {
                System.out.printf("ERROR: Failed to send message to server: \"%s\"", new String(bytes, charset));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}