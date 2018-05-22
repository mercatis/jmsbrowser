package com.mercatis.jmsbrowser.ui.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.listener.MessageContainerChangedListener;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;
import com.mercatis.jmsbrowser.ui.util.FormatUtil;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class MessageContainer implements Listener, KeyListener, FocusListener {
	public static enum ColumnType { TSTAMP, LABEL, PROPS, PAYLOAD };
	private ArrayList<ArchivedMessage> messages = new ArrayList<>();
	private File folder;
	private Comparator<ArchivedMessage> comp = ArchivedMessage.getComperator(ColumnType.TSTAMP, false);
	private List<MessageContainer> children = new LinkedList<>();
	private TreeItem treeRoot;
	private Font normalFont, modifiedFont;
	private MessageContainerChangedListener listener;

	public MessageContainer (File folder, TreeItem treeRoot) throws IOException {
		if (!folder.exists() && !folder.isDirectory())
			throw new IOException(folder.toString()+" does not exist or is no directory");

		boolean isRoot = treeRoot.getParentItem()==null;

		treeRoot.setData(this);
		treeRoot.setText(isRoot ? "Message Archive" : folder.getName());
		treeRoot.setImage(IconStore.archive_closed);

		this.treeRoot = treeRoot;
		this.folder = folder;

		int error = 0;

		for (File f : folder.listFiles()) {
			if (f.isDirectory()) {
				children.add(new MessageContainer(f, new TreeItem(treeRoot, SWT.NONE)));
			}
			if (f.isFile())
				try {
					messages.add(ArchivedMessage.readFromDisk(f));
				} catch (IOException e) {
					++error;
				}
		}
		treeRoot.setExpanded(true);
		Collections.sort(messages, comp);
		if (error>0)
			LogUtil.logError("there was an error reading "+error+" messages from disk", null);
		updateTreeLabel();
	}

	public void addListener(MessageContainerChangedListener listener) {
		this.listener = listener;
	}

	public void removeListener(MessageContainerChangedListener listener) {
		if (this.listener == listener)
			this.listener = null;
	}

	public String getName() {
		return treeRoot.getText();
	}

	public ArchivedMessage getMessage(int index) {
		return messages.get(index);
	}

	public void addMessage(ArchivedMessage msg, boolean saveNow) throws IOException {
		if (saveNow)
			msg.saveToDisk(folder);
		messages.add(msg);
		Collections.sort(messages, comp);
		updateTreeLabel();
		int idx = messages.indexOf(msg);
		if (listener != null)
			listener.onNewMessage(idx, msg);
	}

	public void removeMessage(ArchivedMessage msg) throws IOException {
		removeMessage(messages.indexOf(msg));
	}

	public void removeMessage(int index) throws IOException {
		ArchivedMessage msg = messages.get(index);
		File file = new File(folder, msg.getFilename());
		if (!file.exists() || file.delete()) {
			messages.remove(index);
			if (listener != null)
				listener.onMessageRemoved(index);
			updateTreeLabel();
		}
	}

	public void removeAllMessage() {
		int cnt = messages.size();
		for (int i=cnt-1; i>=0; --i) {
			try {
				removeMessage(i);
			} catch (IOException e) {
				// ignore deletion errors
			}
		}
	}

	private void updateTreeLabel() {
		treeRoot.setText(1, Integer.toString(messages.size()));
	}

	public int getMessageCount() {
		return messages.size();
	}

	public void setSortColumn(ColumnType col, int order) {
		comp = ArchivedMessage.getComperator(col, order==SWT.UP);
		Collections.sort(messages, comp);
	}

	public String getFolderPath() {
		try {
			return folder.toURI().toURL().toString().replace(':', '.');
		} catch (MalformedURLException e) {
			LogUtil.logError("Error escaping folder path: "+e.getMessage(), e);
		}
		return null;
	}

	public boolean isRoot() {
		return treeRoot.getParentItem()==null;
	}

	public void delete() {
		try {
			recusiveDelete();
		} catch (IOException e) {
			LogUtil.logError("Error deleting archive "+getName()+". "+e.getMessage(), e);
		}
	}

	private void recusiveDelete() throws IOException {
		for (MessageContainer mc : children)
			mc.recusiveDelete();
		for (ArchivedMessage am : messages)
			am.deleteFromDisk(folder);
		if (!folder.delete())
			throw new IOException("Unable to remove folder "+getName());
		treeRoot.dispose();
	}

	// Listener (for SWT.SetData)
	@Override
	public void handleEvent(Event event) {
		TableItem item = (TableItem) event.item;
		if (modifiedFont==null) {
			normalFont = item.getFont();
			FontData[] fds = normalFont.getFontData();
			for (FontData fd : fds)
				fd.setStyle(SWT.BOLD);
			modifiedFont = new Font(normalFont.getDevice(), fds);
		}

		int index = event.index;
		ArchivedMessage msg = messages.get(index);
		item.setData(msg);
		item.setText(new String[] { FormatUtil.DATE_FORMAT.format(msg.getTimestamp()), msg.getLabel() });
		Image img = null;
		if (msg.isTextMessage())
			img = IconStore.message_text;
		else if (msg.isBytesMessage())
			img = IconStore.message_binary;
		else if (msg.isStreamMessage())
			img = IconStore.message_stream;
		else if (msg.isObjectMessage())
			img = IconStore.message_object;
		item.setImage(img);
		if (msg.isModified())
			item.setFont(modifiedFont);
		else
			item.setFont(normalFont);
	}

	public List<MessageContainer> getChildren() {
		if (children.isEmpty())
			return null;
		return Collections.unmodifiableList(children);
	}

	public File getFolder() {
		return folder;
	}

	public void rename() {
		Tree tree = treeRoot.getParent();
		TreeEditor editor = (TreeEditor) tree.getData();
		// Clean up any previous editor control
		Control oldEditor = editor.getEditor();
		if (oldEditor != null)
			oldEditor.dispose();

		// The control that will be the editor must be a child of the Tree
		Text newEditor = new Text(tree, SWT.NONE);
		newEditor.setText(treeRoot.getText());
		newEditor.addKeyListener(this);
		newEditor.addFocusListener(this);
		newEditor.selectAll();
		newEditor.setFocus();
		editor.setEditor(newEditor, treeRoot);
	}

	public void createNewSubFolder() {
		TreeItem item = new TreeItem(treeRoot, SWT.NONE);
		treeRoot.setExpanded(true);
		new NewArchivePlaceholder(item, folder);
	}

	private void updateLocation(File newSubFolder) {
		if (!folder.equals(newSubFolder))
			folder = new File(newSubFolder, folder.getName());
		for (MessageContainer mc : children) {
			mc.updateLocation(folder);
		}
	}

	private boolean finishRename(Text t) {
		String oldName = getName();
		String newName = t.getText();
		if (oldName.equals(newName)) {
			t.dispose();
			return true;
		}
		File newFolder = new File(folder.getParentFile(), newName);
		if (newFolder.exists()) {
			LogUtil.logError("Unable to renamed archive "+oldName+". Archive "+newName+" already exists.", null);
			t.selectAll();
			return false;
		}
		if (!folder.renameTo(newFolder)) {
			LogUtil.logError("Error renaming archive "+oldName+" to "+newName+". Access denied.", null);
			t.selectAll();
			return false;
		}
		treeRoot.setText(newName);
		t.dispose();
		updateLocation(folder);
		treeRoot.getParent().select(treeRoot);
		LogUtil.logInfo("Renamed archive "+oldName+" to "+newName);
		return true;
	}

	// KeyListener
	@Override
	public void keyPressed(KeyEvent e) { /* ignore */ }

	// KeyListener
	@Override
	public void keyReleased(KeyEvent e) {
		// ENTER
		if (((e.stateMask & SWT.MODIFIER_MASK) == 0) && (e.keyCode==13)) {
			finishRename((Text) e.getSource());
			return;
		}
		// ESC
		if (((e.stateMask & SWT.MODIFIER_MASK) == 0) && (e.keyCode==27)) {
			((Text) e.getSource()).dispose();
			treeRoot.getParent().select(treeRoot);
			return;
		}
	}

	// FocusListener
	@Override
	public void focusGained(FocusEvent e) { /* ignore */ }

	// FocusListener
	@Override
	public void focusLost(FocusEvent e) {
		final Text t = (Text) e.getSource();
		if (!finishRename((Text) e.getSource())) {
			treeRoot.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					t.setFocus();
				}
			});
		}
	}
}