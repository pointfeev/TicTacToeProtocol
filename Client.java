import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;

class Client {
    static String host = "127.0.0.1";
    static int port = 9876;

    static ClientState state = ClientState.CONNECTING;
    static Socket socket = null;
    static InputStream in = null;
    static OutputStream out = null;

    static Thread inputThread = null;

    static ClientGame game = null;

    /**
     * Initializes the client, shutdown hook, client socket and client game state.
     * <p>
     * Listens for messages from the server on the main thread indefinitely; see {@link #receiveMessage()}.
     * <p>
     * Once a message fails to be received or is invalid, stops listening for messages and calls {@link #disconnect()}.
     *
     * @param args Takes in a host as argument #1, which defaults to {@link #host}. Takes in a port number as
     *             argument #2, which defaults to {@link #port}.
     */
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
        while (receiveMessage()) {
            Thread.yield();
        }
        disconnect();
    }

    /**
     * Attempt to clear the console using control sequences.
     */
    static void clear() {
        System.out.print("\033[H\033[2J\033[3J");
        // System.out.print("\033\143");
    }

    /**
     * Runs a thread that waits for input from the client.
     * <p>
     * Starts by calling {@link #stopReadingInput()} in case the client is already reading input.
     * <p>
     * I use a thread here so that we can wait for input in a non-blocking manner, so that the client can listen to
     * messages while it waits on input.
     * <p>
     * Unfortunately, the way I currently do this will make it so that typed input doesn't display until the client
     * submits it (by hitting enter) on Windows-based terminals; I either need to find a way around that, or just
     * scrap this entirely.
     *
     * @param prompt    The input prompt.
     * @param condition The condition (callable) by which to continue waiting for input.
     * @param action    The action (function) to call once input has been obtained.
     */
    static void readInput(String prompt, Callable<Boolean> condition, Function<Byte, Boolean> action) {
        stopReadingInput();

        inputThread = new Thread(() -> {
            try {
                while (!inputThread.isInterrupted() && condition.call()) {
                    System.in.read(new byte[System.in.available()]); // skip existing bytes
                    System.out.print(prompt);
                    while (System.in.available() < 1) { // wait for a byte to become available
                        Thread.yield();
                        if (inputThread.isInterrupted() || !condition.call()) {
                            return;
                        }
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

    /**
     * Interrupts the thread that is waiting for input and waits for that thread to die.
     */
    static void stopReadingInput() {
        if (inputThread != null && inputThread.isAlive()) {
            do {
                if (!inputThread.isInterrupted()) {
                    inputThread.interrupt();
                }
                Thread.yield();
            } while (inputThread.isAlive());
            System.out.print('\n');
        }
    }

    /**
     * Sets up the client socket and its input and output streams.
     *
     * @return Boolean indicating success.
     */
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

    /**
     * Closes the client socket and its input and output streams.
     * <p>
     * Calls {@link #stopReadingInput()} to interrupt input before disconnecting.
     */
    static void disconnect() {
        if (state == ClientState.DISCONNECTING || state == ClientState.DISCONNECTED) {
            return;
        }
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

        stopReadingInput();
        System.out.print("Disconnected from server\n");

        socket = null;

        state = ClientState.DISCONNECTED;
    }

    /**
     * Waits for a byte from the server by calling {@link InputStream#read()}, then parses the received byte(s) and
     * updates the client/game state accordingly.
     * <p>
     * Starts by calling {@link #stopReadingInput()} in case the client is reading input.
     *
     * @return Boolean indicating success.
     */
    static boolean receiveMessage() {
        try {
            int nextByte;
            if ((nextByte = in.read()) == -1) {
                return false;
            }

            stopReadingInput();

            // Waiting for another player to join the game
            // `w` (lowercase)
            // Only sent if there is not yet a second player
            // IF second player arrives, send "game starting" message
            if (nextByte == 'w') {
                game.waitingForOpponent();
                return true;
            }

            // Game starting
            // `x` – Game starting, you are X
            // `o` – Game starting, you are O
            // Indicate for each player if they are `X` or `O`
            if (nextByte == 'x' || nextByte == 'o') {
                char role = Character.toUpperCase((char) nextByte);
                game.gameStarting(role);
                return true;
            }

            // How many people before the player in the queue
            // `Q` followed by byte with value 0…254
            // 255 = lots (255 or more) players ahead of you
            if (nextByte == 'Q') {
                if ((nextByte = in.read()) == -1) {
                    return false;
                }
                game.inQueue(nextByte);
                return true;
            }

            // Current state of the board
            // String – each of 9 characters represents one square on the board (`X`, `O` or ` `)
            // Always sent "square 1", "square 2", …, "square 9"
            if (nextByte == 'X' || nextByte == 'O' || nextByte == ' ') {
                char[] board = new char[9];
                int square = 0;
                boolean eof = false;
                do {
                    board[square] = (char) nextByte;
                } while (++square < 9 && !(eof = ((nextByte = in.read()) == -1)));
                if (eof) {
                    return false;
                }
                game.boardStateChanged(board);
                return true;
            }

            // Indicate who plays next
            // Boolean – `1` = your turn, `0` = other player`s turn
            boolean yourTurn = nextByte == 1;
            if (yourTurn || nextByte == 0) {
                game.nextTurn(yourTurn);
                return true;
            }

            // Incorrect move
            // `I`
            // Only sent if move is incorrect
            // If correct, message with board and indicating other player`s move
            if (nextByte == 'I') {
                game.invalidMove();
                return true;
            }

            // You won/lost/tied
            // `W` = win (followed by byte with length of win streak)
            // `L` = loss
            // `T` = tie
            if (nextByte == 'W') {
                // Indicate winning streak to winner
                // Send at end of each game to winner
                // Number – indicates length of the streak
                // In binary, one byte of 1..255
                // Streaks longer than 255 will be reported as 255
                if ((nextByte = in.read()) == -1) {
                    return false;
                }
                game.gameWon(nextByte);
                return true;
            }
            boolean tie = nextByte == 'T';
            if (tie || nextByte == 'L') {
                game.gameLost(tie);
                return true;
            }

            System.out.printf("ERROR: Received unrecognized byte from server: %s\n", nextByte);
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /**
     * Sends a message to the server.
     *
     * @return Boolean indicating success.
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