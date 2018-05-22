package com.mercatis.jms;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.internal.GenericListener;
import com.mercatis.jms.internal.QueueListenerThread;

public abstract class AbstractJmsConnection<F extends javax.jms.ConnectionFactory, C extends javax.jms.Connection, S extends javax.jms.Session> extends TimerTask implements JmsConnection, ExceptionListener {
	final static Integer errResult = Integer.valueOf(-1);
	protected F factory;
	protected C connection;
	protected S session;
	protected Map<String, String> properties;
	private final Map<String, QueueListenerThread> queueListeners = new HashMap<String, QueueListenerThread>();
	private final Map<String, GenericListener> topicListeners = new HashMap<String, GenericListener>();
	private final Map<String, MessageCountListener> queueCountListeners = new HashMap<String, MessageCountListener>();
	private final Map<JmsDurableSubscription<?>, MessageCountListener> subscriptionListeners = new HashMap<JmsDurableSubscription<?>, MessageCountListener>();
	private final Map<DestinationType, List<String>> cachedDestinations = Collections.synchronizedMap(new HashMap<DestinationType, List<String>>());
	private final Map<JmsConnectionListener, Object> connectionListeners = new HashMap<JmsConnectionListener, Object>();
	private Timer t = null;
	private final int timerDelay = 5000;
	private int reconnectSleep = 2500;
	private Boolean isOpen = false;
	private static final String clientID;

	protected final static String USERNAME = "Username";
	protected final static String PASSWORD = "Password";
	protected final static String HOST = "Host";
	protected final static String PORT = "Port";

