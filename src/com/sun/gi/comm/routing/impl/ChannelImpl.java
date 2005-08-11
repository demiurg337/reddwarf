package com.sun.gi.comm.routing.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.SGSUser;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.utils.types.BYTEARRAY;

public class ChannelImpl implements SGSChannel, TransportChannelListener {
	private TransportChannel transportChannel;
	private ChannelID localID; // NOTE: Local to router
	private byte[] localIDbytes;
	private ByteBuffer hdr = ByteBuffer.allocate(1024);
	private ByteBuffer[] buffs = new ByteBuffer[2];
	private Map<UserID,SGSUser> localUsers = new HashMap<UserID,SGSUser>();
	
	private enum OPCODE {
		UserJoinedChan, UserLeftChan, UnicastMessage, MulticastMessage, BroadcastMessage,
	}
	
	
	public ChannelImpl(TransportChannel chan) {
		transportChannel = chan;
		localID = new ChannelID();
		localIDbytes = localID.toByteArray();
		buffs[0]=hdr;
	}

	public void unicastData(UserID from, UserID to, ByteBuffer message, boolean reliable) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UnicastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);
			byte[] tobytes = to.toByteArray();
			hdr.putInt(tobytes.length);
			hdr.put(tobytes);
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}
		
		
	}

	public void multicastData(UserID from, UserID[] tolist, ByteBuffer message, boolean reliable) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.MulticastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);
			hdr.putInt(tolist.length);
			for(int i=0;i<tolist.length;i++){
				byte[] tobytes = to[i].toByteArray();
				hdr.putInt(tobytes.length);
				hdr.put(tobytes);
			}
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}
		
	}

	public void broadcastData(UserID from, ByteBuffer message, boolean reliable) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.BroadcastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);			
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}
	}

	public void join(SGSUser user) {
		localUsers.put(user.getUserID(),user);
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UserJoinedChan.ordinal());
			byte[] userbytes = user.getUserID().toByteArray();
			hdr.putInt(userbytes.length);
			hdr.put(userbytes);			
			buffs[1] = null;
			try {
				transportChannel.sendData(hdr);
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}
		
	}

	public ChannelID channelID() {
		return local_id;
	}

	//	 Transport Channel Listener
	
	public synchronized void dataArrived(ByteBuffer buff) {
		int opcode = (int)buff.get();
		OPCODE code = OPCODE.values()[opcode];
		switch(code) {
			case BroadcastMessage:
				boolean reliable = (buff.get()==1);
				int frombytelen = buff.getInt();				
				byte[] frombytes = new byte[frombytelen];
				buff.get(frombytes);
				broadcastToLocalUsers(frombytes,buff,reliable);	
				break;
			case MulticastMessage:
				reliable = (buff.get()==1);
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				int tolistlen = buff.getInt();
				byte[][] tolist = new byte[tolistlen][];
				for(int i=0;i<tolistlen;i++){					
					int tobytelen = hdr.getInt();
					tolist[i] = new byte[tobytelen];
					buff.get(tolist[i]);
				}	
				multicastToLocalUsers(frombytes,tolist,buff,reliable);
				break;
			case UnicastMessage:
				reliable = (buff.get()==1);
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				int tobytelen = buff.getInt();
				byte[] tobytes = new byte[tobytelen];
				buff.get(tobytes);
				sendToLocalUser(frombytes,tobytes,buff,reliable);
				break;
			case UserJoinedChan:
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				sendJoinToLocalUsers(frombytes);
				break;
			case UserLeftChan:
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				sendLeaveToLocalUsers(frombytes);
				break;
		}
	}


	private void sendLeaveToLocalUsers(byte[] frombytes) {		
		for(SGSUser user : localUsers.values()){
			try {
				user.sendUserLeftChannel(localIDbytes,frombytes);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	private void sendJoinToLocalUsers(byte[] frombytes) {
		for(SGSUser user : localUsers.values()){
			try {
				user.sendUserJoinedChannel(localIDbytes,frombytes);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void sendToLocalUser(byte[] frombytes, byte[] tobytes, ByteBuffer buff, boolean reliable) {
		try {
			SGSUser user = localUsers.get(new UserID(tobytes));
			if (user != null){ // our user
				user.sendMsg(localIDbytes,frombytes,reliable,buff);
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	private void multicastToLocalUsers(byte[] frombytes, byte[][] tolist, ByteBuffer buff, boolean reliable) {
		for(byte[] tobytes : tolist){
			sendToLocalUser(frombytes,tobytes,buff,reliable);
		}
		
	}

	private void broadcastToLocalUsers(byte[] frombytes, ByteBuffer buff, boolean reliable) {
		for(SGSUser user : localUsers.values()){
			sendToLocalUser(frombytes,user.getUserID().toByteArray(),buff,reliable);
		}
		
	}

	public void channelClosed() {
		for(SGSUser user : localUsers.values()){
			user.sendLeftChan(localIDbytes);
		}		
	}

	
}
