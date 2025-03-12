import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

class ServerClient {
    static int lastClientId = 0;
    int clientId;

    ClientState state = ClientState.CONNECTING;
    Socket socket;
    InputStream in;
    OutputStream out;

    Thread thread;

    /**
     * Starts a new thread that runs {@link #connect()} to initialize the client, then listens for messages from the
     * client indefinitely; see {@link #receiveMessage()}.
     * <p>
     * Once a message fails to be received or is invalid, stops listening for messages and calls {@link #disconnect()}.
     *
     * @param socket Socket to associate with the client.
     */
    ServerClient(Socket socket) {
        this.socket = socket;

        thread = new Thread(() -> {
            if (!connect()) {
                System.out.printf("WARNING: %s failed to connect: %s\n", this,
                        socket.getInetAddress().getHostAddress());
                return;
            }

            while (receiveMessage()) {}
            disconnect();
        });
        thread.start();
    }

    /**
     * Builds and returns a string representation of the client, including client ID and special game state data
     * (such as 'Winner', 'Player X', etc.).
     *
     * @return A string representation of the client.
     */
    @Override
    public String toString() {
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

    /**
     * Sets up the client socket input and output streams, and calls the synchronized
     * {@link Server#connect(ServerClient)} method for thread-safe server/game state updates.
     *
     * @return Boolean indicating success.
     */
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

    /**
     * Closes the client socket and its input and output streams, and calls the synchronized
     * {@link Server#disconnect(ServerClient)} method for thread-safe server/game state updates.
     */
    void disconnect() {
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
        Server.disconnect(this);
        socket = null;

        state = ClientState.DISCONNECTED;
    }

    /**
     * Waits for a byte from the client by calling {@link InputStream#read()}, then sends the received byte over to the
     * {@link Server#receiveMessage(ServerClient, int)} method for thread-safe server/game state updates.
     *
     * @return Boolean indicating success.
     */
    boolean receiveMessage() {
        if (state != ClientState.CONNECTED) {
            return false;
        }

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

    /**
     * Sends a message to the client.
     *
     * @return Boolean indicating success.
     */
    boolean sendMessage(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            if (state != ClientState.DISCONNECTING) {
                System.out.printf("ERROR: Failed to send message to %s: %s\n", this, Arrays.toString(bytes));
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}