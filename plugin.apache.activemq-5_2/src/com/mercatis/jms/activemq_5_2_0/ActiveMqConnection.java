package com.mercatis.jms.activemq_5_2_0;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import com.mercatis.jms.AbstractJmxJmsConnection;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsDurableSubscription;
import com.mercatis.jms.log.LogUtil;

public class ActiveMqConnection extends AbstractJmxJmsConnection<ActiveMQConnectionFactory, ActiveMQConnection, Session> {

	final private String brokerPrefix;
	final private String topicPrefix;
	final private ObjectName broker;
	
	ActiveMqConnection(ActiveMQConnectionFactory factory, Map<String, String> properties) throws JmsBrowserException {
		super(factory, properties);
		brokerPrefix = "org.apache.activemq:BrokerName="+properties.get("JMXBrokerName")+",";
		topicPrefix = brokerPrefix+"Type=Topic,Destination=";
		ObjectName on = null;
		try {
			on = new ObjectName(brokerPrefix+"Type=Broker");
		} catch (Exception e) { /* ignore */ }
		broker = on;
	}

	@Override
	public JmsConnectionService getConnectionService() {
		return new ActiveMqConnectionService();
	}
	
	@Override
	public long getSupportedFeatures() {
		if (isJmxUsable())
			return JmsFeature.DURABLES_LIST | JmsFeature.DESTINATION_CREATE;
		return JmsFeature.NONE;
	}
	
	@Override
	protected List<JmsDestination> getQueues() {
		List<JmsDestination> queues = new ArrayList<JmsDestination>();
		if (isJmxUsable()) {
			MBeanServerConnection conn = getMBeanServerConnection();
			try {
				ObjectName onames[] = (ObjectName[]) conn.getAttribute(broker, "Queues");
				for (ObjectName on : onames) {
					String name = on.toString();
					String queueName = name.substring(name.lastIndexOf('=')+1);
					queues.add(new JmsDestination(DestinationType.QUEUE, queueName));
				}
				return queues;
			} catch (Exception e) {
				queues.clear();
			}
		}
		try {
			Set<ActiveMQQueue> infos = connection.getDestinationSource().getQueues();
			if (!infos.isEmpty()) {
				for (ActiveMQQueue info : infos) {
					queues.add(new JmsDestination(DestinationType.QUEUE, info.getQueueName()));
				}
			}
		} catch (JMSException ex) {
			ex.printStackTrace();
		}
		return queues;
	}

	@Override
	protected List<JmsDestination> getTopics() {
		List<JmsDestination> topics = new ArrayList<JmsDestination>();
		if (isJmxUsable()) {
			MBeanServerConnection conn = getMBeanServerConnection();
			try {
				ObjectName onames[] = (ObjectName[]) conn.getAttribute(broker, "Topics");
				for (ObjectName on : onames) {
					String name = on.toString();
					String topicName = name.substring(name.lastIndexOf('=')+1);
					if (!topicName.startsWith("ActiveMQ.Advisory."))
						topics.add(new JmsDestination(DestinationType.TOPIC, topicName));
				}
				return topics;
			} catch (Exception e) {
				topics.clear();
			}
		}
		try {
			Set<ActiveMQTopic> infos = this.connection.getDestinationSource().getTopics();
			if (!infos.isEmpty()) {
				for (ActiveMQTopic info : infos) {
					topics.add(new JmsDestination(DestinationType.TOPIC, info.getTopicName()));
				}
			}
		} catch (JMSException ex) {
			ex.printStackTrace();
		}
		return topics;
	}

	@Override
	protected void postOpen(Map<String, String> properties) throws JmsBrowserException {
	}
	
	@Override
	public List<JmsDurableSubscription<?>> getDurableSubscriptions(String topicName) throws JmsBrowserException {
		if (!isJmxUsable())
			return null;
		List<JmsDurableSubscription<?>> result = new ArrayList<JmsDurableSubscription<?>>();
		MBeanServerConnection conn = getMBeanServerConnection();
		try {
			ObjectName ton = new ObjectName(topicPrefix+topicName);
			ObjectName onames[] = (ObjectName[]) conn.getAttribute(ton, "Subscriptions");
			for (ObjectName on : onames) {
				StringTokenizer tok = new StringTokenizer(on.toString(), ",");
				String clientId = null, subscriptionID = null;
				while (tok.hasMoreTokens()) {
					String full = tok.nextToken();
					int idx = full.indexOf('=');
					String key = full.substring(0, idx);
					String value = full.substring(idx+1);
					if ("Type".equals(key) && !"Subscription".equals(value))
						break;
					if ("persistentMode".equals(key) && !"Durable".equals(value))
						break;
					if ("subscriptionID".equals(key)) {
						subscriptionID = value;
						continue;
					}
					if ("destinationType".equals(key) && !"Topic".equals(value))
						break;
					if ("destinationName".equals(key) && !topicName.equals(value))
						break;
					if ("clientId".equals(key)) {
						clientId = value;
						continue;
					}
				}
				if (clientId!=null && subscriptionID!=null)
					result.add(new JmsDurableSubscription<Object>(topicName, subscriptionID, clientId, null));
			}
		} catch (Exception ex) {
			LogUtil.logError("Error querying durable subscribers "+topicName+": "+ex.getMessage(), ex);
		}
		return result;
	}
	
	@Override
	public void createQueueOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			MBeanServerConnection conn = getMBeanServerConnection();
			try {
				final String[] args = { name };
				conn.invoke(broker, "addQueue", args, null);
				return;
			} catch (Exception e) {
				LogUtil.logError("Jmx queue creation failed. Trying dynamic creation.", e);
			}
		}
		super.createQueueOnServer(name);
	};
	
	@Override
	public void createTopicOnServer(String name) throws JmsBrowserException {
		if (isJmxUsable()) {
			MBeanServerConnection conn = getMBeanServerConnection();
			try {
				final String[] args = { name };
				conn.invoke(broker, "addTopic", args, null);
				return;
			} catch (Exception e) {
				LogUtil.logError("Jmx topic creation failed. Trying dynamic creation.", e);
			}
		}
		super.createTopicOnServer(name);
	}
	
	@Override
	protected int getSubscriberMessageCount(JmsDurableSubscription<?> sub) throws JmsBrowserException {
		return -1;
	}
}
