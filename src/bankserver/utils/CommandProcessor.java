package bankserver.utils;

import java.io.*;
import java.util.Scanner;
import bankserver.BankServerImpl;

public class CommandProcessor {
    private final BankServerImpl bankServer;

    public CommandProcessor(BankServerImpl bankServer) {
        this.bankServer = bankServer;
    }

    // Interactive mode
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (!processCommand(line)) break;
        }
    }

    // Batch mode
    public void runBatch(String filename) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!processCommand(line)) break;

                // Random delay between 0.5s–1.5s
                double delay = 0.5 + Math.random();
                Thread.sleep((long) (delay * 1000));
            }
        }
    }

    private boolean processCommand(String commandLine) {
        if (commandLine.isEmpty()) return true;
        String[] parts = commandLine.split("\\s+");
        String cmd = parts[0];

        try {
            switch (cmd) {
                case "memberInfo" -> bankServer.printMembers();
                case "getQuickBalance" -> System.out.println("Balance: " + bankServer.getQuickBalance(parts[1]));
                case "getSyncedBalance" -> bankServer.getSyncedBalance(parts[1]);
                case "deposit" -> bankServer.deposit(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                case "addInterest" -> {
                    if (parts.length == 2) bankServer.addInterestAll(Double.parseDouble(parts[1]));
                    else bankServer.addInterest(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                }
                case "getHistory" -> bankServer.getHistory();
                case "cleanHistory" -> bankServer.cleanHistory();
                case "checkTxStatus" -> bankServer.checkTxStatus(parts[1]);
                case "sleep" -> Thread.sleep(Long.parseLong(parts[1]) * 1000);
                case "exit" -> {
                    System.out.println("Exiting...");
                    return false;
                }
                default -> System.out.println("Unknown command: " + cmd);
            }
        } catch (Exception e) {
            System.err.println("Error processing command '" + commandLine + "': " + e.getMessage());
        }
        return true;
    }
}
