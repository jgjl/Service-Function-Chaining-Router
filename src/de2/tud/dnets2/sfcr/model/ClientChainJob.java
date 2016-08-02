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
package de2.tud.dnets2.sfcr.model;

import de.tud.dnets2.model.Client;

public class ClientChainJob {
	private Client client;
	private ChainJob chainJob;
	
	public ClientChainJob(Client client, ChainJob chainJob) {
		this.client = client;
		this.chainJob = chainJob;
	}

	public Client getClient() {
		return client;
	}

	public ChainJob getChainJob() {
		return chainJob;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((chainJob == null) ? 0 : chainJob.hashCode());
		result = prime * result + ((client == null) ? 0 : client.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClientChainJob other = (ClientChainJob) obj;
		if (chainJob != other.chainJob)
			return false;
		if (client == null) {
			if (other.client != null)
				return false;
		} else if (!client.equals(other.client))
			return false;
		return true;
	}
}
