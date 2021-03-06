package pegasus.eventbus.topology;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pegasus.eventbus.amqp.RoutingInfo;
import pegasus.eventbus.amqp.TopologyManager;
import pegasus.eventbus.topology.events.HeartBeat;
import pegasus.eventbus.topology.events.RegisterClient;
import pegasus.eventbus.topology.events.TopologyUpdate;
import pegasus.eventbus.topology.events.UnregisterClient;

import pegasus.eventbus.client.EventHandler;
import pegasus.eventbus.client.EventManager;
import pegasus.eventbus.client.EventResult;
import pegasus.eventbus.client.SubscriptionToken;

//TODO: PEGA-721 This class need test coverage.
public class GlobalTopologyServiceManager implements TopologyManager {

    protected static final Logger LOG              = LoggerFactory.getLogger(GlobalTopologyServiceManager.class);

    private TopologyRegistry      topologyRegistry = new TopologyRegistry();
    private String                clientName;
    private int                   hearbeatIntervalSeconds;
    private EventManager          eventManager;
    private SubscriptionToken     subscriptionToken;

	private ScheduledExecutorService scheduler ;

    public GlobalTopologyServiceManager(String clientName, int hearbeatIntervalSeconds) {

        LOG.info("Instantiating the Global Topology Service Manager.");

        this.clientName = clientName;
        this.hearbeatIntervalSeconds = hearbeatIntervalSeconds;
    }

    @Override
    public void start(EventManager eventManager) {

        LOG.trace("Global Topology Service Manager starting.");

        this.eventManager = eventManager;

        LOG.trace("Subscribing to topology update events.");

        subscriptionToken = eventManager.subscribe(new TopologyUpdateHandler());

        RegisterClient registerClientEvent = new RegisterClient(clientName, topologyRegistry.getVersion());
        try {

            LOG.trace("Registering client {} with Global Topology Service.", clientName);

            @SuppressWarnings("unchecked")
            TopologyUpdate topologyUpdateResponseEvent = eventManager.getResponseTo(registerClientEvent, 5000, TopologyUpdate.class);
            topologyRegistry = topologyUpdateResponseEvent.getTopologyRegistry();
        
            startHeartBeat();
            
    		LOG.debug("Global Topology Service Manager started.");

        } catch (Exception e) {

            // unable to connect with the topo service.
            // this is expected behavior if this is the topo service itself
            // @todo - review
            LOG.error("Error starting Global Topology Service Manager.", e);

        }
    }

    private void startHeartBeat() {

		scheduler = Executors.newScheduledThreadPool(1);
		
		Runnable sender = new Runnable(){
			@Override
			public void run() {
				try{
					LOG.trace("Sending HearBeat.");
					eventManager.publish(new HeartBeat(clientName));
				} catch ( Throwable e){
					LOG.error("Exception occurred attempting to send heartbeat message." , e);
				}
			}};
		
		scheduler.scheduleAtFixedRate(sender, hearbeatIntervalSeconds, hearbeatIntervalSeconds, TimeUnit.SECONDS);

		LOG.debug("HearBeat thread started.");
    }

	@Override
    public void close() {
		
		if(scheduler != null && !scheduler.isShutdown()){
			LOG.trace("Stopping HearBeat thread.");
        	scheduler.shutdownNow();
		}
		
        LOG.trace("Global Topology Service Manager closing.");
        eventManager.unsubscribe(subscriptionToken);
        LOG.trace("Unsubscribed TopologyUpdateHandler.");
        UnregisterClient unregisterClientEvent = new UnregisterClient(clientName);
        LOG.trace("Unregistered client.");
        eventManager.publish(unregisterClientEvent);
        
        LOG.debug("Global Topology Service Manager closed.");
    }

    @Override
    public RoutingInfo getRoutingInfoForEvent(Class<?> eventType) {

        LOG.trace("Looking for route for event type [{}] in global topology service.", eventType.getCanonicalName());

        RoutingInfo route = null;
        String topic = eventType.getCanonicalName();
        if (topologyRegistry.hasEventRoute(topic)) {
            route = topologyRegistry.getEventRoute(topic);

            LOG.trace("Found route [{}] in global topology service.", route);
        }
        return route;
    }

    @Override
    public RoutingInfo[] getRoutingInfoForNamedEventSet(String eventSetName) {

        LOG.trace("Looking for routes for event set name [{}] in global topology service.", eventSetName);

        RoutingInfo[] routes = null;
        if (topologyRegistry.hasEventSetRoutes(eventSetName)) {
            routes = topologyRegistry.getEventSetRoutes(eventSetName);

            LOG.trace("Found routes [{}] in global topology service.", routes);
        }
        return routes;
    }

    public class TopologyUpdateHandler implements EventHandler<TopologyUpdate> {

        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends TopologyUpdate>[] getHandledEventTypes() {
            return new Class[] { TopologyUpdate.class };
        }

        @Override
        public EventResult handleEvent(TopologyUpdate event) {

            LOG.trace("Received topology update.");

            topologyRegistry = event.getTopologyRegistry();
            return EventResult.Handled;
        }

    }

}
