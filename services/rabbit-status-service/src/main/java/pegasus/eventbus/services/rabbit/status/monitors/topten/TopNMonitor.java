package pegasus.eventbus.services.rabbit.status.monitors.topten;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dashboard.server.metric.Metric;
import dashboard.server.metric.TopNMetric;
import dashboard.server.metric.TrendMetric;
import pegasus.eventbus.services.rabbit.status.monitors.Monitor;

public abstract class TopNMonitor implements Monitor {

	final protected Logger LOG = LoggerFactory.getLogger(this.getClass());
	final protected int maxMetrics = 10;
	final static protected Calendar calendar = Calendar.getInstance();
	
	private Map<String, TrendMetric> previousMetrics = new HashMap<String, TrendMetric>();
	
	@Override
	public Metric getMetric() {
		
		TopNMetric metric = new TopNMetric();
		metric.setLabel(getLabel());
		metric.setMetrics(getMetrics());
		metric.setTime(calendar.getTimeInMillis());
		return metric;
	}

	protected List<TrendMetric> getMetrics(){
		
		MetricSource source = getMetricSource();
		
		ArrayList<TrendMetric> metrics = new ArrayList<TrendMetric>();
		TrendMetric metric = source.getNextMetricGreaterThan(0);
		while(metric != null){
			metrics.add(metric);
			metric = source.getNextMetricGreaterThan(0);
		}
	
		Collections.sort(metrics, new Comparator<TrendMetric>(){
			@Override
			public int compare(TrendMetric arg0, TrendMetric arg1) {
				return arg1.getValue() - arg0.getValue();
			}});

		if(metrics.size() > maxMetrics){
			int leastValueToReturn = metrics.get(maxMetrics -1).getValue();
			for(int i = metrics.size()-1; metrics.get(i).getValue() < leastValueToReturn; i--){
				metrics.remove(i);
			}
		}
		
		synchronized (previousMetrics){
			Map<String, TrendMetric> currentMetrics = new HashMap<String, TrendMetric>();
			for(TrendMetric  m :metrics){
				TrendMetric p = previousMetrics.get(m.getLabel());
				if(p != null && p.getValue() != 0){
					m.setTrend( 100 * (m.getValue() - p.getValue()) / Math.abs(p.getValue()));
				}
				currentMetrics.put(m.getLabel(), m);
				LOG.debug("label: {} Value{} Trend{}", m.getLabel());
			}
			previousMetrics = currentMetrics;
		}
		
		return metrics;	
	}
	
	protected abstract String getLabel();
	protected abstract String getJson();
	protected abstract Pattern getRateFinder();
	
	protected MetricSource getMetricSource() {
		return new MetricSource(){

			private final String json = getJson();
			private final Matcher matcher = getRateFinder().matcher(json);
			
			@Override
			public TrendMetric getNextMetricGreaterThan(int threshold) {
				
				while(matcher.find()){
					String valueString = matcher.group(1);
					String label = matcher.group(2);
					
					LOG.debug("Label: '{}' Value: '{}'", label, valueString);

					int value = 0;
					try{
						value = Integer.parseInt(valueString);
					} catch(Exception e){
						LOG.warn("Could not parse value of '{}' to integer for label '{}'.", valueString, label);
						continue;
					}
					
					if(value <= threshold ) continue;
					
					TrendMetric metric = new TrendMetric();
					metric.setLabel(label);
					metric.setInfo(label);
					metric.setValue(value);
					metric.setTime(calendar.getTimeInMillis());
					return metric;
				}
				return null;
			}
			
		};
	}
	
	protected interface MetricSource{
		TrendMetric getNextMetricGreaterThan(int threshold);
	}
}