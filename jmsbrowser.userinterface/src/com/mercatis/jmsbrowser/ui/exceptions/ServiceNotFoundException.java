package com.mercatis.jmsbrowser.ui.exceptions;

public class ServiceNotFoundException extends JmsBrowserUIException {
	private static final long serialVersionUID = -7183553842539086039L;

	private static String format(String service) {
		return new StringBuilder("Unable to find service '").append(service).append("'.").toString();
	}
	
	public ServiceNotFoundException(String service, Throwable t) {
		super(format(service), t);
	}
	
	public ServiceNotFoundException(String service) {
		super(format(service));
	}

}
