package com.mercatis.jmsbrowser.ui.newconnectionwizard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.List;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import com.mercatis.jms.JmsConnectionService;
import com.mercatis.jms.LibraryDependency;
import com.mercatis.jmsbrowser.ui.data.ServiceDescription;

public class ClientLibHelperPage extends WizardPage implements MouseListener, FilenameFilter {
	private final String os = System.getProperty("os.name");
	private final String arch = System.getProperty("os.arch");

	private List missingLibs;
	private DirectoryDialog dlg;
	private Bundle bundle;
	private URL bundleUrl;
	private java.util.List<LibraryDependency> libraries;

	public static void copyFile(File in, File out) throws IOException {
		FileInputStream is = new FileInputStream(in);
		FileOutputStream os = new FileOutputStream(out);
		FileChannel inChannel = is.getChannel();
		FileChannel outChannel = os.getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} finally {
			if (inChannel != null) inChannel.close();
			if (outChannel != null) outChannel.close();
			is.close();
			os.close();
		}
	}
	
	public ClientLibHelperPage() {
		super("ClientLibPage");
		setDescription("Please provide the location of the following libraries:");
	}

	@Override
	public void createControl(Composite parent) {
		dlg = new DirectoryDialog(getShell());
		dlg.setText("Browse for directory...");
		dlg.setMessage("Select the directory where to search for the missing libraries.");
		Composite c = new Composite(parent, SWT.NULL);
		c.setLayout(new GridLayout());
		missingLibs = new List(c, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
		missingLibs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Button b = new Button(c, SWT.PUSH);
		b.setText("Select directory...");
		b.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));
		b.addMouseListener(this);
		setControl(c);
	}
	
	@Override
	public void mouseDoubleClick(MouseEvent e) {}

	@Override
	public void mouseDown(MouseEvent e) {}

	private boolean updateMissingLibs() {
		missingLibs.removeAll();
		if (libraries!=null) {
			for (LibraryDependency lib : libraries) {
				try {
					URL url = new URL(bundleUrl+"lib/"+lib.getPath());
					File f = new File(url.getPath());
					if(!f.exists())
						missingLibs.add(lib.toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		boolean result = missingLibs.getItemCount()==0;
		setPageComplete(result);
		return result;
	}
	
	private void copyMissingLibs(String srcdir) throws IOException {
		if (libraries==null)
			return;
		File dir = new File(srcdir);
		String[] children = dir.list(this);
		File srcFile, destFile;
		for (String s : children) {
			for (LibraryDependency ld : libraries) {
				if (ld.name.equals(s)) {
					srcFile = new File(srcdir+"/"+s);
					destFile = new File(new URL(bundleUrl+"lib/"+ld.getPath()).getPath());
					if (ld.subpath!=null)
						new File(new URL(bundleUrl+"lib/"+ld.subpath).getPath()).mkdirs();
					copyFile(srcFile, destFile);
					break;
				}
			}
		}
	}
	
	@Override
	public void mouseUp(MouseEvent e) {
		String dir = dlg.open();
		try {
			if (dir != null) {
				copyMissingLibs(dir);
			}
			if (updateMissingLibs()) {
				bundle.stop();
				bundle.update();
				bundle.start();
				setMessage("All libraries installed, please click Next to continue...", INFORMATION);
			}
		} catch (Exception ex) {
			setPageComplete(false);
			setMessage(ex.getMessage(), ERROR);
			ex.printStackTrace();
		}
	}

	public boolean setService(ServiceDescription sd) throws IOException {
		JmsConnectionService service = sd.service;
		setTitle(service.getDisplayName()+" has missing components.");
		this.bundle = FrameworkUtil.getBundle(service.getClass());
		this.bundleUrl = FileLocator.toFileURL(bundle.getEntry ("/"));
		this.libraries = service.getNotIncludedLibraryNames(os, arch);
		return updateMissingLibs();
	}

	@Override
	public boolean accept(File dir, String name) {
		for (LibraryDependency ld : libraries)
			if (missingLibs.indexOf(ld.toString())!=-1 && name.equals(ld.name))
				return true;
		return false;
	}
}
