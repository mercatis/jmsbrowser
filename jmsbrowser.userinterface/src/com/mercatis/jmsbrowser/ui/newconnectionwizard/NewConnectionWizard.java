package com.mercatis.jmsbrowser.ui.newconnectionwizard;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.osgi.service.prefs.BackingStoreException;

import com.mercatis.jmsbrowser.ui.Activator;
import com.mercatis.jmsbrowser.ui.data.ServiceDescription;
import com.mercatis.jmsbrowser.ui.exceptions.JmsBrowserUIException;
import com.mercatis.jmsbrowser.ui.listener.ProjectCreationListener;

public class NewConnectionWizard extends Wizard {

	private ConnectionServicePage page1;
	private ClientLibHelperPage page2;
	private ConnectionSettingsPage page3;
	
	private final ProjectCreationListener callback;
	
	public NewConnectionWizard(ProjectCreationListener listener) {
		super();
		setWindowTitle("New connection...");
		callback = listener;
	}
	
	@Override
	public void addPages() {
		page1 = new ConnectionServicePage();
		page2 = new ClientLibHelperPage();
		page3 = new ConnectionSettingsPage();

		addPage(page1);
		addPage(page2);
		addPage(page3);
	}
	
	@Override
	public boolean canFinish() {
		if (getContainer().getCurrentPage()==page3)
			return page3.validatePage();
		return false;
	}
	
	@Override
	public boolean performFinish() {
		IProgressMonitor pm = new NullProgressMonitor();
		try {
			IProject ip = ResourcesPlugin.getWorkspace().getRoot().getProject(page1.getProjectName());
			ip.create(pm);
			ip.open(pm);
			
			IScopeContext projectScope = new ProjectScope(ip);
			IEclipsePreferences prefs = projectScope.getNode(Activator.PLUGIN_ID);
			
			prefs.put("connectionService", page3.getConnectionService().id);
			prefs.putInt("MaxMessagesShown", 500);
			prefs.putInt("QueueRedThreshold", 100);
			page3.getProperties(prefs);
		
			prefs.flush();
			prefs.sync();
			
			callback.onNewProject(ip);
			
			return true;
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		} catch (JmsBrowserUIException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		ServiceDescription service;
		try {
			service = page1.getConnectionService();
			if (service!=null) {
				if (!page2.setService(service) && page instanceof ConnectionServicePage) {
					return page2;
				}
				page3.setServiceName(service);
				return page3;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return super.getNextPage(page);
	}
}
