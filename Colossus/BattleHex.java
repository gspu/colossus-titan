import java.awt.*;

/**
 * Class BattleHex describes one Battlemap hex.
 * @version $Id$
 * @author David Ripton
 */

class BattleHex extends Hex
{
    private BattleMap map;

    // Normal hexes hold only one creature, but entrances can hold up to 7.
    int numCritters = 0;
    private Critter [] critters = new Critter[7];

    // Valid elevations are 0, 1, and 2.
    private int elevation = 0;

    // Terrain types are:
    // p, r, s, t, o, v, d
    // plain, bramble, sand, tree, bog, volcano, drift

    // d, c, s, w, space
    // dune, cliff, slope, wall, no obstacle
    // The hexside is marked only in the higher hex.
    private char [] hexsides = new char[6];

    private BattleHex [] neighbors = new BattleHex[6];

    private int xCoord;
    private int yCoord;

    private String label;


    BattleHex(int cx, int cy, int scale, BattleMap map, int xCoord, int yCoord)
    {
        this.map = map;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.scale = scale;
        len = scale / 3.0;

        xVertex[0] = cx;
        yVertex[0] = cy;
        xVertex[1] = cx + 2 * scale;
        yVertex[1] = cy;
        xVertex[2] = cx + 3 * scale;
        yVertex[2] = cy + (int) Math.round(SQRT3 * scale);
        xVertex[3] = cx + 2 * scale;
        yVertex[3] = cy + (int) Math.round(2 * SQRT3 * scale);
        xVertex[4] = cx;
        yVertex[4] = cy + (int) Math.round(2 * SQRT3 * scale);
        xVertex[5] = cx - 1 * scale;
        yVertex[5] = cy + (int) Math.round(SQRT3 * scale);

        hexagon = new Polygon(xVertex, yVertex, 6);
        // Add 1 to width and height because Java rectangles come up
        // one pixel short.
        rectBound = new Rectangle(xVertex[5], yVertex[0], xVertex[2] -
                        xVertex[5] + 1, yVertex[3] - yVertex[0] + 1);

        for (int i = 0; i < 6; i++)
        {
            hexsides[i] = ' ';
        }

        setTerrain('p');
        assignLabel();
    }


    void rescale(int cx, int cy, int scale)
    {
        this.scale = scale;
        len = scale / 3.0;

        xVertex[0] = cx;
        yVertex[0] = cy;
        xVertex[1] = cx + 2 * scale;
        yVertex[1] = cy;
        xVertex[2] = cx + 3 * scale;
        yVertex[2] = cy + (int) Math.round(SQRT3 * scale);
        xVertex[3] = cx + 2 * scale;
        yVertex[3] = cy + (int) Math.round(2 * SQRT3 * scale);
        xVertex[4] = cx;
        yVertex[4] = cy + (int) Math.round(2 * SQRT3 * scale);
        xVertex[5] = cx - scale;
        yVertex[5] = cy + (int) Math.round(SQRT3 * scale);

        // The hit testing breaks if we just reassign the vertices
        // of the old hexagon.
        hexagon = new Polygon(xVertex, yVertex, 6);

        // Add 1 to width and height because Java rectangles come up
        // one pixel short.
        rectBound.x =  xVertex[5];
        rectBound.y =  yVertex[0];
        rectBound.width = xVertex[2] - xVertex[5] + 1;
        rectBound.height = yVertex[3] - yVertex[0] + 1;
    }


    public void paint(Graphics g)
    {
        if (isSelected())
        {
            g.setColor(Color.white);
        }
        else
        {
            g.setColor(getTerrainColor());
        }

        g.fillPolygon(hexagon);
        g.setColor(Color.black);
        g.drawPolygon(hexagon);

        FontMetrics fontMetrics = g.getFontMetrics();

        String name = getTerrainName().toUpperCase();
        g.drawString(name, rectBound.x + (rectBound.width -
            fontMetrics.stringWidth(name)) / 2,
            rectBound.y + (fontMetrics.getHeight() + rectBound.height) / 2);
        
        // Show hex label in upper left corner.
        g.drawString(label, rectBound.x + (rectBound.width -
            fontMetrics.stringWidth(label)) / 3,
            rectBound.y + (fontMetrics.getHeight() + rectBound.height) / 4);

        // Draw hexside features.
        for (int i = 0; i < 6; i++)
        {
            char hexside = hexsides[i];
            if (hexside != ' ')
            {
                int n = (i + 1) % 6;
                drawHexside(g, xVertex[i], yVertex[i], xVertex[n], yVertex[n],
                    hexside);
            }
        }

        // Draw them again from the other side.
        for (int i = 0; i < 6; i++)
        {
            char hexside = getOppositeHexside(i);
            if (hexside != ' ')
            {
                int n = (i + 1) % 6;
                drawHexside(g, xVertex[n], yVertex[n], xVertex[i], yVertex[i],
                    hexside);
            }
        }
    }


