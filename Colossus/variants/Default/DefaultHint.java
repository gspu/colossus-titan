package Default;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.colossus.ai.AbstractHintProvider;
import net.sf.colossus.util.DevRandom;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.IHintOracle;
import net.sf.colossus.variant.IOracleLegion;
import net.sf.colossus.variant.MasterBoardTerrain;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.variant.Variant;


public class DefaultHint extends AbstractHintProvider
{
    public DefaultHint(Variant variant)
    {
        super(variant);
    }

    private final DevRandom rnd = new DevRandom();

    // Convert list of recruits from Creature to String for easier compares.
    public static List<String> creaturesToStrings(List<CreatureType> creatures)
    {
        List<String> recruits = new ArrayList<String>();
        for (Iterator<CreatureType> it = creatures.iterator(); it.hasNext();)
        {
            Object ob = it.next();
            String str = ob.toString();
            recruits.add(str);
        }
        return recruits;
    }

    public CreatureType getRecruitHint(MasterBoardTerrain terrain,
        IOracleLegion legion, List<CreatureType> recruits,
        IHintOracle oracle,
        List<AIStyle> aiStyles)
    {
        String terrainId = terrain.getId();
        List<String> recruitNames = creaturesToStrings(recruits);

        if (terrainId.equals("Brush") || terrainId.equals("Jungle"))
        {
            int numCyclops = legion.numCreature("Cyclops");
            if (numCyclops > 0 && numCyclops < 3
                && !legion.contains("Behemoth") && !legion.contains("Serpent")
                && oracle.creatureAvailable("Behemoth") >= 2
                && oracle.creatureAvailable("Cyclops") >= 1)
            {
                return getCreatureType("Cyclops");
            }
        }
        else if (terrainId.equals("Plains"))
        {
            if (recruitNames.contains("Lion") && !legion.contains("Griffon")
                && legion.numCreature("Lion") == 2
                && oracle.canReach("Desert")
                && oracle.creatureAvailable("Griffon") >= 2)
            {
                return getCreatureType("Lion");
            }
            if (aiStyles.contains(AIStyle.Defensive))
            {
                if (recruitNames.contains("Centaur")
                    && legion.numCreature("Centaur") == 2
                    && !legion.contains("Warbear") && legion.getHeight() < 6
                    && oracle.biggestAttackerHeight() == 0
                    && oracle.canReach("Woods")
                    && !oracle.hexLabel().equals("1")
                    && !oracle.hexLabel().equals("15")
                    && !oracle.hexLabel().equals("29"))
                {
                    return getCreatureType("Centaur");
                }
            }
            else if (aiStyles.contains(AIStyle.Offensive))
            {
                if (recruitNames.contains("Centaur")
                    && legion.numCreature("Centaur") == 2
                    && !legion.contains("Warbear") && legion.getHeight() <= 2
                    && oracle.biggestAttackerHeight() == 0
                    && oracle.canReach("Woods"))
                {
                    return getCreatureType("Centaur");
                }
            }
        }
        else if (terrainId.equals("Marsh"))
        {
            if (recruitNames.contains("Troll") && !legion.contains("Wyvern")
                && legion.numCreature("Troll") == 2
                && oracle.canReach("Swamp")
                && oracle.creatureAvailable("Wyvern") >= 2)
            {
                return getCreatureType("Troll");
            }
            if (aiStyles.contains(AIStyle.Defensive))
            {
                if (recruitNames.contains("Ogre")
                    && legion.numCreature("Ogre") == 2
                    && !legion.contains("Minotaur") && legion.getHeight() < 6
                    && oracle.biggestAttackerHeight() == 0
                    && oracle.canReach("Hills")
                    && !oracle.hexLabel().equals("8")
                    && !oracle.hexLabel().equals("22")
                    && !oracle.hexLabel().equals("36"))
                {
                    return getCreatureType("Ogre");
                }
            }
            else if (aiStyles.contains(AIStyle.Offensive))
            {
                if (recruitNames.contains("Ogre")
                    && legion.numCreature("Ogre") == 2
                    && !legion.contains("Minotaur") && legion.getHeight() <= 2
                    && oracle.biggestAttackerHeight() == 0
                    && oracle.canReach("Hills"))
                {
                    return getCreatureType("Ogre");
                }
            }
        }
        else if (terrainId.equals("Tower"))
        {
            if (recruitNames.contains("Warlock"))
            {
                return getCreatureType("Warlock");
            }
            if (recruitNames.contains("Guardian"))
            {
                return getCreatureType("Guardian");
            }
            if (recruitNames.contains("Ogre")
                && legion.numCreature("Ogre") == 2)
            {
                return getCreatureType("Ogre");
            }
            if (recruitNames.contains("Centaur")
                && legion.numCreature("Centaur") == 2)
            {
                return getCreatureType("Centaur");
            }
            if (recruitNames.contains("Gargoyle")
                && legion.numCreature("Gargoyle") == 1
                && oracle.creatureAvailable("Cyclops") >= 3)
            {
                return getCreatureType("Gargoyle");
            }
            if (recruitNames.contains("Ogre")
                && legion.numCreature("Ogre") == 1
                && oracle.creatureAvailable("Troll") >= 2)
            {
                return getCreatureType("Ogre");
            }
            if (recruitNames.contains("Centaur")
                && legion.numCreature("Centaur") == 1
                && oracle.creatureAvailable("Lion") >= 2)
            {
                return getCreatureType("Centaur");
            }
            if (recruitNames.contains("Gargoyle")
                && legion.numCreature("Gargoyle") == 0
                && oracle.creatureAvailable("Cyclops") >= 6)
            {
                return getCreatureType("Gargoyle");
            }
            if (recruitNames.contains("Ogre")
                && legion.numCreature("Ogre") == 0
                && oracle.creatureAvailable("Troll") >= 6)
            {
                return getCreatureType("Ogre");
            }
            if (recruitNames.contains("Centaur")
                && legion.numCreature("Centaur") == 0
                && oracle.creatureAvailable("Lion") >= 6)
            {
                return getCreatureType("Centaur");
            }
        }

        return recruits.get(recruits.size() - 1);
    }

