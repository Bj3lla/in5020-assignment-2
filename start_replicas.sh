#!/bin/bash

# Ensure the logs directory exists
mkdir -p logs

# All replicas will join 'group01' and expect 3 initial members.
ACCOUNT_NAME="group01"
START_REPLICAS=3

# Start 3 replicas immediately
echo "Starting 3 replicas for $ACCOUNT_NAME..."
java -cp bin bankserver.BankServer localhost:1099 $ACCOUNT_NAME $START_REPLICAS input/TradingRate.txt input/Rep1.txt > logs/Rep1_1.log 2>&1 &
java -cp bin bankserver.BankServer localhost:1099 $ACCOUNT_NAME $START_REPLICAS input/TradingRate.txt input/Rep1.txt > logs/Rep1_2.log 2>&1 &
java -cp bin bankserver.BankServer localhost:1099 $ACCOUNT_NAME $START_REPLICAS input/TradingRate.txt input/Rep1.txt > logs/Rep1_3.log 2>&1 &

# Wait for 5 seconds
sleep 5

# Start a 4th replica that joins the SAME group
echo "Starting 1 new replica for $ACCOUNT_NAME..."
java -cp bin bankserver.BankServer localhost:1099 $ACCOUNT_NAME $START_REPLICAS input/TradingRate.txt input/Rep2.txt > logs/Rep2.log 2>&1 &

# Wait for another 10 seconds
sleep 10

# Start a 5th replica that joins the SAME group
echo "Starting 1 new replica for $ACCOUNT_NAME..."
java -cp bin bankserver.BankServer localhost:1099 $ACCOUNT_NAME $START_REPLICAS input/TradingRate.txt input/Rep3.txt > logs/Rep3.log 2>&1 &

echo "All replicas started for group $ACCOUNT_NAME. Logs are in the logs/ directory."
