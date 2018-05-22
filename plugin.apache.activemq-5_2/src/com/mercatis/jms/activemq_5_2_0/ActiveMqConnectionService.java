package com.mercatis.jms.activemq_5_2_0;

import java.util.List;
import java.util.Map;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.LibraryDependency;

public class ActiveMqConnectionService implements JmsConnectionService {

	/* (non-Javadoc)
	 * @see com.mercatis.jms.JmsConnectionService#createConnection(java.util.Map)
	 */
	@Override
	public JmsConnection createConnection(Map<String, String> properties) throws JmsBrowserException {
		String host = properties.get("Host");
		String port = properties.get("Port");
		// create connectionFactory
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://"+host+":"+port);
		// create ActivemqConnection
		ActiveMqConnection result = new ActiveMqConnection(factory, properties);
		return result;
	}

	/* (non-Javadoc)
	 * @see com.mercatis.jms.JmsConnectionService#getMissingLibraries()
	 */
	@Override
	public List<LibraryDependency> getNotIncludedLibraryNames(String os, String arch) {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.mercatis.jms.JmsConnectionService#getSupportedVersions()
	 */
	@Override
	public String getDisplayName() {
		return "Apache ActiveMQ (v4,v5)";
	}
}
