import java.awt.*;
import java.util.*;

/**
 * Class Chit implements the GUI for a Titan chit representing
 * either a monster or a legion.
 * @version $Id$
 * @author David Ripton
 */

class Chit
{
    private Image image;
    private boolean selected = false;
    private Rectangle rect;
    private Container container;

    // Offset of the mouse cursor within the chit.
    private int dx = 0;
    private int dy = 0;

    // Flag to mark chit as dead and paint it with an "X" through it.
    private boolean dead = false;

    Chit(int cx, int cy, int scale, String imageFilename,
        Container container)
    {
        rect = new Rectangle(cx, cy, scale, scale);
        image = Toolkit.getDefaultToolkit().getImage(imageFilename);
        this.container = container;
    }


    void rescale(int scale)
    {
        dx = 0;
        dy = 0;
        rect.width = scale;
        rect.height = scale;
    }


    public void paint(Graphics g)
    {
        g.drawImage(image, rect.x, rect.y, rect.width, rect.width, container);
        if (dead)
        {
            // Draw a triple-wide red X.
            g.setColor(java.awt.Color.red);    

            g.drawLine(rect.x, rect.y, rect.x + rect.width, 
                rect.y + rect.height);
            g.drawLine(rect.x, rect.y - 1, rect.x + rect.width - 1, 
                rect.y + rect.height);
            g.drawLine(rect.x + 1, rect.y, rect.x + rect.width, 
                rect.y + rect.height - 1);

            g.drawLine(rect.x + rect.width, rect.y, rect.x, 
                rect.y + rect.height);
            g.drawLine(rect.x + rect.width - 1, rect.y, rect.x, 
                rect.y + rect.height - 1);
            g.drawLine(rect.x + rect.width, rect.y + 1, rect.x + 1, 
                rect.y + rect.height);
        }
    }


    boolean select(Point point)
    {
        if (rect.contains(point))
        {
            selected = true;
            dx = point.x - rect.x;
            dy = point.y - rect.y;
        }
        else
        {
            selected = false;
        }
        return selected;
    }


    void setLocation(Point point)
    {
        point.x -= dx;
        point.y -= dy;
        rect.setLocation(point);
    }


    void setLocationAbs(Point point)
    {
        rect.setLocation(point);
    }


    public Rectangle getBounds()
    {
        return rect;
    }


    public Point center()
    {
        return new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
    }

    
    public Point topLeft()
    {
        return new Point(rect.x, rect.y);
    }


    public Image getImage()
    {
        return image;
    }


    public boolean isDead()
    {
        return dead;
    }


    public void setDead(boolean dead)
    {
        this.dead = dead;
    }


    public void toggleDead()
    {
        dead = !dead;
    }
}
