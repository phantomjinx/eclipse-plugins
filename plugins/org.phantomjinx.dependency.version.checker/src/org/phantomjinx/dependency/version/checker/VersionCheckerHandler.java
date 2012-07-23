/*
 * Copyright (c) 2012, Paul Richardson (phantomjinx). All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.phantomjinx.dependency.version.checker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.PluginModelManager;
import org.osgi.framework.Version;

/**
 *
 */
public class VersionCheckerHandler implements IHandler {

	private static final String DOT = "."; //$NON-NLS-1$

	private static final String NEWLINE = "\n"; //$NON-NLS-1$

	private static final String SPEECH_MARK = "\""; //$NON-NLS-1$

	private static final String SEMI_COLON = ";"; //$NON-NLS-1$
	
	private static final String COLON = ":"; //$NON-NLS-1$
	
	private static final String COMMA = ","; //$NON-NLS-1$

	private static final String SPACE = " "; //$NON-NLS-1$
	
	private static final String OPEN_SQUARE_BRACKET = "["; //$NON-NLS-1$

	private static final String CLOSE_BRACKET = ")"; //$NON-NLS-1$

	private static final String META_INF = "META-INF"; //$NON-NLS-1$

	private static final String MANIFEST_FILENAME = "MANIFEST.MF"; //$NON-NLS-1$

	private static final String REQUIRE_BUNDLE = "Require-Bundle:"; //$NON-NLS-1$
	
	private static final String BUNDLE_VERSION_TAG = ";bundle-version=\""; //$NON-NLS-1$
	
	private static final Version ZERO = new Version(0, 0, 0);

	private Logger logger = Logger
			.getLogger(this.getClass().getCanonicalName());

	@Override
	public Object execute(ExecutionEvent event) {

		Job job = new Job("Dependency Checking") { //$NON-NLS-1$

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					versionProjects(monitor);
				}
				catch (CoreException ex) {
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

	/**
	 * Version dependencies of all projects:
	 * 
	 * @param projectDirectory
	 * @param monitor
	 * @throws CoreException
	 */
	private void versionProjects(IProgressMonitor monitor) throws CoreException {
		Map<String, IProject> projectMap = new HashMap<String, IProject>();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		if (projects == null) {
			return;
		}
		
		for (IProject project : projects) {
			projectMap.put(project.getName(), project);
		}	
		
		PluginModelManager modelManager = PDECore.getDefault().getModelManager();
		IPluginModelBase[] pluginModels = modelManager.getAllModels(true);
		
		for (IPluginModelBase pluginModel : pluginModels) {
			BundleDescription bundleDescription = pluginModel.getBundleDescription();
			
			IProject project = projectMap.get(bundleDescription.getSymbolicName());
			if (project == null) {
				continue;
			}
			
			BundleSpecification[] requiredBundles = bundleDescription.getRequiredBundles();
			if (requiredBundles == null || requiredBundles.length == 0) {
				continue;
			}
			
			logger.info("Analysing " + bundleDescription.getSymbolicName() + "'s dependencies ..."); //$NON-NLS-1$ //$NON-NLS-2$
			
			IFolder metaInf = project.getFolder(META_INF);
			IFile manifestIFile = metaInf.getFile(MANIFEST_FILENAME);
			if (manifestIFile == null || ! manifestIFile.exists()) {
				logger.severe("Cannot get the manifest for plugin " + project.getName()); //$NON-NLS-1$
				continue;
			}
			
			File manifestFile = manifestIFile.getRawLocation().makeAbsolute().toFile();
			Map<String, Version> reqBundleMap = new HashMap<String, Version>();
			
			for (BundleSpecification bundleSpec : requiredBundles) {
				logger.info("\t" + bundleSpec.getVersionRange() + "\t" + bundleSpec.getName()); //$NON-NLS-1$ //$NON-NLS-2$
				
				IPluginModelBase reqBundleModel = modelManager.findModel(bundleSpec.getName());
				if (reqBundleModel == null) {
					logger.severe("Failed to find the plugin " + bundleSpec.getName()); //$NON-NLS-1$
					continue;
				}
				
				reqBundleMap.put(bundleSpec.getName(), reqBundleModel.getBundleDescription().getVersion());
			}
			
			updateManifest(manifestFile, reqBundleMap);
		}
		
		projectMap.clear();
	}

	/**
	 * @param manifestFile
	 * @param reqBundleMap
	 */
	private void updateManifest(File manifestFile, Map<String, Version> reqBundleMap) {
		try {
			StringBuffer buf = readManifest(manifestFile, reqBundleMap);
			
			if (buf == null) {
				return;
			}
			
			writeManifest(manifestFile, buf);
		}
		catch (Exception ex) {
			logger.severe("Failed to update manifest file " + manifestFile.getAbsolutePath()); //$NON-NLS-1$
			ex.printStackTrace();
		}
	}
	
	private StringBuffer readManifest(File manifestFile, Map<String, Version> reqBundleMap) {
		FileInputStream fis = null;
		BufferedReader reader = null;
		
		try {
			fis = new FileInputStream(manifestFile);
			reader = new BufferedReader(new InputStreamReader(fis));

			String line;
			StringBuffer buf = new StringBuffer();
			boolean inRequireBundle = false;

			while ((line = reader.readLine()) != null) {
				if (line.startsWith(REQUIRE_BUNDLE)) {
					inRequireBundle = true;
				} else if (inRequireBundle && !line.startsWith(SPACE)
						&& line.contains(COLON)) {
					// finished the require-bundle section
					inRequireBundle = false;
				}

				if (inRequireBundle) {
					line = removeBundleVersionTag(line);
				
					for (Entry<String, Version> entry : reqBundleMap.entrySet()) {
						String reqBundle = entry.getKey();

						if (line.contains(reqBundle + COMMA) || line.endsWith(reqBundle)) {
							String minVersion = entry.getValue().getMajor() + DOT + entry.getValue().getMinor() + DOT + entry.getValue().getMicro();
							String maxVersion = (entry.getValue().getMajor() + 1) + DOT + ZERO.getMinor() + DOT + ZERO.getMicro(); 
							
							String lineAndVersion = reqBundle + BUNDLE_VERSION_TAG + OPEN_SQUARE_BRACKET + minVersion + COMMA + maxVersion + CLOSE_BRACKET + SPEECH_MARK;
							line = line.replace(reqBundle, lineAndVersion);
						}
					}
				}

				buf.append(line);
				buf.append(NEWLINE);
			}
			
			return buf;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * @param line
	 * @return
	 */
	private String removeBundleVersionTag(String line) {
		if (! line.contains(BUNDLE_VERSION_TAG))
			return line;
		
		String[] splits = line.split(SEMI_COLON);
		String newLine = splits[0];
		
		if (line.endsWith(COMMA)) {
			newLine = newLine.concat(COMMA);
		}
		
		return newLine;
	}

	private void writeManifest(File manifestFile, StringBuffer buf) throws Exception {
		FileWriter writer = new FileWriter(manifestFile);
		writer.write(buf.toString());
		writer.close();
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
