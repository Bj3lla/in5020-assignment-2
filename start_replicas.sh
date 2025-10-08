#!/bin/bash

# Ensure the logs directory exists
mkdir -p logs

# Start 3 replicas with Rep1 immediately
echo "Starting 3 replicas with Rep1..."
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/Rep1.txt > logs/Rep1_1.log 2>&1 &
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/Rep1.txt > logs/Rep1_2.log 2>&1 &
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/Rep1.txt > logs/Rep1_3.log 2>&1 &

# Wait for 5 seconds
sleep 5

# Start 1 replica with Rep2
echo "Starting 1 replica with Rep2..."
java -cp bin bankserver.BankServer localhost:1099 group02 1 input/TradingRate.txt input/Rep2.txt > logs/Rep2.log 2>&1 &

# Wait for another 10 seconds (total 15 seconds)
sleep 10

# Start 1 replica with Rep3
echo "Starting 1 replica with Rep3..."
java -cp bin bankserver.BankServer localhost:1099 group03 1 input/TradingRate.txt input/Rep3.txt > logs/Rep3.log 2>&1 &

echo "All replicas started. Logs are being written to the logs/ directory."