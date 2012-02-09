package pegasus.eventbus.integration_tests;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.httpclient.*;
import org.apache.log4j.Logger;
import org.junit.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import pegasus.eventbus.amqp.AmqpEventManager;
import pegasus.eventbus.amqp.Configuration;
import pegasus.eventbus.amqp.ConnectionParameters;
import pegasus.eventbus.client.EventManager;
import pegasus.eventbus.testsupport.RabbitManagementApiHelper;
import pegasus.eventbus.testsupport.TestSendEvent;

public class IntegrationTestBase {
	
	protected String virtualHostName;
	protected Logger log = Logger.getLogger(this.getClass());
	protected EventManager manager;
	protected ApplicationContext context;
	protected TestSendEvent sendEvent;

	RabbitManagementApiHelper rabbitManagementApi;
	
	@Before
	public void beforeEachTest() throws HttpException, IOException {
		
		FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext("src/test/resources/eventbus-context.xml");

		final ConnectionParameters connectionParameters = context.getBean(ConnectionParameters.class);
		assertFalse("Cannot use default vhost for tests", "/" == connectionParameters.getVirtualHost());
		
		rabbitManagementApi = new RabbitManagementApiHelper(connectionParameters);
		resetVirtualHost();
		
		manager = context.getBean(EventManager.class);
		
    	sendEvent = new TestSendEvent("John Doe", new Date(1324058322000L), 101, "weather","wind","age");
	}

	@After
	public void afterEachTest() throws Exception {
		manager.close();
		// Wait for broker communication to finish before trying to stop container
		Thread.sleep(300L);
		rabbitManagementApi.deleteVirtualHost();
	}
	
	protected void resetVirtualHost() throws HttpException, IOException{
		rabbitManagementApi.deleteVirtualHost();
		rabbitManagementApi.createVirtualHost();
	}
	
	protected void assertExchangeExists() throws HttpException, IOException{
		rabbitManagementApi.assertExchangeExists(getExchangeName());
	}
	
	protected void assertExchangeDoesNotExist() throws HttpException, IOException{
		rabbitManagementApi.assertExchangeDoesNotExist(getExchangeName());
	}
	
	protected void assertQueueExists() throws HttpException, IOException{
		assertEquals(1, rabbitManagementApi.getAllQueueNames().size());
	}
	
	protected void assertQueueDoesNotExists() throws HttpException, IOException{
		assertEquals(0, rabbitManagementApi.getAllQueueNames().size());
	}
	
	protected void assertQeueuHasBindingFor(Class<TestSendEvent> class1) {
		String nameOfFirstQueue = rabbitManagementApi.getAllQueueNames().get(0);  //should only ever be on in the current vhost
		String expectedBinding = class1.getCanonicalName()+".#";
		assertTrue(rabbitManagementApi.getBindingsForQueue(nameOfFirstQueue, true).contains(expectedBinding));
		
	}
	
	protected EventManager createEventManager() {
	    Configuration configuration = new Configuration();
        configuration.setClientName(this.getClass().getSimpleName());
        configuration.setAmqpMessageBus(null);
        configuration.setEventTypeToTopicMapper(null);
        configuration.setTopicToRoutingMapper(null);
        configuration.setSerializer(null);
        
        return new AmqpEventManager(configuration);
	}

	protected String getExchangeName() {
		return "pegasus"; 
	}
 
}

