package net.sf.colossus.gui;



import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import net.sf.colossus.client.LegionClientSide;
import net.sf.colossus.common.IOptions;
import net.sf.colossus.common.Options;
import net.sf.colossus.guiutil.KDialog;
import net.sf.colossus.guiutil.SaveWindow;
import net.sf.colossus.variant.MasterHex;


@SuppressWarnings("serial")
class AutoInspector extends KDialog
{
    private final IOptions options;

    private final SaveWindow saveWindow;

    private final JScrollPane scrollPane;

    private final JFrame parentFrame;

    private final int viewMode;

    private boolean dubiousAsBlanks;

    public AutoInspector(JFrame frame, IOptions options, int viewMode,
        boolean dubiousAsBlanks)
    {
        super(frame, "Inspector", false);
        assertEDT(); // maybe better in KDialog
        this.parentFrame = frame;
        this.options = options;
        this.viewMode = viewMode;
        this.dubiousAsBlanks = dubiousAsBlanks;

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                AutoInspector.this.options.setOption(
                    Options.showAutoInspector, false);
            }
        });

        saveWindow = new SaveWindow(options, "AutoInspector");
        Point location = getUpperRightCorner(550);
        saveWindow.restore(this, location);

        scrollPane = new JScrollPane();

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(scrollPane, BorderLayout.CENTER);

        setVisible(true);
    }

    @Override
    public void dispose()
    {
        saveWindow.saveSize(getSize());
        saveWindow.saveLocation(getLocation());
        super.dispose();
    }

    @Override
    public Dimension getPreferredSize()
    {
        // this is just a first go to have some size at all
        // TODO do a decent estimate of a legion's size
        return new Dimension(550, 110);
    }

    public void showLegion(LegionClientSide legion)
    {
        assertEDT();
        scrollPane.getViewport().removeAll();
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        LegionInfoPanel liPanel = new LegionInfoPanel(legion, 4 * Scale.get(),
            5, 2, false, viewMode, dubiousAsBlanks, false);
        panel.add(liPanel);
        String valueText = liPanel.getValueText();
        String ownerText = legion.isMyLegion() ? "" : " ["
            + legion.getPlayer().getName() + "]";
        setTitle("Inspector: Legion " + legion.getMarkerId() + valueText
            + ownerText);
        liPanel = null;

        scrollPane.getViewport().add(panel);
        repaint();
    }

    public void showHexRecruitTree(GUIMasterHex hex)
    {
        assertEDT();
        setTitle("Inspector");
        scrollPane.getViewport().removeAll();
        MasterHex hexModel = hex.getHexModel();
        scrollPane.getViewport().add(
            new HexRecruitTreePanel(BoxLayout.X_AXIS, hexModel.getTerrain(),
                hexModel, this.parentFrame, false));
        repaint();
    }

    // public so that client can update it when option is changed.
    public void setDubiousAsBlanks(boolean newVal)
    {
        this.dubiousAsBlanks = newVal;
        repaint();
    }
}
