package com.mercatis.jmsbrowser.ui.view;

import java.util.List;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.mercatis.jms.log.LogEntry;
import com.mercatis.jms.log.LogListener;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.ApplicationWorkbenchWindowAdvisor;
import com.mercatis.jmsbrowser.ui.listener.OpenUrlOnClickListener;
import com.mercatis.jmsbrowser.ui.util.FormatUtil;
import com.mercatis.jmsbrowser.ui.util.StackTraceFormatter;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class LogView extends JmsBrowserView implements LogListener, Listener {
	public static final String ID = "com.mercatis.jmsbrowser.view.log";
	private Table logTable;
	private int columnWidth = -1;
	private Shell tooltip;
	private Text tooltipLabel;

	final Listener labelDisposeListener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			Text label = (Text) event.widget;
			label.getShell().dispose();
		}
	};

	private TableColumn createTableColumn(Table t, int style, String text) {
		TableColumn col = new TableColumn(t, style);
		col.setText(text);
		col.setResizable(true);
		col.setMoveable(false);
		return col;
	}

	private void createAboutInfo(Composite parent) {
		Display d = parent.getDisplay();

		Label l = new Label(parent, SWT.NONE);
		l.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 3));
		l.setImage(ImageDescriptor.createFromFile(IconStore.class, "/icons/branding/icon_128x128.png").createImage());

		Label title = new Label(parent, SWT.NONE);
		title.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		title.setText(ApplicationWorkbenchWindowAdvisor.getVersion());
		FontData fdata[] = title.getFont().getFontData();
		for (FontData fd : fdata) {
			fd.setStyle(SWT.BOLD);
			fd.setHeight(fd.getHeight() * 3);
		}
		title.setFont(new Font(d, fdata));

		Label copyright = new Label(parent, SWT.WRAP);
		copyright.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		copyright.setText("Copyright 2009-2011 mercatis technologies AG. All rights reserved.");
		fdata = copyright.getFont().getFontData();
		for (FontData fd : fdata) {
			fd.setStyle(SWT.BOLD);
		}
		copyright.setFont(new Font(d, fdata));

		Label legal = new Label(parent, SWT.WRAP);
		legal.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		legal.setText("This product is proprietary trade secret information of mercatis technologies AG. Use, transcription, duplication and modification are strictly prohibited without prior written consent of mercatis technologies AG.");
	}

	private void createLinks(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		c.setLayout(new GridLayout(3, true));

		Link link1 = new Link(c, SWT.CENTER);
		link1.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
		link1.setText("<a>JMS Browser Homepage</a>");
		link1.addSelectionListener(new OpenUrlOnClickListener("http://www.jmsbrowser.com/"));

		Link link2 = new Link(c, SWT.CENTER);
		link2.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
		link2.setText("<a>mercatis technologies AG</a>");
		link2.addSelectionListener(new OpenUrlOnClickListener("http://www.mercatis.com/"));

		Link link3 = new Link(c, SWT.CENTER);
		link3.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
		link3.setText("<a>Icons © Yusuke Kamiyamane</a>");
		link3.addSelectionListener(new OpenUrlOnClickListener("http://p.yusukekamiyamane.com/"));

	}

	@Override
	public void createPartControl(Composite helper) {
		setPartName("");
		helper.setLayout(new GridLayout(2, false));

		createAboutInfo(helper);
		createLinks(helper);

		// logger
		Group logGroup = new Group(helper, SWT.NONE);
		logGroup.setText("Log");
		logGroup.setLayout(createFillLayout(4));
		logGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		Composite c = new Composite(logGroup, SWT.NONE);
		logTable = new Table(c, SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.VIRTUAL);
		//		logTable.setLinesVisible(true);
		logTable.setHeaderVisible(true);
		logTable.setToolTipText("");

		TableColumnLayout tcl = new TableColumnLayout();
		c.setLayout(tcl);
		TableColumn col1 = createTableColumn(logTable, SWT.LEFT, "Timestamp");
		tcl.setColumnData(col1, new ColumnWeightData(4));
		TableColumn col2 = createTableColumn(logTable, SWT.LEFT, "Message");
		tcl.setColumnData(col2, new ColumnWeightData(5));

		List<LogEntry> existingLogs = LogUtil.getLogMessages();
		int idx=0;
		for (LogEntry le : existingLogs) {
			addEntry(le, idx++);
		}
		LogUtil.setListener(this);
		final int listenerEvents[] = { SWT.Dispose, SWT.KeyDown, SWT.MouseMove, SWT.MouseHover, SWT.Resize, SWT.Paint };
		for (int event : listenerEvents)
			logTable.addListener(event, this);
	}

	private void addEntry(LogEntry le, int index) {
		TableItem ti = new TableItem(logTable, SWT.NONE, index);
		ti.setData(le);
		ti.setText(new String[] { FormatUtil.DATE_FORMAT.format(le.timeStamp), le.msg });
		switch (le.type) {
			case FATAL:
			case ERROR:
				ti.setImage(IconStore.log_error);
				break;
			case WARNING:
				ti.setImage(IconStore.log_warn);
				break;
			case INFO:
				ti.setImage(IconStore.log_info);
		}
	}

	@Override
	public void setFocus() {
		super.setFocus();
		logTable.setFocus();
	}

	// LogListener
	@Override
	public void onLogEntry(LogEntry le) {
		addEntry(le, 0);
	}

	@Override
	public void handleEvent(Event e) {
		switch (e.type) {
			case SWT.Paint:
				if (columnWidth<0 && logTable.getItemCount()>0 && logTable.isVisible()) {
					TableColumn col1 = logTable.getColumn(0);
					col1.pack();
					columnWidth = col1.getWidth();
				}
				break;
			case SWT.Dispose:
			case SWT.KeyDown:
			case SWT.MouseMove:
				break;
			case SWT.MouseHover: {
				final TableItem item = logTable.getItem(new Point(e.x, e.y));
				if (item != null) {
					if (tooltip != null && !tooltip.isDisposed())
						tooltip.dispose();
					final LogEntry le = (LogEntry) item.getData();
					final Shell shell = getSite().getShell();
					final Display display = shell.getDisplay();
					tooltip = new Shell(shell, SWT.ON_TOP | SWT.TOOL);
					tooltip.setLayout(new FillLayout());
					tooltipLabel = new Text(tooltip, SWT.MULTI | SWT.READ_ONLY);
					tooltipLabel.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
					tooltipLabel.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
					tooltipLabel.setText(new StackTraceFormatter(le.toString(), le.throwable).getStacktrace());
					tooltipLabel.addListener(SWT.MouseExit, labelDisposeListener);
					final Point size = tooltip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					final Point pt = logTable.toDisplay(e.x, e.y);
					tooltip.setBounds(pt.x-2, pt.y-2, size.x, size.y);
					tooltip.setVisible(true);
				}
			}
		}
	}
}
