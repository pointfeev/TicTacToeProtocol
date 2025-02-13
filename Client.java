import java.io.*;
import java.net.Socket;

class Client {
    static String host = "127.0.0.1";
    static int port = 9999;

    static Socket socket;
    static DataInputStream in;
    static DataOutputStream out;

    static boolean connect() {
        try {
            socket = new Socket(host, port);

            InputStream inputStream = socket.getInputStream();
            in = new DataInputStream(inputStream);

            OutputStream outputStream = socket.getOutputStream();
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            out = new DataOutputStream(bufferedOutputStream);
        } catch (IOException e) {
            disconnect();
            return false;
        }

        System.out.printf("Connected to server at %s:%d\n", socket.getInetAddress().getHostAddress(), socket.getPort());
        return true;
    }

    static void disconnect() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null;
        }

        if (out != null) {
            sendMessage("Q");
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
     * Turn – Client chooses square _ for their move
     *     1-9, with 1=top left, 2=top middle, 3=top right, 4=center left, … 9=bottom right
     * Does the winner want to play again or not?
     *     ‘Y’ = yes, ‘N’ = no
     * Leave/disconnect (can occur mid-game)
     *     ‘Q’ (or just close socket)
     */
    static boolean sendMessage(String message) {
        try {
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            if (in != null) {
                System.out.printf("ERROR: Failed to send message \"%s\" to server", message);
                System.exit(-1);
            }
            return false;
        }
        return true;
    }
}