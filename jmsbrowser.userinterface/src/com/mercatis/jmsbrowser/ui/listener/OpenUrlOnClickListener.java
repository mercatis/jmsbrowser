package com.mercatis.jmsbrowser.ui.listener;

import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;

public class OpenUrlOnClickListener implements SelectionListener {
	
	private final String target;
	
	public OpenUrlOnClickListener(String url) {
		target = url;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		org.eclipse.swt.program.Program.launch(target);	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// ignore
	}

}
