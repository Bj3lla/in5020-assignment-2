package common;

import java.io.*;
import java.util.*;

public class CurrencyConverter {
    private final Map<String, Double> rates = new HashMap<>();

    /**
     * Constructor that loads trading rates from a file.
     * The file should contain lines in the format: <Currency> <RateToUSD>
     * Example:
     * EUR 1.17
     * NOK 0.10
     * GBP 1.34
     * CHF 1.25
     * DKK 0.16
     */
    public CurrencyConverter(String filename) throws IOException {
        // Add USD as the default currency with a rate of 1.0
        rates.put("USD", 1.0);

        // Load trading rates from the file
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    String currency = parts[0].toUpperCase();
                    double rateToUSD = Double.parseDouble(parts[1]);
                    rates.put(currency, rateToUSD);
                }
            }
        }
    }

    /**
     * Converts an amount from the specified currency to USD.
     * @param currency The currency to convert from.
     * @param amount The amount in the specified currency.
     * @return The equivalent amount in USD.
     */
    public double toUSD(String currency, double amount) {
        Double rate = rates.get(currency.toUpperCase());
        if (rate == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return amount * rate;
    }

    /**
     * Converts an amount from USD to the specified currency.
     * @param currency The currency to convert to.
     * @param amount The amount in USD.
     * @return The equivalent amount in the specified currency.
     */
    public double fromUSD(String currency, double amount) {
        Double rate = rates.get(currency.toUpperCase());
        if (rate == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return amount / rate;
    }

    /**
     * Returns the list of supported currencies.
     * @return A set of supported currency codes.
     */
    public Set<String> supportedCurrencies() {
        return rates.keySet();
    }

    /**
     * Prints all the loaded trading rates for debugging purposes.
     */
    public void printRates() {
        System.out.println("Loaded trading rates:");
        rates.forEach((currency, rate) -> System.out.println(currency + " -> USD: " + rate));
    }
}
