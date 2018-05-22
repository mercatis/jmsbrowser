package com.mercatis.jms.openmq_4_4_0;

import java.util.List;
import java.util.Map;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.LibraryDependency;

public class OpenMqConnectionService implements JmsConnectionService {
	@Override
	public JmsConnection createConnection(Map<String, String> properties) throws JmsBrowserException {
		return new OpenMqConnection(properties);
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
		return "OpenMQ (v4)";
	}
}
