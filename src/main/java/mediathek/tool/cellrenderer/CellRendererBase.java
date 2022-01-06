package mediathek.tool.cellrenderer;

import mediathek.tool.sender_icon_cache.MVSenderIconCache;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Base class for all cell renderer.
 */
public class CellRendererBase extends DefaultTableCellRenderer {
    /**
     * Draws the sender icon in the sender model column.
     *
     * @param sender Name of the sender.
     */
    protected void setSenderIcon(@NotNull String sender, boolean useSmallIcon, @NotNull Dimension targetDim) {
        setHorizontalAlignment(SwingConstants.CENTER);
        var origIcon = MVSenderIconCache.get(sender, useSmallIcon);
        origIcon.ifPresent(icon -> {
            setHorizontalAlignment(SwingConstants.CENTER);
            setText("");
            Dimension iconDim = new Dimension(icon.getIconWidth(), icon.getIconHeight());
            var scaleDim = getScaledDimension(iconDim, targetDim);
            Image newimg = icon.getImage().getScaledInstance(scaleDim.width, scaleDim.height, Image.SCALE_SMOOTH);
            setIcon(new ImageIcon(newimg));
        });
    }

    protected Dimension getSenderCellDimension(@NotNull JTable table, int row, int columnModelIndex) {
        Dimension targetDim = new Dimension();
        targetDim.height = table.getRowHeight(row);
        targetDim.width = table.getColumnModel().getColumn(columnModelIndex).getWidth();
        targetDim.height -= 4;
        targetDim.width -= 4;
        return targetDim;
    }

    protected Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {

        double widthRatio = boundary.getWidth() / imageSize.getWidth();
        double heightRatio = boundary.getHeight() / imageSize.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);

        return new Dimension((int) (imageSize.width  * ratio),
                (int) (imageSize.height * ratio));
    }


}
