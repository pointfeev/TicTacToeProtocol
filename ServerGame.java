import java.util.Random;

enum GameState {
    INITIALIZING, WAITING_FOR_PLAYERS, PLAYING
}

class ServerGame {
    GameState state = GameState.INITIALIZING;
    char[] board = new char[9];
    int turn = 0;
    ServerClient playerX = null;
    ServerClient playerO = null;

    Random random = new Random();

    ServerGame() {
        random.setSeed(System.currentTimeMillis());

        waitForPlayers();
    }

    void waitForPlayers() {
        if (state == GameState.WAITING_FOR_PLAYERS) {
            return;
        }

        state = GameState.WAITING_FOR_PLAYERS;
        System.out.print("Waiting for players...\n");
        new Thread(() -> {
            while (!Server.serverSocket.isClosed()) {
                if (findPlayers()) {
                    startGame();
                    break;
                }
            }
        }).start();
    }

    boolean findPlayers() {
        int clientCount = Server.clients.size();
        ServerClient player1 = clientCount >= 1 ? Server.clients.get(0) : null;
        if (player1 == null || player1.state != ClientState.CONNECTED) {
            return false;
        }
        ServerClient player2 = clientCount >= 2 ? Server.clients.get(1) : null;

        if (playerX != player1 && playerO != player1) {
            if (random.nextBoolean()) {
                playerX = player1;
            } else {
                playerO = player1;
            }

            if (player2 == null || player2.state != ClientState.CONNECTED) {
                // TODO: send `w` to player1
                return false;
            }
        }

        if (player2 == null || player2.state != ClientState.CONNECTED) {
            return false;
        }
        if (playerX == null) {
            playerX = player2;
        } else {
            playerO = player2;
        }
        return true;
    }

    void startGame() {
        for (int square = 0; square < 9; square++) {
            board[square] = ' ';
        }
        turn = 0;

        state = GameState.PLAYING;
        System.out.print("Game started\n");

        // TODO: send `x`, `o` messages to respective clients

        // TODO: send board state and indicator of who plays next to game players
    }

    ServerClient getTurnPlayer() {
        return turn % 2 == 0 ? playerX : playerO;
    }

    void playTurn(ServerClient player, int square) {
        if (state != GameState.PLAYING) {
            return;
        }

        ServerClient turnPlayer = getTurnPlayer();
        if (turnPlayer != player) {
            return;
        }

        if (board[square] != ' ') {
            // TODO: send `I` to turnPlayer
            return;
        }
        board[square] = turnPlayer == playerX ? 'X' : 'O';
        turn++;

        // TODO: send board state and indicator of who plays next to game players
    }

    void endGame(ServerClient winner) {
        if (state != GameState.PLAYING) {
            return;
        }

        String output = "Game over, ";
        if (winner == playerX) {
            output += "X wins!";
        } else if (winner == playerO) {
            output += "O wins!";
        } else {
            output += "it's a tie!";
        }
        System.out.printf("%s\n", output);

        // TODO: send `W`, `L`, `T` messages to respective clients and shift the loser to the end of the queue (client array)
        //       also send byte to indicate winning streak to winner (cap at 255 before sending; we also need to figure out how/where to store that value)
        //       we will probably want to enter a WAITING_ON_WINNER state or something like that here to ask if they want to play again
        //       make sure we shift the winner to the end of the queue (client array) too if they choose not to play again

        playerX = null;
        playerO = null;
        waitForPlayers();
    }
}