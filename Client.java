import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;

class Client {
    static String host = "127.0.0.1";
    static int port = 9999;

    static ClientState state = ClientState.CONNECTING;
    static Socket socket = null;
    static InputStream in = null;
    static OutputStream out = null;

    static Thread inputThread = null;

    static ClientGame game = null;

    static boolean connect() {
        state = ClientState.CONNECTING;

        try {
            socket = new Socket(host, port);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            disconnect();
            return false;
        }

        state = ClientState.CONNECTED;
        return true;
    }

    static void disconnect() {
        state = ClientState.DISCONNECTING;

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

        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }

        if (inputThread != null && inputThread.isAlive()) {
            inputThread.interrupt();
            System.out.print('\n');
        }
        System.out.print("Disconnected from server\n");

        socket = null;

        state = ClientState.DISCONNECTED;
    }

    static void clear() {
        System.out.print("\033[H\033[2J\033[3J");
        // System.out.print("\033\143");
    }

    public static void main(String[] args) {
        clear();

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.printf("ERROR: Invalid port number \"%s\"\n", args[1]);
                System.exit(-1);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(Client::disconnect));

        System.out.printf("Connecting to server at %s:%d...\n", host, port);
        if (!connect()) {
            System.out.print("ERROR: Failed to connect to server\n");
            System.exit(-1);
        }

        System.out.print("Connected to server\n");
        game = new ClientGame();
        while (receiveMessage()) {}
        disconnect();
    }

    static void getInput(String prompt, Callable<Boolean> condition, Function<Byte, Boolean> action) {
        if (inputThread != null && inputThread.isAlive()) {
            inputThread.interrupt();
            while (inputThread.isAlive()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        inputThread = new Thread(() -> {
            try {
                while (condition.call()) {
                    System.out.print(prompt);
                    System.in.read(new byte[System.in.available()]); // skip existing bytes
                    while (condition.call() && System.in.available() < 1) {
                        Thread.sleep(100);
                    }
                    if (!condition.call()) {
                        break;
                    }
                    int input = System.in.read();
                    System.in.read(new byte[System.in.available()]); // skip remaining bytes
                    if (action.apply((byte) input)) {
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        });
        inputThread.start();
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
    static boolean receiveMessage() {
        try {
            int nextByte;
            if ((nextByte = in.read()) == -1) {
                return false;
            }

            if (inputThread != null && inputThread.isAlive()) {
                System.out.print('\n');
            }

            if (nextByte == 'w') {
                game.waitingForOpponent();
                return true;
            }

            if (nextByte == 'x' || nextByte == 'o') {
                char role = Character.toUpperCase((char) nextByte);
                game.gameStarting(role);
                return true;
            }

            if (nextByte == 'Q') {
                if ((nextByte = in.read()) == -1) {
                    return false;
                }
                game.inQueue(nextByte);
                return true;
            }

            if (nextByte == 'X' || nextByte == 'O' || nextByte == ' ') {
                char[] board = new char[9];
                int square = 0;
                boolean eof = false;
                do {
                    board[square] = (char) nextByte;
                } while (++square < 9 && !(eof = ((nextByte = in.read()) == -1)));
                if (eof || (nextByte = in.read()) == -1) {
                    return false;
                }
                boolean yourTurn = nextByte == '1';
                game.boardStateChanged(board, yourTurn);
                return true;
            }

            if (nextByte == 'W') {
                if ((nextByte = in.read()) == -1) {
                    return false;
                }
                game.gameWon(nextByte);
                return true;
            }

            boolean lose = nextByte == 'L';
            if (lose || nextByte == 'T') {
                game.gameLost(!lose);
                return true;
            }

            System.out.printf("ERROR: Received unrecognized byte from server: %s\n", nextByte);
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
    static boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (state != ClientState.DISCONNECTING) {
                System.out.printf("ERROR: Failed to send message to server: %s\n", Arrays.toString(bytes));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}