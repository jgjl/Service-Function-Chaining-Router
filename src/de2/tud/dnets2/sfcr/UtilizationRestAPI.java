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

import org.json.JSONObject;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class UtilizationRestAPI extends ServerResource {
	
	
	/**
	 * REST GET request. Returns the resource with a given id or
	 * lists all controlled resources.
	 * @return
	 * @throws Exception
	 */
	@Get("json")
	public String handleGet() {
		try {
			return new JSONObject(ServiceFunctionChainRouter.getUtilisationList()).toString();
		} catch (Exception e) {
			// should really never happen
			e.printStackTrace();
			System.err.println("should never happen!");
			return null;
		}
	}

}
