package bankserver.utils;

import bankserver.BankServerInterface; // <-- use interface, not Impl
import java.io.*;
import java.util.Scanner;

public class CommandProcessor {
    private final BankServerInterface bankServer;

    public CommandProcessor(BankServerInterface bankServer) {
        this.bankServer = bankServer;
    }

    // Interactive mode
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            if (!processCommand(line)) break;
        }
    }

    // Batch mode
    public void runBatch(String filename) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!processCommand(line.trim())) break;
                // random delay between 0.5s–1.5s
                double delay = 0.5 + Math.random();
                Thread.sleep((long)(delay * 1000));
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
                case "getQuickBalance" -> bankServer.getQuickBalance(parts[1]);
                case "getSyncedBalance" -> bankServer.getSyncedBalance(parts[1]);
                case "deposit" -> bankServer.deposit(parts[1], Double.parseDouble(parts[2]));
                case "addInterest" -> {
                    if (parts.length == 2) bankServer.addInterestAll(Double.parseDouble(parts[1]));
                    else bankServer.addInterest(parts[1], Double.parseDouble(parts[2]));
                }
                case "getHistory" -> bankServer.getHistory();
                case "cleanHistory" -> bankServer.cleanHistory();
                case "checkTxStatus" -> bankServer.checkTxStatus(parts[1]);
                case "sleep" -> Thread.sleep(Long.parseLong(parts[1]) * 1000);
                case "exit" -> {
                    return false;
                }
                default -> System.out.println("Unknown command: " + cmd);
            }
        } catch (Exception e) {
            System.err.println("Error processing command: " + commandLine + " -> " + e.getMessage());
        }
        return true;
    }
}
