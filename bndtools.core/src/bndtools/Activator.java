package bndtools;

import java.io.File;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import aQute.bnd.build.ReflectAction;
import aQute.bnd.service.action.Action;
import aQute.lib.osgi.Processor;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "aQute.bmaker";

    // The shared instance
    static volatile Activator       instance;
    BundleContext                   context;
    RepositoryListenerPluginTracker repoListenerTracker;

    /**
     * The constructor
     */
    public Activator() {
        instance = this;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        this.context = context;

        Hashtable<String,Object> p = new Hashtable<String, Object>();
        // p.put(Action.ACTION_MENU, new String[] {"a:b", "a:c", "a:d", "a:d:e"});
        context.registerService(Action.class.getName(), new ReflectAction(""), p);

        repoListenerTracker = new RepositoryListenerPluginTracker(context);
        repoListenerTracker.open();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        repoListenerTracker.close();
        instance = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static Activator getDefault() {
        return instance;
    }

    /**
     * Returns an image descriptor for the image file at the given plug-in
     * relative path
     *
     * @param path
     *            the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

    static volatile boolean busy;
    public void error(final String msg, final Throwable t) {
        Status s = new Status(Status.ERROR, PLUGIN_ID, 0, msg, t);
        getLog().log(s);
        async(new Runnable() {
            public void run() {
                synchronized(this) {
                    if  ( busy )
                        return;
                    busy = true;
                }
                Status s = new Status(Status.ERROR, PLUGIN_ID, 0, "", null);
                ErrorDialog.openError(null, "Errors during bundle generation",
                        msg + " " + t.getMessage(), s);

                busy = false;
            }
        });
    }

    public void info(String msg) {
        Status s = new Status(Status.INFO, PLUGIN_ID, 0, msg, null);
        getLog().log(s);
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

    public void message(final String msg) {
        async(new Runnable() {
            public void run() {
                MessageDialog.openInformation(null, "Bnd", msg);
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

    void async(Runnable run) {
        if (Display.getCurrent() == null) {
            Display.getDefault().asyncExec(run);
        } else
            run.run();
    }

    public boolean getReportDone() {
        return true;
        // return
        // getPreferenceStore().getBoolean(PreferenceConstants.P_REPORT_DONE);
    }

    public File getCopy() {
        return null;

        // String path =
        // getPreferenceStore().getString(PreferenceConstants.P_COPY);
        // if ( path == null )
        // return null;

        // File file = new File(path);
        // if ( !file.exists() || file.isFile() )
        // return null;

        // return file;
    }

    public boolean isPedantic() {
        return false;
        // IPreferenceStore store = getPreferenceStore();
        // return store.getBoolean(PreferenceConstants.P_PEDANTIC);
    }

    public BundleContext getBundleContext() {
        return context;
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

}
