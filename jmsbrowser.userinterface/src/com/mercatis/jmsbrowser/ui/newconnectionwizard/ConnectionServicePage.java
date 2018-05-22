package com.mercatis.jmsbrowser.ui.newconnectionwizard;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.mercatis.jmsbrowser.ui.data.ServiceDescription;
import com.mercatis.jmsbrowser.ui.util.ConnectionServiceHelper;

public class ConnectionServicePage extends WizardPage implements SelectionListener, KeyListener {
	private static final String descriptionMessage = "Create a new connection using the selected connection service.";
	
	private org.eclipse.swt.widgets.List providerList;
	private Text projectName;

	public ConnectionServicePage() {
		super("ConnectionServicePage");
		setTitle("New Connection");
		setDescription(descriptionMessage);
	}

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		initializeDialogUnits(parent);

		composite.setLayout(new GridLayout());

		Label l = new Label(composite, SWT.NONE);
		l.setText("Project Name");
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		projectName = new Text(composite, SWT.BORDER);
		projectName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		projectName.addSelectionListener(this);
		projectName.addKeyListener(this);

		l = new Label(composite, SWT.NONE);
		l.setText("Connection Service");
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		providerList = new org.eclipse.swt.widgets.List(composite, SWT.SINGLE | SWT.BORDER);
		providerList.setLayoutData(new GridData(GridData.FILL_BOTH));
		try {
			String[] names = ConnectionServiceHelper.getAllServiceNames();
			providerList.setItems(names);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		providerList.select(0);
		providerList.addSelectionListener(this);

		setControl(composite);
	}
	
	public String getProjectName() {
		return projectName.getText();
	}

	public ServiceDescription getConnectionService() throws CoreException {
		return ConnectionServiceHelper.getService(providerList.getSelection()[0]);
	}
	
	@Override
	public boolean canFlipToNextPage() {
		String name = projectName.getText();
		if (name==null || name.length()==0)
			return false; 
		IProject ip = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (ip.exists()) {
			setErrorMessage("Connection with name '"+name+"' already exists. Please choose a different name.");
			return false;
		} else {
			setErrorMessage(null);
			setDescription(descriptionMessage);
		}
		return providerList.getSelectionIndex()!=-1;
	}

	// SelectionListener
	@Override
	public void widgetSelected(SelectionEvent e) {
		getWizard().getContainer().updateButtons();
	}

	// SelectionListener
	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		widgetSelected(e);
	}

	// KeyListener
	@Override
	public void keyPressed(KeyEvent e) { /* ignore */ }

	// KeyListener
	@Override
	public void keyReleased(KeyEvent e) {
		getWizard().getContainer().updateButtons();
	}
}
