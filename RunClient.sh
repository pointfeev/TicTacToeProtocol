#!/usr/bin/env bash
javac Client.java -d out/client
cd out/client
java Client "$@"