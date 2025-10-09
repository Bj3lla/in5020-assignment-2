package bankserver;

import bankserver.utils.CommandProcessor;
import common.CurrencyConverter;

public class BankServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: BankServer <MDServer host:port> <account name> <#replicas> <currency file> [batch file]");
            return;
        }

        String mdServerHostPort = args[0]; // e.g., "localhost:1099"
        String accountName = args[1]; // e.g., group1
        int replicas = Integer.parseInt(args[2]); // e.g., 3
        String currencyFile = args[3]; // e.g., "TradingRate.txt"
        String batchFile = args.length > 4 ? args[4] : null; // e.g., input/Rep1.txt

        // Initialize currency converter
        CurrencyConverter converter = new CurrencyConverter(currencyFile);

        // Create server name
        String serverName = accountName;

        // Instantiate BankServerImpl
        BankServerImpl bankServer = new BankServerImpl(mdServerHostPort, converter, mdServerHostPort, replicas);
        System.out.println("BankServer started for account: " + accountName);

        // Bind to RMI registry
        java.rmi.Naming.rebind("rmi://localhost/" + serverName, bankServer);
        System.out.println("BankServer " + serverName + " is running and registered.");

        // Command processor: interactive or batch
        CommandProcessor processor = new CommandProcessor(bankServer, serverName);
        if (batchFile == null) {
            processor.runInteractive();
        }
        else {
        processor.runBatch(batchFile);
        }
    }
}
