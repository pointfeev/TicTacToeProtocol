import java.util.Random;

class ServerGame {
    GameState state = GameState.INITIALIZING;

    char[] board = new char[9];

    int turn = 0;
    ServerClient playerX = null;
    ServerClient playerO = null;

    ServerClient lastWinner = null;
    int streak = 0;

    Random random = new Random();

    /**
     * Sets the random number generator seed to the current time; see {@link Random#setSeed(long)} and
     * {@link System#currentTimeMillis()}.
     * <p>
     * Begins waiting for players; see {@link #waitForPlayers()}.
     */
    ServerGame() {
        random.setSeed(System.currentTimeMillis());
        waitForPlayers();
    }

    /**
     * Starts a new thread that looks for players, and starts the game once it finds some; see {@link #findPlayers()}
     * and {@link #startGame()}.
     */
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

    /**
     * Grabs the first two clients from the {@link Server#clients} array (queue). The two clients will only be used
     * if/once they are fully connected.
     * <p>
     * If there is only one client, sends the "waiting for another player" message to that client ('w'); this message
     * is only sent to the client once.
     * <p>
     * Uses {@link Random#nextBoolean()} to determine which player will be X.
     *
     * @return Boolean indicating whether two players were found.
     */
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

    /**
     * @return The number of clients currently playing the game.
     */
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

    /**
     * Sends the position in the queue to every fully connected client, skipping (from the beginning of the array)
     * the number of clients defined by {@code skipClients}.
     *
     * @param skipClients Number of clients (from the beginning of the array) to skip sending the message to.
     */
    void sendQueueUpdates(int skipClients) {
        int players = getPlayerCount();
        for (int index = Server.clients.size() - 1; index >= skipClients; index--) {
            ServerClient client = Server.clients.get(index);
            if (client.state == ClientState.CONNECTED) {
                client.sendMessage(new byte[]{'Q', (byte) (index - players)});
            }
        }
    }

    /**
     * Populates the passed {@code byteArray} array with the board character bytes from {@link #board}.
     *
     * @param byteArray Byte array to populate.
     * @param offset    Integer to add to the byte array indices.
     */
    void populateBoardBytes(byte[] byteArray, int offset) {
        for (int square = 0; square < 9; square++) {
            byteArray[offset + square] = (byte) board[square];
        }
    }

    /**
     * @return The current player whose turn it is based on {@link #turn}.
     */
    ServerClient getTurnPlayer() {
        return turn % 2 == 0 ? playerX : playerO;
    }

    /**
     * Starts the game.
     * <p>
     * Sends queue updates to all clients except for the current players; see {@link #sendQueueUpdates(int)}.
     * <p>
     * Sends "game starting", board state and "indicate who plays next" messages to the current players.
     */
    void startGame() {
        sendQueueUpdates(2);

        for (int square = 0; square < 9; square++) {
            board[square] = ' ';
        }
        turn = 0;

        state = GameState.PLAYING;
        System.out.printf("Game started with %s and %s!\n", playerX, playerO);

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

    /**
     * Checks the passed row (which can be a row, column, or a diagonal, determined by {@code startSquare}, {@code
     * rowStep} and {@code columnStep}) for a win for the passed {@code role}.
     *
     * @param role        The role to check for the win.
     * @param startSquare The square to begin iteration on.
     * @param rowStep     The amount to increment the row after each iteration.
     * @param columnStep  The amount to increment the column after each iteration.
     * @return Boolean indicating whether the passed {@code role} won on the passed row.
     */
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

    /**
     * Checks the rows, columns and diagonals of the passed {@code square} for a win for the passed {@code role}.
     *
     * @param role   The role to check for the win.
     * @param square The square to check the rows, columns and diagonals of.
     * @return Boolean indicating whether the passed {@code role} won.
     */
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

    /**
     * Checks if all board squares are filled, indicating a tie.
     *
     * @return Boolean indicating whether the board is completely full, indicating a tie.
     */
    boolean checkTie() {
        int filled = 0;
        for (int square = 0; square < 9; square++) {
            if (board[square] != ' ') {
                filled++;
            }
        }
        return filled == 9;
    }

    /**
     * Attempts to play a turn.
     * <p>
     * If the move is invalid, sends the "incorrect move" message to the client.
     * <p>
     * Otherwise, makes the move and checks for a win and a tie; see {@link #checkWin(char, int)} and
     * {@link #checkTie()}.
     * <p>
     * If the player won, calls {@link #endGame(ServerClient)}.
     * <p>
     * Otherwise, sends board state and "indicate who plays next" messages to the current players.
     *
     * @param player The client claiming to be a player and attempting to play a turn.
     * @param square The square the player wants to play their turn on.
     */
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

        System.out.printf("%s played square %d.\n", player, square + 1);

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

    /**
     * Ends the game.
     * <p>
     * If a {@code winner} was passed, increments their win streak, and sends them and the loser the win and loss
     * messages. The loser will be sent to the end of the {@link Server#clients} array (queue), and the server will
     * then wait for the passed {@code winner} to respond whether they want to play again.
     * <p>
     * If no {@code winner} was passed ({@code null}), sends the tie messages to both current players, moves them
     * both to the end of the {@link Server#clients} array (queue) and begins waiting for players again; see
     * {@link #waitForPlayers()}.
     *
     * @param winner The player who won, or {@code null} if it was a tie.
     */
    void endGame(ServerClient winner) {
        if (state != GameState.PLAYING) {
            return;
        }

        if (winner != lastWinner) {
            streak = 0;
        }
        lastWinner = winner;

        if (winner != null && winner.state == ClientState.CONNECTED) {
            System.out.printf("Game over, %s wins!\n", winner);
            if (++streak > 1) {
                System.out.printf("%s has won %d games in a row!\n", winner, streak);
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
            System.out.printf("Waiting on %s to respond...\n", winner);

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

    /**
     * Restarts the game.
     * <p>
     * If the winner ({@code player}) chooses not to play again, disconnects them from the server.
     * <p>
     * Begins waiting for players again; see {@link #waitForPlayers()}.
     *
     * @param player           The client claiming to be the winner and attempting to restart the game.
     * @param winnerPlaysAgain Whether the winner ({@code player}) wants to play again.
     */
    void restartGame(ServerClient player, boolean winnerPlaysAgain) {
        if (state != GameState.WAITING_ON_WINNER) {
            return;
        }

        if (player != lastWinner) {
            return;
        }

        if (!winnerPlaysAgain) {
            if (player.state == ClientState.CONNECTED) {
                System.out.printf("%s will not play again.\n", player);
                player.disconnect();
            }
        } else {
            System.out.printf("%s will play again!\n", player);
        }

        waitForPlayers();
    }
}