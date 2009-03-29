package net.sf.colossus.gui;


import guiutil.KDialog;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.colossus.client.Client;
import net.sf.colossus.game.Legion;
import net.sf.colossus.server.Constants;


/**
 * Class PickRecruiter allows a player to choose which creature(s) recruit.
 * @version $Id$
 * @author David Ripton
 */

final class PickRecruiter extends KDialog
{
    private final List<Chit> recruiterChits = new ArrayList<Chit>();
    private final Marker legionMarker;
    private String recruiterName;
    private final SaveWindow saveWindow;

    /** recruiters is a list of creature name strings */
    private PickRecruiter(JFrame parentFrame, List<String> recruiters,
        String hexDescription, Legion legion, Client client)
    {
        super(parentFrame, client.getOwningPlayer().getName()
            + ": Pick Recruiter in " + hexDescription, true);

        recruiterName = null;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        setBackground(Color.lightGray);
        int scale = 4 * Scale.get();

        JLabel label = new JLabel(
            "  There is more than one way you can recruit this.");
        label.setAlignmentX(FlowLayout.LEADING);

        contentPane.add(Box.createRigidArea(new Dimension(0, scale / 4)));
        contentPane.add(label);
        contentPane.add(Box.createRigidArea(new Dimension(0, scale / 4)));

        JPanel legionPane = new JPanel();
        String legionId = legion.getMarkerId();
        String text = "Current content of legion " + legionId + ":";
        legionPane.setBorder(BorderFactory.createTitledBorder(text));

        legionMarker = new Marker(scale, legion.getMarkerId());
        legionPane.add(legionMarker);

        List<String> imageNames = client.getLegionImageNames(legion);
        Iterator<String> it = imageNames.iterator();
        while (it.hasNext())
        {
            String imageName = it.next();
            Chit chit = new Chit(scale, imageName);
            legionPane.add(chit);
        }

        contentPane.add(legionPane);
        contentPane.add(Box.createRigidArea(new Dimension(0, scale / 4)));

        JLabel label2 = new JLabel(
            "  Pick the creature type you want to reveal:  ");
        label2.setAlignmentX(FlowLayout.LEADING);
        contentPane.add(label2);

        JPanel recruiterPane = new JPanel();
        contentPane.add(recruiterPane);

        for (String potentialRecruiterName : recruiters)
        {
            if (potentialRecruiterName.equals(Constants.titan))
            {
                potentialRecruiterName = legion.getPlayer()
                    .getTitanBasename();
            }
            final String realRecruiterName = potentialRecruiterName;
            Chit chit = new Chit(scale, realRecruiterName);
            recruiterChits.add(chit);
            recruiterPane.add(chit);
            chit.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    recruiterName = realRecruiterName;
                    if (recruiterName.startsWith(Constants.titan))
                    {
                        recruiterName = Constants.titan;
                    }
                    // Then exit.
                    dispose();
                }
            });
        }

        pack();
        saveWindow = new SaveWindow(client.getOptions(), "PickRecruiter");
        Point location = saveWindow.loadLocation();
        if (location == null)
        {
            centerOnScreen();
        }
        else
        {
            setLocation(location);
        }
        setVisible(true);
        repaint();
    }

    private String getRecruiterName()
    {
        return recruiterName;
    }

    static synchronized String pickRecruiter(JFrame parentFrame,
        List<String> recruiters, String hexDescription, Legion legion,
        Client client)
    {
        PickRecruiter pr = new PickRecruiter(parentFrame, recruiters,
            hexDescription, legion, client);
        return pr.getRecruiterName();
    }
}
