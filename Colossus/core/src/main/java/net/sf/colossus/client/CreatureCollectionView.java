package net.sf.colossus.client;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import net.sf.colossus.server.Creature;
import net.sf.colossus.util.KDialog;
import net.sf.colossus.util.Log;


/** 
 *  Viewer for a collection, say the graveyard or the creature keeper
 *  @version $Id$
 *  @author Tom Fruchterman
 *  @author David Ripton
 */
class CreatureCollectionView extends KDialog implements WindowListener
{
    private Client client;
    private Point location;
    private Dimension size;
    private static final int fixedChitSize = 60;

    /** hash by creature name to the label that displays the bottom counts */
    Map countMap = new HashMap();

    /** hash by creature name to the label that displays the top count */
    Map topCountMap = new HashMap();

    /** hash by creature name to the Chit (so we can cross away the dead) */
    Map chitMap = new HashMap();
    private SaveWindow saveWindow;
    private final static Font countFont =
            new Font("Monospaced", Font.PLAIN, 12);
    private final static String baseString = "--/--/--";
    private final static JLabel baseLabel =
            new JLabel(baseString, SwingConstants.CENTER);
    private final static JLabel legendLabel =
            new JLabel(htmlizeOnly(
            htmlColorizeOnly("Values are: ", "black") +
            htmlColorizeOnly("Total", "blue") +
            htmlColorizeOnly("/", "black") +
            htmlColorizeOnly("In Stack", "black") +
            htmlColorizeOnly("/", "black") +
            htmlColorizeOnly("In Game", "green") +
            htmlColorizeOnly("/", "black") +
            htmlColorizeOnly("Dead", "red")));

    static
    {
        baseLabel.setFont(countFont);
    }

    CreatureCollectionView(JFrame frame, Client client)
    {
        super(frame, "Caretaker's Stacks", false);
        setFocusable(false);

        this.client = client;

        getContentPane().setLayout(new BorderLayout());

        JScrollPane scrollPane =
            new JScrollPane(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel panel = makeCreaturePanel(scrollPane);
        scrollPane.setViewportView(panel);
        getContentPane().add(scrollPane,
                             BorderLayout.CENTER);

        getContentPane().add(legendLabel, BorderLayout.SOUTH);

        addWindowListener(this);

        pack();

        saveWindow = new SaveWindow(client, "CreatureCollectionView");

        if (location == null)
        {
            location = saveWindow.loadLocation();
        }
        if (location == null)
        {
            upperRightCorner();
            location = getLocation();
        }
        else
        {
            setLocation(location);
        }

        if (size == null)
        {
            size = saveWindow.loadSize();
        }
        if (size == null)
        {
            size = getPreferredSize();
        }
        setSize(size);

        update();
        setVisible(true);
    }

    /** the count for an individual creature */
    class CreatureCount extends JPanel
    {
        private JLabel label;
        private JLabel topLabel;
        private Chit chit;

        CreatureCount(String name)
        {
            super(new BorderLayout());

            setBorder(BorderFactory.createLineBorder(Color.black));
            if (!(name.equals("Titan")))
            {
                chit = new Chit(fixedChitSize, name, this);
            }
            else
            {
                chit = new Chit(fixedChitSize, "Titan-0-Black", this);
            }
            chitMap.put(name, chit);
            label = new JLabel(baseString, SwingConstants.CENTER);
            topLabel =
                    new JLabel(htmlizeOnly(
                    htmlColorizeOnly(
                    Integer.toString(
                    client.getCreatureMaxCount(name)), "blue")),
                    SwingConstants.CENTER);
            label.setFont(countFont);
            topLabel.setFont(countFont);
            countMap.put(name, label);
            topCountMap.put(name, topLabel);

            // jikes whines because add is defined in both JPanel and JDialog.
            this.add(topLabel, BorderLayout.NORTH);
            this.add(chit, BorderLayout.CENTER);
            this.add(label, BorderLayout.SOUTH);
        }

        public Dimension getPreferredSize()
        {
            Dimension labelDim = label.getPreferredSize();
            Rectangle chitDim = chit.getBounds();
            int minX = chitDim.width + 1;
            int minY = chitDim.height + (2 * (int)labelDim.getHeight()) + 1;
            if (minX < (int)labelDim.getWidth() + 2)
            {
                minX = (int)labelDim.getWidth() + 2;
            }
            return new Dimension(minX, minY);
        }
    }

    private JPanel makeCreaturePanel(JScrollPane scrollPane)
    {
        List creatures = Creature.getCreatures();
        JPanel creaturePanel = new JPanel();
        creaturePanel.setLayout(new CCVFlowLayout(scrollPane, creaturePanel, FlowLayout.LEFT, 2, 2));
        Iterator it = creatures.iterator();
        while (it.hasNext())
        {
            Creature creature = (Creature)it.next();
            creaturePanel.add(new CreatureCount(creature.getName()));
        }

        return creaturePanel;
    }

    public void update()
    {
        try
        {
            Iterator it = countMap.entrySet().iterator();
            while (it.hasNext())
            {
                Map.Entry entry = (Map.Entry)it.next();
                String name = (String)entry.getKey();
                JLabel label = (JLabel)entry.getValue();
                int count = client.getCreatureCount(name);
                int maxcount = client.getCreatureMaxCount(name);
                int deadCount = client.getCreatureDeadCount(name);
                int inGameCount = maxcount - (deadCount + count);

                // safety check
                if ((inGameCount < 0) || (inGameCount > maxcount))
                {
                    Log.error("Something went wrong:" +
                            " discrepancy between total (" + maxcount +
                            "), remaining (" + count +
                            ") and dead (" + deadCount +
                            ") count for creature " + name);
                    return;
                }

                boolean immortal =
                        Creature.getCreatureByName(name).isImmortal();
                String color;
                if (count == 0)
                {
                    color = "yellow";
                    if (!immortal)
                    {
                        Chit chit = (Chit)chitMap.get(name);
                        chit.setDead(true);
                    }
                }
                else
                {
                    Chit chit = (Chit)chitMap.get(name);
                    chit.setDead(false);
                    if (count == maxcount)
                    {
                        color = "green";
                    }
                    else
                    {
                        color = "black";
                    }
                }
                String htmlCount =
                        htmlColorizeOnly((count < 10 ? "0" : "") +
                        Integer.toString(count), color);
                String htmlDeadCount =
                        htmlColorizeOnly(
                        immortal && deadCount == 0 ?
                        "--" :
                        (deadCount < 10 ? "0" : "") +
                        Integer.toString(deadCount), "red");
                String htmlInGameCount =
                        htmlColorizeOnly((inGameCount < 10 ? "0" : "") +
                        Integer.toString(inGameCount),
                        "green");
                String htmlSlash = htmlColorizeOnly("/", "black");
                label.setText(htmlizeOnly(htmlCount + htmlSlash +
                        htmlInGameCount + htmlSlash +
                        htmlDeadCount));
                JLabel topLabel = (JLabel)topCountMap.get(name);
                topLabel.setText(htmlizeOnly(htmlColorizeOnly(
                        Integer.toString(maxcount), "blue")));
            }

            repaint();
        }
        catch (NullPointerException ex)
        {
            // If we try updating this dialog before creatures are loaded,
            // just ignore the exception and let it retry later.
        }
    }

    private static String htmlColorizeOnly(String input, String color)
    {
        StringBuffer sb = new StringBuffer("<font color=");
        sb.append(color);
        sb.append(">");
        sb.append(input);
        sb.append("</font>");
        return sb.toString();
    }

    private static String htmlizeOnly(String input)
    {
        StringBuffer sb = new StringBuffer("<html>");
        sb.append(input);
        sb.append("</html>");
        return sb.toString();
    }

    public void dispose()
    {
        super.dispose();
        location = getLocation();
        size = getSize();
        saveWindow.saveLocation(location);
        saveWindow.saveSize(size);
    }

    public void windowClosing(WindowEvent e)
    {
        dispose();
    }

    public Dimension getMinimumSize()
    {
        java.util.List creatures = Creature.getCreatures();
        // default : 5 creatures wide 

        int minSingleX = fixedChitSize + 8;
        if (minSingleX < (int)baseLabel.getPreferredSize().getWidth() + 8)
        {
            minSingleX = (int)baseLabel.getPreferredSize().getWidth() + 8;
        }

        int minX = minSingleX * 5;
        int minY = ((fixedChitSize + 8 +
                (2 * (int)baseLabel.getPreferredSize().getHeight())) *
                ((creatures.size() + 4) / 5)) + fixedChitSize;

        return new Dimension(minX, minY);
    }

    public Dimension getPreferredSize()
    {
        return getMinimumSize();
    }
}

class CCVFlowLayout extends FlowLayout implements ComponentListener {
    private JScrollPane parentScrollPane;
    private JComponent parentComponent;
    
