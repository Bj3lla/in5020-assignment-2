package bankserver.utils;

import java.io.*;
import java.util.*;

public class CurrencyConverter {
    private Map<String, Double> rates = new HashMap<>();

    public CurrencyConverter(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    rates.put(parts[0], Double.parseDouble(parts[1]));
                }
            }
        }
    }

    public double toUSD(String currency, double amount) {
        return amount * rates.getOrDefault(currency, 1.0);
    }
}
