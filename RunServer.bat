@echo off
javac TicTacToeServer.java || (pause >nul && exit)
java TicTacToeServer || (pause >nul && exit)
del /q *.class >nul 2>nul