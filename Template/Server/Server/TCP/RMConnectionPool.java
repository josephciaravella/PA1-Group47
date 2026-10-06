package Server.TCP;

import Server.Interface.IResourceManagerInternal;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe connection pool for communicating with a backend ResourceManager.
 * Allows multiple concurrent client handler threads in the Middleware to make calls
 * to the same backend RM without blocking one another.
 */
public class RMConnectionPool implements IResourceManagerInternal {
    private final String m_host;
    private final int m_port;
    private final BlockingQueue<TCPResourceManagerProxy> m_pool = new LinkedBlockingQueue<TCPResourceManagerProxy>();

    public RMConnectionPool(String host, int port) {
        this.m_host = host;
        this.m_port = port;
    }

    private TCPResourceManagerProxy acquire() throws RemoteException {
        TCPResourceManagerProxy proxy = m_pool.poll();
        if (proxy == null || proxy.isClosed()) {
            try {
                proxy = new TCPResourceManagerProxy(m_host, m_port);
            } catch (IOException e) {
                throw new RemoteException("Failed to connect to RM at " + m_host + ":" + m_port + ": " + e.getMessage(), e);
            }
        }
        return proxy;
    }

    private void release(TCPResourceManagerProxy proxy) {
        if (proxy != null && !proxy.isClosed()) {
            m_pool.offer(proxy);
        }
    }

    // Functional interface for executing a remote call with automatic proxy acquisition and release
    @FunctionalInterface
    private interface RMCall<T> {
        T call(TCPResourceManagerProxy proxy) throws RemoteException;
    }

    private <T> T execute(RMCall<T> action) throws RemoteException {
        TCPResourceManagerProxy proxy = acquire();
        try {
            return action.call(proxy);
        } catch (RemoteException e) {
            proxy.close();
            throw e;
        } finally {
            release(proxy);
        }
    }

    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        return execute(p -> p.addFlight(flightNum, flightSeats, flightPrice));
    }

    public boolean addCars(String location, int count, int price) throws RemoteException {
        return execute(p -> p.addCars(location, count, price));
    }

    public boolean addRooms(String location, int count, int price) throws RemoteException {
        return execute(p -> p.addRooms(location, count, price));
    }

    public int newCustomer() throws RemoteException {
        return execute(p -> p.newCustomer());
    }

    public boolean newCustomer(int cid) throws RemoteException {
        return execute(p -> p.newCustomer(cid));
    }

    public boolean deleteFlight(int flightNum) throws RemoteException {
        return execute(p -> p.deleteFlight(flightNum));
    }

    public boolean deleteCars(String location) throws RemoteException {
        return execute(p -> p.deleteCars(location));
    }

    public boolean deleteRooms(String location) throws RemoteException {
        return execute(p -> p.deleteRooms(location));
    }

    public boolean deleteCustomer(int customerID) throws RemoteException {
        return execute(p -> p.deleteCustomer(customerID));
    }

    public int queryFlight(int flightNum) throws RemoteException {
        return execute(p -> p.queryFlight(flightNum));
    }

    public int queryCars(String location) throws RemoteException {
        return execute(p -> p.queryCars(location));
    }

    public int queryRooms(String location) throws RemoteException {
        return execute(p -> p.queryRooms(location));
    }

    public String queryCustomerInfo(int customerID) throws RemoteException {
        return execute(p -> p.queryCustomerInfo(customerID));
    }

    public int queryFlightPrice(int flightNum) throws RemoteException {
        return execute(p -> p.queryFlightPrice(flightNum));
    }

    public int queryCarsPrice(String location) throws RemoteException {
        return execute(p -> p.queryCarsPrice(location));
    }

    public int queryRoomsPrice(String location) throws RemoteException {
        return execute(p -> p.queryRoomsPrice(location));
    }

    public boolean reserveFlight(int customerID, int flightNum) throws RemoteException {
        return execute(p -> p.reserveFlight(customerID, flightNum));
    }

    public boolean reserveCar(int customerID, String location) throws RemoteException {
        return execute(p -> p.reserveCar(customerID, location));
    }

    public boolean reserveRoom(int customerID, String location) throws RemoteException {
        return execute(p -> p.reserveRoom(customerID, location));
    }

    public boolean bundle(int customerId, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        return execute(p -> p.bundle(customerId, flightNumbers, location, car, room));
    }

    public String getName() throws RemoteException {
        return execute(p -> p.getName());
    }

    public int reserveResource(String key) throws RemoteException {
        return execute(p -> p.reserveResource(key));
    }

    public boolean unreserveResource(String key, int count) throws RemoteException {
        return execute(p -> p.unreserveResource(key, count));
    }
}
