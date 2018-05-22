package com.mercatis.jms.jboss_messaging_1_4_3;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.JBossSession;

import com.mercatis.jms.AbstractJmsConnection;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsFeature;


public class JBossMessagingConnection extends AbstractJmsConnection<JBossConnectionFactory, JBossConnection, JBossSession> {

	private InitialContext context;

	@Override
	public JmsConnectionService getConnectionService() {
		return new JBossMessagingConnectionService();
	}

	JBossMessagingConnection(Map<String, String> properties /*, MBeanServerConnection mbeanServer */) throws JmsBrowserException {
		super(null, properties);
	}

	@Override
	public long getSupportedFeatures() {
		return JmsFeature.NONE;
	}

	@Override
	protected void preOpen(Map<String, String> properties) throws JmsBrowserException {
		try {
			String url = properties.get("Host")+":"+properties.get("Port");
			Properties connProps = new Properties();
			connProps.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
			connProps.put(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces");
			connProps.put(Context.PROVIDER_URL, url);
			context = new InitialContext(connProps);
			factory = (JBossConnectionFactory) context.lookup(properties.get("ConnectionFactory"));
		} catch (NamingException e) {
			throw new JmsBrowserException("error getting jboss messaging context.", e);
		}
	}

	@Override
	protected void postOpen(Map<String, String> properties) throws JmsBrowserException {
	}

	@Override
	protected void preClose() {
		try {
			if (context!=null)
				context.close();
		} catch (NamingException e1) {}
	}

	/**
	 * @throws JMSException
	 *
	 */
	@Override
	protected List<JmsDestination> getQueues() {
		List<JmsDestination> queues = new LinkedList<>();

		NamingEnumeration<NameClassPair> list;
		try {
			list = context.list("queue");
			while (list.hasMoreElements()) {
				NameClassPair nameClassPair = list
						.nextElement();
				String name = /*"name=" +*/ nameClassPair.getName();

				queues.add(new JmsDestination(DestinationType.QUEUE, name /*queueName.toString()*/));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return queues;
	}

	@Override
	protected List<JmsDestination> getTopics() {
		List<JmsDestination> topics = new ArrayList<>();

		NamingEnumeration<NameClassPair> list;
		try {
			list = context.list("topic");
			while (list.hasMoreElements()) {
				NameClassPair nameClassPair = list.nextElement();
				String name = /*"name=" +*/ nameClassPair.getName();

				topics.add(new JmsDestination(DestinationType.TOPIC, name /*topicName.toString()*/));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return topics;
	}
}
