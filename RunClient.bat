@echo off
javac TicTacToeClient.java || (pause >nul && exit)
java TicTacToeClient || (pause >nul && exit)
del /q *.class >nul 2>nul