    void repaint()
    {
        // If an entrance needs repainting, paint the whole map.
        if (isEntrance())
        {
            map.repaint();
        }
        else
        {
            map.repaint(rectBound.x, rectBound.y, rectBound.width,
                rectBound.height);
        }
    }


    void drawHexside(Graphics g, int vx1, int vy1, int vx2, int vy2, char
        hexsideType)
    {
        int x0;                  // first focus point
        int y0;
        int x1;                  // second focus point
        int y1;
        int x2;                  // center point
        int y2;
        double theta;            // gate angle
        int [] x = new int[4];   // hexside points
        int [] y = new int[4];   // hexside points


        x0 = vx1 + (vx2 - vx1) / 6;
        y0 = vy1 + (vy2 - vy1) / 6;
        x1 = vx1 + (vx2 - vx1) / 3;
        y1 = vy1 + (vy2 - vy1) / 3;

        theta = Math.atan2(vy2 - vy1, vx2 - vx1);

        switch (hexsideType)
        {
            case 'c':     // cliff -- triangles
                for (int j = 0; j < 3; j++)
                {
                    x0 = vx1 + (vx2 - vx1) * (2 + 3 * j) / 12;
                    y0 = vy1 + (vy2 - vy1) * (2 + 3 * j) / 12;
                    x1 = vx1 + (vx2 - vx1) * (4 + 3 * j) / 12;
                    y1 = vy1 + (vy2 - vy1) * (4 + 3 * j) / 12;

                    x[0] = (int) Math.round(x0 - len * Math.sin(theta));
                    y[0] = (int) Math.round(y0 + len * Math.cos(theta));
                    x[1] = (int) Math.round((x0 + x1) / 2 + len * 
                        Math.sin(theta));
                    y[1] = (int) Math.round((y0 + y1) / 2 - len * 
                        Math.cos(theta));
                    x[2] = (int) Math.round(x1 - len * Math.sin(theta));
                    y[2] = (int) Math.round(y1 + len * Math.cos(theta));
                    
                    g.setColor(Color.white);
                    g.fillPolygon(x, y, 3);
                    g.setColor(Color.black);
                    g.drawPolyline(x, y, 3);
                }
                break;

            case 'd':     // dune --  arcs
                for (int j = 0; j < 3; j++)
                {
                    x0 = vx1 + (vx2 - vx1) * (2 + 3 * j) / 12;
                    y0 = vy1 + (vy2 - vy1) * (2 + 3 * j) / 12;
                    x1 = vx1 + (vx2 - vx1) * (4 + 3 * j) / 12;
                    y1 = vy1 + (vy2 - vy1) * (4 + 3 * j) / 12;

                    x[0] = (int) Math.round(x0 - len * Math.sin(theta));
                    y[0] = (int) Math.round(y0 + len * Math.cos(theta));
                    x[1] = (int) Math.round(x0 + len * Math.sin(theta));
                    y[1] = (int) Math.round(y0 - len * Math.cos(theta));
                    x[2] = (int) Math.round(x1 + len * Math.sin(theta));
                    y[2] = (int) Math.round(y1 - len * Math.cos(theta));
                    x[3] = (int) Math.round(x1 - len * Math.sin(theta));
                    y[3] = (int) Math.round(y1 + len * Math.cos(theta));

                    x2 = (int) Math.round((x0 + x1) / 2);
                    y2 = (int) Math.round((y0 + y1) / 2);
                    Rectangle rect = new Rectangle();
                    rect.x = x2 - (int) Math.round(len);
                    rect.y = y2 - (int) Math.round(len);
                    rect.width = (int) (2 * Math.round(len));
                    rect.height = (int) (2 * Math.round(len));

                    g.setColor(Color.white);
                    // Draw a bit more than a semicircle, to clean edge.
                    g.fillArc(rect.x, rect.y, rect.width, rect.height,
                        (int) Math.round((2 * Math.PI - theta) *
                        RAD_TO_DEG - 10), 200);
                    g.setColor(Color.black);
                    g.drawArc(rect.x, rect.y, rect.width, rect.height,
                        (int) Math.round((2 * Math.PI - theta) * RAD_TO_DEG),
                        180);
                    
                }
                break;

            case 's':     // slope -- lines
                for (int j = 0; j < 3; j++)
                {
                    x0 = vx1 + (vx2 - vx1) * (2 + 3 * j) / 12;
                    y0 = vy1 + (vy2 - vy1) * (2 + 3 * j) / 12;
                    x1 = vx1 + (vx2 - vx1) * (4 + 3 * j) / 12;
                    y1 = vy1 + (vy2 - vy1) * (4 + 3 * j) / 12;

                    x[0] = (int) Math.round(x0 - len / 3 * Math.sin(theta));
                    y[0] = (int) Math.round(y0 + len / 3 * Math.cos(theta));
                    x[1] = (int) Math.round(x0 + len / 3 * Math.sin(theta));
                    y[1] = (int) Math.round(y0 - len / 3 * Math.cos(theta));
                    x[2] = (int) Math.round(x1 + len / 3 * Math.sin(theta));
                    y[2] = (int) Math.round(y1 - len / 3 * Math.cos(theta));
                    x[3] = (int) Math.round(x1 - len / 3 * Math.sin(theta));
                    y[3] = (int) Math.round(y1 + len / 3 * Math.cos(theta));
                    
                    g.setColor(Color.black);
                    g.drawLine(x[0], y[0], x[1], y[1]);
                    g.drawLine(x[2], y[2], x[3], y[3]);
                }
                break;

            case 'w':     // wall --  blocks
                for (int j = 0; j < 3; j++)
                {
                    x0 = vx1 + (vx2 - vx1) * (2 + 3 * j) / 12;
                    y0 = vy1 + (vy2 - vy1) * (2 + 3 * j) / 12;
                    x1 = vx1 + (vx2 - vx1) * (4 + 3 * j) / 12;
                    y1 = vy1 + (vy2 - vy1) * (4 + 3 * j) / 12;

                    x[0] = (int) Math.round(x0 - len * Math.sin(theta));
                    y[0] = (int) Math.round(y0 + len * Math.cos(theta));
                    x[1] = (int) Math.round(x0 + len * Math.sin(theta));
                    y[1] = (int) Math.round(y0 - len * Math.cos(theta));
                    x[2] = (int) Math.round(x1 + len * Math.sin(theta));
                    y[2] = (int) Math.round(y1 - len * Math.cos(theta));
                    x[3] = (int) Math.round(x1 - len * Math.sin(theta));
                    y[3] = (int) Math.round(y1 + len * Math.cos(theta));
                    
                    g.setColor(Color.white);
                    g.fillPolygon(x, y, 4);
                    g.setColor(Color.black);
                    g.drawPolyline(x, y, 4);
                }
                break;
        }
    }


