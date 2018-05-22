package com.mercatis.jms.jboss_messaging_1_4_3;

import java.util.List;
import java.util.Map;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.LibraryDependency;


public class JBossMessagingConnectionService implements JmsConnectionService {

	/* (non-Javadoc)
	 * @see com.mercatis.jms.JmsConnectionService#createConnection(java.util.Map)
	 */
	@Override
	public JmsConnection createConnection(Map<String, String> properties) throws JmsBrowserException {
		return new JBossMessagingConnection(properties); //, mbeanServerConn);
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
		return "JBoss Messaging (v1.4.3)";
	}
}
