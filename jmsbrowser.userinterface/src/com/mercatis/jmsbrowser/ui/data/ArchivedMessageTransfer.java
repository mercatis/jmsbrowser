package com.mercatis.jmsbrowser.ui.data;

import org.eclipse.swt.dnd.ByteArrayTransfer;

import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;

public class ArchivedMessageTransfer extends ByteArrayTransfer {
	private static final String TYPE_NAME = ArchivedMessage.class.getCanonicalName();
	private static final int TYPE_ID = registerType(TYPE_NAME);
	
	private static ArchivedMessageTransfer _instance = new ArchivedMessageTransfer();

	public static ArchivedMessageTransfer getInstance() {
		return _instance;
	}
	
	@Override
	protected int[] getTypeIds() {
		return new int[] { TYPE_ID };
	}

	@Override
	protected String[] getTypeNames() {
		return new String[] { TYPE_NAME };
	}

}
