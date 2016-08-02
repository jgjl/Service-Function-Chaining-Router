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

import net.floodlightcontroller.packet.Ethernet;
import org.openflow.protocol.OFMatch;

/**
@author Sergej Melnikowitsch

created Jan 6, 2014
 */

public class OFUtil {

	
	/**
	 * @param ingressPort
	 * @param srcMAC
	 * @param dstMAC
	 * @param dataLayerType
	 * @param vlanID
	 * @param vlanPriority
	 * @param srcIP
	 * @param dstIP
	 * @param protocolIP
	 * @param srcPort
	 * @param dstPort
	 * @return
	 */
	public static OFMatch createMatch(Short ingressPort, String srcMAC, String dstMAC, Short dataLayerType, 
			Short vlanID, Byte vlanPriority, Integer srcIP, Integer dstIP, Byte protocolIP, 
			Short srcPort, Short dstPort){
		OFMatch match = new OFMatch();
		
		int wildcard = OFMatch.OFPFW_ALL;
		
		if(ingressPort != null){
			match.setInputPort(ingressPort);
			wildcard &= ~OFMatch.OFPFW_IN_PORT;
		}
		
		if(srcMAC != null){
			match.setDataLayerSource(srcMAC);
			wildcard &= ~OFMatch.OFPFW_DL_SRC;
		}
		
		if(dstMAC != null){
			match.setDataLayerDestination(dstMAC);
			wildcard &= ~OFMatch.OFPFW_DL_DST;
		}
		
		if(dataLayerType != null){
			match.setDataLayerType(dataLayerType);
			wildcard &= ~OFMatch.OFPFW_DL_TYPE;
		}
		
		if(vlanID != null){
			match.setDataLayerVirtualLan(vlanID);
			wildcard &= ~OFMatch.OFPFW_DL_VLAN;
		}
		
		if(vlanPriority != null){
			match.setDataLayerVirtualLanPriorityCodePoint(vlanPriority);
			wildcard &= ~OFMatch.OFPFW_DL_VLAN_PCP;
		}
		
		if(srcIP != null){
			match.setNetworkSource(srcIP);
			wildcard &= ~OFMatch.OFPFW_NW_SRC_MASK;
		}
		if(dstIP != null){
			match.setNetworkDestination(dstIP);
			wildcard &= ~OFMatch.OFPFW_NW_DST_MASK;
		}
		if(protocolIP != null){
			match.setNetworkProtocol(protocolIP);
			wildcard &= ~OFMatch.OFPFW_NW_PROTO;
		}
		if(srcPort != null){
			match.setTransportSource(srcPort);
			wildcard &= ~OFMatch.OFPFW_TP_SRC;
		}
		if(dstPort != null){
			match.setTransportDestination(dstPort);
			wildcard &= ~OFMatch.OFPFW_TP_DST;
		}
		
		match.setWildcards(wildcard);
		
		return match;
	}
	
	/**
	 * @param mac
	 * @return
	 */
	public static long toLongMAC(String mac){
		return Ethernet.toLong(Ethernet.toMACAddress(mac));
	}
}