	static {
		String hostname;
		try {
			final InetAddress addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (final UnknownHostException e) {
			hostname = "localhost";
		}
		clientID = "JmsBrowser-"+System.getProperty("user.name")+"@"+hostname;
	}

	/***********************************************
	 *
	 *  DEFAULT CONSTRUCTOR AND CLEANUP
	 * @throws JmsBrowserException
	 *
	 ***********************************************/
	protected AbstractJmsConnection(final F factory, final Map<String, String> properties) throws JmsBrowserException {
		this.factory = factory;
		this.properties = properties;
		this.t = new Timer(true);
		this.t.schedule(this, 1000, this.timerDelay);
	}

	protected void preOpen(final Map<String, String> properties) throws JmsBrowserException {

	}

	protected abstract void postOpen(Map<String, String> properties) throws JmsBrowserException;

	@Override
	@SuppressWarnings("unchecked")
	public void open() throws JmsBrowserException {
		try {
			synchronized(this.isOpen) {
				if (this.isOpen)
					return;
				this.preOpen(this.properties);
				final String user = this.properties.get(USERNAME);
				final String pass = this.properties.get(PASSWORD);
				this.connection = (C) this.factory.createConnection(user, pass);
				this.connection.setClientID(clientID);
				this.connection.setExceptionListener(this);
				this.connection.start();
				this.session = (S) this.connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
				this.isOpen=true;
				this.postOpen(this.properties);
				for (final Entry<JmsConnectionListener, Object> cl : this.connectionListeners.entrySet())
					cl.getKey().onReconnect(this, cl.getValue());
				for (final QueueListenerThread qlt : this.queueListeners.values())
					qlt.wakeUp();
			}
			synchronized (this.t) {
				this.t.notify();
			}
		} catch (final JMSException e) {
			this.close();
			e.printStackTrace();
			throw new JmsBrowserException("Error creating connection or session.", e);
		}
	}

	protected abstract void preClose();

	protected void postClose() {};

	@Override
	public void close() {
		synchronized(this.isOpen) {
			if (this.isOpen) {
				this.preClose();
				try {
					this.session.close();
				} catch (final JMSException ex) {/*ex.printStackTrace();*/}
				try {
					this.connection.close();
				} catch (final JMSException ex) {/*ex.printStackTrace();*/}
				this.session = null;
				this.connection = null;
				this.isOpen=false;
				try {
					this.postClose();
				} catch (final RuntimeException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	@Override
	public boolean isOpen() {
		synchronized(this.isOpen) {
			return this.isOpen;
		}
	}

	/***********************************************
	 *
	 *  QUEUE THREAD DELAY
	 *
	 ***********************************************/
	protected int getQueueThreadDelay() {
		return 2000;
	}

	/***********************************************
	 *
	 *  LIST OF SUPPORTED TYPES
	 *
	 ***********************************************/
	@Override
	public DestinationType[] getSupportedDestinations() {
		final DestinationType[] response = {DestinationType.TOPIC, DestinationType.QUEUE};
		return response;
	}

	/***********************************************
	 *
	 *  MESSAGE LISTENERS
	 *
	 ***********************************************/
	protected void addQueueListener(final String queueName, final JmsDestinationListener listener) {
		QueueListenerThread qlt;
		if (!this.queueListeners.containsKey(queueName)) {
			qlt = new QueueListenerThread(queueName, this, this.getQueueThreadDelay());
			this.queueListeners.put(queueName, qlt);
		} else {
			qlt = this.queueListeners.get(queueName);
		}
		qlt.addListener(listener);
	}

	protected void addTopicListener(final String topicName, final JmsDestinationListener listener) throws JmsBrowserException {
		GenericListener tl;
		if (!this.topicListeners.containsKey(topicName)) {
			try {
				tl = new GenericListener(this.connection, topicName);
			} catch (final JMSException e) {
				throw new JmsBrowserException("Error adding topic listener.", e);
			}
			this.topicListeners.put(topicName, tl);
		} else {
			tl = this.topicListeners.get(topicName);
		}
		tl.addListener(listener);
	}

	// interface method
	@Override
	public void addMessageListener(final JmsDestination dest, final JmsDestinationListener listener) throws JmsBrowserException {
		switch (dest.type) {
			case QUEUE:
				this.addQueueListener(dest.name, listener);
				break;
			case TOPIC:
				this.addTopicListener(dest.name, listener);
				break;
			default:
				throw new JmsBrowserException("unsupported destination type.");
		}
	}

	protected void removeQueueListener(final String queueName, final JmsDestinationListener listener) throws JmsBrowserException {
		if (!this.queueListeners.containsKey(queueName))
			return;
		final QueueListenerThread qlt = this.queueListeners.get(queueName);
		qlt.removeListener(listener);
	}

	protected void removeTopicListener(final String topicName, final JmsDestinationListener listener) throws JmsBrowserException {
		if (!this.topicListeners.containsKey(topicName))
			return;
		final GenericListener tl = this.topicListeners.get(topicName);
		tl.removeListener(listener);
		if (tl.isEmpty()) {
			tl.shutdown();
			this.topicListeners.remove(topicName);
		}
	}

	// interface method
	@Override
	public void removeMessageListener(final JmsDestination dest, final JmsDestinationListener listener) throws JmsBrowserException {
		switch (dest.type) {
		case QUEUE:
			this.removeQueueListener(dest.name, listener);
			break;
		case TOPIC:
			this.removeTopicListener(dest.name, listener);
			break;
		default:
			throw new JmsBrowserException("unsupported destination type.");
		}
	}

	@Override
	public void addQueueCountListener(final JmsDestination dest, final MessageCountListener listener) {
//		if (queueCountListeners.containsKey(dest))
//			queueCountListeners.p remove(dest);
		this.queueCountListeners.put(dest.name, listener);
	}

	@Override
	public void removeQueueCountListener(final JmsDestination dest, final MessageCountListener listener) {
		if (this.queueCountListeners.containsKey(dest.name))
			this.queueCountListeners.remove(dest.name);
	}

	// queues have no need for onPurge!
	private void notifyPurge(final String topicName) {
		if (!this.topicListeners.containsKey(topicName))
			return;
		final GenericListener tl = this.topicListeners.get(topicName);
		tl.notifyPurge();
	}

	@Override
	public void addSubscriberCountListener(final JmsDurableSubscription<?> sub, final MessageCountListener listener) {
		this.subscriptionListeners.put(sub, listener);
	}

	@Override
	public void removeSubscriberCountListener(final JmsDurableSubscription<?> sub, final MessageCountListener listener) {
		if (this.subscriptionListeners.containsKey(sub))
			this.subscriptionListeners.remove(sub);
	}

	/***********************************************
	 *
	 *  MESSAGE CREATION
	 *
	 ***********************************************/
	// message creation
	@Override
	public BytesMessage createBytesMessage() throws JmsBrowserException {
		return this.createBytesMessage(null);
	}

	@Override
	public BytesMessage createBytesMessage(final byte[] payload) throws JmsBrowserException {
		try {
			final BytesMessage message = this.session.createBytesMessage();
			if (payload!=null && payload.length>0)
				message.writeBytes(payload);
			return message;
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating BytesMessage.", e);
		}
	}

	@Override
	public TextMessage createTextMessage() throws JmsBrowserException {
		return this.createTextMessage(null);
	}

	@Override
	public TextMessage createTextMessage(final String payload) throws JmsBrowserException {
		try {
			final TextMessage message = this.session.createTextMessage();
			if (payload!=null)
				message.setText(payload);
			return message;
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating TextMessage.", e);
		}
	}

	@Override
	public ObjectMessage createObjectMessage() throws JmsBrowserException {
		try {
			return this.session.createObjectMessage();
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating ObjectMessage.", e);
		}
	}

	@Override
	public StreamMessage createStreamMessage() throws JmsBrowserException {
		try {
			return this.session.createStreamMessage();
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating StreamMessage.", e);
		}
	}

	@Override
	public javax.jms.Destination createDestination(final JmsDestination dest) throws JmsBrowserException {
		try {
			switch (dest.type) {
				case QUEUE:
					return this.session.createQueue(dest.name);
				case TOPIC:
					return this.session.createTopic(dest.name);
				default:
					return null;
			}
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating destination "+dest+".", e);
		}
	}

	@Override
	public javax.jms.Destination createDestination(final javax.jms.Destination dest) throws JmsBrowserException {
		try {
			if (dest instanceof Queue)
				return this.session.createQueue(((Queue) dest).getQueueName());
			if (dest instanceof Topic)
				return this.session.createTopic(((Topic) dest).getTopicName());
			return null;
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating destination "+dest+".", e);
		}
	}

	@Override
	public javax.jms.QueueBrowser createQueueBrowser(final String queueName) throws JmsBrowserException {
		try {
			return this.session.createBrowser((Queue) this.createDestination(new JmsDestination(DestinationType.QUEUE, queueName)));
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error creating queue browser.", e);
		}
	}

	/***********************************************
	 *
	 *  MESSAGE DELETION
	 *
	 ***********************************************/
	protected void deleteMessageFromQueue(final String queueName, final Message msg) throws JmsBrowserException {
		try {
			final Session s = this.connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			final Queue queue = s.createQueue(queueName);
			final String selector = "JMSMessageID = '" + msg.getJMSMessageID() + "'";
			final MessageConsumer consumer = s.createConsumer(queue, selector);
			String result = null;
			if (consumer.receive(150)==null)
				result = "error deleting message for selector: " + selector;
			else
				s.commit();
			consumer.close();
			s.close();
			if (result!=null)
				throw new JmsBrowserException(result);
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error deleting message from queue"+queueName+".", e);
		}
	}

	protected void deleteMessageFromTopic(final String topicName, final Message msg) throws JmsBrowserException {
		try {
			final Session s = this.connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			final Topic topic = s.createTopic(topicName);
			final String selector = "JMSMessageID = '" + msg.getJMSMessageID() + "'";
			final MessageConsumer consumer = s.createConsumer(topic, selector);
			if (consumer.receive(250)!=null)
				s.commit();
			s.close();
		} catch (final JMSException e) {
			throw new JmsBrowserException("Error deleting message from topic "+topicName+".", e);
		}
//		String result = null;
//		if (consumer.receive(250)==null)
//			result = "warning: no message for selector: " + selector;
//		else
//			s.commit();
//		consumer.close();
//		s.close();
//		if (result!=null)
//			throw new JMSException(result);
	}

	// interface method
	@Override
	public void deleteMessage(final Message msg) throws JmsBrowserException {
		try {
			final JmsDestination dest = this.translateDestination(msg.getJMSDestination());
			switch (dest.type) {
				case QUEUE:
					this.deleteMessageFromQueue(dest.name, msg);
					break;
				case TOPIC:
	//				deleteMessageFromTopic(dest.name, msg);
					break;
				default:
					throw new JmsBrowserException("unsupported destination type.");
			}
		} catch (final JMSException e) {
			throw new JmsBrowserException("error getting message source.", e);
		}
	}

	// interface method
	@Override
	public void deleteMessage(final Object[] msgs) throws JmsBrowserException {
		for (final Object m : msgs)
			this.deleteMessage((Message) m);
	}

	/***********************************************
	 *
	 *  MESSAGE SENDING
	 *
	 ***********************************************/
	protected void genericSendMessage(final Message message, final Destination destination) throws JmsBrowserException {
		MessageProducer producer = null;
		try {
			producer = this.session.createProducer(destination);
			producer.setDeliveryMode(message.getJMSDeliveryMode());
			producer.setPriority(message.getJMSPriority());
			producer.setTimeToLive(message.getJMSExpiration());
			producer.send(message);
			producer.close();
		} catch(final JMSException e) {
			if (producer != null)
				try {
					producer.close();
				} catch (final JMSException ex) {
					ex.printStackTrace();
				}
			throw new JmsBrowserException("error sending message", e);
		}
	}

	// interface method
	@Override
	public void sendMessage(final Message message, final JmsDestination dest) throws JmsBrowserException {
		switch (dest.type) {
			case QUEUE:
				this.genericSendMessage(message, this.createDestination(dest));
				break;
			case TOPIC:
				this.genericSendMessage(message, this.createDestination(dest));
				break;
			default:
				throw new JmsBrowserException("unsupported destination type.");
		}
	}

	@Override
	public void sendMessage(final Message message) throws JmsBrowserException {
		JmsDestination dest;
		try {
			dest = this.translateDestination(message.getJMSDestination());
			this.sendMessage(message, dest);
		} catch (final JMSException e) {
			throw new JmsBrowserException("error getting message destination.", e);
		}
	}

	/***********************************************
	 *
	 *  GET DESTINATIONS
	 *
	 ***********************************************/
	// implementation mandatory
	protected abstract List<JmsDestination> getQueues();

	protected abstract List<JmsDestination> getTopics();

	// vendor specific
	protected List<JmsDestination> getGroups() throws JmsBrowserException {
		throw new JmsBrowserException("unsupported destination type.");
	}

	protected List<JmsDestination> getUsers() throws JmsBrowserException {
		throw new JmsBrowserException("unsupported destination type.");
	}

	// interface method
	@Override
	public List<JmsDestination> getDestinations(final DestinationType type) throws JmsBrowserException {
		List<JmsDestination> ljd;
		switch (type) {
		case QUEUE:
			ljd = this.getQueues();
			break;
		case TOPIC:
			ljd = this.getTopics();
			break;
		default:
			throw new JmsBrowserException("unsupported destination type.");
		}
		final List<String> ls = new ArrayList<String>(ljd.size());
		for (final JmsDestination d : ljd)
			ls.add(d.name);
		this.cachedDestinations.put(type, ls);
		return ljd;
	}

	@Override
	public List<JmsDurableSubscription<?>> getDurableSubscriptions(final String topicName) throws JmsBrowserException {
		return null;
	}

	/***********************************************
	 *
	 *  PURGE DESTINATION
	 *
	 ***********************************************/
	protected void purgeQueue(final String name) throws JmsBrowserException {
		try {
			final Session s = this.connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			final Queue queue = s.createQueue(name);
			final MessageConsumer consumer = s.createConsumer(queue);
			while ((consumer.receive(250)) != null) { /* NOP */ }
			s.commit();
			consumer.close();
			s.close();
		} catch (final JMSException e) {
			throw new JmsBrowserException("error purging queue"+name+".", e);
		}
	}

	protected void purgeTopic(final String name) throws JmsBrowserException {
		try {
			final Session s = this.connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			final Topic topic = s.createTopic(name);
			final MessageConsumer subscriber = s.createConsumer(topic);
			while (subscriber.receive(250) != null) { /* NOP */ }
			s.commit();
			subscriber.close();
			s.close();
		} catch (final JMSException e) {
			throw new JmsBrowserException("error purging topic"+name+".", e);
		}
	}

	// interface method
	@Override
	public void purgeDestination(final JmsDestination dest) throws JmsBrowserException {
		switch (dest.type) {
			case QUEUE:
				this.purgeQueue(dest.name);
				break;
			case TOPIC:
				this.purgeTopic(dest.name);
				break;
			default:
				throw new JmsBrowserException("unsupported destination type.");
		}
		this.notifyPurge(dest.name);
	}

	/***********************************************
	 *
	 *  CREATE AND DELETE DESTINATIONS
	 *
	 ***********************************************/

	@Override
	public void createDestinationOnServer(final JmsDestination dest) throws JmsBrowserException {
		switch (dest.type) {
			case QUEUE:
				this.createQueueOnServer(dest.name);
				break;
			case TOPIC:
				this.createTopicOnServer(dest.name);
				break;
			default:
				throw new JmsBrowserException("unsupported destination type.");
		}
	}

	protected void createQueueOnServer(final String name) throws JmsBrowserException {
		// try dynamic queue creation
		try {
			final Queue q = this.session.createQueue(name);
			final Message msg = this.createTextMessage();
			msg.setJMSDestination(q);
			this.sendMessage(msg);
			this.deleteMessage(msg);
		} catch (final JMSException e) {
			throw new JmsBrowserException("Dynamic queue creation failed, maybe it is disabled or not supported.", e);
		}
	}

	protected void createTopicOnServer(final String name) throws JmsBrowserException {
		// try dynamic topic creation
		try {
			final Topic t = this.session.createTopic(name);
			final Message msg = this.createTextMessage();
			msg.setJMSDestination(t);
			this.sendMessage(msg);
		} catch (final JMSException e) {
			throw new JmsBrowserException("Dynamic topic creation failed, maybe it is disabled or not supported.", e);
		}
	}

	@Override
	public void deleteDestinationOnServer(final JmsDestination dest) throws JmsBrowserException {
		switch (dest.type) {
			case QUEUE:
				this.deleteQueueOnServer(dest.name);
				break;
			case TOPIC:
				this.deleteTopicOnServer(dest.name);
				break;
			default:
				throw new JmsBrowserException("unsupported destination type.");
		}
	}

	protected void deleteQueueOnServer(final String name) throws JmsBrowserException {
		throw new JmsBrowserException("deleting queues is not supported.");
	}

	protected void deleteTopicOnServer(final String name) throws JmsBrowserException {
		throw new JmsBrowserException("deleting topics is not supported.");
	}

	/***********************************************
	 *
	 *  DON'T KNOW, DON'T CARE
	 * @throws JmsBrowserException
	 *
	 ***********************************************/
	protected Integer getQueueMessageCount(final String name) throws JmsBrowserException {
		try {
			final Queue queue = this.session.createQueue(name);
			final QueueBrowser qb = this.session.createBrowser(queue);
			final Enumeration<?> en = qb.getEnumeration();
			int i = 0;
			while (en.hasMoreElements()) {
				en.nextElement();
				++i;
			}
			qb.close();
			return Integer.valueOf(i);
		} catch (final Exception e) {
			throw new JmsBrowserException("error counting messages in queue "+name+".", e);
		}
//		return errResult;
	}

	private void sleep(final int millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e1) {}
	}

	/***********************************************
	 *
	 *  Timer Procedure
	 *
	 ***********************************************/
	@Override
	public void run() {
		if (!this.isOpen())
			return;
		try {
			final List<String> ljd = this.cachedDestinations.get(DestinationType.QUEUE);
			if (ljd!=null) for (final String s : ljd) {
				if (this.queueCountListeners.containsKey(s)) {
					final Integer i = this.getQueueMessageCount(s);
					this.queueCountListeners.get(s).onUpdate(i);
				}
			}
			if (JmsFeature.isSupported(this, JmsFeature.DURABLES_COUNT)) {
				for (final Entry<JmsDurableSubscription<?>, MessageCountListener> e : this.subscriptionListeners.entrySet()) {
					final int i = this.getSubscriberMessageCount(e.getKey());
					e.getValue().onUpdate(i);
				}
			}
		} catch (final Exception e) {
			if (!this.isOpen()) {
				System.err.println("timer error " + e.getMessage());
				try {
					synchronized (this.t) {
						this.t.wait();
					}
				} catch (final InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	@Override
	public void purgeSubscriberMessages(final JmsDurableSubscription<?> sub) throws JmsBrowserException {
		throw new JmsBrowserException("purging durable subscriptions is not supported");
	}

	protected int getSubscriberMessageCount(final JmsDurableSubscription<?> sub) throws JmsBrowserException {
		return -1;
	}

	@Override
	public void addConnectionListener(final JmsConnectionListener listener, final Object callbackData) {
//		if (!connectionListeners.contains(listener)) {
//			connectionListeners.add(listener);
			this.connectionListeners.put(listener, callbackData);
//		}
	}

	@Override
	public void removeConnectionListener(final JmsConnectionListener listener) throws JmsBrowserException {
		if (this.connectionListeners.containsKey(listener))
			this.connectionListeners.remove(listener);
	}


	/***********************************************
	 *
	 *  Exception Listener
	 *
	 ***********************************************/
	@Override
	public void onException(final JMSException exception) {
		System.err.println("JMSException caught: " + exception.getMessage());
		this.close();
		for (final QueueListenerThread qlt : this.queueListeners.values())
			qlt.enterSandman();
		for (final Entry<JmsConnectionListener, Object> cl : this.connectionListeners.entrySet())
			cl.getKey().onDisconnect(this, cl.getValue());
		boolean retry = true;
		while (retry) {
			try {
				this.sleep(this.reconnectSleep);
				this.open();
				this.reconnectSleep = 2500;
				retry = false;
				for (final QueueListenerThread qlt : this.queueListeners.values())
					qlt.wakeUp();
			} catch (final JmsBrowserException e) {
//				System.err.println("reconnect failed after sleeping for " + reconnectSleep/1000.0 + "s ("  + e.getMessage() + ")");
				if (this.reconnectSleep<20000)
					this.reconnectSleep *= 2;
			}
		}
//		for (QueueListenerThread qlt : queueListeners.values())
//			qlt.shutdown();
	}

	/***********************************************
	 *
	 *  Converts Destinations
	 *
	 ***********************************************/
	@Override
	public JmsDestination translateDestination(final javax.jms.Destination dest) {
		try {
			if (dest instanceof javax.jms.Queue)
				return new JmsDestination(DestinationType.QUEUE, ((javax.jms.Queue)dest).getQueueName());
			if (dest instanceof javax.jms.Topic)
				return new JmsDestination(DestinationType.TOPIC, ((javax.jms.Topic)dest).getTopicName());
		} catch (final JMSException e) {
			e.printStackTrace();
		}
		return null;
	}
}
