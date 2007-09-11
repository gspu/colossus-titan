package net.sf.colossus.client;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import net.sf.colossus.server.Constants;
import net.sf.colossus.util.Log;


public class RevealEvent
{
    private Client client;
    private int turnNumber;
    private int playerNr;
    private int eventType;
    private String markerId;
    private int height;
    private ArrayList knownCreatures;

    // child legion or summoner (for split or summon events)
    private String markerId2;
    private int height2;

    // for mulligan:
    private int oldRoll;
    private int newRoll;
    private String mulliganTitanBaseName;  // for titan in place of solid marker

    private boolean undone = false;
    private int scale;
    private JPanel p;
    
    private String info;
    // set for losing battle events, because if Titan killed the
    // marker does already belong to slayer when we ask. 
    private String realPlayer;

    public final static int eventSplit = 0;
    public final static int eventRecruit = 1;
    public final static int eventSummon = 2;
    public final static int eventTeleport = 3;
    public final static int eventAcquire = 4;
    public final static int eventWon = 5;
    public final static int eventLost = 6;
    public final static int eventTurnChange = 7;
    public final static int eventPlayerChange = 8;
    public final static int eventMulligan = 9;
    public final static int eventMoveRoll = 10;
    
    // Battle is only a temporary state, before it becomes Won or Lost
    // ( = no filter / checkbox setting for that needed, so far at least...)
    public final static int eventBattle = 11;     
    public final static int NUMBEROFEVENTS = 12;

    
    private final static String eventSplitText = "Split";
    private final static String eventRecruitText = "Recruit";
    private final static String eventSummonText = "Summon";
    private final static String eventTeleportText = "Teleport";
    private final static String eventAcquireText = "Acquire";
    private final static String eventWonText = "Winner";
    private final static String eventLostText = "Lost";
    private final static String eventTurnChangeText = "TurnChange";
    private final static String eventPlayerChangeText = "PlayerChange";
    private final static String eventMulliganText = "Mulligan";
    private final static String eventMoveRollText = "Movement roll";
    private final static String eventBattleText = "Battle";

    private static String[] eventTypeToString = {
        eventSplitText, eventRecruitText, eventSummonText, eventTeleportText, 
        eventAcquireText, eventWonText, eventLostText, eventTurnChangeText, 
        eventPlayerChangeText, eventMulliganText, eventMoveRollText, eventBattleText
    };
    
            
    public RevealEvent(Client client, int turnNumber, int playerNr, 
            int eventType, String markerId, int height, ArrayList 
            knownCreatures, String markerId2, int height2)
    {
        if (markerId == null && eventType != eventPlayerChange && 
            eventType != eventTurnChange)
        {
            Log.error("ERROR: null marker for event " + 
                getEventTypeText(eventType));
            return;
        }
        this.client = client;
        this.turnNumber = turnNumber;
        // Number of the player in whose turn this event happens.
        this.playerNr = playerNr;
        this.eventType = eventType;
        // affected legion; split: parent; summon: donor
        this.markerId = markerId;
        this.height = height;
        this.knownCreatures = knownCreatures;
        // next 2: child legion or summoner
        this.markerId2 = markerId2;
        this.height2 = height2;
        
        makeCreaturesTitanChangeSafe(knownCreatures);
    }

    // mulligan and movement roll
    public RevealEvent(Client client, int turnNumber, int playerNr, 
            int eventType, int oldRoll, int newRoll)
    {
        this.client = client;
        this.turnNumber = turnNumber;
        this.playerNr = playerNr;
        this.eventType = eventType;

        this.oldRoll = oldRoll;
        this.newRoll = newRoll;
        
        PlayerInfo info = client.getPlayerInfo(playerNr);
        this.mulliganTitanBaseName = getTitanBasename(info);
    }

    /*
     * if there is a Titan in this legion, then nail down it's 
     * titanBaseName of the time when this happened.
     * Called also after battle to update to new power after
     * points were rewarded.
     */
    private void makeCreaturesTitanChangeSafe(ArrayList list)
    {
        if (list == null || list.isEmpty())
        {
            return;
        }
        
        Iterator it = list.iterator();
        while (it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            if (rc != null && rc.getPlainName() != null && 
                    rc.getPlainName().equals(Constants.titan))
            {
                String playerName = (realPlayer != null ? realPlayer :  
                        client.getPlayerNameByMarkerId(markerId));

                if (playerName == null)
                {
                    Log.error("For making titan base name: " + 
                            "playerName is null!");
                }
                else
                {
                    PlayerInfo info = client.getPlayerInfo(playerName);
                    String tbName = getTitanBasename(info);
                    rc.setTitanBaseName(tbName);
                }
            }
        }
    }

