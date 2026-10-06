package Server.TCP;

import Server.Interface.IResourceManagerInternal;
import Server.Interface.TCPMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Vector;

public class TCPResourceManagerProxy implements IResourceManagerInternal {
    private Socket m_socket;
    private ObjectOutputStream m_out;
    private ObjectInputStream m_in;

    public TCPResourceManagerProxy(String host, int port) throws IOException {
        this.m_socket = new Socket(host, port);
        this.m_out = new ObjectOutputStream(m_socket.getOutputStream());
        this.m_out.flush();
        this.m_in = new ObjectInputStream(m_socket.getInputStream());
    }

    /**
     * General wrapping / message creation service.
     * All operations delegate to this method.
     */
    private synchronized Object sendRequest(String methodName, Object... args) throws RemoteException {
        try {
            TCPMessage request = new TCPMessage(methodName, args);
            m_out.writeObject(request);
            m_out.flush();

            TCPMessage response = (TCPMessage) m_in.readObject();
            if (response.getException() != null) {
                if (response.getException() instanceof RemoteException) {
                    throw (RemoteException) response.getException();
                }
                throw new RemoteException("Server error: " + response.getException().getMessage(), response.getException());
            }
            return response.getResult();
        } catch (RemoteException re) {
            throw re;
        } catch (Exception e) {
            throw new RemoteException("TCP communication error: " + e.getMessage(), e);
        }
    }

    // --- IResourceManager Methods ---

    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        return (Boolean) sendRequest("addFlight", flightNum, flightSeats, flightPrice);
    }

    public boolean addCars(String location, int numCars, int price) throws RemoteException {
        return (Boolean) sendRequest("addCars", location, numCars, price);
    }

    public boolean addRooms(String location, int numRooms, int price) throws RemoteException {
        return (Boolean) sendRequest("addRooms", location, numRooms, price);
    }

    public int newCustomer() throws RemoteException {
        return (Integer) sendRequest("newCustomer");
    }

    public boolean newCustomer(int cid) throws RemoteException {
        return (Boolean) sendRequest("newCustomer", cid);
    }

    public boolean deleteFlight(int flightNum) throws RemoteException {
        return (Boolean) sendRequest("deleteFlight", flightNum);
    }

    public boolean deleteCars(String location) throws RemoteException {
        return (Boolean) sendRequest("deleteCars", location);
    }

    public boolean deleteRooms(String location) throws RemoteException {
        return (Boolean) sendRequest("deleteRooms", location);
    }

    public boolean deleteCustomer(int customerID) throws RemoteException {
        return (Boolean) sendRequest("deleteCustomer", customerID);
    }

    public int queryFlight(int flightNumber) throws RemoteException {
        return (Integer) sendRequest("queryFlight", flightNumber);
    }

    public int queryCars(String location) throws RemoteException {
        return (Integer) sendRequest("queryCars", location);
    }

    public int queryRooms(String location) throws RemoteException {
        return (Integer) sendRequest("queryRooms", location);
    }

    public String queryCustomerInfo(int customerID) throws RemoteException {
        return (String) sendRequest("queryCustomerInfo", customerID);
    }

    public int queryFlightPrice(int flightNumber) throws RemoteException {
        return (Integer) sendRequest("queryFlightPrice", flightNumber);
    }

    public int queryCarsPrice(String location) throws RemoteException {
        return (Integer) sendRequest("queryCarsPrice", location);
    }

    public int queryRoomsPrice(String location) throws RemoteException {
        return (Integer) sendRequest("queryRoomsPrice", location);
    }

    public boolean reserveFlight(int customerID, int flightNumber) throws RemoteException {
        return (Boolean) sendRequest("reserveFlight", customerID, flightNumber);
    }

    public boolean reserveCar(int customerID, String location) throws RemoteException {
        return (Boolean) sendRequest("reserveCar", customerID, location);
    }

    public boolean reserveRoom(int customerID, String location) throws RemoteException {
        return (Boolean) sendRequest("reserveRoom", customerID, location);
    }

    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        return (Boolean) sendRequest("bundle", customerID, flightNumbers, location, car, room);
    }

    public String getName() throws RemoteException {
        return (String) sendRequest("getName");
    }

    // --- IResourceManagerInternal Methods ---

    public int reserveResource(String key) throws RemoteException {
        return (Integer) sendRequest("reserveResource", key);
    }

    public boolean unreserveResource(String key, int count) throws RemoteException {
        return (Boolean) sendRequest("unreserveResource", key, count);
    }

    public boolean isClosed() {
        return m_socket == null || m_socket.isClosed();
    }

    public void close() {
        try {
            if (m_socket != null) m_socket.close();
        } catch (IOException ignored) {}
    }
}