    boolean isOccupied()
    {
        return (numCritters > 0);
    }


    void addCritter(Critter critter)
    {
        if (numCritters < 7)
        {
            critters[numCritters] = critter;
            numCritters++;
            alignChits();
        }
    }


    void removeCritter(int i)
    {
        if (i >= 0 && i < numCritters)
        {
            for (int j = i; j < numCritters - 1; j++)
            {
                critters[j] = critters[j + 1];
            }
            critters[numCritters - 1] = null;
            numCritters--;

            // Clearing the area is only necessary for entrances.
            if (isEntrance())
            {
                map.setEraseFlag();
            }

            // Reposition all chits within the hex.
            alignChits();
        }
    }


    void removeCritter(Critter critter)
    {
        for (int i = 0; i < numCritters; i++)
        {
            if (critters[i] == critter)
            {
                removeCritter(i);
            }
        }
    }


    Critter getCritter()
    {
        if (numCritters > 0)
        {
            return critters[0];
        }
        else
        {
            return null;
        }
    }


    Critter getCritter(int i)
    {
        if (i >= 0 && i < numCritters)
        {
            return critters[i];
        }
        else
        {
            return null;
        }
    }


    void alignChits()
    {
        if (numCritters == 0)
        {
            return;
        }

        int chitScale = critters[0].getChit().getBounds().width;
        Point point = getCenter();

        // Cascade chits diagonally.
        point.x -= chitScale * (1 + (numCritters)) / 4;
        point.y -= chitScale * (1 + (numCritters)) / 4;

        for (int i = 0; i < numCritters; i++)
        {
            BattleChit chit = critters[i].getChit();
            chit.setLocationAbs(point);
            point.x += chitScale / 4;
            point.y += chitScale / 4;
        }
    }


