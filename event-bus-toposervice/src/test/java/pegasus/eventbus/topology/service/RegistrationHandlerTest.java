package pegasus.eventbus.topology.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import pegasus.eventbus.client.EventManager;
import pegasus.eventbus.client.EventResult;
import pegasus.eventbus.client.SubscriptionToken;
import pegasus.eventbus.topology.TopologyRegistry;
import pegasus.eventbus.topology.events.HeartBeat;
import pegasus.eventbus.topology.events.RegisterClient;
import pegasus.eventbus.topology.events.Registration;
import pegasus.eventbus.topology.events.UnregisterClient;
import pegasus.eventbus.topology.service.ClientRegistry;
import pegasus.eventbus.topology.service.RegistrationHandler;

public class RegistrationHandlerTest {

    private RegistrationHandler registrationHandler;
    @Mock
    private EventManager        eventManager;
    @Mock
    private ClientRegistry      clientRegistry;
    @Mock
    private TopologyRegistry    topologyRegistry;
    @Mock
    private SubscriptionToken   subscriptionToken;

    @Before
    public void beforeEachTest() {
        MockitoAnnotations.initMocks(this);

        registrationHandler = new RegistrationHandler(eventManager, clientRegistry, topologyRegistry);
    }

    @Test
    public void startingTheHandlerShouldSubscribeTheHandler() {
        when(eventManager.subscribe(registrationHandler)).thenReturn(subscriptionToken);
        registrationHandler.start();
        verify(eventManager).subscribe(registrationHandler);
    }

    @Test
    public void stoppingTheHandlerShouldUnsubscribeTheHandler() {
        when(eventManager.subscribe(registrationHandler)).thenReturn(subscriptionToken);
        registrationHandler.start();
        registrationHandler.stop();
        verify(eventManager).unsubscribe(subscriptionToken);
    }

    @Test
    public void getHandledEventTypesTest() {
        List<Class<Registration>> handledEventTypes = Arrays.asList(registrationHandler.getHandledEventTypes());
        assertTrue(handledEventTypes.contains(RegisterClient.class));
        assertTrue(handledEventTypes.contains(UnregisterClient.class));
        assertTrue(handledEventTypes.contains(HeartBeat.class));
           }

    @Test
    public void handleRegisterClientEventTest() {
        String clientName = "clientName";
        String version = "1.0";
        RegisterClient registerEvent = new RegisterClient(clientName, version);
        assertEquals(EventResult.Handled, registrationHandler.handleEvent(registerEvent));
        verify(clientRegistry).registerClient(registerEvent);
    }

    @Test
    public void handleUnregisterClientEventTest() {
        String clientName = "clientName";
        UnregisterClient unregisterEvent = new UnregisterClient(clientName);
        assertEquals(EventResult.Handled, registrationHandler.handleEvent(unregisterEvent));
        verify(clientRegistry).unregisterClient(unregisterEvent);
    }

    @Test
    public void handleHeartBeatEventTest() {
        String clientName = "clientName";
        HeartBeat heartbeatEvent = new HeartBeat(clientName);
        assertEquals(EventResult.Handled, registrationHandler.handleEvent(heartbeatEvent));
        verify(clientRegistry, never()).registerClient(any(RegisterClient.class));
        verify(clientRegistry, never()).unregisterClient(any(UnregisterClient.class));
    }

    @Test
    public void handleNullEventTest() {
        assertEquals(EventResult.Failed, registrationHandler.handleEvent(null));
    }

    @Test
    public void exceptionInHandleEventTest() {
        assertEquals(EventResult.Failed, registrationHandler.handleEvent(null));
    }

}
