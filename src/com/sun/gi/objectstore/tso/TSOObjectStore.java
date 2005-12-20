/**
 *
 * <p>Title: TSOObjectStore.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.utils.SGSUUID;

/**
 *
 * <p>Title: TSOObjectStore.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSOObjectStore implements ObjectStore {
	DataSpace dataSpace;
	SecureRandom random;
	Map<SGSUUID,TSOTransaction> localTransactionIDMap = new HashMap<SGSUUID,TSOTransaction>();
	
	public TSOObjectStore(DataSpace space) throws InstantiationException{
		dataSpace = space;
		try {
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {			
			e.printStackTrace();
			throw new InstantiationException();
		}
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.ObjectStore#newTransaction(long, java.lang.ClassLoader)
	 */
	public Transaction newTransaction(long appID, ClassLoader loader) {		
		TSOTransaction trans = new TSOTransaction(this,appID,loader,System.currentTimeMillis(),
				random.nextLong());
		localTransactionIDMap.put(trans.uuid,trans);
		return trans;
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.ObjectStore#clearAll()
	 */
	public void clearAll() {
		dataSpace.clearAll();
		
	}
	/**
	 * @param appID
	 * @param loader
	 * @return
	 */
	DataSpaceTransaction getDataSpaceTransaction(long appID, ClassLoader loader) {		
		return dataSpace.getTransaction(appID,loader);
	}
	/**
	 * @param dsTrans
	 */
	void returnDataSpaceTransaction(DataSpaceTransaction dsTrans) {
		dataSpace.returnTransaction(dsTrans);
		
	}
	/**
	 * @param uuid
	 */
	void doTimeStampInterrupt(SGSUUID uuid) {
		TSOTransaction trans = localTransactionIDMap.get(uuid);
		if (trans!=null){
			trans.timeStampInterrupt();
		}
		
	}

	
}
