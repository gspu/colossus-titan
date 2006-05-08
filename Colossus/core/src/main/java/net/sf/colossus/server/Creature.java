package net.sf.colossus.server;


import java.util.*;
import java.io.*;

import net.sf.colossus.util.ResourceLoader;
import net.sf.colossus.util.Log;
import net.sf.colossus.xmlparser.CreatureLoader;


/**
 * Class Creature represents the CONSTANT information about a
 * Titan (the game) Creature. Titan (the creature) use
 * class CreatureTitan.
 *
 * Game related info is in Critter.  Counts of
 * recruited/available/dead are in Caretaker.
 *
 * @version $Id$
 * @author David Ripton, Bruce Sherrod
 * @author Romain Dolbeau
 */

public class Creature
implements
    Comparable,
    net.sf.colossus.util.Terrains // H_xxx constants
{
    private final String name;
    private final String pluralName;
    private final int power;
    private final int skill;
    private final boolean rangestrikes;
    private final boolean flies;
    private final boolean nativeBramble;
    private final boolean nativeDrift;
    private final boolean nativeBog;
    private final boolean nativeSandDune;
    private final boolean nativeSlope;
    private final boolean nativeVolcano;
    private final boolean nativeRiver;
    private final boolean nativeStone;
    private final boolean nativeTree;
    private final boolean waterDwelling;
    private final boolean magicMissile;
    private final boolean summonable;
    private final boolean lord;
    private final boolean demilord;
    private int maxCount;  // Not final because we adjust for titans.
    private final String baseColor;
    private static boolean noBaseColor = false;

    public static final Creature unknown = new Creature("Unknown", 1, 1,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            1, "Unknown", null);

    /** Sometimes we need to iterate through all creature types. */
    private static List creatures = new ArrayList();
    private static List summonableCreatures = new ArrayList();

    public Creature(String name, int power, int skill, boolean rangestrikes,
            boolean flies, boolean nativeBramble, boolean nativeDrift,
            boolean nativeBog, boolean nativeSandDune, boolean nativeSlope,
            boolean nativeVolcano, boolean nativeRiver, boolean nativeStone,
            boolean nativeTree, boolean waterDwelling, boolean magicMissile,
            boolean summonable, boolean lord, boolean demilord, int maxCount,
            String pluralName, String baseColor)
    {
        this.name = name;
        this.power = power;
        this.skill = skill;
        this.rangestrikes = rangestrikes;
        this.flies = flies;
        this.nativeBramble = nativeBramble;
        this.nativeDrift = nativeDrift;
        this.nativeBog = nativeBog;
        this.nativeSandDune = nativeSandDune;
        this.nativeSlope = nativeSlope;
        this.nativeVolcano = nativeVolcano;
        this.nativeRiver = nativeRiver;
        this.nativeStone = nativeStone;
        this.nativeTree = nativeTree;
        this.waterDwelling = waterDwelling;
        this.magicMissile = magicMissile;
        this.summonable = summonable;
        this.lord = lord;
        this.demilord = demilord;
        this.maxCount = maxCount;
        this.pluralName = pluralName;
        this.baseColor = baseColor;

        /* warn about likely inapropriate combinations */
        if (waterDwelling && nativeSandDune)
        {
            Log.warn("Creature " + name +
                    " is both a Water Dweller and native to Sand and Dune.");
        }
    }

    public Creature(Creature creature)
    {
        this.name = creature.name;
        this.power = creature.power;
        this.skill = creature.skill;
        this.rangestrikes = creature.rangestrikes;
        this.flies = creature.flies;
        this.nativeBramble = creature.nativeBramble;
        this.nativeDrift = creature.nativeDrift;
        this.nativeBog = creature.nativeBog;
        this.nativeSandDune = creature.nativeSandDune;
        this.nativeSlope = creature.nativeSlope;
        this.nativeVolcano = creature.nativeVolcano;
        this.nativeRiver = creature.nativeRiver;
        this.nativeStone = creature.nativeStone;
        this.nativeTree = creature.nativeTree;
        this.waterDwelling = creature.waterDwelling;
        this.magicMissile = creature.magicMissile;
        this.summonable = creature.summonable;
        this.lord = creature.lord;
        this.demilord = creature.demilord;
        this.maxCount = creature.maxCount;
        this.pluralName = creature.pluralName;
        this.baseColor = creature.baseColor;
    }

    /** Call immediately after loading variant, before using creatures. */
    public static void loadCreatures()
    {
        try
        {
            creatures.clear();
            List directories = VariantSupport.getVarDirectoriesList();
            InputStream creIS = ResourceLoader.getInputStream(
                    VariantSupport.getCreaturesName(), directories);
            if (creIS == null)
            {
                throw new FileNotFoundException(
                        VariantSupport.getCreaturesName());
            }
            CreatureLoader creatureLoader = new CreatureLoader(creIS);
            creatures.addAll(creatureLoader.getCreatures());
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to load Creatures definition", e);
        }
        summonableCreatures.clear();
        Iterator it = creatures.iterator();
        while (it.hasNext())
        {
            Creature c = (Creature)it.next();
            if (c.isSummonable())
            {
                summonableCreatures.add(c);
            }
        }
        Collections.sort(creatures);
    }

    public static List getCreatures()
    {
        return java.util.Collections.unmodifiableList(creatures);
    }

    public static List getSummonableCreatures()
    {
        return java.util.Collections.unmodifiableList(summonableCreatures);
    }

    public int getMaxCount()
    {
        return maxCount;
    }

    /** Only called on Titans after numPlayers is known. */
    void setMaxCount(int maxCount)
    {
        this.maxCount = maxCount;
    }

    public boolean isLord()
    {
        return lord;
    }

    public boolean isDemiLord()
    {
        return demilord;
    }

    public boolean isLordOrDemiLord()
    {
        return (isLord() || isDemiLord());
    }

    public boolean isImmortal()
    { // might not the same for derived class
        return isLordOrDemiLord();
    }

    public boolean isTitan()
    { // Titan use class CreatureTitan
        return false;
    }

    /** true if any if the values can change during the game returned by:
     * - getPower, getSkill, (and therefore getPointValue)
     * - isRangestriker, isFlier, useMagicMissile
     * - isNativeTerraion(t), for all t
     * - isNativeHexSide(h) for all h
     * In Standard game only the titans change their attributes
     */
    public boolean canChangeValue()
    {
        return isTitan();
    }

    /* The name is an unique identifier and must not be changed,
     so this function is final */
    public final String getName()
    {
        return name;
    }

    public String getPluralName()
    {
        return pluralName;
    }

    public String getImageName()
    {
        return name;
    }

    public String getDisplayName()
    {
        return name;
    }

    public String[] getImageNames()
    {
        String[] tempNames;
        if (baseColor != null)
        {
            int specialIncrement = ((isFlier() || isRangestriker()) ? 1 : 0);
            tempNames = new String[4 + specialIncrement];
            String colorSuffix = "-" + (noBaseColor ? "black" : baseColor);
            tempNames[0] = getImageName();
            tempNames[1] = "Power-" + getPower() + colorSuffix;

            tempNames[2] = "Skill-" + getSkill() + colorSuffix;
            tempNames[3] = getDisplayName() + "-Name" + colorSuffix;
            if (specialIncrement > 0)
            {
                tempNames[4] =
                        (isFlier() ? "Flying" : "") +
                        (isRangestriker() ? "Rangestrike" : "") + colorSuffix;
            }
        }
        else
        {
            tempNames = new String[1];
            tempNames[0] = getImageName();
        }
        return tempNames;
    }

    public int getPower()
    {
        return power;
    }

    public int getSkill()
    {
        return skill;
    }

    public int getPointValue()
    { // this function is replicated in Critter
        return getPower() * getSkill();
    }

    public int getHintedRecruitmentValue()
    { // this function is replicated in Critter
        return getPointValue() +
                VariantSupport.getHintedRecruitmentValueOffset(name);
    }

    public int getHintedRecruitmentValue(String[] section)
    { // this function is replicated in Critter
        return getPointValue() +
                VariantSupport.getHintedRecruitmentValueOffset(name, section);
    }

    public boolean isRangestriker()
    {
        return rangestrikes;
    }

    public boolean isFlier()
    {
        return flies;
    }

    public boolean isNativeTerrain(String t)
    {
        if (t.equals(H_PLAINS))
        { /* undefined */
            return false;
        }
        else if (t.equals(H_TOWER))
        { /* undefined, beneficial for everyone */
            return true;
        }
        else if (t.equals(H_BRAMBLES))
        {
            return isNativeBramble();
        }
        else if (t.equals(H_SAND))
        {
            return isNativeSandDune();
        }
        else if (t.equals(H_TREE))
        {
            return isNativeTree();
        }
        else if (t.equals(H_BOG))
        {
            return isNativeBog();
        }
        else if (t.equals(H_VOLCANO))
        {
            return isNativeVolcano();
        }
        else if (t.equals(H_DRIFT))
        {
            return isNativeDrift();
        }
        else if (t.equals(H_LAKE))
        {
            return isWaterDwelling();
        }
        else if (t.equals(H_STONE))
        {
            return isNativeStone();
        }
        else
        {
            return false;
        }
    }

    public boolean isNativeHexside(char h)
    {
        switch (h)
        {
            default:
                return false;

            case ' ': /* undefined */
                return false;

            case 'd':
                return isNativeSandDune();

            case 'c': /* undefined */
                return false;

            case 's':
                return isNativeSlope();

            case 'w': /* undefined, beneficial for everyone */
                return true;

            case 'r':
                return isNativeRiver();
        }
    }

    public boolean isNativeBramble()
    {
        return nativeBramble;
    }

    public boolean isNativeDrift()
    {
        return nativeDrift;
    }

    public boolean isNativeBog()
    {
        return nativeBog;
    }

    public boolean isNativeSandDune()
    {
        return nativeSandDune;
    }

    public boolean isNativeSlope()
    {
        return nativeSlope;
    }

    public boolean isNativeVolcano()
    {
        return nativeVolcano;
    }

    public boolean isNativeRiver()
    {
        return nativeRiver;
    }

    public boolean isNativeStone()
    {
        return nativeStone;
    }

    public boolean isNativeTree()
    {
        return nativeTree;
    }

    public boolean isWaterDwelling()
    {
        return waterDwelling;
    }

    public boolean useMagicMissile()
    {
        return magicMissile;
    }

    public boolean isSummonable()
    {
        return summonable;
    }

    /** getCreatureByName cache.
     *  towi: do you believe it? 20% of the time was spent in the
     *    method getCreatureByName(). i had to do something about that!
     *
     *    since the names of the creatures do NOT change during a game
     *    i implemented a simple caching mechanism, filles upon requests.
     *    in this cache (aka Hashtable) we map from upper/lowercase variants
     *    to the wanted creature.
     *
     *    we add null values when a creature is not found. if these are
     *    queried we can transparently return this null.
     *
     *    Why do I use a "weak" HashMap? I dunno. I guessed in the init phase
     *    of the game a certain set of different spelling variants might occur.
     *    these can be discarded later. Hmm, doesnt matter really, i think.
     */
    private static java.util.Map _getCreatureByName_cache = new WeakHashMap();
    // init the cache with predefined values.
    static {
        // "null" (not a null pointer...) is used for recruiter
        // when it is anonoymous, so it is known and legal,
        // mapped to null (a null pointer, this time).
        _getCreatureByName_cache.put("null", null);
    }

    /** case insensitive creature type lookup. its cached, its fast.
     *
     * implementation description:
     * - if creaturen name is found in cache (hash map String->Creature)
     *   return it
     * - if not, find it iterating
     * - put pair into cache, with this specific spelling variant
     * - return the creature
     *
     * @param name case insensitive (!) name of a creature type
     * @return creature with the given name, null if not a creature.
     * @raise NullPointerException whenname is null
     */
    public static Creature getCreatureByName(final String name)
    {
        // do not allow null key/name.
        if (name == null)
        {
            throw new NullPointerException(
                "Calling Creature.getCreatureByName() on null");
        }

        // first check the cache.
        if (_getCreatureByName_cache.containsKey(name))
        {
            // we found it. can be null, from earlier adding null
            //   as "not found" marker.
            return (Creature) _getCreatureByName_cache.get(name);
        }
        else
        {
            // find it the slow way and add to cache.
            Iterator it = creatures.iterator();
            while (it.hasNext())
            {
                Creature creature = (Creature) it.next();
                if (name.equalsIgnoreCase(creature.getName()))
                {
                    // found it the hard way. now add this spelling to cache
                    _getCreatureByName_cache.put(name, creature);
                    // end search on success.
                    return creature;
                }
            }
            // not found the slow way? damn.
            //   then store this as a negative result for the future, too.
            Log.debug("CUSTOM: unknown creature: " + name);
            _getCreatureByName_cache.put(name, null);
            return null;
        }
    }

    /** @param name (exact) name of a creature.
     * @return true if this names represents a creature
     * TODO: make more efficient. maybe use getCreatureByName()
     * TODO: maybe param name will become case insensitive.
     */
    public static boolean isCreature(final String name)
    {
        Iterator it = creatures.iterator();
        while (it.hasNext())
        {
            Creature creature = (Creature) it.next();
            if (name != null && name.equals(creature.getName()))
            {
                return true;
            }
        }
        return false;
    }

    public String toString()
    {
        return name;
    }

    /**
     * Compare by name.
     * overloaded in Critter w/ a different semantic
     */
    public int compareTo(Object object)
    {
        if (object instanceof Creature)
        {
            Creature other = (Creature)object;
            return (name.compareTo(other.name));
        }
        else
        {
            throw new ClassCastException();
        }
    }

    /** Compare by name. */
    public final boolean equals(Object object)
    {
        if (!(object instanceof Creature))
        {
            return false;
        }
        Creature other = (Creature)object;
        return name.equals(other.getName());
    }

    public int hashCode()
    {
        return name.hashCode();
    }

    public static void setNoBaseColor(boolean b)
    {
        noBaseColor = b;
    }

    public String getBaseColor()
    {
        if (baseColor != null)
        {
            return baseColor;
        }
        else
        {
            return "";
        }
    }

    /** get the non-terrainified part of the kill-value.
     *
     * towi: Note that you have to take special care for creatures that
     * might change their attributes -- like the Power of titans.
     * since this modifies the return value.
     * on the other hand... since titans have a killValue>1000 anyway,
     * this might not matter at all.
     */
    public int getKillValue()
    {
        int val = 10 * getPointValue();
        final int skill = getSkill();
        if (skill >= 4)
        {
            val += 2;
        }
        else if (skill <= 2)
        {
            val += 1;
        }
        if (isFlier())
        {
            val += 4;
        }
        if (isRangestriker())
        {
            val += 5;
        }
        if (useMagicMissile())
        {
            val += 4;
        }
        if (isTitan())
        {
            val += 1000;
        }
        return val;
    }
}