import java.awt.event.*;
import java.awt.*;

/**
 * Class GameButton launches Colossus as an applet.
 * @version $Id$
 * @author David Barr
 */

public class GameButton extends Button implements ActionListener 
{
    private Game game;
    private GameApplet applet;


    public GameButton(GameApplet applet)
    {
        super("Start");
        addActionListener(this);
        this.applet = applet;
    }

    public void actionPerformed(ActionEvent e) 
    {
        // Don't allow multiple clicks to start multiple simultaneous games.
        if (game == null)
        {
            game = new Game(true, applet);
        }
    }
}
