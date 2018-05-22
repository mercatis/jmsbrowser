package com.mercatis.jmsbrowser.ui.data;

import org.eclipse.core.runtime.IConfigurationElement;

public class ConnectionProperty {
	public final String name;
	public final boolean mandatory;
	public final String type;
	public final String defaultValue;
	
	public ConnectionProperty(IConfigurationElement propertyElement) {
		name = propertyElement.getAttribute("name");
		type = propertyElement.getAttribute("type");
		defaultValue = propertyElement.getAttribute("defaultValue");
		mandatory = propertyElement.getAttribute("mandatory").equals("true");
	}
}
