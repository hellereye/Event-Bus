package pegasus.esp;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import pegasus.esp.data.ActiveRange;
import pegasus.esp.data.ValueStreams;
import pegasus.esp.data.ValueStreamsDataProvider;
import pegasus.eventbus.client.Envelope;

import com.espertech.esper.client.EventBean;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import eventbus.esp.metric.TopNMetricGenerator;

public class EnvelopeCounter extends EventMonitor {

    int[] defaultperiods = {
            ValueStreams.seconds(30),
            ValueStreams.minutes(1),
            ValueStreams.minutes(5),
            ValueStreams.hours(1)
            };

    public interface EnvelopeRetriever {
        public String retrieve(Envelope e);
        public String key();
        public int[] periods();
        public int getValue(String type, Envelope env);
    }

    Collection<EnvelopeRetriever> counters = Lists.newArrayList();
    private Map<String, ValueStreams> streamsMap = Maps.newHashMap();

    public EnvelopeCounter() {

        counters.add(new EnvelopeRetriever() {
            public String retrieve(Envelope e) { return e.getEventType(); }
            public String key() { return "Event Type"; }
            public int[] periods() { return defaultperiods; }
            public int getValue(String type, Envelope env) { return 1; }
        });

        counters.add(new EnvelopeRetriever() {
            public String retrieve(Envelope e) { return e.getEventType(); }
            public String key() { return "Total Body Length by Type"; }
            public int[] periods() { return defaultperiods; }
            public int getValue(String type, Envelope env) { return env.getBody().length; }
        });


        counters.add(new EnvelopeRetriever() {
            public String retrieve(Envelope e) { return "BodyLength"; }
            public String key() { return "Total Body Length"; }
            public int[] periods() { return defaultperiods; }
            public int getValue(String type, Envelope env) { return env.getBody().length; }
        });

        counters.add(new EnvelopeRetriever() {
            public String retrieve(Envelope e) {
                if (e.getEventType().equals("pegasus.core.search.event.TextSearchEvent")) {
                    return EnvelopeUtils.getBodyValue(e, "queryText");
                }
                return null;
            }
            public String key() { return "Search Query"; }
            public int[] periods() { return defaultperiods; }
            public int getValue(String type, Envelope env) { return 1; }
        });

        boolean countTopics = false;

        if (countTopics) {
            counters.add(new EnvelopeRetriever() {
                public String retrieve(Envelope e) { return e.getTopic(); }
                public String key() { return "Topic"; }
                public int[] periods() { return defaultperiods; }
                public int getValue(String type, Envelope env) { return 1; }
            });
        }

        for (EnvelopeRetriever envelopeRetriever : counters) {
            String type = envelopeRetriever.key();
            ValueStreams valueStreams = new ValueStreams(type);
            streamsMap.put(type, valueStreams);
            for (int per : envelopeRetriever.periods()) {
                String desc = valueStreams.addPeriod(per);
                ValueStreamsDataProvider provider = new ValueStreamsDataProvider(valueStreams, desc);
                TopNMetricGenerator generator = new TopNMetricGenerator();
                generator.setDataProvider(provider);
            }
        }


    }

    public void dumpFreqs() {
        for (EnvelopeRetriever counter : counters) {
            String type = counter.key();
            ValueStreams streams = streamsMap.get(type);
            streams.display();
        }
    }

    @Override
    public InferredEvent receive(EventBean eventBean) {
        Envelope env = (Envelope) eventBean.get("env");
        recordValues(env);
        return null;
    }

    long lastDisplayTime = Long.MIN_VALUE;
    int displayFrequency = ValueStreams.seconds(10);
    int envelopesSeen = 0;

    private void gotEnvelopeCheckForDumping() {
        boolean showFrequencies = false;
//        showFrequencies = true;

        if (showFrequencies) {
            envelopesSeen++;
            long curtime = new Date().getTime();
            if (curtime > lastDisplayTime + displayFrequency) {
                System.out.println("EC: After envelope " + envelopesSeen);
                dumpFreqs();
                lastDisplayTime = curtime;
            }
        }
    }


    private void recordValues(Envelope env) {
        for (EnvelopeRetriever counter : counters) {
            String type = counter.key();
            String item = counter.retrieve(env);
            if (item == null) continue;
            int value = counter.getValue(type, env);
            ValueStreams valueStreams = streamsMap.get(type);
            Date timestamp = env.getTimestamp();
            // If the envelope doesn't have a timestamp, use the current time
            if (timestamp == null) timestamp = new Date();
            long time = timestamp.getTime();
            valueStreams.addValue(item, time, value);
        }
        gotEnvelopeCheckForDumping();
    }

    @Override
    public void registerPatterns(EventStreamProcessor esp) {
        esp.monitor(true, getPattern(), this);
    }

    private String getPattern() {
        return "select env from Envelope as env";
    }
}
