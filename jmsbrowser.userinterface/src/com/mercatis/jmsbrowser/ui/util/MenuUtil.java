package com.mercatis.jmsbrowser.ui.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

public class MenuUtil {
	public static MenuItem createItem(Menu menu, String title, Object data, int type, Image img, SelectionListener listener) {
		MenuItem item = new MenuItem (menu, type);
		if (title != null)
			item.setText(title);
		if (img != null)
			item.setImage(img);
		if (data != null)
			item.setData(data);
		if (listener != null)
			item.addSelectionListener(listener);
		return item;
	}
	
	public static MenuItem createSeperator(Menu menu) {
		return createItem(menu, null, null, SWT.SEPARATOR, null, null);
	}

	public static MenuItem createNop(Menu menu) {
		MenuItem i = createItem(menu, "<no commands available>", null, SWT.PUSH, null, null);
		i.setEnabled(false);
		return i;
	}
}
