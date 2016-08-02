/**
 * This file is part of the program/application/library Service Function Chaining Router (SFCR), a component of the Software-Defined Network Service Chaining Demonstrator.
 *
 * © 2015 Waechter, Timm and Negoescu, Victor and Blendin, Jeremias
 *
 * This program is licensed under the Apache-2.0 License (http://opensource.org/licenses/Apache-2.0). See also file LICENSING
 *
 * Development sponsored by Deutsche Telekom AG [ opensource@telekom.de<mailto:opensource@telekom.de> ]
 * 
 * Modificationsections:
 * 1. 2014, Waechter, Timm: Initial Release.
 */
package de2.tud.dnets2.sfcr;

public class ClientPriorities {
	private short clientPriority;
	
	enum ClientPriorityType {
		DEFAULT, ELEVATED, UPDATE, ELEVATED_UPDATE
	}

	public ClientPriorities(short clientPriority) {
		this.clientPriority = clientPriority;
	}
	
	public short getByType(ClientPriorityType type) {
		switch (type) {
		case DEFAULT:
			return getDefaultPriority();
		case ELEVATED:
			return getElevatedPriority();
		case UPDATE:
			return getUpdatePriority();
		case ELEVATED_UPDATE:
			return getElevatedUpdatePriority();
		}
		return getDefaultPriority();
	}
	
	public short getDefaultPriority() {
		return clientPriority;
	}
	
	public short getElevatedPriority() {
		return (short) (clientPriority+1);
	}
	
	public short getUpdatePriority() {
		return (short) (clientPriority+2);
	}
	
	public short getElevatedUpdatePriority() {
		return (short) (clientPriority+3);
	}
}
