# in5020-assignment-2

### Quick start

Navigate to the root folder (Assignment2), and run this to compile the files
```jsx
javac -d bin src/common/*.java src/bankserver/*.java src/bankserver/utils/*.java src/mdserver/*.java src/mdserver/utils/*.java 
```

Then run this to connect to a port, and start the MDServer
```jsx
cd bin
rmiregistry 1099 &
cd ..
java -cp bin mdserver.MDServer 1099
```

lastly, open a new terminal window, and run:
./start_replicas.sh

This will:
- Start 3 members with Rep1 at timestamp 0
- Start 1 new members with Rep2 at timestamp 5
- Start 1 new members with Rep3 at timestamp 15

The logs from all replicas will then be written to the logs/ directory. 
