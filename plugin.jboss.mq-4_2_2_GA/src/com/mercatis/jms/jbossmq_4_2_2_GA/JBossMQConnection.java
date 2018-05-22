package com.mercatis.jms.jbossmq_4_2_2_GA;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import com.mercatis.jms.AbstractJmsConnection;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.JmsDestination.DestinationType;

public class JBossMQConnection extends AbstractJmsConnection<ConnectionFactory, org.jboss.mq.Connection, Session> {
	private InitialContext context;

	@Override
	public JmsConnectionService getConnectionService() {
		return new JBossMQConnectionService();
	}

	public JBossMQConnection(Map<String, String> properties) throws JmsBrowserException {
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
			String factoryName = properties.get("ConnectionFactory");
			factory = (ConnectionFactory) context.lookup(factoryName);
		} catch (NamingException e) {
			throw new JmsBrowserException("error getting jboss mq context.", e);
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
	 * @return
	 */
	@Override
	public List<JmsDestination> getQueues() {
		List<JmsDestination> queues = new ArrayList<JmsDestination>();
		try {
			NamingEnumeration<NameClassPair> e = context.list("queue");

			while (e.hasMoreElements()) {
				NameClassPair obj = e.next();
				queues.add(new JmsDestination(DestinationType.QUEUE, obj.getName()));
			}
		} catch (NamingException e) {
			e.printStackTrace();
		}
		return queues;
	}

	/**
	 * @return
	 */
	@Override
	public List<JmsDestination> getTopics() {
		List<JmsDestination> topics = new ArrayList<JmsDestination>();
		try {
			NamingEnumeration<NameClassPair> e = context.list("topic");
			while (e.hasMoreElements()) {
				NameClassPair obj = e.next();
				topics.add(new JmsDestination(DestinationType.TOPIC, obj.getName()));
			}
		} catch (NamingException e) {
			e.printStackTrace();
		}
		return topics;
	}
}
