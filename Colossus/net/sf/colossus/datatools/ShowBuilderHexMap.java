package net.sf.colossus.datatools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

/**
 * Class ShowBuilderHexMap displays a battle map.
 * @version $Id$
 * @author David Ripton
 * @author Romain Dolbeau
 */

final class ShowBuilderHexMap extends BuilderHexMap implements WindowListener,
    MouseListener
{
    private JDialog dialog;
    private JPopupMenu popupMenuTerrain;
    private JPopupMenu popupMenuBorder;
    private Point lastPoint;
    private Component lastComponent;
    private int lastSide;

    private static final String[] terrains = 
    {
        "Plain",
        "Tower",
        "Bramble",
        "Sand",
        "Tree",
        "Bog",
        "Volcano",
        "Drift",
        "Lake"
    };

    private static final char[] c_terrains = 
    {
        'p', 'w', 'r', 's', 't', 'o', 'v', 'd', 'l'
    };

    class TerrainAction extends AbstractAction
    {
        char c;
        TerrainAction(String t, char c)
        {
            super(t);
            this.c = c;
        }
        public void actionPerformed(ActionEvent e) {
            GUIBuilderHex h = getHexContainingPoint(lastPoint);
            h.setTerrain(c);
            h.repaint();
        }
    }

    class ElevationAction extends AbstractAction
    {
        int el;
        ElevationAction(String t, int el)
        {
            super(t);
            this.el = el;
        }
        public void actionPerformed(ActionEvent e) {
            GUIBuilderHex h = getHexContainingPoint(lastPoint);
            h.setElevation(el);
            h.repaint();
        }
    }

    private static final String[] hexsides =
    {
        "Nothing",
        "Dune",
        "Cliff",
        "Slope",
        "Wall"
    };

    private static final char[] c_hexsides = 
    {
        ' ', 'd', 'c', 's', 'w'
    };

    class HexsideAction extends AbstractAction        
    {
        char c;
        HexsideAction(String t, char c)
        {
            super(t);
            this.c = c;
        }
        public void actionPerformed(ActionEvent e) {
            GUIBuilderHex h = getHexContainingPoint(lastPoint);
            h.setHexside(lastSide, c);
            h.repaint();
            ((GUIBuilderHex)h.getNeighbor(lastSide)).repaint();
        }
    }

    private AbstractAction saveBattlelandAction;
    private AbstractAction saveBattlelandAsAction;
    private AbstractAction quitAction;

    JMenuBar menuBar;

    ShowBuilderHexMap(String f)
    {
        super(f);

        menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);
        JMenuItem mi;

        saveBattlelandAction = new AbstractAction("Save Battleland") {
                public void actionPerformed(ActionEvent e) {
                    System.out.print(dumpAsString());
                    if (filename != null)
                    {
                        
                    }
                }
            };

        saveBattlelandAsAction = new AbstractAction("Save Battleland As...") {
                public void actionPerformed(ActionEvent e) {
                    System.out.print(dumpAsString());
                }
            };

        quitAction = new AbstractAction("Quit") {
                public void actionPerformed(ActionEvent e) {
                    System.exit(0);
                }
            };

        mi = fileMenu.add(saveBattlelandAction);
        mi.setMnemonic(KeyEvent.VK_S);
        mi = fileMenu.add(saveBattlelandAsAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi = fileMenu.add(quitAction);
        mi.setMnemonic(KeyEvent.VK_Q);

        dialog = new JDialog();

        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(menuBar, BorderLayout.NORTH);

        addMouseListener(this);
        dialog.addWindowListener(this);

        contentPane.add(this, BorderLayout.CENTER);
        dialog.pack();
        dialog.setVisible(true);

        popupMenuTerrain = new JPopupMenu("Choose Terrain");
        contentPane.add(popupMenuTerrain);

        for (int i = 0 ; i < terrains.length ; i++)
        {
            mi = popupMenuTerrain.add(new TerrainAction(terrains[i],
                                                        c_terrains[i]));
        }
        popupMenuTerrain.addSeparator();
        for (int i = 0 ; i < 3 ; i++)
        {
            mi = popupMenuTerrain.add(new ElevationAction(
                                      "Set Elevation to: " + i,
                                      i));
        }

        popupMenuBorder = new JPopupMenu("Choose Border");
        contentPane.add(popupMenuBorder);

        for (int i = 0 ; i < hexsides.length ; i++)
        {
            mi = popupMenuBorder.add(new HexsideAction(hexsides[i],
                                                       c_hexsides[i]));
        }
        
        lastPoint = new Point(0,0);
        lastComponent = contentPane;
        lastSide = 0;
    }

    public void mousePressed(MouseEvent e)
    {
        lastPoint = e.getPoint();
        lastComponent = e.getComponent();
        GUIBuilderHex h = getHexContainingPoint(lastPoint);
        if (h != null)
        {
            if (h.innerContains(lastPoint))
            { // change content
                popupMenuTerrain.show(e.getComponent(), lastPoint.x, lastPoint.y);
            }
            else
            { // change border
                Point c = h.findCenter();
                if (c.y >= lastPoint.y)
                { // uppper half
                    if (lastPoint.x >=((c.x) + (h.getBounds().x + h.getBounds().width))/2)
                        lastSide = 1;
                    else if (lastPoint.x <= ((c.x) + (h.getBounds().x))/2)
                        lastSide = 5;
                    else
                        lastSide = 0;
                }
                else
                { // lower half
                    if (lastPoint.x >=((c.x) + (h.getBounds().x + h.getBounds().width))/2)
                        lastSide = 2;
                    else if (lastPoint.x <= ((c.x) + (h.getBounds().x))/2)
                        lastSide = 4;
                    else
                        lastSide = 3;
                }

                popupMenuBorder.show(e.getComponent(), lastPoint.x, lastPoint.y); 
            }
        }
    }

    public void windowClosing(WindowEvent e)
    {
        dialog.dispose();
    }
}
