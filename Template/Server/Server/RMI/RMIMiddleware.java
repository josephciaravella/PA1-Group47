/ -------------------------------
// COMP 512 - Programming Assignment 1
// RMI Middleware
//
// Implements IResourceManager and forwards every call to whichever back-end
// resource manager (Flights, Cars, Rooms) owns that data. Clients and the
// three resource managers are unchanged.
// -------------------------------

package Server.RMI;

import Server.Interface.*;
import Server.Common.Trace;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.Calendar;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Routing:
 *   flight* ............ Flights RM
 *   car* ................ Cars RM
 *   room* ............... Rooms RM
 *   customer ops ........ sent to all three (see note below)
 *   bundle .............. built here out of the calls above
 *
 * Customers are replicated on all three resource managers, instead of being
 * tracked only in the middleware. ResourceManager.reserveItem() requires a
 * local Customer record to reserve anything, so this is what lets the
 * back-end code stay completely unmodified. newCustomer/deleteCustomer apply
 * to all three; queryCustomerInfo merges the three partial bills into one.
 */
public class RMIMiddleware implements IResourceManager
{
        // TODO: ADD YOUR GROUP NUMBER TO COMPLETE (must match the RMs and the client)
        private static final String RMI_PREFIX = "group_xx_";
        private static final String MIDDLEWARE_NAME = "Middleware";
        private static int s_port = 1099;

        private final String m_name;
        private IResourceManager m_flights;
        private IResourceManager m_cars;
        private IResourceManager m_rooms;

        // Bundle does an availability check, then reserves. This lock keeps two
        // concurrent bundles from both passing the check on the same last seat.
        private final ReentrantLock m_bundleLock = new ReentrantLock();

