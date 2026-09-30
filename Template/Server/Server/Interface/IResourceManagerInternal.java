package Server.Interface;

import java.rmi.RemoteException;

/**
 * Server-internal extension of IResourceManager, used by the Middleware to
 * operate on reservable items held by a remote ResourceManager when the
 * customer records live elsewhere (in the Middleware).
 *
 * Not part of RMIInterface.jar -- the client never sees these methods.
 */
public interface IResourceManagerInternal extends IResourceManager
{
    /**
     * Atomically take one unit of the item with the given key.
     *
     * @return Price of the unit reserved, or -1 if the item doesn't exist or none are left
     */
    public int reserveResource(String key)
	throws RemoteException;

    /**
     * Atomically give back count units of the item with the given key
     * (customer deletion, or rollback of a failed bundle).
     *
     * @return Success
     */
    public boolean unreserveResource(String key, int count)
	throws RemoteException;
}
