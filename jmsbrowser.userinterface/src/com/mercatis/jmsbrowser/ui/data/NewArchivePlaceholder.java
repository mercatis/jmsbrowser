package com.mercatis.jmsbrowser.ui.data;

import java.io.File;
import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class NewArchivePlaceholder implements KeyListener, FocusListener {
	private static final String DEFAULT_NAME = "New Archive";

	private TreeItem treeRoot;
	private File parentFolder;

	public NewArchivePlaceholder(TreeItem item, File parent) {
		item.setImage(IconStore.archive_move);
		treeRoot = item;
		parentFolder = parent;
		Tree tree = item.getParent();
		tree.setSelection(item);
		TreeEditor editor = (TreeEditor) tree.getData();
		// Clean up any previous editor control
		Control oldEditor = editor.getEditor();
		if (oldEditor != null)
			oldEditor.dispose();

		// The control that will be the editor must be a child of the Tree
		Text newEditor = new Text(tree, SWT.NONE);
		newEditor.setText(DEFAULT_NAME);
		newEditor.addKeyListener(this);
		newEditor.addFocusListener(this);
		newEditor.selectAll();
		newEditor.setFocus();
		editor.setEditor(newEditor, treeRoot);

	}

	private boolean finish(Text t) {
		String newName = t.getText();
		if (newName.length() == 0) {
			LogUtil.logError("Error creating archive. No name was entered.", null);
			t.setText(DEFAULT_NAME);
			t.selectAll();
			return false;
		}
		File newFolder = new File(parentFolder, newName);
		if (newFolder.exists()) {
			LogUtil.logError("Unable to create new archive. "+newName+" already exists.", null);
			t.selectAll();
			return false;
		}
		if (newFolder.mkdir()) {
			try {
				new MessageContainer(newFolder, treeRoot);
				LogUtil.logInfo("Created archive "+newName);
			} catch (IOException e) {
				treeRoot.dispose();
				LogUtil.logError("Error creating archive "+newName+". "+e.getMessage(), e);
			}
		} else {
			treeRoot.dispose();
			LogUtil.logError("Error creating archive "+newName+". Access denied.", null);
		}
		t.dispose();
		return true;
	}

	// KeyListener
	@Override
	public void keyPressed(KeyEvent e) { /* ignore */ }

	// KeyListener
	@Override
	public void keyReleased(KeyEvent e) {
		Text t = (Text) e.getSource();
		// ENTER
		if (((e.stateMask & SWT.MODIFIER_MASK) == 0) && (e.keyCode==13)) {
			finish(t);
			return;
		}
		// ESC
		if (((e.stateMask & SWT.MODIFIER_MASK) == 0) && (e.keyCode==27)) {
			t.dispose();
			treeRoot.getParent().deselectAll();
			treeRoot.dispose();
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
		if (!finish((Text) e.getSource())) {
			treeRoot.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					t.setFocus();
				}
			});
		}
	}
}
