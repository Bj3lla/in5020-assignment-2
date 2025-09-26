package common;

import java.io.*;
import java.util.*;

public class CurrencyConverter {
    private final Map<String, Double> rates = new HashMap<>();

    public CurrencyConverter(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    rates.put(parts[0].toUpperCase(), Double.valueOf(parts[1]));
                }
            }
        }
        rates.putIfAbsent("USD", 1.0); // default currency
    }

    public double toUSD(String currency, double amount) {
        return amount * rates.getOrDefault(currency.toUpperCase(), 0.0);
    }

    public double fromUSD(String currency, double amount) {
        return amount / rates.getOrDefault(currency.toUpperCase(), 1.0);
    }

    public Set<String> supportedCurrencies() {
        return Collections.unmodifiableSet(rates.keySet());
    }
}
