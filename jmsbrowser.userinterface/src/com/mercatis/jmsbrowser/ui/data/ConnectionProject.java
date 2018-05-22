package com.mercatis.jmsbrowser.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.prefs.BackingStoreException;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsConnectionListener;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsDurableSubscription;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.MessageCountListener;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.Activator;
import com.mercatis.jmsbrowser.ui.exceptions.JmsBrowserUIException;
import com.mercatis.jmsbrowser.ui.exceptions.ServiceNotFoundException;
import com.mercatis.jmsbrowser.ui.util.store.ColorStore;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;
import com.mercatis.jmsbrowser.ui.view.DestinationMonitorView;

public class ConnectionProject implements JmsConnectionListener {
	private final static String itemLabels[] = { "Queues", "Topics" };
	private final static DestinationType itemTypes[] = { DestinationType.QUEUE, DestinationType.TOPIC };
	private final static String QUEUECOUNT_KEY = "QueueCountCallback";
	private final static String SUBSCCOUNT_KEY = "SubscCountCallback";
	private final static String PREF_PRETTYFORMAT = "PrettyFormatDestinationNames";
	private IProject project;
	private TreeItem treeRoot;
	private TreeItem itemGroups[] = new TreeItem[itemLabels.length];
	private JmsConnectionService service;
	private JmsConnection jmsConn;
	private int redThreshold = 500;

	private class CountCallback implements MessageCountListener, Runnable {
		private final Display d;
		private final TreeItem tItem;
		private int nextVal;

		public CountCallback(TreeItem ti) {
			d = ti.getDisplay();
			tItem = ti;
		}

		// QueueCountListener
		@Override
		public void onUpdate(int messageCount) {
			if (d.isDisposed())
				return;
			nextVal = messageCount;
			d.asyncExec(this);
		}

		private boolean colorEqual(Color c1, Color c2) {
			return c1.getRed() == c2.getRed() && c1.getBlue() == c2.getBlue() && c1.getGreen() == c2.getGreen();
		}

		// Runnable
		@Override
		public void run() {
			// Safety first
			if (tItem.isDisposed())
				return;
			Color c = tItem.getForeground();
			if (nextVal > 0 && nextVal < redThreshold && !colorEqual(ColorStore.black, c))
				tItem.setForeground(null);
			tItem.setText(1, Integer.toString(nextVal));
			if (nextVal == 0 && !colorEqual(ColorStore.darkgrey, c))
				tItem.setForeground(ColorStore.darkgrey);
			if (nextVal >= redThreshold && !colorEqual(ColorStore.darkred, c))
				tItem.setForeground(ColorStore.darkred);
		}
	}

	private JmsConnectionService getConnectionService(String serviceName) throws ServiceNotFoundException {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getExtensionPoint("com.mercatis.jms.connectionService").getConfigurationElements();
		try {
			for (IConfigurationElement element : elements) {
				String name = element.getAttribute("name");
				if (name != null && name.equals(serviceName)) {
					JmsConnectionService service = (JmsConnectionService) element.createExecutableExtension("class");
					return service;
				}
			}
		} catch (CoreException e) {
			throw new ServiceNotFoundException(serviceName, e);
		}
		throw new ServiceNotFoundException(serviceName);
	}

	public ConnectionProject(IProject prj, TreeItem ti) throws JmsBrowserUIException {
		project = prj;
		treeRoot = ti;
		treeRoot.setImage(IconStore.connection_closed);
		String name = getProjectName();
		NullProgressMonitor pm = new NullProgressMonitor();
		try {
			if (!project.isOpen())
				project.open(pm);

			final IEclipsePreferences prefs = getPrefs();
			initJmsConnection(prefs);

			treeRoot.setText(name);
			treeRoot.setData(this);
		} catch (JmsBrowserException je) {
			LogUtil.logError("Error connecting to " + getProjectName() + ": " + je.getMessage(), je);
		} catch (CoreException ce) {
			throw new JmsBrowserUIException("Error opening project", ce);
		} catch (BackingStoreException be) {
			throw new JmsBrowserUIException("Error opening project", be);
		} finally {
			// we want a closed project
			try {
				project.close(pm);
			} catch (CoreException e) { /* ignore */}
		}
	}

	public IEclipsePreferences getPrefs() {
		IScopeContext projectScope = new ProjectScope(project);
		return projectScope.getNode(Activator.PLUGIN_ID);
	}

