package com.mercatis.jmsbrowser.ui.view;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.TableEditor;
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
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
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

import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.data.ArchivedMessageTransfer;
import com.mercatis.jmsbrowser.ui.data.JmsProperty;
import com.mercatis.jmsbrowser.ui.data.MessageContainer;
import com.mercatis.jmsbrowser.ui.data.MessageContainer.ColumnType;
import com.mercatis.jmsbrowser.ui.listener.MessageContainerChangedListener;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage.MessageType;
import com.mercatis.jmsbrowser.ui.util.FormatUtil;
import com.mercatis.jmsbrowser.ui.util.MenuUtil;
import com.mercatis.jmsbrowser.ui.util.store.ColorStore;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

import swt_components.SwtHexEdit;

public class MessageArchiveView extends JmsBrowserView implements SelectionListener, MenuDetectListener, MessageContainerChangedListener, DragSourceListener {
	public static final String ID = "com.mercatis.jmsbrowser.view.messagearchive";;
	
	protected class ChangeListener implements KeyListener, Listener {
		private int index;
		public ChangeListener(int columnIndex) {
			index = columnIndex;
		}
		@Override
		public void handleEvent(Event event) {
			Text t = (Text) event.widget;
			TableItem ti = (TableItem) t.getData();
			if (ti!=null)
				t.setBounds(ti.getBounds(index));
		}
		@Override
		public void keyPressed(KeyEvent e) { /* ignore */ }
		@Override
		public void keyReleased(KeyEvent e) {
			ArchivedMessage msg = (ArchivedMessage) editorTable.getData();
			Control c = (Control) e.getSource();
			if (c.equals(payloadText)) {
				msg.setPayload(payloadText.getText().getBytes());
			} else if (c instanceof StyledText) {
				msg.setModified(true);
			} else {
				Object o = c.getData();
				if (o instanceof TableItem) {
					Object o2 = ((TableItem) o).getData();
					if (o2 instanceof JmsProperty) {
						JmsProperty prop = (JmsProperty) o2;
						prop.value = ((Text) c).getText();
						msg.setJmsProperty(prop);
					}
				}
			}
			updateIsModified(msg!=null && msg.isModified());
		}
	}
	
	protected class NewPropertyListener implements KeyListener, FocusListener {
		@Override
		public void focusGained(FocusEvent e) { /* ignore */ }
		@Override
		public void focusLost(FocusEvent e) {
			doSomething((Text) e.widget, false);
		}
		@Override
		public void keyPressed(KeyEvent e) { /* ignore */ }
		@Override
		public void keyReleased(KeyEvent e) {
			if (e.keyCode == SWT.CR) {
				doSomething((Text) e.widget, true);
			}
		}
		
		private void doSomething(Text t, boolean setFocus) {
			String s = t.getText();
			if (s.length()==0)
				return;
			ArchivedMessage msg = (ArchivedMessage) editorTable.getData();
			JmsProperty jp = new JmsProperty(s, "", ColorStore.blue, true, true);
			msg.setJmsProperty(jp);
			TableItem ti = (TableItem) t.getData();
			int idx = editorTable.indexOf(ti);
			TableEditor newTe = addProperty(jp, idx);
			t.setText("");
			t.setBounds(editorTable.getItem(idx+1).getBounds(0));
			if (setFocus)
				newTe.getEditor().setFocus();
			updateIsModified(msg!=null && msg.isModified());
		}
	}
	
	private class SortListener implements Listener {
		@Override
		public void handleEvent(Event e) {
			if (!(e.widget instanceof TableColumn))
				return;
			// determine new sort column and direction
			TableColumn sortColumn = messageTable.getSortColumn();
			TableColumn currentColumn = (TableColumn) e.widget;
			int dir = messageTable.getSortDirection();
			if (sortColumn == currentColumn) {
				dir = (dir == SWT.UP) ? SWT.DOWN : SWT.UP;
			} else {
				messageTable.setSortColumn(currentColumn);
				dir = SWT.UP;
			}
			messageTable.setSortDirection(dir);
			// sort the data based on column and direction
			getMessageContainer().setSortColumn((ColumnType) currentColumn.getData(), dir);
			// update data displayed in table
			messageTable.setSelection(-1);
			messageTable.clearAll();
			editorTable.setData(null);
			updateMessageEditor();
		}
	}
	
