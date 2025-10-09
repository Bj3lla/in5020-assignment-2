# in5020-assignment-2

### Quick start

Navigate to the root folder (Assignment2), and run this to compile the files
```jsx
javac -d bin src/common/*.java src/bankserver/*.java src/bankserver/utils/*.java src/mdserver/*.java src/mdserver/utils/*.java 
```

Now start the RMI Registry:

```
cd bin && rmiregistry 1099
```

Alternatively, run this command to kill the current process listening on port 1099 and then start the RMI Registry:
```
sudo lsof -t -i:1099 | xargs kill -9 2>/dev/null; cd bin && rmiregistry 1099
```

then in a second terminal run this to connect to a port
```
java -cp bin mdserver.MDServer 1099
```

lastly, open a third terminal window, and run the script to start the banking server replicas:
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

### Difference between getSyncedBalance and getQuickBalance
getSyncedBalance - waits for all outstanding transactions to be executed before determining balance.
getQuickBalance - does not wait, instantly returns the current balance from the caller replica, not including any outstanding transactions that will shortly after affect the value of the balance.

### Workload separating

We worked agile on the assignment, creating a common GitHub repo where we all listed issues and to-dos and everyone picked issues to code. We had success doing this despite some merge conflicts here and there.

Before submitting the assignment, we coded together at IFI to fix the final bugs regarding synchronization of balance.

### Important: Check the stdout logs before checking the logfiles
Upon being disconnected, a replica will still produce logfiles, but these logfiles will be faulty in amount. This is due to the replica being removed from the MDS upon not answering within the expected response timeframe. Upon being removed from the MDS, no broadcasts from or to the replica will get through. 