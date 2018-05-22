package com.mercatis.jms;

public class JmsBrowserException extends Exception {
	private static final long serialVersionUID = 1L;

	public JmsBrowserException(String message) {
		super(message);
	}

	public JmsBrowserException(String message, Throwable cause) {
		super(message, cause);
	}
}
