import java.util.concurrent.Callable;
import java.util.function.Function;

class ClientGame {
    GameState state = GameState.INITIALIZING;
    char role = ' ';

    /**
     * Updates the client/game state and outputs to the client that they're waiting for another player.
     */
    void waitingForOpponent() {
        state = GameState.WAITING_FOR_PLAYERS;

        System.out.print("Waiting for another player...\n");
    }

    /**
     * Updates the client/game state and outputs to the client their position in the queue.
     */
    void inQueue(int ahead) {
        state = GameState.WAITING_FOR_PLAYERS;

        String output = "You are in a queue to play. ";
        if (ahead == 0) {
            output += "You are next in line.";
        } else {
            output += "There " + (ahead == 1 ? "is 1 client" : "are " + ahead + " clients") + " ahead of you.";
        }
        System.out.print(output + '\n');
    }

    /**
     * Updates the client/game state and the client's role, and outputs to the client that the game is starting.
     */
    void gameStarting(char role) {
        state = GameState.INITIALIZING;

        this.role = role;
        System.out.printf("Game starting, you will be %c...\n", role);
    }

    /**
     * Outputs the current state of the board to the client.
     */
    void boardStateChanged(char[] board) {
        System.out.printf("%c %c %c\n", board[0] == ' ' ? '_' : board[0], board[1] == ' ' ? '_' : board[1],
                board[2] == ' ' ? '_' : board[2]);
        System.out.printf("%c %c %c\n", board[3] == ' ' ? '_' : board[3], board[4] == ' ' ? '_' : board[4],
                board[5] == ' ' ? '_' : board[5]);
        System.out.printf("%c %c %c\n", board[6] == ' ' ? '_' : board[6], board[7] == ' ' ? '_' : board[7],
                board[8] == ' ' ? '_' : board[8]);
    }

    /**
     * Updates the client/game state and outputs to the player whose turn it is and what role they play.
     * <p>
     * If it is the current client's turn (indicated by {@code yourTurn}), waits for input on which square they want
     * to play; see {@link Client#readInput(String, Callable, Function)}.
     *
     * @param yourTurn Whether it is the current client's turn to play.
     */
    void nextTurn(boolean yourTurn) {
        state = GameState.PLAYING;

        if (role == ' ') {
            System.out.print("ERROR: Received next turn byte before game starting byte.\n");
            System.exit(-1);
        }

        if (yourTurn) {
            System.out.printf("It's your turn, you are %c.\n", role);
            Client.readInput("What square do you want to play (1-9)? ",
                    () -> Client.state == ClientState.CONNECTED && state == GameState.PLAYING, inputByte -> {
                if (inputByte >= 49 && inputByte <= 57) { // decimal values for ASCII number characters 1-9
                    Client.sendMessage(new byte[]{(byte) (inputByte - 48)}); // subtract 48 to convert to integer
                    return true;
                }
                return false;
            });
        } else {
            System.out.printf("It is your opponent's turn, you are %c.\n", role);
        }
    }

    /**
     * Outputs to the player that their last move was invalid and calls {@link #nextTurn(boolean)}.
     */
    void invalidMove() {
        System.out.print("Invalid move!\n");
        nextTurn(true);
    }

    /**
     * Updates the client/game state and outputs to the player that they won.
     * <p>
     * Waits for input from the winner on whether they want to play again; see
     * {@link Client#readInput(String, Callable, Function)}.
     *
     * @param streak The current client's win streak.
     */
    void gameWon(int streak) {
        state = GameState.WAITING_ON_WINNER;

        role = ' ';

        String output = "Game over, you win!";
        if (streak > 1) {
            output += " You have won " + streak + " games in a row!";
        }
        System.out.print(output + '\n');

        Client.readInput("Do you want to play again (Y/N)? ",
                () -> Client.state == ClientState.CONNECTED && state == GameState.WAITING_ON_WINNER, inputByte -> {
            char choice = Character.toUpperCase((char) inputByte.byteValue());
            if (choice == 'Y' || choice == 'N') {
                Client.sendMessage(new byte[]{(byte) choice});
                return true;
            }
            return false;
        });
    }

    /**
     * Updates the client/game state and outputs to the player that they lost or tied.
     *
     * @param tie Whether the loss was from a tie.
     */
    void gameLost(boolean tie) {
        state = GameState.WAITING_FOR_PLAYERS;

        role = ' ';

        String output = "Game over, ";
        if (tie) {
            output += "it's a tie!";
        } else {
            output += "you lose!";
        }
        System.out.print(output + '\n');
    }
}