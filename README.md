# Pegasus Event Bus Client API Guide #

The Pegasus Event Bus is an implementation of a service bus architecture and is the communication backbone of the Pegasus High Performance Computing Environment. The Pegasus Event Bus Client abstracts the details of the underlying architecture and provides application developers with publish/subscribe, queuing, wiretapping, and RPC messaging patterns in a consistent and secure manner.

## Pegasus Event Bus Overview ##

The Pegasus Event Bus utilizes RabbitMQ as the underlying message broker. The messaging protocol implemented by RabbitMQ is the Advanced Message Queuing Protocol (AMQP). It is a wire-level protocol which means that clients can send and receive asynchronous messages regardless of their respective platforms.
Asynchronous messaging decouples the clients from the services. Clients and services are able to communicate by conforming to a loosely defined contract that is the combination of the types of messages and the publish/subscribe methods used to send those message. Implementation details of the client and service technologies are transparent. In fact, applications can be completely replaced as long as they conform to the existing communication contract. 

A basic understanding of the AMQP model, while not entirely necessary, is helpful when developing applications for the Pegasus Event Bus. As the messaging broker, RabbitMQ receives all the messages published to the bus by producers and routes them to their respective consumers. Exchanges are the internal entities which receive published messages. Exchanges route messages to zero or more queues. The routing algorithm used depends on the exchange type and queue bindings. Direct exchanges deliver messages based on the message’s routing key. Fanout exchanges deliver messages to all queues bound to the exchange. Topic exchanges route messages by matching the message’s routing key with a queue’s binding pattern. Queues store messages that are consumed by applications. Queues are bound to exchanges by bindings. Bindings are the rules that exchanges use to route messages to queues. If messages of type M are delivered to exchange E then an application must connect to the bus and subscribe to messages of type M by creating a queue Q and binding it to exchange E with a binding pattern of M.

More details on messaging and RabbitMQ concepts may be found on the RabbitMQ site: http://www.rabbitmq.com

## Pegasus Event Bus Client ##

The Pegasus Event Bus Client abstracts the messaging and protocol details of the bus. The Pegasus Event Bus Client is currently implemented in Java, .NET, Ruby and JavaScript. This document gives an overview of the Java client API. The API is also delivered with detailed Javadoc if more details are necessary.

## Connecting to the Bus ##

The EventManager interface defines the primary API to the event bus. AmqpEventManager is the concrete implementation for the Pegasus Event Bus. The following code connects an application to the bus:

```java
    import pegasus.eventbus.amqp.AmqpEventManager; 
    import pegasus.eventbus.amqp.Configuration; 
    import pegasus.eventbus.amqp.ConnectionParameters;

    ConnectionParameters connectionParameters = new ConnectionParameters();
    connectionParameters.setUsername("guest");
    connectionParameters.setPassword("guest");
    connectionParameters.setVirtualHost("/");
    connectionParameters.setHost("localhost");
    connectionParameters.setPort(5672);
    Configuration configuration = Configuration.getDefault(clientName, connectionParameters);
    eventManager = new AmqpEventManager(configuration);
    //NEW - please 'start' the Event Manager (or you will get NullReferenceErrors out
    //of the topology managers).
    eventManager.start();
```

All of the configuration parameters (clientName, userName, password, virtualHost, hostName and portNumber) are provided by the event bus administrator. Once eventManager is instantiated, a connection is open to the bus and the client is ready to begin publishing or subscribing to messages. 

## Publishing Messages ##

To publish a message call the publish method on eventManager.

```java
    eventManager.publish(requestMessage);
```

This serializes and sends requestMessage to the bus. Any application subscribed to messages of type RequestMessage will receive the message.

## Subscribing to Messages ##

To subscribe to messages call the subscribe method on eventManager. EventManager returns a SubscriptionToken which uniquely identifies the subscription. There are multiple overloaded subscribe methods, the simplest of which expects an event handler parameter. EventHandler is the interface that all handlers must implement.

```java
    import pegasus.eventbus.client.EventHandler;
    import pegasus.eventbus.client.EventManager;
    import pegasus.eventbus.client.EventResult;
    import pegasus.eventbus.client.SubscriptionToken;

    public class RequestMessageHandler implements EventHandler<RequestMessage> {

        @SuppressWarnings("unchecked")
        public Class<? extends RequestMessage>[] getHandledEventTypes() {
            return new Class[] { RequestMessage.class };
        }

        public EventResult handleEvent(RequestMessage requestMessage) {
            try {
                // handle requestMessage
                return EventResult.Handled;
            } catch (Exception e) {
                return EventResult.Failed;
            }
        }
    }
```

