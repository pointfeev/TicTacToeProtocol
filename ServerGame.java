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

    ServerClient getTurnPlayer() {
        return turn % 2 == 0 ? playerX : playerO;
    }

    void startGame() {
        sendQueueUpdates(2);

        for (int square = 0; square < 9; square++) {
            board[square] = ' ';
        }
        turn = 0;

        state = GameState.PLAYING;
        System.out.printf("Game started with %s and %s!\n", playerX.getIdentifier(), playerO.getIdentifier());

        ServerClient turnPlayer = getTurnPlayer();
        ServerClient otherPlayer = turnPlayer == playerX ? playerO : playerX;

        byte[] turnPlayerBytes = new byte[11];
        turnPlayerBytes[0] = 'x';
        populateBoardBytes(turnPlayerBytes, 1);
        turnPlayerBytes[10] = 1;
        turnPlayer.sendMessage(turnPlayerBytes);

        byte[] otherPlayerBytes = new byte[11];
        otherPlayerBytes[0] = 'o';
        populateBoardBytes(otherPlayerBytes, 1);
        otherPlayerBytes[10] = 0;
        otherPlayer.sendMessage(otherPlayerBytes);
    }

    boolean checkRow(char role, int startSquare, int rowStep, int columnStep) {
        int filled = 0;
        int row = startSquare / 3;
        int column = startSquare % 3;
        do {
            int square = row * 3 + column;
            if (board[square] == role) {
                filled++;
            } else {
                break;
            }
            row += rowStep;
            column += columnStep;
        } while (row < 3 && column < 3);
        return filled == 3;
    }

    boolean checkWin(char role, int square) {
        int row = square / 3;
        int column = square % 3;
        if (checkRow(role, row * 3, 0, 1) // check row
                || checkRow(role, column, 1, 0)) { // check column
            return true;
        }
        if (square % 2 == 1) { // no need to check diagonals
            return false;
        }
        return checkRow(role, 0, 1, 1) // check negative diagonal
                || checkRow(role, 2, 1, -1); // check positive diagonal
    }

    boolean checkTie() {
        int filled = 0;
        for (int square = 0; square < 9; square++) {
            if (board[square] != ' ') {
                filled++;
            }
        }
        return filled == 9;
    }

    void playTurn(ServerClient player, int square) {
        if (state != GameState.PLAYING) {
            return;
        }

        ServerClient turnPlayer = getTurnPlayer();
        if (turnPlayer == null || turnPlayer.state != ClientState.CONNECTED || turnPlayer != player) {
            return;
        }
        ServerClient otherPlayer = turnPlayer == playerX ? playerO : playerX;
        if (otherPlayer == null || otherPlayer.state != ClientState.CONNECTED) {
            return;
        }

        if (board[square] != ' ') {
            turnPlayer.sendMessage(new byte[]{'I'});
            return;
        }
        char role = turnPlayer == playerX ? 'X' : 'O';
        board[square] = role;
        turn++;

        System.out.printf("%s played square %d.\n", player.getIdentifier(), square + 1);

        boolean win = checkWin(role, square);
        if (win || checkTie()) {
            byte[] boardBytes = new byte[9];
            populateBoardBytes(boardBytes, 0);
            turnPlayer.sendMessage(boardBytes);
            otherPlayer.sendMessage(boardBytes);

            endGame(win ? turnPlayer : null);
            return;
        }

        byte[] turnPlayerBytes = new byte[10];
        populateBoardBytes(turnPlayerBytes, 0);
        turnPlayerBytes[9] = 0;
        turnPlayer.sendMessage(turnPlayerBytes);

        byte[] otherPlayerBytes = new byte[10];
        populateBoardBytes(otherPlayerBytes, 0);
        otherPlayerBytes[9] = 1;
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

        if (winner != null && winner.state == ClientState.CONNECTED) {
            System.out.printf("Game over, %s wins!\n", winner.getIdentifier());
            if (++streak > 1) {
                System.out.printf("%s has won %d games in a row!\n", winner.getIdentifier(), streak);
            }

            if (winner == playerX) {
                if (Server.clients.remove(playerO)) {
                    Server.clients.add(playerO);
                }
            } else {
                if (Server.clients.remove(playerX)) {
                    Server.clients.add(playerX);
                }
            }

            state = GameState.WAITING_ON_WINNER;
            System.out.printf("Waiting on %s to respond...\n", winner.getIdentifier());

            winner.sendMessage(new byte[]{'W', (byte) streak});
            ServerClient loser = winner == playerX ? playerO : playerX;
            if (loser != null && loser.state == ClientState.CONNECTED) {
                loser.sendMessage(new byte[]{'L'});
            }
        } else {
            System.out.print("Game over, it's a tie!\n");

            if (playerX != null && playerX.state == ClientState.CONNECTED) {
                if (Server.clients.remove(playerX)) {
                    Server.clients.add(playerX);
                }
                playerX.sendMessage(new byte[]{'T'});
            }
            if (playerO != null && playerO.state == ClientState.CONNECTED) {
                if (Server.clients.remove(playerO)) {
                    Server.clients.add(playerO);
                }
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
                System.out.printf("%s will not play again.\n", player.getIdentifier());
            }
        } else {
            System.out.printf("%s will play again!\n", player.getIdentifier());
        }

        waitForPlayers();
    }
}