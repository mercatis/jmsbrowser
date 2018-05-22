package com.mercatis.jms.openmq_4_4_0;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import com.mercatis.jms.AbstractJmxJmsConnection;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.log.LogUtil;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.ConnectionFactory;
import com.sun.messaging.jms.Connection;
import com.sun.messaging.jms.Session;
import com.sun.messaging.jms.management.server.DestinationAttributes;
import com.sun.messaging.jms.management.server.DestinationOperations;
import com.sun.messaging.jms.management.server.MQObjectName;

public class OpenMqConnection  extends AbstractJmxJmsConnection<ConnectionFactory, Connection, Session> {
	private static final String openmqQueueType = com.sun.messaging.jms.management.server.DestinationType.QUEUE;
	private static final String openmqTopicType = com.sun.messaging.jms.management.server.DestinationType.TOPIC;

	@Override
	public JmsConnectionService getConnectionService() {
		return new OpenMqConnectionService();
	}

	public OpenMqConnection(Map<String, String> properties) throws JmsBrowserException {
		super(null, properties);
	}

	@Override
	public long getSupportedFeatures() {
		if (isJmxUsable())
			return JmsFeature.DURABLES_LIST | JmsFeature.DESTINATION_ALL;
		return JmsFeature.NONE;
	}

	@Override
	protected void preOpen(Map<String, String> properties) throws JmsBrowserException {
		String url = properties.get("Host")+":"+properties.get("Port");
		try {
			factory = new ConnectionFactory();
			factory.setProperty(ConnectionConfiguration.imqAddressList, url);
		} catch (JMSException e) {
			throw new JmsBrowserException("error creating open mq connection factory.", e);
		}
		super.preOpen(properties);
	}

	@Override
	protected void postOpen(Map<String, String> properties) throws JmsBrowserException {
	}

	@Override
	protected JMXConnector createJMXConnector(Map<String, String> properties) throws JmsBrowserException {
		String url = properties.get("Host")+":"+properties.get("Port");
		String username = properties.get("Username");
		String password = properties.get("Password");
		AdminConnectionFactory acf = new AdminConnectionFactory();
		try {
			acf.setProperty(AdminConnectionConfiguration.imqAddress, url);
			return acf.createConnection(username, password);
		} catch (Exception e) {
			JmsBrowserException ex = new JmsBrowserException("error creating open mq jmx connector.", e);
			throw ex;
		}
	}

	private List<JmsDestination> getMBeanDestinations(DestinationType dt) {
		List<JmsDestination> l = new LinkedList<>();
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try
		{
			// Create object name for destination manager monitor MBean
			ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME);
			// Get destination object names
			ObjectName destNames[] = (ObjectName[]) mbsc.invoke(destMgrMonitorName, DestinationOperations.GET_DESTINATIONS, null, null);

			String dName, dType, dState;
			for (ObjectName eachDestName : destNames)
			{
				dName = (String) mbsc.getAttribute(eachDestName, DestinationAttributes.NAME);
				dType = (String) mbsc.getAttribute(eachDestName, DestinationAttributes.TYPE);
				dState = (String) mbsc.getAttribute(eachDestName, DestinationAttributes.STATE_LABEL);
				if (dType.equals(openmqQueueType) && dt==DestinationType.QUEUE && dState.equals("Running"))
					l.add(new JmsDestination(dt, dName));
				if (dType.equals(openmqTopicType) && dt==DestinationType.TOPIC && dState.equals("Running"))
					l.add(new JmsDestination(dt, dName));
			}
		}
		catch (Exception e)	{
			System.out.println( "Exception occurred: " + e.toString() );
			e.printStackTrace();
		}
		return l;
	}

	/**
	 * @return
	 */
	@Override
	public List<JmsDestination> getQueues() {
		return getMBeanDestinations(DestinationType.QUEUE);
	}

	/**
	 * @return
	 */
	@Override
	public List<JmsDestination> getTopics() {
		return getMBeanDestinations(DestinationType.TOPIC);
	}

	@Override
	public void purgeQueue(String name) throws JmsBrowserException {
		purgeOpenMqDestination(openmqQueueType, name);
	}

	@Override
	public void purgeTopic(String name) throws JmsBrowserException {
		purgeOpenMqDestination(openmqTopicType, name);
	}

	private void purgeOpenMqDestination(String destinationType, String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try {
			// Create object name for destination manager monitor MBean
			ObjectName destMgrConfigName = MQObjectName.createDestinationConfig(destinationType, name);
			mbsc.invoke(destMgrConfigName, DestinationOperations.PURGE, null, null);
		} catch (Exception e) {
			JmsBrowserException je = new JmsBrowserException("error purging open mq destination "+name+".", e);
			throw je;
		}
	}

	@Override
	protected Integer getQueueMessageCount(String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try {
			ObjectName destMonName = MQObjectName.createDestinationMonitor(openmqQueueType, name);
			Long cnt = (Long) mbsc.getAttribute(destMonName, DestinationAttributes.NUM_MSGS);
			return cnt.intValue();
		} catch (Exception e) {
			JmsBrowserException je = new JmsBrowserException("error getting message count of open mq queue "+name+".", e);
			throw je;
		}
	}

	@Override
	protected void createQueueOnServer(String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try
		{
			ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);
			final Object[] args = { com.sun.messaging.jms.management.server.DestinationType.QUEUE, name };
			final String[] signature = { String.class.getName(), String.class.getName() };
			mbsc.invoke(destMgrMonitorName, DestinationOperations.CREATE, args, signature);
		} catch (Exception e) {
			LogUtil.logError("Queue creating failed. Trying dynamic creation.", e);
		}
		super.createQueueOnServer(name);
	}

	@Override
	protected void createTopicOnServer(String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try
		{
			ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);
			final Object[] args = { com.sun.messaging.jms.management.server.DestinationType.TOPIC, name };
			final String[] signature = { String.class.getName(), String.class.getName() };
			mbsc.invoke(destMgrMonitorName, DestinationOperations.CREATE, args, signature);
		} catch (Exception e) {
			LogUtil.logError("Topic creating failed. Trying dynamic creation.", e);
		}
		super.createTopicOnServer(name);
	}

	@Override
	protected void deleteQueueOnServer(String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try
		{
			ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);
			final Object[] args = { com.sun.messaging.jms.management.server.DestinationType.QUEUE, name };
			final String[] signature = { String.class.getName(), String.class.getName() };
			mbsc.invoke(destMgrMonitorName, DestinationOperations.DESTROY, args, signature);
		} catch (Exception e) {
			throw new JmsBrowserException("Deleting queue failed.", e);
		}
	}

	@Override
	protected void deleteTopicOnServer(String name) throws JmsBrowserException {
		MBeanServerConnection mbsc = getMBeanServerConnection();
		try
		{
			ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);
			final Object[] args = { com.sun.messaging.jms.management.server.DestinationType.TOPIC, name };
			final String[] signature = { String.class.getName(), String.class.getName() };
			mbsc.invoke(destMgrMonitorName, DestinationOperations.DESTROY, args, signature);
		} catch (Exception e) {
			throw new JmsBrowserException("Deleting topic failed.", e);
		}
	}
}
