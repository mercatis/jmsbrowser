package com.mercatis.jmsbrowser.ui.view;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.jms.Message;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.prefs.BackingStoreException;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsDurableSubscription;
import com.mercatis.jms.JmsFeature;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.data.ArchivedMessageTransfer;
import com.mercatis.jmsbrowser.ui.data.ConnectionProject;
import com.mercatis.jmsbrowser.ui.data.MessageContainer;
import com.mercatis.jmsbrowser.ui.exceptions.JmsBrowserUIException;
import com.mercatis.jmsbrowser.ui.listener.ProjectCreationListener;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;
import com.mercatis.jmsbrowser.ui.newconnectionwizard.NewConnectionWizard;
import com.mercatis.jmsbrowser.ui.util.ArchiveMenuMaker;
import com.mercatis.jmsbrowser.ui.util.MenuUtil;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class ProjectsTreeView extends ViewPart implements ControlListener, DropTargetListener, ProjectCreationListener, MenuDetectListener, MouseListener {
	public static final String ID = "com.mercatis.jmsbrowser.view.tree";

	private static enum CPCOMMAND {
		Refresh, Disconnect, Connect, Delete, OpenViewProps
	};

	private static enum MCCOMMAND {
		Open, CreateSub, Rename, Delete
	};

	private Shell shell;
	private Tree tree;
	private MessageContainer archiveRoot;

	private List<ConnectionProject> projects;

	private boolean isLinux = false;
	private boolean isWindows = false;

	protected class StateIconListener implements TreeListener {
		@Override
		public void treeCollapsed(TreeEvent e) {
			TreeItem ti = (TreeItem) e.item;
			Object data = ti.getData();
			if (data instanceof DestinationType)
				ti.setImage(IconStore.folder_closed);
		}

		@Override
		public void treeExpanded(TreeEvent e) {
			TreeItem ti = (TreeItem) e.item;
			Object data = ti.getData();
			if (data instanceof DestinationType)
				ti.setImage(IconStore.folder_open);
		}
	}

	private class ConnectionProjectExecutionListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			CPCOMMAND cmd = null;
			Object src = e.getSource();
			if (src instanceof MenuItem) {
				MenuItem mi = (MenuItem) e.getSource();
				cmd = (CPCOMMAND) mi.getData();
				TreeItem ti = tree.getSelection()[0];
				if (cmd == null) {
					LogUtil.logWarn("Unable to execute anything for the source " + src.getClass().getCanonicalName());
					return;
				}
				Object data = ti.getData();
				ConnectionProject proj = (ConnectionProject) data;
				final String name = proj.getProjectName();
				switch (cmd) {
					case Refresh:
						proj.refreshTree();
						LogUtil.logInfo("Refresh done for " + proj.getProjectName());
						break;
					case Disconnect:
						try {
							proj.close();
							LogUtil.logInfo("Disconnected from " + name);
						} catch (CoreException ex) {
							LogUtil.logError("Error closing " + name + ": " + ex.getMessage(), ex);
						}
						break;
					case Connect:
						proj.open();
						break;
					case Delete:
						if (MessageDialog.openQuestion(shell, "Remove " + name + " from disk?", "Do you really want to remove the connection " + name
								+ " and all its dependencies from your hard disk?")) {
							try {
								delete(proj);
								LogUtil.logInfo("Removed connection " + name);
							} catch (CoreException ex) {
								LogUtil.logError("Error removing connection " + name + " " + ex.getMessage(), ex);
							}
						} else {
							LogUtil.logInfo("Deletion of connection " + name + " cancelled.");
						}
						break;
					case OpenViewProps:
						String viewID = proj.getProjectName();
						try {
							PropertiesEditorView view = (PropertiesEditorView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
									.showView(PropertiesEditorView.ID, viewID, IWorkbenchPage.VIEW_ACTIVATE);
							view.setConnectionProject(proj);
						} catch (PartInitException ex) {
							LogUtil.logError("Error opening properties editor for " + viewID, ex);
						} catch (BackingStoreException ex) {
							LogUtil.logError("Error initializing properties editor for " + viewID, ex);
						} catch (CoreException ex) {
							LogUtil.logError("Error accessing connection details for " + viewID, ex);
						}
						break;
				}
			}
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
	}

	public ProjectsTreeView() {
		String os = System.getProperty("os.name").toLowerCase();
		isLinux = os.equals("linux");
		isWindows = os.startsWith("windows");
	}

	private void openArchiveView(MessageContainer mc) {
		String viewID = mc.getFolderPath();
		try {
			MessageArchiveView view = (MessageArchiveView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
					.showView(MessageArchiveView.ID, viewID, IWorkbenchPage.VIEW_ACTIVATE);
			view.setMessageContainer(mc);
		} catch (PartInitException ex) {
			LogUtil.logError("Error opening archive view for " + viewID + ": " + ex.getMessage(), ex);
		}
	}

	private void openMonitorView(TreeItem ti, JmsDestination dest) {
		final TreeItem root = ti.getParentItem().getParentItem();
		final ConnectionProject cp = (ConnectionProject) root.getData();
		DestinationMonitorView view = null;
		try {
			String viewID = cp.getProjectName() + "!!" + dest.toString();
			view = (DestinationMonitorView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
					.showView(DestinationMonitorView.ID, viewID, IWorkbenchPage.VIEW_ACTIVATE);
			view.setDataSources(cp, dest, 500, new ArchiveMenuMaker(archiveRoot));
		} catch (PartInitException ex) {
			LogUtil.logError("Error opening view for " + JmsDestination.getTypeName(dest.type) + " " + dest.name, ex);
		} catch (JmsBrowserException ex) {
			view.dispose();
			LogUtil.logError("Error opening view for " + JmsDestination.getTypeName(dest.type) + " " + dest.name, ex);
		}
	}

	private class MessageContainerExecutionListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			MCCOMMAND cmd = null;
			//			boolean pushed = false;
			Object src = e.getSource();
			if (src instanceof MenuItem) {
				MenuItem mi = (MenuItem) e.getSource();
				cmd = (MCCOMMAND) mi.getData();
				TreeItem ti = tree.getSelection()[0];
				//				pushed = mi.getSelection();
				if (cmd == null) {
					LogUtil.logWarn("Unable to execute anything for the source " + src.getClass().getCanonicalName());
					return;
				}
				Object data = ti.getData();
				MessageContainer mc = (MessageContainer) data;
				switch (cmd) {
					case Open:
						openArchiveView(mc);
						break;
					case CreateSub:
						mc.createNewSubFolder();
						break;
					case Delete:
						if (MessageDialog.openConfirm(shell, "Delete ", "Are you sure you want to permanently delete the folder " + mc.getName()
								+ " and all of its subfolders?")) {
							mc.delete();
						}
						break;
					case Rename:
						mc.rename();
						break;
				}
			}
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
	}

	private ConnectionProjectExecutionListener cpExecutionListener = new ConnectionProjectExecutionListener();
	private MessageContainerExecutionListener mcExecutionListener = new MessageContainerExecutionListener();

	private void addDropTarget(Control c) {
		DropTarget target = new DropTarget(c, DND.DROP_COPY | DND.DROP_DEFAULT);
		target.setTransfer(new Transfer[] { ArchivedMessageTransfer.getInstance() });
		target.addDropListener(this);
	}

	@Override
	public void createPartControl(Composite parent) {
		tree = new Tree(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		tree.setRedraw(false);
		tree.setHeaderVisible(true);
		tree.addMouseListener(this);

		TreeEditor editor = new TreeEditor(tree);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		tree.setData(editor);

		TreeColumn tc = new TreeColumn(tree, SWT.LEFT);
		tc.setText("Connections");
		tc.setMoveable(false);
		if (isLinux)
			tc.setWidth(180);

		tc = new TreeColumn(tree, SWT.RIGHT);
		tc.setWidth(40);
		tc.setText("#");
		tc.setMoveable(false);

		tree.addControlListener(this);
		tree.addTreeListener(new StateIconListener());
		tree.addMenuDetectListener(this);

		addDropTarget(tree);

		File archiveFolder = new File(Platform.getLocation().toFile(), "_archive");
		try {
			if (!archiveFolder.exists()) {
				if (!archiveFolder.mkdir())
					LogUtil.logError("error creating message archive", null);
			}
			if (archiveFolder.exists() && archiveFolder.isDirectory())
				archiveRoot = new MessageContainer(archiveFolder, new TreeItem(tree, SWT.NONE));
			else
				LogUtil.logError("error accessing message archive", null);
		} catch (Exception e) {
			LogUtil.logError("error creating message archive: " + e.getMessage(), e);
		}

		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		projects = new ArrayList<ConnectionProject>(allProjects.length);

		if (allProjects.length == 0) {
			NewConnectionWizard myWizard = new NewConnectionWizard(this);
			WizardDialog wizardDialog = new WizardDialog(shell, myWizard);
			wizardDialog.create();
			wizardDialog.setBlockOnOpen(false);
			wizardDialog.open();
		}

		ConnectionProject cp;
		TreeItem ti;
		for (IProject p : allProjects) {
			ti = null;
			try {
				ti = new TreeItem(tree, SWT.NONE);
				cp = new ConnectionProject(p, ti);
				projects.add(cp);
			} catch (Exception e) {
				if (ti != null)
					ti.dispose();
				if (MessageDialog.openQuestion(shell, "Error opening connection!", "Error opening connection " + p.getName()
						+ "\n\nDo you want to permanently remove the connection?")) {
					NullProgressMonitor npm = new NullProgressMonitor();
					try {
						p.close(npm);
						p.delete(true, true, npm);
					} catch (CoreException e1) {
						MessageDialog.openError(shell, "Error deleting connection!", "Unable to delete " + p.getName() + ":\n\n" + e1.getMessage());
					}
				}
			}
		}
		tree.setRedraw(true);
	}

	@Override
	public void dispose() {
		// close all projects when the view gets disposed 
		for (ConnectionProject cp : projects)
			try {
				cp.close();
			} catch (CoreException e) { /* ignore */}
		super.dispose();
	}

	@Override
	public void setFocus() {
		LogUtil.refresh(getViewSite().getActionBars().getStatusLineManager());
		tree.setFocus();
	}

	/*
	 * custom non-overridden methods
	 */

	public Shell getShell() {
		return shell;
	}

	public void delete(ConnectionProject proj) throws CoreException {
		String projName = proj.getProjectName();
		// close open properties view 
		for (IWorkbenchPage page : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPages()) {
			IViewReference views[] = page.getViewReferences();
			for (IViewReference ref : views) {
				String secID = ref.getSecondaryId();
				if (PropertiesEditorView.ID.equals(ref.getId()) && secID != null && secID.equals(projName)) {
					// we found the properties view and there can only be one
					page.hideView(ref);
					break;
				}
			}
		}
		try {
			proj.close();
		} catch (CoreException e) { /* ignore */}
		proj.getProject().delete(true, true, new NullProgressMonitor());
		projects.remove(proj);
		proj.dispose();
	}

	/*
	 * listener methods
	 */

	// ControlListener
	@Override
	public void controlMoved(ControlEvent e) {}

	// ControlListener
	@Override
	public void controlResized(ControlEvent e) {
		Tree t = (Tree) e.getSource();
		TreeColumn col = t.getColumn(0);
		t.getColumn(1).pack();
		int fix = t.getColumn(1).getWidth();
		col.setWidth(Math.min(t.getClientArea().width - fix, t.getSize().x - fix));
	}

	// ProjectCreationListener
	@Override
	public void onNewProject(IProject project) throws JmsBrowserUIException {
		projects.add(new ConnectionProject(project, new TreeItem(tree, SWT.NONE)));
	}

	// MenuDetectListener
	@Override
	public void menuDetected(MenuDetectEvent e) {
		Point p = tree.toControl(new Point(e.x, e.y));
		final TreeItem ti = tree.getItem(p);
		Menu m = new Menu(tree);
		if (ti == null) {
			MenuUtil.createItem(m, "Create new connection...", null, SWT.PUSH, IconStore.connection_established, new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					NewConnectionWizard myWizard = new NewConnectionWizard(ProjectsTreeView.this);
					WizardDialog wizardDialog = new WizardDialog(shell, myWizard);
					wizardDialog.create();
					wizardDialog.setBlockOnOpen(false);
					wizardDialog.open();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
			});
		} else {
			Object data = ti.getData();
			if (data instanceof ConnectionProject) {
				ConnectionProject proj = (ConnectionProject) data;
				if (proj.isOpen()) {
					MenuUtil.createItem(m, "Refresh", CPCOMMAND.Refresh, SWT.PUSH, IconStore.generic_refresh, cpExecutionListener);
					MenuUtil.createItem(m, "Disconnect", CPCOMMAND.Disconnect, SWT.PUSH, IconStore.connection_closed, cpExecutionListener);
				} else {
					MenuUtil.createItem(m, "Connect", CPCOMMAND.Connect, SWT.PUSH, IconStore.connection_established, cpExecutionListener);
				}
				MenuUtil.createSeperator(m);
				MenuUtil.createItem(m, "Delete", CPCOMMAND.Delete, SWT.PUSH, IconStore.generic_delete, cpExecutionListener);
				MenuUtil.createSeperator(m);
				MenuUtil.createItem(m, "Properties", CPCOMMAND.OpenViewProps, SWT.PUSH, IconStore.properties_edit, cpExecutionListener);
			}
			if (data instanceof DestinationType) {
				final DestinationType type = (DestinationType) data;
				MenuUtil.createItem(m, "Reload " + JmsDestination.getTypeName(type) + "s", null, SWT.PUSH, IconStore.generic_refresh_simple,
						new SelectionListener() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								final DestinationType type = (DestinationType) ti.getData();
								final TreeItem root = ti.getParentItem();
								final ConnectionProject cp = (ConnectionProject) root.getData();
								cp.refreshTreeDestination(ti, type);
							}

							@Override
							public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
						});
				MenuUtil.createSeperator(m);
				final String typeName = JmsDestination.getTypeName(type);
				MenuUtil.createItem(m, "Create "+typeName, null, SWT.PUSH, IconStore.generic_refresh_simple,
						new SelectionListener() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								final DestinationType type = (DestinationType) ti.getData();
								final TreeItem root = ti.getParentItem();
								final ConnectionProject cp = (ConnectionProject) root.getData();
								final StringBuilder sb = new StringBuilder();
								if (!JmsFeature.isSupported(cp.getJmsConnection(), JmsFeature.DESTINATION_CREATE)) {
									sb.append("NOTE: The connection does not support direct creation of destinations. If dynamic creation is supported and enabled on the server the operation may succeed. The resulting destination may not be permanent.\n\n");
								}
								sb.append("Please enter the name of the new ").append(typeName).append('.');
								final InputDialog id = new InputDialog(shell, "Create new "+typeName+" on "+cp.getProjectName(), sb.toString(), "new"+typeName, null);
								if (id.open()==Window.OK && id.getValue()!=null && !id.getValue().isEmpty()) {
									final String name = id.getValue();
									try {
										final JmsDestination destination = new JmsDestination(type, name);
										cp.getJmsConnection().createDestinationOnServer(destination);
										cp.refreshTreeDestination(ti, destination.type);
										LogUtil.logInfo("Created " + typeName + " "+name+" from the connection "+cp.getProjectName());
									} catch (JmsBrowserException e1) {
										LogUtil.logError("Creating " + typeName + " "+name+" from the connection "+cp.getProjectName()+" + failed.", e1);
									}
								}
							}
							@Override
							public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
						});
			}
			if (data instanceof JmsDestination) {
				final JmsDestination dest = (JmsDestination) data;
				final TreeItem root = ti.getParentItem().getParentItem();
				final ConnectionProject cp = (ConnectionProject) root.getData();
				MenuUtil.createItem(m, "Browse", null, SWT.PUSH, IconStore.dest_monitor, new SelectionListener() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						openMonitorView(ti, dest);
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
				});
				if (dest.type == DestinationType.QUEUE) {
					MenuUtil.createSeperator(m);
					MenuUtil.createItem(m, "Purge", null, SWT.PUSH, IconStore.dest_purge, new SelectionListener() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							try {
								String title = "Purge Queue " + dest.name + " of connection " + cp.getProjectName() + "?";
								String msgText = "Purge all messages from " + cp.getProjectName() + " - " + dest.name
										+ "?\n\nWarning! This will remove all messages permanently.";
								if (MessageDialog.openQuestion(shell, title, msgText)) {
									cp.getJmsConnection().purgeDestination(dest);
									LogUtil.logInfo("Purged " + JmsDestination.getTypeName(dest.type) + " " + dest.name);
								}
							} catch (JmsBrowserException ex) {
								LogUtil.logError("Error purging " + JmsDestination.getTypeName(dest.type) + " " + dest.name + ": " + ex.getMessage(), ex);
							}
						}

						@Override
						public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
					});
				}
				if (JmsFeature.isSupported(cp.getJmsConnection(), JmsFeature.DESTINATION_DELETE)) {
					MenuUtil.createSeperator(m);
					MenuUtil.createItem(m, "Delete "+dest.name, null, SWT.PUSH, IconStore.generic_refresh_simple,
							new SelectionListener() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									if (MessageDialog.openQuestion(shell, "Delete "+dest.name, "Do you want to delete the " + JmsDestination.getTypeName(dest.type) + " "+dest.name+" from the connection "+cp.getProjectName()+"?")) {
										try {
											cp.getJmsConnection().deleteDestinationOnServer(dest);
											cp.refreshTreeDestination(ti.getParentItem(), dest.type);
											LogUtil.logInfo("Deleted " + JmsDestination.getTypeName(dest.type) + " "+dest.name+" from the connection "+cp.getProjectName());
										} catch (JmsBrowserException e1) {
											LogUtil.logError("Deleting " + JmsDestination.getTypeName(dest.type) + " "+dest.name+" from the connection "+cp.getProjectName()+" + failed.", e1);
										}
									}
								}
								@Override
								public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
							});
				}

			}
			if (data instanceof MessageContainer) {
				MessageContainer mc = (MessageContainer) data;
				MenuUtil.createItem(m, "Open", MCCOMMAND.Open, SWT.PUSH, IconStore.dest_monitor, mcExecutionListener);
				MenuUtil.createSeperator(m);
				MenuUtil.createItem(m, "Create Subfolder", MCCOMMAND.CreateSub, SWT.PUSH, IconStore.archive_closed, mcExecutionListener);
				if (!mc.isRoot()) {
					MenuUtil.createItem(m, "Rename", MCCOMMAND.Rename, SWT.PUSH, IconStore.generic_rename, mcExecutionListener);
					MenuUtil.createSeperator(m);
					MenuUtil.createItem(m, "Delete", MCCOMMAND.Delete, SWT.PUSH, IconStore.generic_delete, mcExecutionListener);
				}
			}
			if (data instanceof JmsDurableSubscription) {
				final TreeItem root = ti.getParentItem().getParentItem().getParentItem();
				final ConnectionProject cp = (ConnectionProject) root.getData();
				final JmsDurableSubscription<?> jds = (JmsDurableSubscription<?>) data;
				if (JmsFeature.isSupported(cp.getJmsConnection(), JmsFeature.DURABLES_PURGE)) {
					MenuUtil.createItem(m, "Purge", null, SWT.PUSH, IconStore.dest_purge, new SelectionListener() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							try {
								String title = "Purge all messages for " + jds.name + " of connection " + cp.getProjectName() + "?";
								String msgText = "Purge all messages from " + cp.getProjectName() + " - " + jds.name
										+ "?\n\nWarning! This will remove all messages permanently.";
								if (MessageDialog.openQuestion(shell, title, msgText)) {
									cp.getJmsConnection().purgeSubscriberMessages(jds);
									LogUtil.logInfo("Purged " + jds.name + " of topic " + jds.topic);
								}
							} catch (JmsBrowserException ex) {
								LogUtil.logError("Error purging " + jds.name + " of topic " + jds.topic + ": " + ex.getMessage(), ex);
							}
						}
	
						@Override
						public void widgetDefaultSelected(SelectionEvent e) { /* ignore */}
					});
				} else {
					MenuUtil.createNop(m);
				}
			}
		}
		if (m != null) {
			m.setLocation(e.x, e.y);
			m.setVisible(true);
		}
	}

	private int validateDrop(int x, int y) {
		TreeItem ti = tree.getItem(tree.toControl(x, y));
		if (ti != null && ti.getData() != null) {
			Object o = ti.getData();
			if (o instanceof MessageContainer || o instanceof JmsDestination)
				return DND.DROP_COPY;
		}
		return DND.DROP_NONE;
	}

	// DropTargetListener
	@Override
	public void dragEnter(DropTargetEvent event) {
		event.detail = validateDrop(event.x, event.y);
	}

	// DropTargetListener
	@Override
	public void dragLeave(DropTargetEvent event) { /* ignore */}

	// DropTargetListener
	@Override
	public void dragOperationChanged(DropTargetEvent event) {
		event.detail = DND.DROP_COPY;
	}

	// DropTargetListener
	@Override
	public void dragOver(DropTargetEvent event) {
		event.detail = validateDrop(event.x, event.y);
	}

	// DropTargetListener
	@Override
	public void drop(DropTargetEvent event) {
		TreeItem ti = tree.getItem(tree.toControl(event.x, event.y));
		if (ti != null && event.data != null) {
			try {
				byte data[] = (byte[]) event.data;
				ByteArrayInputStream bais = new ByteArrayInputStream(data);
				ObjectInputStream ois = new ObjectInputStream(bais);
				ArchivedMessage msgs[] = (ArchivedMessage[]) ois.readObject();

				Object dest = ti.getData();
				if (dest instanceof MessageContainer) {
					MessageContainer mc = (MessageContainer) dest;
					for (ArchivedMessage msg : msgs) {
						mc.addMessage(msg, true);
					}
					LogUtil.logInfo("Copied " + msgs.length + " messages to archive " + mc.getName());
				}
				if (dest instanceof JmsDestination) {
					ConnectionProject cp = (ConnectionProject) ti.getParentItem().getParentItem().getData();
					JmsConnection conn = cp.getJmsConnection();
					JmsDestination jd = (JmsDestination) dest;
					for (ArchivedMessage msg : msgs) {
						Message jmsMsg = msg.createMessage(conn);
						conn.sendMessage(jmsMsg, jd);
					}
					LogUtil.logInfo("Sent " + msgs.length + " messages to " + jd + " of connection " + cp.getProjectName());
				}
			} catch (IOException e) {
				LogUtil.logError("Error executing drop: " + e.getMessage(), e);
			} catch (ClassNotFoundException e) {
				LogUtil.logError("Fatal error deserializing drop data: " + e.getMessage(), e);
			} catch (JmsBrowserException e) {
				LogUtil.logError("Error sending message: " + e.getMessage(), e);
			}
		}
	}

	// DropTargetListener
	@Override
	public void dropAccept(DropTargetEvent event) {
		event.detail = validateDrop(event.x, event.y);
	}

	// MouseListener
	@Override
	public void mouseUp(MouseEvent e) { /* ignore */}

	// MouseListener
	@Override
	public void mouseDown(MouseEvent e) { /* ignore */}

	// MouseListener
	@Override
	public void mouseDoubleClick(MouseEvent e) {
		Tree t = (Tree) e.getSource();
		TreeItem ti = t.getItem(new Point(e.x, e.y));
		if (ti == null)
			return;
		Object o = ti.getData();
		if (o == null)
			return;
		if (o instanceof ConnectionProject) {
			ConnectionProject cp = (ConnectionProject) o;
			if (!cp.isOpen()) {
				cp.open();
				return;
			}
		}
		if (o instanceof MessageContainer) {
			openArchiveView((MessageContainer) o);
			return;
		}
		if (o instanceof JmsDestination) {
			openMonitorView(ti, (JmsDestination) o);
			return;
		}
		if (!isWindows)
			ti.setExpanded(!ti.getExpanded());
	}
}