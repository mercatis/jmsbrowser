package com.mercatis.jmsbrowser.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;
import com.mercatis.jms.log.LogUtil;

public abstract class JmsBrowserView extends ViewPart {

	protected TableColumn createTableColumn(Table t, int style, String text, Object data, Listener sortListener) {
		TableColumn col = new TableColumn(t, style);
		col.setText(text);
		col.setData(data);
		if (data!=null && sortListener!=null) {
			col.addListener(SWT.Selection, sortListener);
		}
		return col;
	}
	
	@Override
	public void setFocus() {
		LogUtil.refresh(getViewSite().getActionBars().getStatusLineManager());
	}

	protected void copyToClipboard(String text) {
		Clipboard clipboard = new Clipboard(getSite().getShell().getDisplay());
		TextTransfer textTransfer = TextTransfer.getInstance();
		clipboard.setContents(new String[] { text }, new Transfer[] { textTransfer });
		clipboard.dispose();
	}

	protected FillLayout createFillLayout(int spacing) {
		FillLayout fl = new FillLayout();
		fl.marginHeight = spacing;
		fl.marginWidth = spacing;
		return fl;
	}

	protected GridLayout createGridLayout(int numColumns, boolean equalWidth, int spacing) {
		GridLayout gl = new GridLayout(numColumns, equalWidth);
		gl.marginHeight = spacing;
		gl.marginWidth = spacing;
		return gl;
	}

	protected String getFileName(int dialogType) {
		FileDialog fd = new FileDialog(getSite().getShell(), dialogType);
		fd.setText(dialogType == SWT.OPEN ? "Open" : "Save");
		fd.setFilterPath("C:/");
		String[] filterExt = { "*.txt", "*.xml", "*.*" };
		fd.setFilterExtensions(filterExt);
		return fd.open();
	}
	
	protected ToolItem addToolbarButton(ToolBar toolBar, int style, String toolTip, Image image, Object data, SelectionListener listener) {
		ToolItem ti = new ToolItem(toolBar, style);
		if (toolTip != null)
			ti.setToolTipText(toolTip);
		if (image != null)
			ti.setImage(image);
		if (data != null)
			ti.setData(data);
		if (listener != null)
			ti.addSelectionListener(listener);
		return ti;
	}
}
