package com.mercatis.jms;

import java.util.List;
import com.mercatis.jms.JmsDestination.DestinationType;

public interface JmsConnection {
	public long getSupportedFeatures();
	
	public void addConnectionListener(JmsConnectionListener listener, Object callbackData) throws JmsBrowserException;

	public void removeConnectionListener(JmsConnectionListener listener) throws JmsBrowserException;

	public void addMessageListener(JmsDestination dest, JmsDestinationListener listener) throws JmsBrowserException;

	public void removeMessageListener(JmsDestination dest, JmsDestinationListener listener) throws JmsBrowserException;
	
	public void open() throws JmsBrowserException;
	
	public void close();
	
	public boolean isOpen();
	
	public javax.jms.BytesMessage createBytesMessage() throws JmsBrowserException;
	
	public javax.jms.BytesMessage createBytesMessage(byte[] payload) throws JmsBrowserException;

	public javax.jms.TextMessage createTextMessage() throws JmsBrowserException;

	public javax.jms.TextMessage createTextMessage(String string) throws JmsBrowserException;

	public javax.jms.ObjectMessage createObjectMessage() throws JmsBrowserException;

	public javax.jms.StreamMessage createStreamMessage() throws JmsBrowserException;

	public void deleteMessage(javax.jms.Message message) throws JmsBrowserException;
	
	public void deleteMessage(Object[] message) throws JmsBrowserException;
	
	public List<JmsDestination> getDestinations(DestinationType type) throws JmsBrowserException;
	
	public List<JmsDurableSubscription<?>> getDurableSubscriptions(String topicName) throws JmsBrowserException;
	
	public void purgeDestination(JmsDestination dest) throws JmsBrowserException ;
	
	public void sendMessage(javax.jms.Message message, JmsDestination dest) throws JmsBrowserException;
	
	public void sendMessage(javax.jms.Message message) throws JmsBrowserException;
	
	public javax.jms.Destination createDestination(JmsDestination dest) throws JmsBrowserException;

	public javax.jms.Destination createDestination(javax.jms.Destination dest) throws JmsBrowserException;
	
	public javax.jms.QueueBrowser createQueueBrowser(String queueName) throws JmsBrowserException;
	
	public void createDestinationOnServer(JmsDestination dest) throws JmsBrowserException;
	
	public void deleteDestinationOnServer(JmsDestination dest) throws JmsBrowserException;
	
	public DestinationType[] getSupportedDestinations();
	
	public void addQueueCountListener(JmsDestination dest, MessageCountListener listener);
	
	public void removeQueueCountListener(JmsDestination dest, MessageCountListener listener);

	public void addSubscriberCountListener(JmsDurableSubscription<?> sub, MessageCountListener listener);
	
	public void removeSubscriberCountListener(JmsDurableSubscription<?> sub, MessageCountListener listener);

	public JmsDestination translateDestination(javax.jms.Destination dest);
	
	public void purgeSubscriberMessages(JmsDurableSubscription<?> sub) throws JmsBrowserException;
	
	public JmsConnectionService getConnectionService();

}
