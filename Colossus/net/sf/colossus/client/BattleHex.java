package net.sf.colossus.client;


import java.awt.*;
import java.util.*;
import java.awt.geom.*;

import net.sf.colossus.server.Creature;
import net.sf.colossus.util.HTMLColor;
import net.sf.colossus.util.Log;


/**
 * Class BattleHex holds game state for battle hex.
 * @version $Id$
 * @author David Ripton
 * @author Romain Dolbeau
 */

public class BattleHex extends Hex
{
    /** Valid elevations are 0, 1, and 2. */
    private int elevation;

    // Hex terrain types are:
    // p, r, s, t, o, v, d, w
    // plain, bramble, sand, tree, bog, volcano, drift, tower
    // also
    // l
    // lake

    private static char[] allTerrains =
    { 'p', 'w', 'r', 's', 't', 'o', 'v', 'd', 'l' };

    // Hexside terrain types are:
    // d, c, s, w, space
    // dune, cliff, slope, wall, no obstacle
    private static char[] allHexsides =
    { ' ', 'd', 'c', 's', 'w'};

    // The hexside is marked only in the higher hex.
    private char [] hexsides = new char[6];

    private BattleHex [] neighbors = new BattleHex[6];

    private int xCoord;
    private int yCoord;

    // Hex labels are:
    // A1-A3, B1-B4, C1-C5, D1-D6, E1-E5, F1-F4.
    // Letters increase left to right; numbers increase bottom to top.

    /** Movement costs */
    public static final int IMPASSIBLE_COST = 99;
    private static final int SLOW_COST = 2;
    private static final int NORMAL_COST = 1;



    BattleHex(int xCoord, int yCoord)
    {
        this.xCoord = xCoord;
        this.yCoord = yCoord;

        for (int i = 0; i < 6; i++)
        {
            hexsides[i] = ' ';
        }

        setTerrain('p');
        assignLabel();
    }


    public String getTerrainName()
    {
        switch (getTerrain())
        {
        case 'p':
            switch (elevation)
            {
            case 0:
                return "Plains";
            case 1:
                return "Plains (1)";
            case 2:
                return "Plains (2)";
            }
        case 'w':
            switch (elevation)
            {
            case 0:
                return "Tower";
            case 1:
                return "Tower (1)";
            case 2:
                return "Tower (2)";
            }
        case 'r':
            return "Bramble";
        case 's':
            return "Sand";
        case 't':
            return "Tree";
        case 'o':
            return "Bog";
        case 'v':
            return "Volcano (2)";
        case 'd':
            return "Drift";
        case 'l':
            return "Lake";
            
        default:
            return "?????";
        }
    }
    

    Color getTerrainColor()
    {
        switch (getTerrain())
        {
        case 'p':  // plain
            switch (elevation)
            {
            case 0:
                return HTMLColor.lightOlive;
            case 1:
                return HTMLColor.darkYellow;
            case 2:
                return Color.yellow;
            }
        case 'w':  // tower
            switch (elevation)
            {
            case 0:
                return HTMLColor.lightGray;
            case 1:
                return Color.gray;
            case 2:
                return HTMLColor.darkGray;
            }
        case 'r':  // bramble
            return Color.green;
        case 's':  // sand
            return Color.orange;
        case 't':  // tree
            return HTMLColor.brown;
        case 'o':  // bog
            return Color.gray;
        case 'v':  // volcano
            return Color.red;
        case 'd':  // drift
            return Color.blue;
        case 'l':  // lake
            return HTMLColor.skyBlue;
        default:
            return Color.black;
        }
    }


    public boolean isNativeBonusTerrain()
    {
        char t = getTerrain();
        if (t == 'r' || t == 'v')
        {
            return true;
        }
        for (int i = 0; i < 6; i++)
        {
            char h = getHexside(i);
            if (h == 'w' || h == 's' || h == 'd')
            {
                return true;
            }
        }
        return false;
    }


    public boolean isNonNativePenaltyTerrain()
    {
        char t = getTerrain();
        if (t == 'r' || t == 'd')
        {
            return true;
        }
        for (int i = 0; i < 6; i++)
        {
            char h = getOppositeHexside(i);
            if (h == 'w' || h == 's' || h == 'd')
            {
                return true;
            }
        }
        return false;
    }


    private void assignLabel()
    {
        if (xCoord == -1)
        {
            label = "X" + yCoord;
            return;
        }

        char xLabel;
        switch (xCoord)
        {
            case 0:
                xLabel = 'A';
                break;
            case 1:
                xLabel = 'B';
                break;
            case 2:
                xLabel = 'C';
                break;
            case 3:
                xLabel = 'D';
                break;
            case 4:
                xLabel = 'E';
                break;
            case 5:
                xLabel = 'F';
                break;
            default:
                xLabel = '?';
        }

        int yLabel = 6 - yCoord - (int)Math.abs(((xCoord - 3) / 2));
        label = xLabel + Integer.toString(yLabel);
    }


