package com.mercatis.jms.hornetq_2_2_2;

import java.util.List;
import java.util.Map;
import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.LibraryDependency;

public class HornetQConnectionService implements JmsConnectionService {

	@Override
	public JmsConnection createConnection(Map<String, String> properties) throws JmsBrowserException {
		// this static constructor is needed to keep this class clean of tibco ems dependencies
		return new HornetQConnection(properties);
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
		return "HornetQ (v2.2)";
	}
}
