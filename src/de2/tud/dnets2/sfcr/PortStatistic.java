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

public class PortStatistic {
	
	private String swIDstring;
	private long swIDlong;
	private short port;
	private long bandwidth;
	private long txbytes, rxBytes;
	private long txpkt, rxpkt;
	private long txdropped, rxdropped;
	private long txerrors, rxerrors;
	private long timestamp;
	
	// Utilization [bytes/second]
	private long utilization;
	protected long delay;
	protected long dropped;
	private long errors;
	
//	public PortStatisitc(String sw, short port){
//		this.setSWID(sw);
//		this.setPort(port);
//	}
	public PortStatistic(long sw, short port){
		this.setSwIDlong(sw);
		this.setPort(port);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + port;
		result = prime * result + (int) (swIDlong ^ (swIDlong >>> 32));
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
		PortStatistic other = (PortStatistic) obj;
		if (port != other.port)
			return false;
		if (swIDlong != other.swIDlong)
			return false;
		return true;
	}

	public void setStatisticValues(long txbytes, long rxbytes, long txpkt, long rxpkt, long txdropped, long rxdropped,
			long txerrors, long rxerrors, long timestamp){
		this.txbytes = txbytes;
		this.rxBytes = rxbytes;
		this.txpkt = txpkt;
		this.rxpkt = rxpkt;
		this.txdropped = txdropped;
		this.rxdropped = rxdropped;
		this.txerrors = txerrors;
		this.rxerrors = rxerrors;
		this.timestamp = timestamp;
	}
	
	public void updateStatistic(long utilization, long delay, long dropped, long errors, long timestamp){
		this.utilization = utilization;
		this.delay = delay;
		this.dropped = dropped;
		this.errors = errors;
		this.timestamp = timestamp;
	}
	
	public long getBandwidth() {
		return bandwidth;
	}

	public void setBandwith(long bandwidth) {
		this.bandwidth = bandwidth;
	}	
	
	public long getTXBytes() {
		return txbytes;
	}

	public void setTXBytes(long txbytes) {
		this.txbytes = txbytes;
	}

	public long getUtilization() {
		return utilization;
	}
	
	public long getDelay() {
		return delay;
	}
	
	public long getDropped() {
		return dropped;
	}

	public long getErrors() {
		return errors;
	}

	public String getSWID() {
		return swIDstring;
	}

	public void setSWID(String sw) {
		this.swIDstring = sw;
	}

	public short getPort() {
		return port;
	}

	public void setPort(short port) {
		this.port = port;
	}

	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp){
		this.timestamp = timestamp;
	}
	
	public String toString(){
		return "PortStatisitc, SW:"+ swIDstring +" Port:"+ port +" Bandwidth:" +bandwidth +
				" Utilization:"+ utilization +" Delay:"+ delay +" Dropped:"+ dropped +" Errors:"+ errors +"\n";
	}

	public long getSwIDlong() {
		return swIDlong;
	}

	public void setSwIDlong(long swIDlong) {
		this.swIDlong = swIDlong;
	}

	public long getRxBytes() {
		return rxBytes;
	}

	public void setRxBytes(long rxBytes) {
		this.rxBytes = rxBytes;
	}

	
}
