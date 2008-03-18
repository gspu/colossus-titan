package net.sf.colossus.ai;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import net.sf.colossus.client.BattleChit;
import net.sf.colossus.client.BattleMap;
import net.sf.colossus.client.Client;
import net.sf.colossus.client.CritterMove;
import net.sf.colossus.client.LegionClientSide;
import net.sf.colossus.client.PlayerClientSide;
import net.sf.colossus.client.Strike;
import net.sf.colossus.game.Legion;
import net.sf.colossus.game.Player;
import net.sf.colossus.server.Constants;
import net.sf.colossus.server.Dice;
import net.sf.colossus.server.HintOracleInterface;
import net.sf.colossus.server.VariantSupport;
import net.sf.colossus.util.DevRandom;
import net.sf.colossus.util.Glob;
import net.sf.colossus.util.Options;
import net.sf.colossus.util.PermutationIterator;
import net.sf.colossus.util.Probs;
import net.sf.colossus.variant.BattleHex;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.HazardTerrain;
import net.sf.colossus.variant.MasterBoardTerrain;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.variant.Variant;
import net.sf.colossus.webcommon.InstanceTracker;
import net.sf.colossus.xmlparser.TerrainRecruitLoader;


/**
 * Simple implementation of a Titan AI
 * @version $Id$
 * @author Bruce Sherrod, David Ripton
 * @author Romain Dolbeau
 */
public class SimpleAI implements AI
{
    private static final Logger LOGGER = Logger.getLogger(SimpleAI.class
        .getName());

    /**
     * Stores the skill and power bonuses for a single terrain.
     * 
     * For internal use only, so we don't bother with encapsulation.
     */
    private static class TerrainBonuses
    {
        int attackerPower;
        int defenderPower;
        int attackerSkill;
        int defenderSkill;

        TerrainBonuses(int attackerPower, int defenderPower,
            int attackerSkill, int defenderSkill)
        {
            this.attackerPower = attackerPower;
            this.defenderPower = defenderPower;
            this.attackerSkill = attackerSkill;
            this.defenderSkill = defenderSkill;
        }
    }

    /**
     * Maps the terrain names to their matching bonuses.
     * 
     * Only the terrains that have bonuses are in this map, so
     * users have to expect to retrieve null values. Note that
     * the terrain names include names for master board and
     * hazard terrains, so it can be used for lookup up either
     * type.
     * 
     * TODO there seems to be some overlap with 
     * {@link HazardTerrain#isNativeBonusTerrain()} and
     * {@link HazardTerrain#isNonNativePenaltyTerrain()}.
     * 
     * This is a Map<String,TerrainBonuses>.
     */
    private static final Map<String, TerrainBonuses> TERRAIN_BONUSES = new HashMap<String, TerrainBonuses>();
    static
    {
        // strike down wall, defender strike up
        TERRAIN_BONUSES.put("Tower", new TerrainBonuses(0, 0, 1, 1));
        // native in bramble has skill to hit increased by 1
        TERRAIN_BONUSES.put("Brush", new TerrainBonuses(0, 0, 0, 1));
        TERRAIN_BONUSES.put("Jungle", new TerrainBonuses(0, 0, 0, 1));
        TERRAIN_BONUSES.put("Brambles", new TerrainBonuses(0, 0, 0, 1));
        // native gets an extra die when attack down slope
        // non-native loses 1 skill when attacking up slope
        TERRAIN_BONUSES.put("Hills", new TerrainBonuses(1, 0, 0, 1));
        // native gets an extra 2 dice when attack down dune
        // non-native loses 1 die when attacking up dune
        TERRAIN_BONUSES.put("Desert", new TerrainBonuses(2, 1, 0, 0));
        TERRAIN_BONUSES.put("Sand", new TerrainBonuses(2, 1, 0, 0));
        // Native gets extra 1 die when attack down slope
        // non-native loses 1 skill when attacking up slope
        TERRAIN_BONUSES.put("Mountains", new TerrainBonuses(1, 0, 0, 1));
        TERRAIN_BONUSES.put("Volcano", new TerrainBonuses(1, 0, 0, 1));
        // the other types have only movement bonuses
    }

    Client client;

    int timeLimit = Constants.DEFAULT_AI_TIME_LIMIT; // in s
    boolean timeIsUp;
    Random random = new DevRandom();
    String[] hintSectionUsed = { Constants.sectionOffensiveAI };
    int splitsDone = 0;
    int splitsAcked = 0;
    ArrayList<String> remainingMarkers = null;

    public SimpleAI(Client client)
    {
        this.client = client;
        // initialize the creature info needed by the AI
        InstanceTracker.register(this, client.getOwningPlayer().getName());
    }

    public String pickColor(List<String> colors, List<String> favoriteColors)
    {
        for (String preferredColor : favoriteColors)
        {
            if (colors.contains(preferredColor))
            {
                return preferredColor;
            }
        }
        // Can't have one of our favorites, so take what's there.
        for (String color : colors)
        {
            return color;
        }
        return null;
    }

    /* prepare a list of markers, first those with preferred color,
     * then all the others, but inside these groups shuffled.
     * Caller can then always just take next to get a "random" marker.
     * @param markerIds list of available markers to prepare
     * @param preferredShortColor thos with this color first
     * @returns list of markers
     */
    private ArrayList<String> prepareMarkers(Set<String> markerIds,
        String preferredShortColor)
    {
        List<String> myMarkerIds = new ArrayList<String>();
        List<String> otherMarkerIds = new ArrayList<String>();
        ArrayList<String> allMarkerIds = new ArrayList<String>();

        // split between own / other
        for (String markerId : markerIds)
        {
            if (preferredShortColor != null
                && markerId.startsWith(preferredShortColor))
            {
                myMarkerIds.add(markerId);
            }
            else
            {
                otherMarkerIds.add(markerId);
            }
        }

        if (!(myMarkerIds.isEmpty()))
        {
            Collections.shuffle(myMarkerIds, random);
            allMarkerIds.addAll(myMarkerIds);
        }

        if (!(otherMarkerIds.isEmpty()))
        {
            Collections.shuffle(otherMarkerIds, random);
            allMarkerIds.addAll(otherMarkerIds);
        }
        return allMarkerIds;
    }

    public String pickMarker(Set<String> markerIds, String preferredShortColor)
    {
        List<String> myMarkerIds = new ArrayList<String>();
        List<String> otherMarkerIds = new ArrayList<String>();
        // split between own / other
        for (String markerId : markerIds)
        {
            if (preferredShortColor != null
                && markerId.startsWith(preferredShortColor))
            {
                myMarkerIds.add(markerId);
            }
            else
            {
                otherMarkerIds.add(markerId);
            }
        }

        if (!(myMarkerIds.isEmpty()))
        {
            Collections.shuffle(myMarkerIds, random);
            return myMarkerIds.get(0);
        }

        if (!(otherMarkerIds.isEmpty()))
        {
            Collections.shuffle(otherMarkerIds, random);
            return otherMarkerIds.get(0);
        }
        return null;
    }

    public void muster()
    {
        client.resetRecruitReservations();

        // Do not recruit if this legion is a scooby snack.
        double scoobySnackFactor = 0.15;
        int minimumSizeToRecruit = (int)(scoobySnackFactor * client
            .getAverageLegionPointValue());
        for (LegionClientSide legion : client.getOwningPlayer().getLegions())
        {
            if (legion.hasMoved()
                && legion.canRecruit()
                && (legion.hasTitan() || legion.getPointValue() >= minimumSizeToRecruit))
            {
                CreatureType recruit = chooseRecruit(legion, legion
                    .getCurrentHex(), true);
                if (recruit != null)
                {
                    List<String> recruiters = client.findEligibleRecruiters(
                        legion, recruit.getName());

                    String recruiterName = null;
                    if (!recruiters.isEmpty())
                    {
                        // Just take the first one.
                        recruiterName = recruiters.get(0);
                    }
                    client.doRecruit(legion, recruit.getName(), recruiterName);
                    client.reserveRecruit(recruit);
                }
            }
        }
        client.resetRecruitReservations();
    }

    public void reinforce(Legion legion)
    {
        CreatureType recruit = chooseRecruit((LegionClientSide)legion, legion
            .getCurrentHex());
        String recruitName = null;
        String recruiterName = null;
        if (recruit != null)
        {
            recruitName = recruit.getName();
            List<String> recruiters = client.findEligibleRecruiters(legion,
                recruit.getName());
            if (!recruiters.isEmpty())
            {
                recruiterName = recruiters.get(0);
            }
        }
        // Call regardless to advance past recruiting.
        client.doRecruit(legion, recruitName, recruiterName);
    }

    // support old interface without reserve feature
    CreatureType chooseRecruit(LegionClientSide legion, MasterHex hex)
    {
        return chooseRecruit(legion, hex, false);
    }

    CreatureType chooseRecruit(LegionClientSide legion, MasterHex hex,
        boolean considerReservations)
    {
        List<CreatureType> recruits = client.findEligibleRecruits(legion, hex,
            considerReservations);
        if (recruits.size() == 0)
        {
            return null;
        }

        CreatureType recruit = getVariantRecruitHint(legion, hex, recruits);

        /* use the hinted value as the actual recruit */
        return recruit;
    }

    public boolean split()
    {
        PlayerClientSide player = client.getOwningPlayer();
        remainingMarkers = prepareMarkers(player.getMarkersAvailable(), player
            .getShortColor());

        splitsDone = 0;
        splitsAcked = 0;
        for (LegionClientSide legion : player.getLegions())
        {
            if (remainingMarkers.isEmpty())
            {
                break;
            }
            splitOneLegion(player, legion);
        }
        remainingMarkers.clear();
        remainingMarkers = null;

        // if we did splits, don't signal to client that it's done;
        // because it would do doneWithSplits immediately;
        // instead the last didSplit callback will do doneWithSplits
        // (This is done to avoid the advancePhase illegally messages)
        return splitsDone <= 0;
    }

    /** Unused in this AI; just return true to indicate done. */
    public boolean splitCallback(Legion parent, Legion child)
    {
        splitsAcked++;
        return splitsAcked >= splitsDone;
    }

    private void splitOneLegion(PlayerClientSide player,
        LegionClientSide legion)
    {
        if (legion.getHeight() < 7)
        {
            return;
        }

        // Do not split if we're likely to be forced to attack and lose
        // Do not split if we're likely to want to fight and we need to
        //     be 7 high.
        // Do not split if there's no upwards recruiting or angel
        //     acquiring potential.

        // TODO: Don't split if we're about to be attacked and we
        // need the muscle

        // Only consider this if we're not doing initial game split
        if (legion.getHeight() == 7)
        {
            int forcedToAttack = 0;
            boolean goodRecruit = false;
            for (int roll = 1; roll <= 6; roll++)
            {
                Set<MasterHex> moves = client.getMovement().listAllMoves(
                    legion, legion.getCurrentHex(), roll);
                int safeMoves = 0;
                for (MasterHex hex : moves)
                {
                    if (client.getEnemyLegions(hex, player).size() == 0)
                    {
                        safeMoves++;
                        if (!goodRecruit
                            && couldRecruitUp(legion, hex, null, hex
                                .getTerrain()))
                        {
                            goodRecruit = true;
                        }
                    }
                    else
                    {
                        Legion enemy = client.getFirstEnemyLegion(hex, player);
                        int result = estimateBattleResults(legion, true,
                            enemy, hex);

                        if (result == WIN_WITH_MINIMAL_LOSSES)
                        {
                            LOGGER
                                .finest("We can safely split AND attack with "
                                    + legion);
                            safeMoves++;

                            // Also consider acquiring angel.
                            if (!goodRecruit
                                && couldRecruitUp(legion, hex, enemy, hex
                                    .getTerrain()))
                            {
                                goodRecruit = true;
                            }
                        }

                        int result2 = estimateBattleResults(legion, false,
                            enemy, hex);

                        if (result2 == WIN_WITH_MINIMAL_LOSSES
                            && result != WIN_WITH_MINIMAL_LOSSES && roll <= 4)
                        {
                            // don't split so that we can attack!
                            LOGGER.finest("Not splitting " + legion
                                + " because we want the muscle to attack");

                            forcedToAttack = 999;
                            return;
                        }
                    }
                }

                if (safeMoves == 0)
                {
                    forcedToAttack++;
                }
                // If we'll be forced to attack on 2 or more rolls,
                // don't split.
                if (forcedToAttack >= 2)
                {
                    return;
                }
            }
            if (!goodRecruit)
            {
                // No point in splitting, since we can't improve.
                LOGGER.finest("Not splitting " + legion
                    + " because it can't improve from here");
                return;
            }
        }

        if (remainingMarkers.isEmpty())
        {
            LOGGER.finest("Not splitting " + legion
                + " because no markers available");
            return;
        }

        String newMarkerId = remainingMarkers.get(0);
        remainingMarkers.remove(0);

        List<CreatureType> creatures = chooseCreaturesToSplitOut(legion);
        List<String> creatureNames = new ArrayList<String>();
        for (CreatureType creature : creatures)
        {
            creatureNames.add(creature.getName());
        }
        String results = Glob.glob(",", creatureNames);
        // increment BEFORE calling client 
        // (instead of: return true and caller increments). 
        // Otherwise we might have a race situation, if callback is quicker
        // than caller incrementing the splitsDone value...
        this.splitsDone++;
        client.doSplit(legion, newMarkerId, results);
    }

