package com.mercatis.jms;

public interface JmsDestinationListener {
	public void onMessage(javax.jms.Message msg);
	
	public void onPurge();
}
