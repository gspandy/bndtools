/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.bindex.BundleIndexer;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.util.tracker.ServiceTracker;

import aQute.bnd.build.Workspace;
import aQute.lib.osgi.Processor;
import aQute.libg.version.Version;
import bndtools.api.ILogger;
import bndtools.services.WorkspaceURLStreamHandlerService;

public class Plugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "bndtools.core";
	public static final String BND_EDITOR_ID = PLUGIN_ID + ".bndEditor";

	public static final Version DEFAULT_VERSION = new Version(0, 0, 0);

	public static final String BNDTOOLS_NATURE = "bndtools.core.bndnature";

	private static volatile Plugin plugin;

	private BundleContext bundleContext;
	private Activator bndActivator;
	private final List<IStartupParticipant> startupParticipants = new LinkedList<IStartupParticipant>();

    private volatile RepositoryModel repositoryModel;
    private volatile ServiceTracker workspaceTracker;
    private volatile ServiceRegistration urlHandlerReg;
    private volatile IndexerTracker indexerTracker;
    private volatile ResourceIndexerTracker resourceIndexerTracker;
    
    private volatile ScheduledExecutorService scheduler;

    private volatile Central central;
    
    private final ILogger logger = new ILogger() {
        public void logError(String message, Throwable exception) {
            getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, 0, message, exception));
        }
        public void logWarning(String message, Throwable exception) {
            getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, 0, message, exception));
        }
        public void logInfo(String message, Throwable exception) {
            getLog().log(new Status(IStatus.INFO, PLUGIN_ID, 0, message, exception));
        }
    };

    @Override
    public void start(BundleContext context) throws Exception {
        registerWorkspaceURLHandler(context);
        super.start(context);
        plugin = this;
        this.bundleContext = context;
        
        scheduler = Executors.newScheduledThreadPool(1);

        bndActivator = new Activator();
        bndActivator.start(context);

        indexerTracker = new IndexerTracker(context);
        indexerTracker.open();
        
        resourceIndexerTracker = new ResourceIndexerTracker(context, 1000);
        resourceIndexerTracker.open();

        registerWorkspaceServiceFactory(context);

        central = new Central();

        repositoryModel = new RepositoryModel();

        runStartupParticipants();
    }

    private void registerWorkspaceServiceFactory(BundleContext context) {
        Properties props = new Properties();
        props.put("name", "bndtools");

        context.registerService(Workspace.class.getName(), new WorkspaceServiceFactory(), props);
    }

	private void registerWorkspaceURLHandler(BundleContext context) {
	    workspaceTracker = new ServiceTracker(context, IWorkspace.class.getName(), null);
	    workspaceTracker.open();

	    Properties props = new Properties();
	    props.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { WorkspaceURLStreamHandlerService.PROTOCOL });
	    urlHandlerReg = context.registerService(URLStreamHandlerService.class.getName(), new WorkspaceURLStreamHandlerService(workspaceTracker), props);
    }

    private void runStartupParticipants() {
        IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(PLUGIN_ID, "bndtoolsStartupParticipant");

        for (IConfigurationElement element : elements) {
            try {
                Object obj = element.createExecutableExtension("class");
                if (obj instanceof Runnable) {
                    Runnable participant = (Runnable) obj;
                    participant.run();
                } else if (obj instanceof IStartupParticipant) {
                    IStartupParticipant isp = (IStartupParticipant) obj;
                    startupParticipants.add(isp);
                    isp.start();
                }
            } catch (CoreException e) {
                logError("Error executing startup participant", e);
            }
        }
    }

    private void stopStartupParticipants() {
        for (IStartupParticipant isp : startupParticipants) {
            try {
                isp.stop();
            } catch (Exception e) {
                logError("Error stopping startup participant", e);
            }
        }
    }

	private void unregisterWorkspaceURLHandler() {
	    urlHandlerReg.unregister();
	    workspaceTracker.close();
	}

    @Override
    public void stop(BundleContext context) throws Exception {
        stopStartupParticipants();

        bndActivator.stop(context);
        central.close();
        resourceIndexerTracker.close();
        indexerTracker.close();
        this.bundleContext = null;
        plugin = null;
        super.stop(context);
        unregisterWorkspaceURLHandler();
        scheduler.shutdown();
    }

	public static Plugin getDefault() {
		return plugin;
	}

	public BundleContext getBundleContext() {
		return bundleContext;
	}

	public RepositoryModel getRepositoryModel() {
        return repositoryModel;
    }

    public void report(boolean warnings, boolean acknowledge , Processor reporter, final String title, final String extra ) {
        if (reporter.getErrors().size() > 0
                || (warnings && reporter.getWarnings().size() > 0)) {
            final StringBuffer sb = new StringBuffer();
            sb.append("\n");
            if (reporter.getErrors().size() > 0) {
                sb.append("[Errors]\n");
                for (String msg : reporter.getErrors()) {
                    sb.append(msg);
                    sb.append("\n");
                }
            }
            sb.append("\n");
            if (reporter.getWarnings().size() > 0) {
                sb.append("[Warnings]\n");
                for (String msg : reporter.getWarnings()) {
                    sb.append(msg);
                    sb.append("\n");
                }
            }
            final Status s = new Status(Status.ERROR, PLUGIN_ID, 0, sb.toString(), null);
            reporter.clear();

            async(new Runnable() {
                public void run() {
                    ErrorDialog.openError(null, title, title + "\n" + extra, s);
                }
            });

        } else {
            message(title+ " : ok");
        }
    }
    public void message(final String msg) {
        async(new Runnable() {
            public void run() {
                MessageDialog.openInformation(null, "Bnd", msg);
            }
        });
    }
    void async(Runnable run) {
        if (Display.getCurrent() == null) {
            Display.getDefault().asyncExec(run);
        } else
            run.run();
    }
    public void error(List<String> errors) {
        final StringBuffer sb = new StringBuffer();
        for (String msg : errors) {
            sb.append(msg);
            sb.append("\n");
        }

        async(new Runnable() {
            public void run() {
                Status s = new Status(Status.ERROR, PLUGIN_ID, 0, "", null);
                ErrorDialog.openError(null, "Errors during bundle generation",
                        sb.toString(), s);
            }
        });
    }
    static final AtomicBoolean busy = new AtomicBoolean(false);
    public void error(final String msg, final Throwable t) {
        Status s = new Status(Status.ERROR, PLUGIN_ID, 0, msg, t);
        getLog().log(s);
        async(new Runnable() {
            public void run() {
            	if(!busy.compareAndSet(false, true)) {
		            Status s = new Status(Status.ERROR, PLUGIN_ID, 0, "", null);
		            ErrorDialog.openError(null, "Errors during bundle generation",
		                    msg + " " + t.getMessage(), s);

		            busy.set(false);
            	}
            }
        });
    }
    public void warning(List<String> errors) {
        final StringBuffer sb = new StringBuffer();
        for (String msg : errors) {
            sb.append(msg);
            sb.append("\n");
        }
        async(new Runnable() {
            public void run() {
                Status s = new Status(Status.WARNING, PLUGIN_ID, 0, "", null);
                ErrorDialog.openError(null,
                        "Warnings during bundle generation", sb.toString(), s);
            }
        });
    }
    
    public ILogger getLogger() {
        return logger;
    }
    
    /**
     * @deprecated Use {@link #getLogger() instead}
     * @param status
     */
    @Deprecated
    public static void log(IStatus status) {
        Plugin instance = plugin;
        if (instance != null) {
            instance.getLog().log(status);
        } else {
            System.err.println(String.format("Unable to print to log for %s: bundle has been stopped.", Plugin.PLUGIN_ID));
        }
    }

    /**
     * @deprecated Use {@link #getLogger() instead}
     * @param status
     */
    @Deprecated
    public static void logError(String message, Throwable exception) {
        log(new Status(IStatus.ERROR, PLUGIN_ID, 0, message, exception));
    }
    
    public Central getCentral() {
        return central;
    }

	public static ImageDescriptor imageDescriptorFromPlugin(String imageFilePath) {
		return AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, imageFilePath);
	}

    public BundleIndexer getBundleIndexer() {
        return indexerTracker;
    }
    
    public ResourceIndexer getResourceIndexer() {
        return resourceIndexerTracker;
    }
    
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

}
