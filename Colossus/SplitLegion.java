import java.awt.*;
import java.awt.event.*;

/**
 * Class SplitLegion allows a player to split a Legion into two Legions.
 * @version $Id$
 * author David Ripton
 */

class SplitLegion extends Dialog implements MouseListener, ActionListener
{
    MediaTracker tracker;
    boolean imagesLoaded;
    Legion oldLegion;
    Legion newLegion;
    Chit [] oldChits;
    Chit [] newChits;
    Chit oldMarker;
    Player player;
    final int scale = 60;
    Frame parentFrame;
    Button button1;
    Button button2;
    boolean laidOut = false;

    SplitLegion(Frame parentFrame, Legion oldLegion, Player player)
    {
        super(parentFrame, player.name + ": Split Legion " + 
            oldLegion.markerId, true);

        setResizable(false);
        setLayout(null);

        this.oldLegion = oldLegion;
        this.player = player;
        this.parentFrame = parentFrame;

        imagesLoaded = false;

        PickMarker pickmarker = new PickMarker(parentFrame, player);

        newLegion = new Legion(scale / 5, 2 * scale, scale,
            player.markerSelected, oldLegion.markerId, this, 0, null, 
            null, null, null, null, null, null, null);

        setSize(getPreferredSize());

        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(d.width / 2 - getSize().width / 2, d.height / 2
            - getSize().height / 2));