	public void initJmsConnection(final IEclipsePreferences prefs) throws JmsBrowserException, ServiceNotFoundException, BackingStoreException {
		String serviceName = prefs.get("connectionService", null);
		service = getConnectionService(serviceName);

		Map<String, String> connectionPrefs = new HashMap<String, String>();
		for (String key : prefs.keys()) {
			connectionPrefs.put(key, prefs.get(key, ""));
		}
		if (jmsConn != null && jmsConn.isOpen()) {
			jmsConn.close();
			jmsConn = null;
		}
		jmsConn = service.createConnection(connectionPrefs);
		jmsConn.addConnectionListener(this, null);
	}

	public int getArchiveCount() {
		return 0;
	}

	public boolean isConnected() {
		return project.isOpen() && jmsConn.isOpen();
	}

	public boolean isOpen() {
		return project.isOpen();
	}

	public void open() {
		final String name = getProjectName();
		Job job = new Job("Connect " + name) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						treeRoot.setImage(IconStore.connection_connecting);
					}
				});
				try {
					if (!project.isOpen())
						project.open(new NullProgressMonitor());
					if (!jmsConn.isOpen())
						jmsConn.open();
				} catch (final CoreException e) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							LogUtil.logError("Error opening " + name + ": " + e.getMessage(), e);
						}
					});
				} catch (final JmsBrowserException e) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							LogUtil.logError("Error connecting to " + name + ": " + e.getMessage(), e);
							if (!isConnected()) {
								try {
									close();
								} catch (CoreException e) {
									LogUtil.logError("Error closing " + name + ": " + e.getMessage(), e);
								}
							}
						}
					});
				}
				return Status.OK_STATUS;
			}
		};
		// Start the Job
		job.schedule();
	}

	public void close() throws CoreException {
		String prefix = project.getName() + "!!";
		for (IWorkbenchPage page : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPages()) {
			IViewReference views[] = page.getViewReferences();
			for (IViewReference ref : views) {
				String secID = ref.getSecondaryId();
				if (DestinationMonitorView.ID.equals(ref.getId()) && secID != null && secID.startsWith(prefix))
					page.hideView(ref);
			}
		}
		if (jmsConn.isOpen())
			jmsConn.close();
		if (project.isOpen())
			project.close(new NullProgressMonitor());
		if (!treeRoot.isDisposed()) {
			treeRoot.setImage(IconStore.connection_closed);
			refreshTree();
		}
	}

	private void cleanTree(TreeItem ti) {
		for (TreeItem item : ti.getItems()) {
			cleanTree(item);
			item.dispose();
		}
		Object o = ti.getData(QUEUECOUNT_KEY);
		if (o != null)
			jmsConn.removeQueueCountListener((JmsDestination) ti.getData(), (CountCallback) o);
		o = ti.getData(SUBSCCOUNT_KEY);
		if (o != null)
			jmsConn.removeSubscriberCountListener((JmsDurableSubscription<?>) ti.getData(), (CountCallback) o);
	}

	public void refreshTreeDestination(TreeItem ti, DestinationType type) {
		cleanTree(ti);
		try {
			ti.setData(type);
			List<JmsDestination> dests = jmsConn.getDestinations(type);
			Collections.sort(dests);
			ti.setText(1, Integer.toString(dests.size()));
			for (JmsDestination dest : dests) {
				TreeItem qItem = new TreeItem(ti, SWT.NONE);
				qItem.setText(getPrefs().getBoolean(PREF_PRETTYFORMAT, true) ? prettyFormat(dest.name) : dest.name);
				qItem.setData(dest);

				if (dest.type == DestinationType.QUEUE) {
					CountCallback cc = new CountCallback(qItem);
					qItem.setImage(IconStore.queue);
					qItem.setText(1, "?");
					qItem.setData(QUEUECOUNT_KEY, cc);
					jmsConn.addQueueCountListener(dest, cc);
				} else {
					qItem.setImage(IconStore.topic);
					if (JmsFeature.isSupported(jmsConn, JmsFeature.DURABLES_LIST)) {
						try {
							List<JmsDurableSubscription<?>> subs = jmsConn.getDurableSubscriptions(dest.name);
							refreshSubscribers(qItem, subs);
						} catch (JmsBrowserException e) {
							LogUtil.logError("Error getting durable subscriptions: " + e.getMessage(), e);
						}
					}
				}
			}
			ti.setImage(dests.size() > 0 ? IconStore.folder_open : IconStore.folder_closed);
			ti.setExpanded(true);
		} catch (JmsBrowserException e) {
			ti.setText(1, ":-(");
		}
	}

	public void refreshSubscribers(TreeItem ti, List<JmsDurableSubscription<?>> subs) {
		cleanTree(ti);
		//			ti.setData(type);
		if (subs == null)
			return;
		Collections.sort(subs);
		int size = subs.size();
		ti.setText(1, size > 0 ? Integer.toString(size) : "");
		for (JmsDurableSubscription<?> sub : subs) {
			TreeItem qItem = new TreeItem(ti, SWT.NONE);
			qItem.setImage(IconStore.durable_subscriber);
			qItem.setText(sub.name);
			qItem.setText(1, "?");
			qItem.setData(sub);
			CountCallback cc = new CountCallback(qItem);
			qItem.setData(SUBSCCOUNT_KEY, cc);
			jmsConn.addSubscriberCountListener(sub, cc);
		}
		ti.setExpanded(false);
	}

	public void refreshTree() {
		cleanTree(treeRoot);
		if (jmsConn == null || !jmsConn.isOpen())
			return;
		final String serviceName = getProjectName();
		final List<String> unsupported = new ArrayList<String>();
		if (!JmsFeature.isSupported(jmsConn, JmsFeature.DURABLES_ALL)) {
			unsupported.add("durable supscriptions");
		} else {
			if (!JmsFeature.isSupported(jmsConn, JmsFeature.DURABLES_LIST)) {
				unsupported.add("listing durable supscriptions");
			}
			if (!JmsFeature.isSupported(jmsConn, JmsFeature.DURABLES_COUNT)) {
					unsupported.add("durable supscriptions message counting");
			}
			if (!JmsFeature.isSupported(jmsConn, JmsFeature.DURABLES_PURGE)) {
				unsupported.add("purging durable supscriptions");
			}
			if (!JmsFeature.isSupported(jmsConn, JmsFeature.DESTINATION_CREATE)) {
				unsupported.add("creating destination (non-dynamic)");
			}
		}
		final int size = unsupported.size();
		if (size > 0) {
			final StringBuilder sb = new StringBuilder("The connection '" + serviceName + "' does not support ");
			for (int i=0; i<size-1; ++i) {
				sb.append(unsupported.get(i));
				sb.append((i!=size-2) ? ", " : " & ");
			}
			sb.append(unsupported.get(size-1));
			LogUtil.logWarn(sb.toString());
		}
		for (int i = 0; i < itemGroups.length; ++i) {
			itemGroups[i] = new TreeItem(treeRoot, SWT.NONE);
			itemGroups[i].setText(itemLabels[i]);
			if (itemTypes[i] == null)
				continue;
			refreshTreeDestination(itemGroups[i], itemTypes[i]);
		}
		treeRoot.setExpanded(true);
	}

	private String prettyFormat(String name) {
		int lastPoint = name.lastIndexOf('.');
		if (lastPoint == -1)
			return name;
		return String.format("%s (%s)", name.substring(lastPoint + 1), name.substring(0, lastPoint));
	}

	public String getProjectName() {
		return project.getName();
	}

	public IProject getProject() {
		return project;
	}

	// JmsConnectionListener
	@Override
	public void onDisconnect(JmsConnection conn, Object callbackData) {
		treeRoot.getDisplay().syncExec(new Runnable() {
			//			@Override
			@Override
			public void run() {
				treeRoot.setImage(IconStore.connection_connecting);
				LogUtil.logError("Connection to " + getProjectName() + " was lost, beginning background reconnect.", null);
			}
		});
	}

	// JmsConnectionListener
	@Override
	public void onReconnect(JmsConnection conn, Object callbackData) {
		treeRoot.getDisplay().asyncExec(new Runnable() {
			//			@Override
			@Override
			public void run() {
				treeRoot.setImage(IconStore.connection_established);
				refreshTree();
				LogUtil.logInfo("Connection established to " + getProjectName());
			}
		});
	}

	public void dispose() {
		cleanTree(treeRoot);
		treeRoot.dispose();
	}

	public JmsConnection getJmsConnection() {
		return jmsConn;
	}
}
