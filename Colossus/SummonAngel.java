import java.awt.*;
import java.awt.event.*;

/**
 * Class SummonAngel allows a player to Summon an angel or archangel.
 * @version $Id$
 * author David Ripton
 */


class SummonAngel extends Dialog implements MouseListener, ActionListener,
    WindowListener
{
    private MediaTracker tracker;
    private Player player;
    private Legion legion;
    private MasterBoard board;
    private Button button1;
    private Button button2;
    private static final int scale = 60;
    private boolean laidOut = false;
    private Chit angelChit;
    private Chit archangelChit;
    private boolean imagesLoaded = false;
    private Legion donor;
    private Graphics offGraphics;
    private Dimension offDimension;
    private Image offImage;


    SummonAngel(MasterBoard board, Legion legion)
    {
        super(board, legion.getPlayer().getName() +
            ": Summon Angel into Legion " + legion.getMarkerId(), false);

        this.legion = legion;
        player = legion.getPlayer();
        this.board = board;

        // Paranoia
        if (!legion.canSummonAngel())
        {
            cleanup(false);
            return;
        }

        // Count and highlight legions with summonable angels, and put
        // board into a state where those legions can be selected.
        if (board.highlightSummonableAngels(legion) < 1)
        {
            cleanup(false);
            return;
        }

        addMouseListener(this);
        addWindowListener(this);

        setLayout(null);

        pack();

        setBackground(Color.lightGray);
        setSize(getPreferredSize());
        setResizable(false);

        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(d.width / 2 - getSize().width / 2,
            d.height / 2 - getSize().height / 2));

        angelChit = new Chit(2 * scale, scale, scale,
            Creature.angel.getImageName(), this);
        archangelChit = new Chit(5 * scale, scale, scale,
            Creature.archangel.getImageName(), this);
        // X out chits since no legion is selected.
        angelChit.setDead(true);
        archangelChit.setDead(true);

        tracker = new MediaTracker(this);
        tracker.addImage(angelChit.getImage(), 0);
        tracker.addImage(archangelChit.getImage(), 0);
        try
        {
            tracker.waitForAll();
        }
        catch (InterruptedException e)
        {
            new MessageBox(board, e.toString() +
                "waitForAll was interrupted");
        }
        imagesLoaded = true;

        button1 = new Button("Summon");
        button2 = new Button("Cancel");
        add(button1);
        add(button2);
        button1.addActionListener(this);
        button2.addActionListener(this);

        setVisible(true);
        repaint();
    }


    Legion getLegion()
    {
        return legion;
    }


    private void cleanup(boolean summoned)
    {
        if (summoned)
        {
            // Only one angel can be summoned per turn.
            player.disallowSummoningAngel();
            legion.markSummoned();
            player.setLastLegionSummonedFrom(donor);
        }

        dispose();

        // Let the MasterBoard know to leave the angel-summoning state.
        board.finishSummoningAngel();
    }


    public void update(Graphics g)
    {
        if (!imagesLoaded)
        {
            return;
        }

        Dimension d = getSize();

        // Create the back buffer only if we don't have a good one.
        if (offGraphics == null || d.width != offDimension.width ||
            d.height != offDimension.height)
        {
            offDimension = d;
            offImage = createImage(2 * d.width, 2 * d.height);
            offGraphics = offImage.getGraphics();
        }

        donor = player.getSelectedLegion();
        if (donor != null)
        {
            int angels = donor.numCreature(Creature.angel);
            angelChit.setDead(angels == 0);

            int archangels = donor.numCreature(Creature.archangel);
            archangelChit.setDead(archangels == 0);
        }

        angelChit.paint(offGraphics);
        archangelChit.paint(offGraphics);

        if (!laidOut)
        {
            Insets insets = getInsets();
            button1.setBounds(insets.left + d.width / 9, 3 * d.height / 4 -
                insets.bottom, d.width / 3, d.height / 8);
            button2.setBounds(5 * d.width / 9 - insets.right,
                3 * d.height / 4 - insets.bottom, d.width / 3, d.height / 8);
            laidOut = true;
        }

        g.drawImage(offImage, 0, 0, this);
    }


    public void paint(Graphics g)
    {
        // Double-buffer everything.
        update(g);
    }


    public void mousePressed(MouseEvent e)
    {
        donor = player.getSelectedLegion();
        if (donor == null)
        {
            return;
        }

        Point point = e.getPoint();

        if (angelChit.select(point) && !angelChit.isDead())
        {
            donor.removeCreature(Creature.angel);
            // Update the number of creatures in the donor stack.
            donor.getCurrentHex().repaint();

            legion.addCreature(Creature.angel);
            // Update the number of creatures in the legion.
            legion.getCurrentHex().repaint();

            cleanup(true);
        }

        else if (archangelChit.select(point) && !archangelChit.isDead())
        {
            donor.removeCreature(Creature.archangel);
            // Update the number of creatures in the donor stack.
            donor.getCurrentHex().repaint();

            legion.addCreature(Creature.archangel);
            // Update the number of creatures in the legion.
            legion.getCurrentHex().repaint();

            cleanup(true);
        }
    }


    public void mouseEntered(MouseEvent e)
    {
    }


    public void mouseExited(MouseEvent e)
    {
    }


    public void mouseClicked(MouseEvent e)
    {
    }


    public void mouseReleased(MouseEvent e)
    {
    }


    public void windowActivated(WindowEvent e)
    {
    }


    public void windowClosed(WindowEvent e)
    {
    }


    public void windowClosing(WindowEvent e)
    {
        cleanup(false);
    }


    public void windowDeactivated(WindowEvent e)
    {
    }


    public void windowDeiconified(WindowEvent e)
    {
    }


    public void windowIconified(WindowEvent e)
    {
    }


    public void windowOpened(WindowEvent e)
    {
    }


    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("Summon"))
        {
            donor = player.getSelectedLegion();
            if (donor == null)
            {
                new MessageBox(board, "Must select a legion.");
                return;
            }

            int angels = donor.numCreature(Creature.angel);
            int archangels = donor.numCreature(Creature.archangel);

            if (angels == 0 && archangels == 0)
            {
                new MessageBox(board, "No angels are available.");
                return;
            }

            if (archangels == 0)
            {
                // Must take an angel.
                donor.removeCreature(Creature.angel);
                legion.addCreature(Creature.angel);
            }
            else if (angels == 0)
            {
                // Must take an archangel.
                donor.removeCreature(Creature.archangel);
                legion.addCreature(Creature.archangel);
            }
            else
            {
                // If both are available, make the player click on one.
                new MessageBox(board, "Select angel or archangel.");
                return;
            }

            cleanup(true);
        }

        else if (e.getActionCommand().equals("Cancel"))
        {
            cleanup(false);
        }
    }


    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }


    public Dimension getPreferredSize()
    {
        return new Dimension(8 * scale, 3 * scale);
    }
}
