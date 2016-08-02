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

import net.floodlightcontroller.core.IOFSwitch;

public class SwitchCookie {
	private IOFSwitch sw;
	private long cookie;
	
	public SwitchCookie(IOFSwitch sw, long cookie) {
		this.sw = sw;
		this.cookie = cookie;
	}
	
	public IOFSwitch getSw() {
		return sw;
	}
	public void setSw(IOFSwitch sw) {
		this.sw = sw;
	}
	public long getCookie() {
		return cookie;
	}
	public void setCookie(long cookie) {
		this.cookie = cookie;
	}
}
