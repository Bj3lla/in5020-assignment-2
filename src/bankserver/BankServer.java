// Server Main: the main class creates an instance of BankServerImpl and binds it to the RMI registry.
package bankserver;

import java.rmi.Naming;
import bankserver.utils.CommandProcessor;
import common.CurrencyConverter;

public class BankServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: BankServer <MDServer address> <account name> <#replicas> <currency file> [batch file]");
            return;
        }

        String mdServerAddr = args[0];
        String accountName = args[1];
        int replicas = Integer.parseInt(args[2]);
        String currencyFile = args[3];
        String batchFile = args.length > 4 ? args[4] : null;

        // ✅ Instantiate CurrencyConverter once, pass it as dependency
        CurrencyConverter converter = new CurrencyConverter(currencyFile);

        String serverName = accountName + "_Replica" + System.currentTimeMillis();
        BankServerImpl bankServer = new BankServerImpl(serverName, converter, mdServerAddr, replicas);

        Naming.rebind("rmi://localhost/" + serverName, bankServer);
        System.out.println("BankServer " + serverName + " is running and registered.");

        CommandProcessor processor = new CommandProcessor(bankServer);
        if (batchFile == null) {
            processor.runInteractive();
        } else {
            processor.runBatch(batchFile);
        }
    }
}
