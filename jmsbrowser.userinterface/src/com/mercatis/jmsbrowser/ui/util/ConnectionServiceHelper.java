package com.mercatis.jmsbrowser.ui.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jmsbrowser.ui.data.ServiceDescription;

public class ConnectionServiceHelper {
	public static ServiceDescription[] getAllServices() {
		List<ServiceDescription> matches = new LinkedList<ServiceDescription>();
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getExtensionPoint("com.mercatis.jms.connectionService").getConfigurationElements();
		for (IConfigurationElement element : elements) {
			try {
				ServiceDescription sd = new ServiceDescription(element);
				matches.add(sd);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		return matches.toArray(new ServiceDescription[matches.size()]);
	}
	
	public static JmsConnectionService getService(IConfigurationElement element) throws CoreException {
		return (JmsConnectionService) element.createExecutableExtension("class");
	}
	
	public static String[] getAllServiceNames() throws CoreException {
		ServiceDescription[] services = getAllServices();
		final int length = services.length;
		String[] names = new String[length];
		for (int i=0; i<length; ++i)
			names[i] = services[i].displayName;
		Arrays.sort(names);
		return names;
	}
	
	public static ServiceDescription getService(String displayName) throws CoreException {
		ServiceDescription[] services = getAllServices();
		for (ServiceDescription service : services) {
			if (displayName.equals(service.displayName))
				return service;
		}
		return null;
	}
}
