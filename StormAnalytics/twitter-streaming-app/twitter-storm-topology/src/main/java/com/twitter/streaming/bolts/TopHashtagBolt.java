package com.twitter.streaming.bolts;

import backtype.storm.Config;
import backtype.storm.Constants;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Bolt implementation to return Top N hashtags given a certain time frame.
 */
public class TopHashtagBolt extends BaseBasicBolt {
    List<List> rankings = new ArrayList<>();
    private static final Logger LOG = LoggerFactory.getLogger(TopHashtagBolt.class);
    private static final Integer TOPN = 20;
    private static final Integer TICK_FREQUENCY = 10;

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
        if (isTickTuple(tuple)) {
            LOG.debug("Tick: " + rankings);
            collector.emit(new Values(new ArrayList(rankings)));
        } else {
            rank(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("tophashtags"));
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        Config conf = new Config();
        conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, TICK_FREQUENCY);
        return conf;
    }

    private void rank(Tuple tuple) {
        String hashtag = tuple.getStringByField("hashtag");
        Integer existingIndex = lookup(hashtag);
        if (existingIndex != null)
            rankings.set(existingIndex, tuple.getValues());
        else
            rankings.add(tuple.getValues());

        Collections.sort(rankings, (Comparator<? super List>) new Comparator<List<Object>>() {
            @Override
            public int compare(List o1, List o2) {
                return compareRanking(o1, o2);
            }
        });

        shrinkRanking();
    }

    //returns the existing index of hashtag if present else returns null.
    private Integer lookup(String hashtag) {
        for(int i = 0; i < rankings.size(); ++i) {
            String current = (String) rankings.get(i).get(0);
            if (current.equals(hashtag)) {
                return i;
            }
        }
        return null;
    }

    private int compareRanking(List one, List two) {
        long valueOne = (Long) one.get(1);
        long valueTwo = (Long) two.get(1);
        long delta = valueTwo - valueOne;
        if(delta > 0) {
            return 1;
        } else if (delta < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    //keeps check on total size of rankings list. As soon as it's > TOPN then it starts removing the elements 
    //until ranking list size is returned to TOPN.
    private void shrinkRanking() {
        int size = rankings.size();
        if (TOPN >= size) return;
        for (int i = TOPN; i < size; i++) {
            rankings.remove(rankings.size() - 1);
        }
    }

    private static boolean isTickTuple(Tuple tuple) {
        return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
                && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
}
