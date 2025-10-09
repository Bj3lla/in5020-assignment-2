package bankserver;

import java.util.UUID;

import bankserver.utils.CommandProcessor;
import common.CurrencyConverter;

public class BankServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: BankServer <MDServer host:port> <account name> <#replicas> <currency file> [batch file]");
            return;
        }

        String mdServerHostPort = args[0]; // e.g., "localhost:1099"
        String accountName = args[1];
        int replicas = Integer.parseInt(args[2]);
        String currencyFile = args[3];
        String batchFile = args.length > 4 ? args[4] : null;

        // Parse host and port from mdServerHostPort
        String[] hostPortParts = mdServerHostPort.split(":");
        if (hostPortParts.length != 2) {
            System.err.println("Error: MDServer host:port must be in format host:port (e.g., localhost:1099)");
            return;
        }
        String mdServerHost = hostPortParts[0];
        String mdServerPort = hostPortParts[1];

        // Initialize currency converter
        CurrencyConverter converter = new CurrencyConverter(currencyFile);

        // Create a unique instance name for this replica, e.g., "group01_123e4567-e89b-12d3-a456-426614174000"
        String instanceName = accountName + "_" + UUID.randomUUID().toString();

        // Instantiate BankServerImpl with its unique name
        // We also pass a flag to choose the getSyncedBalance implementation ("correct" or "naive")
        BankServerImpl bankServer = new BankServerImpl(instanceName, accountName, converter, mdServerHostPort, replicas, "correct");
        System.out.println("BankServer instance " + instanceName + " started for account: " + accountName);

        // Bind to RMI registry using its unique name
        java.rmi.Naming.rebind("rmi://" + mdServerHost + ":" + mdServerPort + "/" + instanceName, bankServer);
        System.out.println("BankServer " + instanceName + " is running and registered.");

        // ... (awaitInitialSync and CommandProcessor logic remains the same) ...
        bankServer.awaitInitialSync();
        
        CommandProcessor processor = new CommandProcessor(bankServer, instanceName);
        if (batchFile == null) {
            processor.runInteractive();
        } else {
            processor.runBatch(batchFile);
        }
    }
}