	private class ExecutionListener implements SelectionListener {
		@Override
		public void widgetSelected(SelectionEvent e) {
			COMMAND cmd = null;
			Object src = e.getSource(); 
			if (src instanceof MenuItem) {
				MenuItem mi = (MenuItem) e.getSource();
				cmd = (COMMAND) mi.getData();
				if (cmd != null)
					executeAction(cmd);
				else
					LogUtil.logWarn("Unable to execute anything for the source "+src.getClass().getCanonicalName());
			}
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }		
	}
	
	private class ToolbarAction implements SelectionListener {
		@Override
		public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
		@Override
		public void widgetSelected(SelectionEvent e) {
			COMMAND cmd = null;
			if (e.getSource() instanceof ToolItem)
				cmd = (COMMAND) ((ToolItem) e.getSource()).getData();
			executeAction(cmd);
		}
	}

	private static enum COMMAND { DeleteAll, MsgDelete, MsgDupe, MsgRevert, MsgSave, MsgSaveAll, NewByte, NewText, PayClear, PayExport, PayImport }
	private static final NumberFormat numFormat = NumberFormat.getIntegerInstance();

	private List<TableEditor> activeEditors = new LinkedList<TableEditor>();
	private ToolItem[] needSelectionToolbarItems;
	private Table messageTable, editorTable;;
	private Group payloadGroup;
	private Text payloadText;
	private SwtHexEdit payloadHex;

	private ChangeListener changeListener[] = { new ChangeListener(0), new ChangeListener(1)};
	private NewPropertyListener newPropListener = new NewPropertyListener();
	private SortListener sortListener = new SortListener();
	private ExecutionListener executionListener = new ExecutionListener();
	
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		
		SashForm sf = new SashForm(parent, SWT.HORIZONTAL | SWT.SMOOTH);
		sf.setSashWidth(4);
		
		// left of sash
		Composite left = new Composite(sf, SWT.NONE);
		left.setLayout(new GridLayout(1, false));
		
		ToolBar leftToolbar = new ToolBar(left, SWT.FLAT);
		leftToolbar.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		ToolbarAction toolbarAction = new ToolbarAction();
		
		List<ToolItem> list = new LinkedList<ToolItem>();
		addToolbarButton(leftToolbar, SWT.PUSH, "New Textmessage", IconStore.message_text, COMMAND.NewText, toolbarAction);
		addToolbarButton(leftToolbar, SWT.PUSH, "New Bytemessage", IconStore.message_binary, COMMAND.NewByte, toolbarAction);
		addToolbarButton(leftToolbar, SWT.SEPARATOR, null, null, null, null);
		addToolbarButton(leftToolbar, SWT.PUSH, "Save all", IconStore.message_save_all, COMMAND.MsgSaveAll, toolbarAction);
		list.add(addToolbarButton(leftToolbar, SWT.PUSH, "Duplicate", IconStore.message_dupe, COMMAND.MsgDupe, toolbarAction));
		list.add(addToolbarButton(leftToolbar, SWT.PUSH, "Delete", IconStore.generic_delete, COMMAND.MsgDelete, toolbarAction));
		addToolbarButton(leftToolbar, SWT.SEPARATOR, null, null, null, null);
		addToolbarButton(leftToolbar, SWT.PUSH, "Empty Archive", IconStore.dest_purge, COMMAND.DeleteAll, toolbarAction);
		
		Group leftGroup = new Group(left, SWT.NONE);
		leftGroup.setText("Message List");
		leftGroup.setLayout(createFillLayout(4));
		leftGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// message list
		Composite c = new Composite(leftGroup, SWT.NONE);

