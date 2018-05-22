package com.mercatis.jmsbrowser.ui;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import com.mercatis.jmsbrowser.ui.view.DestinationMonitorView;
import com.mercatis.jmsbrowser.ui.view.LogView;
import com.mercatis.jmsbrowser.ui.view.MessageArchiveView;
import com.mercatis.jmsbrowser.ui.view.ProjectsTreeView;

public class Perspective implements IPerspectiveFactory {
	public static final String viewFolderID = "com.mercatis.jmsbrowser.view.folder.placeholder";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(false);
		layout.addStandaloneView(ProjectsTreeView.ID, false, IPageLayout.LEFT, 0.3f, IPageLayout.ID_EDITOR_AREA);
		layout.setFixed(true);
		IFolderLayout folderLayout = layout.createFolder(viewFolderID, IPageLayout.RIGHT, 0.7f, IPageLayout.ID_EDITOR_AREA);
		folderLayout.addPlaceholder(MessageArchiveView.ID+":*");
		folderLayout.addPlaceholder(DestinationMonitorView.ID+":*");
		folderLayout.addView(LogView.ID);
	}

}
