package com.mercatis.jms;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;

public abstract class AbstractJmxJmsConnection<F extends javax.jms.ConnectionFactory, C extends javax.jms.Connection, S extends javax.jms.Session> extends AbstractJmsConnection<F, C, S> {
	private JMXConnector jmxc;
	private MBeanServerConnection mbsc;
	public static final String JMX_PORT = "JMXPort";
	public static final String JMX_USER = "JMXUsername";
	public static final String JMX_PASS = "JMXPassword";
	
	public AbstractJmxJmsConnection(F factory, Map<String, String> properties) throws JmsBrowserException {
		super(factory, properties);
	}
	
	@Override
	protected void preOpen(Map<String, String> properties) throws JmsBrowserException {
		// test if jmx is configured and usable
		try {
			jmxc = createJMXConnector(properties);
			if (jmxc!=null)
				mbsc = createMBeanServerConnection();
		} catch (IOException e) {
			preClose();
		}
	}
	
	@Override
	protected void preClose() {
		mbsc = null;
		if (jmxc != null) {
			try {
				jmxc.close();
				jmxc = null;
			} catch (IOException e) { /* ignore */ }
		}
	}
	
	protected boolean isJmxUsable() {
		return jmxc != null;
	}
	
	protected JMXConnector createJMXConnector(Map<String, String> properties) throws JmsBrowserException {
		String host = properties.get("Host");
		String jmxPort = properties.get(JMX_PORT);
		Hashtable<String, String> ht = null;
		String jmxUser = properties.get(JMX_USER);
		if (jmxUser==null)
			jmxUser = properties.get(USERNAME);
		String jmxPass = properties.get(JMX_PASS);
		if (jmxPass==null)
			jmxPass = properties.get(PASSWORD);
		if (jmxUser!=null && jmxUser.length()>0) {
			ht = new Hashtable<String, String>();
			ht.put(Context.SECURITY_PRINCIPAL, jmxUser);
			if (jmxPass!=null && jmxPass.length()>0)
				ht.put(Context.SECURITY_AUTHENTICATION, jmxPass);
		}
		if (jmxPort!=null && jmxPort.length()>0) {
			try {
				JMXServiceURL serviceUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+host+":"+jmxPort+"/jmxrmi");
				return JMXConnectorFactory.connect(serviceUrl, ht);
			} catch (Exception e) {
				throw new JmsBrowserException("error creating jmx connector", e);
			}
		}
		return null;
	}
	
	private MBeanServerConnection createMBeanServerConnection() throws IOException {
		return jmxc.getMBeanServerConnection();
	}
	
	protected JMXConnector getJMXConnector() {
		return jmxc;
	}
	
	protected MBeanServerConnection getMBeanServerConnection() {
		return mbsc;
	}
	
	protected Object getMBean(ObjectName on, Class<?> clazz, boolean notificationBroadcaster) throws IOException {
		Object o = MBeanServerInvocationHandler.newProxyInstance(mbsc, on, clazz, notificationBroadcaster);
		return o;
	}

}
