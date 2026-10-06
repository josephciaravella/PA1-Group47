package Server.TCP;

import Server.Common.*;
import Server.Interface.IResourceManagerInternal;
import Server.Interface.TCPMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP Middleware for the Travel Reservation System.
 * Sits between clients and the Flights, Cars, and Rooms ResourceManagers.
 *
 * Concurrency & Non-blocking Design:
 * - Listens for incoming client connections and offloads each connection to a thread pool.
 * - Uses thread-safe connection pools to the backend RMs, ensuring that while the Middleware
 *   is waiting for an RM to respond, it continues accepting and processing other client requests.
 * - Customer state is maintained locally in the Middleware.
 * - Per-customer locks allow different customers to perform operations in parallel.
 */
public class TCPMiddleware extends ResourceManager {
    private static String s_serverName = "TCPMiddleware";
    private static int s_middlewarePort = 50000;

    private static String s_flightsHost = "localhost";
    private static int s_flightsPort = 50001;

    private static String s_carsHost = "localhost";
    private static int s_carsPort = 50002;

    private static String s_roomsHost = "localhost";
    private static int s_roomsPort = 50003;

    private String m_flightsHost = s_flightsHost;
    private int m_flightsPort = s_flightsPort;

    private String m_carsHost = s_carsHost;
    private int m_carsPort = s_carsPort;

    private String m_roomsHost = s_roomsHost;
    private int m_roomsPort = s_roomsPort;

    private int m_middlewarePort = s_middlewarePort;

    private IResourceManagerInternal m_flightsRM;
    private IResourceManagerInternal m_carsRM;
    private IResourceManagerInternal m_roomsRM;

    private final ConcurrentHashMap<Integer, Object> m_customerLocks = new ConcurrentHashMap<Integer, Object>();
    private final ExecutorService m_clientThreadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        // Parse arguments: <flightsHost[:port]> <carsHost[:port]> <roomsHost[:port]> [middlewarePort]
        if (args.length >= 3) {
            parseHostPort(args[0], host -> s_flightsHost = host, port -> s_flightsPort = port);
            parseHostPort(args[1], host -> s_carsHost = host, port -> s_carsPort = port);
            parseHostPort(args[2], host -> s_roomsHost = host, port -> s_roomsPort = port);
        }
        if (args.length >= 4) {
            try {
                s_middlewarePort = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid middleware port: " + args[3]);
                System.exit(1);
            }
        }

