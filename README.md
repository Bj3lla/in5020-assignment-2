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

Lastly, open a new terminal window, navigate to the root folder (Assignment2), and run:
```jsx
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/ExampleInputFile.txt
```