        // If there were no markers left to pick, exit.
        if (player.markerSelected == null)
        {
            dispose();
        }
        else
        {
            addMouseListener(this);

            oldChits = new Chit[oldLegion.height];
            for (int i = 0; i < oldLegion.height; i++)
            {
                oldChits[i] = new Chit((i + 1) * scale + (scale / 5), scale / 2,
                    scale, "images/" + oldLegion.creatures[i].name + ".gif",
                    this);
            }
            newChits = new Chit[oldLegion.height];
            
            oldMarker = new Chit(scale / 5, scale / 2, scale, 
                "images/" + oldLegion.markerId + ".gif", this);

            tracker = new MediaTracker(this);

            for (int i = 0; i < oldLegion.height; i++)
            {
                tracker.addImage(oldChits[i].image, 0);
            }
            tracker.addImage(oldMarker.image, 0);
            tracker.addImage(newLegion.chit.image, 0);

            try
            {
                tracker.waitForAll();
            }
            catch (InterruptedException e)
            {
                new MessageBox(parentFrame, "waitForAll was interrupted");
            }

            button1 = new Button("Done");
            button2 = new Button("Cancel");
            add(button1);
            add(button2);
            button1.addActionListener(this);
            button2.addActionListener(this);

            pack();

            imagesLoaded = true;
            setVisible(true);
            repaint();
        }
    }


    public void paint(Graphics g)
    {
        if (!imagesLoaded)
        {
            return;
        }

        //Rectangle rectClip = g.getClipBounds();

        //if (rectClip.intersects(oldMarker.getBounds()))
        {
            oldMarker.paint(g);
        }
        //if (rectClip.intersects(newLegion.chit.getBounds()))
        {
            newLegion.chit.paint(g);
        }

        for (int i = oldLegion.height - 1; i >= 0; i--)
        {
            //if (rectClip.intersects(oldChits[i].getBounds()))
            {
                oldChits[i].paint(g);
            }
        }
        for (int i = newLegion.height - 1; i >= 0; i--)
        {
            //if (rectClip.intersects(newChits[i].getBounds()))
            {
                newChits[i].paint(g);
            }
        }

        if (!laidOut)
        {
            Insets insets = getInsets(); 
            Dimension d = getSize();
            button1.setBounds(insets.left + d.width / 8, 7 * d.height / 8 - 
                insets.bottom, d.width / 8, d.height / 8);
            button2.setBounds(3 * d.width / 4 - insets.right, 
                7 * d.height / 8 - insets.bottom, d.width / 8, d.height / 8);
        }

    }


    public void mouseClicked(MouseEvent e)
    {
        Point point = e.getPoint();
        for (int i = 0; i < oldLegion.height; i++)
        {
            if (oldChits[i].select(point))
            {
                // Got a hit.
                // Move this Creature over to the other Legion and adjust 
                // appropriate chit screen coordinates.
                newLegion.height++;
                newLegion.creatures[newLegion.height - 1] = 
                    oldLegion.creatures[i];
                newChits[newLegion.height - 1] = oldChits[i];
                newChits[newLegion.height - 1].setLocationAbs(new 
                    Point(newLegion.height * scale + scale / 5, 2 * scale));

                for (int j = i; j < oldLegion.height - 1; j++)
                {
                    oldLegion.creatures[j] = oldLegion.creatures[j + 1];
                    oldChits[j] = oldChits[j + 1];
                    oldChits[j].setLocationAbs(new Point((j + 1) * scale + 
                        scale / 5, scale / 2));
                }
                oldLegion.creatures[oldLegion.height - 1] = null;
                oldChits[oldLegion.height - 1] = null;
                oldLegion.height--;

                repaint();
                return;
            }
        }
        for (int i = 0; i < newLegion.height; i++)
        {
            if (newChits[i].select(point))
            {
                // Got a hit.
                // Move this Creature over to the other Legion and adjust 
                // appropriate chit screen coordinates.
                oldLegion.height++;
                oldLegion.creatures[oldLegion.height - 1] = 
                    newLegion.creatures[i];
                oldChits[oldLegion.height - 1] = newChits[i];
                oldChits[oldLegion.height - 1].setLocationAbs(new 
                    Point(oldLegion.height * scale + scale / 5, scale / 2));

                for (int j = i; j < newLegion.height - 1; j++)
                {
                    newLegion.creatures[j] = newLegion.creatures[j + 1];
                    newChits[j] = newChits[j + 1];
                    newChits[j].setLocationAbs(new Point((j + 1) * scale + 
                        scale / 5, 2 * scale));
                }
                newLegion.creatures[newLegion.height - 1] = null;
                newChits[newLegion.height - 1] = null;
                newLegion.height--;

                repaint();
                return;
            }
        }
    }


    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
    }

    public void mouseReleased(MouseEvent e)
    {
    }


    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand() == "Done")
        {
            // Check to make sure that each Legion is legal.
            // Each legion must have 2 <= height <= 7.
            // Also, if this was an initial split, each Legion
            // must have height 4 and one lord.
            if (oldLegion.height < 2 || newLegion.height < 2)
            {
                new MessageBox(parentFrame, "Legion too short.");
                return;
            }
            if (oldLegion.height + newLegion.height == 8)
            {
                if (oldLegion.height != newLegion.height)
                {
                    new MessageBox(parentFrame, "Initial split not 4-4.");
                    return;
                }
                else
                {
                    int lordCounter = 0;
                    for (int i = 0; i < 4; i++)
                    {
                        if (oldLegion.creatures[i].lord)
                        {
                            lordCounter++;
                        }
                    }
                    if (lordCounter != 1)
                    {
                        new MessageBox(parentFrame, 
                            "Each stack must have one lord.");
                        return;
                    }
                }
            }

            // The split is legal.
            // Set the new chit next to the old chit on the masterboard.
            newLegion.chit.setLocationAbs(oldLegion.chit.center());
            // Resize it.
            newLegion.chit.rescale(oldLegion.chit.getBounds().width);

            // Add the new legion to the player.
            player.numLegions++;
            player.legions[player.numLegions - 1] = newLegion;

            // Exit.
            dispose();
        }

        else if (e.getActionCommand() == "Cancel")
        {
            // Original legion must have had height < 8 for this
            // to be allowed.
            if (oldLegion.height  + newLegion.height>= 8)
            {
                new MessageBox(parentFrame, "Must split.");
            }

            // Put the stack marker back, reset the old legion, then exit.
            else
            {
                player.numMarkersAvailable++;
                player.markersAvailable[player.numMarkersAvailable - 1] =
                    new String(player.markerSelected);
                player.markerSelected = null;

                for (int i = 0; i < newLegion.height; i++)
                {
                    oldLegion.height++;
                    oldLegion.creatures[oldLegion.height - 1] = 
                        newLegion.creatures[i];
                }

                dispose();
            }
        }
    }


    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }

    public Dimension getPreferredSize()
    {
        return new Dimension((21 * scale / 20) * (oldLegion.height + 
            newLegion.height + 1), 7 * scale / 2);
    }

}
