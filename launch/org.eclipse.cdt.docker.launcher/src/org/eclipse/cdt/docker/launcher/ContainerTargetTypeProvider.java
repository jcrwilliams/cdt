/*******************************************************************************
 * Copyright (c) 2017, 2018 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * 		Red Hat Inc. - modified for use with Docker Container launching
 *******************************************************************************/
package org.eclipse.cdt.docker.launcher;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.ICBuildConfigurationManager;
import org.eclipse.cdt.core.build.ICBuildConfigurationManager2;
import org.eclipse.cdt.debug.core.CDebugCorePlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.launchbar.core.ILaunchBarManager;
import org.eclipse.launchbar.core.target.ILaunchTarget;
import org.eclipse.launchbar.core.target.ILaunchTargetManager;
import org.eclipse.launchbar.core.target.ILaunchTargetProvider;
import org.eclipse.launchbar.core.target.ILaunchTargetWorkingCopy;
import org.eclipse.launchbar.core.target.TargetStatus;
import org.eclipse.linuxtools.docker.core.DockerConnectionManager;
import org.eclipse.linuxtools.docker.core.EnumDockerConnectionState;
import org.eclipse.linuxtools.docker.core.IDockerConnection;
import org.eclipse.linuxtools.docker.core.IDockerConnectionManagerListener;
import org.eclipse.linuxtools.docker.core.IDockerImage;

/**
 * @since 1.2
 * @author jjohnstn
 *
 */
