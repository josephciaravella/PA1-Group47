package Client;

public class TCPClient extends Client {
    private static String s_serverHost = "localhost";
    private static int s_serverPort = 50001;

    public static void main(String args[]) {
        if (args.length > 0) {
            s_serverHost = args[0];
        }
        if (args.length > 1) {
            try {
                s_serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1]);
                System.exit(1);
            }
        }

        try {
            TCPClient client = new TCPClient();
            client.connectServer();
            client.start();
        } catch (Exception e) {
            System.err.println("TCPClient exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public TCPClient() {
        super();
    }

    @Override
    public void connectServer() {
        connectServer(s_serverHost, s_serverPort);
    }

    public void connectServer(String host, int port) {
        boolean first = true;
        while (true) {
            try {
                m_resourceManager = new TCPResourceManagerProxy(host, port);
                System.out.println("Connected to TCP server [" + host + ":" + port + "]");
                break;
            } catch (Exception e) {
                if (first) {
                    System.out.println("Waiting for TCP server [" + host + ":" + port + "]...");
                    first = false;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