    // only for Engagement events, to set it afterwards to Winner or Loser
    public void setEventType(int eventType)
    {
        this.eventType = eventType;
        // if battle winner, re-trigger the setting titanbasename(s)
        // because his power has changed.
        if (eventType == RevealEvent.eventWon)
        {
            makeCreaturesTitanChangeSafe(knownCreatures);
        }
    }
    
    public void setEventInfo(String info)
    {
        this.info = info;
    }

    public void setRealPlayer(String realPlayer)
    {
        this.realPlayer = realPlayer;
        if (this.realPlayer == null)
        {
            Log.error("RevealEvent: give realPlayer is null!");
            this.realPlayer = "dummy?";
        }
    }
    
    public void setUndone(boolean undone)
    {
        this.undone = undone;
    }
    
    public boolean wasUndone()
    {
        return this.undone;
    }
    
    public void setAllDead()
    {
        Iterator it = this.knownCreatures.iterator();
        while (it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            rc.setDead(true);
        }
        height = 0;
    }
    
    public int getAliveCount()
    {
        int alive = 0;
        Iterator it = this.knownCreatures.iterator();
        while (it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            if (!rc.isDead())
            {
                alive++;
            }
        }
        return alive;
    }

    public int getDeadCount()
    {
        int dead = 0;
        Iterator it = this.knownCreatures.iterator();
        while (it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            if (rc.isDead())
            {
                dead++;
            }
        }
        return dead;
    }

    // so far I don't know any event in which Titan would be added,
    // thus we don't need to make them TitanSafe here.
    public void addCreature(RevealedCreature rc)
    {
        knownCreatures.add(rc);
        height++;
    }

    // creatures were revealed some while after event was created.
    // E.g. engagements.
    public void updateKnownCreatures(ArrayList revealedCreatures)
    {
        this.knownCreatures = revealedCreatures;
        makeCreaturesTitanChangeSafe(knownCreatures);
        this.height = knownCreatures.size();
    }
    
    public void setCreatureDied(String name, int newHeight)
    {
        Iterator it = this.knownCreatures.iterator();
        boolean done = false;
        while (!done && it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            if (rc.matches(name) && !rc.isDead())
            {
                rc.setDead(true);
                done = true;
            }
        }
        if (!done)
        {
            if (name.startsWith("Titan-"))
            {
                // never mind. Whole legion is already gone.
            }
            else
            {
                Log.warn("got order to kill creature " + 
                      name + " in legionEvent " + this.toString() + 
                      " but no such alive creature found!!");
            }
        }
        // client tells us new accurate count how many are still alive.
        height = newHeight;
    }

    // undo the summoning of the angel to this battle event legion
    // (when angel was left off board).
    public boolean undoSummon(int turnNumber, String name)
    {
        if (turnNumber != this.turnNumber)
        {
            Log.warn("undoSummon for " + this.toString() + " -- wrong turn.");
            return false;
        }
        Iterator it = this.knownCreatures.iterator();
        boolean done = false;
        while (!done && it.hasNext())
        {
            RevealedCreature rc = (RevealedCreature) it.next();
            if (rc.matches(name) && rc.wasSummoned())
            {
                it.remove();
                done = true;
            }
        }
        return done;
    }
    
    public int getEventType()
    {
        return eventType;
    }

    public String getEventTypeText()
    {
        return eventTypeToString[eventType];
    }

    public static String getEventTypeText(int type)
    {
        return eventTypeToString[type];
    }

    public String getMarkerId()
    {
        return markerId;
    }

    public String getMarkerId2()
    {
        return markerId2;
    }

    public int getHeight()
    {
        return height;
    }
    
    public int getTurn()
    {
        return turnNumber;
    }
    
    public String getPlayer()
    {
        return client.getPlayerInfo(playerNr).getName();
    }
    
    public int getPlayerNr()
    {
        return playerNr;
    }