getHandledEventTypes returns the list of event types that the handler can process.
handleEvent is called by the Pegasus Event Bus Client when messages matching getHandledEventTypes are received from the bus. Notice that the event handler returns EventResult.Handled on success and EventResult.Failed on failure. Handled
 
Once the handler is in place:

```java
    RequestMessageHandler eventHandler = new RequestMessageHandler();
    SubscriptionToken subscriptionToken = eventManager.subscribe(eventHandler);
```

To unsubscribe from request messages:

```java 
    eventManager.unsubscribe(subscriptionToken);
```

## Disconnecting from the Bus ##

The EventManager close method unsubscribes any non-durable subscriptions and closes the connection to the bus.

```java
    eventManager.close();
```

## Single Message Publish/Subscribe ##

There may be times when subscribe may be more cumbersome than necessary. An example is the initial authentication request. In this case, the application sends a single authentication message to the authentication service and waits for a single response message containing the security token. The convenience method, getResponseTo, simplifies this problem. getResponseTo subscribes to the response message, publishes the request message and, upon receipt of the response, unsubscribes from the response message.

```java
    responseMessage = eventManager.getResponseTo(requestMessage, 30, ResponseMessage.class);
```

The other side of the coin is respondTo. Messages have id’s attached to them and respondTo writes the request message’s id value to the response message’s correlation id field. With this, the receiver knows that the message is the response to the request.

```java 
    eventManager.respondTo(requestMessage, responseMessage);
```

## Advanced Usage ##

TBD – need to discuss what scenarios to present. Handling exceptions, durable queues, custom mappers, etc.

## Coding Examples ##

The first example is a simple chat application. Messages are broadcast to all chat clients.

SimleMessage.java

```java
public class SimpleMessage {
    public String message;
    public String from;
}
```

SimpleClient.java

```java
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.rabbitmq.client.ConnectionFactory;

import pegasus.eventbus.amqp.AmqpEventManager;
import pegasus.eventbus.client.EventHandler;
import pegasus.eventbus.client.EventManager;
import pegasus.eventbus.client.EventResult;
import pegasus.eventbus.client.SubscriptionToken;
import pegasus.eventbus.gson.GsonSerializer;
import pegasus.eventbus.rabbitmq.RabbitMessageBus;
import pegasus.eventbus.routing.BasicTopicToRoutingMapper;
import pegasus.eventbus.routing.BasicTypeToTopicMapper;

public class SimpleClient {

    public static void main(String[] args) {
        new SimpleClient(args[0]);
    }

    private String clientName;
    private EventManager eventManager;
    private SubscriptionToken subscriptionToken;

    public SimpleClient(String clientName) {
        this.clientName = clientName;
        initEventManager();
        startChatSession();
        monitorCommandLine();
    }

    protected void initEventManager() {
        ConnectionParameters connectionParameters = new ConnectionParameters();
        connectionParameters.setUsername("guest");
        connectionParameters.setPassword("guest");
        connectionParameters.setVirtualHost("/");
        connectionParameters.setHost("localhost");
        connectionParameters.setPort(5672);
        Configuration configuration = Configuration.getDefault(clientName, connectionParameters);
        eventManager = new AmqpEventManager(configuration);
        eventManager.start();
    }
        
    protected void startChatSession() {
        subscriptionToken = eventManager.subscribe(new SimpleMessageHandler());
    }
        
    protected void endChatSession() {
        eventManager.close();
    }

    protected void monitorCommandLine() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String line = "";
                SimpleMessage simpleMessage;
                while (true) {
                    try {
                        System.out.print("Send: ");
                        line = reader.readLine();
                        simpleMessage = new SimpleMessage();
                        simpleMessage.from = clientName;
                        simpleMessage.message = line;
                        eventManager.publish(simpleMessage);
                        Thread.sleep(500);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
        
    protected class SimpleMessageHandler implements EventHandler<SimpleMessage> {

        @SuppressWarnings("unchecked")
        public Class<? extends SimpleMessage>[] getHandledEventTypes() {
            return new Class[] { SimpleMessage.class };
        }

        public EventResult handleEvent(SimpleMessage simpleMessage) {
            try {
                if (!simpleMessage.from.equals(clientName)) {
                    String from = simpleMessage.from;
                    String message = simpleMessage.message;
                    String format = "\nChat Message Received [ from=%s message=%s ]\n";
                    String display = String.format(format, from, message);
                    System.out.println(display);
                    return EventResult.Handled;
                } else {
                   return EventResult.Failed;
                }
            } catch (Exception e) {
                return EventResult.Failed;
            }
        }

    }

}

```
