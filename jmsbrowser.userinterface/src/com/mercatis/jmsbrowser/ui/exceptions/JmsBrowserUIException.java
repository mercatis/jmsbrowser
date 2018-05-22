package com.mercatis.jmsbrowser.ui.exceptions;


public class JmsBrowserUIException extends Exception {
	private static final long serialVersionUID = 8974670925841051950L;

	public JmsBrowserUIException(String msg) {
		super(msg);
	}

	public JmsBrowserUIException(Throwable t) {
		super(t);
	}

	public JmsBrowserUIException(String msg, Throwable t) {
		super(msg, t);
	}

}
