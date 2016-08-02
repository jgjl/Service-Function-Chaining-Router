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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.monitoring.IMonitoringListener;
import net.floodlightcontroller.monitoring.IMonitoringService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.slf4j.LoggerFactory;

import de.tud.dnets2.global.Constants;
import de.tud.dnets2.model.Client;
import de.tud.dnets2.model.ContentProvider;
import de.tud.dnets2.model.FlowLogEntry;
import de.tud.dnets2.model.ServiceChainInstance;
import de.tud.dnets2.model.ServiceInstance;
import de.tud.dnets2.model.ServiceNode;
import de.tud.dnets2.server.ChainRouter;
import de.tud.dnets2.server.ServiceFunctionChainingController;
import de2.tud.dnets2.sfcr.ClientPriorities.ClientPriorityType;
import de2.tud.dnets2.sfcr.model.ChainJob;
import de2.tud.dnets2.sfcr.model.ClientChainJob;
import de2.tud.dnets2.sfcr.model.FlowRule;

/**
 * @author Sergej Melnikowitsch / Timm Waechter
 * @version 2.0 created Jan 6, 2014
 */

public class ServiceFunctionChainRouter extends Application implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
		ITopologyListener, IMonitoringListener, ChainRouter {

	protected static org.slf4j.Logger log = LoggerFactory.getLogger(ServiceFunctionChainRouter.class);

	// Module dependencies
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topologyService;
	protected IMonitoringService monitoringService;
	protected IRoutingService routingService;

	protected static final short IDLE_TIMEOUT_DEFAULT = 10;
	protected static final short HARD_TIMEOUT_DEFAULT = 0;
	protected static final short PRIORITY_SMALL = 100;
	protected static final short PRIORITY_DEFAULT = 200;
	protected static final short PRIORITY_PREMIUM = 31000;
	protected static final short PRIORITY_MAX = 32000;

	private ServiceFunctionChainingController sfcc = null;

	/** Last used cookie id for rule identification */
	private short lastPriority = 0;

	/** Client priorities, for creating/removing rules */
	private Map<Client, ClientPriorities> clientPriorities = new HashMap<Client, ClientPriorities>();

	/** Stored cookies for each client: Client -> List of OF rules */
	private Map<Client, List<FlowRule>> clientCookies = new HashMap<Client, List<FlowRule>>();

	/** Client chain jobs to be processed */
	private List<ClientChainJob> chainJobs = Collections.synchronizedList(new ArrayList<ClientChainJob>());

	/** Link utilization: Switch -> Port -> Utilization */
	private static Map<Long, HashMap<Short, Long>> utilisationList = Collections.synchronizedMap(new HashMap<Long, HashMap<Short, Long>>());

	/** Topology: Switch -> Port -> Switch */
	private static HashMap<Long, HashMap<Short, Long>> linkList = new HashMap<Long, HashMap<Short, Long>>();

	private static Component component = new Component();
	private static Server server = new Server(Protocol.HTTP, de.tud.dnets2.global.Settings.SFCR_REST_SERVER_PORT);

	@Override
	public synchronized Restlet createInboundRoot() {
		Router router = new Router(getContext());

		router.attach("/utilization", UtilizationRestAPI.class);
		router.attach("/topology", TopologyRestAPI.class);

		return router;
	}

	/**
	 * Generic pair of items
	 * @author jblendin
	 *
	 * @param <First> the left item
	 * @param <Second> the right item
	 */
	public class Pair<First, Second> {
		private final First first;
		private final Second second;

		public Pair(First first, Second second) {
			this.first = first;
			this.second = second;
		}

		public First getFirst() {
			return this.first;
		}

		public Second getSecond() {
			return this.second;
		}
	}

	public enum ChainDirection {
		INGRESS, EGRESS
	}

	@Override
	public String getName() {
		return "sfcr";
	}

	@Override
	public void removedSwitch(IOFSwitch sw) {
		// Not implemented for test scenario.
	}

	@Override
	public void switchPortChanged(Long switchId) {
		// Not implemented for test scenario.
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// Not implemented for test scenario.
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// Not implemented for test scenario.
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		String error = "";
		if (msg instanceof OFPacketIn) {
			OFPacketIn pin = (OFPacketIn) msg;
			OFMatch match = new OFMatch();
			match.loadFromPacket(pin.getPacketData(), pin.getInPort());
			error += " // InPort: " + pin.getInPort() + " // Reason: " + pin.getReason().toString();
		}
		System.err.println("[WARN] Unhandled message. Switch: " + sw.getStringId() + " // MessageType: " + msg.getType() + error + " // Data: " + msg.toString());
		return null;
	}

	@Override
	public void addedSwitch(IOFSwitch sw) {
		OFMatch match = OFUtil.createMatch(null, null, null, Ethernet.TYPE_BSN, null, null, null, null, null, null, null);
		List<OFAction> askCtrlActions = new ArrayList<OFAction>();
		int actionLength = OFFlowMod.MINIMUM_LENGTH;
		askCtrlActions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue()));
		actionLength += OFActionOutput.MINIMUM_LENGTH;
		String flowId = "BSN-Flow" + sw.getId();
		try {
			writeFlow(new FlowRule(flowId, sw, match, askCtrlActions, actionLength, true, PRIORITY_MAX));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		match = OFUtil.createMatch(null, null, null, Ethernet.TYPE_LLDP, null, null, null, null, null, null, null);
		askCtrlActions = new ArrayList<OFAction>();
		actionLength = OFFlowMod.MINIMUM_LENGTH;
		askCtrlActions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue()));
		actionLength += OFActionOutput.MINIMUM_LENGTH;
		flowId = "LLDP-Flow" + sw.getId();
		try {
			writeFlow(new FlowRule(flowId, sw, match, askCtrlActions, actionLength, true, PRIORITY_MAX));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// ******************
	// ITopologyListener
	// ******************
	@Override
	public void topologyChanged() {
		// System.err.println("[DEBUG] Topology change!");

		List<LDUpdate> updates = topologyService.getLastLinkUpdates();
		HashMap<Short, Long> portLink = new HashMap<Short, Long>();
		for (LDUpdate update : updates) {
			UpdateOperation operation = update.getOperation();

			if (operation.equals(UpdateOperation.LINK_UPDATED)) {
				long srcId = update.getSrc();
				long dstId = update.getDst();

				if (srcId == dstId)
					continue;

				short srcPort = update.getSrcPort();
				if (linkList.containsKey(srcId)) {
					linkList.get(srcId).put(srcPort, dstId);
				} else {
					portLink.put(srcPort, dstId);
					linkList.put(srcId, portLink);
				}
			} else if (operation.equals(UpdateOperation.LINK_REMOVED)) {
				long srcId = update.getSrc();
				short srcPort = update.getSrcPort();

				if (linkList.containsKey(srcId)) {
					linkList.get(srcId).remove(srcPort);
				}
			}
		}

		synchronized (chainJobs) {
			processChainJobs();
		}
	}

	public static HashMap<Long, HashMap<Short, Long>> getLinkList() {
		return linkList;
	}

	// ******************
	// IMonitoringListener
	// ******************
	@Override
	public synchronized void statisitcsRecieved(OFStatisticsReply statRep, IOFSwitch sw, long timestamp) {
		if (statRep.getStatisticType() == OFStatisticsType.PORT) {
			long swID = sw.getId();

			HashMap<Short, Long> portUtil;
			if (utilisationList.containsKey(swID))
				portUtil = utilisationList.get(swID);
			else
				portUtil = new HashMap<Short, Long>();

			for (OFStatistics stat : statRep.getStatistics()) {
				OFPortStatisticsReply portStat = (OFPortStatisticsReply) stat;

				short swPort = portStat.getPortNumber();
				portUtil.put(swPort, portStat.getReceiveBytes() + portStat.getTransmitBytes());
			}

			utilisationList.put(swID, portUtil);
		}
	}

	// public static HashMap<String, HashMap<Short,Long>> getUtilisationList() {
	// return utilisationList;
	// }
	public static Map<Long, HashMap<Short, Long>> getUtilisationList() {
		return utilisationList;
	}

	// ******************
	// IFloodlightModule
	// ******************
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(ITopologyService.class);
		l.add(IMonitoringService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		monitoringService = context.getServiceImpl(IMonitoringService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		topologyService.addListener(this);
		monitoringService.addListener(this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFSwitchListener(this);

		component.getServers().add(server);
		component.getClients().add(Protocol.HTTP);
		server.getContext().getParameters().add("maxThreads", "512");
		server.getContext().getParameters().add("maxTotalConnections", "512");

		component.getDefaultHost().attach("/sfcr", this);

		try {
			component.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		sfcc = new ServiceFunctionChainingController(this);
		new Thread(sfcc).start();

		new Thread(new Runnable() {

			@Override
			public void run() {
				synchronized (chainJobs) {
					processChainJobs();
				}
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	// ******************
	// Flow Rules
	// ******************
	/**
	 * Writes a OF rule on a switch.
	 * 
	 * @param client client for that the rule is being written
	 * @param sw switch on that the rule is being written
	 * @param match OF match
	 * @param actions OF actions
	 * @param actionLength length of actions
	 * @param isStatic determines if the rule is static or dynamic
	 * @param priorityType priority type
	 * @return the rule
	 * @throws Exception if the operation fails
	 */
	private FlowRule generateFlowRule(Client client, IOFSwitch sw, OFMatch match, List<OFAction> actions, int actionLength, boolean isStatic, ClientPriorityType priorityType) throws Exception {
		if (!clientCookies.containsKey(client))
			clientCookies.put(client, new ArrayList<FlowRule>());

		if (! clientPriorities.containsKey(client)) {
			lastPriority += 10;
			clientPriorities.put(client, new ClientPriorities(lastPriority));
		}

		String flowId = "traffic-Flow, client: " + client.getIP();
		short priority = (short) (PRIORITY_DEFAULT + clientPriorities.get(client).getByType(priorityType));
		FlowRule flowRule = new FlowRule(flowId, sw, match, actions, actionLength, isStatic, priority);
		writeFlow(flowRule);
		clientCookies.get(client).add(flowRule);
		informSFCCAboutFlow(flowRule, client, OFFlowMod.OFPFC_ADD);
		return flowRule;
	}

	/**
	 * Deletes an OF rule from a switch
	 * 
	 * @param flowRule the rule to delete
	 * @param client client the rule was written for
	 * @throws Exception if the operation fails
	 */
	private void deleteFlowRule(FlowRule flowRule, Client client) throws Exception {

		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT).setOutPort(OFPort.OFPP_NONE).setMatch(flowRule.getMatch()).setPriority(flowRule.getPriority());

		IOFSwitch sw = flowRule.getSw();
		sw.write(flowMod, null);
		sw.flush();
		informSFCCAboutFlow(flowRule, client, OFFlowMod.OFPFC_DELETE_STRICT);
		log.info("deleted from switch {}", sw.getId());
	}

	/**
	 * informs the SFCC about a flow rule being written/deleted
	 * 
	 * @param flow flow rule being written/deleted
	 * @param client client the rule was written/deleted for
	 * @param flowmod OF operation
	 */
	private void informSFCCAboutFlow(FlowRule flow, Client client, int flowmod) {

		List<String> actionStrs = new ArrayList<String>();
		for (OFAction action : flow.getActions())
			actionStrs.add(action.toString());

		FlowLogEntry flowLogEntry = new FlowLogEntry();
		flowLogEntry.setClient(client);
		flowLogEntry.setSwitchMAC(flow.getSw().getStringId());
		flowLogEntry.setCommand(flowmod);
		flowLogEntry.setActions(actionStrs);
		flowLogEntry.setMatch(flow.getMatch().toString());
		ServiceFunctionChainingController.getEntityStorage().insert(flowLogEntry);
	}

	/**
	 * Writes a given flow rule on the appropriate switch
	 * 
	 * @param flowRule the flow rule to write
	 * @throws Exception
	 */
	private void writeFlow(FlowRule flowRule) throws Exception {
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flowMod.setMatch(flowRule.getMatch()).setCommand(OFFlowMod.OFPFC_ADD).setIdleTimeout(flowRule.isStatic() ? 0 : IDLE_TIMEOUT_DEFAULT).setHardTimeout(flowRule.isStatic() ? 0 : HARD_TIMEOUT_DEFAULT).setPriority(flowRule.getPriority()).setBufferId(OFPacketOut.BUFFER_ID_NONE).setOutPort(OFPort.OFPP_NONE.getValue()).setActions(flowRule.getActions()).setLength((short) flowRule.getActionLength());
		try {
			IOFSwitch sw = flowRule.getSw();
			sw.write(flowMod.clone(), null);
			sw.flush();
			log.info("added to switch {}", sw.getId());
		} catch (Exception e) {
			log.error("Failed to write {} to switch {}", new Object[] { flowMod, flowRule.getSw() }, e);
			throw new IllegalStateException("Could not write flowRule to switch " + flowRule.getSw());
		}
	}
	
	/**
	 * Sends a barrier request to every switch
	 * 
	 * @throws Exception
	 */
	private void barrier() throws Exception {
		OFBarrierRequest barrierReq = (OFBarrierRequest) floodlightProvider.getOFMessageFactory().getMessage(OFType.BARRIER_REQUEST);
		for (Entry<Long, IOFSwitch> e : floodlightProvider.getSwitches().entrySet()) {
			IOFSwitch sw = e.getValue();
			try {
				sw.write(barrierReq, null);
				sw.flush();
				log.info("barrier request @ switch: {}", sw.getId());
			} catch (Exception ex) {
				log.error("Failed to send barrier request {} to switch {}", new Object[] { barrierReq, sw }, ex);
				throw new IllegalStateException("Could not send barrier request to switch " + sw);
			}
		}
		
		Thread.sleep(500);
	}

	// ******************
	// ServiceChaining
	// ******************
	@Override
	public boolean initChain(Client client) {
		ClientChainJob job = new ClientChainJob(client, ChainJob.INIT);
		synchronized (chainJobs) {
			if (chainJobs.contains(job)) {
				System.err.println("[WARN] Job already in progress");
				return false; // job in progress
			}

			chainJobs.add(job);
			System.err.println("[INFO] Job added");
			processChainJobs();
			return true;
		}
	}

	@Override
	public boolean removeChain(Client client) {
		ClientChainJob job = new ClientChainJob(client, ChainJob.DELETE);
		synchronized (chainJobs) {
			if (chainJobs.contains(job)) {
				System.err.println("[WARN] Job already in progress");
				return false; // job in progress
			}

			chainJobs.add(job);
			System.err.println("[INFO] Job added");
			processChainJobs();
			return true;
		}
	}

	@Override
	public boolean updateChain(Client client) {
		ClientChainJob job = new ClientChainJob(client, ChainJob.UPDATE);
		synchronized (chainJobs) {
			if (chainJobs.contains(job)) {
				System.err.println("[WARN] Job already in progress");
				return false; // job in progress
			}

			chainJobs.add(job);
			System.err.println("[INFO] Job added");
			processChainJobs();
			return true;
		}
	}

	// ******************
	// Internal Methods
	// ******************
	
	/**
	 * Processes undone chain jobs
	 */
	private void processChainJobs() {
		synchronized (chainJobs) {
			Iterator<ClientChainJob> iter = chainJobs.iterator();
			while (iter.hasNext()) {
				ClientChainJob job = iter.next();
				Client client = job.getClient();
				ChainJob chainJob = job.getChainJob();
				ServiceChainInstance serChainInst = client.getServiceChainInstance();
				List<ServiceInstance> serInstances = serChainInst.getServiceInstances();
				ContentProvider contentProvider = serChainInst.getContentProvider();
				System.err.println("[INFO] ChainJob " + chainJob.toString() + " for client " + client.getIP());

				boolean successful = false;
				SWITCH: switch (chainJob) {
				case INIT:
				case UPDATE:
					boolean update = chainJob.equals(ChainJob.UPDATE);
					/**
					 * Sets up the routing for a new/modified Service Chain Instance
					 */

					// add new rules as (DEFAULT, ELEVATED) in INIT mode and
					// as (DEFAULT_UPDATE, ELEVATED_UPDATE) in UPDATE mode
					if (! createClientFlow(client, contentProvider, serInstances, update))
						break SWITCH;
					
					// -- barrier --
					if (update) {
						try {
							barrier();
						} catch (Exception e) {
							System.err.println("[WARN] Failed to send barrier request (1). Sleep fallback used.");
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e1) {}
							e.printStackTrace();
						}
					}

					/* In update mode, old rules are still installed with priorities DEFAULT and ELEVATED.
					 * The new rules have just been installed with priorities UPDATE and ELEVATED_UPDATE.
					 * Since modification of priorities is not possible with OpenFlow, we'll do the following:
					 * 		1) Remove rules with priorities DEFAULT and ELEVATED.
					 * 		2) Add the new rules again with priorities DEFAULT and ELEVATED
					 * 		3) Remove rules with priorities UPDATE and ELEVATED_UPDATE
					 */
					if (update) {
						// remove DEFAULT & ELEVATED
						if (! removeClientFlow(client, false))
							break SWITCH;
						
						// add new rules as DEFAULT & ELEVATED
						if (! createClientFlow(client, contentProvider, serInstances, false))
							break SWITCH;
						
						// -- barrier --
						try {
							barrier();
						} catch (Exception e) {
							System.err.println("[WARN] Failed to send barrier request (2). Sleep fallback used.");
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e1) {}
							e.printStackTrace();
						}
						
						// remove UPDATE & ELEVATED_UPDATE
						if (! removeClientFlow(client, true))
							break SWITCH;
					}					
					
					successful = true;
					break;
				case DELETE:
					/**
					 * Removes the routing rules of an existing Service Chain
					 * Instance
					 */
					if (! removeClientFlow(client, false))
						break SWITCH;
						
					if (! removeClientFlow(client, true))
						break SWITCH;

					successful = true;
					break;
				}
				if (successful) {
					System.err.println("[INFO] ### SUCCESS ###");
					iter.remove(); // Remove job after successful run
				} else {
					System.err.println("[INFO] ### FAIL ###");
				}
			}
		}
	}

	/**
	 * Removes all associated client rules with a given priority class
	 * 
	 * @param client client the rules will be deleted for
	 * @param update priority class (false = default class, true = update class)
	 * @return success of the operation
	 */
	private boolean removeClientFlow(Client client, boolean update) {
		System.err.println("[DEBUG] removeClientFlow(): deleting flow of client " + client.getMAC());
		
		if (! clientCookies.containsKey(client)) {
			System.err.println("[INFO] removeClientFlow(): no rules installed for client " + client.getMAC());
			return true;
		}
		
		ClientPriorities priorities = clientPriorities.get(client);
		Set<Short> prioritiesToDelete = new HashSet<Short>();
		if (update) {
			prioritiesToDelete.add((short)(PRIORITY_DEFAULT + priorities.getElevatedUpdatePriority()));
			prioritiesToDelete.add((short)(PRIORITY_DEFAULT + priorities.getUpdatePriority()));
		} else {
			prioritiesToDelete.add((short)(PRIORITY_DEFAULT + priorities.getElevatedPriority()));
			prioritiesToDelete.add((short)(PRIORITY_DEFAULT + priorities.getDefaultPriority()));
		}
		
		System.err.println("[DEBUG] removeClientFlow(): number stored rules of client " + client.getMAC() + ": " + 
							clientCookies.get(client).size());
		System.err.println("[DEBUG] removeClientFlow(): we look for rules with priorities " + prioritiesToDelete.toString());
		Iterator<FlowRule> flowRuleIter = clientCookies.get(client).iterator();
		while (flowRuleIter.hasNext()) {
			FlowRule flowRule = flowRuleIter.next();
			System.err.println("[DEBUG] removeClientFlow(): priority of rule " +flowRule.getPriority());
			if (prioritiesToDelete.contains(flowRule.getPriority())) {
				System.err.println("[DEBUG] removeClientFlow(): found matching rule " +flowRule);
				try {
					deleteFlowRule(flowRule, client);
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
				flowRuleIter.remove();
			}
		}
		
		return true;
	}
	
	/**
	 * Writes all necessary flow rules in order to make a client's Service Chain Instance functional.
	 * 
	 * @param client client the rules will be written for
	 * @param contentProvider content provider (WWW edge)
	 * @param serInstances list of Service Instances
	 * @param update priority class (false = default class, true = update class)
	 * @return success of the operation
	 */
	private boolean createClientFlow(Client client, ContentProvider contentProvider, List<ServiceInstance> serInstances, boolean update) {
		System.err.println("[DEBUG] createClientFlow(): creating flow for client " + client.getMAC());
		
		// Try to determine the switches the Service Instances are
		// connected to
		for (ServiceInstance si : serInstances) {
			IOFSwitch sw = floodlightProvider.getSwitches().get(Long.valueOf(si.getServiceNode().getSwitchMAC()));
			if (sw == null) {
				System.err.println("[ERROR] Service Node switch not found (MAC " + si.getServiceNode().getSwitchMAC() + ")");
				return false;
			}
		}

		// Set flow rules to redirect traffic through the Service
		// Chain Instance
		try {
			if (! redirectTrafficThroughChain(client, contentProvider, serInstances.get(0), serInstances.get(serInstances.size() - 1), update)) {
				System.err.println("[ERROR] Redirect of traffic through chain failed");
				return false; // Error
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		// Loop over all Service Instances
		for (int i = 0; i < serInstances.size(); i++) {
			ServiceInstance serviceInstance = serInstances.get(i);
			// Connect this service instance to the next one
			if (i != serInstances.size() - 1) {
				try {
					connectServiceChainInstances(client, serviceInstance, serInstances.get(i + 1), update);
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		
		return true;
	}

	/**
	 * Connect two service chain instances that are connected to the same
	 * switch.
	 * @throws Exception if the operation fails
	 */
	private void connectServiceChainInstances1SW(Client client, ServiceInstance serviceInstance, ServiceInstance nextServiceInstance, IOFSwitch of_switch, short egressPortSI, short ingressPortNextSI, boolean update) throws Exception {
		Pair<List<OFAction>, Integer> actions;

		// Rule for SI to NextSI

		// get actions for SI.egress
		List<OFAction> siEgressOut = new ArrayList<OFAction>();
		// int siEgressOutLength = 0;

		actions = getSIEgressOutActions(serviceInstance, client);
		siEgressOut.addAll(actions.getFirst());
		int siEgressOutLength = actions.getSecond();

		// get actions for NextSI.ingress
		List<OFAction> nextSIIngressIn = new ArrayList<OFAction>();
		// int nextSIEgressInLength = 0;

		actions = getSIIngressInActions(nextServiceInstance, client);
		nextSIIngressIn.addAll(actions.getFirst());
		int nextSIIngressInLength = actions.getSecond();

		// Create list for actual rules
		List<OFAction> actionsSItoNextSI = new ArrayList<OFAction>();
		int actionLengthSItoNextSI = OFFlowMod.MINIMUM_LENGTH;

		// Merge egress out and ingress in and egress in and ingress out
		actionsSItoNextSI.addAll(siEgressOut);
		actionLengthSItoNextSI += siEgressOutLength;
		actionsSItoNextSI.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(nextServiceInstance.getIngressMAC())));
		actionLengthSItoNextSI += OFActionDataLayerDestination.MINIMUM_LENGTH;
		actionsSItoNextSI.addAll(nextSIIngressIn);
		actionLengthSItoNextSI += nextSIIngressInLength;

		// find outgoing Port on start switch
		actionsSItoNextSI.add(new OFActionOutput(ingressPortNextSI));
		actionLengthSItoNextSI += OFActionOutput.MINIMUM_LENGTH;

		// set matcher on this switch for flows in the incoming interface
		OFMatch egressOutMatch = OFUtil.createMatch(egressPortSI, null, null, null, null, null, null, null, null, null, null);
		// send flow rules to switches
		generateFlowRule(client, of_switch, egressOutMatch, actionsSItoNextSI, actionLengthSItoNextSI, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);

		// Rule for NextSI to SI
		List<OFAction> nextSIIngressOut = new ArrayList<OFAction>();
		actions = getSIIngressOutActions(nextServiceInstance, client);
		nextSIIngressOut.addAll(actions.getFirst());
		int nextSIIngressOutLength = actions.getSecond();

		List<OFAction> siEgressIn = new ArrayList<OFAction>();
		actions = getSIEgressInActions(serviceInstance, client);
		siEgressIn.addAll(actions.getFirst());
		int siEgressInLength = actions.getSecond();

		// Create list for actual rules
		List<OFAction> actionsNextSItoSI = new ArrayList<OFAction>();
		int actionLengthNextSItoSI = OFFlowMod.MINIMUM_LENGTH;

		actionsNextSItoSI.addAll(nextSIIngressOut);
		actionLengthNextSItoSI += nextSIIngressOutLength;
		actionsNextSItoSI.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(serviceInstance.getEgressMAC())));
		actionLengthNextSItoSI += OFActionDataLayerDestination.MINIMUM_LENGTH;
		actionsNextSItoSI.addAll(siEgressIn);
		actionLengthNextSItoSI += siEgressInLength;

		// find outgoing Port on last switch
		actionsNextSItoSI.add(new OFActionOutput(egressPortSI));
		actionLengthNextSItoSI += OFActionOutput.MINIMUM_LENGTH;

		// Rule for NextSI to SI
		OFMatch matchReturn = OFUtil.createMatch(ingressPortNextSI, null, null, null, null, null, null, null, null, null, null);
		generateFlowRule(client, of_switch, matchReturn, actionsNextSItoSI, actionLengthNextSItoSI, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);
	}

	/**
	 * Connect two service chain instances that are connected to two different
	 * switches.
	 * @throws Exception if the operation fails
	 */
	private void connectServiceChainInstances2SWSItoNextSI(Client client, ServiceInstance serviceInstance, String nextServiceInstanceIngressMAC, IOFSwitch of_switch, short egressPort, short upStreamPort, boolean update) throws Exception {
		Pair<List<OFAction>, Integer> actions;

		// EGRESS IN
		OFMatch matchEgressIn = OFUtil.createMatch(null, null, serviceInstance.getEgressMAC(), null, null, null, null, null, null, null, null);

		List<OFAction> siEgressIn = new ArrayList<OFAction>();
		int siEgressInLength = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIEgressInActions(serviceInstance, client);
		siEgressIn.addAll(actions.getFirst());
		siEgressInLength += actions.second;

		siEgressIn.add(new OFActionOutput(egressPort));
		siEgressInLength += OFActionOutput.MINIMUM_LENGTH;

		generateFlowRule(client, of_switch, matchEgressIn, siEgressIn, siEgressInLength, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// EGRESS OUT
		OFMatch matchEgressOut = OFUtil.createMatch(egressPort, null, null, null, null, null, null, null, null, null, null);

		List<OFAction> siEgressOut = new ArrayList<OFAction>();
		int siEgressOutLength = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIEgressOutActions(serviceInstance, client);
		siEgressOut.addAll(actions.getFirst());
		siEgressOutLength += actions.second;

		siEgressOut.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(nextServiceInstanceIngressMAC)));
		siEgressOutLength += OFActionDataLayerDestination.MINIMUM_LENGTH;
		siEgressOut.add(new OFActionOutput(upStreamPort));
		siEgressOutLength += OFActionOutput.MINIMUM_LENGTH;

		generateFlowRule(client, of_switch, matchEgressOut, siEgressOut, siEgressOutLength, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);
	}

	/**
	 * Connect two service chain instances that are connected to two different
	 * switches.
	 * @throws Exception if the operation fails
	 */
	private void connectServiceChainInstances2SWNextSItoSI(Client client, ServiceInstance serviceInstance, String serviceInstanceEgressMAC, IOFSwitch of_switch, short ingressPort, short upStreamPort, boolean update) throws Exception {
		Pair<List<OFAction>, Integer> actions;

		// INGRESS IN
		OFMatch matchIngressIn = OFUtil.createMatch(null, null, serviceInstance.getIngressMAC(), null, null, null, null, null, null, null, null);

		List<OFAction> siIngressIn = new ArrayList<OFAction>();
		int siIngressInLength = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIIngressInActions(serviceInstance, client);
		siIngressIn.addAll(actions.getFirst());
		siIngressInLength += actions.second;

		siIngressIn.add(new OFActionOutput(ingressPort));
		siIngressInLength += OFActionOutput.MINIMUM_LENGTH;

		generateFlowRule(client, of_switch, matchIngressIn, siIngressIn, siIngressInLength, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// INGRESS OUT
		OFMatch matchIngressOut = OFUtil.createMatch(ingressPort, null, null, null, null, null, null, null, null, null, null);

		List<OFAction> siIngressOut = new ArrayList<OFAction>();
		int siIngressOutLength = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIIngressOutActions(serviceInstance, client);
		siIngressOut.addAll(actions.getFirst());
		siIngressOutLength += actions.second;

		siIngressOut.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(serviceInstanceEgressMAC)));
		siIngressOutLength += OFActionDataLayerDestination.MINIMUM_LENGTH;
		siIngressOut.add(new OFActionOutput(upStreamPort));
		siIngressOutLength += OFActionOutput.MINIMUM_LENGTH;

		siIngressOut.add(new OFActionOutput(upStreamPort));
		siIngressOutLength += OFActionOutput.MINIMUM_LENGTH;

		generateFlowRule(client, of_switch, matchIngressOut, siIngressOut, siIngressOutLength, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);
	}

	/**
	 * generates a flow rule on the switch of the current service instance which
	 * rewrites the macdst to the mac of the next service instance inport
	 * 
	 * @param client client the rules will be written for
	 * @param serviceInstance first Service Instance
	 * @param nextServiceInstance next Service Instance
	 * @throws Exception if the operation fails
	 */
	private void connectServiceChainInstances(Client client, ServiceInstance serviceInstance, ServiceInstance nextServiceInstance, boolean update) throws Exception {

		// get current switch and OutPort of the serviceInstance to the switch
		IOFSwitch startSW = floodlightProvider.getSwitches().get(Long.valueOf(serviceInstance.getServiceNode().getSwitchMAC()));
		short inPortOnSW = startSW.getPort(serviceInstance.getEgressSwitchInterface()).getPortNumber();
		IOFSwitch destinationSW = floodlightProvider.getSwitches().get(Long.valueOf(nextServiceInstance.getServiceNode().getSwitchMAC()));
		short outPortOnSW = destinationSW.getPort(nextServiceInstance.getIngressSwitchInterface()).getPortNumber();

		if (startSW.equals(destinationSW)) {
			connectServiceChainInstances1SW(client, serviceInstance, nextServiceInstance, startSW, inPortOnSW, outPortOnSW, update);
		} else {
			// Calculate path between two service nodes
			List<NodePortTuple> newPath = routingService.getRoute(startSW.getId(), destinationSW.getId()).getPath();
			// Add routing
			installRoutingFlowRules(client, newPath, nextServiceInstance.getIngressMAC(), serviceInstance.getEgressMAC(), update);

			// find outgoing Port on start switch
			short startSWUpstreamPort = newPath.get(0).getPortId();
			// find outgoing Port on last switch
			short destinationSWUpstreamPort = newPath.get(newPath.size() - 1).getPortId();

			connectServiceChainInstances2SWSItoNextSI(client, serviceInstance, nextServiceInstance.getIngressMAC(), startSW, inPortOnSW, startSWUpstreamPort, update);
			connectServiceChainInstances2SWNextSItoSI(client, nextServiceInstance, serviceInstance.getEgressMAC(), destinationSW, outPortOnSW, destinationSWUpstreamPort, update);
		}
	}

	/**
	 * install the required flow rules in the route
	 * 
	 * @param srcSW source switch
	 * @param destPort destination switch port
	 * @throws Exception if the operation fails
	 */
	private void installRoutingFlowRules(Client client, List<NodePortTuple> path, String dstSW, String scrSW, boolean update) throws Exception {
		// matcher
		// TODO: Match input port
		OFMatch matchTowards;// = OFUtil.createMatch(null, null, dstSW, null,
								// null, null, null, null, null, null, null);
		OFMatch matchReturn;// = OFUtil.createMatch(null, null, scrSW, null,
							// null, null, null, null, null, null, null);
		short inputPort;

		// towards path
		for (int i = 2; i < path.size(); i = i + 2) {
			NodePortTuple npt = path.get(i);

			IOFSwitch sw = floodlightProvider.getSwitches().get(npt.getNodeId());

			List<OFAction> actions = new ArrayList<OFAction>();
			int actionLength = OFFlowMod.MINIMUM_LENGTH;
			actions.add(new OFActionOutput(npt.getPortId()));
			actionLength += OFActionOutput.MINIMUM_LENGTH;

			inputPort = path.get(i - 1).getPortId();
			matchTowards = OFUtil.createMatch(inputPort, null, dstSW, null, null, null, null, null, null, null, null);

			generateFlowRule(client, sw, matchTowards, actions, actionLength, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		}
		// return path
		for (int i = 1; i < path.size() - 1; i = i + 2) {
			NodePortTuple npt = path.get(i);

			IOFSwitch sw = floodlightProvider.getSwitches().get(npt.getNodeId());

			List<OFAction> actions = new ArrayList<OFAction>();
			int actionLength = OFFlowMod.MINIMUM_LENGTH;
			actions.add(new OFActionOutput(npt.getPortId()));
			actionLength += OFActionOutput.MINIMUM_LENGTH;

			inputPort = path.get(i + 1).getPortId();
			matchReturn = OFUtil.createMatch(inputPort, null, scrSW, null, null, null, null, null, null, null, null);

			generateFlowRule(client, sw, matchReturn, actions, actionLength, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);
		}
	}

	/**
	 * assigns a user to the chain and install flow rules on the incoming and
	 * outgoing switch
	 * 
	 * @param client
	 * @param provider
	 * @param firstServiceInstance
	 * @param lastServiceInstance
	 * @throws Exception 
	 */
	private boolean redirectTrafficThroughChain(Client client, ContentProvider provider, ServiceInstance firstServiceInstance, ServiceInstance lastServiceInstance, boolean update) throws Exception {

		if (!connectClientToFirstSI(client, firstServiceInstance, update)) {
			System.err.println("[INFO] ### REDIRECT NOT succeeded (PROBLEM connection Client<->firstSI). ###");
			return false;
		}
		if (!connectLastSIToProvider(client, provider, lastServiceInstance, update)) {
			System.err.println("[INFO] ### REDIRECT NOT succeeded (PROBLEM connection lastSI<->Provider). ###");
			return false;
		}
		System.err.println("[INFO] ### REDIRECT succeeded. ###");
		return true;
	}

	private boolean connectClientToFirstSI(Client client, ServiceInstance firstServiceInstance, boolean update) throws Exception {

		// Get all switches
		IOFSwitch clientSwitch = floodlightProvider.getSwitches().get(Long.valueOf(client.getSwitchMAC()));
		if (clientSwitch == null) {
			System.err.println("[ERROR] Client switch " + client.getSwitchMAC() + " not ready!");
			return false; // Client switch is not ready
		}

		List<ServiceNode> serviceNodes = ServiceFunctionChainingController.getEntityStorage().list(ServiceNode.class);
		// Query the registered Service Nodes for the switches
		for (ServiceNode sn : serviceNodes) {
			IOFSwitch snSwitch = floodlightProvider.getSwitches().get(Long.valueOf(sn.getSwitchMAC()));
			if (snSwitch == null) {
				System.err.println("[ERROR] Switch " + sn.getSwitchMAC() + " of Service Node " + sn.getId() + " (" + sn.getTag() + ") not ready!");
				return false; // Registered switch is not ready
			}
		}

		IOFSwitch firstSwitchOfChain = floodlightProvider.getSwitches().get(Long.valueOf(firstServiceInstance.getServiceNode().getSwitchMAC()));
		if (firstSwitchOfChain == null) {
			System.err.println("[ERROR] First Service Chain switch " + firstServiceInstance.getServiceNode().getSwitchMAC() + " at Service Node " + firstServiceInstance.getServiceNode().getId() + " (" + firstServiceInstance.getServiceNode().getTag() + ") not ready!");
			return false; // First Service Chain switch is not ready
		}

		Route clientToChainRoute = routingService.getRoute(clientSwitch.getId(), firstSwitchOfChain.getId());
		Route chainToClientRoute = routingService.getRoute(firstSwitchOfChain.getId(), clientSwitch.getId());

		if (clientToChainRoute == null) {
			System.err.println("[ERROR] Client > Chain route is not ready!");
			return false;
		}
		if (chainToClientRoute == null) {
			System.err.println("[ERROR] Client < Chain route is not ready!");
			return false;
		}

		System.err.println("[INFO] *** Pre-checks client<->firstSI finished. ***");

		List<NodePortTuple> clientToChainPath = clientToChainRoute.getPath();
		List<NodePortTuple> chainToClientPath = chainToClientRoute.getPath();

		// Traffic to Client @ Client switch
		short clientPort = Short.valueOf(client.getSwitchInterface());
		OFMatch matchClient = OFUtil.createMatch(null, null, client.getEntryHopMAC(), Ethernet.TYPE_IPv4, null, null, null, IPv4.toIPv4Address(client.getIP()), null, null, null);
		List<OFAction> actionsClient = new ArrayList<OFAction>();
		int actionLengthClient = OFFlowMod.MINIMUM_LENGTH;
		actionsClient.add(new OFActionOutput(clientPort));
		actionLengthClient += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, clientSwitch, matchClient, actionsClient, actionLengthClient, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// Traffic to SI @ SI switch
		short inPortSI = firstSwitchOfChain.getPort(firstServiceInstance.getIngressSwitchInterface()).getPortNumber();
		OFMatch matchInSI = OFUtil.createMatch(null, null, firstServiceInstance.getIngressMAC(), null, null, null, null, null, null, null, null);
		List<OFAction> actionsInSI = new ArrayList<OFAction>();
		int actionLengthInSI = OFFlowMod.MINIMUM_LENGTH;

		Pair<List<OFAction>, Integer> actions = getSIIngressInActions(firstServiceInstance, client);
		actionsInSI.addAll(actions.getFirst());
		actionLengthInSI += actions.getSecond();

		actionsInSI.add(new OFActionOutput(inPortSI));
		actionLengthInSI += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, firstSwitchOfChain, matchInSI, actionsInSI, actionLengthInSI, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// Client OUTGOING traffic @ Client switch (-> first Service Instance)
		OFMatch matchClientToChain = OFUtil.createMatch(clientPort, null, null, Ethernet.TYPE_IPv4, null, null, IPv4.toIPv4Address(client.getIP()), null, null, null, null);
		List<OFAction> actionsClientToChain = new ArrayList<OFAction>();
		int actionLengthClientToChain = OFFlowMod.MINIMUM_LENGTH;
		actionsClientToChain.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(firstServiceInstance.getIngressMAC())));
		actionLengthClientToChain += OFActionDataLayerDestination.MINIMUM_LENGTH;

		actionsClientToChain.add(new OFActionOutput(clientToChainPath.get(0).getPortId()));
		actionLengthClientToChain += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, clientSwitch, matchClientToChain, actionsClientToChain, actionLengthClientToChain, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// First Service Chain -> Client @ Service Chain Start Switch
		// (Backtraffic from Content Provider)
		short ingressPortChain = firstSwitchOfChain.getPort(firstServiceInstance.getIngressSwitchInterface()).getPortNumber();
		OFMatch matchChainToClient = OFUtil.createMatch(ingressPortChain, null, null, null, null, null, null, null, null, null, null);
		List<OFAction> actionsChainToClient = new ArrayList<OFAction>();
		int actionLengthChainToClient = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIIngressOutActions(firstServiceInstance, client);
		actionsChainToClient.addAll(actions.getFirst());
		actionLengthChainToClient += actions.second;

		actionsChainToClient.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(client.getEntryHopMAC())));
		actionLengthChainToClient += OFActionDataLayerDestination.MINIMUM_LENGTH;

		actionsChainToClient.add(new OFActionOutput(chainToClientPath.get(0).getPortId()));
		actionLengthChainToClient += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, firstSwitchOfChain, matchChainToClient, actionsChainToClient, actionLengthChainToClient, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);

		installRoutingFlowRules(client, clientToChainPath, firstServiceInstance.getIngressMAC(), client.getEntryHopMAC(), update);

		System.err.println("[INFO] ### client<->fistSI connected. ###");
		return true;
	}

	// TODO FIXME should work without client
	private boolean connectLastSIToProvider(Client client, ContentProvider provider, ServiceInstance lastServiceInstance, boolean update) throws Exception {

		IOFSwitch providerSwitch = floodlightProvider.getSwitches().get(Long.valueOf(provider.getSwitchMAC()));
		if (providerSwitch == null) {
			System.err.println("[ERROR] Provider switch " + provider.getSwitchMAC() + " not ready!");
			return false; // Provider switch is not ready
		}

		List<ServiceNode> serviceNodes = ServiceFunctionChainingController.getEntityStorage().list(ServiceNode.class);
		// Query the registered Service Nodes for the switches
		for (ServiceNode sn : serviceNodes) {
			IOFSwitch snSwitch = floodlightProvider.getSwitches().get(Long.valueOf(sn.getSwitchMAC()));
			if (snSwitch == null) {
				System.err.println("[ERROR] Switch " + sn.getSwitchMAC() + " of Service Node " + sn.getId() + " (" + sn.getTag() + ") not ready!");
				return false; // Registered switch is not ready
			}
		}

		IOFSwitch lastSwitchOfChain = floodlightProvider.getSwitches().get(Long.valueOf(lastServiceInstance.getServiceNode().getSwitchMAC()));
		if (lastSwitchOfChain == null) {
			System.err.println("[ERROR] Last Service Chain switch " + lastServiceInstance.getServiceNode().getSwitchMAC() + " at Service Node " + lastServiceInstance.getServiceNode().getId() + " (" + lastServiceInstance.getServiceNode().getTag() + ") not ready!");
			return false; // Last Service Chain switch is not ready
		}

		Route providerToChainRoute = routingService.getRoute(providerSwitch.getId(), lastSwitchOfChain.getId());
		Route chainToProviderRoute = routingService.getRoute(lastSwitchOfChain.getId(), providerSwitch.getId());

		if (providerToChainRoute == null) {
			System.err.println("[ERROR] Provider > Chain route is not ready!");
			System.err.println("        Details: "+providerSwitch.getId()+" to here "+lastSwitchOfChain.getId());
			return false;
		}
		if (chainToProviderRoute == null) {
			System.err.println("[ERROR] Provider < Chain route is not ready!");
			return false;
		}

		System.err.println("[INFO] *** Pre-checks lastSI<->Provider finished. ***");

		List<NodePortTuple> providerToChainPath = providerToChainRoute.getPath();
		List<NodePortTuple> returnChainToProvider = chainToProviderRoute.getPath();

		// Traffic to Provider @ Provider switch (absolute static?)
		short providerPort = Short.valueOf(provider.getSwitchInterface());
		OFMatch matchProvider = OFUtil.createMatch(null, null, provider.getMAC(), null, null, null, null, null, null, null, null);
		// OFMatch matchProvider = OFUtil.createMatch(null, null,
		// provider.getMAC(), Ethernet.TYPE_IPv4, null, null, null,
		// IPv4.toIPv4Address(provider.getIP()), null, null, null);
		List<OFAction> actionsProvider = new ArrayList<OFAction>();
		int actionLengthProvider = OFFlowMod.MINIMUM_LENGTH;
		// TODO add
		// actionsClientToChain.add(new
		// OFActionDataLayerSource(Ethernet.toMACAddress(client.getMAC())));
		// actionLengthClientToChain += OFActionDataLayerSource.MINIMUM_LENGTH;
		actionsProvider.add(new OFActionOutput(providerPort));
		actionLengthProvider += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, providerSwitch, matchProvider, actionsProvider, actionLengthProvider, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// Traffic to SI @ SI switch
		// short outPortSI =
		// Short.valueOf(lastServiceInstance.getEgressSwitchInterface());
		short siEgressPort = lastSwitchOfChain.getPort(lastServiceInstance.getEgressSwitchInterface()).getPortNumber();
		OFMatch matchSIEgressIn = OFUtil.createMatch(null, null, lastServiceInstance.getEgressMAC(), null, null, null, null, null, null, null, null);
		List<OFAction> actionsSIEgressIn = new ArrayList<OFAction>();
		int actionSIEgressInLength = OFFlowMod.MINIMUM_LENGTH;

		// actionsOutSI.addAll(getSIEgressInActions(lastServiceInstance,
		// client));
		Pair<List<OFAction>, Integer> actions = getSIEgressInActions(lastServiceInstance, client);
		actionsSIEgressIn.addAll(actions.getFirst());
		actionSIEgressInLength += actions.second;

		actionsSIEgressIn.add(new OFActionOutput(siEgressPort));
		actionSIEgressInLength += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, lastSwitchOfChain, matchSIEgressIn, actionsSIEgressIn, actionSIEgressInLength, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// Content Provider -> Last element of Service Chain @ Provider Switch
		OFMatch matchProviderToChain = OFUtil.createMatch(providerPort, null, null, Ethernet.TYPE_IPv4, null, null, null, IPv4.toIPv4Address(client.getIP()), null, null, null);
		List<OFAction> actionsProviderToChain = new ArrayList<OFAction>();
		int actionLengthProviderToChain = OFFlowMod.MINIMUM_LENGTH;
		actionsProviderToChain.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(lastServiceInstance.getEgressMAC())));
		actionLengthProviderToChain += OFActionDataLayerDestination.MINIMUM_LENGTH;
		// TODO add
		// actionsClientToChain.add(new
		// OFActionDataLayerSource(Ethernet.toMACAddress(client.getMAC())));
		// actionLengthClientToChain += OFActionDataLayerSource.MINIMUM_LENGTH;
		actionsProviderToChain.add(new OFActionOutput(providerToChainPath.get(0).getPortId()));
		actionLengthProviderToChain += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, providerSwitch, matchProviderToChain, actionsProviderToChain, actionLengthProviderToChain, true, update ? ClientPriorityType.UPDATE : ClientPriorityType.DEFAULT);

		// Last element of Service Chain -> Content Provider (rule on OVS)
		short egressPortChain = lastSwitchOfChain.getPort(lastServiceInstance.getEgressSwitchInterface()).getPortNumber();
		OFMatch matchChainToProvider = OFUtil.createMatch(egressPortChain, null, null, null, null, null, null, null, null, null, null);
		List<OFAction> actionsChainToProvider = new ArrayList<OFAction>();
		int actionLengthChainToProvider = OFFlowMod.MINIMUM_LENGTH;

		actions = getSIEgressOutActions(lastServiceInstance, client);
		actionsChainToProvider.addAll(actions.getFirst());
		actionLengthChainToProvider += actions.second;

		actionsChainToProvider.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(provider.getMAC())));
		actionLengthChainToProvider += OFActionDataLayerDestination.MINIMUM_LENGTH;
		// TODO add
		// actionsClientToChain.add(new
		// OFActionDataLayerSource(Ethernet.toMACAddress(client.getMAC())));
		// actionLengthClientToChain += OFActionDataLayerSource.MINIMUM_LENGTH;

		actionsChainToProvider.add(new OFActionOutput(returnChainToProvider.get(0).getPortId()));
		actionLengthChainToProvider += OFActionOutput.MINIMUM_LENGTH;
		generateFlowRule(client, lastSwitchOfChain, matchChainToProvider, actionsChainToProvider, actionLengthChainToProvider, true, update ? ClientPriorityType.ELEVATED_UPDATE : ClientPriorityType.ELEVATED);

		installRoutingFlowRules(client, returnChainToProvider, provider.getMAC(), lastServiceInstance.getEgressMAC(), update);

		System.err.println("[INFO] ### lastSI<->Provider connected. ###");
		return true;
	}

	public Pair<List<OFAction>, Integer> getSIIngressInActions(ServiceInstance instance, Client client) {
		List<OFAction> actions = new ArrayList<OFAction>();
		int actionLength = 0;

		if (instance.getServiceFunction().getIngressPacketL2Destination().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			// Should not be needed, the packet should already have the correct
			// MAC address
		} else if (instance.getServiceFunction().getIngressPacketL2Destination().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			actions.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(client.getServiceChainInstance().getContentProvider().getMAC())));
			actionLength += OFActionDataLayerDestination.MINIMUM_LENGTH;
		}

		if (instance.getServiceFunction().getIngressPacketL3Destination().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionNetworkLayerDestination(IPv4.toIPv4Address(instance.getIngressIP())));
			actionLength += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getIngressPacketL3Destination().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			// Nothing to be done
		}

		return new Pair<List<OFAction>, Integer>(actions, actionLength);
	}

	public Pair<List<OFAction>, Integer> getSIIngressOutActions(ServiceInstance instance, Client client) {
		List<OFAction> actions = new ArrayList<OFAction>();
		int actionLength = 0;

		if (instance.getServiceFunction().getIngressPacketL2Destination().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionDataLayerSource(Ethernet.toMACAddress(client.getServiceChainInstance().getContentProvider().getMAC())));
			actionLength += OFActionDataLayerSource.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getIngressPacketL2Destination().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			// Should not be needed, the packet should already have the correct
			// MAC address
		}

		if (instance.getServiceFunction().getIngressPacketL3Destination().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionNetworkLayerSource(IPv4.toIPv4Address(instance.getIngressIP())));
			actionLength += OFActionNetworkLayerSource.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getIngressPacketL3Destination().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			//
		}

		return new Pair<List<OFAction>, Integer>(actions, actionLength);
	}

	public Pair<List<OFAction>, Integer> getSIEgressOutActions(ServiceInstance instance, Client client) {
		List<OFAction> actions = new ArrayList<OFAction>();
		int actionLength = 0;

		if (instance.getServiceFunction().getEgressPacketL2Source().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			//
		} else if (instance.getServiceFunction().getEgressPacketL2Source().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			//
		}

		if (instance.getServiceFunction().getEgressPacketL3Source().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionNetworkLayerSource(IPv4.toIPv4Address(client.getIP())));
			actionLength += OFActionNetworkLayerSource.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getEgressPacketL3Source().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			//
		}

		return new Pair<List<OFAction>, Integer>(actions, actionLength);
	}

	public Pair<List<OFAction>, Integer> getSIEgressInActions(ServiceInstance instance, Client client) {
		List<OFAction> actions = new ArrayList<OFAction>();
		int actionLength = 0;

		if (instance.getServiceFunction().getEgressPacketL2Source().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(instance.getEgressMAC())));
			actionLength += OFActionDataLayerDestination.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getEgressPacketL2Source().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			actions.add(new OFActionDataLayerDestination(Ethernet.toMACAddress(client.getEntryHopMAC())));
			actionLength += OFActionDataLayerDestination.MINIMUM_LENGTH;
		}

		if (instance.getServiceFunction().getEgressPacketL3Source().equals(Constants.ADDRESS_REWRITE_MODE_INSTANCE)) {
			actions.add(new OFActionNetworkLayerDestination(IPv4.toIPv4Address(instance.getEgressIP())));
			actionLength += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
		} else if (instance.getServiceFunction().getEgressPacketL3Source().equals(Constants.ADDRESS_REWRITE_MODE_REAL)) {
			//
		}

		return new Pair<List<OFAction>, Integer>(actions, actionLength);
	}
}