    public List<CreatureType> getInitialSplitHint(MasterHex hex,
        List<AIStyle> aiStyles)
    {
        List<CreatureType> li = new ArrayList<CreatureType>();
        if (hex.getLabel().equals("100"))
        {
            if (rnd.nextFloat() < 0.5)
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Centaur"));
                li.add(getCreatureType("Centaur"));
            }
            else
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Ogre"));
            }
        }
        else if (hex.getLabel().equals("200"))
        {
            li.add(getCreatureType("Titan"));
            li.add(getCreatureType("Gargoyle"));
            li.add(getCreatureType("Gargoyle"));
            li.add(getCreatureType("Ogre"));
        }
        else if (hex.getLabel().equals("300"))
        {
            if (rnd.nextFloat() < 0.5)
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Ogre"));
            }
            else
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Centaur"));
                li.add(getCreatureType("Centaur"));
                li.add(getCreatureType("Ogre"));
            }
        }
        else if (hex.getLabel().equals("400"))
        {
            if (rnd.nextFloat() < 0.5)
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Ogre"));
                li.add(getCreatureType("Ogre"));
            }
            else
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Centaur"));
            }
        }
        else if (hex.getLabel().equals("500"))
        {
            li.add(getCreatureType("Titan"));
            li.add(getCreatureType("Gargoyle"));
            li.add(getCreatureType("Gargoyle"));
            li.add(getCreatureType("Centaur"));
        }
        else if (hex.getLabel().equals("600"))
        {
            if (rnd.nextFloat() < 0.5)
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Gargoyle"));
                li.add(getCreatureType("Centaur"));
            }
            else
            {
                li.add(getCreatureType("Titan"));
                li.add(getCreatureType("Ogre"));
                li.add(getCreatureType("Ogre"));
                li.add(getCreatureType("Centaur"));
            }
        }
        else
        {
            throw new RuntimeException("Bad hex: " + hex);
        }
        return li;
    }
}