		messageTable = new Table(c, SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.VIRTUAL);
		messageTable.setSortDirection(SWT.UP);
		messageTable.addMenuDetectListener(this);
		messageTable.addSelectionListener(this);
		messageTable.addListener(SWT.SELECTED, sortListener);
//		messageTable.setLinesVisible(true);
		messageTable.setHeaderVisible(true);
		TableColumnLayout tcl = new TableColumnLayout();
		c.setLayout(tcl);
		TableColumn col1 = createTableColumn(messageTable, SWT.LEFT, "Timestamp", ColumnType.TSTAMP, sortListener); 
		tcl.setColumnData(col1, new ColumnWeightData(4));
		TableColumn col2 = createTableColumn(messageTable, SWT.LEFT, "Label", ColumnType.LABEL, sortListener); 
		tcl.setColumnData(col2, new ColumnWeightData(5));
		messageTable.setSortColumn(col1);
		messageTable.pack();
		
		DragSource source = new DragSource(messageTable, DND.DROP_COPY);
		source.setTransfer(new Transfer[] { ArchivedMessageTransfer.getInstance() });
		source.addDragListener(this);
		
		// right of sash
		Composite right = new Composite(sf, SWT.NONE);
		right.setLayout(new GridLayout(1, false));
		
		ToolBar rightToolbar = new ToolBar(right, SWT.FLAT);
		rightToolbar.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		
		SashForm sfRight = new SashForm(right, SWT.VERTICAL | SWT.SMOOTH);
		sfRight.setSashWidth(4);
		sfRight.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Group propGroup = new Group(sfRight, SWT.NONE);
		propGroup.setText("Jms Properties");
		propGroup.setLayout(createFillLayout(4));
		
		payloadGroup = new Group(sfRight, SWT.NONE);
		payloadGroup.setText("Payload");
		payloadGroup.setLayout(createFillLayout(4));
		

		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Save Message", IconStore.message_save, COMMAND.MsgSave, toolbarAction));
		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Revert Message", IconStore.message_load, COMMAND.MsgRevert, toolbarAction));
		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Delete Message", IconStore.message_delete, COMMAND.MsgDelete, toolbarAction));
		addToolbarButton(rightToolbar, SWT.SEPARATOR, null, null, null, null);
		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Clear Payload", IconStore.payload_clear, COMMAND.PayClear, toolbarAction));
		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Import Payload", IconStore.payload_load, COMMAND.PayImport, toolbarAction));
		list.add(addToolbarButton(rightToolbar, SWT.PUSH, "Export Payload", IconStore.payload_save, COMMAND.PayExport, toolbarAction));

		needSelectionToolbarItems = list.toArray(new ToolItem[list.size()]);
		for (ToolItem ti : needSelectionToolbarItems)
			ti.setEnabled(false);

		Composite tableContainer = new Composite(propGroup, SWT.NONE);
		tcl = new TableColumnLayout();
		tableContainer.setLayout(tcl);

		editorTable = new Table(tableContainer, SWT.SINGLE | SWT.V_SCROLL | SWT.HIDE_SELECTION);
		
		tcl.setColumnData(createTableColumn(editorTable, SWT.LEFT, "Property", null, sortListener), new ColumnWeightData(1));
		tcl.setColumnData(createTableColumn(editorTable, SWT.LEFT, "Value", null, sortListener), new ColumnWeightData(2));
		
		editorTable.pack();
		editorTable.setLinesVisible(true);
		editorTable.setHeaderVisible(true);
		editorTable.addMenuDetectListener(this);
		
		// this should be done late
		sfRight.setWeights(new int[]{3,2});
		
