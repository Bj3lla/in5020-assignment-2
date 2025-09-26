# in5020-assignment-2

### Quick start
Navigate to the root folder named Assignment2
Run this to compile:
javac -d bin src/common/*.java src/bankserver/*.java src/bankserver/utils/*.java src/mdserver/*.java src/mdserver/utils/*.java 

then this to connect to a port:
java -cp bin mdserver.MDServer 1099

lastly, open a new terminal window, and run:
java -cp bin bankserver.BankServer localhost:1099 group01 3 input/TradingRate.txt input/ExampleInputFile.txt
