import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

class Client {
    static String host = "127.0.0.1";
    static int port = 9999;

    static ClientState state = ClientState.CONNECTING;
    static Socket socket;
    static InputStream in;
    static OutputStream out;

    static char role = ' ';

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
        while (receiveMessage()) {}
        disconnect();
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
            byte[] bytes = new byte[2];
            int bytesRead = in.read(bytes);
            if (bytesRead == -1) {
                return false;
            }

            if (bytes[0] == 'w') {
                clear();
                System.out.print("Waiting for another player...\n");
                return true;
            }

            if (bytes[0] == 'x' || bytes[0] == 'o') {
                role = Character.toUpperCase((char) bytes[0]);
                clear();
                System.out.printf("Game starting, you will be %c...\n", role);
                return true;
            }

            if (bytes[0] == 'Q') {
                int ahead = Byte.toUnsignedInt(bytes[1]);
                String output = "You are in a queue to play. ";
                if (ahead == 0) {
                    output += "You are next in line.";
                } else {
                    output += "There " + (ahead == 1 ? "is 1 client" : "are " + ahead + " clients") + " ahead of you.";
                }
                System.out.print(output + '\n');
                return true;
            }

            boolean win = bytes[0] == 'W';
            boolean lose = bytes[0] == 'L';
            if (win || lose || bytes[0] == 'T') {
                String output = "Game over, ";
                if (win) {
                    output += "you win!";
                    int streak = Byte.toUnsignedInt(bytes[1]);
                    if (streak > 1) {
                        output += " You have won " + streak + " games in a row!";
                    }
                } else if (lose) {
                    output += "you lose!";
                } else {
                    output += "it's a tie!";
                }
                System.out.print(output + '\n');

                if (win) {
                    while (true) {
                        System.out.print("Do you want to play again (Y/N)? ");
                        int choiceByte = System.in.read();
                        System.in.read(new byte[System.in.available()]); // skip the rest of the bytes
                        char choice = Character.toUpperCase((char) choiceByte);
                        if (choice == 'Y' || choice == 'N') {
                            sendMessage(new byte[]{(byte) choice});
                            break;
                        }
                    }
                } else {
                    // TODO: see if we need to handle lose or tie any further here
                }
                return true;
            }

            System.out.printf("ERROR: Received unrecognized message from server: %s\n", Arrays.toString(bytes));
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