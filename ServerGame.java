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
                player1.sendMessage(new byte[]{'w'});
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

    int getPlayerCount() {
        int players = 2;
        if (playerX == null) {
            players--;
        }
        if (playerO == null) {
            players--;
        }
        return players;
    }

    void sendQueueUpdates(int skipClients) {
        int players = getPlayerCount();
        for (int index = Server.clients.size() - 1; index >= skipClients; index--) {
            ServerClient client = Server.clients.get(index);
            if (client.state == ClientState.CONNECTED) {
                client.sendMessage(new byte[]{'Q', (byte) (index - players)});
            }
        }
    }

    void populateBoardBytes(byte[] byteArray, int offset) {
        for (int square = 0; square < 9; square++) {
            byteArray[offset + square] = (byte) board[square];
        }
    }

    void startGame() {
        sendQueueUpdates(2);

        for (int square = 0; square < 9; square++) {
            board[square] = ' ';
        }
        turn = 0;

        state = GameState.PLAYING;
        System.out.print("Game started!\n");

        ServerClient turnPlayer = getTurnPlayer();
        ServerClient otherPlayer = turnPlayer == playerX ? playerO : playerX;

        byte[] turnPlayerBytes = new byte[11];
        turnPlayerBytes[0] = (byte) 'x';
        populateBoardBytes(turnPlayerBytes, 1);
        turnPlayerBytes[10] = (byte) '1';
        turnPlayer.sendMessage(turnPlayerBytes);

        byte[] otherPlayerBytes = new byte[11];
        otherPlayerBytes[0] = (byte) 'o';
        populateBoardBytes(otherPlayerBytes, 1);
        otherPlayerBytes[10] = (byte) '0';
        otherPlayer.sendMessage(otherPlayerBytes);
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
            turnPlayer.sendMessage(new byte[]{'I'});
            return;
        }
        board[square] = turnPlayer == playerX ? 'X' : 'O';

        // TODO: determine if the move caused a win or a tie
        //          for efficiency, we can likely get away with only checking the
        //          changed square's row, column and diagonals instead of the whole board

        turn++;
        turnPlayer = getTurnPlayer();
        ServerClient otherPlayer = turnPlayer == playerX ? playerO : playerX;

        byte[] turnPlayerBytes = new byte[10];
        populateBoardBytes(turnPlayerBytes, 0);
        turnPlayerBytes[9] = (byte) '1';
        turnPlayer.sendMessage(turnPlayerBytes);

        byte[] otherPlayerBytes = new byte[10];
        populateBoardBytes(otherPlayerBytes, 0);
        otherPlayerBytes[9] = (byte) '0';
        otherPlayer.sendMessage(otherPlayerBytes);
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
        if (winner != null && winner.state == ClientState.CONNECTED) {
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

            winner.sendMessage(new byte[]{'W', (byte) streak});
            ServerClient loser = winner == playerX ? playerO : playerX;
            if (loser != null && loser.state == ClientState.CONNECTED) {
                loser.sendMessage(new byte[]{'L'});
            }
        } else {
            output += "it's a tie!";
            System.out.printf("%s\n", output);

            if (playerX != null && playerX.state == ClientState.CONNECTED) {
                playerX.sendMessage(new byte[]{'T'});
            }
            if (playerO != null && playerO.state == ClientState.CONNECTED) {
                playerO.sendMessage(new byte[]{'T'});
            }

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