    String getTerrainName()
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
                        return new Color(150, 150, 0);
                    case 1:
                        return new Color(200, 200, 0);
                    case 2:
                        return Color.yellow;
                }
            case 'r':  // bramble
                return Color.green;
            case 's':  // sand
                return Color.orange;
            case 't':  // tree
                return new Color(180, 90, 0);
            case 'o':  // bog
                return Color.gray;
            case 'v':  // volcano
                return Color.red;
            case 'd':  // drift
                return Color.blue;
            default:
                return Color.black;
        }
    }


    // A1-A3, B1-B4, C1-C5, D1-D6, E1-E5, F1-F4.  
    // Letters increase left to right; numbers increase
    // bottom to top.
    String getLabel()
    {
        return label;
    }


    private void assignLabel()
    {
        if (xCoord == -1)
        {
            label = "entrance";
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

        int yLabel = 6 - yCoord - (int) Math.abs(Math.floor((xCoord - 3) / 2));
        label = new String(xLabel + Integer.toString(yLabel));
    }


    void setHexside(int i, char hexside)
    {
        this.hexsides[i] = hexside;
    }


    char getHexside(int i)
    {
        if (i < 0 || i > 5)
        {
            return ' ';
        }
        else
        {
            return hexsides[i];
        }
    }


    // Return the flip side of hexside i.
    char getOppositeHexside(int i)
    {
        char hexside = ' ';

        BattleHex neighbor = getNeighbor(i);
        if (neighbor != null)
        {
            hexside = neighbor.getHexside((i + 3) % 6);
        }

        return hexside;
    }


    void setElevation (int elevation)
    {
        this.elevation = elevation;
    }


    int getElevation()
    {
        return elevation;
    }


    void setNeighbor(int i, BattleHex hex)
    {
        if (i >= 0 && i < 6)
        {
            neighbors[i] = hex;
        }
    }


    BattleHex getNeighbor(int i)
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


    int getXCoord()
    {
        return xCoord;
    }


    int getYCoord()
    {
        return yCoord;
    }


    boolean isEntrance()
    {
        return (xCoord == -1);
    }


    // Return the number of movement points it costs to enter this hex.
    // For fliers, this is the cost to land in this hex, not fly over it.
    // If entry is illegal, just return a cost greater than the maximum
    // possible number of movement points.
    int getEntryCost(Creature creature, int cameFrom)
    {
        char terrain = getTerrain();

        // Check to see if the hex is occupied or totally impassable.
        if (isOccupied() || terrain == 't' || (terrain == 'v' && 
            !creature.getName().equals("Dragon")) || (terrain == 'o' && 
            !creature.isNativeBog()))
        {
            return 5;
        }

        char hexside = getHexside(cameFrom);

        // Non-fliers may not cross cliffs.
        if ((hexside == 'c' || getOppositeHexside(cameFrom) == 'c') && 
            !creature.flies())
        {
            return 5;
        }

        // Check for a slowing hexside.
        if ((hexside == 'w' || (hexside == 's' && !creature.isNativeSlope()))
            && !creature.flies() &&
            elevation > getNeighbor(cameFrom).getElevation())
        {
            // All hexes where this applies happen to have no
            // additional movement costs.
            return 2;
        }

        // Bramble, drift, and sand slow non-natives, except that sand
        //     doesn't slow fliers.
        if ((terrain == 'r' && !creature.isNativeBramble()) ||
            (terrain == 'd' && !creature.isNativeDrift()) ||
            (terrain == 's' && !creature.isNativeSandDune() &&
            !creature.flies()))
        {
            return 2;
        }

        // Other hexes only cost 1.
        return 1;
    }
}
