package org.bndtools.core.utils.jface;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import bndtools.Plugin;

public class StatusLabelProvider extends StyledCellLabelProvider {

    private Image imgError = null;
    private Image imgWarning = null;
    private Image imgInfo = null;

    @Override
    public void update(ViewerCell cell) {
        IStatus status = (IStatus) cell.getElement();

        switch (status.getSeverity()) {
        case IStatus.ERROR:
            if (imgError == null) imgError = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/error_obj.gif").createImage();
            cell.setImage(imgError);
            break;
        case IStatus.WARNING:
            if (imgWarning == null) imgWarning = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/warning.gif").createImage();
            cell.setImage(imgWarning);
            break;
        case IStatus.INFO:
            if (imgInfo == null) imgInfo = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/information.gif").createImage();
            cell.setImage(imgInfo);
            break;
        }

        StyledString label = new StyledString(status.getMessage());

        cell.setText(label.getString());
        cell.setStyleRanges(label.getStyleRanges());
    }

    @Override
    public void dispose() {
        super.dispose();
        if (imgError != null)
            imgError.dispose();
        if (imgWarning!= null)
            imgWarning.dispose();
        if (imgInfo != null)
            imgInfo.dispose();

    }
}