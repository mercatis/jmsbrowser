package com.mercatis.jmsbrowser.ui.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsMessageHelper;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.data.ArchivedMessageTransfer;
import com.mercatis.jmsbrowser.ui.data.ConnectionProject;
import com.mercatis.jmsbrowser.ui.data.JmsProperty;
import com.mercatis.jmsbrowser.ui.data.MessageContainer;
import com.mercatis.jmsbrowser.ui.data.MessageReceiver;
import com.mercatis.jmsbrowser.ui.data.MessageReceiver.ColumnType;
import com.mercatis.jmsbrowser.ui.listener.AddToArchiveListener;
import com.mercatis.jmsbrowser.ui.listener.MessageReveiverChangedListener;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;
import com.mercatis.jmsbrowser.ui.util.ArchiveMenuMaker;
import com.mercatis.jmsbrowser.ui.util.FormatUtil;
import com.mercatis.jmsbrowser.ui.util.MenuUtil;
import com.mercatis.jmsbrowser.ui.util.store.ColorStore;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

import swt_components.SwtHexEdit;

public class DestinationMonitorView extends JmsBrowserView implements SelectionListener, DragSourceListener, MenuDetectListener, MessageReveiverChangedListener, AddToArchiveListener {
	public static final String ID = "com.mercatis.jmsbrowser.view.destinationmonitor";

	private static enum COMMAND { ViewFreeze, MsgArchive, MsgResend, MsgDelete, DestPurge }
	
	// UI Controls and related
	private Display display;
	private Group leftGroup;
	private Table msgTable, infoTable;
	private Group payloadGroup;
	private Text payloadText;
	private SwtHexEdit payloadHex;
	private Menu headerMenu;
	private ToolItem[] needSelectionToolbarItems;
	private ToolItem purgeButton;
	
	// data sources and cached stuff
	private String titleSuffix;
	private MessageReceiver receiver;
	private JmsConnection connection;
	
