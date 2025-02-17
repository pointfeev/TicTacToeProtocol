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
        // TODO: figure out how to best output the board
        //       not all terminals support box drawing characters, may just need to use spaces only
        //       not all terminals may use monospace fonts either, need to figure that out
        /*if (yourTurn) {
            System.out.print("(1)│(2)│(3)\n");
        } else {
            System.out.print("   │   │   \n");
        }
        System.out.printf(" %c │ %c │ %c \n", board[0], board[1], board[2]);
        if (yourTurn) {
            System.out.print("(4)│(5)│(6)\n");
        } else {
            System.out.print("   │   │   \n");
        }
        System.out.print("   │   │   \n");
        System.out.print("───┼───┼───\n");
        System.out.print("   │   │   \n");
        System.out.printf(" %c │ %c │ %c \n", board[3], board[4], board[5]);
        if (yourTurn) {
            System.out.print("(7)│(8)│(9)\n");
        } else {
            System.out.print("   │   │   \n");
        }
        System.out.print("   │   │   \n");
        System.out.print("───┼───┼───\n");
        System.out.print("   │   │   \n");
        System.out.printf(" %c │ %c │ %c \n", board[6], board[7], board[8]);
        System.out.print("   │   │   \n");*/
        System.out.printf("%c%c%c\n", board[0], board[1], board[2]);
        System.out.printf("%c%c%c\n", board[3], board[4], board[5]);
        System.out.printf("%c%c%c\n", board[6], board[7], board[8]);
    }

    void nextTurn(boolean yourTurn) {
        state = GameState.PLAYING;

        if (role == ' ') {
            System.out.print("ERROR: Received board state before receiving role.\n");
            System.exit(-1);
        }

        if (yourTurn) {
            System.out.printf("It's your turn, you are %c.\n", role);
            Client.getInput("What square do you want to play (1-9)? ",
                    () -> Client.state == ClientState.CONNECTED && state == GameState.PLAYING, input -> {
                // TODO: figure out how to check if it's characters 1-9
                if (input <= 9) {
                    Client.sendMessage(new byte[]{input});
                    return true;
                }
                return false;
            });
        } else {
            System.out.printf("It is your opponent's turn, you are %c.\n", role);
        }
    }

    void gameWon(int streak) {
        state = GameState.WAITING_ON_WINNER;

        String output = "Game over, you win!";
        if (streak > 1) {
            output += " You have won " + streak + " games in a row!";
        }
        System.out.print(output + '\n');

        Client.getInput("Do you want to play again (Y/N)? ",
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

        // TODO: see if we need to handle lose or tie any further here
    }
}