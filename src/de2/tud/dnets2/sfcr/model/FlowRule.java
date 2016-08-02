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

import java.util.List;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;

import net.floodlightcontroller.core.IOFSwitch;

public class FlowRule {
	
	private String flowId;
	private IOFSwitch sw;
	private OFMatch match;
	private List<OFAction> actions;
	private int actionLength;
	private short priority;
	private boolean isStatic;
	
	
	public FlowRule(String flowId, IOFSwitch sw, OFMatch match, List<OFAction> actions, int actionLength, boolean isStatic, short priority){
		this.setFlowId(flowId);
		this.setSw(sw);
		this.setMatch(match);
		this.setActions(actions);
		this.setActionLength(actionLength);
		this.setStatic(isStatic);
		this.setPriority(priority);
	}

	public String getFlowId() {
		return flowId;
	}


	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}

	public IOFSwitch getSw() {
		return sw;
	}


	public void setSw(IOFSwitch sw) {
		this.sw = sw;
	}


	public OFMatch getMatch() {
		return match;
	}


	public void setMatch(OFMatch match) {
		this.match = match;
	}


	public List<OFAction> getActions() {
		return actions;
	}


	public void setActions(List<OFAction> actions) {
		this.actions = actions;
	}


	public int getActionLength() {
		return actionLength;
	}


	public void setActionLength(int actionLength) {
		this.actionLength = actionLength;
	}


	public short getPriority() {
		return priority;
	}


	public void setPriority(short priority) {
		this.priority = priority;
	}


	public boolean isStatic() {
		return isStatic;
	}


	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}
}
