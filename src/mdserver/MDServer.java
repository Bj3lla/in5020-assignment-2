// Server Main: the main class creates an instance of MDServerImpl and binds it to the RMI registry:
package mdserver;

import java.rmi.Naming;

public class MDServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MDServer <host:port> or <port> (defaults to localhost)");
            return;
        }

        String host = "localhost"; // Default to localhost
        int port;

        // Check if argument contains host:port or just port
        if (args[0].contains(":")) {
            String[] parts = args[0].split(":");
            if (parts.length != 2) {
                System.err.println("Error: Host:port must be in format host:port (e.g., 0.0.0.0:1099)");
                return;
            }
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            port = Integer.parseInt(args[0]);
        }

        MDServerImpl server = new MDServerImpl();

        // Bind to the specified host and port
        Naming.rebind("rmi://" + host + ":" + port + "/MDServer", server);
        System.out.println("Message Delivery Server running on " + host + ":" + port);
    }
}


