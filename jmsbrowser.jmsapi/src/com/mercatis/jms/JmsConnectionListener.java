package com.mercatis.jms;

public interface JmsConnectionListener {
	public void onDisconnect(JmsConnection conn, Object callbackData);

	public void onReconnect(JmsConnection conn, Object callbackData);
}
