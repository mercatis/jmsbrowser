package com.mercatis.jmsbrowser.ui;

import java.util.Dictionary;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.osgi.framework.Bundle;
import com.mercatis.jms.log.LogUtil;

public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor {
	private static String version;

	public ApplicationWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
		super(configurer);
	}

	@Override
	public ActionBarAdvisor createActionBarAdvisor(IActionBarConfigurer configurer) {
		return new ApplicationActionBarAdvisor(configurer);
	}
	
	public static String getVersion() {
		Bundle bundle = Platform.getBundle("jmsbrowser.userinterface");
		Dictionary<String, String> headers = bundle.getHeaders();
		String bundleVer = headers.get("Bundle-Version");
		int endIdx = bundleVer.lastIndexOf('.');
		String ver = bundleVer.substring(0, endIdx);
		while (ver.endsWith(".0")) {
			endIdx-=2;
			ver = ver.substring(0, endIdx);
		}
		return "JMS Browser " + ver;
	}
	
	@Override
	public void preWindowOpen() {
		version = getVersion();
		
		IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		configurer.setInitialSize(new Point(800, 600));
		configurer.setShowCoolBar(false);
		configurer.setShowStatusLine(true);
		configurer.setTitle(version);
	}
	
	@Override
	public void postWindowOpen() {
		super.postWindowOpen();
		LogUtil.logInfo(version + " ready.");
	}

	@Override
	public boolean isDurableFolder(String perspectiveId, String folderId) {
		if( Perspective.viewFolderID.equals(folderId)) {
			return true; 
		}
		return super.isDurableFolder(perspectiveId, folderId);
	}
}