	// execute a COMMAND on click
	private class DefaultExecutionListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			COMMAND cmd = null;
			boolean pushed = false;
			Object src = e.getSource(); 
			if (src instanceof ToolItem) {
				ToolItem ti = (ToolItem) e.getSource();
				cmd = (COMMAND) ti.getData();
				pushed = ti.getSelection();
			}
			if (src instanceof MenuItem) {
				MenuItem mi = (MenuItem) e.getSource();
				cmd = (COMMAND) mi.getData();
				pushed = mi.getSelection();
			}
			if (cmd != null)
				executeAction(cmd, pushed);
			else
				LogUtil.logWarn("Unable to execute anything for the source "+src.getClass().getCanonicalName());
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }		
	}

	// shows the archive hierarchy popup menu when run
	public class ToolbarMenuAction implements SelectionListener {
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
		@Override
		public void widgetSelected(SelectionEvent e) {
			if (e.getSource() instanceof ToolItem) {
				ToolItem ti = (ToolItem) e.getSource();
				Rectangle rect = ti.getBounds();
				Point pt = new Point(rect.x, rect.y + rect.height);
				pt = ti.getParent().toDisplay(pt);
				ArchiveMenuMaker amm = (ArchiveMenuMaker) msgTable.getData();
				if (amm!=null) {
					Menu m = amm.makePopupMenu(getSite().getShell(), DestinationMonitorView.this);
					m.setLocation(pt);
					m.setVisible(true);
				}
			}
		}
	}

	// updates the filter settings in the receiver
	private class QuickFilterAction implements SelectionListener {
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
		@Override
		public void widgetSelected(SelectionEvent e) {
			boolean pushed = false;
			if (e.getSource() instanceof ToolItem) {
				ToolItem ti = (ToolItem) e.getSource();
				ColumnType ct = (ColumnType) ti.getData();
				pushed = ti.getSelection();
				msgTable.setSelection(-1);
				infoTable.setData(null);
				updateMessageEditor();
				receiver.setFilterTarget(ct, pushed);
			}
		}
	}

	// changes the sort direction and column
	private class SortListener implements Listener {
		@Override
		public void handleEvent(Event e) {
			if (!(e.widget instanceof TableColumn))
				return;
			// determine new sort column and direction
			TableColumn sortColumn = msgTable.getSortColumn();
			TableColumn currentColumn = (TableColumn) e.widget;
			int dir = msgTable.getSortDirection();
			if (sortColumn == currentColumn) {
				dir = (dir == SWT.UP) ? SWT.DOWN : SWT.UP;
			} else {
				msgTable.setSortColumn(currentColumn);
				dir = SWT.UP;
			}
			msgTable.setSortDirection(dir);
			// sort the data based on column and direction
			getMessageReceiver().setSortColumn((ColumnType) currentColumn.getData(), dir);
			// update data displayed in table
			msgTable.setSelection(-1);
			msgTable.clearAll();
			infoTable.setData(null);
			updateMessageEditor();
		}
	}
	
	private class CopyClipboardListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			MenuItem mi = (MenuItem) e.getSource();
			String text = (String) mi.getData();
			copyToClipboard(text);
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }		
	}
	
	private class ChangeColumnsListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			List<ColumnType> lct = new LinkedList<ColumnType>();
			for (MenuItem mi : headerMenu.getItems()) {
				if (mi.getSelection())
					lct.add((ColumnType) mi.getData());
			}
			setColumns(lct.toArray(new ColumnType[0]));
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }		
	}
	
	private SortListener sortListener = new SortListener();
	private DefaultExecutionListener executionListener = new DefaultExecutionListener();
	private CopyClipboardListener copyListener = new CopyClipboardListener();
	private ChangeColumnsListener columnListener = new ChangeColumnsListener();
	
	private void executeAction(COMMAND cmd, boolean pushed) {
		JmsDestination dest = receiver.getDestination();
		boolean queue = receiver.isQueue();
		switch (cmd) {
			case DestPurge: {
				StringBuilder sb = new StringBuilder();
				String title;
				if (queue) {
					title = titleSuffix + " - Purge Queue?";
					sb.append("Purge all messages from ").append(titleSuffix).append("?\n\nWarning! This will remove all messages permanently.");
				} else {
					title = titleSuffix + " - Clear message table?";
					sb.append("Clear list of displayed messages?");
				}
				if (MessageDialog.openQuestion(getSite().getShell(), title, sb.toString())) {
					try {
						LogUtil.logInfo("Purging destination " + dest.toString() + "!");
						connection.purgeDestination(dest);
					} catch (JmsBrowserException e) {
						LogUtil.logError("Error purging " + dest.toString() + "!", e);
					}
				}
				break;
			}
			case MsgDelete: {
				StringBuilder sb = new StringBuilder();
				int cnt = msgTable.getSelectionCount();
				String title = titleSuffix+" - Delete message(s)?";;
				if (queue) {
					sb.append("Are you sure you want to permanently delete "+cnt+" message(s) from ").append(titleSuffix).append("?\n\nWarning! This action can not be undone.");
				} else {
					sb.append("Are you sure you want to remove "+cnt+" message(s) from ").append(titleSuffix).append("?");
				}
				if (MessageDialog.openQuestion(getSite().getShell(), title, sb.toString())) {
					int success = 0;
					for (TableItem ti : msgTable.getSelection()) {
						String id = "<error getting message id>";
						Message msg = (Message) ti.getData();
						if (queue) {
							try {
								id = msg.getJMSMessageID();
								connection.deleteMessage(msg);
								++success;
							} catch (Exception e) {
								LogUtil.logError("Error deleting message "+id+" from "+dest.toString()+". "+e.getMessage(), e);
							}
						} else {
							//							ti.dispose();
							receiver.removeMessage(msg);
						}
					}
					if (success>0)
						LogUtil.logInfo("Deleted "+success+" message(s) from "+dest.toString());
				}
				break;
			}
			case MsgResend: {
				int cnt = msgTable.getSelectionCount();
				String msgText = "Resend "+cnt+" message(s) to "+dest.toString()+"?"; 
				if (MessageDialog.openQuestion(getSite().getShell(), titleSuffix+" - Resend message(s)?", msgText)) {
					int success = 0;
					for (TableItem ti : msgTable.getSelection()) {
						String id = "<error getting message id>";
						Message msg = (Message) ti.getData();
						try {
							id = msg.getJMSMessageID();
							connection.sendMessage(msg);
							++success;
						} catch (Exception e) {
							LogUtil.logError("Error sending message "+id+" to "+dest.toString()+". "+e.getMessage(), e);
						}
					}
					if (success>0)
						LogUtil.logInfo("Resend "+success+" message(s) to "+dest.toString());
				}
				break;
			}
			case ViewFreeze:
				getMessageReceiver().toggleListener(pushed);
				break;
			// TODO: find out if these two are needed
			case MsgArchive:
				break;
			default:
				break;
		}
	}
	
	private void createQuickFilter(ToolBar toolbar, ToolItem sep) {
		Text text = new Text(toolbar, SWT.BORDER);
		text.setForeground(ColorStore.darkgrey);
		text.setData(Boolean.FALSE);
		text.setText("Quick Filter");
		sep.setWidth(180);
		sep.setControl(text);
		text.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
				msgTable.setSelection(-1);
				infoTable.setData(null);
				updateMessageEditor();
				Boolean b = (Boolean) ((Text) e.getSource()).getData();
				if (b)
					getMessageReceiver().setFilterString(((Text) e.getSource()).getText());
			}
			@Override
			public void keyPressed(KeyEvent e) {}
		});
		text.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				Text t = (Text) e.getSource();
				if (t.getText().length()==0) {
					t.setForeground(ColorStore.darkgrey);
					t.setData(Boolean.FALSE);
					t.setText("Quick Filter");
				}
			}
			@Override
			public void focusGained(FocusEvent e) {
				Text t = (Text) e.getSource();
				Boolean b = (Boolean) t.getData();
				if (!b) {
					t.setText("");
					t.setData(Boolean.TRUE);
					t.setForeground(null);
				}
			}
		});
		QuickFilterAction qfa = new QuickFilterAction();
		List<ToolItem> list = new LinkedList<ToolItem>();
		list.add(addToolbarButton(toolbar, SWT.CHECK, "Timestamp", IconStore.filter_tstamp, ColumnType.TSTAMP, qfa));
		list.add(addToolbarButton(toolbar, SWT.CHECK, "Custom Properties", IconStore.filter_props, ColumnType.PROPS, qfa));
		list.add(addToolbarButton(toolbar, SWT.CHECK, "Payload (Text only)", IconStore.filter_payload, ColumnType.PAYLOAD, qfa));
		for (ToolItem ti : list)
			ti.setSelection(true);
	}
	
	@Override
	public void createPartControl(Composite parent) {
		display = parent.getDisplay();
		
		parent.setLayout(new GridLayout(1, false));
		
		ToolBar toolbar = new ToolBar(parent, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		List<ToolItem> list = new LinkedList<ToolItem>();
		
		addToolbarButton(toolbar, SWT.CHECK, "Toggle Refresh", IconStore.generic_refresh, COMMAND.ViewFreeze, executionListener).setSelection(true);
		addToolbarButton(toolbar, SWT.SEPARATOR, null, null, null, executionListener);
		list.add(addToolbarButton(toolbar, SWT.DROP_DOWN, "Archive Message", IconStore.archive_add, COMMAND.MsgArchive, new ToolbarMenuAction()));
		list.add(addToolbarButton(toolbar, SWT.PUSH, "Resend Message", IconStore.message_resend, COMMAND.MsgResend, executionListener));
		list.add(addToolbarButton(toolbar, SWT.PUSH, "Delete Message", IconStore.generic_delete, COMMAND.MsgDelete, executionListener));
		addToolbarButton(toolbar, SWT.SEPARATOR, null, null, null, executionListener);
		purgeButton = addToolbarButton(toolbar, SWT.PUSH, "", IconStore.dest_purge, COMMAND.DestPurge, executionListener);
		addToolbarButton(toolbar, SWT.SEPARATOR, null, null, null, executionListener);
		
		createQuickFilter(toolbar, addToolbarButton(toolbar, SWT.SEPARATOR, null, null, null, executionListener));

		needSelectionToolbarItems = list.toArray(new ToolItem[list.size()]);
		for (ToolItem ti : needSelectionToolbarItems)
			ti.setEnabled(false);
		
		// create left and right group
		SashForm sf = new SashForm(parent, SWT.HORIZONTAL | SWT.SMOOTH);
		sf.setSashWidth(4);
		sf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// fill left
		leftGroup = new Group(sf, SWT.NONE);
		leftGroup.setText("some.queue.or.topic");
		leftGroup.setLayout(createFillLayout(4));
		leftGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite tableParent = new Composite(leftGroup, SWT.NONE);

		msgTable = new Table(tableParent, SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.VIRTUAL);
		msgTable.setSortDirection(SWT.UP);
		msgTable.addMenuDetectListener(this);
		msgTable.addSelectionListener(this);
		msgTable.addListener(SWT.SELECTED, sortListener);
		msgTable.setHeaderVisible(true);

		TableColumn cols[] = setColumns(new ColumnType[] { ColumnType.TSTAMP, ColumnType.MSGID });
		
		msgTable.setSortColumn(cols[0]);
		msgTable.pack();

		DragSource source = new DragSource(msgTable, DND.DROP_COPY);
		source.setTransfer(new Transfer[] { ArchivedMessageTransfer.getInstance() });
		source.addDragListener(this);
		
		// fill right
		Composite right = new Composite(sf, SWT.NONE);
		right.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		right.setLayout(new FillLayout());
		
		SashForm sf2 = new SashForm(right, SWT.VERTICAL | SWT.SMOOTH);
		sf2.setSashWidth(4);
		
		Group gRightTop = new Group(sf2, SWT.NONE);
		gRightTop.setLayout(createFillLayout(4));
		gRightTop.setText("Jms Properties");
		
		Composite tableContainer = new Composite(gRightTop, SWT.NONE);
		TableColumnLayout tcl = new TableColumnLayout();
		tableContainer.setLayout(tcl);
		
		infoTable = new Table(tableContainer, SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION);
		tcl.setColumnData(createTableColumn(infoTable, SWT.LEFT, "Property", null, sortListener), new ColumnWeightData(1));
		tcl.setColumnData(createTableColumn(infoTable, SWT.LEFT, "Value", null, sortListener), new ColumnWeightData(2));

		infoTable.pack();
		infoTable.setLinesVisible(true);
		infoTable.setHeaderVisible(true);
		infoTable.addMenuDetectListener(this);
		
		payloadGroup = new Group(sf2, SWT.NONE);
		payloadGroup.setLayout(createFillLayout(4));
		payloadGroup.setText("Payload");
		
		sf2.setWeights(new int[]{3, 2});

		headerMenu = new Menu(msgTable);
		MenuUtil.createItem(headerMenu, "Timestamp", ColumnType.TSTAMP, SWT.CHECK, null, columnListener).setSelection(true);
		MenuUtil.createItem(headerMenu, "Message ID", ColumnType.MSGID, SWT.CHECK, null, columnListener).setSelection(true);
		MenuUtil.createItem(headerMenu, "Custom Properties", ColumnType.PROPS, SWT.CHECK, null, columnListener);
		MenuUtil.createItem(headerMenu, "Payload (text only)", ColumnType.PAYLOAD, SWT.CHECK, null, columnListener);
	}

	@Override
	public void setFocus() {
		super.setFocus();
		msgTable.setFocus();
	}
	
	// SelectionListener
	@Override
	public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
	
	// SelectionListener
	@Override
	public void widgetSelected(SelectionEvent e) {
		int cnt = msgTable.getSelectionCount();
		Message data = null;
		if (cnt==1)
			data = (Message) e.item.getData();
		for (ToolItem ti : needSelectionToolbarItems)
			ti.setEnabled(cnt>0);
		infoTable.setData(data);
		updateMessageEditor();
	}
	
	private TableColumn[] setColumns(ColumnType cols[]) {
		msgTable.setRedraw(false);
		for (TableColumn tc : msgTable.getColumns())
			tc.dispose();
		String text;
		TableColumnLayout tcl = new TableColumnLayout();
		List<TableColumn> columns = new LinkedList<TableColumn>();
		for (ColumnType ct : cols) {
			int flags = SWT.LEFT;
			int weight=2;
			switch(ct) {
				case TSTAMP:
					text = "Timestamp"; weight=1;
					break;
				case MSGID:
					text = "MessageID";
					break;
				case PROPS:
					text = "Custom Properties";
					break;
				case PAYLOAD:
					text = "Text Payload"; weight=3;
					break;
				default:
					text = "?";
			}
			TableColumn column = createTableColumn(msgTable, flags, text, ct, sortListener);
			tcl.setColumnData(column, new ColumnWeightData(weight));
			columns.add(column);
		}
		if (columns.size()>0)
			msgTable.setSortColumn(columns.get(0));
		msgTable.clearAll();
		msgTable.setRedraw(true);
		Composite parent = msgTable.getParent();
		parent.setLayout(tcl);
		parent.layout();
		return columns.toArray(new TableColumn[columns.size()]);
	}
	
	private void addProperty(JmsProperty prop) {
		addProperty(prop, -1);
	}
	
	private void addProperty(JmsProperty prop, int index) {
		TableItem ti;
		if (index<0)
			ti = new TableItem(infoTable, SWT.NONE);
		else
			ti = new TableItem(infoTable, SWT.NONE, index);
		if (prop.color != null)
			ti.setForeground(prop.color);
		ti.setData(prop);
		ti.setText(prop.name);
		ti.setText(1, prop.value);
	}
	
	public void updateMessageEditor() {
		Message message = (Message) infoTable.getData();
		infoTable.removeAll();
		boolean enable = message != null;
		if (message != null) {
			try {
				// class impl name
				addProperty(new JmsProperty("Class", message.getClass().getCanonicalName(), ColorStore.green, false, false));
				// default properties
				addProperty(new JmsProperty("Message ID", message.getJMSMessageID(), null, false, false));
				addProperty(new JmsProperty("Correlation ID", message.getJMSCorrelationID(), null, false, false));
				addProperty(new JmsProperty("Delivery Mode", message.getJMSDeliveryMode(), null, false, false));
				addProperty(new JmsProperty("Expiration", message.getJMSExpiration(), null, false, false));
				addProperty(new JmsProperty("Priority", message.getJMSPriority(), null, false, false));
				addProperty(new JmsProperty("Reply To", message.getJMSReplyTo(), null, false, false));
				addProperty(new JmsProperty("Timestamp", FormatUtil.DATE_FORMAT.format(new Date(message.getJMSTimestamp())), ColorStore.darkgrey, false, false));
				addProperty(new JmsProperty("Type", message.getJMSType(), null, false, true));
				addProperty(new JmsProperty("Destination", message.getJMSDestination()==null ? "" : message.getJMSDestination().toString(), ColorStore.darkgrey, false, false));
				//		addProperty(new JmsProperty("Redelivered", message.getJMSRedelivered()));
				Enumeration<?> propEnum = message.getPropertyNames();
				while (propEnum.hasMoreElements()) {
					String name = (String) propEnum.nextElement();
					addProperty(new JmsProperty(name, message.getStringProperty(name), ColorStore.blue, false, false));
				}
			} catch (JMSException e) {
				addProperty(new JmsProperty("ERROR", e.getMessage(), ColorStore.darkred, false, true));
			}

			try {
				if (message instanceof BytesMessage) {
					if (payloadHex==null) {
						if (payloadText!=null) {
							payloadText.dispose();
							payloadText = null;
						}
						payloadHex = new SwtHexEdit(payloadGroup, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL, 0, 16, 5);
						payloadGroup.layout();
					}
					BytesMessage bMsg = (BytesMessage) message;
					bMsg.reset();
					int dataSize = (int) bMsg.getBodyLength();
					byte[] data = new byte[dataSize];
					bMsg.readBytes(data, dataSize);
					payloadHex.setByteData(data);
					payloadGroup.setText("Payload ("+NumberFormat.getIntegerInstance().format(data.length)+" Bytes)");
				} else {
					if (payloadText==null) {
						if (payloadHex!=null) {
							payloadHex.dispose();
							payloadHex = null;
						}
						payloadText = new Text(payloadGroup, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
						payloadGroup.layout();
					}
					String payloadString = "<unknown content or no message selected>";
					if (message instanceof TextMessage) {
						payloadGroup.setText("Text Payload");
						String s = ((TextMessage) message).getText();
						if (s!=null && s.startsWith("<"))
							payloadString = JmsMessageHelper.prettyFormatXml(s);
						else
							payloadString = s==null ? "" : s;
					} else if (message instanceof ObjectMessage) {
						payloadGroup.setText("Payload");
						ObjectMessage om = (ObjectMessage) message;
						try {
							Object o = om.getObject();
							String s = o==null ? "null" : o.getClass().getName();
							payloadString = "Message contains object of type\n\"" + s + "\".";
						} catch (Exception ex) {
							// evil hack - works if classnotfound occurred - may steal your girlfriend otherwise
							payloadString = "Message contains object of type\n\"" + ex.getMessage() + "\".";
						}
					} else if (message instanceof StreamMessage) {
						payloadGroup.setText("Payload");
						payloadString = "<streammessage not implemented>";
					}
					payloadText.setText(payloadString);
				}
			} catch (JMSException e) {
				if (payloadText!=null) {
					payloadText.dispose();
					payloadText = null;
				}
				if (payloadHex!=null) {
					payloadHex.dispose();
					payloadHex = null;
				}
				payloadGroup.setText("Payload");
				payloadGroup.layout();
				LogUtil.logError("error parsing payload", e);
//				payloadString = "<error handling payload: \"" + e.getMessage() + "\">";
			}
		} else {
			addProperty(new JmsProperty("", "select one message", ColorStore.darkgrey, false, false));
			if (payloadText!=null) {
				payloadText.dispose();
				payloadText = null;
			}
			if (payloadHex!=null) {
				payloadHex.dispose();
				payloadHex = null;
			}
			payloadGroup.setText("Payload");
			payloadGroup.layout();
		}
		infoTable.setEnabled(enable);
//		payload.setEnabled(enable);
	}


	// MenuDetectListener
	@Override
	public void menuDetected(MenuDetectEvent e) {
		Object src = e.getSource();
		Menu m = null;
		int selCnt = msgTable.getSelectionCount();
		if (src instanceof Table && src==msgTable) {
			Rectangle clientArea = msgTable.getClientArea();
			Point pt = msgTable.toControl(new Point(e.x, e.y));
			boolean header = clientArea.y <= pt.y && pt.y < (clientArea.y + msgTable.getHeaderHeight());
			if (header) {
				m = headerMenu;
			} else {
				m = new Menu(msgTable);
				if (selCnt>0) {
					ArchiveMenuMaker amm = (ArchiveMenuMaker) msgTable.getData();
					amm.makeSubMenu(m, this);
					MenuUtil.createItem(m, "Resend Message", COMMAND.MsgResend, SWT.PUSH, IconStore.message_resend, executionListener);
					MenuUtil.createSeperator(m);
					MenuUtil.createItem(m, "Delete Message", COMMAND.MsgDelete, SWT.PUSH, IconStore.generic_delete, executionListener);
					MenuUtil.createSeperator(m);
				}
				String purgeCmdName = getMessageReceiver().isQueue() ? "Purge Queue" : "Clear Table";
				MenuUtil.createItem(m, purgeCmdName, COMMAND.DestPurge, SWT.PUSH, IconStore.dest_purge, executionListener);
			}
		}
		if (src instanceof Table && src==infoTable) {
			m = new Menu(infoTable);
			Point p = infoTable.toControl(new Point(e.x, e.y));
			TableItem ti = infoTable.getItem(p);
			JmsProperty prop = ti!=null ? (JmsProperty) ti.getData() : null;
			if (prop != null) {
				MenuUtil.createItem(m, "Copy Name", prop.name, SWT.PUSH, null, copyListener);
				MenuUtil.createItem(m, "Copy Value", prop.value, SWT.PUSH, null, copyListener);
				MenuUtil.createItem(m, "Copy Name && Value", prop.name+"="+prop.value, SWT.PUSH, null, copyListener);
			}
		}
		if (m!=null) {
			m.setLocation(e.x, e.y);
			m.setVisible(true);
		}
	}
	
	public MessageReceiver getMessageReceiver() {
		return receiver;
	}
	
	private String createSuffix(String connName, JmsDestination dest) {
		StringBuilder sb = new StringBuilder();
		int idx = dest.name.lastIndexOf('.');
		if (idx<0)
			sb.append(dest.name);
		else
			sb.append(dest.name.substring(idx+1));
		sb.append(" (");
		if (dest.type == DestinationType.QUEUE)
			sb.append("Queue");
		else
			sb.append("Topic");
		sb.append(") - ");
		sb.append(connName);
		titleSuffix = sb.toString(); 
		return titleSuffix;
	}
	
	public void setDataSources(ConnectionProject proj, JmsDestination dest, int maxMessages, ArchiveMenuMaker amm) throws JmsBrowserException {
		leftGroup.setText(dest.name);
		msgTable.setRedraw(false);
		msgTable.setSelection(-1);
		infoTable.setData(null);
		updateMessageEditor();
		MessageReceiver previous = getMessageReceiver();
		if (previous != null) {
			msgTable.removeListener(SWT.SetData, previous);
			previous.dispose();
			msgTable.removeAll();
		}
		msgTable.removeAll();
		connection = proj.getJmsConnection();
		
		receiver = new MessageReceiver(connection, dest, maxMessages, this);
		
		setPartName(createSuffix(proj.getProjectName(), dest));
		setTitleImage(dest.type==DestinationType.QUEUE ? IconStore.queue : IconStore.topic);
		receiver.setSortColumn((ColumnType) msgTable.getSortColumn().getData(), msgTable.getSortDirection());
		msgTable.setData(amm);
		msgTable.addListener(SWT.SetData, receiver);
		msgTable.setRedraw(true);

		purgeButton.setToolTipText(dest.type==DestinationType.QUEUE ? "Purge Queue" : "Clear Table");
	}
	
	private void updateTitle(int total, int visible) {
		String title;
		if (visible != total)
			title = String.format("[%d/%d] %s", visible, total, titleSuffix);
		else
			title = String.format("[%d] %s", total, titleSuffix);
		setPartName(title);
	}
	
	private void uiExec(Runnable action) {
		display.asyncExec(action);
	}

	@Override
	public void onNewMessage(final int index, final Message msg, final int total, final int filtered) {
		uiExec(new Runnable() {
			@Override
			public void run() {
				if (msgTable.isDisposed())
					return;
				if (index<0) {
					msgTable.setItemCount(filtered);
				} else {
					new TableItem(msgTable, SWT.NONE, index);
				}
				updateTitle(total, filtered);
			}
		});
	}
	
	@Override
	public void onMessageChanged(final int index, Message msg) {
		uiExec(new Runnable() {
			@Override
			public void run() {
				if (msgTable.isDisposed())
					return;
				msgTable.clear(index);
			}
		});
	}

	@Override
	public void onMessageRemoved(final int index, final int total, final int filtered) {
		uiExec(new Runnable() {
			@Override
			public void run() {
				if (msgTable.isDisposed())
					return;
				msgTable.remove(index);
				updateTitle(total, filtered);
			}
		});
	}

	@Override
	public void onMessageRemovedAll() {
		uiExec(new Runnable() {
			@Override
			public void run() {
				if (msgTable.isDisposed())
					return;
				msgTable.removeAll();
				msgTable.setItemCount(0);
				updateTitle(0, 0);
			}
		});
	}

	@Override
	public void onMessageChangedAll(final int total, final int filtered) {
		uiExec(new Runnable() {
			@Override
			public void run() {
				if (msgTable.isDisposed())
					return;
				msgTable.setItemCount(filtered);
				msgTable.clearAll();
				updateTitle(total, filtered);
			}
		});
	}
	
	@Override
	public void addToArchive(MessageContainer container) {
		String msgText = "Are you sure you want to copy "+msgTable.getSelectionCount()+" message(s) to "+container.getName()+'?';
		if (MessageDialog.openQuestion(getSite().getShell(), "Archive message(s) from "+titleSuffix+'?', msgText)) {
			int success = 0;
			for (TableItem ti : msgTable.getSelection()) {
				Message msg = (Message) ti.getData();
				ArchivedMessage am = new ArchivedMessage(msg, connection);
				try {
					container.addMessage(am, true);
					++success;
				} catch (IOException e) {
					LogUtil.logError("Error archiving message with id "+am.getMessageID()+". "+e.getMessage(), e);
				}
			}
			if (success>0)
				LogUtil.logInfo("Copied "+success+" messages to archive "+container.getName());
		}
	}
	
	@Override
	public void dispose() {
		getMessageReceiver().dispose();
		super.dispose();
	}
	
	// DragSourceListener 
	@Override
	public void dragStart(DragSourceEvent event) { /* ignore */ }

	// DragSourceListener 
	@Override
	public void dragSetData(DragSourceEvent event) {
		try {
			TableItem selection[] = msgTable.getSelection();
			ArchivedMessage dragData[] = new ArchivedMessage[selection.length];
			for (int i=0; i<selection.length; ++i)
				dragData[i] = new ArchivedMessage((Message) selection[i].getData(), connection);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(dragData);
			oos.close();
			event.data = baos.toByteArray();
		} catch (IOException e) {
			event.doit = false;
			LogUtil.logError("Error starting drag operation: "+e.getMessage(), e);
		}
	}

	// DragSourceListener 
	@Override
	public void dragFinished(DragSourceEvent event) { /* ignore */ }
}
