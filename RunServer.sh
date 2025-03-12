#!/usr/bin/env bash
javac Server.java -d out/server
cd out/server
java Server "$@"