    public String toString()
    {
        String msg = "<unknown event?>";
        if (eventType == eventSplit)
        {
            msg = "Revealing event: \"" + getEventTypeText() + "\" (turn "+turnNumber+"):\n" +
                "- Legion with marker: "+ markerId  + " (now height: " + height+"\n" +
                "- Splitoff to marker: "+ markerId2 + " (now height: " + height2+"\n";
        }
        else if (eventType == eventSummon)
        {
            RevealedCreature rc = 
                (RevealedCreature) this.knownCreatures.get(0);
            String summoned = rc.getName();
            
            msg = "Revealing event: \"" + getEventTypeText() + "\":\n" +
                 "  Summonable \"" + summoned + "\" from " + markerId + 
                 "(" + height + ") to " + markerId2 + "("+height2+")";
        }

        else if (eventType == eventWon)
        {
            msg = "Revealing event: " + markerId + " won";
        }

        else if (eventType == eventLost)
        {
            msg = "Revealing event: " + markerId + " lost";
        }

        else if (eventType == eventBattle)
        {
            msg = "Revealing event: " + markerId + " battle starts";
        }

        else if (eventType == eventTurnChange)
        {
            msg = "Revealing event: Turn change, now player "+getPlayerNr()+
            " ("+getPlayer()+"), Turn "+getTurn();
        }
        else if (eventType == eventPlayerChange)
        {
            msg = "Revealing event: Player change, now player "+getPlayerNr()+
            " ("+getPlayer()+"), Turn "+getTurn();
        }
        else if (eventType == eventMulligan)
        {
            msg = "Revealing event: Player "+getPlayerNr()+
            " ("+getPlayer()+"), Turn "+getTurn() + " took mulligan;" +
            " old="+ oldRoll + ", new=" + newRoll;
        }
        else if (eventType == eventMoveRoll)
        {
            msg = "Revealing event: Player "+getPlayerNr()+
            " ("+getPlayer()+"), Turn "+getTurn() + " had movement roll: " +
            " old="+ oldRoll;
        }
        
        else
        {
            StringBuffer msgBuf = new StringBuffer(1000);

            msgBuf.append("Revealing event: \"" + getEventTypeText() + 
                "\" for marker " + markerId + "\n");
            Iterator it = knownCreatures.iterator();
        
            int i = 0;
            while (it.hasNext())
            {   
                i++;
                RevealedCreature rc = (RevealedCreature)it.next();
                msgBuf.append(i + ". " + rc.toString()+"\n");
            }
            msgBuf.append(" => legion " + markerId + " now " + height + 
                " creatures.");
            msg = msgBuf.toString();
        }

        return msg;
    }
    