    /** Return true if the legion could recruit or acquire something
     *  better than its worst creature in hexLabel. */
    private boolean couldRecruitUp(Legion legion, MasterHex hex, Legion enemy,
        MasterBoardTerrain terrain)
    {
        CreatureType weakest = client.getGame().getVariant()
            .getCreatureByName(
                ((LegionClientSide)legion).getContents().get(
                    (legion).getHeight() - 1));

        // Consider recruiting.
        List<CreatureType> recruits = client.findEligibleRecruits(legion, hex);
        if (!recruits.isEmpty())
        {
            CreatureType bestRecruit = recruits.get(recruits.size() - 1);
            if (bestRecruit != null
                && getHintedRecruitmentValue(bestRecruit, legion,
                    hintSectionUsed) > getHintedRecruitmentValue(weakest,
                    legion, hintSectionUsed))
            {
                return true;
            }
        }

        // Consider acquiring angels.
        if (enemy != null)
        {
            int pointValue = ((LegionClientSide)enemy).getPointValue();
            boolean wouldFlee = flee(enemy, legion);
            if (wouldFlee)
            {
                pointValue /= 2;
            }

            // should work with all variants
            int currentScore = ((PlayerClientSide)legion.getPlayer())
                .getScore();
            int arv = TerrainRecruitLoader.getAcquirableRecruitmentsValue();
            int nextScore = ((currentScore / arv) + 1) * arv;

            CreatureType bestRecruit = null;
            while ((currentScore + pointValue) >= nextScore)
            {
                List<String> ral = TerrainRecruitLoader
                    .getRecruitableAcquirableList(terrain, nextScore);
                for (String creatureName : ral)
                {
                    CreatureType tempRecruit = client.getGame().getVariant()
                        .getCreatureByName(creatureName);
                    if ((bestRecruit == null)
                        || (getHintedRecruitmentValue(tempRecruit, legion,
                            hintSectionUsed) >= getHintedRecruitmentValue(
                            bestRecruit, legion, hintSectionUsed)))
                    {
                        bestRecruit = tempRecruit;
                    }
                }
                nextScore += arv;
            }

            if (bestRecruit != null
                && getHintedRecruitmentValue(bestRecruit, legion,
                    hintSectionUsed) > getHintedRecruitmentValue(weakest,
                    legion, hintSectionUsed))
            {
                return true;
            }
        }

        return false;
    }

    /** Decide how to split this legion, and return a list of
     *  Creatures to remove.  */
    List<CreatureType> chooseCreaturesToSplitOut(Legion legion)
    {
        //
        // split a 7 or 8 high legion somehow
        //
        // idea: pick the 2 weakest creatures and kick them
        // out. if there are more than 2 weakest creatures,
        // prefer a pair of matching ones.
        //
        // For an 8-high starting legion, call helper
        // method doInitialGameSplit()
        //
        // TODO: keep 3 cyclops if we don't have a behemoth
        // (split out a gorgon instead)
        //
        // TODO: prefer to split out creatures that have no
        // recruiting value (e.g. if we have 1 angel, 2
        // centaurs, 2 gargoyles, and 2 cyclops, split out the
        // gargoyles)
        //
        if (legion.getHeight() == 8)
        {
            return doInitialGameSplit(legion.getCurrentHex());
        }

        CreatureType weakest1 = null;
        CreatureType weakest2 = null;

        for (String name : ((LegionClientSide)legion).getContents())
        {
            CreatureType critter = client.getGame().getVariant()
                .getCreatureByName(name);

            // Never split out the titan.
            if (critter.isTitan())
            {
                continue;
            }

            if (weakest1 == null)
            {
                weakest1 = critter;
            }
            else if (weakest2 == null)
            {
                weakest2 = critter;
            }
            else if (getHintedRecruitmentValue(critter, legion,
                hintSectionUsed) < getHintedRecruitmentValue(weakest1, legion,
                hintSectionUsed))
            {
                weakest1 = critter;
            }
            else if (getHintedRecruitmentValue(critter, legion,
                hintSectionUsed) < getHintedRecruitmentValue(weakest2, legion,
                hintSectionUsed))
            {
                weakest2 = critter;
            }
            else if (getHintedRecruitmentValue(critter, legion,
                hintSectionUsed) == getHintedRecruitmentValue(weakest1,
                legion, hintSectionUsed)
                && getHintedRecruitmentValue(critter, legion, hintSectionUsed) == getHintedRecruitmentValue(
                    weakest2, legion, hintSectionUsed))
            {
                if (critter.getName().equals(weakest1.getName()))
                {
                    weakest2 = critter;
                }
                else if (critter.getName().equals(weakest2.getName()))
                {
                    weakest1 = critter;
                }
            }
        }

        List<CreatureType> creaturesToRemove = new ArrayList<CreatureType>();

        creaturesToRemove.add(weakest1);
        creaturesToRemove.add(weakest2);

        return creaturesToRemove;
    }

    // From Hugh Moore:
    //
    // It really depends on how many players there are and how good I
    // think they are.  In a 5 or 6 player game, I will pretty much
    // always put my gargoyles together in my Titan group. I need the
    // extra strength, and I need it right away.  In 3-4 player
    // games, I certainly lean toward putting my gargoyles together.
    // If my opponents are weak, I sometimes split them for a
    // challenge.  If my opponents are strong, but my situation looks
    // good for one reason or another, I may split them.  I never
    // like to split them when I am in tower 3 or 6, for obvious
    // reasons. In two player games, I normally split the gargoyles,
    // but two player games are fucked up.
    //

    /** Return a list of exactly four creatures (including one lord) to
     *  split out. */
    List<CreatureType> doInitialGameSplit(MasterHex hex)
    {
        List<CreatureType> hintSuggestedSplit = getInitialSplitHint(hex);

        /* Log.debug("HINT: suggest splitting " + hintSuggestedSplit +
         " in " + label); */

        if (!((hintSuggestedSplit == null) || (hintSuggestedSplit.size() != 4)))
        {
            return hintSuggestedSplit;
        }

        CreatureType[] startCre = TerrainRecruitLoader
            .getStartingCreatures(hex.getTerrain());
        // in CMU style splitting, we split centaurs in even towers,
        // ogres in odd towers.
        final boolean oddTower = "100".equals(hex.getLabel())
            || "300".equals(hex.getLabel()) || "500".equals(hex.getLabel());
        final CreatureType splitCreature = oddTower ? startCre[2]
            : startCre[0];
        final CreatureType nonsplitCreature = oddTower ? startCre[0]
            : startCre[2];

        // XXX Hardcoded to default board.
        // don't split gargoyles in tower 3 or 6 (because of the extra jungles)
        if ("300".equals(hex.getLabel()) || "600".equals(hex.getLabel()))
        {
            return CMUsplit(false, splitCreature, nonsplitCreature, hex);
        }
        //
        // new idea due to David Ripton: split gargoyles in tower 2 or
        // 5, because on a 5 we can get to brush and jungle and get 2
        // gargoyles.  I'm not sure if this is really better than recruiting
        // a cyclops and leaving the other group in the tower, but it's
        // interesting so we'll try it.
        //
        else if ("200".equals(hex.getLabel()) || "500".equals(hex.getLabel()))
        {
            return MITsplit(true, splitCreature, nonsplitCreature, hex);
        }
        //
        // otherwise, mix it up for fun
        else
        {
            if (Dice.rollDie() <= 3)
            {
                return MITsplit(true, splitCreature, nonsplitCreature, hex);
            }
            else
            {
                return CMUsplit(true, splitCreature, nonsplitCreature, hex);
            }
        }
    }

