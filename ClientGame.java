class ClientGame {
    GameState state = GameState.INITIALIZING;
    char role = ' ';

    void waitingForOpponent() {
        state = GameState.WAITING_FOR_PLAYERS;

        System.out.print("Waiting for another player...\n");
    }

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

    void gameStarting(char role) {
        state = GameState.INITIALIZING;

        this.role = role;
        System.out.printf("Game starting, you will be %c...\n", role);
    }

    void boardStateChanged(char[] board) {
        System.out.printf("%c %c %c\n", board[0] == ' ' ? '1' : board[0], board[1] == ' ' ? '2' : board[1],
                board[2] == ' ' ? '3' : board[2]);
        System.out.printf("%c %c %c\n", board[3] == ' ' ? '4' : board[3], board[4] == ' ' ? '5' : board[4],
                board[5] == ' ' ? '6' : board[5]);
        System.out.printf("%c %c %c\n", board[6] == ' ' ? '7' : board[6], board[7] == ' ' ? '8' : board[7],
                board[8] == ' ' ? '9' : board[8]);
    }

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

    void invalidMove() {
        System.out.print("Invalid move!\n");
        nextTurn(true);
    }

    void gameWon(int streak) {
        state = GameState.WAITING_ON_WINNER;

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

    void gameLost(boolean tie) {
        state = GameState.WAITING_FOR_PLAYERS;

        String output = "Game over, ";
        if (tie) {
            output += "it's a tie!";
        } else {
            output += "you lose!";
        }
        System.out.print(output + '\n');
    }
}