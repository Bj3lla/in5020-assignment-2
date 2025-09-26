package bankserver;

import java.rmi.Naming;
import bankserver.utils.CommandProcessor;
import common.CurrencyConverter;

public class BankServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: BankServer <MDServer host> <account name> <#replicas> <currency file> [batch file]");
            return;
        }

        String mdServerHost = args[0]; // Only the host, e.g., "localhost"
        String accountName = args[1];
        int replicas = Integer.parseInt(args[2]);
        String currencyFile = args[3];
        String batchFile = args.length > 4 ? args[4] : null;

        // Initialize currency converter
        CurrencyConverter converter = new CurrencyConverter(currencyFile);

        // Create server name
        String serverName = accountName + "_Replica" + System.currentTimeMillis();

        // Instantiate BankServerImpl
        BankServerImpl bankServer = new BankServerImpl(serverName, converter, mdServerHost, replicas);

        // Bind to RMI registry
        Naming.rebind("rmi://localhost/" + serverName, bankServer);
        System.out.println("BankServer " + serverName + " is running and registered.");

        // Command processor: interactive or batch
        CommandProcessor processor = new CommandProcessor(bankServer);
        if (batchFile == null) processor.runInteractive();
        else processor.runBatch(batchFile);
    }
}