    /** Keep the gargoyles together. */
    List<CreatureType> CMUsplit(boolean favorTitan,
        CreatureType splitCreature, CreatureType nonsplitCreature,
        MasterHex hex)
    {
        CreatureType[] startCre = TerrainRecruitLoader
            .getStartingCreatures(hex.getTerrain());
        List<CreatureType> splitoffs = new LinkedList<CreatureType>();

        if (favorTitan)
        {
            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    Constants.titan));
                splitoffs.add(startCre[1]);
                splitoffs.add(startCre[1]);
                splitoffs.add(splitCreature);
            }
            else
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    TerrainRecruitLoader.getPrimaryAcquirable()));
                splitoffs.add(nonsplitCreature);
                splitoffs.add(nonsplitCreature);
                splitoffs.add(splitCreature);
            }
        }
        else
        {
            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    Constants.titan));
            }
            else
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    TerrainRecruitLoader.getPrimaryAcquirable()));
            }

            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(startCre[1]);
                splitoffs.add(startCre[1]);
                splitoffs.add(splitCreature);
            }
            else
            {
                splitoffs.add(nonsplitCreature);
                splitoffs.add(nonsplitCreature);
                splitoffs.add(splitCreature);
            }
        }

        return splitoffs;
    }

    /** Split the gargoyles. */
    List<CreatureType> MITsplit(boolean favorTitan,
        CreatureType splitCreature, CreatureType nonsplitCreature,
        MasterHex hex)
    {
        CreatureType[] startCre = TerrainRecruitLoader
            .getStartingCreatures(hex.getTerrain());
        List<CreatureType> splitoffs = new LinkedList<CreatureType>();

        if (favorTitan)
        {
            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    Constants.titan));
                splitoffs.add(nonsplitCreature);
                splitoffs.add(nonsplitCreature);
                splitoffs.add(startCre[1]);
            }
            else
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    TerrainRecruitLoader.getPrimaryAcquirable()));
                splitoffs.add(splitCreature);
                splitoffs.add(splitCreature);
                splitoffs.add(startCre[1]);
            }
        }
        else
        {
            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    Constants.titan));
            }
            else
            {
                splitoffs.add(client.getGame().getVariant().getCreatureByName(
                    TerrainRecruitLoader.getPrimaryAcquirable()));
            }

            if (Dice.rollDie() <= 3)
            {
                splitoffs.add(nonsplitCreature);
                splitoffs.add(nonsplitCreature);
                splitoffs.add(startCre[1]);
            }
            else
            {
                splitoffs.add(splitCreature);
                splitoffs.add(splitCreature);
                splitoffs.add(startCre[1]);
            }
        }

        return splitoffs;
    }

    List<CreatureType> getInitialSplitHint(MasterHex hex)
    {
        List<String> byName = VariantSupport.getInitialSplitHint(hex,
            hintSectionUsed);

        if (byName == null)
        {
            return null;
        }

        List<CreatureType> byCreature = new ArrayList<CreatureType>();

        for (String name : byName)
        {
            CreatureType cre = client.getGame().getVariant()
                .getCreatureByName(name);
            if (cre == null)
            {
                LOGGER.severe("HINT: Unknown creature in hint (" + name
                    + "), aborting.");
                return null;
            }
            byCreature.add(cre);
        }
        return byCreature;
    }

    /** little helper to store info about possible moves */
    private class MoveInfo
    {
        final LegionClientSide legion;

        /** hex to move to.  if hex == null, then this means sit still. */
        final MasterHex hex;
        final int value;
        final int difference; // difference from sitting still

        MoveInfo(LegionClientSide legion, MasterHex hex, int value,
            int difference)
        {
            this.legion = legion;
            this.hex = hex;
            this.value = value;
            this.difference = difference;
        }
    }

    /** Return true if we need to run this method again after the server
     *  updates the client with the results of a move or mulligan. */
    public boolean masterMove()
    {
        boolean moved = false;

        PlayerClientSide player = client.getOwningPlayer();

        // consider mulligans
        if (handleMulligans(player))
        {
            return true;
        }

        /** cache all places enemies can move to, for use in risk analysis. */
        Map<MasterHex, List<Legion>>[] enemyAttackMap = buildEnemyAttackMap(player);

        // A mapping from Legion to List of MoveInfo objects,
        // listing all moves that we've evaluated.  We use this if
        // we're forced to move.
        Map<Legion, List<MoveInfo>> moveMap = new HashMap<Legion, List<MoveInfo>>();

        moved = handleVoluntaryMoves(player, moveMap, enemyAttackMap);
        if (moved)
        {
            return true;
        }

        // make sure we move splits (when forced)
        moved = handleForcedSplitMoves(player, moveMap);
        if (moved)
        {
            return true;
        }

        // make sure we move at least one legion
        if (!player.hasMoved())
        {
            moved = handleForcedSingleMove(player, moveMap);
            // Earlier here was a comment: 
            // "always need to retry" and hardcoded returned true.
            // In [ 1748718 ] Game halt in Abyssal9 this lead to a deadlock;
            // - so, if here is returned "false" as for "I won't do any more
            // move", that problem does not occur (server recognizes that 
            // there is no legal move and accepts it)
            // -- does this cause negative side effects elsewhere?? 
            // Let's try ;-)

            return moved;
        }
        return false;
    }

    /** Return true if AI took a mulligan. */
    boolean handleMulligans(Player player)
    {
        // TODO: This is really stupid.  Do something smart here.
        if (client.getTurnNumber() == 1
            && player.getMulligansLeft() > 0
            && (client.getMovementRoll() == 2 || client.getMovementRoll() == 5)
            && !client.tookMulligan())
        {
            client.mulligan();
            // TODO Need to wait for new movement roll.
            return true;
        }
        return false;
    }

    /** Return true if we moved something. */
    @SuppressWarnings("unchecked")
    private boolean handleVoluntaryMoves(PlayerClientSide player,
        Map<Legion, List<MoveInfo>> moveMap,
        Map<MasterHex, List<Legion>>[] enemyAttackMap)
    {
        boolean moved = false;
        // TODO this is still List<LegionClientSide> to get the Comparable
        // -> use a Comparator instead since we are the only ones needing this
        List<LegionClientSide> legions = player.getLegions();

        // Sort markerIds in descending order of legion importance.
        Collections.sort(legions);

        for (LegionClientSide legion : legions)
        {
            if (legion.hasMoved() || legion.getCurrentHex() == null)
            {
                continue;
            }

            // compute the value of sitting still
            List<MoveInfo> moveList = new ArrayList<MoveInfo>();
            moveMap.put(legion, moveList);

            MoveInfo sitStillMove = new MoveInfo(legion, null, evaluateMove(
                legion, legion.getCurrentHex(), false, enemyAttackMap), 0);
            moveList.add(sitStillMove);

            // find the best move (1-ply search)
            MasterHex bestHex = null;
            int bestValue = Integer.MIN_VALUE;
            Set<MasterHex> set = client.getMovement().listAllMoves(legion,
                legion.getCurrentHex(), client.getMovementRoll());

            for (MasterHex hex : set)
            {
                // TODO
                // Do not consider moves onto hexes where we already have a 
                // legion. This is sub-optimal since the legion in this hex 
                // may be able to move and "get out of the way"
                if (client.getFriendlyLegions(hex, player).size() > 0)
                {
                    continue;
                }
                final int value = evaluateMove(legion, hex, true,
                    enemyAttackMap);

                if (value > bestValue || bestHex == null)
                {
                    bestValue = value;
                    bestHex = hex;
                }
                MoveInfo move = new MoveInfo(legion, hex, value, value
                    - sitStillMove.value);
                moveList.add(move);
            }

            // if we found a move that's better than sitting still, move
            if (bestValue > sitStillMove.value && bestHex != null)
            {
                moved = doMove(legion, bestHex);
                if (moved)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return true if we moved something. */
    private boolean handleForcedSplitMoves(PlayerClientSide player,
        Map<Legion, List<MoveInfo>> moveMap)
    {
        for (LegionClientSide legion : player.getLegions())
        {
            List<LegionClientSide> friendlyLegions = client
                .getFriendlyLegions(legion.getCurrentHex(), player);

            if (friendlyLegions.size() > 1
                && !client.getMovement().listNormalMoves(legion,
                    legion.getCurrentHex(), client.getMovementRoll())
                    .isEmpty())
            {
                // Pick the legion in this hex whose best move has the
                // least difference with its sitStillValue, scaled by
                // the point value of the legion, and force it to move.
                LOGGER.finest("Ack! forced to move a split group");

                // first, concatenate all the moves for all the
                // legions that are here, and sort them by their
                // difference from sitting still multiplied by
                // the value of the legion.
                List<MoveInfo> allmoves = new ArrayList<MoveInfo>();
                for (Legion friendlyLegion : friendlyLegions)
                {
                    List<MoveInfo> moves = moveMap.get(friendlyLegion);
                    if (moves != null)
                    {
                        allmoves.addAll(moves);
                    }
                }

                Collections.sort(allmoves, new Comparator<MoveInfo>()
                {
                    public int compare(MoveInfo m1, MoveInfo m2)
                    {
                        return m2.difference * (m2.legion).getPointValue()
                            - m1.difference * (m1.legion).getPointValue();
                    }
                });

                // now, one at a time, try applying moves until we
                // have handled our split problem.
                for (MoveInfo move : allmoves)
                {
                    if (move.hex == null)
                    {
                        continue; // skip the sitStill moves
                    }
                    LOGGER.finest("forced to move split legion " + move.legion
                        + " to " + move.hex + " taking penalty "
                        + move.difference
                        + " in order to handle illegal legion " + legion);

                    boolean moved = doMove(move.legion, move.hex);
                    if (moved)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean handleForcedSingleMove(Player player,
        Map<Legion, List<MoveInfo>> moveMap)
    {
        LOGGER.finest("Ack! forced to move someone");

        // Pick the legion whose best move has the least
        // difference with its sitStillValue, scaled by the
        // point value of the legion, and force it to move.

        // first, concatenate all the moves all legions, and
        // sort them by their difference from sitting still

        List<MoveInfo> allmoves = new ArrayList<MoveInfo>();
        for (Legion friendlyLegion : player.getLegions())
        {
            List<MoveInfo> moves = moveMap.get(friendlyLegion);
            if (moves != null)
            {
                allmoves.addAll(moves);
            }
        }

        Collections.sort(allmoves, new Comparator<MoveInfo>()
        {
            public int compare(MoveInfo m1, MoveInfo m2)
            {
                return m2.difference * (m2.legion).getPointValue()
                    - m1.difference * (m1.legion).getPointValue();
            }
        });

        // now, one at a time, try applying moves until we have moved a legion
        for (MoveInfo move : allmoves)
        {
            if (move.hex == null)
            {
                continue; // skip the sitStill moves
            }

            LOGGER.finest("forced to move " + move.legion + " to " + move.hex
                + " taking penalty " + move.difference
                + " in order to handle illegal legion " + move.legion);

            boolean moved = doMove(move.legion, move.hex);
            if (moved)
            {
                return true;
            }
        }

        LOGGER.warning("handleForcedSingleMove() didn't move anyone - "
            + "probably no legion can move?? "
            + "(('see [ 1748718 ] Game halt in Abyssal9'))");
        // Let's hope the server sees it the same way, otherwise
        // we'll loop forever...
        return false;
    }

    private boolean doMove(LegionClientSide legion, MasterHex hex)
    {
        return client.doMove(legion, hex);
    }

    // arrays and generics don't work well together -- TODO replace the
    // array with a list or model some intermediate classes
    @SuppressWarnings("unchecked")
    Map<MasterHex, List<Legion>>[] buildEnemyAttackMap(Player player)
    {
        Map<MasterHex, List<Legion>>[] enemyMap = (Map<MasterHex, List<Legion>>[])new HashMap<?, ?>[7];
        for (int i = 1; i <= 6; i++)
        {
            enemyMap[i] = new HashMap<MasterHex, List<Legion>>();
        }

        // for each enemy player
        for (PlayerClientSide enemyPlayer : client.getPlayers())
        {
            if (enemyPlayer == player)
            {
                continue;
            }

            // for each legion that player controls
            for (LegionClientSide legion : enemyPlayer.getLegions())
            {
                // for each movement roll he might make
                for (int roll = 1; roll <= 6; roll++)
                {
                    // count the moves he can get to
                    Set<MasterHex> set;

                    // Only allow Titan teleport
                    // Remember, tower teleports cannot attack
                    if (legion.hasTitan()
                        && legion.getPlayer().canTitanTeleport()
                        && client.getMovement().titanTeleportAllowed())
                    {
                        set = client.getMovement().listAllMoves(legion,
                            legion.getCurrentHex(), roll);
                    }
                    else
                    {
                        set = client.getMovement().listNormalMoves(legion,
                            legion.getCurrentHex(), roll);
                    }

                    for (MasterHex hex : set)
                    {
                        for (int effectiveRoll = roll; effectiveRoll <= 6; effectiveRoll++)
                        {
                            // legion can attack to hexlabel on a effectiveRoll
                            List<Legion> list = enemyMap[effectiveRoll]
                                .get(hex);

                            if (list == null)
                            {
                                list = new ArrayList<Legion>();
                            }

                            if (list.contains(legion))
                            {
                                continue;
                            }

                            list.add(legion);
                            enemyMap[effectiveRoll].put(hex, list);
                        }
                    }
                }
            }
        }

        return enemyMap;
    }

    // cheap, inaccurate evaluation function.  Returns a value for
    // moving this legion to this hex.  The value defines a distance
    // metric over the set of all possible moves.
    //
    // TODO: should be parameterized with weights
    // TODO: the hex parameter is probably not needed anymore now that we
    // pass the legion instead of just the marker
    //
    int evaluateMove(LegionClientSide legion, MasterHex hex, boolean moved,
        Map<MasterHex, List<Legion>>[] enemyAttackMap)
    {
        // Avoid using MIN_VALUE and MAX_VALUE because of possible overflow.
        final int WIN_GAME = Integer.MAX_VALUE / 2;
        final int LOSE_LEGION = -10000;

        int value = 0;
        // consider making an attack
        final Legion enemyLegion = client.getFirstEnemyLegion(hex, legion
            .getPlayer());

        if (enemyLegion != null)
        {
            final int enemyPointValue = enemyLegion.getPointValue();
            final int result = estimateBattleResults(legion, enemyLegion, hex);

            switch (result)
            {
                case WIN_WITH_MINIMAL_LOSSES:
                    LOGGER.finest("legion " + legion + " can attack "
                        + enemyLegion + " in " + hex
                        + " and WIN_WITH_MINIMAL_LOSSES");

                    // we score a fraction of a basic acquirable
                    value += ((client.getGame().getVariant()
                        .getCreatureByName(TerrainRecruitLoader
                            .getPrimaryAcquirable())).getPointValue() * enemyPointValue)
                        / TerrainRecruitLoader
                            .getAcquirableRecruitmentsValue();
                    // plus a fraction of a titan strength
                    // TODO Should be by variant
                    value += (6 * enemyPointValue)
                        / TerrainRecruitLoader.getTitanImprovementValue();
                    // plus some more for killing a group (this is arbitrary)
                    value += (10 * enemyPointValue) / 100;

                    // TODO if enemy titan, we also score half points
                    // (this may make the AI unfairly gun for your titan)
                    break;

                case WIN_WITH_HEAVY_LOSSES:
                    LOGGER.finest("legion " + legion + " can attack "
                        + enemyLegion + " in " + hex
                        + " and WIN_WITH_HEAVY_LOSSES");
                    // don't do this with our titan unless we can win the game
                    boolean haveOtherSummonables = false;
                    Player player = legion.getPlayer();
                    for (Legion l : player.getLegions())
                    {
                        if (l.equals(legion))
                        {
                            continue;
                        }

                        if (!l.hasSummonable())
                        {
                            continue;
                        }

                        haveOtherSummonables = true;

                        break;
                    }

                    if (legion.hasTitan())
                    {
                        // unless we can win the game with this attack
                        if ((enemyLegion).hasTitan()
                            && client.getNumLivingPlayers() == 2)
                        {
                            // do it and win the game
                            value += enemyPointValue;
                        }
                        else
                        {
                            // ack! we'll fuck up our titan group
                            value += LOSE_LEGION + 10;
                        }
                    }
                    // don't do this if we'll lose our only summonable group
                    // and won't score enough points to make up for it
                    else if (legion.hasSummonable()
                        && !haveOtherSummonables
                        && enemyPointValue < TerrainRecruitLoader
                            .getAcquirableRecruitmentsValue() * .88)
                    {
                        value += LOSE_LEGION + 5;
                    }
                    else
                    {
                        // we score a fraction of a basic acquirable
                        value += ((client.getGame().getVariant()
                            .getCreatureByName(TerrainRecruitLoader
                                .getPrimaryAcquirable())).getPointValue() * enemyPointValue)
                            / TerrainRecruitLoader
                                .getAcquirableRecruitmentsValue();
                        // plus a fraction of a titan strength
                        value += (6 * enemyPointValue)
                            / TerrainRecruitLoader.getTitanImprovementValue();
                        // but we lose this group
                        value -= (20 * legion.getPointValue()) / 100;
                        // TODO: if we have no other angels, more penalty here
                        // TODO: if enemy titan, we also score half points
                        // (this may make the AI unfairly gun for your titan)
                    }
                    break;

                case DRAW:
                    LOGGER.finest("legion " + legion + " can attack "
                        + enemyLegion + " in " + hex + " and DRAW");

                    // If this is an unimportant group for us, but
                    // is enemy titan, do it.  This might be an
                    // unfair use of information for the AI
                    if (legion.numLords() == 0 && enemyLegion.hasTitan())
                    {
                        // Arbitrary value for killing a player but
                        // scoring no points: it's worth a little
                        // If there are only 2 players, we should do this.
                        if (client.getNumLivingPlayers() == 2)
                        {
                            value = WIN_GAME;
                        }
                        else
                        {
                            value += enemyPointValue / 6;
                        }
                    }
                    else
                    {
                        // otherwise no thanks
                        value += LOSE_LEGION + 2;
                    }
                    break;

                case LOSE_BUT_INFLICT_HEAVY_LOSSES:
                    LOGGER.finest("legion " + legion + " can attack "
                        + enemyLegion + " in " + hex
                        + " and LOSE_BUT_INFLICT_HEAVY_LOSSES");

                    // TODO: how important is it that we damage his group?
                    value += LOSE_LEGION + 1;
                    break;

                case LOSE:
                    LOGGER.finest("legion " + legion + " can attack "
                        + enemyLegion + " in " + hex + " and LOSE");

                    value += LOSE_LEGION;
                    break;

                default:
                    LOGGER.severe("Bogus battle result case");
            }
        }

        // consider what we can recruit
        CreatureType recruit = null;

        if (moved)
        {
            recruit = chooseRecruit(legion, hex);

            if (recruit != null)
            {
                int oldval = value;

                if (legion.getHeight() <= 5)
                {
                    value += getHintedRecruitmentValue(recruit, legion,
                        hintSectionUsed);
                }
                else if (legion.getHeight() == 6)
                {
                    // if we're 6-high, then the value of a recruit is
                    // equal to the improvement in the value of the
                    // pieces that we'll have after splitting.
                    // TODO this should call our splitting code to see
                    // what split decision we would make
                    // If the legion would never split, then ignore
                    // this special case.

                    // This special case was overkill.  A 6-high stack
                    // with 3 lions, or a 6-high stack with 3 clopses,
                    // sometimes refused to go to a safe desert/jungle,
                    // and 6-high stacks refused to recruit colossi,
                    // because the value of the recruit was toned down
                    // too much. So the effect has been reduced.
                    LOGGER.finest("--- 6-HIGH SPECIAL CASE");

                    CreatureType weakest1 = null;
                    CreatureType weakest2 = null;

                    for (String name : legion.getContents())
                    {
                        // XXX Titan
                        CreatureType critter = client.getGame().getVariant()
                            .getCreatureByName(name);

                        if (weakest1 == null)
                        {
                            weakest1 = critter;
                        }
                        else if (weakest2 == null)
                        {
                            weakest2 = critter;
                        }
                        else if (getHintedRecruitmentValue(critter, legion,
                            hintSectionUsed) < getHintedRecruitmentValue(
                            weakest1, legion, hintSectionUsed))
                        {
                            weakest1 = critter;
                        }
                        else if (getHintedRecruitmentValue(critter, legion,
                            hintSectionUsed) < getHintedRecruitmentValue(
                            weakest2, legion, hintSectionUsed))
                        {
                            weakest2 = critter;
                        }
                        else if (getHintedRecruitmentValue(critter, legion,
                            hintSectionUsed) == getHintedRecruitmentValue(
                            weakest1, legion, hintSectionUsed)
                            && getHintedRecruitmentValue(critter, legion,
                                hintSectionUsed) == getHintedRecruitmentValue(
                                weakest2, legion, hintSectionUsed))
                        {
                            if (critter.getName().equals(weakest1.getName()))
                            {
                                weakest2 = critter;
                            }
                            else if (critter.getName().equals(
                                weakest2.getName()))
                            {
                                weakest1 = critter;
                            }
                        }
                    }

                    int minCreaturePV = Math.min(getHintedRecruitmentValue(
                        weakest1, legion, hintSectionUsed),
                        getHintedRecruitmentValue(weakest2, legion,
                            hintSectionUsed));
                    int maxCreaturePV = Math.max(getHintedRecruitmentValue(
                        weakest1, legion, hintSectionUsed),
                        getHintedRecruitmentValue(weakest2, legion,
                            hintSectionUsed));
                    // point value of my best 5 pieces right now
                    int oldPV = legion.getPointValue() - minCreaturePV;
                    // point value of my best 5 pieces after adding this
                    // recruit and then splitting off my 2 weakest
                    int newPV = legion.getPointValue()
                        - getHintedRecruitmentValue(weakest1, legion,
                            hintSectionUsed)
                        - getHintedRecruitmentValue(weakest2, legion,
                            hintSectionUsed)
                        + Math.max(maxCreaturePV, getHintedRecruitmentValue(
                            recruit, legion, hintSectionUsed));

                    value += (newPV - oldPV)
                        + getHintedRecruitmentValue(recruit, legion,
                            hintSectionUsed);
                }
                else if (legion.getHeight() == 7)
                {
                    // Cannot recruit, unless we have an angel to summon out,
                    // and we're not fighting, and someone else is, and that
                    // other stack summons out our angel.
                    // Since we don't have enough information about other 
                    // stacks to be sure that someone will summon from us, 
                    // just give a small bonus for the possible recruit, if 
                    // we're not fighting and have a summonable.
                    if (enemyLegion == null && legion.hasSummonable())
                    {
                        // This is total fudge.  Removing an angel may hurt 
                        // this legion, or may help it if the recruit is even
                        // better.  But it'll help someone else.  And we don't
                        // know which legion is more important.  So just give
                        // a small bonus for possibly being able to summon out
                        // an angel and recruit.
                        double POSSIBLE_SUMMON_FACTOR = 0.1;
                        value += POSSIBLE_SUMMON_FACTOR
                            * getHintedRecruitmentValue(recruit, legion,
                                hintSectionUsed);
                    }
                }
                else
                {
                    LOGGER.severe("Bogus legion height "
                        + (legion).getHeight());
                }

                LOGGER.finest("--- if " + legion + " moves to " + hex
                    + " then recruit " + recruit.toString() + " (adding "
                    + (value - oldval) + ")");
            }
        }

        // consider what we might be able to recruit next turn, from here
        int nextTurnValue = 0;

        for (int roll = 1; roll <= 6; roll++)
        {
            // XXX Should ignore friends.
            Set<MasterHex> moves = client.getMovement().listAllMoves(legion,
                hex, roll);
            int bestRecruitVal = 0;

            for (MasterHex nextHex : moves)
            {
                // if we have to fight in that hex and we can't
                // WIN_WITH_MINIMAL_LOSSES, then assume we can't
                // recruit there.  IDEA: instead of doing any of this
                // work, perhaps we could recurse here to get the
                // value of being in _that_ hex next turn... and then
                // maximize over choices and average over die rolls.
                // this would be essentially minimax but ignoring the
                // others players ability to move.
                Legion enemy = client.getFirstEnemyLegion(nextHex, legion
                    .getPlayer());

                if (enemy != null
                    && estimateBattleResults(legion, enemy, nextHex) != WIN_WITH_MINIMAL_LOSSES)
                {
                    continue;
                }

                List<CreatureType> nextRecruits = client.findEligibleRecruits(
                    legion, nextHex);

                if (nextRecruits.size() == 0)
                {
                    continue;
                }

                CreatureType nextRecruit = nextRecruits.get(nextRecruits
                    .size() - 1);
                // Reduced val by 5 to make current turn recruits more
                // valuable than next turn's recruits
                int val = nextRecruit.getSkill() * nextRecruit.getPower() - 5;
                if (val > bestRecruitVal)
                {
                    bestRecruitVal = val;
                }
            }

            nextTurnValue += bestRecruitVal;
        }

        nextTurnValue /= 6; // 1/6 chance of each happening
        value += nextTurnValue;

        // consider risk of being attacked
        if (enemyAttackMap != null)
        {
            if (moved)
            {
                LOGGER.finest("considering risk of moving " + legion + " to "
                    + hex);
            }
            else
            {
                LOGGER.finest("considering risk of leaving " + legion + " in "
                    + hex);
            }

            Map<MasterHex, List<Legion>>[] enemiesThatCanAttackOnA = enemyAttackMap;
            int roll;

            for (roll = 1; roll <= 6; roll++)
            {
                List<Legion> enemies = enemiesThatCanAttackOnA[roll].get(hex);

                if (enemies == null)
                {
                    continue;
                }

                for (Legion enemy : enemies)
                {
                    final int result = estimateBattleResults(enemy, false,
                        legion, hex, recruit);

                    if (result == WIN_WITH_MINIMAL_LOSSES || result == DRAW
                        && (legion).hasTitan())
                    {
                        break;
                        // break on the lowest roll from which we can
                        // be attacked and killed
                    }
                }
            }

            // Ignore all fear of attack on turn 1.  Not perfect,
            // but a pretty good rule of thumb.
            if (roll < 7 && client.getTurnNumber() > 1)
            {
                final double chanceToAttack = (7.0 - roll) / 6.0;
                final double risk;

                if (legion.hasTitan())
                {
                    risk = LOSE_LEGION * chanceToAttack;
                }
                else
                {
                    risk = -legion.getPointValue() / 2 * chanceToAttack;
                }

                value += risk;
            }
        }

        // TODO: consider mobility.  e.g., penalty for suckdown
        // squares, bonus if next to tower or under the top
        // TODO: consider what we can attack next turn from here
        // TODO: consider nearness to our other legions
        // TODO: consider being a scooby snack (if so, everything
        // changes: we want to be in a location with bad mobility, we
        // want to be at risk of getting killed, etc)
        // TODO: consider risk of being scooby snacked (this might be inherent)
        // TODO: consider splitting up our good recruitment rolls
        // (i.e. if another legion has warbears under the top that
        // recruit on 1,3,5, and we have a behemoth with choice of 3/5
        // to jungle or 4/6 to jungle, prefer the 4/6 location).
        LOGGER.finest("EVAL " + legion + (moved ? " move to " : " stay in ")
            + hex + " = " + value);

        return value;
    }

    static final int WIN_WITH_MINIMAL_LOSSES = 0;
    static final int WIN_WITH_HEAVY_LOSSES = 1;
    static final int DRAW = 2;
    static final int LOSE_BUT_INFLICT_HEAVY_LOSSES = 3;
    static final int LOSE = 4;

    /* can be overloaded by subclass -> not final */

    static double RATIO_WIN_MINIMAL_LOSS = 1.30;
    static double RATIO_WIN_HEAVY_LOSS = 1.15;
    static double RATIO_DRAW = 0.85;
    static double RATIO_LOSE_HEAVY_LOSS = 0.70;

    private int estimateBattleResults(Legion attacker, Legion defender,
        MasterHex hex)
    {
        return estimateBattleResults(attacker, false, defender, hex, null);
    }

    private int estimateBattleResults(Legion attacker,
        boolean attackerSplitsBeforeBattle, Legion defender, MasterHex hex)
    {
        return estimateBattleResults(attacker, attackerSplitsBeforeBattle,
            defender, hex, null);
    }

    private int estimateBattleResults(Legion attacker,
        boolean attackerSplitsBeforeBattle, Legion defender, MasterHex hex,
        CreatureType recruit)
    {
        MasterBoardTerrain terrain = hex.getTerrain();
        double attackerPointValue = getCombatValue(attacker, terrain);

        if (attackerSplitsBeforeBattle)
        {
            // remove PV of the split
            List<CreatureType> creaturesToRemove = chooseCreaturesToSplitOut(attacker);
            for (CreatureType creature : creaturesToRemove)
            {
                attackerPointValue -= getCombatValue(creature, terrain);
            }
        }

        if (recruit != null)
        {
            // Log.debug("adding in recruited " + recruit +
            // " when evaluating battle");
            attackerPointValue += getCombatValue(recruit, terrain);
        }
        // TODO: add angel call

        double defenderPointValue = getCombatValue(defender, terrain);
        // TODO: add in enemy's most likely turn 4 recruit

        if (hex.getTerrain().isTower())
        {
            // defender in the tower!  ouch!
            defenderPointValue *= 1.2;
        }
        else if (hex.getTerrain().equals("Abyss")) // The Abyss, in variants
        {
            // defender in the abyss!  Kill!
            defenderPointValue *= 0.8;
        }

        // really dumb estimator
        double ratio = attackerPointValue / defenderPointValue;

        if (ratio >= RATIO_WIN_MINIMAL_LOSS)
        {
            return WIN_WITH_MINIMAL_LOSSES;
        }
        else if (ratio >= RATIO_WIN_HEAVY_LOSS)
        {
            return WIN_WITH_HEAVY_LOSSES;
        }
        else if (ratio >= RATIO_DRAW)
        {
            return DRAW;
        }
        else if (ratio >= RATIO_LOSE_HEAVY_LOSS)
        {
            return LOSE_BUT_INFLICT_HEAVY_LOSSES;
        }
        else
        // ratio less than 0.70
        {
            return LOSE;
        }
    }

    class SimpleAIOracle implements HintOracleInterface
    {
        LegionClientSide legion;
        MasterHex hex;
        List<CreatureType> recruits;
        Map<MasterHex, List<Legion>>[] enemyAttackMap = null;

        SimpleAIOracle(LegionClientSide legion, MasterHex hex,
            List<CreatureType> recruits2)
        {
            this.legion = legion;
            this.hex = hex;
            this.recruits = recruits2;

        }

        public boolean canReach(String terrain)
        {
            int now = getNumberOfWaysToTerrain(legion, hex, terrain);
            return (now > 0);
        }

        public int creatureAvailable(String name)
        {
            // TODO name doesn't seem to always refer to an actual creature
            //      type, which means the next line can return null, then
            //      causing an NPE in getReservedRemain(..)
            // Still TODO ?
            //      Fixed "Griffon vs. Griffin" in Undead, which was the
            //      reason in all cases I got that exception (Clemens).
            CreatureType type = client.getGame().getVariant()
                .getCreatureByName(name);
            int count = client.getReservedRemain(type);
            return count;
        }

        public boolean otherFriendlyStackHasCreature(List<String> allNames)
        {
            for (Legion other : client.getOwningPlayer().getLegions())
            {
                if (!(legion.equals(other)))
                {
                    boolean hasAll = true;

                    for (String name : allNames)
                    {
                        if (((LegionClientSide)other).numCreature(name) <= 0)
                        {
                            hasAll = false;
                        }
                    }
                    if (hasAll)
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean hasCreature(String name)
        {
            int num = legion.numCreature(name);
            return num > 0;
        }

        public boolean canRecruit(String name)
        {
            return recruits.contains(client.getGame().getVariant()
                .getCreatureByName(name));
        }

        public int stackHeight()
        {
            return legion.getHeight();
        }

        public String hexLabel()
        {
            return hex.getLabel();
        }

        public int biggestAttackerHeight()
        {
            if (enemyAttackMap == null)
            {
                enemyAttackMap = buildEnemyAttackMap(client.getOwningPlayer());
            }
            int worst = 0;
            for (int i = 1; i < 6; i++)
            {
                List<Legion> enemyList = enemyAttackMap[i].get(legion
                    .getCurrentHex());
                if (enemyList != null)
                {
                    for (Legion enemy : enemyList)
                    {
                        if ((enemy).getHeight() > worst)
                        {
                            worst = (enemy).getHeight();
                        }
                    }
                }
            }
            return worst;
        }
    }

    public CreatureType getVariantRecruitHint(LegionClientSide legion,
        MasterHex hex, List<CreatureType> recruits)
    {
        String recruitName = VariantSupport.getRecruitHint(hex.getTerrain(),
            legion, recruits, new SimpleAIOracle(legion, hex, recruits),
            hintSectionUsed);

        if (recruitName == null)
        {
            return recruits.get(recruits.size() - 1);
        }
        if ((recruitName.equals("nothing")) || (recruitName.equals("Nothing")))
        {
            // suggest recruiting nothing
            return null;
        }

        CreatureType recruit = client.getGame().getVariant()
            .getCreatureByName(recruitName);
        if (!(recruits.contains(recruit)))
        {
            LOGGER
                .warning("HINT: Invalid Hint for this variant ! (can't recruit "
                    + recruitName
                    + "; recruits="
                    + recruits.toString()
                    + ") in " + hex.getTerrain());
            return (recruits.get(recruits.size() - 1));
        }
        return recruit;
    }

    int getNumberOfWaysToTerrain(LegionClientSide legion, MasterHex hex,
        String terrainType)
    {
        int total = 0;
        for (int roll = 1; roll <= 6; roll++)
        {
            Set<MasterHex> set = client.getMovement().listAllMoves(legion,
                hex, roll, true);
            if (setContainsHexWithTerrain(set, terrainType))
            {
                total++;
            }
        }
        return total;
    }

    private boolean setContainsHexWithTerrain(Set<MasterHex> set,
        String terrainType)
    {
        for (MasterHex hex : set)
        {
            if (hex.getTerrain().equals(terrainType))
            {
                return true;
            }
        }
        return false;
    }

    // This is a really dumb placeholder.  TODO Make it smarter.
    // In particular, the AI should pick a side that will let it enter
    // as many creatures as possible.
    public String pickEntrySide(MasterHex hex, Legion legion,
        Set<String> entrySides)
    {
        // Default to bottom to simplify towers.
        if (entrySides.contains(Constants.bottom))
        {
            return Constants.bottom;
        }
        if (entrySides.contains(Constants.right))
        {
            return Constants.right;
        }
        if (entrySides.contains(Constants.left))
        {
            return Constants.left;
        }
        return null;
    }

    public MasterHex pickEngagement()
    {
        Set<MasterHex> hexes = client.findEngagements();

        // Bail out early if we have no real choice.
        int numChoices = hexes.size();
        if (numChoices == 0)
        {
            return null;
        }
        if (numChoices == 1)
        {
            return hexes.iterator().next();
        }

        MasterHex bestChoice = null;
        int bestScore = Integer.MIN_VALUE;

        for (MasterHex hex : hexes)
        {
            int score = evaluateEngagement(hex);
            if (score > bestScore)
            {
                bestScore = score;
                bestChoice = hex;
            }
        }
        return bestChoice;
    }

    private int evaluateEngagement(MasterHex hex)
    {
        // Fight losing battles last, so that we don't give away
        //    points while they may be used against us this turn.
        // Fight battles with angels first, so that those angels
        //    can be summoned out.
        // Try not to lose potential angels and recruits by having
        //    scooby snacks flee to 7-high stacks (or 6-high stacks
        //    that could recruit later this turn) and push them
        //    over 100-point boundaries.

        Player player = client.getActivePlayer();
        Legion attacker = client.getFirstFriendlyLegion(hex, player);
        Legion defender = client.getFirstEnemyLegion(hex, player);
        int value = 0;

        final int result = estimateBattleResults(attacker, defender, hex);

        // The worse we expect to do, the more we want to put off this
        // engagement, either to avoid strengthening an enemy titan that
        // we may fight later this turn, or to increase our chances of
        // being able to call an angel.
        value -= result;

        // Avoid losing angels and recruits.
        boolean wouldFlee = flee(defender, attacker);
        if (wouldFlee)
        {
            int currentScore = ((PlayerClientSide)player).getScore();
            int fleeValue = ((LegionClientSide)defender).getPointValue() / 2;
            if (((currentScore + fleeValue) / TerrainRecruitLoader
                .getAcquirableRecruitmentsValue()) > (currentScore / TerrainRecruitLoader
                .getAcquirableRecruitmentsValue()))
            {
                if ((attacker).getHeight() == 7 || (attacker).getHeight() == 6
                    && ((LegionClientSide)attacker).canRecruit())
                {
                    value -= 10;
                }
                else
                {
                    // Angels go best in Titan legions.
                    if ((attacker).hasTitan())
                    {
                        value += 6;
                    }
                    else
                    {
                        // A bird in the hand...
                        value += 2;
                    }
                }
            }
        }

        // Fight early with angel legions, so that others can summon.
        if (result <= WIN_WITH_HEAVY_LOSSES
            && ((LegionClientSide)attacker).hasSummonable())
        {
            value += 5;
        }

        return value;
    }

    public boolean flee(Legion legion, Legion enemy)
    {
        if ((legion).hasTitan())
        {
            return false;
        } // Titan never flee !

        int result = estimateBattleResults(enemy, legion, legion
            .getCurrentHex());
        switch (result)
        {
            case WIN_WITH_HEAVY_LOSSES:
            case DRAW:
            case LOSE_BUT_INFLICT_HEAVY_LOSSES:
            case LOSE:
                LOGGER.finest("Legion " + legion.getMarkerId()
                    + " doesn't flee " + " before " + enemy.getMarkerId()
                    + " with result " + result + " ("
                    + ((LegionClientSide)legion).getPointValue() + " vs. "
                    + ((LegionClientSide)enemy).getPointValue() + " in "
                    + legion.getCurrentHex().getTerrainName() + ")");
                return false;

            case WIN_WITH_MINIMAL_LOSSES:
                // don't bother unless we can try to weaken the titan stack
                // and we aren't going to help him by removing cruft
                // also, 7-height stack never flee and wimpy stack always flee
                if ((legion).getHeight() < 6)
                {
                    LOGGER.finest("Legion " + legion.getMarkerId() + " flee "
                        + " as they are just " + (legion).getHeight()
                        + " wimps !");
                    return true;
                }
                if ((((LegionClientSide)enemy).getPointValue() * 0.5) > ((LegionClientSide)legion)
                    .getPointValue())
                {
                    LOGGER.finest("Legion " + legion.getMarkerId() + " flee "
                        + " as they are less than half as strong as "
                        + enemy.getMarkerId());
                    return true;
                }
                if ((enemy).getHeight() == 7)
                {
                    List<CreatureType> recruits = client.findEligibleRecruits(
                        enemy, legion.getCurrentHex());
                    if (recruits.size() > 0)
                    {
                        CreatureType best = recruits.get(recruits.size() - 1);
                        int lValue = ((LegionClientSide)enemy).getPointValue();
                        if (best.getPointValue() > (lValue / (enemy)
                            .getHeight()))
                        {
                            LOGGER.finest("Legion " + legion + " flee "
                                + " to prevent " + enemy
                                + " to be able to recruit " + best);
                            return true;
                        }
                    }
                }
                if ((enemy).hasTitan())
                {
                    LOGGER.finest("Legion " + legion.getMarkerId()
                        + " doesn't flee " + " to fight the Titan in "
                        + enemy.getMarkerId());
                }
                if ((legion).getHeight() == 7)
                {
                    LOGGER.finest("Legion " + legion.getMarkerId()
                        + " doesn't flee " + " they are the magnificent 7 !");
                }
                return !((enemy).hasTitan() || ((legion).getHeight() == 7));
        }
        return false;
    }

    public boolean concede(Legion legion, Legion enemy)
    {
        // Never concede titan legion.
        if ((legion).hasTitan())
        {
            return false;
        }

        // Wimpy legions should concede if it costs the enemy an
        // angel or good recruit.
        MasterBoardTerrain terrain = legion.getCurrentHex().getTerrain();
        int height = (enemy).getHeight();
        if (getCombatValue(legion, terrain) < 0.5 * getCombatValue(enemy,
            terrain)
            && height >= 6)
        {
            int currentScore = ((PlayerClientSide)enemy.getPlayer())
                .getScore();
            int pointValue = ((LegionClientSide)legion).getPointValue();
            boolean canAcquireAngel = ((currentScore + pointValue)
                / TerrainRecruitLoader.getAcquirableRecruitmentsValue() > (currentScore / TerrainRecruitLoader
                .getAcquirableRecruitmentsValue()));
            // Can't use Legion.getRecruit() because it checks for
            // 7-high legions.
            boolean canRecruit = !client.findEligibleRecruits(enemy,
                enemy.getCurrentHex()).isEmpty();
            if (height == 7 && (canAcquireAngel || canRecruit))
            {
                return true;
            }
            if (canAcquireAngel && canRecruit) // know height == 6
            {
                return true;
            }
        }
        return false;
    }

    // should be correct for most variants.
    public String acquireAngel(Legion legion, List<String> angels)
    {
        // TODO If the legion is a tiny scooby snack that's about to get
        // smooshed, turn down the angel.

        CreatureType bestAngel = getBestCreature(angels);
        if (bestAngel == null)
        {
            return null;
        }

        // Don't take an angel if 6 high and a better recruit is available.
        // TODO Make this also work for post-battle reinforcements
        if ((legion).getHeight() == 6
            && ((LegionClientSide)legion).canRecruit())
        {
            List<CreatureType> recruits = client.findEligibleRecruits(legion,
                legion.getCurrentHex());
            CreatureType bestRecruit = recruits.get(recruits.size() - 1);
            if (getKillValue(bestRecruit) > getKillValue(bestAngel))
            {
                LOGGER.finest("AI declines acquiring to recruit "
                    + bestRecruit.getName());
                return null;
            }
        }
        return bestAngel.getName();
    }

    /** Return the most important Creature in the list of Creatures or
     * creature name strings.. */
    CreatureType getBestCreature(List<String> creatures)
    {
        if (creatures == null || creatures.isEmpty())
        {
            return null;
        }
        CreatureType best = null;
        for (String creatureName : creatures)
        {
            CreatureType creature = client.getGame().getVariant()
                .getCreatureByName(creatureName);
            if (best == null || getKillValue(creature) > getKillValue(best))
            {
                best = creature;
            }
        }
        return best;
    }

    /** Return a string of form angeltype:donorId, or null. */
    public String summonAngel(Legion summoner)
    {
        // Always summon the biggest possible angel, from the least
        // important legion that has one.
        //
        // TODO Sometimes leave room for recruiting.

        Set<MasterHex> hexes = client.findSummonableAngelHexes(summoner);

        LegionClientSide bestLegion = null;
        String bestAngel = null;

        for (MasterHex hex : hexes)
        {
            List<LegionClientSide> legions = client.getLegionsByHex(hex);
            if (legions.size() != 1)
            {
                LOGGER.severe("SimpleAI.summonAngel(): Engagement in " + hex);
                continue;
            }
            LegionClientSide legion = legions.get(0);
            String myAngel = legion.bestSummonable();
            if (myAngel == null)
            {
                LOGGER.severe("SimpleAI.summonAngel(): No angel in " + legion);
                continue;
            }

            if (bestAngel == null
                || bestLegion == null
                || (client.getGame().getVariant().getCreatureByName(myAngel))
                    .getPointValue() > (client.getGame().getVariant()
                    .getCreatureByName(bestAngel)).getPointValue()
                || legion.compareTo(bestLegion) > 0
                && ((client.getGame().getVariant().getCreatureByName(myAngel))
                    .getPointValue() == (client.getGame().getVariant()
                    .getCreatureByName(bestAngel)).getPointValue()))
            {
                bestLegion = legion;
                bestAngel = myAngel;
            }
        }
        return bestAngel == null || bestLegion == null ? null : (bestAngel
            + ":" + bestLegion.getMarkerId());
    }

    /** Create a map containing each target and the number of hits it would
     *  likely take if all possible creatures attacked it. */
    private Map<BattleChit, Double> generateDamageMap()
    {
        Map<BattleChit, Double> map = new HashMap<BattleChit, Double>();
        for (BattleChit critter : client.getActiveBattleChits())
        {
            // Offboard critters can't strike.
            if (critter.getCurrentHexLabel().startsWith("X"))
            {
                continue;
            }
            Set<String> set = client.findStrikes(critter.getTag());
            for (String hexLabel : set)
            {
                BattleChit target = client.getBattleChit(hexLabel);
                int dice = client.getStrike().getDice(critter, target);
                int strikeNumber = client.getStrike().getStrikeNumber(critter,
                    target);
                double h = Probs.meanHits(dice, strikeNumber);

                if (map.containsKey(target))
                {
                    double d = map.get(target).doubleValue();
                    h += d;
                }

                map.put(target, new Double(h));
            }
        }
        return map;
    }

    private BattleChit findBestTarget()
    {
        BattleChit bestTarget = null;
        MasterBoardTerrain terrain = client.getBattleSite().getTerrain();

        // Create a map containing each target and the likely number
        // of hits it would take if all possible creatures attacked it.
        Map<BattleChit, Double> map = generateDamageMap();

        for (BattleChit target : map.keySet())
        {
            double h = map.get(target).doubleValue();

            if (h + target.getHits() >= target.getPower())
            {
                // We can probably kill this target.
                if (bestTarget == null
                    || getKillValue(target, terrain) > getKillValue(
                        bestTarget, terrain))
                {
                    bestTarget = target;
                }
            }
            else
            {
                // We probably can't kill this target.
                // But if it is a Titan it may be more valuable to do fractional damage
                if (bestTarget == null
                    || (0.5 * ((h + target.getHits()) / target.getPower())
                        * getKillValue(target, terrain) > getKillValue(
                        bestTarget, terrain)))
                {
                    bestTarget = target;
                }
            }
        }
        return bestTarget;
    }

    // TODO Have this actually find the best one, not the first one.
    private BattleChit findBestAttacker(BattleChit target)
    {
        for (BattleChit critter : client.getActiveBattleChits())
        {
            if (client.getStrike().canStrike(critter, target))
            {
                return critter;
            }
        }
        return null;
    }

    /** Apply carries first to the biggest creature that could be killed
     *  with them, then to the biggest creature.  carryTargets are
     *  hexLabel description strings. */
    public void handleCarries(int carryDamage, Set<String> carryTargets)
    {
        MasterBoardTerrain terrain = client.getBattleSite().getTerrain();
        BattleChit bestTarget = null;

        for (String desc : carryTargets)
        {
            String targetHexLabel = desc.substring(desc.length() - 2);
            BattleChit target = client.getBattleChit(targetHexLabel);

            if (target.wouldDieFrom(carryDamage))
            {
                if (bestTarget == null
                    || !bestTarget.wouldDieFrom(carryDamage)
                    || getKillValue(target, terrain) > getKillValue(
                        bestTarget, terrain))
                {
                    bestTarget = target;
                }
            }
            else
            {
                if (bestTarget == null
                    || (!bestTarget.wouldDieFrom(carryDamage) && getKillValue(
                        target, terrain) > getKillValue(bestTarget, terrain)))
                {
                    bestTarget = target;
                }
            }
        }
        if (bestTarget == null)
        {
            LOGGER.warning("No carry target but " + carryDamage
                + " points of available carry damage");
            client.leaveCarryMode();
        }
        else
        {
            LOGGER.finest("Best carry target is "
                + bestTarget.getDescription());
            client.applyCarries(bestTarget.getCurrentHexLabel());
        }
    }

    /** Pick one of the list of String strike penalty options. */
    public String pickStrikePenalty(List<String> choices)
    {
        // XXX Stupid placeholder.
        return choices.get(choices.size() - 1);
    }

    /** Simple one-ply group strike algorithm.  Return false if there were
     *  no strike targets. */
    public boolean strike(Legion legion)
    {
        LOGGER.finest("Called ai.strike() for " + legion);
        // PRE: Caller handles forced strikes before calling this.

        // Pick the most important target that can likely be killed this
        // turn.  If none can, pick the most important target.
        // TODO If none can, and we're going to lose the battle this turn,
        // pick the easiest target to kill.

        BattleChit bestTarget = findBestTarget();
        if (bestTarget == null)
        {
            LOGGER.finest("Best target is null, aborting");
            return false;
        }
        LOGGER.finest("Best target is " + bestTarget.getDescription());

        // Having found the target, pick an attacker.  The
        // first priority is finding one that does not need
        // to worry about carry penalties to hit this target.
        // The second priority is using the weakest attacker,
        // so that more information is available when the
        // stronger attackers strike.

        BattleChit bestAttacker = findBestAttacker(bestTarget);
        if (bestAttacker == null)
        {
            return false;
        }

        LOGGER.finest("Best attacker is " + bestAttacker.getDescription());

        // Having found the target and attacker, strike.
        // Take a carry penalty if there is still a 95%
        // chance of killing this target.
        client.strike(bestAttacker.getTag(), bestTarget.getCurrentHexLabel());
        return true;
    }

    static int getCombatValue(BattleChit chit, MasterBoardTerrain terrain)
    {
        int val = chit.getPointValue();
        CreatureType creature = chit.getCreature();

        if (creature.isFlier())
        {
            val++;
        }

        if (creature.isRangestriker())
        {
            val++;
        }

        if (terrain.hasNativeCombatBonus(creature))
        {
            val++;
        }

        return val;
    }

    /** XXX Inaccurate for titans. */
    int getCombatValue(CreatureType creature, MasterBoardTerrain terrain)
    {
        if (creature.isTitan())
        {
            // Don't know the power, so just estimate.
            LOGGER.warning("Called SimpleAI.getCombatValue() for Titan");
            return 6 * client.getGame().getVariant()
                .getCreatureByName("Titan").getSkill();
        }

        int val = (creature).getPointValue();

        if ((creature).isFlier())
        {
            val++;
        }

        if ((creature).isRangestriker())
        {
            val++;
        }

        if (terrain.hasNativeCombatBonus(creature))
        {
            val++;
        }

        return val;
    }

    int getTitanCombatValue(int power)
    {
        int val = power
            * client.getGame().getVariant().getCreatureByName("Titan")
                .getSkill();
        if (power < 9)
        {
            val -= (6 + 2 * (9 - power));
        }
        return val;
    }

    int getCombatValue(Legion legion, MasterBoardTerrain terrain)
    {
        int val = 0;
        for (String name : ((LegionClientSide)legion).getContents())
        {
            if (name.startsWith(Constants.titan))
            {
                val += getTitanCombatValue(((PlayerClientSide)legion
                    .getPlayer()).getTitanPower());
            }
            else
            {
                CreatureType creature = client.getGame().getVariant()
                    .getCreatureByName(name);
                val += getCombatValue(creature, terrain);
            }
        }

        return val;
    }

    static int getKillValue(final CreatureType creature)
    {
        return getKillValue(creature, null);
    }

    // XXX titan power
    static int getKillValue(final BattleChit chit,
        final MasterBoardTerrain terrain)
    {
        return getKillValue(chit.getCreature(), terrain);
    }

    private static int getKillValue(final CreatureType creature,
        MasterBoardTerrain terrain)
    {
        int val;
        if (creature == null)
        {
            LOGGER.warning("Called getKillValue with null creature");
            return 0;
        }
        // get non-terrain modified part of kill value
        val = creature.getKillValue();
        // modify with terrain
        if (terrain != null && terrain.hasNativeCombatBonus(creature))
        {
            val += 3;
        }
        return val;
    }

    class PowerSkill
    {
        private final String name;
        private final int power_attack;
        private final int power_defend; // how many dice attackers lose
        private final int skill_attack;
        private final int skill_defend;
        private double hp; // how many hit points or power left
        private final double value;

        public PowerSkill(String nm, int p, int pa, int pd, int sa, int sd)
        {
            name = nm;
            power_attack = pa;
            power_defend = pd;
            skill_attack = sa;
            skill_defend = sd;
            hp = p; // may not be the same as power_attack!
            value = p * Math.min(sa, sd);
        }

        public PowerSkill(String nm, int pa, int sa)
        {
            this(nm, pa, pa, 0, sa, sa);
        }

        public int getPowerAttack()
        {
            return power_attack;
        }

        public int getPowerDefend()
        {
            return power_defend;
        }

        public int getSkillAttack()
        {
            return skill_attack;
        }

        public int getSkillDefend()
        {
            return skill_defend;
        }

        public double getHP()
        {
            return hp;
        }

        public void setHP(double h)
        {
            hp = h;
        }

        public void addDamage(double d)
        {
            hp -= d;
        }

        public double getPointValue()
        {
            return value;
        }

        public String getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return name + "(" + hp + ")";
        }
    }

    // return power and skill of a given creature given the terrain
    // terrain here is either a board hex label OR
    // a Hex terrain label
    // TODO this either or is dangerous and forces us to use the label
    //      instead of the objects
    private PowerSkill calc_bonus(CreatureType creature, String terrain,
        boolean defender)
    {
        int power = creature.getPower();
        int skill = creature.getSkill();

        TerrainBonuses bonuses = TERRAIN_BONUSES.get(terrain);
        if (bonuses == null)
        {
            // terrain has no special bonuses
            return new PowerSkill(creature.getName(), power, skill);
        }
        else if (terrain.equals("Tower") && defender == false)
        {
            // no attacker bonus for tower
            return new PowerSkill(creature.getName(), power, skill);
        }
        else if ((terrain.equals("Mountains") || terrain.equals("Volcano"))
            && defender == true && creature.getName().equals("Dragon"))
        {
            // Dragon gets an extra 3 die when attack down slope
            // non-native loses 1 skill when attacking up slope
            return new PowerSkill(creature.getName(), power, power + 3,
                bonuses.defenderPower, skill + bonuses.attackerSkill, skill
                    + bonuses.defenderSkill);
        }
        else
        {
            return new PowerSkill(creature.getName(), power, power
                + bonuses.attackerPower, bonuses.defenderPower, skill
                + bonuses.attackerSkill, skill + bonuses.defenderSkill);
        }
    }

    // return power and skill of a given creature given the terrain
    // board hex label 
    protected PowerSkill getNativeValue(CreatureType creature,
        MasterBoardTerrain terrain, boolean defender)
    {
        // TODO checking the tower via string is unsafe -- maybe terrain.isTower()
        //      is meant anyway
        if (!(terrain.hasNativeCombatBonus(creature) || (terrain.getId()
            .equals("Tower") && defender == true)))
        {
            return new PowerSkill(creature.getName(), creature.getPower(),
                creature.getSkill());
        }

        return calc_bonus(creature, terrain.getId(), defender);

    }

    // return power and skill of a given creature given 
    // a hazard terrain
    protected PowerSkill getNativeTerrainValue(CreatureType creature,
        HazardTerrain terrain, boolean defender)
    {
        return calc_bonus(creature, terrain.getName(), defender);
    }

    ////////////////////////////////////////////////////////////////
    // Battle move stuff
    ////////////////////////////////////////////////////////////////

    /*
     Battles are 2-player games within the multiplayer titan game.
     They must be evaluated within that context.  So not all
     winning positions are equally good, and not all losing
     positions are equally bad, since the surviving contents of
     the winning stack matter. All results that kill the last
     enemy titan while leaving ours alive are equally good, though,
     and all results that get our titan killed are equally bad.

     We can greatly simplify analysis by assuming that every strike
     will score the average number of hits.  This may or may not
     be good enough.  In particular, exposing a titan in a situation
     where a slightly above average number of hits will kill it is
     probably unwise, so we need to hack in some extra caution for
     titans.

     There are 27 hexes on each battle map.  A fast creature starting
     near the middle of the map can move to all of them, terrain and
     other creatures permitting.  So the possible number of different
     positions after one move is huge.  So we can't really iterate over
     all possible moves.  We need to consider one creature at a time.
     But we also need to use team tactics.

     When finding all possible moves, we need to take into account
     that friendly creatures can block one another's moves.  That
     gets really complex, so instead assume that they don't when
     computing all possible moves, and then try to ensure that
     less important creatures get out of the way of more important
     ones.
     */

    /** Return a list of critter moves, in best move order. */
    public List<CritterMove> battleMove()
    {
        LOGGER.finest("Called battleMove()");

        // Defer setting time limit until here where it's needed, to
        // avoid initialization timing issues.
        timeLimit = client.getOptions().getIntOption(Options.aiTimeLimit);

        // Consider one critter at a time, in order of importance.
        // Examine all possible moves for that critter not already
        // taken by a more important one.

        // TODO Handle summoned/recruited critters, in particular
        // getting stuff out of the way so that a reinforcement
        // has room to enter.

        List<LegionMove> legionMoves = findBattleMoves();
        LegionMove bestLegionMove = findBestLegionMove(legionMoves);
        List<CritterMove> bestMoveOrder = findMoveOrder(bestLegionMove);

        return bestMoveOrder;
    }

    /** Try another move for creatures whose moves failed. */
    public void retryFailedBattleMoves(List<CritterMove> bestMoveOrder)
    {
        if (bestMoveOrder == null)
        {
            return;
        }
        for (CritterMove cm : bestMoveOrder)
        {
            BattleChit critter = cm.getCritter();
            String startingHexLabel = cm.getStartingHexLabel();

            LOGGER.finest(critter.getDescription() + " failed to move");
            List<CritterMove> moveList = findBattleMovesOneCritter(critter);
            if (!moveList.isEmpty())
            {
                CritterMove cm2 = moveList.get(0);
                LOGGER.finest("Moving " + critter.getDescription() + " to "
                    + cm2.getEndingHexLabel() + " (startingHexLabel was "
                    + startingHexLabel + ")");
                client.tryBattleMove(cm2);
            }
        }
    }

    List<CritterMove> findMoveOrder(LegionMove lm)
    {
        if (lm == null)
        {
            return null;
        }

        int perfectScore = 0;

        ArrayList<CritterMove> critterMoves = new ArrayList<CritterMove>();
        critterMoves.addAll(lm.getCritterMoves());

        Iterator<CritterMove> itCrit = critterMoves.iterator();
        while (itCrit.hasNext())
        {
            CritterMove cm = itCrit.next();
            if (cm.getStartingHexLabel().equals(cm.getEndingHexLabel()))
            {
                // Prune non-movers
                itCrit.remove();
            }
            else
            {
                perfectScore += cm.getCritter().getPointValue();
            }
        }

        if (perfectScore == 0)
        {
            // No moves, so exit.
            return null;
        }

        // Figure the order in which creatures should move to get in
        // each other's way as little as possible.
        // Iterate through all permutations of critter move orders,
        // tracking how many critters get their preferred hex with each
        // order, until we find an order that lets every creature reach
        // its preferred hex.  If none does, take the best we can find.

        int bestScore = 0;
        List<CritterMove> bestOrder = null;
        boolean bestAllOK = false;

        int count = 0;
        Timer findMoveTimer = setupTimer();

        Iterator<List<CritterMove>> it = new PermutationIterator<CritterMove>(
            critterMoves);
        while (it.hasNext())
        {
            List<CritterMove> order = it.next();

            count++;

            boolean allOK = true;
            int score = testMoveOrder(order, null);
            if (score < 0)
            {
                allOK = false;
                score = -score;
            }
            if (score > bestScore)
            {
                bestOrder = new ArrayList<CritterMove>(order);
                bestScore = score;
                bestAllOK = allOK;
                if (score >= perfectScore)
                {
                    break;
                }
            }

            // Bail out early, if there is at least some valid move.
            if (timeIsUp)
            {
                if (bestScore > 0)
                {
                    break;
                }
                else
                {
                    LOGGER
                        .warning("Time is up figuring move order, but we ignore "
                            + "it (no valid moveOrder found yet... "
                            + " - buggy break)");
                    timeIsUp = false;
                }
            }
        }
        findMoveTimer.cancel();
        if (!bestAllOK)
        {
            ArrayList<CritterMove> newOrder = new ArrayList<CritterMove>();
            testMoveOrder(bestOrder, newOrder);
            bestOrder = new ArrayList<CritterMove>(newOrder);
        }
        LOGGER.finest("Got score " + bestScore + " in " + count
            + " permutations");
        return bestOrder;
    }

    /** Try each of the moves in order.  Return the number that succeed,
     *  scaled by the importance of each critter. 
     *  In newOrder, if not null, place the moves that are valid.   
     */
    private int testMoveOrder(List<CritterMove> order,
        List<CritterMove> newOrder)
    {
        boolean allOK = true;
        int val = 0;
        for (CritterMove cm : order)
        {
            BattleChit critter = cm.getCritter();
            String hexLabel = cm.getEndingHexLabel();
            if (client.testBattleMove(critter, hexLabel))
            {
                // XXX Use kill value instead?
                val += critter.getPointValue();
                if (newOrder != null)
                {
                    newOrder.add(cm);
                }
            }
            else
            {
                allOK = false;
            }
        }

        // Move them all back where they started.
        for (CritterMove cm : order)
        {
            BattleChit critter = cm.getCritter();
            String hexLabel = cm.getStartingHexLabel();
            critter.setHexLabel(hexLabel);
        }

        if (!allOK)
        {
            val = -val;
        }
        return val;
    }

    private final int MAX_LEGION_MOVES = 10000;

    /** Find the maximum number of moves per creature to test, such that
     *  numMobileCreaturesInLegion ^ N <= LEGION_MOVE_LIMIT, but we must
     *  have at least as many moves as mobile creatures to ensure that
     *  every creature has somewhere to go. */
    int getCreatureMoveLimit()
    {
        int mobileCritters = client.findMobileBattleChits().size();
        if (mobileCritters <= 1)
        {
            // Avoid infinite logs and division by zero, and just try
            // all possible moves.
            return Constants.BIGNUM;
        }
        int max = (int)Math.floor(Math.log(MAX_LEGION_MOVES)
            / Math.log(mobileCritters));
        return (Math.min(max, mobileCritters));
    }

    List<LegionMove> findBattleMoves()
    {
        LOGGER.finest("Called findBattleMoves()");

        // Consider one critter at a time in isolation.
        // Find the best N moves for each critter.

        // TODO Do not consider immobile critters.  Also, do not allow
        // non-flying creatures to move through their hexes.

        // TODO Handle summoned/recruited critters, in particular
        // getting stuff out of the way so that a reinforcement
        // has room to enter.

        // The caller is responsible for actually making the moves.

        // allCritterMoves is an ArrayList (for clone()) of moveLists.
        final ArrayList<List<CritterMove>> allCritterMoves = new ArrayList<List<CritterMove>>();

        for (BattleChit critter : client.getActiveBattleChits())
        {
            List<CritterMove> moveList = findBattleMovesOneCritter(critter);

            // Add this critter's moves to the list.
            allCritterMoves.add(moveList);

            // Put all critters back where they started.
            Iterator<List<CritterMove>> it2 = allCritterMoves.iterator();
            while (it2.hasNext())
            {
                moveList = it2.next();
                CritterMove cm = moveList.get(0);
                BattleChit critter2 = cm.getCritter();
                critter2.moveToHex(cm.getStartingHexLabel());
            }
        }

        List<LegionMove> legionMoves = findLegionMoves(allCritterMoves);
        return legionMoves;
    }

    private List<CritterMove> findBattleMovesOneCritter(BattleChit critter)
    {
        String currentHexLabel = critter.getCurrentHexLabel();

        // moves is a list of hex labels where one critter can move.

        // Sometimes friendly critters need to get out of the way to
        // clear a path for a more important critter.  We consider
        // moves that the critter could make, disregarding mobile allies.

        // XXX Should show moves including moving through mobile allies.
        Set<String> moves = client.showBattleMoves(critter.getTag());

        // TODO Make less important creatures get out of the way.

        // Not moving is also an option.
        moves.add(currentHexLabel);

        List<CritterMove> moveList = new ArrayList<CritterMove>();

        for (String hexLabel : moves)
        {
            CritterMove cm = new CritterMove(critter, currentHexLabel,
                hexLabel);

            // Need to move the critter to evaluate.
            critter.moveToHex(hexLabel);

            // Compute and save the value for each CritterMove.
            cm.setValue(evaluateCritterMove(critter, null));
            moveList.add(cm);
            // Move the critter back where it started.
            critter.moveToHex(critter.getStartingHexLabel());
        }

        // Sort critter moves in descending order of score.
        Collections.sort(moveList, new Comparator<CritterMove>()
        {
            public int compare(CritterMove cm1, CritterMove cm2)
            {
                return cm2.getValue() - cm1.getValue();
            }
        });

        // Show the moves considered.
        StringBuilder buf = new StringBuilder("Considered " + moveList.size()
            + " moves for " + critter.getTag() + " "
            + critter.getCreatureName() + " in " + currentHexLabel + ":");
        for (CritterMove cm : moveList)
        {
            buf.append(" " + cm.getEndingHexLabel());
        }
        LOGGER.finest(buf.toString());

        return moveList;
    }

    Timer setupTimer()
    {
        // java.util.Timer, not Swing Timer
        Timer timer = new Timer();
        timeIsUp = false;
        final int MS_PER_S = 1000;
        if (timeLimit < Constants.MIN_AI_TIME_LIMIT
            || timeLimit > Constants.MAX_AI_TIME_LIMIT)
        {
            timeLimit = Constants.DEFAULT_AI_TIME_LIMIT;
        }
        timer.schedule(new TriggerTimeIsUp(), MS_PER_S * timeLimit);
        return timer;
    }

    private final static int MIN_ITERATIONS = 50;

    /** Evaluate all legion moves in the list, and return the best one.
     *  Break out early if the time limit is exceeded. */
    LegionMove findBestLegionMove(List<LegionMove> legionMoves)
    {
        int bestScore = Integer.MIN_VALUE;
        LegionMove best = null;

        Collections.shuffle(legionMoves, random);

        Timer findBestLegionMoveTimer = setupTimer();

        int count = 0;
        for (LegionMove lm : legionMoves)
        {
            int score = evaluateLegionBattleMove(lm);
            if (score > bestScore)
            {
                bestScore = score;
                best = lm;
            }

            count++;

            if (timeIsUp)
            {
                if (count >= MIN_ITERATIONS)
                {
                    LOGGER.finest("findBestLegionMove() time up after "
                        + count + " iterations");
                    break;
                }
                else
                {
                    LOGGER.finest("findBestLegionMove() time up after "
                        + count + " iterations, but we keep searching until "
                        + MIN_ITERATIONS);
                }
            }
        }
        findBestLegionMoveTimer.cancel();
        LOGGER.finest("Best legion move: "
            + ((best == null) ? "none " : best.toString()) + " (" + bestScore
            + ")");
        return best;
    }

    /** allCritterMoves is a List of sorted MoveLists.  A MoveList is a
     *  sorted List of CritterMoves for one critter.  Return a sorted List
     *  of LegionMoves.  A LegionMove is a List of one CritterMove per
     *  mobile critter in the legion, where no two critters move to the
     *  same hex. */
    List<LegionMove> findLegionMoves(
        final List<List<CritterMove>> allCritterMoves)
    {
        List<List<CritterMove>> critterMoves = new ArrayList<List<CritterMove>>(
            allCritterMoves);
        while (trimCritterMoves(critterMoves))
        {// Just trimming
        }

        // Now that the list is as small as possible, start finding combos.
        List<LegionMove> legionMoves = new ArrayList<LegionMove>();
        int[] indexes = new int[critterMoves.size()];

        nestForLoop(indexes, indexes.length - 1, critterMoves, legionMoves);

        LOGGER.finest("Got " + legionMoves.size() + " legion moves");
        return legionMoves;
    }

    private final Set<String> duplicateHexChecker = new HashSet<String>();

    private void nestForLoop(int[] indexes, final int level,
        final List<List<CritterMove>> critterMoves,
        List<LegionMove> legionMoves)
    {
        // TODO See if doing the set test at every level is faster than
        // always going down to level 0 then checking.
        if (level == 0)
        {
            duplicateHexChecker.clear();
            boolean offboard = false;
            for (int j = 0; j < indexes.length; j++)
            {
                List<CritterMove> moveList = critterMoves.get(j);
                if (indexes[j] >= moveList.size())
                {
                    return;
                }
                CritterMove cm = moveList.get(indexes[j]);
                String endingHexLabel = cm.getEndingHexLabel();
                if (endingHexLabel.startsWith("X"))
                {
                    offboard = true;
                }
                else if (duplicateHexChecker.contains(endingHexLabel))
                {
                    // Need to allow duplicate offboard moves, in case 2 or
                    // more creatures cannot enter.
                    return;
                }
                duplicateHexChecker.add(cm.getEndingHexLabel());
            }

            LegionMove lm = makeLegionMove(indexes, critterMoves);
            // Put offboard moves last, so they'll be skipped if the AI
            // runs out of time.
            if (offboard)
            {
                legionMoves.add(lm);
            }
            else
            {
                legionMoves.add(0, lm);
            }
        }
        else
        {
            for (int i = 0; i < indexes.length; i++)
            {
                indexes[level] = i;
                nestForLoop(indexes, level - 1, critterMoves, legionMoves);
            }
        }
    }

    private LegionMove makeLegionMove(int[] indexes,
        List<List<CritterMove>> critterMoves)
    {
        LegionMove lm = new LegionMove();
        for (int i = 0; i < indexes.length; i++)
        {
            List<CritterMove> moveList = critterMoves.get(i);
            CritterMove cm = moveList.get(indexes[i]);
            lm.add(cm);
        }
        return lm;
    }

    /** Modify allCritterMoves in place, and return true if it changed. */
    boolean trimCritterMoves(List<List<CritterMove>> allCritterMoves)
    {
        Set<String> takenHexLabels = new HashSet<String>(); // XXX reuse?
        boolean changed = false;

        // First trim immobile creatures from the list, and add their
        // hexes to takenHexLabels.
        Iterator<List<CritterMove>> it = allCritterMoves.iterator();
        while (it.hasNext())
        {
            List<CritterMove> moveList = it.next();
            if (moveList.size() == 1)
            {
                // This critter is not mobile, and its hex is taken.
                CritterMove cm = moveList.get(0);
                takenHexLabels.add(cm.getStartingHexLabel());
                it.remove();
                changed = true;
            }
        }

        // Now trim all moves to taken hexes from all movelists.
        it = allCritterMoves.iterator();
        while (it.hasNext())
        {
            List<CritterMove> moveList = it.next();
            for (CritterMove cm : moveList)
            {
                if (takenHexLabels.contains(cm.getEndingHexLabel()))
                {
                    it.remove();
                    changed = true;
                }
            }
        }

        return changed;
    }

    BattleEvalConstants bec = new BattleEvalConstants();

    class BattleEvalConstants
    {

        int OFFBOARD_DEATH_SCALE_FACTOR = -150;
        int NATIVE_BONUS_TERRAIN = 40; // 50 -- old value
        int NATIVE_BOG = 20;
        int NON_NATIVE_PENALTY_TERRAIN = -100;
        int PENALTY_DAMAGE_TERRAIN = -200;
        int FIRST_RANGESTRIKE_TARGET = 300;
        int EXTRA_RANGESTRIKE_TARGET = 100;
        int RANGESTRIKE_TITAN = 500;
        int RANGESTRIKE_WITHOUT_PENALTY = 100;
        int ATTACKER_ADJACENT_TO_ENEMY = 400;
        int DEFENDER_ADJACENT_TO_ENEMY = -20;
        int ADJACENT_TO_ENEMY_TITAN = 1300;
        int ADJACENT_TO_RANGESTRIKER = 500;
        int ATTACKER_KILL_SCALE_FACTOR = 25; // 100
        int DEFENDER_KILL_SCALE_FACTOR = 1; // 100
        int KILLABLE_TARGETS_SCALE_FACTOR = 0; // 10
        int ATTACKER_GET_KILLED_SCALE_FACTOR = -20;
        int DEFENDER_GET_KILLED_SCALE_FACTOR = -40;
        int ATTACKER_GET_HIT_SCALE_FACTOR = -1;
        int DEFENDER_GET_HIT_SCALE_FACTOR = -2;
        int TITAN_TOWER_HEIGHT_BONUS = 2000;
        int DEFENDER_TOWER_HEIGHT_BONUS = 80;
        int TITAN_FORWARD_EARLY_PENALTY = -10000;
        int TITAN_BY_EDGE_OR_TREE_BONUS = 400;
        int DEFENDER_FORWARD_EARLY_PENALTY = -60;
        int ATTACKER_DISTANCE_FROM_ENEMY_PENALTY = -300;
        int ADJACENT_TO_BUDDY = 100;
        int ADJACENT_TO_BUDDY_TITAN = 600; // 200
        int GANG_UP_ON_CREATURE = 50;
    }

    /** Return a map of target hex label to number 
     * of friendly creatures that can strike it */
    private Map<String, Integer> findStrikeMap()
    {
        Map<String, Integer> map = new HashMap<String, Integer>();

        for (BattleChit critter : client.getActiveBattleChits())
        {
            Set<String> targets = client.findStrikes(critter.getTag());
            for (String hexLabel : targets)
            {
                Integer old = map.get(hexLabel);
                if (old == null)
                {
                    map.put(hexLabel, Integer.valueOf(1));
                }
                else
                {
                    map.put(hexLabel, Integer.valueOf(old.intValue() + 1));
                }
            }
        }
        return map;
    }

    /** strikeMap is optional */
    private int evaluateCritterMove(BattleChit critter,
        Map<String, Integer> strikeMap)
    {
        final MasterBoardTerrain terrain = client.getBattleSite().getTerrain();
        final LegionClientSide legion = (LegionClientSide)client
            .getMyEngagedLegion();
        final int skill = critter.getSkill();
        final int power = critter.getPower();
        final BattleHex hex = client.getBattleHex(critter);
        final int turn = client.getBattleTurnNumber();

        // TODO this is broken: the method expects a master terrain name,
        // not the hazard terrain name -- most likely the expectation of
        // the method should be changed to expect a HazardTerrain instance
        PowerSkill ps = getNativeTerrainValue(critter.getCreature(), hex
            .getTerrain(), true);

        int native_power = ps.getPowerAttack() + (ps.getPowerDefend() + power);
        int native_skill = ps.getSkillAttack() + ps.getSkillDefend();

        int value = 0;

        // Add for sitting in favorable terrain.
        // Subtract for sitting in unfavorable terrain.
        if (hex.isEntrance())
        {
            // Staying offboard to die is really bad.
            value += bec.OFFBOARD_DEATH_SCALE_FACTOR
                * getCombatValue(critter, terrain);
        }
        else if (hex.isNativeBonusTerrain()
            && critter.getCreature().isNativeIn(hex.getTerrain()))
        {
            value += bec.NATIVE_BONUS_TERRAIN;

            // Above gives a small base value.
            // Scale remaining bonus to size of benefit

            if (hex.getElevation() > 0)
            {
                native_skill += 1; // guess at bonus
            }

            int bonus = (native_power - 2 * power) * skill
                + (native_skill - 2 * skill) * power;

            value += 3 * bonus;

            // We want marsh natives to slightly prefer moving to bog hexes,
            // even though there's no real bonus there, to leave other hexes
            // clear for non-native allies.
            if (hex.getTerrain().equals(HazardTerrain.BOG))
            {
                value += bec.NATIVE_BOG;
            }

            /* 
             Log.debug("Native " + critter.getCreature().getName() + " in " + 
             hex.getTerrain() +  " bonus " + bonus);
             Log.debug("Native SKA " + ps.getSkillAttack() + " SKD " + 
             ps.getSkillDefend());
             Log.debug("Native PA " + ps.getPowerAttack() + " PD " + 
             ps.getPowerDefend() + power);
             Log.debug("Native skill " + native_skill + " skill " + 2*skill);
             Log.debug("Native power " + native_power + " power " + 2*power);
             **/
        }
        else
        // Critter is not native or the terrain is not beneficial
        {
            if (hex.isNonNativePenaltyTerrain()
                && (!critter.getCreature().isNativeIn(hex.getTerrain())))
            {
                value += bec.NON_NATIVE_PENALTY_TERRAIN;

                // Above gives a small base value.
                // Scale remaining bonus to size of benefit
                int bonus = (native_power - 2 * power) * skill
                    + (native_skill - 2 * skill) * power;

                value += 3 * bonus; // bonus should be negative here
            }
        }

        /* damage is positive, healing is negative, so we can always add */
        value += bec.PENALTY_DAMAGE_TERRAIN
            * hex.damageToCreature(critter.getCreature());

        Set<String> targetHexLabels = client.findStrikes(critter.getTag());
        int numTargets = targetHexLabels.size();

        if (numTargets >= 1)
        {
            if (!client.isInContact(critter, true))
            {
                // Rangestrikes.
                value += bec.FIRST_RANGESTRIKE_TARGET;

                // Having multiple targets is good, in case someone else
                // kills one.
                if (numTargets >= 2)
                {
                    value += bec.EXTRA_RANGESTRIKE_TARGET;
                }

                // Non-warlock skill 4 rangestrikers should slightly prefer
                // range 3 to range 4.  Non-brush rangestrikers should
                // prefer strikes not through bramble.  Warlocks should
                // try to rangestrike titans.
                boolean penalty = true;
                for (String hexLabel : targetHexLabels)
                {
                    BattleChit target = client.getBattleChit(hexLabel);
                    if (target.isTitan())
                    {
                        value += bec.RANGESTRIKE_TITAN;
                    }
                    int strikeNum = client.getStrike().getStrikeNumber(
                        critter, target);
                    if (strikeNum <= 4 - skill + target.getSkill())
                    {
                        penalty = false;
                    }

                    // Reward ganging up on enemies.
                    if (strikeMap != null)
                    {
                        int numAttackingThisTarget = strikeMap.get(hexLabel)
                            .intValue();
                        if (numAttackingThisTarget > 1)
                        {
                            value += bec.GANG_UP_ON_CREATURE;
                        }
                    }
                }
                if (!penalty)
                {
                    value += bec.RANGESTRIKE_WITHOUT_PENALTY;
                }
            }
            else
            {
                // Normal strikes.  If we can strike them, they can strike us.

                // Reward being adjacent to an enemy if attacking.
                if (legion.equals(client.getAttacker()))
                {
                    value += bec.ATTACKER_ADJACENT_TO_ENEMY;
                }
                // Slightly penalize being adjacent to an enemy if defending.
                else
                {
                    value += bec.DEFENDER_ADJACENT_TO_ENEMY;
                }

                int killValue = 0;
                int numKillableTargets = 0;
                int hitsExpected = 0;

                for (String hexLabel : targetHexLabels)
                {
                    BattleChit target = client.getBattleChit(hexLabel);

                    // Reward being next to enemy titans.  (Banzai!)
                    if (target.isTitan())
                    {
                        value += bec.ADJACENT_TO_ENEMY_TITAN;
                    }

                    // Reward being next to a rangestriker, so it can't hang
                    // back and plink us.
                    if (target.isRangestriker() && !critter.isRangestriker())
                    {
                        value += bec.ADJACENT_TO_RANGESTRIKER;
                    }

                    // Attack Warlocks so they don't get Titan
                    if (target.getName().equals("Warlock"))
                    {
                        value += bec.ADJACENT_TO_BUDDY_TITAN;
                    }

                    // Reward being next to an enemy that we can probably
                    // kill this turn.
                    int dice = client.getStrike().getDice(critter, target);
                    int strikeNum = client.getStrike().getStrikeNumber(
                        critter, target);
                    double meanHits = Probs.meanHits(dice, strikeNum);
                    if (meanHits + target.getHits() >= target.getPower())
                    {
                        numKillableTargets++;
                        int targetValue = getKillValue(target, terrain);
                        killValue = Math.max(targetValue, killValue);
                    }
                    else
                    {
                        // reward doing damage to target - esp. titan.
                        int targetValue = getKillValue(target, terrain);
                        killValue = (int)(0.5 * (meanHits / target.getPower()) * Math
                            .max(targetValue, killValue));
                    }

                    // Reward ganging up on enemies.
                    if (strikeMap != null)
                    {
                        int numAttackingThisTarget = strikeMap.get(hexLabel)
                            .intValue();
                        if (numAttackingThisTarget > 1)
                        {
                            value += bec.GANG_UP_ON_CREATURE;
                        }
                    }

                    // Penalize damage that we can take this turn,
                    {
                        dice = client.getStrike().getDice(target, critter);
                        strikeNum = client.getStrike().getStrikeNumber(target,
                            critter);
                        hitsExpected += Probs.meanHits(dice, strikeNum);
                    }
                }

                if (legion.equals(client.getAttacker()))
                {
                    value += bec.ATTACKER_KILL_SCALE_FACTOR * killValue
                        + bec.KILLABLE_TARGETS_SCALE_FACTOR
                        * numKillableTargets;
                }
                else
                {
                    value += bec.DEFENDER_KILL_SCALE_FACTOR * killValue
                        + bec.KILLABLE_TARGETS_SCALE_FACTOR
                        * numKillableTargets;
                }

                int hits = critter.getHits();

                // XXX Attacking legions late in battle ignore damage.
                if (legion.equals(client.getDefender()) || critter.isTitan()
                    || turn <= 4)
                {
                    if (hitsExpected + hits >= power)
                    {
                        if (legion.equals(client.getAttacker()))
                        {
                            value += bec.ATTACKER_GET_KILLED_SCALE_FACTOR
                                * getKillValue(critter, terrain);
                        }
                        else
                        {
                            value += bec.DEFENDER_GET_KILLED_SCALE_FACTOR
                                * getKillValue(critter, terrain);
                        }
                    }
                    else
                    {
                        if (legion.equals(client.getAttacker()))
                        {
                            value += bec.ATTACKER_GET_HIT_SCALE_FACTOR
                                * getKillValue(critter, terrain);
                        }
                        else
                        {
                            value += bec.DEFENDER_GET_HIT_SCALE_FACTOR
                                * getKillValue(critter, terrain);
                        }
                    }
                }
            }
        }

        BattleHex entrance = BattleMap.getEntrance(terrain, legion
            .getEntrySide());

        // Reward titans sticking to the edges of the back row
        // surrounded by allies.  We need to relax this in the
        // last few turns of the battle, so that attacking titans
        // don't just sit back and wait for a time loss.
        if (critter.isTitan())
        {
            if (terrain.isTower() && legion.equals(client.getDefender()))
            {
                // Stick to the center of the tower.
                value += bec.TITAN_TOWER_HEIGHT_BONUS * hex.getElevation();
            }
            else
            {
                if (turn <= 4)
                {
                    value += bec.TITAN_FORWARD_EARLY_PENALTY
                        * Strike.getRange(hex, entrance, true);
                    for (int i = 0; i < 6; i++)
                    {
                        BattleHex neighbor = hex.getNeighbor(i);
                        if (neighbor == null
                            || neighbor.getTerrain()
                                .equals(HazardTerrain.TREE))
                        {
                            value += bec.TITAN_BY_EDGE_OR_TREE_BONUS;
                        }
                    }
                }
            }
        }

        // Encourage defending critters to hang back.
        else if (legion.equals(client.getDefender()))
        {
            if (terrain.isTower())
            {
                // Stick to the center of the tower.
                value += bec.DEFENDER_TOWER_HEIGHT_BONUS * hex.getElevation();
            }
            else
            {
                int range = Strike.getRange(hex, entrance, true);

                // To ensure that defending legions completely enter
                // the board, prefer the second row to the first.  The
                // exception is small legions early in the battle,
                // when trying to survive long enough to recruit.
                int preferredRange = 3;
                if (legion.getHeight() <= 3 && turn < 4)
                {
                    preferredRange = 2;
                }
                if (range != preferredRange)
                {
                    value += bec.DEFENDER_FORWARD_EARLY_PENALTY
                        * Math.abs(range - preferredRange);
                }
            }
        }

        else
        // Attacker, non-titan, needs to charge.
        {
            // Head for enemy creatures.
            value += bec.ATTACKER_DISTANCE_FROM_ENEMY_PENALTY
                * client.getStrike().minRangeToEnemy(critter);
        }

        // Adjacent buddies
        for (int i = 0; i < 6; i++)
        {
            if (!hex.isCliff(i))
            {
                BattleHex neighbor = hex.getNeighbor(i);
                if (neighbor != null && client.isOccupied(neighbor))
                {
                    BattleChit other = client.getBattleChit(neighbor
                        .getLabel());
                    if (other.isInverted() == critter.isInverted())
                    {
                        // Buddy
                        if (other.isTitan())
                        {
                            value += bec.ADJACENT_TO_BUDDY_TITAN;
                            value += native_skill
                                * (native_power - critter.getHits());
                        }
                        else
                        {
                            value += bec.ADJACENT_TO_BUDDY;
                        }
                    }
                }
            }
        }

        return value;
    }

    private int evaluateLegionBattleMove(LegionMove lm)
    {
        // First we need to move all critters into position.
        for (CritterMove cm : lm.getCritterMoves())
        {
            cm.getCritter().moveToHex(cm.getEndingHexLabel());
        }

        Map<String, Integer> strikeMap = findStrikeMap();

        // Then find the sum of all critter evals.
        int sum = 0;
        for (CritterMove cm : lm.getCritterMoves())
        {
            sum += evaluateCritterMove(cm.getCritter(), strikeMap);
        }

        // Then move them all back.
        for (CritterMove cm : lm.getCritterMoves())
        {
            cm.getCritter().moveToHex(cm.getStartingHexLabel());
        }

        return sum;
    }

    int getHintedRecruitmentValue(CreatureType creature, Legion legion,
        String[] section)
    {
        if (!(creature).isTitan())
        {
            return (creature).getHintedRecruitmentValue(section);
        }
        Player player = legion.getPlayer();
        int power = ((PlayerClientSide)player).getTitanPower();
        int skill = (creature).getSkill();
        return power
            * skill
            * VariantSupport.getHintedRecruitmentValueOffset(creature
                .getName(), section);
    }

    /** LegionMove has a List of one CritterMove per mobile critter
     *  in the legion. */
    class LegionMove
    {
        private final List<CritterMove> critterMoves = new ArrayList<CritterMove>();

        void add(CritterMove cm)
        {
            critterMoves.add(cm);
        }

        List<CritterMove> getCritterMoves()
        {
            return Collections.unmodifiableList(critterMoves);
        }

        @Override
        public String toString()
        {
            List<String> cmStrings = new ArrayList<String>();
            for (CritterMove cm : critterMoves)
            {
                cmStrings.add(cm.toString());
            }
            return Glob.glob(", ", cmStrings);
        }

        @Override
        public boolean equals(Object ob)
        {
            if (!(ob instanceof LegionMove))
            {
                return false;
            }
            LegionMove lm = (LegionMove)ob;
            return toString().equals(lm.toString());
        }

        @Override
        public int hashCode()
        {
            return toString().hashCode();
        }
    }

    class TriggerTimeIsUp extends TimerTask
    {
        @Override
        public void run()
        {
            timeIsUp = true;
        }
    }

    protected Variant getVariantPlayed()
    {
        return this.client.getGame().getVariant();
    }
}
