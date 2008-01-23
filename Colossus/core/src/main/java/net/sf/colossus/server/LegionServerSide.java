package net.sf.colossus.server;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.colossus.client.BattleMap;
import net.sf.colossus.game.Creature;
import net.sf.colossus.game.Legion;
import net.sf.colossus.game.Player;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.xmlparser.TerrainRecruitLoader;


/**
 * Class Legion represents a Titan stack of Creatures and its
 * stack marker.
 * @version $Id$
 * @author David Ripton
 * @author Romain Dolbeau
 */

public final class LegionServerSide extends net.sf.colossus.game.Legion
    implements Comparable<LegionServerSide>
{
    private static final Logger LOGGER = Logger
        .getLogger(LegionServerSide.class.getName());

    private final String parentId;

    /**
     * The label of the starting hex of the last move.
     */
    private String startingHexLabel;
    private boolean moved;
    private int entrySide = -1;
    private boolean teleported;
    private String recruitName;
    private int battleTally;
    private final GameServerSide game;
    private int angelsToAcquire;

    /**
     * Creates a new Legion instance.
     * 
     * Not that this class does not constraint the number of creatures. In a 
     * normal Titan game it is between 0 and 8 creatures, but this class does
     * not enforce this.
     */
    public LegionServerSide(String markerId, String parentId,
        String currentHexLabel, String startingHexLabel, Player player,
        GameServerSide game, CreatureTypeServerSide... creatureTypes)
    {
        super(player, markerId);
        this.parentId = parentId;
        // Sanity check
        if (parentId != null && parentId.equals(markerId))
        {
            parentId = null;
        }
        setHexLabel(currentHexLabel);
        this.startingHexLabel = startingHexLabel;
        this.game = game;

        for (CreatureTypeServerSide creature : creatureTypes)
        {
            assert creature != null : "Null creature not allowed";
            getCreatures().add(new CreatureServerSide(creature, this, game));
        }
    }

    static LegionServerSide getStartingLegion(String markerId,
        String hexLabel, Player player, GameServerSide game)
    {
        CreatureTypeServerSide[] startCre = TerrainRecruitLoader
            .getStartingCreatures(game.getVariant().getMasterBoard()
                .getHexByLabel(hexLabel).getTerrain());
        LegionServerSide legion = new LegionServerSide(
            markerId,
            null,
            hexLabel,
            hexLabel,
            player,
            game,
            (CreatureTypeServerSide)VariantSupport.getCurrentVariant()
                .getCreatureByName(Constants.titan),
            (CreatureTypeServerSide)VariantSupport
                .getCurrentVariant()
                .getCreatureByName(TerrainRecruitLoader.getPrimaryAcquirable()),
            startCre[2], startCre[2], startCre[0], startCre[0], startCre[1],
            startCre[1]);

        Iterator<CreatureServerSide> it = legion.getCreatures().iterator();
        while (it.hasNext())
        {
            CreatureServerSide critter = it.next();
            game.getCaretaker().takeOne(critter.getCreature());
        }
        return legion;
    }

    public int getPointValue()
    {
        int pointValue = 0;
        for (CreatureServerSide critter : getCreatures())
        {
            pointValue += critter.getPointValue();
        }
        return pointValue;
    }

    /**
     * TODO handling the points shouldn't be really a task for the Legion.
     */
    // Example: Start with 375, earn 150
    void addPoints(int points)
    {
        if (game == null)
        {
            return;
        }
        PlayerServerSide player = getPlayer();
        int score = player.getScore(); // 375
        player.addPoints(points); // 375 + 150 = 525
        int value = TerrainRecruitLoader.getAcquirableRecruitmentsValue();
        // 100
        int tmpScore = score; // 375
        int tmpPoints = points; // 150

        // round Score down, and tmpPoints by the same amount.
        // this allow to keep all points
        int round = (tmpScore % value); //  75
        tmpScore -= round; // 300
        tmpPoints += round; // 225

        List<String> recruits;

        while ((getHeight() < 7) && (tmpPoints >= value))
        {
            tmpScore += value; // 400   500
            tmpPoints -= value; // 125    25
            recruits = game.findEligibleAngels(this, tmpScore);
            if ((recruits != null) && (!recruits.isEmpty()))
            {
                angelsToAcquire++; // 1       2
                game.askAcquireAngel(getPlayer(), this, recruits);
            }
        }
    }

    void addAngel(String angelType)
    {
        if (angelsToAcquire <= 0)
        {
            return;
        }
        if (angelType == null)
        {
            // Declined to acquire.
        }
        else
        {
            if (getHeight() >= 7)
            {
                LOGGER.log(Level.INFO, "Legion " + getLongMarkerName()
                    + " is full and cannot acquire: " + angelType);
            }
            else
            {
                CreatureType angel = game.getVariant().getCreatureByName(
                    angelType);
                if (angel != null)
                {
                    addCreature(angel, true);
                    LOGGER.log(Level.INFO, "Legion " + getLongMarkerName()
                        + " acquires one " + angelType);
                    game.getServer().allTellAddCreature(this, angelType, true,
                        Constants.reasonAcquire);
                }
            }
        }
        angelsToAcquire--;
        if (angelsToAcquire <= 0)
        {
            game.doneAcquiringAngels();
        }
    }

    int getBattleTally()
    {
        return battleTally;
    }

    void clearBattleTally()
    {
        battleTally = 0;
    }

    void clearBattleInfo()
    {
        clearBattleTally();

        for (CreatureServerSide critter : getCreatures())
        {
            critter.heal();
            critter.addBattleInfo(null, null, null);
        }
    }

    void addToBattleTally(int points)
    {
        battleTally += points;
    }

    void addBattleTallyToPoints()
    {
        addPoints(battleTally);
        clearBattleTally();
    }

    public String getMarkerName()
    {
        return getMarkerName(markerId);
    }

    public static String getMarkerName(String markerId)
    {
        return VariantSupport.getMarkerNamesProperties().getProperty(markerId);
    }

    public static String getLongMarkerName(String markerId)
    {
        StringBuffer sb = new StringBuffer(markerId);
        sb.append(" (");
        sb.append(getMarkerName(markerId));
        sb.append(")");
        return sb.toString();
    }

    public String getLongMarkerName()
    {
        return getLongMarkerName(markerId);
    }

    public String getParentId()
    {
        return parentId;
    }

    Legion getParent()
    {
        return getPlayer().getLegionByMarkerId(parentId);
    }

    @Override
    public String toString()
    {
        return markerId;
    }

    /** Return a list of imageNames for all critters in this legion. */
    List<String> getImageNames()
    {
        sortCritters();
        List<String> imageNames = new ArrayList<String>();
        Iterator<CreatureServerSide> it = getCreatures().iterator();
        while (it.hasNext())
        {
            CreatureServerSide critter = it.next();

            /*
             * Use getName(), not getImageName(), as
             * 1) Chit should be built with actual name to make sure
             * they find the Creature ;
             * 2) it seems that elements in this List somehow find their
             * way to Creature.getCreatureByName(), and if one uses the
             * Image Name in there, hell breaks loose.
             */
            String name = critter.getName();
            if (name == null || name.equals(""))
            {
                LOGGER.log(Level.SEVERE, "getImagenames: null or empty name");
            }
            imageNames.add(critter.getName());
        }
        return imageNames;
    }

    String getImageName()
    {
        return markerId;
    }

    boolean canFlee()
    {
        for (CreatureServerSide critter : getCreatures())
        {
            if (critter.isLord())
            {
                return false;
            }
        }
        return true;
    }

    int numCreature(CreatureType creatureType)
    {
        int count = 0;
        for (CreatureServerSide critter : getCreatures())
        {
            if (critter.getType().equals(creatureType))
            {
                count++;
            }
        }
        return count;
    }

    public int numLords()
    {
        int count = 0;
        for (CreatureServerSide critter : getCreatures())
        {
            if (critter.isLord())
            {
                count++;
            }
        }
        return count;
    }

    public int numRangestrikers()
    {
        int count = 0;
        for (CreatureServerSide critter : getCreatures())
        {
            if (critter.isRangestriker())
            {
                count++;
            }
        }
        return count;
    }

    public boolean hasSummonable()
    {
        for (CreatureServerSide critter : getCreatures())
        {
            if (critter.isSummonable())
            {
                return true;
            }
        }
        return false;
    }

    public int getHeight()
    {
        return getCreatures().size();
    }

    @Override
    public PlayerServerSide getPlayer()
    {
        return (PlayerServerSide)super.getPlayer();
    }

    public boolean hasMoved()
    {
        return moved;
    }

    void setMoved(boolean moved)
    {
        this.moved = moved;
    }

    /** Eliminate this legion. */
    void remove(boolean returnCrittersToStacks, boolean updateHistory)
    {
        prepareToRemove(returnCrittersToStacks, updateHistory);
        if (getPlayer() != null)
        {
            getPlayer().removeLegion(this);
        }
    }

    void remove()
    {
        remove(true, true);
    }

    /** Do the cleanup required before this legion can be removed. */
    void prepareToRemove(boolean returnCrittersToStacks, boolean updateHistory)
    {
        LOGGER.log(Level.INFO, "Legion " + markerId
            + getCreatures().toString() + " is eliminated");
        if (getHeight() > 0)
        {
            // Return immortals to the stacks, others to the Graveyard
            Iterator<CreatureServerSide> it = getCreatures().iterator();
            while (it.hasNext())
            {
                CreatureServerSide critter = it.next();
                prepareToRemoveCritter(critter, returnCrittersToStacks,
                    updateHistory);
                it.remove();
            }
        }

        game.getCaretaker().resurrectImmortals();

        // Let the clients clean up the legion marker, etc.
        if (updateHistory)
        {
            game.getServer().allRemoveLegion(this);
        }
        if (getPlayer() != null)
        {
            getPlayer().addLegionMarker(getMarkerId());
        }
    }

    void moveToHex(MasterHex hex, String entrySide, boolean teleported,
        String teleportingLord)
    {
        PlayerServerSide player = getPlayer();
        String hexLabel = hex.getLabel();

        setHexLabel(hexLabel);
        moved = true;

        setEntrySide(entrySide);

        // If we teleported, no more teleports are allowed this turn.
        if (teleported)
        {
            setTeleported(true);
            player.setTeleported(true);
        }

        LOGGER
            .log(Level.INFO,
                "Legion "
                    + getLongMarkerName()
                    + " in "
                    + getStartingHexLabel()
                    + (teleported ? (game.getNumEnemyLegions(hexLabel,
                        getPlayer()) > 0 ? " titan teleports "
                        : " tower teleports (" + teleportingLord + ") ")
                        : " moves ") + "to " + hex.getDescription()
                    + " entering on " + entrySide);
    }

    boolean hasConventionalMove()
    {
        return !game.listNormalMoves(this, getCurrentHex(),
            getPlayer().getMovementRoll(), false).isEmpty();
    }

    void undoMove()
    {
        if (moved)
        {
            // If this legion teleported, allow teleporting again.
            if (hasTeleported())
            {
                setTeleported(false);
                getPlayer().setTeleported(false);
            }

            setHexLabel(startingHexLabel);

            moved = false;
            LOGGER.log(Level.INFO, "Legion " + this + " undoes its move");
        }
    }

    /** Called at end of player turn. */
    void commitMove()
    {
        startingHexLabel = getHexLabel();
        moved = false;
        recruitName = null;
    }

    boolean hasRecruited()
    {
        return (recruitName != null);
    }

    String getRecruitName()
    {
        return recruitName;
    }

    void setRecruitName(String recruitName)
    {
        this.recruitName = recruitName;
    }

    /** hasMoved() is a separate check, so that this function can be used in
     *  battle as well as during the muster phase. */
    boolean canRecruit()
    {
        return (recruitName == null && getHeight() <= 6
            && !getPlayer().isDead() && !(game.findEligibleRecruits(
            getMarkerId(), getHexLabel()).isEmpty()));
    }

    void undoRecruit()
    {
        if (recruitName != null)
        {
            CreatureTypeServerSide creature = (CreatureTypeServerSide)game
                .getVariant().getCreatureByName(recruitName);
            game.getCaretaker().putOneBack(creature);
            removeCreature(creature, false, true);
            recruitName = null;
            LOGGER.log(Level.INFO, "Legion " + getLongMarkerName()
                + " undoes its recruit");
        }
    }

    /** Return true if this legion can summon. */
    boolean canSummonAngel()
    {
        PlayerServerSide player = getPlayer();
        if (getHeight() >= 7 || player.hasSummoned())
        {
            return false;
        }
        return !game.findSummonableAngels(markerId).isEmpty();
    }

    String getStartingHexLabel()
    {
        return startingHexLabel;
    }

    void setEntrySide(int entrySide)
    {
        this.entrySide = entrySide;
    }

    void setEntrySide(String entrySide)
    {
        this.entrySide = BattleMap.entrySideNum(entrySide);
    }

    int getEntrySide()
    {
        return entrySide;
    }

    boolean hasTeleported()
    {
        return teleported;
    }

    void setTeleported(boolean teleported)
    {
        this.teleported = teleported;
    }

    /** Add a creature to this legion.  If takeFromStack is true,
     then do this only if such a creature remains in the stacks,
     and decrement the number of this creature type remaining. */
    boolean addCreature(CreatureType creature, boolean takeFromStack)
    {
        if (getHeight() > 7 || (getHeight() == 7 && game.getTurnNumber() > 1))
        {
            LOGGER.log(Level.SEVERE, "Tried to add to 7-high legion!");
            return false;
        }
        if (takeFromStack)
        {
            Caretaker caretaker = game.getCaretaker();
            if (caretaker.getCount(creature) > 0)
            {
                caretaker.takeOne(creature);
            }
            else
            {
                LOGGER.log(Level.SEVERE, "Tried to addCreature "
                    + creature.toString()
                    + " when there were none of those left!");
                return false;
            }
        }

        getCreatures().add(new CreatureServerSide(creature, this, game));
        return true;
    }

    /** Remove the creature in position i in the legion.  Return the
     removed creature. Put immortal creatures back on the stack
     and others to the Graveyard if returnImmortalToStack is true. */
    CreatureType removeCreature(int i, boolean returnToStack,
        boolean disbandIfEmpty)
    {
        CreatureServerSide critter = getCreatures().remove(i);

        // If the creature is an immortal, put it back in the stacks.
        if (returnToStack)
        {
            if (critter.isImmortal())
            {
                game.getCaretaker().putOneBack(critter.getCreature());
            }
            else
            {
                game.getCaretaker().putDeadOne(critter.getCreature());
            }
        }

        // If there are no critters left, disband the legion.
        if (disbandIfEmpty && getHeight() == 0)
        {
            remove(false, true);
        }
        // return a Creature, not a Critter
        return critter.getCreature();
    }

    /** Remove the first creature matching the passed creature's type
     from the legion.  Return the removed creature. */
    CreatureType removeCreature(CreatureType creature,
        boolean returnImmortalToStack, boolean disbandIfEmpty)
    {
        // indexOf wants the same object, not just the same type.
        // So use getCritter() to get the correct object.
        Creature critter = getCritter(creature);
        if (critter == null)
        {
            return null;
        }
        else
        {
            int i = getCreatures().indexOf(critter);
            return removeCreature(i, returnImmortalToStack, disbandIfEmpty);
        }
    }

    /** Do the cleanup associated with removing the critter from this
     *  legion.  Do not actually remove it, to prevent co-modification
     *  errors.  Do not disband the legion if empty, since the critter
     *  has not actually been removed. */
    void prepareToRemoveCritter(CreatureServerSide critter,
        boolean returnToStacks, boolean updateHistory)
    {
        if (critter == null || !getCreatures().contains(critter))
        {
            LOGGER.log(Level.SEVERE,
                "Called prepareToRemoveCritter with bad critter");
            return;
        }
        // Put even immortal creatures in the graveyard; they will be
        // pulled back out later.
        if (returnToStacks)
        {
            game.getCaretaker().putDeadOne(critter.getCreature());
        }
        if (updateHistory)
        {
            game.removeCreatureEvent(this, critter.getName());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CreatureServerSide> getCreatures()
    {
        return (List<CreatureServerSide>)super.getCreatures();
    }

    /**
     * TODO avoid index-based access
     */
    CreatureServerSide getCritter(int i)
    {
        return getCreatures().get(i);
    }

    void addCritter(CreatureServerSide critter)
    {
        getCreatures().add(critter);
        critter.setLegion(this);
    }

    /** Return the first critter with a matching tag. */
    CreatureServerSide getCritterByTag(int tag)
    {
        for (CreatureServerSide critter : getCreatures())
        {
            if (tag == critter.getTag())
            {
                return critter;
            }
        }
        return null;
    }

    /** Move critter to the first position in the critters list.
     *  Return true if it was moved. */
    boolean moveToTop(CreatureServerSide critter)
    {
        int i = getCreatures().indexOf(critter);
        if (i <= 0)
        {
            // Not found, or already first in the list.
            return false;
        }
        else
        {
            getCreatures().remove(i);
            getCreatures().add(0, critter);
            return true;
        }
    }

    /** Gets the first critter in this legion with the same creature
     type as the passed creature. */
    Creature getCritter(CreatureType creatureType)
    {
        for (Creature creature : getCreatures())
        {
            if (creature.getType().equals(creatureType))
            {
                return creature;
            }
        }
        return null;
    }

    /** 
     * Sort critters into descending order of importance. 
     *
     * TODO maybe a SortedSet would be better instead of sorting every now and then
     */
    void sortCritters()
    {
        Collections.sort(getCreatures(), CreatureServerSide.IMPORTANCE_ORDER);
    }

    /** Recombine this legion into another legion. Only remove this
     legion from the Player if remove is true.  If it's false, the
     caller is responsible for removing this legion, which can avoid
     concurrent access problems. Someone needs to call
     MasterBoard.alignLegions() on the remaining legion's hexLabel
     after the recombined legion is actually removed. */
    void recombine(Legion legion, boolean remove)
    {
        // Sanity check
        if (legion == this)
        {
            LOGGER.log(Level.WARNING,
                "Tried to recombine a legion with itself!");
            return;
        }
        for (Creature critter : getCreatures())
        {
            ((LegionServerSide)legion).addCreature(critter.getType(), false);
        }

        if (remove)
        {
            remove(false, false);
        }
        else
        {
            prepareToRemove(false, false);
        }

        LOGGER.log(Level.INFO, "Legion " + this + " recombined into legion "
            + legion);

        sortCritters();

        // Let the clients know that the legions have recombined.
        game.getServer().undidSplit(this, legion, true, game.getTurnNumber());
    }

    /**
     * Split off creatures into a new legion using legion marker markerId.
     * (Or the first available marker, if markerId is null.)
     * Return the new legion, or null if there's an error.
     */
    Legion split(List<CreatureType> creatures, String newMarkerId)
    {
        PlayerServerSide player = getPlayer();
        if (newMarkerId == null)
        {
            return null;
        }

        player.selectMarkerId(newMarkerId);
        Legion newLegion = new LegionServerSide(newMarkerId, markerId,
            getHexLabel(), getHexLabel(), getPlayer(), game);

        Iterator<CreatureType> it = creatures.iterator();
        while (it.hasNext())
        {
            CreatureType creature = it.next();
            creature = removeCreature(creature, false, false);
            if (creature == null)
            {
                // Abort the split.
                ((LegionServerSide)newLegion).recombine(this, true);
                return null;
            }
            ((LegionServerSide)newLegion).addCreature(creature, false);
        }

        player.addLegion(newLegion);

        game.getServer().allUpdatePlayerInfo();
        LOGGER.log(Level.INFO, ((LegionServerSide)newLegion).getHeight()
            + " creatures are split off from legion " + this
            + " into new legion " + newLegion);

        sortCritters();
        ((LegionServerSide)newLegion).sortCritters();

        // game.getServer().allTellLegionLocation(newMarkerId);

        return newLegion;
    }

    /** List the lords eligible to teleport this legion to hexLabel,
     *  as strings. */
    List<String> listTeleportingLords(String hexLabel)
    {
        // Needs to be a List not a Set so that it can be passed as
        // an imageList.
        List<String> lords = new ArrayList<String>();

        // Titan teleport
        if (game.getNumEnemyLegions(hexLabel, getPlayer()) >= 1)
        {
            if (hasTitan())
            {
                lords.add(Constants.titan);
            }
        }

        // Tower teleport
        else
        {
            for (CreatureServerSide critter : getCreatures())
            {
                if (critter.isLord())
                {
                    String name = critter.getName();
                    if (!lords.contains(name))
                    {
                        lords.add(name);
                    }
                }
            }
        }

        return lords;
    }

    /** Legions are sorted in descending order of total point value,
     with the titan legion always coming first.  This is inconsistent
     with equals(). */
    public int compareTo(LegionServerSide other)
    {
        if (hasTitan())
        {
            return Integer.MIN_VALUE;
        }
        else if (other.hasTitan())
        {
            return Integer.MAX_VALUE;
        }
        else
        {
            return (other.getPointValue() - this.getPointValue());
        }
    }
}
