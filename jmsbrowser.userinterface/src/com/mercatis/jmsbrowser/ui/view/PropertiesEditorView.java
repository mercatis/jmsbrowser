package com.mercatis.jmsbrowser.ui.view;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.prefs.BackingStoreException;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.data.ConnectionProject;
import com.mercatis.jmsbrowser.ui.exceptions.ServiceNotFoundException;

public class PropertiesEditorView extends ViewPart implements SelectionListener, ModifyListener {
	public static final String ID = "com.mercatis.jmsbrowser.view.propertieseditor";
	private static final String omitSettings[] = { "connectionService", "QueueRedThreshold", "MaxMessagesShown"};
	private static final String boolSettings[] = { "PrettyFormatDestinationNames"};
	private static final String[] boolDisplayValues = {"enabled", "disabled"};
	
	private Composite page;
	private List<Label> labels;
	private List<Text> fields;
	private List<Text> previous;
	private List<Label> boolLabels;
	private List<Combo> boolFields;
	private List<Text> boolPrevious;
	private Button[] buttons;

	public PropertiesEditorView() {
		// default constructor
	}

	@Override
	public void createPartControl(Composite parent) {
		page = parent;
		page.setLayout(new GridLayout(3, true));
	}
	
	private boolean skipField(String field) {
		for (String s : omitSettings)
			if (s.equals(field))
				return true;
		for (String s : boolSettings)
			if (s.equals(field))
				return true;
		return false;
	}
	
	private void addStringConfig(final String prefName, final IEclipsePreferences prefs, final String defaultValue) {
		final Label l = new Label(page, SWT.NONE);
		l.setText(prefName);
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		labels.add(l);
		
		int uiFlags = SWT.BORDER;
		
		if (prefName.equalsIgnoreCase("password"))
			uiFlags |= SWT.PASSWORD;
		
		String value = prefs.get(prefName, "");
		
		final Text curr = new Text(page, uiFlags);
		curr.setText(value);
		curr.setData(prefName);
		curr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		curr.addModifyListener(this);
		fields.add(curr);
		
		final Text prev = new Text(page, uiFlags | SWT.READ_ONLY);
		prev.setText(defaultValue==null ? value : defaultValue);
		prev.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		prev.setData(Boolean.valueOf(defaultValue));
		previous.add(prev);
	}

	private void addBoolConfig(final String prefName, final IEclipsePreferences prefs, final boolean defaultValue) {
		final Label l = new Label(page, SWT.NONE);
		l.setText(prefName);
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		boolLabels.add(l);
		
		int uiFlags = SWT.BORDER;
		
		if (prefName.equalsIgnoreCase("password"))
			uiFlags |= SWT.PASSWORD;
		
		boolean b = prefs.getBoolean(prefName, defaultValue);
		final Combo curr = new Combo(page, SWT.READ_ONLY);
		curr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		curr.setItems(boolDisplayValues);
		curr.setData(prefName);
		setComboBool(curr, b);
		curr.addModifyListener(this);
		boolFields.add(curr);
		
		final Text prev = new Text(page, uiFlags | SWT.READ_ONLY);
		prev.setData(Boolean.valueOf(b));
		prev.setText(b ? boolDisplayValues[0] : boolDisplayValues[1]);
		prev.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		boolPrevious.add(prev);
	}
	
	private void setComboBool(final Combo combo, boolean value) {
		combo.select(value ? 0 : 1);
	}
	
	private boolean getComboBool(final Combo combo) {
		return combo.getSelectionIndex()==0;
	}

	public void setConnectionProject(ConnectionProject cp) throws BackingStoreException, CoreException {
		if (page.getData()!=null)
			return;

		setPartName(cp.getProjectName() + " Properties");
		
		IProject p = cp.getProject();
		boolean wasOpen = p.isOpen();
		
		if (!wasOpen)
			p.open(new NullProgressMonitor());
		
		IEclipsePreferences prefs = cp.getPrefs();
		
		String prefNames[] = prefs.keys();
		
		fields = new ArrayList<Text>();
		labels = new ArrayList<Label>();
		previous = new ArrayList<Text>();
		boolFields = new ArrayList<Combo>();
		boolLabels = new ArrayList<Label>();
		boolPrevious = new ArrayList<Text>();
		
		page.setData(cp);
		page.setLayoutDeferred(true);

		FontData fd[] = page.getFont().getFontData();
		for (FontData data : fd) {
			data.setStyle(data.getStyle() | SWT.BOLD);
		}
		Font f = new Font(page.getDisplay(), fd);
		Label headerLabel = new Label(page, SWT.NONE);
		headerLabel.setText("Property");
		headerLabel.setFont(f);
		headerLabel = new Label(page, SWT.NONE);
		headerLabel.setText("New Value");
		headerLabel.setFont(f);
		headerLabel = new Label(page, SWT.NONE);
		headerLabel.setText("Old Value");
		headerLabel.setFont(f);
		new Label(page, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false, 3, 1));
		
