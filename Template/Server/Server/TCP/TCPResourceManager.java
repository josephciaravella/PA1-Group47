package Server.TCP;

import Server.Common.GenericDispatcher;
import Server.Common.ResourceManager;
import Server.Common.Trace;
import Server.Interface.TCPMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPResourceManager {
    private static String s_serverName = "Server";
    private static int s_serverPort = 50001;

    protected String m_name = "Server";
    protected int m_port = 50001;

    protected ResourceManager m_resourceManager;
    protected ExecutorService m_threadPool;

    public static void main(String[] args) {
        if (args.length > 0) {
            s_serverName = args[0];
        }
        if (args.length > 1) {
            try {
                s_serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1]);
                System.exit(1);
            }
        }

        TCPResourceManager server = new TCPResourceManager(s_serverName, s_serverPort);
        server.start();
    }

    public TCPResourceManager(String name, int port) {
        this.m_name = name;
        this.m_port = port;
        this.m_resourceManager = new ResourceManager(name);
        // Cached thread pool allows handling multiple client/middleware requests concurrently
        this.m_threadPool = Executors.newCachedThreadPool();
    }

    public void start() {
        System.out.println("Starting TCP Resource Manager '" + m_name + "' on port " + m_port);

        try (ServerSocket serverSocket = new ServerSocket(m_port))  {
            while (true) {
                // Wait for an incoming connection
                Socket clientSocket = serverSocket.accept();
                // Pass each connection to a thread pool worker
                m_threadPool.execute(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("ServerSocket error: " + e.getMessage());
            e.printStackTrace();
        }
    }    

    private void handleConnection(Socket socket) {
        try {
            // Note: Initialize ObjectOutputStream BEFORE ObjectInputStream to avoid stream header deadlock!
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // 1. Read request
                TCPMessage request = (TCPMessage) in.readObject();
                if (request == null) break;

                // 2. Execute method dynamically using GenericDispatcher
                Object result = null;
                Exception exception = null;
                try {
                    result = GenericDispatcher.dispatch(m_resourceManager, request);
                } catch (Exception e) {
                    exception = e;
                }

                // 3. Send response back with the same message ID
                TCPMessage response = new TCPMessage(request.getId(), result, exception);
                out.writeObject(response);
                out.flush();
            }
        } catch (EOFException | SocketException e) {
            // Connection closed by client/middleware normally
        } catch (Exception e) {
            Trace.warn("Connection error in TCPResourceManager: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
