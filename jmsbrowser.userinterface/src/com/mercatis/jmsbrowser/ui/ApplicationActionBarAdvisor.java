package com.mercatis.jmsbrowser.ui;

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;

import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class ApplicationActionBarAdvisor extends ActionBarAdvisor {

	public ApplicationActionBarAdvisor(final IActionBarConfigurer configurer) {
		super(configurer);
	}

	@Override
	protected void fillStatusLine(final IStatusLineManager statusLine) {
		LogUtil.refresh(statusLine, IconStore.log_info, IconStore.log_warn, IconStore.log_error);
		super.fillStatusLine(statusLine);
	}
}
