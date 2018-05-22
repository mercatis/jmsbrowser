package com.mercatis.jms.hornetq_2_2_2;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.hornetq.api.jms.management.JMSQueueControl;
import org.hornetq.api.jms.management.JMSServerControl;
import org.hornetq.api.jms.management.SubscriptionInfo;
import org.hornetq.api.jms.management.TopicControl;
import com.mercatis.jms.AbstractJmxJmsConnection;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsDurableSubscription;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.log.LogUtil;

public class HornetQConnection extends AbstractJmxJmsConnection<ConnectionFactory, org.hornetq.jms.client.HornetQConnection, org.hornetq.jms.client.HornetQSession> {
	private InitialContext context;
	private boolean useMgmntQueue = false;
	
	@Override
	public JmsConnectionService getConnectionService() {
		return new HornetQConnectionService();
	}
	
	public HornetQConnection(Map<String, String> properties) throws JmsBrowserException {
		super(null, properties);
	}
	
	@Override
	public long getSupportedFeatures() {
		if (isJmxUsable())
			return JmsFeature.DURABLES_ALL | JmsFeature.DESTINATION_ALL;
		return JmsFeature.NONE;
	}

	@Override
	protected void preOpen(Map<String, String> properties) throws JmsBrowserException {
		// this is mandatory for AbstractJmsJmsConnections
		super.preOpen(properties);
		// Workaround due to HornetQs usage of the context class loader.
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		String host = properties.get("Host");;
		try {
			Properties connProps = new Properties();
			connProps.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
			connProps.put(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces");
			connProps.put(Context.PROVIDER_URL, "jnp://"+host+":"+properties.get("Port"));
			context = new InitialContext(connProps);
			factory = (ConnectionFactory) context.lookup(properties.get("ConnectionFactory"));
		} catch (NamingException e) {
			throw new JmsBrowserException("error getting hornetq context.", e);
		}
	}
	
	/*
	 *  to use this feature put a new property with name "Management Queue" to the
	 *  plugin.xml extensions with default value "hornetq.management"
	 */
	@Override
	protected void postOpen(Map<String, String> properties) throws JmsBrowserException {
//		if (managementQueue==null)
//			managementQueue = properties.get("Management Queue");
//		Queue q = HornetQJMSClient.createQueue(managementQueue);
//		requestor = new QueueRequestor(session, q);
	}

	@Override
	protected void preClose() {
		// this is mandatory for AbstractJmsJmsConnections
		super.preClose();
		
		if (context!=null) {
			try {
				context.close();
			} catch (NamingException e1) { /* ignore */ }
		}
//		requestor = null;
	}

	@Override
	protected List<JmsDestination> getQueues() {
		List<JmsDestination> queues = new LinkedList<JmsDestination>();
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl ctrl = (JMSServerControl) getMBean(on, JMSServerControl.class, false);
				for (String qName : ctrl.getQueueNames()) {
					queues.add(new JmsDestination(DestinationType.QUEUE, qName));
				}
				return queues;
			} catch (Exception e) { /* ignore */}
		}
		NamingEnumeration<NameClassPair> list;
		try {
			list = context.list("/queue");
			while (list.hasMoreElements()) {
				NameClassPair nameClassPair = list.nextElement();
				queues.add(new JmsDestination(DestinationType.QUEUE, nameClassPair.getName()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return queues;
	}

	@Override
	protected List<JmsDestination> getTopics() {
		List<JmsDestination> topics = new ArrayList<JmsDestination>();
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl ctrl = (JMSServerControl)getMBean(on, JMSServerControl.class, false);
		        for (String tName : ctrl.getTopicNames())
		        	topics.add(new JmsDestination(DestinationType.TOPIC, tName));
		        return topics;
			} catch (Exception e) { /* ignore */ }
		}
		NamingEnumeration<NameClassPair> list;
		try {
			list = context.list("/topic");
			while (list.hasMoreElements()) {
				NameClassPair nameClassPair = list.nextElement();
				topics.add(new JmsDestination(DestinationType.TOPIC, nameClassPair.getName()));
			}
		} catch (NameNotFoundException e) {
			// ignore
		} catch (Exception e) {
			e.printStackTrace();
		}
		return topics;
	}

	@Override
	protected void purgeQueue(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSQueueObjectName(name);
				JMSQueueControl queueControl = (JMSQueueControl)getMBean(on, JMSQueueControl.class, false);
				queueControl.removeMessages(null);
				return;
			} catch (Exception e) {
				e.printStackTrace();
				/* ignore */
			}
		}
		if (useMgmntQueue) {
//			try {
//				Message m = session.createMessage();
//				JMSManagementHelper.putAttribute(m, name, "purge");
//				Message reply = requestor.request(m);
//				JMSManagementHelper.getResult(reply);
//			} catch (Exception e) {
//				System.err.println(e.getMessage());
//			}
		}
		super.purgeQueue(name);
	}
	
	@Override
	protected Integer getQueueMessageCount(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSQueueObjectName(name);
				JMSQueueControl queueControl = (JMSQueueControl)getMBean(on, JMSQueueControl.class, false);
				return (int) queueControl.getMessageCount();
			} catch (Exception e) { /* ignore */ }
		}
		if (useMgmntQueue) {
//			try {
//				Message m = session.createMessage();
//				JMSManagementHelper.putAttribute(m, name, "messageCount");
//				Message reply = requestor.request(m);
//				return (Integer)JMSManagementHelper.getResult(reply);
//			} catch (Exception e) { /* ignore */ }
		}
		return super.getQueueMessageCount(name);
	}

	@Override
	protected void deleteMessageFromQueue(String queueName, Message msg) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSQueueObjectName(queueName);
		        JMSQueueControl queueControl = (JMSQueueControl)getMBean(on, JMSQueueControl.class, false);
		        if (queueControl.removeMessage(msg.getJMSMessageID()))
		        	return;
			} catch (Exception e) { /* ignore */ }
		}
		if (useMgmntQueue) {
//			try {
//				Message m = session.createMessage();
//				JMSManagementHelper.putOperationInvocation(m, queueName, "removeMessage", msg.getJMSMessageID());
//				Message reply = requestor.request(m);
//				if (!JMSManagementHelper.hasOperationSucceeded(reply) || !(Boolean)JMSManagementHelper.getResult(reply)) {
//					throw new JMSException("error executing delete command for message "+msg.getJMSMessageID()+" on "+queueName);
//				}
//			} catch (Exception e) {
//				System.err.println(e.getMessage());
//				super.deleteMessageFromQueue(queueName, msg);
//			}
		}
		super.deleteMessageFromQueue(queueName, msg);
	}
	
	@Override
	public List<JmsDurableSubscription<?>> getDurableSubscriptions(String topicName) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSTopicObjectName(topicName);
				TopicControl topicControl = (TopicControl) getMBean(on, TopicControl.class, false);
				SubscriptionInfo infos[] = SubscriptionInfo.from(topicControl.listDurableSubscriptionsAsJSON());
				List<JmsDurableSubscription<?>> result = new ArrayList<JmsDurableSubscription<?>>(infos.length);
				for (SubscriptionInfo info : infos) {
					result.add(new JmsDurableSubscription<String>("jms.topic."+topicName, info.getName(), info.getClientID(), info.getQueueName()));
				}
				return result;
			} catch (Exception e) {
				throw new JmsBrowserException("Getting durable subscriptions failed.", e);
			}
		}
		return null;
	}
	
	@Override
	protected int getSubscriberMessageCount(JmsDurableSubscription<?> subscriberInfo) throws JmsBrowserException {
		if (isJmxUsable()) {
			@SuppressWarnings("unchecked")
			JmsDurableSubscription<String> sub = (JmsDurableSubscription<String>) subscriberInfo;
			try {
				SimpleString arg0 = new SimpleString(sub.topic);
				SimpleString arg1 = new SimpleString(sub.customData);
				ObjectName on = ObjectNameBuilder.DEFAULT.getQueueObjectName(arg0, arg1);
				JMSQueueControl queueControl = (JMSQueueControl)getMBean(on, JMSQueueControl.class, false);
				return (int) queueControl.countMessages(null); //(sub.clientID, sub.name, "");
			} catch (Exception e) {
				e.printStackTrace();
			}
// this also works
//			try {
//				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSTopicObjectName(sub.topic);
//				TopicControl queueControl = (TopicControl)getMBean(on, TopicControl.class, false);
//				return (int) queueControl.countMessagesForSubscription(sub.clientID, sub.name, "");
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
		}
		return -1;
	}
	
	@Override
	public void purgeSubscriberMessages(JmsDurableSubscription<?> subscriberInfo) throws JmsBrowserException {
		if (isJmxUsable()) {
			@SuppressWarnings("unchecked")
			JmsDurableSubscription<String> sub = (JmsDurableSubscription<String>) subscriberInfo;
			try {
				SimpleString arg0 = new SimpleString(sub.topic);
				SimpleString arg1 = new SimpleString(sub.customData);
				ObjectName on = ObjectNameBuilder.DEFAULT.getQueueObjectName(arg0, arg1);
				JMSQueueControl queueControl = (JMSQueueControl)getMBean(on, JMSQueueControl.class, false);
				queueControl.removeMessages(null);
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		throw new JmsBrowserException("Purging durable subscription failed becuase JMX is not available.");
	}
	
	@Override
	protected void createQueueOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl serverControl = (JMSServerControl)getMBean(on, JMSServerControl.class, false);
		        if (serverControl.createQueue(name))
		        	return;
			} catch (Exception e) {
				LogUtil.logError("Queue creating failed. Trying dynamic creation.", e);
			}
		}
		super.createQueueOnServer(name);
	}
	
	@Override
	protected void createTopicOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl serverControl = (JMSServerControl)getMBean(on, JMSServerControl.class, false);
		        if (serverControl.createTopic(name))
		        	return;
			} catch (Exception e) {
				LogUtil.logError("Topic creating failed. Trying dynamic creation.", e);
			}
		}
		super.createTopicOnServer(name);
	}
	
	@Override
	protected void deleteQueueOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl serverControl = (JMSServerControl)getMBean(on, JMSServerControl.class, false);
		        if (serverControl.destroyQueue(name))
		        	return;
		        throw new JmsBrowserException("Deleting queue failed with no reason.");
			} catch (Exception e) {
				throw new JmsBrowserException("Deleting queue failed.", e);
			}
		}
		throw new JmsBrowserException("Deleting queue failed becuase JMX is not available.");
	}
	
	@Override
	protected void deleteTopicOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			try {
				ObjectName on = ObjectNameBuilder.DEFAULT.getJMSServerObjectName();
				JMSServerControl serverControl = (JMSServerControl)getMBean(on, JMSServerControl.class, false);
		        if (serverControl.destroyTopic(name))
		        	return;
		        throw new JmsBrowserException("Deleting topic failed with no reason.");
			} catch (Exception e) {
				throw new JmsBrowserException("Deleting topic failed.", e);
			}
		}
		throw new JmsBrowserException("Deleting topic failed becuase JMX is not available.");
	}
}
