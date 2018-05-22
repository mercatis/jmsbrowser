package com.mercatis.jmsbrowser.ui;

import java.io.File;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import com.mercatis.jms.log.LogUtil;

/**
 * This class controls all aspects of the application's execution
 */
public class Application implements IApplication {
	public final File profileDir;

	public Application() {
		String os = System.getProperty("os.name").toLowerCase();
		String subDir = System.getProperty("user.home");
		String appDir = ".jmsbrowser";
		// possible values: AIX, Digital Unix, FreeBSD, HP UX, Irix, Linux, Mac OS, Mac OS X, MPE/iX, Netware 4.11, OS/2, Solaris,
		//                  Windows 2000, Windows 7, Windows 95, Windows 98, Windows NT, Windows Vista, Windows XP
		if (os.startsWith("windows")) {
			subDir = System.getenv("APPDATA");
			appDir = "JmsBrowser";
		}
		if (os.equals("mac os x")) {
			subDir = System.getProperty("user.home") + "/Library/Application Support";
			appDir = "JmsBrowser";
		}
		profileDir = new File(subDir, appDir);
		LogUtil.logInfo("Profile directory is \""+profileDir.toString()+"\"");
	}
	
	@Override
	public Object start(IApplicationContext context) {
		Display display = PlatformUI.createDisplay();
		try {
			Location loc = Platform.getInstanceLocation();
			loc.release();
			loc.set(profileDir.toURI().toURL(), false);

			int returnCode = PlatformUI.createAndRunWorkbench(display, new ApplicationWorkbenchAdvisor());
			if (returnCode == PlatformUI.RETURN_RESTART) {
				return IApplication.EXIT_RESTART;
			}
			return IApplication.EXIT_OK;
		} catch (Throwable t) {
			if (t.getMessage().compareTo("restart")==0) // workspace not initialized yet
				return IApplication.EXIT_RESTART;
			return IApplication.EXIT_OK;
		} finally {
			display.dispose();
		}
	}

	@Override
	public void stop() {
		if (!PlatformUI.isWorkbenchRunning())
			return;
		final IWorkbench workbench = PlatformUI.getWorkbench();
		final Display display = workbench.getDisplay();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				if (!display.isDisposed())
					workbench.close();
			}
		});
	}
}
