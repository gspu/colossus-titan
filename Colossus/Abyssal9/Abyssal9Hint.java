package Abyssal9;


import net.sf.colossus.client.LegionInfo;
import net.sf.colossus.server.HintOracleInterface;
import net.sf.colossus.server.Creature;
import net.sf.colossus.server.Constants;
import net.sf.colossus.util.DevRandom;
import net.sf.colossus.util.Log;
import java.util.*;


public class Abyssal9Hint implements net.sf.colossus.server.HintInterface
{
    private DevRandom rnd = new DevRandom();

    // Convert list of recruits from Creature to String for easier compares.
    public static List creaturesToStrings(List creatures)
    {
        List recruits = new ArrayList();
        for (Iterator it = creatures.iterator(); it.hasNext();)
        {
            Object ob = it.next();
            String str = ob.toString();
            recruits.add(str);
        }
        return recruits;
    }

    public String getRecruitHint(String terrain,
            LegionInfo legion,
            List recruits,
            HintOracleInterface oracle,
            String[] section)
    {
        recruits = creaturesToStrings(recruits);
        List sect = Arrays.asList(section);

        if (terrain.equals("Brush") || terrain.equals("Jungle"))
        {
            int numCyclops = legion.numCreature("Cyclops");
            if (numCyclops > 0 &&
                    numCyclops < 3 &&
                    !legion.contains("Behemoth") &&
                    oracle.creatureAvailable("Behemoth") >= 2 &&
                    oracle.creatureAvailable("Cyclops") >= 1)
            {
                return "Cyclops";
            }
        }
        else if (terrain.equals("Plains"))
        {
            if (recruits.contains("Lion") &&
                    !legion.contains("Griffon") &&
                    legion.numCreature("Lion") == 2 &&
                    oracle.canReach("Desert") &&
                    oracle.creatureAvailable("Griffon") >= 2)
            {
                return "Lion";
            }
            if (sect.contains(Constants.sectionDefensiveAI))
            {
                if (recruits.contains("Centaur") &&
                        legion.numCreature("Centaur") == 2 &&
                        !legion.contains("Warbear") &&
                        legion.getHeight() < 6 &&
                        oracle.biggestAttackerHeight() == 0 &&
                        oracle.canReach("Woods") &&
                        !oracle.hexLabel().equals("1") &&
                        !oracle.hexLabel().equals("15") &&
                        !oracle.hexLabel().equals("29"))
                {
                    return "Centaur";
                }
            }
            else if (sect.contains(Constants.sectionOffensiveAI))
            {
                if (recruits.contains("Centaur") &&
                        legion.numCreature("Centaur") == 2 &&
                        !legion.contains("Warbear") &&
                        legion.getHeight() <= 2 &&
                        oracle.biggestAttackerHeight() == 0 &&
                        oracle.canReach("Woods"))
                {
                    return "Centaur";
                }
            }
        }
        else if (terrain.equals("Marsh"))
        {
            if (recruits.contains("Troll") &&
                    !legion.contains("Wyvern") &&
                    legion.numCreature("Troll") == 2 &&
                    oracle.canReach("Swamp") &&
                    oracle.creatureAvailable("Wyvern") >= 2)
            {
                return "Troll";
            }
            if (sect.contains(Constants.sectionDefensiveAI))
            {
                if (recruits.contains("Ogre") &&
                        legion.numCreature("Ogre") == 2 &&
                        !legion.contains("Minotaur") &&
                        legion.getHeight() < 6 &&
                        oracle.biggestAttackerHeight() == 0 &&
                        oracle.canReach("Hills") &&
                        !oracle.hexLabel().equals("8") &&
                        !oracle.hexLabel().equals("22") &&
                        !oracle.hexLabel().equals("36"))
                {
                    return "Ogre";
                }
            }
            else if (sect.contains(Constants.sectionOffensiveAI))
            {
                if (recruits.contains("Ogre") &&
                        legion.numCreature("Ogre") == 2 &&
                        !legion.contains("Minotaur") &&
                        legion.getHeight() <= 2 &&
                        oracle.biggestAttackerHeight() == 0 &&
                        oracle.canReach("Hills"))
                {
                    return "Ogre";
                }
            }
        }
        else if (terrain.equals("Tower"))
        {
            if (recruits.contains("Warlock"))
            {
                return "Warlock";
            }
            if (recruits.contains("Guardian"))
            {
                return "Guardian";
            }
            if (recruits.contains("Ogre") &&
                    legion.numCreature("Ogre") == 2)
            {
                return "Ogre";
            }
            if (recruits.contains("Centaur") &&
                    legion.numCreature("Centaur") == 2)
            {
                return "Centaur";
            }
            if (recruits.contains("Gargoyle") &&
                    legion.numCreature("Gargoyle") == 1 &&
                    oracle.creatureAvailable("Cyclops") >= 3)
            {
                return "Gargoyle";
            }
            if (recruits.contains("Ogre") &&
                    legion.numCreature("Ogre") == 1 &&
                    oracle.creatureAvailable("Troll") >= 2)
            {
                return "Ogre";
            }
            if (recruits.contains("Centaur") &&
                    legion.numCreature("Centaur") == 1 &&
                    oracle.creatureAvailable("Lion") >= 2)
            {
                return "Centaur";
            }
            if (recruits.contains("Gargoyle") &&
                    legion.numCreature("Gargoyle") == 0 &&
                    oracle.creatureAvailable("Cyclops") >= 6)
            {
                return "Gargoyle";
            }
            if (recruits.contains("Ogre") &&
                    legion.numCreature("Ogre") == 0 &&
                    oracle.creatureAvailable("Troll") >= 6)
            {
                return "Ogre";
            }
            if (recruits.contains("Centaur") &&
                    legion.numCreature("Centaur") == 0 &&
                    oracle.creatureAvailable("Lion") >= 6)
            {
                return "Centaur";
            }
        }

        return (String)recruits.get(recruits.size() - 1);
    }

    public List getInitialSplitHint(String label, String[] section)
    {
        // This variant favors the ogre track over the centaur track at 
        // the start, so keep either ogres or gargoyles with the centaur
        // except for a rare change-up.
        List li = new ArrayList();
        {
            float f = rnd.nextFloat();
            if (f < 0.01)
            {
                li.add("Titan");
                li.add("Gargoyle");
                li.add("Centaur");
                li.add("Centaur");
            }
            else if (f < 0.5)
            {
                li.add("Titan");
                li.add("Gargoyle");
                li.add("Ogre");
                li.add("Ogre");
            }
            else if (f < 0.75)
            {
                li.add("Titan");
                li.add("Gargoyle");
                li.add("Gargoyle");
                li.add("Centaur");
            }
            else
            {
                li.add("Titan");
                li.add("Gargoyle");
                li.add("Gargoyle");
                li.add("Ogre");
            }
        }
        return li;
    }

    public int getHintedRecruitmentValueOffset(String name,
            String[] section)
    {
        return 0;
    }
}