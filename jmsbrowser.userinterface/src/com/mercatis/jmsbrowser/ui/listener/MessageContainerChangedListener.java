package com.mercatis.jmsbrowser.ui.listener;

import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;

public interface MessageContainerChangedListener {
	public void onNewMessage(int index, ArchivedMessage msg);
	
	public void onMessageChanged(int index, ArchivedMessage msg);
	
	public void onMessageRemoved(int index);
}
