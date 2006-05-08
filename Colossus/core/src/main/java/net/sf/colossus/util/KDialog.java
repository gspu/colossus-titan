package net.sf.colossus.util;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


/** KDialog adds some generally useful functions to JDialog.
 *  @version $Id$
 *  @author David Ripton */

public class KDialog extends JDialog implements MouseListener, WindowListener
{
    private JComponent newContentPane = null;
    protected JLabel label;

    /** Only support one of JDialog's many constructor forms. */    
    public KDialog (Frame owner, String title, boolean modal)
    {
        // The long title goes in a label.  Just put the class 
        // name in the title bar.
        super(owner, modal);
        String shortTitle = getPackageBaseName(getClass().getName());
        setTitle(shortTitle);

        Container cont = super.getContentPane();
        
        cont.setLayout(new BorderLayout());
        newContentPane = new JPanel();

        if (title != null && title.length() > 0)
        {
            label = new JLabel(title);
            cont.add(label, BorderLayout.NORTH);
        }
        cont.add(newContentPane, BorderLayout.CENTER);
    }

    private static String getPackageBaseName(final String packageName)
    {
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    public Container getContentPane()
    {
        return newContentPane;
    }


    /** Place dialog relative to parentFrame's origin, offset by 
     *  point, and fully on-screen. */
    public void placeRelative(JFrame parentFrame, Point point, 
        JScrollPane pane)
    {
  
        JViewport viewPort = pane.getViewport();
        
        // Absolute coordinate in the screen since the window is toplevel
        Point parentOrigin = parentFrame.getLocation();
        
        // Relative coordinate of the view, change when scrolling
        Point viewOrigin = viewPort.getViewPosition();
        
        Point origin = new Point(point.x + parentOrigin.x - viewOrigin.x, 
            point.y + parentOrigin.y - viewOrigin.y);
       
        setLocation(origin);
    }

    /** Center this dialog on the screen.  Must be called after the dialog
     *  size has been set. */
    public void centerOnScreen()
    {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(d.width / 2 - getSize().width / 2,
            d.height / 2 - getSize().height / 2));
    }

    /** Center this dialog on the screen, with an additional offset.
     * Must be called after the dialog size has been set. */
    public void centerOnScreen(int xoffset, int yoffset)
    {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(
             (d.width / 2 - getSize().width / 2) + xoffset,
             (d.height / 2 - getSize().height / 2) + yoffset));
    }

    public void upperRightCorner()
    {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        Point location = new Point(d.width - getSize().width, 0);
        setLocation(location);
    }

    // Move up a few pixels from the bottom, to help avoid taskbars.
    public void lowerRightCorner()
    {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(new Point(d.width - getSize().width,
            d.height - getSize().height - 30));
    }


    // Add the do-nothing mouse and window listener methods here, rather 
    // than using Adapters, to reduce the number of useless little inner
    // class files we generate.

    // Note the potential for error if a subclass tries to override
    // one of these methods, but fails due to a typo, and the compiler
    // no longer flags the error because the interface is legally implemented.
    // (Adapters have the same problem.)

    public void mouseClicked(MouseEvent e)
    {
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

    public void windowClosed(WindowEvent e)
    {
    }

    public void windowActivated(WindowEvent e)
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
}