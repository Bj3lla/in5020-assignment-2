package bankserver.utils;

import bankserver.BankServerInterface;
import java.io.*;

import java.util.Scanner;

public class CommandProcessor {
    private final BankServerInterface bankServer;

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
        try {
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine();
                if (!processCommand(line)) break;
            }
        } catch (Exception e) {
            String error = "Error in interactive mode: " + e.getMessage();
            System.err.println(error);
            e.printStackTrace();
            log(error);
        }finally{
            scanner.close();
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
                    if (parts.length < 2) {
                        log("ERROR: Missing currency for getQuickBalance, defaulting to USD");
                        bankServer.getQuickBalance("USD");
                    } else {
                        double bal = bankServer.getQuickBalance(parts[1]);
                        log("Quick balance for " + parts[1] + ": " + bal);
                    }
                }

                case "getSyncedBalance" -> {
                    if (parts.length < 2) {
                        log("ERROR: Missing currency for getSyncedBalance, defaulting to USD");
                        bankServer.getSyncedBalance("USD");
                    } else {
                        bankServer.getSyncedBalance(parts[1]);
                    }
                }

                case "deposit" -> {
                    if (parts.length < 3) {
                        log("ERROR: Missing arguments for deposit");
                    } else {
                        try {
                            String txId = bankServer.deposit(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                            log("Deposit " + parts[2] + " " + parts[1] + " (txId=" + txId + ")");
                        } catch (IllegalArgumentException e) {
                            log("Error processing command: deposit " + parts[1] + " " + parts[2] + " -> " + e.getMessage());
                        }
                    }
                }

                case "addInterest" -> {
                    if (parts.length < 2) {
                        log("ERROR: Missing arguments for addInterest");
                    } else {
                        String txId;
                        if (parts.length == 2) {
                            txId = bankServer.addInterest( null,Double.parseDouble(parts[1]));
                            log("AddInterestAll " + parts[1] + "% (txId=" + txId + ")");
                        } else {
                            txId = bankServer.addInterest(parts[1].toUpperCase(), Double.parseDouble(parts[2]));
                            log("AddInterest " + parts[2] + "% to " + parts[1] + " (txId=" + txId + ")");
                        }
                    }
                }

                case "getHistory" -> {
                    bankServer.getHistory();
                }

                case "checkTxStatus" -> {
                    if (parts.length < 2) {
                        log("ERROR: Missing txId for checkTxStatus");
                    } else {
                        bankServer.checkTxStatus(parts[1]);
                    }
                }

                case "cleanHistory" -> {
                    bankServer.cleanHistory();
                    log("Transaction history cleaned");
                }

                case "sleep" -> {
                    if (parts.length < 2) {
                        log("ERROR: Missing duration for sleep");
                    } else {
                        try {
                            int duration = Integer.parseInt(parts[1]);
                            log("Sleeping for " + duration + " seconds...");
                            Thread.sleep(duration * 1000L);
                        } catch (NumberFormatException e) {
                            log("ERROR: Invalid duration for sleep");
                        }
                    }
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
