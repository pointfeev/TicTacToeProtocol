import java.util.Random;

class ServerGame {
    GameState state = GameState.INITIALIZING;
    char[] board = new char[9];
    int turn = 0;
    ServerClient playerX = null;
    ServerClient playerO = null;
    Random random = new Random();
    int streak = 0;
    ServerClient lastWinner = null;

    ServerGame() {
        random.setSeed(System.currentTimeMillis());
        waitForPlayers();
    }

    void waitForPlayers() {
        if (state == GameState.WAITING_FOR_PLAYERS) {
            return;
        }

        playerX = null;
        playerO = null;

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
                player1.sendMessage("w".getBytes(ServerClient.charset));
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
        playerX.sendMessage("x".getBytes(ServerClient.charset));
        playerO.sendMessage("o".getBytes(ServerClient.charset));

        for (int square = 0; square < 9; square++) {
            board[square] = ' ';
        }
        turn = 0;

        state = GameState.PLAYING;
        System.out.print("Game started!\n");

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

        if (winner != lastWinner) {
            streak = 0;
        }
        lastWinner = winner;

        String output = "Game over, ";
        if (winner != null) {
            streak++;

            if (winner == playerX) {
                output += "X wins!";
                if (Server.clients.remove(playerO)) {
                    Server.clients.add(playerO);
                }
            } else {
                output += "O wins!";
                if (Server.clients.remove(playerX)) {
                    Server.clients.add(playerX);
                }
            }
            if (streak > 1) {
                output += " This player has won " + streak + " games in a row!";
            }
            System.out.printf("%s\n", output);

            state = GameState.WAITING_ON_WINNER;
            System.out.print("Waiting on winner to respond...\n");

            // TODO: send `W`, `L` messages to respective clients
            //       also send byte to indicate winning streak to winner (cap at 255 before sending)

        } else {
            output += "it's a tie!";
            System.out.printf("%s\n", output);

            // TODO: send `T` messages to respective clients

            waitForPlayers();
        }
    }

    void restartGame(ServerClient player, boolean winnerPlaysAgain) {
        if (state != GameState.WAITING_ON_WINNER) {
            return;
        }

        if (player != lastWinner) {
            return;
        }

        if (!winnerPlaysAgain) {
            if (Server.clients.remove(lastWinner)) {
                Server.clients.add(lastWinner);
                System.out.print("Winner will not play again.\n");
            }
        } else {
            System.out.print("Winner will play again!\n");
        }

        waitForPlayers();
    }
}