        public static void main(String args[])
        {
                String flightsHost = "localhost", carsHost = "localhost", roomsHost = "localhost";
                if (args.length >= 3)
                {
                        flightsHost = args[0];
                        carsHost = args[1];
                        roomsHost = args[2];
                }

                try {
                        RMIMiddleware middleware = new RMIMiddleware(MIDDLEWARE_NAME, flightsHost, carsHost, roomsHost, s_port);
                        IResourceManager stub = (IResourceManager)UnicastRemoteObject.exportObject(middleware, 0);

                        Registry l_registry;
                        try {
                                l_registry = LocateRegistry.createRegistry(s_port);
                        } catch (RemoteException e) {
                                l_registry = LocateRegistry.getRegistry(s_port);
                        }
                        final Registry registry = l_registry;
                        registry.rebind(RMI_PREFIX + MIDDLEWARE_NAME, stub);

                        Runtime.getRuntime().addShutdownHook(new Thread() {
                                public void run() {
                                        try {
                                                registry.unbind(RMI_PREFIX + MIDDLEWARE_NAME);
                                                System.out.println("'" + MIDDLEWARE_NAME + "' unbound");
                                        } catch (Exception e) {
                                                e.printStackTrace();
                                        }
                                }
                        });

                        System.out.println("'" + MIDDLEWARE_NAME + "' ready and bound to '" + RMI_PREFIX + MIDDLEWARE_NAME + "'");
                }
                catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                }
        }

        public RMIMiddleware(String name, String flightsHost, String carsHost, String roomsHost, int port) throws RemoteException
        {
                m_name = name;
                m_flights = connect("Flights", flightsHost, port);
                m_cars    = connect("Cars", carsHost, port);
                m_rooms   = connect("Rooms", roomsHost, port);
        }

        /** Looks up one resource manager in the registry, waiting until it is bound. */
        private static IResourceManager connect(String rmName, String host, int port) throws RemoteException
        {
                while (true)
                {
                        try {
                                Registry registry = LocateRegistry.getRegistry(host, port);
                                IResourceManager rm = (IResourceManager)registry.lookup(RMI_PREFIX + rmName);
                                System.out.println("Connected to resource manager " + rmName + " [" + host + ":" + port + "]");
                                return rm;
                        }
                        catch (NotBoundException e) {
                                System.out.println("Waiting for resource manager " + rmName + " [" + host + ":" + port + "]");
                                try { Thread.sleep(500); } catch (InterruptedException ie) { throw new RemoteException("interrupted", ie); }
                        }
                }
        }

        // ------------------------------------------------------------------
        // Flights -> Flights RM
        // ------------------------------------------------------------------

        public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException
        {
                return m_flights.addFlight(flightNum, flightSeats, flightPrice);
        }

        public boolean deleteFlight(int flightNum) throws RemoteException
        {
                return m_flights.deleteFlight(flightNum);
        }

        public int queryFlight(int flightNum) throws RemoteException
        {
                return m_flights.queryFlight(flightNum);
        }

        public int queryFlightPrice(int flightNum) throws RemoteException
        {
                return m_flights.queryFlightPrice(flightNum);
        }

        public boolean reserveFlight(int customerID, int flightNum) throws RemoteException
        {
                return m_flights.reserveFlight(customerID, flightNum);
        }

        // ------------------------------------------------------------------
        // Cars -> Cars RM
        // ------------------------------------------------------------------

        public boolean addCars(String location, int numCars, int price) throws RemoteException
        {
                return m_cars.addCars(location, numCars, price);
        }

        public boolean deleteCars(String location) throws RemoteException
        {
                return m_cars.deleteCars(location);
        }

        public int queryCars(String location) throws RemoteException
        {
                return m_cars.queryCars(location);
        }

        public int queryCarsPrice(String location) throws RemoteException
        {
                return m_cars.queryCarsPrice(location);
        }

        public boolean reserveCar(int customerID, String location) throws RemoteException
        {
                return m_cars.reserveCar(customerID, location);
        }

        // ------------------------------------------------------------------
        // Rooms -> Rooms RM
        // ------------------------------------------------------------------

        public boolean addRooms(String location, int numRooms, int price) throws RemoteException
        {
                return m_rooms.addRooms(location, numRooms, price);
        }

        public boolean deleteRooms(String location) throws RemoteException
        {
                return m_rooms.deleteRooms(location);
        }

        public int queryRooms(String location) throws RemoteException
        {
                return m_rooms.queryRooms(location);
        }

        public int queryRoomsPrice(String location) throws RemoteException
        {
                return m_rooms.queryRoomsPrice(location);
        }

        public boolean reserveRoom(int customerID, String location) throws RemoteException
        {
                return m_rooms.reserveRoom(customerID, location);
        }

        // ------------------------------------------------------------------
        // Customers -> replicated on all three resource managers
        // ------------------------------------------------------------------

        public int newCustomer() throws RemoteException
        {
                int cid = Integer.parseInt(String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                        String.valueOf(Math.round(Math.random() * 100 + 1)));
                newCustomer(cid);
                Trace.info("MW::newCustomer(" + cid + ") created on all resource managers");
                return cid;
        }

        public boolean newCustomer(int customerID) throws RemoteException
        {
                boolean a = m_flights.newCustomer(customerID);
                boolean b = m_cars.newCustomer(customerID);
                boolean c = m_rooms.newCustomer(customerID);
                return a && b && c;
        }

        // Deleting the customer on each RM also returns that customer's
        // reservations to the RM's own pool.
        public boolean deleteCustomer(int customerID) throws RemoteException
        {
                boolean a = m_flights.deleteCustomer(customerID);
                boolean b = m_cars.deleteCustomer(customerID);
                boolean c = m_rooms.deleteCustomer(customerID);
                return a && b && c;
        }

        // Merges the three per-RM bills into the one bill the client expects.
        // Returns "" if no RM knows the customer, same as a plain ResourceManager.
        public String queryCustomerInfo(int customerID) throws RemoteException
        {
                String flightsBill = m_flights.queryCustomerInfo(customerID);
                String carsBill     = m_cars.queryCustomerInfo(customerID);
                String roomsBill    = m_rooms.queryCustomerInfo(customerID);

                if (flightsBill.isEmpty() && carsBill.isEmpty() && roomsBill.isEmpty())
                {
                        return "";
                }

                String bill = "Bill for customer " + customerID + "\n"
                        + bodyOf(flightsBill) + bodyOf(carsBill) + bodyOf(roomsBill);
                System.out.print(bill);
                return bill;
        }

        // Strips a bill's "Bill for customer <id>" header line, keeping just the items.
        private static String bodyOf(String bill)
        {
                int firstLineEnd = bill.indexOf('\n');
                return (firstLineEnd < 0) ? "" : bill.substring(firstLineEnd + 1);
        }

        // ------------------------------------------------------------------
        // Bundle -- the one call that spans all three resource managers
        // ------------------------------------------------------------------

        public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException
        {
                m_bundleLock.lock();
                try {
                        // Check availability for everything first.
                        for (String flightNumber : flightNumbers)
                        {
                                if (m_flights.queryFlight(Integer.parseInt(flightNumber)) < 1)
                                {
                                        return false;
                                }
                        }
                        if (car && m_cars.queryCars(location) < 1)
                        {
                                return false;
                        }
                        if (room && m_rooms.queryRooms(location) < 1)
                        {
                                return false;
                        }

                        // Everything is available: reserve it.
                        for (String flightNumber : flightNumbers)
                        {
                                if (!m_flights.reserveFlight(customerID, Integer.parseInt(flightNumber)))
                                {
                                        return false;
                                }
                        }
                        if (car && !m_cars.reserveCar(customerID, location))
                        {
                                return false;
                        }
                        if (room && !m_rooms.reserveRoom(customerID, location))
                        {
                                return false;
                        }
                        return true;
                }
                finally {
                        m_bundleLock.unlock();
                }
        }

        public String getName() throws RemoteException
        {
                return m_name;
        }
}
