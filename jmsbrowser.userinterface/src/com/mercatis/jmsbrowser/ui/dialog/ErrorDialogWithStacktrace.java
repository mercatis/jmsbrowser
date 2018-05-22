package com.mercatis.jmsbrowser.ui.dialog;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.mercatis.jmsbrowser.ui.util.StackTraceFormatter;

public class ErrorDialogWithStacktrace implements SelectionListener {
	
	private final Shell shell;
	private final FontMetrics fontMetrics;
	
	public ErrorDialogWithStacktrace(Shell parentShell, Point size, String title, String message ,Throwable error) {
		// Compute and store a font metric
		GC gc = new GC(parentShell);
		gc.setFont(parentShell.getFont());
		fontMetrics = gc.getFontMetrics();
		gc.dispose();

		// calling formatStacktrace also sets the reason
		final StackTraceFormatter stf = new StackTraceFormatter(error);
		
		shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		shell.setText(title);
		
		Label icon = new Label(shell, SWT.NONE);
		icon.setImage(shell.getDisplay().getSystemImage(SWT.ICON_ERROR));
		icon.setLayoutData(new GridData());
		
		Label l = new Label(shell, SWT.WRAP);
		l.setText(message + "\n\nReason: " + stf.getReasonString());
		l.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		
		Text t = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		t.setText(stf.getStacktrace());
		t.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

		Button b = new Button(shell, SWT.PUSH);
		b.setText(IDialogConstants.OK_LABEL);
		b.addSelectionListener(this);
		int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		GridData data= new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1);
		Point minSize = b.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		data.widthHint = Math.max(widthHint, minSize.x);
		b.setLayoutData(data);

		shell.setDefaultButton(b);
		shell.setSize(size==null ? parentShell.getSize() : size);
		shell.setLayout(getShellLayout(l));
		shell.layout();
		shell.open();
	}
	
	private GridLayout getShellLayout(Control control) {
		GridLayout gl = new GridLayout(2, false);
		gl.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		gl.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		gl.verticalSpacing = 2 * convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		gl.horizontalSpacing = 2 * convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		return gl;
	}
	
	private static final int HORIZONTAL_DIALOG_UNIT_PER_CHAR = 4;
	private static final int VERTICAL_DIALOG_UNITS_PER_CHAR = 8;
	
	public int convertVerticalDLUsToPixels(int dlus) {
		// round to the nearest pixel
		return (fontMetrics.getHeight() * dlus + VERTICAL_DIALOG_UNITS_PER_CHAR / 2) / VERTICAL_DIALOG_UNITS_PER_CHAR;
	}
	
	public int convertHorizontalDLUsToPixels(int dlus) {
		// round to the nearest pixel
		return (fontMetrics.getAverageCharWidth() * dlus + HORIZONTAL_DIALOG_UNIT_PER_CHAR / 2) / HORIZONTAL_DIALOG_UNIT_PER_CHAR;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		shell.dispose();
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		widgetSelected(e);
	}
}
