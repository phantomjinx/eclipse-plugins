package org.teiid.project.refresher;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.PlatformUI;

public class RefreshProjectHandler implements IHandler {

    private static final String DOT_PROJECT_FILE = ".project";

    private Logger logger = Logger.getLogger(this.getClass().getCanonicalName());

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // Choose the directory to refresh from
        final String projectDirectory = getChosenDirectory();
        if (projectDirectory == null) {
            return null;
        }

        Job job = new Job("Project synchronizer") {

            @Override
            public IStatus run(IProgressMonitor monitor) {
                try {
                    synchronizeProjects(projectDirectory, monitor);
                } catch (CoreException ex) {
                    logger.severe(ex.getMessage());
                    ex.printStackTrace();
                    return Status.CANCEL_STATUS;
                }
                return Status.OK_STATUS;
            }
        };

        job.setPriority(Job.LONG);
        job.schedule();
        return null;
    }

    private void synchronizeProjects(final String projectDirectory, IProgressMonitor monitor) throws CoreException {
        // Import those projects not already in the workspace
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        final Map<String, IProject> projectMap = new HashMap<String, IProject>();

        if (projects != null) {
            for (IProject project : projects) {
                projectMap.put(project.getName(), project);
            }
        }

        ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
                monitor.beginTask("Synchronizing projects to filesystem", 2);
                // Import new projects
                monitor.subTask("Importing new projects...");
                importProjects(projectDirectory, projectMap);
                monitor.worked(1);

                // Refresh all projects in the workspace
                // Remove those projects no longer in the filesystem
                monitor.subTask("Refreshing projects...");
                refreshProjects(projectMap, null);
                monitor.done();
            }
        }, ResourcesPlugin.getWorkspace().getRoot(), IWorkspace.AVOID_UPDATE, monitor);
    }

    private void refreshProjects(Map<String, IProject> projectMap, IProgressMonitor monitor) throws CoreException {
        Collection<IProject> projects = new ArrayList<IProject>(projectMap.values());

        for (IProject project : projects) {
            IPath location = project.getLocation();

            if (location.toFile().exists() && !isShellProject(project)) {
                project.refreshLocal(IProject.DEPTH_INFINITE, monitor);
            } else {
                projectMap.remove(project.getName());
                project.delete(false, true, monitor);
            }
        }

        projects.clear();
    }

    private boolean isShellProject(IProject project) {
        IPath location = project.getLocation();
        if (location == null) {
            return true;
        }

        File projectDir = location.toFile();
        if (projectDir == null || !projectDir.exists()) {
            return true;
        }

        for (File projectFile : projectDir.listFiles()) {
            if (projectFile.getName().equals(DOT_PROJECT_FILE)) {
                continue;
            } else {
                return false;
            }
        }

        return true;
    }

    private void importProjects(String projectDirectory, Map<String, IProject> projectMap) throws CoreException {

        File projectDir = new File(projectDirectory);
        if (!projectDir.exists()) {
            logger.severe("Chosen directory does not exist!");
            return;
        }

        importProjects(projectDir, projectMap);
    }

    private void importProjects(File projectDir, Map<String, IProject> projectMap) throws CoreException {
        File projectFile = new File(projectDir, DOT_PROJECT_FILE);

        if (projectFile.exists()) {
            // Import the project
            Path projectPath = new Path(projectFile.getAbsolutePath());
            IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(projectPath);

            if (projectMap.containsKey(description.getName())) {
                // Already imported this project
                return;
            }

            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(description.getName());
            project.create(description, null);
            project.open(null);

            projectMap.put(project.getName(), project);
        } else {
            // Not a project but maybe contains projects?
            for (File subDir : projectDir.listFiles()) {
                if (!subDir.isDirectory()) {
                    continue;
                }

                importProjects(subDir, projectMap);
            }
        }
    }

    private String getChosenDirectory() {
        DirectoryDialog dlg = new DirectoryDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell());

        // Change the title bar text
        dlg.setText("Select directory to be synchronised against.");

        // Customizable message displayed in the dialog
        dlg.setMessage("Select a directory");

        // Calling open() will open and run the dialog.
        // It will return the selected directory, or
        // null if user cancels
        String dir = dlg.open();
        return dir;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isHandled() {
        return true;
    }

    @Override
    public void addHandlerListener(IHandlerListener handlerListener) {
        // Not required
    }

    @Override
    public void dispose() {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeHandlerListener(IHandlerListener handlerListener) {
        // No Required
    }

}