    public CCVFlowLayout(JScrollPane sp, JComponent me, int al, int sx, int sy)
    {
        super(al, sx, sy);
        parentScrollPane = sp;
        parentComponent = me;
        parentScrollPane.addComponentListener(this);
    }
    
    protected Dimension getOurSize()
    {
        javax.swing.JViewport viewport = parentScrollPane.getViewport();
        Dimension extentSize = viewport.getExtentSize();
        java.awt.Insets insets = parentComponent.getInsets();
        int maxLegalWidth = extentSize.width - (insets.left + insets.right);
        int x = 0, y = insets.top + getVgap();
        int rowHeight = 0, maxWidth = 0;
        Component[] allComponents = parentComponent.getComponents();
        for (int i = 0; i < allComponents.length; i++)
        {
            if (allComponents[i].isVisible())
            {
                Dimension d = allComponents[i].getPreferredSize();
                if ((x == 0) || ((x + getHgap() + d.width) <= maxLegalWidth))
                {
                    if (x > 0) {
                        x += getHgap();
                    }
                    x += d.width;
                    rowHeight = (rowHeight < d.height ? d.height : rowHeight);
                } else
                {
                    if (x > maxWidth) {
                        maxWidth = x;
                    }
                    x = d.width;
                    y += getVgap() + rowHeight;
                    rowHeight = d.height;
                }
            }
        }
        if (x > (maxWidth - getHgap())) {
            maxWidth = x + getHgap();
        }
        y += getVgap() + rowHeight + insets.bottom;
        return new Dimension(maxWidth, y);
    }
    
    public void componentResized(ComponentEvent e) {
        javax.swing.JViewport viewport = parentScrollPane.getViewport();
        Dimension viewSize = viewport.getViewSize();
        Dimension extentSize = viewport.getExtentSize();
        Dimension ourSize = getOurSize();
        if ((viewSize.width != extentSize.width) ||
            (viewSize.height != ourSize.height)) {
            int x = (ourSize.width > extentSize.width ?  ourSize.width : extentSize.width);
            int y = (ourSize.height > extentSize.height ?  ourSize.height : extentSize.height);
            parentComponent.setPreferredSize(new Dimension(x,y));
            viewport.setViewSize(new Dimension(x,y));
            parentComponent.doLayout();
            parentScrollPane.doLayout();
        }
    }
    
    public void componentMoved(ComponentEvent e) {
    }
    
    public void componentShown(ComponentEvent e) {
    }
    
    public void componentHidden(ComponentEvent e) {
    } 
}
