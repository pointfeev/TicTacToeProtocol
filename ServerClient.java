import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

class ServerClient extends Thread {
    static int lastClientId = 0;
    int clientId;

    ClientState state = ClientState.CONNECTING;
    Socket socket;
    InputStream in;
    OutputStream out;

    ServerClient(Socket socket) {
        this.socket = socket;
    }

    boolean connect() {
        state = ClientState.CONNECTING;

        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            disconnect();
            return false;
        }

        if (!Server.connect(this)) {
            disconnect();
            return false;
        }

        state = ClientState.CONNECTED;
        return true;
    }

    void disconnect() {
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
        Server.disconnect(this);
        socket = null;

        state = ClientState.DISCONNECTED;
    }

    @Override
    public void run() {
        if (!connect()) {
            System.out.printf("WARNING: %s failed to connect: %s\n", getIdentifier(),
                    socket.getInetAddress().getHostAddress());
            return;
        }

        while (receiveMessage()) {}
        disconnect();
    }

    String getIdentifier() {
        String identifier = "Client #" + clientId;
        if (Server.game.state == GameState.WAITING_ON_WINNER && Server.game.lastWinner == this) {
            return identifier + " (Winner)";
        }
        if (Server.game.state == GameState.PLAYING) {
            if (Server.game.playerX == this) {
                return identifier + " (Player X)";
            } else if (Server.game.playerO == this) {
                return identifier + " (Player O)";
            }
        }
        return identifier;
    }

    boolean receiveMessage() {
        try {
            int nextByte;
            if ((nextByte = in.read()) == -1) {
                return false;
            }

            return Server.receiveMessage(this, nextByte);
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
    boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (state != ClientState.DISCONNECTING) {
                System.out.printf("ERROR: Failed to send message to %s: %s\n", getIdentifier(), Arrays.toString(bytes));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}