// Server Main: the main class creates an instance of MDServerImpl and binds it to the RMI registry:
package mdserver;

import java.rmi.Naming;

public class MDServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MDServer <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        MDServerImpl server = new MDServerImpl();

        Naming.rebind("rmi://localhost:" + port + "/MDServer", server);
        System.out.println("Message Delivery Server running on port " + port);
    }
}


