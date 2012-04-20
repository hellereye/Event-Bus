package gov.ment.esp;

import gov.ment.esp.publish.Publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import com.espertech.esper.client.EventBean;

/**
 * This interface describes a monitor that watches the event stream for patterns
 * of events and produces an InferredEvent.
 * 
 * @author israel
 * 
 */
public abstract class EventMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(EventMonitor.class);

  /**
   * register the patterns to be matched by this monitor with the event stream
   * processor
   */
  public abstract Collection<Publisher> registerPatterns(EventStreamProcessor esp);

  /**
   * Processes the matching event or events and return an inferred event, if any
   */
  public abstract InferredEvent receive(EventBean eventBean);

  public String getInferredType() {
    return null;
  }

  public InferredEvent makeInferredEvent() {
    String inferredType = getInferredType();
    String label = getLabel();
    return new InferredEvent(inferredType, label);
  }

  public String getLabel() {
    return this.getClass().getSimpleName();
  }

  public static int len(EventBean[] events) {
    if (events == null)
      return 0;
    return events.length;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "()";
  }
}
