package com.berico.tweetstream.handlers;

import java.util.List;

import com.berico.tweetstream.Tweet;
import com.berico.tweetstream.wordcount.WordSplitter;

import pegasus.eventbus.client.EventHandler;
import pegasus.eventbus.client.EventManager;
import pegasus.eventbus.client.EventResult;

public class NaiveTopicCounterTweetHandler implements EventHandler<Tweet> {

	WordSplitter wordSplitter = null;
	List<TopicMatchAggregate> topicMatchAggregates = null;
	EventManager eventManager = null;
	
	public NaiveTopicCounterTweetHandler(EventManager eventManager, List<TopicMatchAggregate> topicMatchAggregates, WordSplitter splitter) {
	
		this.eventManager = eventManager;
		
		this.topicMatchAggregates = topicMatchAggregates;
		
		this.wordSplitter = splitter;
	}

	@SuppressWarnings("unchecked")
	public Class<? extends Tweet>[] getHandledEventTypes() {
		
		return new Class[]{ Tweet.class };
	}

	public EventResult handleEvent(Tweet tweet) {
		
		String[] words = this.wordSplitter.split(tweet.getMessage());
		
		for(TopicMatchAggregate aggregate : this.topicMatchAggregates){
			
			if(aggregate.isTopicMatch(words)){
				
				eventManager.publish(aggregate);
			}
		}
		
		return EventResult.Handled;
	}
}
