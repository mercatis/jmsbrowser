package com.mercatis.jmsbrowser.ui.listener;

import org.eclipse.core.resources.IProject;
import com.mercatis.jmsbrowser.ui.exceptions.JmsBrowserUIException;

public interface ProjectCreationListener {
	public void onNewProject(IProject project) throws JmsBrowserUIException;
}
