package bankserver.utils;

import bankserver.BankServerInterface;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CommandProcessor {
    private final BankServerInterface bankServer;

    // Keeps track of last transaction IDs
    private final Map<String, String> lastTxIds = new HashMap<>();

    // Logging
    private final PrintWriter logWriter;

    public CommandProcessor(BankServerInterface bankServer, String serverName) throws IOException {
        this.bankServer = bankServer;

        // Ensure logs directory exists
        File logDir = new File("logs");
        if (!logDir.exists()) logDir.mkdirs();

        // Each replica writes to its own log
        this.logWriter = new PrintWriter(new FileWriter("logs/" + serverName + ".log", true), true);
        log("=== Replica " + serverName + " started ===");
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
                // Random delay between 0.5s–1.5s
                double delay = 0.5 + Math.random();
                Thread.sleep((long)(delay * 1000));
            }
        }
    }

    // Command dispatcher
    private boolean processCommand(String commandLine) {
        if (commandLine.isEmpty()) return true;
        String[] parts = commandLine.split("\\s+");
        String cmd = parts[0];

        try {
            switch (cmd) {
                case "memberInfo" -> {
                    bankServer.printMembers();
                    log("memberInfo executed");
                }

                case "getQuickBalance" -> {
                    double bal = bankServer.getQuickBalance(parts[1]);
                    log("Quick balance for " + parts[1] + ": " + bal);
                }

                case "getSyncedBalance" -> {
                    String txId = bankServer.getSyncedBalance(parts[1]);
                    lastTxIds.put("getSyncedBalance", txId);
                    log("Synced balance requested for " + parts[1] + " (txId=" + txId + ")");
                }

                case "deposit" -> {
                    String txId = bankServer.deposit(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                    lastTxIds.put("deposit", txId);
                    log("Deposit " + parts[2] + " " + parts[1] + " (txId=" + txId + ")");
                }

                case "addInterest" -> {
                    String txId;
                    if (parts.length == 2) {
                        txId = bankServer.addInterestAll(Double.parseDouble(parts[1]));
                        lastTxIds.put("addInterestAll", txId);
                        log("AddInterestAll " + parts[1] + "% (txId=" + txId + ")");
                    } else {
                        txId = bankServer.addInterest(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                        lastTxIds.put("addInterest", txId);
                        log("AddInterest " + parts[2] + "% to " + parts[1] + " (txId=" + txId + ")");
                    }
                }

                case "getHistory" -> {
                    bankServer.getHistory();
                    log("History requested");
                }

                case "cleanHistory" -> {
                    bankServer.cleanHistory();
                    log("History cleaned");
                }

                case "checkTxStatus" -> {
                    String arg = parts[1];
                    String txId = null;

                    if (arg.startsWith("<add")) {
                        if (arg.contains("addInterest")) {
                            txId = lastTxIds.getOrDefault("addInterestAll", lastTxIds.get("addInterest"));
                        } else {
                            txId = lastTxIds.get("deposit");
                        }
                        if (txId != null) {
                            bankServer.checkTxStatus(txId);
                            log("checkTxStatus resolved placeholder to " + txId);
                        } else {
                            log("ERROR: Could not resolve placeholder " + arg);
                        }
                    } else {
                        bankServer.checkTxStatus(arg);
                        log("checkTxStatus " + arg);
                    }
                }

                case "sleep" -> {
                    double seconds = Double.parseDouble(parts[1]);
                    Thread.sleep((long) (seconds * 1000));
                    log("Slept " + seconds + "s");
                }

                case "exit" -> {
                    log("Exiting.");
                    logWriter.close();
                    System.exit(0);
                    return false;
                }

                default -> log("Unknown command: " + cmd);
            }
        } catch (Exception e) {
            String error = "Error processing command: " + commandLine + " -> " + e.getMessage();
            System.err.println(error);
            e.printStackTrace();
            log(error);
        }
        return true;
    }

    // Logging helper
    private void log(String msg) {
        System.out.println(msg);
        logWriter.println("[" + System.currentTimeMillis() + "] " + msg);
    }
}