    public void setHexside(int i, char hexside)
    {
        this.hexsides[i] = hexside;
    }


    public char getHexside(int i)
    {
        if (i >= 0 && i <= 5)
        {
            return hexsides[i];
        }
        else
        {
            Log.warn("Called BattleHex.getHexside() with " + i);
            return '?';
        }
    }

    public String getHexsideName(int i)
    {
        switch(hexsides[i])
        {
        default:
        case ' ':
            return("Nothing");
        case 'd':
            return("Dune");
        case 'c':
            return("Cliff");
        case 's':
            return("Slope");
        case 'w':
            return("Wall");
        }
    }

    /** Return the flip side of hexside i. */
    public char getOppositeHexside(int i)
    {
        char hexside = ' ';

        BattleHex neighbor = getNeighbor(i);
        if (neighbor != null)
        {
            hexside = neighbor.getHexside((i + 3) % 6);
        }

        return hexside;
    }


    public int getElevation()
    {
        return elevation;
    }

    public void setElevation (int elevation)
    {
        this.elevation = elevation;
    }


    public BattleHex getNeighbor(int i)
    {
        if (i < 0 || i > 6)
        {
            return null;
        }
        else
        {
            return neighbors[i];
        }
    }

    public void setNeighbor(int i, BattleHex hex)
    {
        if (i >= 0 && i < 6)
        {
            neighbors[i] = hex;
        }
    }


    public int getXCoord()
    {
        return xCoord;
    }

    public int getYCoord()
    {
        return yCoord;
    }


    public boolean isEntrance()
    {
        return (xCoord == -1);
    }


    public boolean hasWall()
    {
        for (int i = 0; i < 6; i++)
        {
            if (hexsides[i] == 'w')
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Return the number of movement points it costs to enter this hex.
     * For fliers, this is the cost to land in this hex, not fly over it.
     * If entry is illegal, just return a cost greater than the maximum
     * possible number of movement points. This caller is responsible
     * for checking to see if this hex is already occupied.
     * @param creature The Creature that is trying to move into the BattleHex.
     * @param cameFrom The HexSide through which the Creature try to enter.
     * @return Cost to enter the BattleHex.
     */
    public int getEntryCost(Creature creature, int cameFrom)
    {
        char terrain = getTerrain();

        // lake is impassable
        if (terrain == 'l')
        {
            return IMPASSIBLE_COST;
        }

        // Check to see if the hex is occupied or totally impassable.
        if (terrain == 't' || (terrain == 'v' &&
            !creature.getName().equals("Dragon")) || (terrain == 'o' &&
            !creature.isNativeBog()))
        {
            return IMPASSIBLE_COST;
        }

        char hexside = getHexside(cameFrom);

        // Non-fliers may not cross cliffs.
        if ((hexside == 'c' || getOppositeHexside(cameFrom) == 'c') &&
            !creature.isFlier())
        {
            return IMPASSIBLE_COST;
        }

        // Check for a slowing hexside.
        if ((hexside == 'w' || (hexside == 's' && !creature.isNativeSlope()))
            && !creature.isFlier() &&
            elevation > getNeighbor(cameFrom).getElevation())
        {
            // All hexes where this applies happen to have no
            // additional movement costs.
            return SLOW_COST;
        }

        // Bramble, drift, and sand slow non-natives, except that sand
        //     doesn't slow fliers.
        if ((terrain == 'r' && !creature.isNativeBramble()) ||
            (terrain == 'd' && !creature.isNativeDrift()) ||
            (terrain == 's' && !creature.isNativeSandDune() &&
            !creature.isFlier()))
        {
            return SLOW_COST;
        }

        // Other hexes only cost 1.
        return NORMAL_COST;
    }

    /**
     * Check if the Creature given in parameter can fly over
     * the BattleHex, or not.
     * @param creature The Creature that want to fly over this BattleHex
     * @return If the Creature can fly over here or not.
     */
    public boolean canBeFliedOverBy(Creature creature)
    {
        char terrain = getTerrain();
        if (!creature.isFlier())
        { // non-flyer can't fly, obviously...
            return false;
        }
        if (terrain == 'v')
        { // only dragon can fly over volcano
            return ((creature.getName()).equals("Dragon"));
        }
        return(true);
    }

    public boolean isCliff(int hexside)
    {
        return getHexside(hexside) == 'c' || 
            getOppositeHexside(hexside) == 'c';
    }

    public static char[] getTerrains()
    {
        return (char[])allTerrains.clone();
    }

    public static char[] getHexsides()
    {
        return (char[])allHexsides.clone();
    }
}