public class ContainerTargetTypeProvider
		implements ILaunchTargetProvider, IDockerConnectionManagerListener {

	public static final String TYPE_ID = "org.eclipse.cdt.docker.launcher.launchTargetType.container"; //$NON-NLS-1$
	public static final String CONTAINER_LINUX = "linux-container"; //$NON-NLS-1$

	private ILaunchTargetManager targetManager;

	@Override
	public synchronized void init(ILaunchTargetManager targetManager) {
		this.targetManager = targetManager;
		ILaunchBarManager launchbarManager = CDebugCorePlugin
				.getService(ILaunchBarManager.class);
		ILaunchTarget defaultTarget = null;
		try {
			defaultTarget = launchbarManager.getActiveLaunchTarget();
		} catch (CoreException e) {
			// ignore
		}
		IDockerConnection[] connections = DockerConnectionManager.getInstance()
				.getConnections();
		Map<String, IDockerConnection> establishedConnectionMap = new HashMap<>();
		Set<String> imageNames = new HashSet<>();
		for (IDockerConnection connection : connections) {
			// Get Images before checking state as the state may be
			// unknown until a request is made
			List<IDockerImage> images = connection.getImages();
			if (connection
					.getState() == EnumDockerConnectionState.ESTABLISHED) {
				establishedConnectionMap.put(connection.getUri(), connection);
			}
			for (IDockerImage image : images) {
				if (!image.isDangling() && !image.isIntermediateImage()) {
					String imageName = "[" //$NON-NLS-1$
							+ image.repoTags().get(0).replace(':', '_')
							// .replace('/', '_')
							+ "]"; //$NON-NLS-1$
					if (imageNames.contains(imageName)) {
						imageName += "[" + connection.getName() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
					}
					imageNames.add(imageName);
					ILaunchTarget target = targetManager
							.getLaunchTarget(TYPE_ID, imageName);
					if (target == null) {
						target = targetManager.addLaunchTarget(TYPE_ID,
								imageName);
					}
					ILaunchTargetWorkingCopy wc = target.getWorkingCopy();
					wc.setAttribute(ILaunchTarget.ATTR_OS, CONTAINER_LINUX);
					wc.setAttribute(ILaunchTarget.ATTR_ARCH,
							Platform.getOSArch());
					wc.setAttribute(IContainerLaunchTarget.ATTR_CONNECTION_URI,
							connection.getUri());
					wc.setAttribute(IContainerLaunchTarget.ATTR_IMAGE_ID,
							image.repoTags().get(0));

					wc.save();
				}
			}
		}

		// remove any launch targets for closed/disabled connections
		ILaunchTarget[] targets = targetManager.getLaunchTargetsOfType(TYPE_ID);
		for (ILaunchTarget target : targets) {
			try {
				String uri = target.getAttribute(
						IContainerLaunchTarget.ATTR_CONNECTION_URI, ""); //$NON-NLS-1$
				if (!establishedConnectionMap.containsKey(uri)) {
					targetManager.removeLaunchTarget(target);
				}
			} catch (IllegalStateException e) {
				// ignore
			}
		}

		// add a Docker Connection listener to handle enablement/disablement of
		// Connections
		DockerConnectionManager.getInstance()
				.addConnectionManagerListener(this);

		// re-check configs in case an enabled Connection has made old configs
		// valid again do this in a separate job to prevent a possible
		// deadlock trying to get the lock on the CBuildConfigurationManager
		// "configs" map (Bug 540085)
		Job checkConfigs = new Job("Check configs") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				// call the recheckConfigs method in case any disabled targets
				// are now
				// ok
				ICBuildConfigurationManager mgr = CCorePlugin
						.getService(ICBuildConfigurationManager.class);
				ICBuildConfigurationManager2 cbuildmanager = (ICBuildConfigurationManager2) mgr;
				cbuildmanager.recheckConfigs();
				return Status.OK_STATUS;
			}
		};
		checkConfigs.setUser(true);
		checkConfigs.schedule();

		try {
			launchbarManager.setActiveLaunchTarget(defaultTarget);
		} catch (CoreException e) {
			DockerLaunchUIPlugin.log(e);
		}

	}

	@Override
	public TargetStatus getStatus(ILaunchTarget target) {
		// Always OK
		return TargetStatus.OK_STATUS;
	}

	@Override
	public synchronized void changeEvent(final IDockerConnection connection,
			final int type) {
		Job checkConfigs = new Job("Check configs") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				ICBuildConfigurationManager mgr = CCorePlugin
						.getService(ICBuildConfigurationManager.class);
				ICBuildConfigurationManager2 manager = (ICBuildConfigurationManager2) mgr;

				if (type == IDockerConnectionManagerListener.ADD_EVENT
						|| type == IDockerConnectionManagerListener.ENABLE_EVENT) {
					ILaunchBarManager launchbarManager = CDebugCorePlugin
							.getService(ILaunchBarManager.class);
					ILaunchTarget defaultTarget = null;
					try {
						defaultTarget = launchbarManager
								.getActiveLaunchTarget();
					} catch (CoreException e) {
						// ignore
					}

					List<IDockerImage> images = connection.getImages();
					for (IDockerImage image : images) {
						if (!image.isDangling()
								&& !image.isIntermediateImage()) {

							String imageName = "[" //$NON-NLS-1$
									+ image.repoTags().get(0).replace(':', '_')
							// .replace('/', '_')
									+ "]"; //$NON-NLS-1$
							String imageName2 = imageName + "[" //$NON-NLS-1$
									+ connection.getName() + "]"; //$NON-NLS-1$
							ILaunchTarget target = targetManager
									.getLaunchTarget(TYPE_ID, imageName2);
							if (target != null) {
								continue;
							}
							target = targetManager.getLaunchTarget(TYPE_ID,
									imageName);
							if (target != null) {
								if (target.getAttribute(
										IContainerLaunchTarget.ATTR_CONNECTION_URI,
										"").equals(connection.getUri())) {
									continue;
								}
								imageName = imageName2;
							}
							target = targetManager.addLaunchTarget(TYPE_ID,
									imageName);
							ILaunchTargetWorkingCopy wc = target
									.getWorkingCopy();
							wc.setAttribute(ILaunchTarget.ATTR_OS,
									CONTAINER_LINUX);
							wc.setAttribute(ILaunchTarget.ATTR_ARCH,
									Platform.getOSArch());
							wc.setAttribute(
									IContainerLaunchTarget.ATTR_CONNECTION_URI,
									connection.getUri());
							wc.setAttribute(
									IContainerLaunchTarget.ATTR_IMAGE_ID,
									image.repoTags().get(0));

							wc.save();
						}
					}

					// reset the default target back again
					if (defaultTarget != null) {
						try {
							launchbarManager
									.setActiveLaunchTarget(defaultTarget);
						} catch (CoreException e) {
							DockerLaunchUIPlugin.log(e);
						}

					}

					// Re-evaluate config list in case a build config was marked
					// invalid and is now enabled
					manager.recheckConfigs();
				} else if (type == IDockerConnectionManagerListener.REMOVE_EVENT
						|| type == IDockerConnectionManagerListener.DISABLE_EVENT) {
					String connectionURI = connection.getUri();
					ILaunchTarget[] targets = targetManager
							.getLaunchTargetsOfType(TYPE_ID);
					for (ILaunchTarget target : targets) {
						String uri = target.getAttribute(
								IContainerLaunchTarget.ATTR_CONNECTION_URI, ""); //$NON-NLS-1$
						if (connectionURI.equals(uri)) {
							targetManager.removeLaunchTarget(target);
						}
					}
				}
				return Status.OK_STATUS;
			}
		};
		checkConfigs.setUser(true);
		checkConfigs.schedule();
	}

}