		for (String prefName : prefNames) {
			if (skipField(prefName))
				continue;
			addStringConfig(prefName, prefs, null);
		}
		addStringConfig(omitSettings[1], prefs, "100");
		addStringConfig(omitSettings[2], prefs, "500");
		for (String prefName : boolSettings)
			addBoolConfig(prefName, prefs, Boolean.TRUE);
		
		new Composite(page, SWT.NONE); // placeholder

		buttons = new Button[2];
		buttons[0] = new Button(page, SWT.PUSH);
		buttons[0].setText("Apply");
		buttons[0].addSelectionListener(this);
		buttons[0].setLayoutData(new GridData(GridData.FILL, SWT.BOTTOM, true, true));
		buttons[0].setEnabled(false);
		
		buttons[1] = new Button(page, SWT.PUSH);
		buttons[1].setText("Reset");
		buttons[1].addSelectionListener(this);
		buttons[1].setLayoutData(new GridData(GridData.FILL, SWT.BOTTOM, true, true));
		buttons[1].setEnabled(false);

		page.layout();
		page.setLayoutDeferred(false);
		
		if (!wasOpen)
			p.close(new NullProgressMonitor());
		
		if (fields!=null && fields.size()>0) {
			fields.get(0).setFocus();
			int idx = fields.get(0).getText().length();
			fields.get(0).setSelection(idx, idx);
		}
	}

	@Override
	public void setFocus() {
		LogUtil.refresh(getViewSite().getActionBars().getStatusLineManager());
	}

	// SelectionListener
	@Override
	public void widgetSelected(SelectionEvent e) {
		boolean additionalWarning = false;
		Button b = (Button) e.getSource();
		ConnectionProject proj = (ConnectionProject) page.getData();
		final String projName = proj.getProjectName();
		IProject p = proj.getProject();
		IProgressMonitor pm = new NullProgressMonitor();
		if (proj.isConnected()) {
			if (MessageDialog.openQuestion(getSite().getShell(), "Restart connection?", "You have to disconnect to apply the new settings.\n\nAre you sure you want to disconnect "+projName+"?")) {
				try {
					proj.close();
				} catch (CoreException e1) {
					e1.printStackTrace();
				}
			} else {
				LogUtil.logWarn("Could not apply properties because the connection "+projName+" is still active.");
				return;
			}
		}
		boolean wasOpen = p.isOpen();
		try {
			if (!wasOpen) {
				p.open(pm);
			}
		} catch (CoreException ex) {
			LogUtil.logError("Error opening connection "+projName+": "+ ex.getMessage(), ex);
			return;
		}

		IEclipsePreferences prefs = proj.getPrefs();
		boolean apply = "Apply".equals(b.getText()); 
		for (Text field : fields) {
			String name = (String) field.getData();
			if (!apply)
				field.setText(prefs.get(name, ""));
			else
				prefs.put(name, field.getText());
		}
		for (int i=0; i<boolFields.size(); ++i) {
			final Combo boolField = boolFields.get(i);
			String name = (String) boolField.getData();
			if (!apply)
				setComboBool(boolField, prefs.getBoolean(name, prefs.getBoolean(name, (Boolean) previous.get(i).getData())));
			else
				prefs.putBoolean(name, getComboBool(boolField));
		}
		if (apply) {
			try {
				prefs.flush();
				prefs.sync();
				for (int i=0; i<fields.size(); ++i)
					previous.get(i).setText(fields.get(i).getText()); 
				for (int i=0; i<boolFields.size(); ++i)
					boolPrevious.get(i).setText(boolFields.get(i).getText()); 
				proj.initJmsConnection(proj.getPrefs());
				LogUtil.logInfo("Saved properties for "+projName+".");
			} catch (BackingStoreException e1) {
				LogUtil.logError("The properties for "+projName+" could not be applied: "+e1.getMessage(), e1);
			} catch (ServiceNotFoundException e2) {
				LogUtil.logError("FATAL ERROR: "+projName+" could be damaged or unusable: "+e2.getMessage(), e2);
			} catch (JmsBrowserException e3) {
				additionalWarning = true;
				LogUtil.logError("The connection "+projName+" could not be reinitalized: "+e3.getMessage(), e3);
			}
		}
		if (!wasOpen) {
			try {
				p.close(new NullProgressMonitor());
			} catch (CoreException ex) {
				LogUtil.logError("Error closing connection "+projName+": "+ ex.getMessage(), ex);
			}
		}
		if (additionalWarning)
			LogUtil.logWarn("Because of previous errors you will have to restart JmsBrowser for the changes to take effekt on the connection "+projName);
		modifyText(null);
	}

	// SelectionListener
	@Override
	public void widgetDefaultSelected(SelectionEvent e) { /* ignore */ }

	@Override
	public void modifyText(ModifyEvent e) {
		boolean disable = true;
		for (int i=0; i<fields.size(); ++i) {
			disable &= fields.get(i).getText().equals(previous.get(i).getText()); 
		}
		for (int i=0; i<boolFields.size(); ++i) {
			disable &= boolFields.get(i).getText().equals(boolPrevious.get(i).getText()); 
		}
		for (Button b: buttons) {
			if (b.getEnabled() != !disable)
				b.setEnabled(!disable);
		}
	}

}
