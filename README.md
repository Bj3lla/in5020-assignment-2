# in5020-assignment-2

### Quick start

Navigate to the root folder (Assignment2), and run this to compile the files
```jsx
javac -d bin src/common/*.java src/bankserver/*.java src/bankserver/utils/*.java src/mdserver/*.java src/mdserver/utils/*.java 
```

then run this to connect to a port
```
java -cp bin mdserver.MDServer 1099
```

lastly, open a new terminal window, and run:
```
./start_replicas.sh
```

This will:
- Start 3 members with Rep1 at timestamp 0
- Start 1 new members with Rep2 at timestamp 5
- Start 1 new members with Rep3 at timestamp 15

The logs from all replicas will then be written to the logs/ directory. 

### Manual input
OR, to choose input-files manually run:
```
java -cp bin bankserver.BankServer localhost:1099 <account name> <number of replicas> <currency file> [batch file]
```
where you insert the account name, desired number of replicas, currency file, and input file like this:
```
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/Rep1.txt
```
### Our assumptions
For this assignment, we haver assumed that negative values for deposits should be rejected. The program therefore rejects negative currency arguments and logs the error as following:

Error processing command: deposit USD -100 -> Deposit amount must be positive.
