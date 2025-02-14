import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

enum ClientState {
    CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED
}

class ServerClient extends Thread {
    ClientState state = ClientState.CONNECTING;
    Socket socket;
    InputStream in;
    OutputStream out;
    static final Charset charset = StandardCharsets.US_ASCII;

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

        if (!Server.clients.add(this)) {
            disconnect();
            return false;
        }
        System.out.printf("%s connected: %s\n", getIdentifier(), socket.getInetAddress().getHostAddress());

        if (Server.game.state == GameState.PLAYING) {
            // TODO: send `Q` to this client, along with a byte containing how many clients ahead in queue (cap at 255 before sending)
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
        int clientIndex = Server.clients.indexOf(this);
        if (clientIndex != -1) {
            Server.clients.remove(clientIndex);
            String identifier = getIdentifier();
            System.out.printf("%s disconnected: %s\n", identifier, socket.getInetAddress().getHostAddress());
            switch (identifier) {
                case "Player X" -> {
                    Server.game.playerX = null;
                    Server.game.endGame(Server.game.playerO);
                }
                case "Player O" -> {
                    Server.game.playerO = null;
                    Server.game.endGame(Server.game.playerX);
                }
            }

            if (Server.game.state == GameState.PLAYING) {
                // TODO: send `Q` to all clients not playing, along with a byte containing how many clients ahead in queue (cap at 255 before sending)
                //       only send to clients whose queue position changed; use clientIndex from above
            }
        }
        socket = null;

        state = ClientState.DISCONNECTED;
    }

    @Override
    public void run() {
        if (!connect()) {
            System.out.printf("WARNING: %s failed to connect: %s\n", getIdentifier(), socket.getInetAddress().getHostAddress());
            return;
        }

        while (receiveMessage()) {}
        disconnect();
    }

    String getIdentifier() {
        if (Server.game.playerX == this) {
            return "Player X";
        } else if (Server.game.playerO == this) {
            return "Player O";
        }
        return "Client";
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
            byte[] bytes = new byte[1];
            int bytesRead = in.read(bytes);
            if (bytesRead != 1) {
                return false;
            }
            String message = new String(bytes, charset);

            try {
                int square = Integer.parseInt(message);
                if (square >= 0 && square <= 9) {
                    Server.game.playTurn(this, square);
                    return true;
                }
            } catch (NumberFormatException e) {
                // ignore
            }

            switch (message) {
                case "Y" -> {
                    // TODO: for asking the winner if they want to play again
                    return true;
                }
                case "N" -> {
                    // TODO: for asking the winner if they want to play again
                    return true;
                }

                case "Q" -> {
                    return false;
                }
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
    boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (state != ClientState.DISCONNECTING) {
                System.out.printf("ERROR: Failed to send message to client: \"%s\"\n", new String(bytes, charset));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}