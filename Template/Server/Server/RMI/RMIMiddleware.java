package Server.RMI;

import Server.Interface.*;
import Server.Common.*;

import java.rmi.NotBoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

// Sits between the client and the Flights/Cars/Rooms ResourceManagers.
// Customers are stored locally (inherited m_data); flight/car/room operations
// are forwarded to the matching ResourceManager.
public class RMIMiddleware extends ResourceManager
{
	private static String s_serverName = "Middleware";
	private static String s_rmiPrefix = "group_47_";
	private static int s_serverPort = 1099;

	private IResourceManagerInternal m_flightsRM;
	private IResourceManagerInternal m_carsRM;
	private IResourceManagerInternal m_roomsRM;

	// One lock object per customer ID: operations on the same customer are
	// serialized, different customers proceed in parallel
	private final ConcurrentHashMap<Integer, Object> m_customerLocks = new ConcurrentHashMap<Integer, Object>();

	public static void main(String args[])
	{
		if (args.length != 3)
		{
			System.err.println((char)27 + "[31;1mMiddleware exception: " + (char)27 + "[0mUsage: java Server.RMI.RMIMiddleware <flightsHost> <carsHost> <roomsHost>");
			System.exit(1);
		}

		try {
			RMIMiddleware server = new RMIMiddleware(s_serverName);
			server.m_flightsRM = connectRM(args[0], "Flights");
			server.m_carsRM = connectRM(args[1], "Cars");
			server.m_roomsRM = connectRM(args[2], "Rooms");

			// Dynamically generate the stub (client proxy)
			IResourceManager middleware = (IResourceManager)UnicastRemoteObject.exportObject(server, 0);

			// Bind the remote object's stub in the registry
			Registry l_registry;
			try {
				l_registry = LocateRegistry.createRegistry(s_serverPort);
			} catch (RemoteException e) {
				l_registry = LocateRegistry.getRegistry(s_serverPort);
			}
			final Registry registry = l_registry;
			registry.rebind(s_rmiPrefix + s_serverName, middleware);

			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						registry.unbind(s_rmiPrefix + s_serverName);
						System.out.println("'" + s_serverName + "' middleware unbound");
					}
					catch(Exception e) {
						System.err.println((char)27 + "[31;1mMiddleware exception: " + (char)27 + "[0mUncaught exception");
						e.printStackTrace();
					}
				}
			});
			System.out.println("'" + s_serverName + "' middleware server ready and bound to '" + s_rmiPrefix + s_serverName + "'");
		}
		catch (Exception e) {
			System.err.println((char)27 + "[31;1mMiddleware exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Look up a ResourceManager, retrying until it is bound (same pattern as RMIClient.connectServer)
	private static IResourceManagerInternal connectRM(String host, String name) throws InterruptedException
	{
		boolean first = true;
		while (true) {
			try {
				Registry registry = LocateRegistry.getRegistry(host, s_serverPort);
				IResourceManagerInternal rm = (IResourceManagerInternal)registry.lookup(s_rmiPrefix + name);
				System.out.println("Connected to '" + name + "' server [" + host + ":" + s_serverPort + "/" + s_rmiPrefix + name + "]");
				return rm;
			}
			catch (NotBoundException|RemoteException e) {
				if (first) {
					System.out.println("Waiting for '" + name + "' server [" + host + ":" + s_serverPort + "/" + s_rmiPrefix + name + "]");
					first = false;
				}
			}
			Thread.sleep(500);
		}
	}

	public RMIMiddleware(String name)
	{
		super(name);
	}

	private Object customerLock(int customerID)
	{
		return m_customerLocks.computeIfAbsent(customerID, k -> new Object());
	}

	// Map a reservable item key ("flight-12", "car-montreal", "room-montreal") to its ResourceManager
	private IResourceManagerInternal rmForKey(String key)
	{
		if (key.startsWith("flight-")) return m_flightsRM;
		if (key.startsWith("car-")) return m_carsRM;
		if (key.startsWith("room-")) return m_roomsRM;
		throw new IllegalArgumentException("Unknown item key '" + key + "'");
	}

	// ---- Forwarded to the Flights/Cars/Rooms ResourceManagers ----

	public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException
	{
		return m_flightsRM.addFlight(flightNum, flightSeats, flightPrice);
	}

	public boolean addCars(String location, int count, int price) throws RemoteException
	{
		return m_carsRM.addCars(location, count, price);
	}

	public boolean addRooms(String location, int count, int price) throws RemoteException
	{
		return m_roomsRM.addRooms(location, count, price);
	}

	public boolean deleteFlight(int flightNum) throws RemoteException
	{
		return m_flightsRM.deleteFlight(flightNum);
	}

	public boolean deleteCars(String location) throws RemoteException
	{
		return m_carsRM.deleteCars(location);
	}

	public boolean deleteRooms(String location) throws RemoteException
	{
		return m_roomsRM.deleteRooms(location);
	}

	public int queryFlight(int flightNum) throws RemoteException
	{
		return m_flightsRM.queryFlight(flightNum);
	}

	public int queryCars(String location) throws RemoteException
	{
		return m_carsRM.queryCars(location);
	}

	public int queryRooms(String location) throws RemoteException
	{
		return m_roomsRM.queryRooms(location);
	}

	public int queryFlightPrice(int flightNum) throws RemoteException
	{
		return m_flightsRM.queryFlightPrice(flightNum);
	}

	public int queryCarsPrice(String location) throws RemoteException
	{
		return m_carsRM.queryCarsPrice(location);
	}

	public int queryRoomsPrice(String location) throws RemoteException
	{
		return m_roomsRM.queryRoomsPrice(location);
	}

	// ---- Customer operations (customers live here, in the Middleware) ----

	public boolean newCustomer(int customerID) throws RemoteException
	{
		synchronized(customerLock(customerID)) {
			return super.newCustomer(customerID);
		}
	}

	public boolean deleteCustomer(int customerID) throws RemoteException
	{
		Trace.info("MW::deleteCustomer(" + customerID + ") called");
		synchronized(customerLock(customerID)) {
			Customer customer = (Customer)readData(Customer.getKey(customerID));
			if (customer == null)
			{
				Trace.warn("MW::deleteCustomer(" + customerID + ") failed--customer doesn't exist");
				return false;
			}

			// Give back every reserved item to the ResourceManager that owns it
			RMHashMap reservations = customer.getReservations();
			for (String reservedKey : reservations.keySet())
			{
				ReservedItem reserveditem = customer.getReservedItem(reservedKey);
				String itemKey = reserveditem.getReservableItemKey();
				Trace.info("MW::deleteCustomer(" + customerID + ") has reserved " + itemKey + " " + reserveditem.getCount() + " times");
				rmForKey(itemKey).unreserveResource(itemKey, reserveditem.getCount());
			}

			removeData(customer.getKey());
			Trace.info("MW::deleteCustomer(" + customerID + ") succeeded");
			return true;
		}
	}

	// Reserve one unit of an item held by rm for a customer held here
	protected boolean reserveItem(int customerID, IResourceManagerInternal rm, String key, String location) throws RemoteException
	{
		Trace.info("MW::reserveItem(customer=" + customerID + ", " + key + ", " + location + ") called");
		synchronized(customerLock(customerID)) {
			Customer customer = (Customer)readData(Customer.getKey(customerID));
			if (customer == null)
			{
				Trace.warn("MW::reserveItem(" + customerID + ", " + key + ", " + location + ") failed--customer doesn't exist");
				return false;
			}

			int price = rm.reserveResource(key);
			if (price < 0)
			{
				Trace.warn("MW::reserveItem(" + customerID + ", " + key + ", " + location + ") failed--item unavailable");
				return false;
			}

			customer.reserve(key, location, price);
			writeData(customer.getKey(), customer);
			Trace.info("MW::reserveItem(" + customerID + ", " + key + ", " + location + ") succeeded");
			return true;
		}
	}

	public boolean reserveFlight(int customerID, int flightNum) throws RemoteException
	{
		return reserveItem(customerID, m_flightsRM, Flight.getKey(flightNum), String.valueOf(flightNum));
	}

	public boolean reserveCar(int customerID, String location) throws RemoteException
	{
		return reserveItem(customerID, m_carsRM, Car.getKey(location), location);
	}

	public boolean reserveRoom(int customerID, String location) throws RemoteException
	{
		return reserveItem(customerID, m_roomsRM, Room.getKey(location), location);
	}

	// Reserve all flights plus optional car/room at location -- all or nothing
	public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException
	{
		Trace.info("MW::bundle(" + customerID + ", " + flightNumbers + ", " + location + ", " + car + ", " + room + ") called");
		synchronized(customerLock(customerID)) {
			Customer customer = (Customer)readData(Customer.getKey(customerID));
			if (customer == null)
			{
				Trace.warn("MW::bundle(" + customerID + ") failed--customer doesn't exist");
				return false;
			}

			// Build the list of (rm, key, location) to reserve
			List<IResourceManagerInternal> rms = new ArrayList<IResourceManagerInternal>();
			List<String> keys = new ArrayList<String>();
			List<String> locations = new ArrayList<String>();
			try {
				for (String flight : flightNumbers)
				{
					int flightNum = Integer.parseInt(flight.trim());
					rms.add(m_flightsRM);
					keys.add(Flight.getKey(flightNum));
					locations.add(String.valueOf(flightNum));
				}
			} catch (NumberFormatException e) {
				Trace.warn("MW::bundle(" + customerID + ") failed--invalid flight number");
				return false;
			}
			if (car)
			{
				rms.add(m_carsRM);
				keys.add(Car.getKey(location));
				locations.add(location);
			}
			if (room)
			{
				rms.add(m_roomsRM);
				keys.add(Room.getKey(location));
				locations.add(location);
			}

			// Reserve each item; on any failure give back what was already taken
			int[] prices = new int[keys.size()];
			int taken = 0;
			boolean success = false;
			try {
				for (; taken < keys.size(); ++taken)
				{
					prices[taken] = rms.get(taken).reserveResource(keys.get(taken));
					if (prices[taken] < 0)
					{
						Trace.warn("MW::bundle(" + customerID + ") failed--" + keys.get(taken) + " unavailable, rolling back");
						return false;
					}
				}
				success = true;
			} finally {
				if (!success)
				{
					for (int i = 0; i < taken; ++i)
					{
						rms.get(i).unreserveResource(keys.get(i), 1);
					}
				}
			}

			// Everything reserved at the RMs -- record it on the customer
			for (int i = 0; i < keys.size(); ++i)
			{
				customer.reserve(keys.get(i), locations.get(i), prices[i]);
			}
			writeData(customer.getKey(), customer);
			Trace.info("MW::bundle(" + customerID + ") succeeded");
			return true;
		}
	}
}
