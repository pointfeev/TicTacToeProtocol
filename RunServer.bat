@echo off && javac Server.java -d out && cd out && java Server %* || pause >nul