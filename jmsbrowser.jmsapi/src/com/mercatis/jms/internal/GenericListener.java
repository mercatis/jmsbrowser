package com.mercatis.jms.internal;

import java.util.LinkedList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;

import com.mercatis.jms.JmsDestinationListener;

public class GenericListener implements MessageListener {
	Session session;
	MessageConsumer consumer;
	private List<JmsDestinationListener> listeners;
	
	public GenericListener(Connection connection, String topicName) throws JMSException {
		listeners = new LinkedList<JmsDestinationListener>();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Topic topic = session.createTopic(topicName);
		consumer = session.createConsumer(topic);
		consumer.setMessageListener(this);
	}
	
	public void addListener(JmsDestinationListener listener) {
		synchronized(listeners) {
			int idx = listeners.indexOf(listener);
			if (idx>=0)
				listeners.remove(idx);
			listeners.add(listener);
		}
	}
	
	public void removeListener(JmsDestinationListener listener) {
		synchronized(listeners) {
			int idx = listeners.indexOf(listener);
			if (idx>=0)
				listeners.remove(idx);
		}
	}

	public void shutdown() {
		synchronized(listeners) {
			listeners.clear();
		}
		try { consumer.close(); } catch (JMSException e) { e.printStackTrace(); }
		try { session.close(); } catch (JMSException e) { e.printStackTrace(); }
	}
	
	@Override
	public void onMessage(Message msg) {
		synchronized(listeners) {
			for (JmsDestinationListener listener : listeners)
				try {
					listener.onMessage(msg);
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
	}

	public void notifyPurge() {
		synchronized(listeners) {
			for (JmsDestinationListener listener : listeners)
				try {
					listener.onPurge();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
	}

	public boolean isEmpty() {
		return listeners.size()==0;
	}
}
