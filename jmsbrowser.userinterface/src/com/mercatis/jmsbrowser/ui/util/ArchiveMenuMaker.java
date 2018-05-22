package com.mercatis.jmsbrowser.ui.util;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import com.mercatis.jmsbrowser.ui.data.MessageContainer;
import com.mercatis.jmsbrowser.ui.listener.AddToArchiveListener;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class ArchiveMenuMaker {
	private MessageContainer rootContainer;
	private SelectionListener menuListener;
	
	private SelectionListener makeListener(final AddToArchiveListener listener) {
		menuListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MenuItem mi = (MenuItem) e.getSource();
				listener.addToArchive((MessageContainer) mi.getData());
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }
		};
		return menuListener;
	}
	
	public ArchiveMenuMaker(MessageContainer root) {
		rootContainer = root;
	}
	
	private void recurse(Menu menu, AddToArchiveListener listener, MessageContainer mc) {
		List<MessageContainer> children = mc.getChildren();
		if (children != null) {
			MenuItem sub = MenuUtil.createItem(menu, mc.getName(), mc, SWT.CASCADE, IconStore.archive_closed, menuListener);
			Menu subMenu = new Menu(menu);
			sub.setMenu(subMenu);
			MenuUtil.createItem(subMenu, "<copy here>", mc, SWT.PUSH, IconStore.archive_move, menuListener);
			MenuUtil.createSeperator(subMenu);
			for (MessageContainer child : children) {
				recurse(subMenu, listener, child);
			}
		} else {
			MenuUtil.createItem(menu, mc.getName(), mc, SWT.PUSH, IconStore.archive_move, menuListener);
		}
	}
	
	private Menu makeMenu(Menu menu, AddToArchiveListener listener) {
		makeListener(listener);
		MenuUtil.createItem(menu, "Root Archive", rootContainer, SWT.PUSH, IconStore.archive_move, menuListener);
		List<MessageContainer> children = rootContainer.getChildren();
		if (children != null) {
			MenuUtil.createSeperator(menu);
			for (MessageContainer mc : children)
				recurse(menu, listener, mc);
		}
		return menu;
	}
	
	public Menu makePopupMenu(Shell shell, AddToArchiveListener listener) {
		Menu menu = new Menu(shell, SWT.POP_UP);
		return makeMenu(menu, listener);
	}
	
	public Menu makeSubMenu(Menu parentMenu, AddToArchiveListener listener) {
		MenuItem mi = MenuUtil.createItem(parentMenu, "Archive Message", null, SWT.CASCADE, IconStore.archive_closed, null);
		Menu menu = new Menu(parentMenu);
		mi.setMenu(menu);
		makeMenu(menu, listener);
		return menu;
	}
}
