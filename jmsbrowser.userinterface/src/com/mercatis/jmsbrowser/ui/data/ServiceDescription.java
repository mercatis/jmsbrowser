package com.mercatis.jmsbrowser.ui.data;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import com.mercatis.jms.JmsConnectionService;

public class ServiceDescription {
	public final String displayName;
	public final String id;
	public final JmsConnectionService service;
	public final IConfigurationElement confElement;
	
	public ServiceDescription(IConfigurationElement element) throws CoreException {
		id = element.getAttribute("name");
		service = (JmsConnectionService) element.createExecutableExtension("class");
		displayName = service.getDisplayName();
		confElement = element;
	}
}
