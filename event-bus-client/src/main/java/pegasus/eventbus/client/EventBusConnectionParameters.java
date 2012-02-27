package pegasus.eventbus.client;

public interface EventBusConnectionParameters {

    public static final String USERNAME_PROPERTY                 = "event.bus.username";
    public static final String PASSWORD_PROPERTY                 = "event.bus.password";
    public static final String HOST_PROPERTY                     = "event.bus.host";
    public static final String PORT_PROPERTY                     = "event.bus.port";
    public static final String VHOST_PROPERTY                    = "event.bus.vhost";
    public static final String CONNECTION_RETRY_TIMEOUT_PROPERTY = "event.bus.connectionRetryTimeout";

    public String getValue(String key, String defaultValue);

    public void setValue(String key, String value);

}