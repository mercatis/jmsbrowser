package com.mercatis.jmsbrowser.ui.newconnectionwizard;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jmsbrowser.ui.data.ConnectionProperty;
import com.mercatis.jmsbrowser.ui.data.ServiceDescription;
import com.mercatis.jmsbrowser.ui.dialog.ErrorDialogWithStacktrace;
import com.mercatis.jmsbrowser.ui.util.store.ColorStore;

public class ConnectionSettingsPage extends WizardPage {

	private static final int SIZING_TEXT_FIELD_WIDTH = 250;
	private Button testCon;
	private Composite page;
	private Label[] labels;
	private Text[] fields;
	
	private Listener modifyListener = new Listener() {
		@Override
		public void handleEvent(Event e) {
			setPageComplete(validatePage());
		}
	};

	private ServiceDescription currentConnectionService;
	
	public ServiceDescription getConnectionService() {
		return currentConnectionService;
	}
	
	public ConnectionSettingsPage() {
		super("ConnectionSettingsPage");
		setDescription("Please provide connection settings for the connection.");
	}
	
	public void setServiceName(ServiceDescription serviceDesc) {
		if (serviceDesc==null || (currentConnectionService!=null && serviceDesc.id.equals(currentConnectionService.id)))
			return;
		
		currentConnectionService = serviceDesc;
		
		setTitle(serviceDesc.displayName+" Connection");
		
		// defer layout
		page.setLayoutDeferred(true);
		
		// remove old controls
		for (Control child : page.getChildren()) {
			child.dispose();
		}
		
		IConfigurationElement[] elements = serviceDesc.confElement.getChildren("property");
		fields = new Text[elements.length];
		labels = new Label[elements.length];
		
		ConnectionProperty cp;
		for (int i=0; i<elements.length; ++i) {
			cp = new ConnectionProperty(elements[i]);
			labels[i] = new Label(page, SWT.NONE);
			labels[i].setText(cp.name+ (cp.mandatory ? ":" : "*:"));
			
			if (cp.name.equals("Password")) {
				fields[i] = new Text(page, SWT.BORDER | SWT.PASSWORD);
			} else {
				fields[i] = new Text(page, SWT.BORDER);
			}
			if (cp.defaultValue != null)
				fields[i].setText(cp.defaultValue);
			
			GridData data = new GridData(GridData.FILL_HORIZONTAL);
			data.widthHint = SIZING_TEXT_FIELD_WIDTH;
			fields[i].setLayoutData(data);
			fields[i].addListener(SWT.Modify, modifyListener);
			fields[i].setData(cp);
		}
        testCon = new Button(page, SWT.PUSH);
        testCon.setText("Test Connection");
//        testCon.setEnabled(false);
        testCon.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				testConnection();
			}
			@Override
			public void mouseDown(MouseEvent e) {}
			@Override
			public void mouseDoubleClick(MouseEvent e) {}
		});
		Label l = new Label(page, SWT.RIGHT);
		l.setText("* = optional");
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		page.layout(true, true);
		page.setLayoutDeferred(false);
		setPageComplete(validatePage());
	}

	@Override
	public void createControl(Composite parent) {
		page = new Composite(parent, SWT.NULL);
		page.setLayout(new GridLayout(2, false));
        initializeDialogUnits(parent);

		currentConnectionService = null;
        setControl(page);
	}
	
	public Map<String, String> getProperties() {
		Map<String, String> properties = new HashMap<String, String>();
		for (Text t : fields) {
			String s = t.getText();
			if (s!=null && s.length()>0) {
				ConnectionProperty cp = (ConnectionProperty) t.getData();
				properties.put(cp.name, s);
			}
		}
		return properties;
	}
	
	public void getProperties(IEclipsePreferences prefs) {
		for (Text t : fields) {
			String s = t.getText();
			if (s!=null) {
				ConnectionProperty cp = (ConnectionProperty) t.getData();
				prefs.put(cp.name, s);
			}
		}
	}
	
	private void testConnection() {
		Map<String, String> properties = getProperties();
		JmsConnection connection = null;
		try {
			connection = currentConnectionService.service.createConnection(properties);
			connection.open();
			MessageDialog.openInformation(getShell(), "Connection Test Succeeded!", "Connection to server succeeded.");
		} catch (JmsBrowserException ex) {
			new ErrorDialogWithStacktrace(getShell(), null, "Connection Test Failed", "The connection test did not succeed. See the stacktrace for detailed informations.", ex);
		} finally {
			if (connection!=null)
				connection.close();
		}
	}
	
	public boolean validatePage() {
		boolean result = true;
		for (Text t : fields) {
			ConnectionProperty cp = (ConnectionProperty) t.getData();
			String s = t.getText();
			if (cp.mandatory && s!=null && s.length()==0) {
				result = false;
				t.setBackground(ColorStore.lightred);
			} else {
				if (t.getBackground() != null)
					t.setBackground(null);
			}
		}
		setErrorMessage(result ? null : "At least one required field is missing.");
		testCon.setEnabled(result);
		return result;
	}
	
	@Override
	public boolean canFlipToNextPage() {
		return false;
	}
}
