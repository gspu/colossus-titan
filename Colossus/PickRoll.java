import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

/**
 * Class PickRoll allows a player to choose which creature(s) recruit.
 * @version $Id$
 * @author David Ripton
 */


public final class PickRoll extends JDialog implements MouseListener,
    WindowListener, KeyListener
{
    private Chit [] dice = new Chit[6];
    private Chit question;
    private static int roll;


    private PickRoll(JFrame parentFrame, String title)
    {
        super(parentFrame, title, true);

        setBackground(Color.lightGray);
        setResizable(false);

        addMouseListener(this);
        addWindowListener(this);
        addKeyListener(this);

        Container contentPane = getContentPane();
        contentPane.setLayout(new FlowLayout());

        int scale = 4 * Scale.get();

        for (int i = 0; i < 6; i++)
        {
            dice[i] = new Chit(scale, "Hit" + (i + 1), this);
            contentPane.add(dice[i]);
            dice[i].addMouseListener(this);
        }
        question = new Chit(scale, "Unknown", this);
        contentPane.add(question);
        question.addMouseListener(this);

        pack();

        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(d.width / 2 - getSize().width / 2,
            d.height / 2 - getSize().height / 2));

        setVisible(true);
        repaint();

        requestFocus();
    }


    public static int pickRoll(JFrame parentFrame, String title)
    {
        new PickRoll(parentFrame, title);
        return roll;
    }


    public void mousePressed(MouseEvent e)
    {
        Object source = e.getSource();
        for (int i = 0; i < 6; i++)
        {
            if (dice[i] == source)
            {
                roll = i + 1;
                dispose();
            }
        }
        if (question == source)
        {
            roll = Game.rollDie();
            dispose();
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


    public void keyTyped(KeyEvent e)
    {
        char ch = e.getKeyChar();
        if (ch >= '1' && ch <= '6')
        {
            roll = ch - '0';
            dispose();
        }
        else if (ch == '?')
        {
            roll = Game.rollDie();
            dispose();
        }
    }


    public void keyPressed(KeyEvent e)
    {
    }


    public void keyReleased(KeyEvent e)
    {
    }


    public boolean isRequestFocusEnabled()
    {
        return true;
    }


    public static void main(String [] args)
    {
        JFrame frame = new JFrame("testing PickRoll");
        int scale = Scale.get();
        frame.setSize(new Dimension(80 * scale, 80 * scale));
        frame.pack();
        frame.setVisible(true);

        int roll = PickRoll.pickRoll(frame, "Pick roll");
        Log.debug("Picked " + roll);
        System.exit(0);
    }
}