        TCPMiddleware middleware = new TCPMiddleware(s_serverName, s_flightsHost, s_flightsPort, s_carsHost, s_carsPort, s_roomsHost, s_roomsPort, s_middlewarePort);
        middleware.initConnections();
        middleware.start();
    }

    private interface Setter<T> { void set(T val); }
    private static void parseHostPort(String input, Setter<String> hostSetter, Setter<Integer> portSetter) {
        String[] parts = input.split(":");
        hostSetter.set(parts[0]);
        if (parts.length > 1) {
            try {
                portSetter.set(Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {}
        }
    }

    public TCPMiddleware(String name) {
        super(name);
        this.m_flightsHost = s_flightsHost;
        this.m_flightsPort = s_flightsPort;
        this.m_carsHost = s_carsHost;
        this.m_carsPort = s_carsPort;
        this.m_roomsHost = s_roomsHost;
        this.m_roomsPort = s_roomsPort;
        this.m_middlewarePort = s_middlewarePort;
    }

    public TCPMiddleware(String name, String flightsHost, int flightsPort, String carsHost, int carsPort, String roomsHost, int roomsPort, int middlewarePort) {
        super(name);
        this.m_flightsHost = flightsHost;
        this.m_flightsPort = flightsPort;
        this.m_carsHost = carsHost;
        this.m_carsPort = carsPort;
        this.m_roomsHost = roomsHost;
        this.m_roomsPort = roomsPort;
        this.m_middlewarePort = middlewarePort;
    }

    public void initConnections() {
        System.out.println("Connecting to Flights RM at " + m_flightsHost + ":" + m_flightsPort);
        this.m_flightsRM = new RMConnectionPool(m_flightsHost, m_flightsPort);

        System.out.println("Connecting to Cars RM at " + m_carsHost + ":" + m_carsPort);
        this.m_carsRM = new RMConnectionPool(m_carsHost, m_carsPort);

        System.out.println("Connecting to Rooms RM at " + m_roomsHost + ":" + m_roomsPort);
        this.m_roomsRM = new RMConnectionPool(m_roomsHost, m_roomsPort);
    }

    public void start() {
        System.out.println("Starting TCP Middleware on port " + m_middlewarePort);

        try (ServerSocket serverSocket = new ServerSocket(s_middlewarePort)) {
            while (true) {
                // Non-blocking accept loop: immediately delegates client socket to a thread pool worker
                Socket clientSocket = serverSocket.accept();
                m_clientThreadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Middleware ServerSocket error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                TCPMessage request = (TCPMessage) in.readObject();
                if (request == null) break;

                Object result = null;
                Exception exception = null;
                try {
                    // Uses GenericDispatcher to execute the method on this Middleware instance
                    result = GenericDispatcher.dispatch(this, request);
                } catch (Exception e) {
                    exception = e;
                }

                TCPMessage response = new TCPMessage(request.getId(), result, exception);
                out.writeObject(response);
                out.flush();
            }
        } catch (EOFException | SocketException ignored) {
            // Client disconnected normally
        } catch (Exception e) {
            Trace.warn("Connection error in TCPMiddleware: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private Object customerLock(int customerID) {
        return m_customerLocks.computeIfAbsent(customerID, k -> new Object());
    }

    private IResourceManagerInternal rmForKey(String key) {
        if (key.startsWith("flight-")) return m_flightsRM;
        if (key.startsWith("car-")) return m_carsRM;
        if (key.startsWith("room-")) return m_roomsRM;
        throw new IllegalArgumentException("Unknown item key '" + key + "'");
    }

    // ==========================================
    // Forwarded Methods to Flights RM
    // ==========================================

    @Override
    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        return m_flightsRM.addFlight(flightNum, flightSeats, flightPrice);
    }

    @Override
    public boolean deleteFlight(int flightNum) throws RemoteException {
        return m_flightsRM.deleteFlight(flightNum);
    }

    @Override
    public int queryFlight(int flightNum) throws RemoteException {
        return m_flightsRM.queryFlight(flightNum);
    }

    @Override
    public int queryFlightPrice(int flightNum) throws RemoteException {
        return m_flightsRM.queryFlightPrice(flightNum);
    }

    // ==========================================
    // Forwarded Methods to Cars RM
    // ==========================================

    @Override
    public boolean addCars(String location, int count, int price) throws RemoteException {
        return m_carsRM.addCars(location, count, price);
    }

    @Override
    public boolean deleteCars(String location) throws RemoteException {
        return m_carsRM.deleteCars(location);
    }

    @Override
    public int queryCars(String location) throws RemoteException {
        return m_carsRM.queryCars(location);
    }

    @Override
    public int queryCarsPrice(String location) throws RemoteException {
        return m_carsRM.queryCarsPrice(location);
    }

    // ==========================================
    // Forwarded Methods to Rooms RM
    // ==========================================

    @Override
    public boolean addRooms(String location, int count, int price) throws RemoteException {
        return m_roomsRM.addRooms(location, count, price);
    }

    @Override
    public boolean deleteRooms(String location) throws RemoteException {
        return m_roomsRM.deleteRooms(location);
    }

    @Override
    public int queryRooms(String location) throws RemoteException {
        return m_roomsRM.queryRooms(location);
    }

    @Override
    public int queryRoomsPrice(String location) throws RemoteException {
        return m_roomsRM.queryRoomsPrice(location);
    }

    // ==========================================
    // Customer Management (Local to Middleware)
    // ==========================================

    @Override
    public int newCustomer() throws RemoteException {
        return super.newCustomer();
    }

    @Override
    public boolean newCustomer(int customerID) throws RemoteException {
        synchronized (customerLock(customerID)) {
            return super.newCustomer(customerID);
        }
    }

    @Override
    public boolean deleteCustomer(int customerID) throws RemoteException {
        Trace.info("TCPMiddleware::deleteCustomer(" + customerID + ") called");
        synchronized (customerLock(customerID)) {
            Customer customer = (Customer) readData(Customer.getKey(customerID));
            if (customer == null) {
                Trace.warn("TCPMiddleware::deleteCustomer(" + customerID + ") failed--customer doesn't exist");
                return false;
            }

            // Return all reserved items to their respective backend ResourceManagers
            RMHashMap reservations = customer.getReservations();
            for (String reservedKey : reservations.keySet()) {
                ReservedItem reserveditem = customer.getReservedItem(reservedKey);
                String itemKey = reserveditem.getReservableItemKey();
                Trace.info("TCPMiddleware::deleteCustomer(" + customerID + ") unreserving " + itemKey + " x" + reserveditem.getCount());
                rmForKey(itemKey).unreserveResource(itemKey, reserveditem.getCount());
            }

            removeData(customer.getKey());
            Trace.info("TCPMiddleware::deleteCustomer(" + customerID + ") succeeded");
            return true;
        }
    }

    @Override
    public String queryCustomerInfo(int customerID) throws RemoteException {
        return super.queryCustomerInfo(customerID);
    }

    // ==========================================
    // Reservation Methods
    // ==========================================

    private boolean reserveItem(int customerID, IResourceManagerInternal rm, String key, String location) throws RemoteException {
        Trace.info("TCPMiddleware::reserveItem(customer=" + customerID + ", " + key + ", " + location + ") called");
        synchronized (customerLock(customerID)) {
            Customer customer = (Customer) readData(Customer.getKey(customerID));
            if (customer == null) {
                Trace.warn("TCPMiddleware::reserveItem(" + customerID + ", " + key + ") failed--customer doesn't exist");
                return false;
            }

            int price = rm.reserveResource(key);
            if (price < 0) {
                Trace.warn("TCPMiddleware::reserveItem(" + customerID + ", " + key + ") failed--item unavailable");
                return false;
            }

            customer.reserve(key, location, price);
            writeData(customer.getKey(), customer);
            Trace.info("TCPMiddleware::reserveItem(" + customerID + ", " + key + ") succeeded");
            return true;
        }
    }

    @Override
    public boolean reserveFlight(int customerID, int flightNum) throws RemoteException {
        return reserveItem(customerID, m_flightsRM, Flight.getKey(flightNum), String.valueOf(flightNum));
    }

    @Override
    public boolean reserveCar(int customerID, String location) throws RemoteException {
        return reserveItem(customerID, m_carsRM, Car.getKey(location), location);
    }

    @Override
    public boolean reserveRoom(int customerID, String location) throws RemoteException {
        return reserveItem(customerID, m_roomsRM, Room.getKey(location), location);
    }

    // ==========================================
    // Atomic Bundle Reservation (with Rollback)
    // ==========================================

    @Override
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        Trace.info("TCPMiddleware::bundle(" + customerID + ", flights=" + flightNumbers + ", location=" + location + ", car=" + car + ", room=" + room + ") called");
        synchronized (customerLock(customerID)) {
            Customer customer = (Customer) readData(Customer.getKey(customerID));
            if (customer == null) {
                Trace.warn("TCPMiddleware::bundle(" + customerID + ") failed--customer doesn't exist");
                return false;
            }

            List<IResourceManagerInternal> rms = new ArrayList<IResourceManagerInternal>();
            List<String> keys = new ArrayList<String>();
            List<String> locations = new ArrayList<String>();

            try {
                for (String flight : flightNumbers) {
                    int flightNum = Integer.parseInt(flight.trim());
                    rms.add(m_flightsRM);
                    keys.add(Flight.getKey(flightNum));
                    locations.add(String.valueOf(flightNum));
                }
            } catch (NumberFormatException e) {
                Trace.warn("TCPMiddleware::bundle(" + customerID + ") failed--invalid flight number");
                return false;
            }

            if (car) {
                rms.add(m_carsRM);
                keys.add(Car.getKey(location));
                locations.add(location);
            }
            if (room) {
                rms.add(m_roomsRM);
                keys.add(Room.getKey(location));
                locations.add(location);
            }

            int[] prices = new int[keys.size()];
            int taken = 0;
            boolean success = false;

            try {
                for (; taken < keys.size(); ++taken) {
                    prices[taken] = rms.get(taken).reserveResource(keys.get(taken));
                    if (prices[taken] < 0) {
                        Trace.warn("TCPMiddleware::bundle(" + customerID + ") failed--" + keys.get(taken) + " unavailable, rolling back");
                        return false;
                    }
                }
                success = true;
            } finally {
                // If any reservation failed midway, roll back all previously reserved items
                if (!success) {
                    for (int i = 0; i < taken; ++i) {
                        rms.get(i).unreserveResource(keys.get(i), 1);
                    }
                }
            }

            // All items successfully reserved: commit to customer's account
            for (int i = 0; i < keys.size(); ++i) {
                customer.reserve(keys.get(i), locations.get(i), prices[i]);
            }
            writeData(customer.getKey(), customer);
            Trace.info("TCPMiddleware::bundle(" + customerID + ") succeeded");
            return true;
        }
    }
}