		updateMessageEditor();
	}
	
	// MenuDetectListener
	@Override
	public void menuDetected(MenuDetectEvent e) {
		Object src = e.getSource();
		Menu m = null;
		int selCnt = messageTable.getSelectionCount();
		if (src instanceof Table && src==messageTable) {
			m = new Menu(messageTable);
			MenuUtil.createItem(m, "New Textmessage", COMMAND.NewText, SWT.PUSH, IconStore.message_text, executionListener);
			MenuUtil.createItem(m, "New Bytemessage", COMMAND.NewByte, SWT.PUSH, IconStore.message_binary, executionListener);
			if (selCnt>0) {
				MenuUtil.createSeperator(m);
				if (selCnt==1) {
					MenuUtil.createItem(m, "Duplicate", COMMAND.MsgDupe, SWT.PUSH, IconStore.message_dupe, executionListener);
				}
				MenuUtil.createItem(m, "Delete", COMMAND.MsgDelete, SWT.PUSH, IconStore.generic_delete, executionListener);
			}
			MenuUtil.createSeperator(m);
			MenuUtil.createItem(m, "Empty Archive", COMMAND.DeleteAll, SWT.PUSH, IconStore.dest_purge, executionListener);
		}
		if (src instanceof Table && src==editorTable) {
			m = new Menu(editorTable);
			Point p = editorTable.toControl(new Point(e.x, e.y));
			TableItem ti = editorTable.getItem(p);
			JmsProperty prop = ti!=null ? (JmsProperty) ti.getData() : null;
			if (prop != null) {
				SelectionListener propCopyListener = new SelectionListener() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						Object src = e.getSource(); 
						if (src instanceof MenuItem) {
							MenuItem mi = (MenuItem) e.getSource();
							Object o = mi.getData();
							if (o instanceof String) {
								copyToClipboard((String) o);
							}
							if (o instanceof JmsProperty) {
								ArchivedMessage am = (ArchivedMessage) editorTable.getData();
								TableItem ti = editorTable.getSelection()[0];
								JmsProperty prop = ti!=null ? (JmsProperty) ti.getData() : null;
								am.removeProperty(prop.name);
								ti.dispose();
								updateIsModified(am.isModified());
								updateMessageEditor();
							}
						}
					}
					@Override
					public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
				};
				MenuUtil.createItem(m, "Copy Name", prop.name, SWT.PUSH, null, propCopyListener);
				MenuUtil.createItem(m, "Copy Value", prop.value, SWT.PUSH, null, propCopyListener);
				MenuUtil.createItem(m, "Copy Name && Value", prop.name + "=" + prop.value, SWT.PUSH, null, propCopyListener);
				if (prop.deleteable) {
					MenuUtil.createSeperator(m);
					MenuUtil.createItem(m, "Remove Property", prop, SWT.PUSH, IconStore.generic_delete, propCopyListener);
				}
			}
		}
		if (m!=null) {
			m.setLocation(e.x, e.y);
			m.setVisible(true);
		}
	}
	
	// MessageContainerChangedListener
	@Override
	public void onMessageChanged(int index, ArchivedMessage msg) {
		updateMessageEditor();
	}

	// MessageContainerChangedListener
	@Override
	public void onMessageRemoved(int index) {
		messageTable.remove(index);
		messageTable.setSelection(-1);
		editorTable.setData(null);
		updateMessageEditor();
	}
	
	// MessageContainerChangedListener
	@Override
	public void onNewMessage(int index, ArchivedMessage msg) {
		new TableItem(messageTable, SWT.NONE, index);
		messageTable.setSelection(index);
		editorTable.setData(msg);
		updateMessageEditor();
	}
	
	@Override
	public void setFocus() {
		super.setFocus();
		messageTable.setFocus();
	}
	
	public void setMessageContainer(MessageContainer mc) {
		MessageContainer previous = getMessageContainer();
		if (previous != null) {
			messageTable.removeListener(SWT.SetData, previous);
			messageTable.removeAll();
			previous.removeListener(this);
		}
		if (!mc.isRoot())
			setPartName(mc.getName() + " MessageArchive");
		mc.addListener(this);
		mc.setSortColumn((ColumnType) messageTable.getSortColumn().getData(), messageTable.getSortDirection());
		messageTable.setData(mc);
		messageTable.addListener(SWT.SetData, mc);
		messageTable.setItemCount(mc.getMessageCount());
	}

	public void updateMessageEditor() {
		ArchivedMessage message = (ArchivedMessage) editorTable.getData();
		editorTable.removeAll();
		for (TableEditor te : activeEditors) {
			te.getEditor().dispose();
			te.dispose();
		}
		activeEditors.clear();
		boolean enable = message != null;
		if (message != null) {
			// label
			addProperty(new JmsProperty("Label", message.getLabel(), ColorStore.green, false, true));
			// read only values
			addProperty(new JmsProperty("Filename", message.getFilename(), ColorStore.darkgrey, false, false));
			addProperty(new JmsProperty("Message Type", message.getMessageType(), ColorStore.darkgrey, false, false));
			addProperty(new JmsProperty("Message ID", message.getMessageID(), ColorStore.darkgrey, false, false));
			addProperty(new JmsProperty("Timestamp", FormatUtil.DATE_FORMAT.format(new Date(message.getTimestamp())), ColorStore.darkgrey, false, false));
			addProperty(new JmsProperty("Destination", message.getDestination()==null ? "" : message.getDestination().toString(), ColorStore.darkgrey, false, false));
			// default entries, read-write
			addProperty(new JmsProperty("Correlation ID", message.getCorrelationID(), null, false, true));
			addProperty(new JmsProperty("Delivery Mode", message.getDeliveryMode(), null, false, true));
			addProperty(new JmsProperty("Expiration", message.getExpiration(), null, false, true));
			addProperty(new JmsProperty("Priority", message.getPriority(), null, false, true));
			//		addProperty(new JmsProperty("Redelivered", message.getJMSRedelivered()));
			addProperty(new JmsProperty("Reply To", message.getReplyTo(), null, false, false));
			addProperty(new JmsProperty("Type", message.getType(), null, false, true));
			Set<String> propEnum = message.getPropertyNames();
			for (String s : propEnum) {
				addProperty(new JmsProperty(s, message.getProperty(s), ColorStore.blue, true, true));
			}
			addProperty(new JmsProperty("", "", ColorStore.blue, false, true));

			if (message.isBytesMessage()) {
				if (payloadHex==null) {
					if (payloadText!=null) {
						payloadText.dispose();
						payloadText = null;
					}
					payloadHex = new SwtHexEdit(payloadGroup, SWT.V_SCROLL | SWT.H_SCROLL, 0, 16, 5);
					payloadHex.addKeyListener(changeListener[0]);
					payloadGroup.layout();
				}
				byte[] data = message.getPayload();
				payloadHex.setByteData(data);
				payloadGroup.setText("Payload ("+numFormat.format(data.length)+" Bytes)");
			}
			if (message.isTextMessage()) {
				if (payloadText==null) {
					if (payloadHex!=null) {
						payloadHex.dispose();
						payloadHex = null;
					}
					payloadText = new Text(payloadGroup, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
					payloadText.addKeyListener(changeListener[0]);
					payloadGroup.setText("Text Payload");
					payloadGroup.layout();
				}
				byte[] data = message.getPayload();
				String s = data!=null ? new String(data) : "";
				payloadText.setText(s);
			}
		} else {
			addProperty(new JmsProperty("", "select one message", ColorStore.darkgrey, false, false));
			//payloadEditor.setText("");
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
		editorTable.setEnabled(enable);
		for (ToolItem ti : needSelectionToolbarItems)
			ti.setEnabled(enable);
		updateIsModified(enable && message.isModified());
	}
	
	private void updateIsModified(boolean enable) {
		int idx = messageTable.getSelectionIndex();
		if (idx != -1) {
			messageTable.clear(idx);
			needSelectionToolbarItems[2].setEnabled(enable);
			needSelectionToolbarItems[3].setEnabled(enable);
		}
	}
	
	// SelectionListener
	@Override
	public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
	
	// SelectionListener
	@Override
	public void widgetSelected(SelectionEvent e) {
		int cnt = messageTable.getSelectionCount();
		ArchivedMessage data = null;
		if (cnt==1)
			data = getMessageContainer().getMessage(messageTable.getSelectionIndex());
		editorTable.setData(data);
		updateMessageEditor();
		needSelectionToolbarItems[0].setEnabled(cnt==1);
		needSelectionToolbarItems[1].setEnabled(cnt>0);
	}
	
	private TableEditor addProperty(JmsProperty prop) {
		return addProperty(prop, -1);
	}
	
	private TableEditor addProperty(JmsProperty prop, int index) {
		TableItem ti;
		if (index<0)
			ti = new TableItem(editorTable, SWT.NONE);
		else
			ti = new TableItem(editorTable, SWT.NONE, index);
		if (prop.color != null)
			ti.setForeground(prop.color);
		ti.setData(prop);
		TableEditor te = null;
		if (prop.name.length()==0)
			te = addTableEditor(ti, prop.name, 0);
		else
			ti.setText(prop.name);
		
		if (prop.editable && prop.name.length()>0)
			te = addTableEditor(ti, prop.value, 1);
		else
			ti.setText(1, prop.value);
		return te;
	}

	private TableEditor addTableEditor(TableItem ti, String label, int colIndex) {
		TableEditor editor = new TableEditor(editorTable);
		Text text = new Text(editorTable, SWT.NONE);
		text.setText(label);
		if (colIndex==0) {
			text.addKeyListener(newPropListener);
			text.addFocusListener(newPropListener);
		} else {
			text.addKeyListener(changeListener[colIndex]);
		}
		
		text.addListener(SWT.Resize, changeListener[colIndex]);
		text.setData(ti);
		editor.grabHorizontal = true;
		editor.setEditor(text, ti, colIndex);
		activeEditors.add(editor);
		return editor;
	}
	
	private void executeAction(COMMAND cmd) {
		ArchivedMessage msg = (ArchivedMessage) editorTable.getData();
		MessageContainer mc = getMessageContainer();
		switch (cmd) {
			case NewText:
			case NewByte:
				MessageType type = cmd==COMMAND.NewText ? MessageType.TEXT : MessageType.BYTE;
				try {
					ArchivedMessage newMsg = new ArchivedMessage(type);
					mc.addMessage(newMsg, false);
					LogUtil.logInfo("Created new " + ArchivedMessage.getTypeLabel(type) + " in archive " + getPartName());
				} catch (IOException ex) {
					LogUtil.logError("Unable to create new " + ArchivedMessage.getTypeLabel(type) + " in archive " + getPartName() + ": " + ex.getMessage(), ex);
				}
				break;
			case MsgDupe:
				try {
					ArchivedMessage newMsg = new ArchivedMessage(msg);
					newMsg.setTimestamp(System.currentTimeMillis());
					mc.addMessage(newMsg, false);
					LogUtil.logInfo("Duplicated message " + msg.getLabel() + " in archive " + getPartName());
				} catch (IOException ex) {
					LogUtil.logError("Unable to duplicate message " + msg.getLabel() + " in archive " + getPartName() + ": " + ex.getMessage(), ex);
				}
				break;
			case MsgDelete:
				int[] idx = messageTable.getSelectionIndices();
				if (!MessageDialog.openQuestion(getSite().getShell(),  getPartName() + " - Delete message(s)?", "Are you sure you want to permanently delete "+idx.length+" message(s)?"))
					return;
				int failures = 0;
				for (int i=idx.length-1; i>=0; --i)
					try {
						mc.removeMessage(idx[i]);
					} catch (IOException e) {
						++failures;
					}
				if (failures == idx.length)
					LogUtil.logError("Unable to delete message(s) in archive " + getPartName(), null);
				else if (failures > 0 )
					LogUtil.logWarn("Unable to delete "+failures+" of "+idx.length+" message(s) in archive " + getPartName());
				else
					LogUtil.logInfo("Deleted "+idx.length+" message(s) from archive " + getPartName());
				break;
			case DeleteAll:
				if (MessageDialog.openQuestion(getSite().getShell(), "Empty message archive "+getPartName()+"?", "Are you sure you want to permanently delete all message(s)?")) {
					mc.removeAllMessage();
					LogUtil.logInfo("Emptied archive " + getPartName());
				}
				break;
			case MsgSave:
				try {
					msg.saveToDisk(getMessageContainer().getFolder());
					messageTable.clear(messageTable.getSelectionIndex());
					updateMessageEditor();
					LogUtil.logInfo("Saved message "+msg.getLabel() + " to archive " + getPartName());
				} catch (IOException ex) {
					LogUtil.logError("An error occured while trying to save " + msg.getLabel() + ": "+ex.getMessage(), ex);
				}
				break;
			case MsgSaveAll:
				for (int i=0; i<messageTable.getItemCount(); ++i) {
					TableItem ti = messageTable.getItem(i);
					ArchivedMessage aMsg = (ArchivedMessage) ti.getData();
					if (aMsg!=null && aMsg.isModified()) {
						try {
							aMsg.saveToDisk(getMessageContainer().getFolder());
							messageTable.clear(i);
							LogUtil.logInfo("Saved message "+aMsg.getLabel() + " to archive " + getPartName());
						} catch (IOException ex) {
							LogUtil.logError("An error occured while trying to save " + msg.getLabel() + ": "+ex.getMessage(), ex);
						}
					}
				}
				updateMessageEditor();
				break;
			case MsgRevert:
				if (!MessageDialog.openQuestion(getSite().getShell(), "Revert message?", "Are you sure you want to revert the selected message?"))
					return;
				try {
					msg.revert(getMessageContainer().getFolder());
					messageTable.clear(messageTable.getSelectionIndex());
					updateMessageEditor();
					LogUtil.logInfo("Reverted message "+msg.getLabel() + " in archive " + getPartName());
				} catch (IOException ex) {
					LogUtil.logError("An error occured while reverting " + msg.getLabel() + " in archive " + getPartName() + ": " + ex.getMessage(), ex);
				}
				break;
			case PayClear:
				msg.setPayload(null, 0);
				updateMessageEditor();
				LogUtil.logInfo("Cleared payload of "+msg.getLabel() + " in archive " + getPartName());
				break;
			case PayImport:
				String fileNameIm = getFileName(SWT.OPEN);
				try {
					File f = new File(fileNameIm);
					FileInputStream fis = new FileInputStream(f);
					BufferedInputStream bis = new BufferedInputStream(fis);
					msg.setPayload(bis);
					updateMessageEditor();
					LogUtil.logInfo("Imported payload from file " + fileNameIm);
				} catch (IOException e) {
					LogUtil.logError("Importing payload from file " + fileNameIm + " failed: " + e.getMessage(), e);
				}
				break;
			case PayExport:
				byte data[] = msg.getPayload();
				if (data==null || data.length==0) {
					LogUtil.logWarn("Unable to exported payload, payload is empty");
					return;
				}
				String fileNameEx = getFileName(SWT.SAVE);
				try {
					File f = new File(fileNameEx);
					FileOutputStream fos = new FileOutputStream(f);
					BufferedOutputStream bos = new BufferedOutputStream(fos);
					bos.write(data);
					bos.close();
					LogUtil.logInfo("Exported payload to file " + fileNameEx);
				} catch (IOException e) {
					LogUtil.logError("Exporting payload to file " + fileNameEx + " failed: " + e.getMessage(), e);
				}
				break;
			default:
				LogUtil.logError("Unknow command: " + cmd.toString(), null);
				break;
		}		
	}

	private MessageContainer getMessageContainer() {
		return (MessageContainer) messageTable.getData();
	}

	// DragSourceListener 
	@Override
	public void dragStart(DragSourceEvent event) { /* ignore */ }

	// DragSourceListener 
	@Override
	public void dragSetData(DragSourceEvent event) {
		try {
			TableItem selection[] = messageTable.getSelection();
			ArchivedMessage dragData[] = new ArchivedMessage[selection.length];
			for (int i=0; i<selection.length; ++i)
				dragData[i] = (ArchivedMessage) selection[i].getData();
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
