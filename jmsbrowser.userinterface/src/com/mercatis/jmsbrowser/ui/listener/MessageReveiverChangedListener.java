package com.mercatis.jmsbrowser.ui.listener;

import javax.jms.Message;

public interface MessageReveiverChangedListener {
	public void onNewMessage(int index, Message msg, int total, int filtered);

	public void onMessageChanged(int index, Message msg);

	public void onMessageRemoved(int index, int total, int filtered);
	
	public void onMessageRemovedAll();

	public void onMessageChangedAll(int total, int filtered);
}