    private void addLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);
    }
    
    // Todo: paint height on top of marker possible?
    private void addMarker(String markerId, int height)
    {
        if (markerId == null)
        {
            Log.error("ERROR: markerId null, event type " + 
                 getEventTypeText()+" turn" +getTurn());
        }
        try
        {
            Chit marker = new Chit(scale, markerId);
            marker.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(marker);
        }
        catch(Exception e)
        {
            Log.error("new Chit for markerId " + markerId + ", event type "+
              getEventTypeText() + " turn" + getTurn() + " threw exception:" + 
              e.toString());
        };
        addLabel("("+height+")");
    }

    // TODO duplicated from LegionInfo.java:
    /** Return the full basename for a titan in legion markerId,
     *  first finding that legion's player, player color, and titan size.
     *  Default to Constants.titan if the info is not there. */
    String getTitanBasename(PlayerInfo info)
    {
        try
        {
            String color = info.getColor();
            int power = info.getTitanPower();
            return "Titan-" + power + "-" + color;
        }
        catch (Exception ex)
        {
            Log.error("RevealEvent.gettitanbasename, PlayerInfo threw " +
                      "exception " + ex.toString());
            return Constants.titan;
        }
    }
 
    // NOTE: this assumes that this event is for the player in whose 
    // turn this happens:
    private Chit getSolidMarker()
    {
        Chit solidMarker;
        // I would have liked to paint a solid marker with color of that
        // player, instead of the Titan picture (or any individual marker),
        // because this is for the "player as such", not related to any 
        // single marker or the Titan creature.
        // But even if I had created BrSolid.gif (or even copied
        // Br01.gif to that name), did compileVariants, and the gif image
        // was listed in jar tfv usage, still I got "Couldn't get image"
        // error and everything was hanging.
        // So, for now we go with the Titan icon.
/*
        try
        {
            String color = client.getShortColor(playerNr);
            solidMarker = new Chit(scale, color+"Solid");
        }
        catch(Exception e)
        {
            Log.error("exception...");
            // if solid marker does not exist for this color,
            // use as fallback the Titan chit.
            solidMarker = new Chit(scale, getTitanBasename());
        }
*/
        // NOTE: this assumes that this event is for the player in whose 
        //       turn this happens:
        solidMarker = new Chit(scale, mulliganTitanBaseName);
        solidMarker.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        return solidMarker;
    }
    
    private void addCreatureWithInfoToPanel(RevealedCreature rc)
    {
        String info = null;
        if (rc.wasSummoned())
        {
            info = "S:";
        }
        else if (rc.wasAcquired())
        {
            info = "A:";
        }
        else if (rc.wasRecruited())
        {
            info = "R:";
        }

        if (info != null)
        {
            JLabel label = new JLabel(info);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(label);
        }
        addCreatureToPanel(rc);
    }
    
    private void addCreatureToPanel(RevealedCreature rc)
    {
        
        Chit creature = rc.toChit(scale);
        if (creature == null)
        {
            return;
        }
        creature.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(creature);
    }

    private JPanel infoEvent(String text)
    {
        JPanel p = new JPanel();

        p.setBorder(new TitledBorder(""));
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);

        return p;
    }

    /*
     * Provides a complete panel for this event
     */
    public JPanel toPanel()
    {
        this.scale = 2 * Scale.get();

        if (eventType == eventTurnChange)
        {
            return infoEvent("Turn "+turnNumber+" starts");
        }
        if (eventType == eventPlayerChange)
        {
            return infoEvent("Turn "+turnNumber+", player "+getPlayer());
        }

        JPanel p = new JPanel();
        this.p = p;
        
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (eventType == eventMulligan || eventType == eventMoveRoll)
        {
            Chit solidMarker = getSolidMarker();
            p.add(solidMarker);

            p.add(Box.createRigidArea(new Dimension(5,0)));
            addLabel(getEventTypeText()+": ");
            
            Chit oldDie = new MovementDie(this.scale,
                    MovementDie.getDieImageName(oldRoll));
            oldDie.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(oldDie);

            if (eventType == eventMulligan)
            {
            	addLabel(" => ");

            	Chit newDie = new MovementDie(this.scale,
            			MovementDie.getDieImageName(newRoll));
            	newDie.setAlignmentX(Component.LEFT_ALIGNMENT);
            	p.add(newDie);
            }
            
            return p;
        }
        
        int showHeight = height;
        if (eventType == eventSplit)
        {
            showHeight = height + height2; 
        }
        
        addMarker(markerId, showHeight);
        p.add(Box.createRigidArea(new Dimension(5,0)));
        
        String eventTypeText = "";
        if ( (eventType == eventLost) &&
                info != null && !info.equals("") )
        {
            eventTypeText = info;
            if (info.equals(Constants.erMethodFlee))
            {
                eventTypeText = "Fled: ";
            }
            else if (info.equals(Constants.erMethodFight))
            {
                eventTypeText = "Destroyed: ";
            }
            else if (info.equals(Constants.erMethodConcede))
            {
                eventTypeText = "Conceded: ";
            }
            else if (info.equals(Constants.erMethodNegotiate))
            {
                eventTypeText = "Negotiated: ";
            }
        }
        else
        {
            eventTypeText = getEventTypeText()+": ";
        }

        if (wasUndone())
        {
            // two labels below each other, first syaing the event,
            // seconds telling "undone"; both in grey color instead of black;
            Box twoLabels = new Box(BoxLayout.Y_AXIS );
            //twoLabels.setBorder(new TitledBorder(""));
            // twoLabels.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            // twoLabels.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel label1 = new JLabel(eventTypeText);
            label1.setAlignmentY(Component.TOP_ALIGNMENT);
            label1.setForeground(Color.darkGray);
            twoLabels.add(label1);

            JLabel label2 = new JLabel("(undone)");
            label2.setForeground(Color.darkGray);
            label2.setAlignmentY(Component.TOP_ALIGNMENT);
            twoLabels.add(label2);

            p.add(twoLabels);
        }
        else
        {
            addLabel(eventTypeText);
        }
        
        if (eventType == eventSplit)
        {
            addLabel(" => ");
            addMarker(markerId, height);

            p.add(Box.createRigidArea(new Dimension(5,0)));
            addMarker(markerId2, height2);
        }
        else if (eventType == eventRecruit)
        {
            Iterator it = knownCreatures.iterator();
            while (it.hasNext())
            {   
                RevealedCreature rc = (RevealedCreature)it.next();
                if (rc.wasRecruited())
                {
                    addLabel( " => ");
                }
                addCreatureToPanel(rc);
            }
        }
        else if (eventType == eventSummon)
        {
            Iterator it = knownCreatures.iterator();
            while (it.hasNext())
            {   
                RevealedCreature rc = (RevealedCreature)it.next();
                addCreatureToPanel(rc);
            }
            addLabel(" to ");
            addMarker(markerId2, height2);
        }

        else if (eventType == eventAcquire)
        {
            Iterator it = knownCreatures.iterator();
            while (it.hasNext())
            {   
                RevealedCreature rc = (RevealedCreature)it.next();
                addCreatureToPanel(rc);
            }
        }

        else if ( knownCreatures != null )
        {
            Iterator it = knownCreatures.iterator();
            while (it.hasNext())
            {   
                RevealedCreature rc = (RevealedCreature)it.next();
                addCreatureWithInfoToPanel(rc);
            }
            
            if (eventType == eventWon && knownCreatures.size() < height)
            {
                int diff = height-knownCreatures.size();
                if (knownCreatures.isEmpty())
                {
                    addLabel(diff + " creatures (not revealed)");
                }
                else
                {
                    addLabel(" + " + diff + " more (not revealed)");
                }
            }
        }
        
        return p;
    }
}