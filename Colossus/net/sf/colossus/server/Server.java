package net.sf.colossus.server;


import java.util.*;
import java.net.*;
import javax.swing.*;

import net.sf.colossus.util.Log;
import net.sf.colossus.client.Client;
import net.sf.colossus.client.Proposal;


/**
 *  Class Server lives on the server side and handles all communcation with
 *  the clients.  It talks to the server classes locally, and to the Clients
 *  via the network protocol.
 *  @version $Id$
 *  @author David Ripton
 */
public final class Server
{
    private Game game;

    // XXX Need to verify that various requests came from the correct
    // client for that player.

    /** For now we'll keep a list of client refs locally rather than using
     *  the network protocol.  We will eventually instead keep a list of the
     *  existing socket connections to use. Maybe also save things like
     *  the originating IP, in case a connection breaks and we need to
     *  authenticate reconnects.  Do not share these references. */
    private List clients = new ArrayList();

    /** Map of player name to client. */
    private Map clientMap = new HashMap();

    // Cached strike information.
    Critter striker; 
    Critter target;
    int strikeNumber; 
    int damage; 
    int [] rolls;



    Server(Game game)
    {
        this.game = game;
    }


    /** Temporary.  We will not use direct client refs later. */
    void addClient(String playerName, boolean primary)
    {
        Client client = new Client(this, playerName, primary);
        clients.add(client);
        clientMap.put(playerName, client);
    }


    /** temp */
    void disposeAllClients()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.dispose();
        }
        clients.clear();
    }


    void allUpdatePlayerInfo()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.updatePlayerInfo(getPlayerInfo());
        }
    }

    void allUpdateCreatureCount(String creatureName, int count)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.updateCreatureCount(creatureName, count);
        }
    }


    void allTellMovementRoll(int roll)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellMovementRoll(roll);
        }
    }


    public void leaveCarryMode()
    {
        Battle battle = game.getBattle();
        battle.leaveCarryMode();
    }


    public void doneWithBattleMoves()
    {
        Battle battle = game.getBattle();
        battle.doneWithMoves();
    }

    public boolean anyOffboardCreatures()
    {
        Battle battle = game.getBattle();
        return battle.anyOffboardCreatures();
    }

    public void doneWithStrikes(String playerName)
    {
        Battle battle = game.getBattle();
        if (!playerName.equals(battle.getActivePlayerName()))
        {
            Log.error(playerName + "illegally called doneWithStrikes()"); 
            return;
        }
        if (!battle.doneWithStrikes())
        {
            showMessageDialog(playerName, "Must take forced strikes");
        }
    }


    public void makeForcedStrikes(String playerName, boolean rangestrike)
    {
        if (playerName.equals(game.getBattle().getActivePlayerName()))
        {
            game.getBattle().makeForcedStrikes(rangestrike);
        }
    }


    private Client getClient(String playerName)
    {
        if (clientMap.containsKey(playerName))
        {
            return (Client)clientMap.get(playerName);
        }
        else
        {
            Log.error("No client in clientMap for " + playerName);
            return null;
        }
    }


    boolean getClientOption(String playerName, String optname)
    {
        Client client = getClient(playerName);
        if (client != null)
        {
            return client.getOption(optname);
        }
        return false;
    }

    /** Return the name of the first human-controlled client, or null if
     *  all clients are AI-controlled. */
    private String getFirstHumanClientName()
    {
        for (int i = 0; i < game.getNumPlayers(); i++)
        {
            Player player = game.getPlayer(i);
            if (player.isHuman())
            {
                return player.getName();
            }
        }
        return null;
    }

    /** Get the option from the first human-controlled client.  If there 
     *  are none, get the option from the first AI-controlled client. */
    boolean getClientOption(String optname)
    {
        String clientName = getFirstHumanClientName();
        if (clientName == null)
        {
            clientName = game.getPlayer(0).getName();
        }
        return getClientOption(clientName, optname);
    }

    /** Get the option from the first human-controlled client.  If there 
     *  are none, get the option from the first AI-controlled client. */
    int getClientIntOption(String optname)
    {
        String clientName = getFirstHumanClientName();
        if (clientName == null)
        {
            clientName = game.getPlayer(0).getName();
        }
        return getClientIntOption(clientName, optname);
    }

    int getClientIntOption(String playerName, String optname)
    {
        Client client = getClient(playerName);
        if (client != null)
        {
            return client.getIntOption(optname);
        }
        return -1;
    }

    void setClientOption(String playerName, String optname, boolean value)
    {
        Client client = getClient(playerName);
        if (client != null)
        {
            client.setOption(optname, value);
        }
    }


    void allInitBoard()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.initBoard();
        }
    }


    void allAddMarkers()
    {
        java.util.List markerIds = game.getAllLegionIds();
        Iterator it = markerIds.iterator();
        while (it.hasNext())
        {
            String markerId = (String)it.next();
            allAddMarker(markerId);
        }
    }

    void allAddMarker(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        String hexLabel = legion.getCurrentHexLabel();

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.addMarker(markerId, hexLabel);
        }
    }

    void allRemoveMarker(String markerId)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.removeMarker(markerId);
        }
    }


    void showMessageDialog(String playerName, String message)
    {
        Client client = getClient(playerName);
        client.showMessageDialog(message);
    }

    void allShowMessageDialog(String message)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.showMessageDialog(message);
        }
    }

    void allTellGameOver(String message)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellGameOver(message);
        }
    }


    void allSetupSplit()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            Client client = getClient(player.getName());
            client.setupSplit(player.getMarkersAvailable(), 
                game.getActivePlayerName(), game.getTurnNumber());
        }
        allUpdatePlayerInfo();
    }


    void allSetupMove()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupMove();
        }
    }

    void allSetupFight()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupFight();
        }
    }

    void allSetupMuster()
    {
        Set hexLabels = game.findAllEligibleRecruitHexes();

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupMuster(hexLabels);
        }
    }


    void allSetupBattleSummon()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupBattleSummon(game.getBattle().getActivePlayerName(),
                game.getBattle().getTurnNumber());
        }
    }

    void allSetupBattleRecruit()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupBattleRecruit(game.getBattle().getActivePlayerName(),
                game.getBattle().getTurnNumber());
        }
    }

    void allSetupBattleMove()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupBattleMove();
        }
    }

    void allSetupBattleFight()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setupBattleFight(game.getBattle().getPhase(),
                game.getBattle().getActivePlayerName());
        }
    }


    void allTellBattleMove(int tag, String startingHex, String endingHex,
        boolean undo)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellBattleMove(tag, startingHex, endingHex, undo);
        }
    }

    void allPlaceNewChit(Critter critter, boolean inverted)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.placeNewChit(critter.getImageName(), inverted,
                critter.getTag(), critter.getCurrentHexLabel());
        }
    }


    void allRemoveDeadBattleChits()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.removeDeadBattleChits();
        }
    }


    void allHighlightEngagements()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.highlightEngagements(game.findEngagements());
        }
    }


    /** Find out if the player wants to acquire an angel or archangel. */
    void askAcquireAngel(String playerName, String markerId, List recruits)
    {
Log.debug("Called Server.askAcquireAngel()");
        Client client = getClient(playerName);
        if (client != null)
        {
            client.askAcquireAngel(markerId, recruits);
        }
    }

    public void acquireAngel(String markerId, String angelType)
    {
Log.debug("Called Server.acquireAngel() for " + markerId + " " + angelType);
        Legion legion = game.getLegionByMarkerId(markerId);
        if (legion != null)
        {
            legion.addAngel(angelType);
        }
    }


    void createSummonAngel(Legion legion)
    {
        Client client = getClient(legion.getPlayerName());
        client.createSummonAngel(legion.getMarkerId(), 
            legion.getLongMarkerName());
    }

    void reinforce(Legion legion)
    {
        if (getClientOption(legion.getPlayerName(), Options.autoRecruit))
        {
            legion.getPlayer().aiReinforce(legion);
        }
        else
        {
            Client client = getClient(legion.getPlayerName());
            client.doReinforce(legion.getMarkerId());
        }
    }

    public void doSummon(String markerId, String donorId, String angel)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        Legion donor = game.getLegionByMarkerId(donorId);
        Creature creature = null;
        if (angel != null)
        {
            creature = Creature.getCreatureByName(angel);
        }
        game.doSummon(legion, donor, creature);
    }


    /** Return true if the legion has moved and can recruit. */
    public boolean canRecruit(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        return legion != null && legion.hasMoved() && legion.canRecruit();
    }

    /** Return a list of creature name strings. */
    public java.util.List findEligibleRecruiters(String markerId,
        String recruitName)
    {
        java.util.List creatures = 
            game.findEligibleRecruiters(markerId, recruitName);
        java.util.List strings = new ArrayList();
        Iterator it = creatures.iterator();
        while (it.hasNext())
        {
            Creature creature = (Creature)it.next();
            strings.add(creature.getName());
        }
        return strings;
    }

    /** Handle mustering for legion. */ 
    public void doRecruit(String markerId, String recruitName,
        String recruiterName)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (legion != null && (legion.hasMoved() || game.getPhase() ==
            Constants.FIGHT) && legion.canRecruit())
        {
            legion.sortCritters();
            Creature recruit = null;
            Creature recruiter = null;
            if (recruitName != null)
            {
                recruit = Creature.getCreatureByName(recruitName);
                recruiter = Creature.getCreatureByName(recruiterName);
                if (recruit != null)
                {
                    game.doRecruit(legion, recruit, recruiter);
                }
            }

            if (!legion.canRecruit())
            {
                didRecruit(legion, recruit, recruiter);
            }
        }
        // Need to always call this to keep game from hanging.
        if (game.getPhase() == Constants.FIGHT)
        {
            if (game.getBattle() != null)
            {
                game.getBattle().doneReinforcing();
            }
        }
    }

    void didRecruit(Legion legion, Creature recruit, Creature recruiter)
    {
        allUpdatePlayerInfo();

        int numRecruiters = Game.numberOfRecruiterNeeded(recruiter, recruit, 
            legion.getCurrentHex().getTerrain());
        String recruiterName = null;
        if (recruiter != null)
        {
            recruiterName = recruiter.getName();
        }

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.didRecruit(legion.getMarkerId(), recruit.getName(),
                recruiterName, numRecruiters);
        }
    }

    void undidRecruit(Legion legion, String recruitName)
    {
        allUpdatePlayerInfo();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.undidRecruit(legion.getMarkerId(), recruitName);
        }
    }


    public void engage(String hexLabel)
    {
        game.engage(hexLabel);
    }


    /** Ask ally's player whether he wants to concede with ally. */
    void askConcede(Legion ally, Legion enemy)
    {
        if (getClientOption(ally.getPlayerName(), Options.autoFlee))
        {
            if (ally.getPlayer().aiConcede(ally, enemy))
            {
                concede(ally.getMarkerId());
            }
            else
            {
                doNotConcede(ally.getMarkerId());
            }
        }
        else
        {
            Client client = getClient(ally.getPlayerName());
            client.askConcede(ally.getLongMarkerName(),
                ally.getCurrentHex().getDescription(), ally.getMarkerId(),
                enemy.getMarkerId());
        }
    }

    public void concede(String markerId)
    {
        game.concede(markerId);
    }

    public void doNotConcede(String markerId)
    {
        game.doNotConcede(markerId);
    }

    /** Ask ally's player whether he wants to flee with ally. */
    void askFlee(Legion ally, Legion enemy)
    {
        if (getClientOption(ally.getPlayerName(), Options.autoFlee))
        {
            if (ally.getPlayer().aiFlee(ally, enemy))
            {
                flee(ally.getMarkerId());
            }
            else
            {
                doNotFlee(ally.getMarkerId());
            }
        }
        else
        {
            Client client = getClient(ally.getPlayerName());
            client.askFlee(ally.getLongMarkerName(),
                ally.getCurrentHex().getDescription(), ally.getMarkerId(),
                enemy.getMarkerId());
        }
    }

    public void flee(String markerId)
    {
        game.flee(markerId);
    }

    public void doNotFlee(String markerId)
    {
        game.doNotFlee(markerId);
    }


    public void tryToConcede(String markerId)
    {
        game.getBattle().concede(markerId);
    }


    void twoNegotiate(Legion attacker, Legion defender)
    {
        Client client1 = getClient(defender.getPlayerName());
        client1.askNegotiate(attacker.getLongMarkerName(), 
            defender.getLongMarkerName(), attacker.getMarkerId(), 
            defender.getMarkerId(), attacker.getCurrentHexLabel());

        Client client2 = getClient(attacker.getPlayerName());
        client2.askNegotiate(attacker.getLongMarkerName(), 
            defender.getLongMarkerName(), attacker.getMarkerId(), 
            defender.getMarkerId(), attacker.getCurrentHexLabel());
    }

    // XXX Stringify the proposal.
    /** playerName makes a proposal. */
    public void makeProposal(String playerName, Proposal proposal)
    {
        game.makeProposal(playerName, proposal);
    }

    /** Tell playerName about proposal. */
    void tellProposal(String playerName, Proposal proposal)
    {
        Client client = getClient(playerName);
        client.tellProposal(proposal);
    }

    public void fight(String hexLabel)
    {
        game.fight(hexLabel);
    }


    public void doBattleMove(int tag, String hexLabel)
    {
        boolean moved = game.getBattle().doMove(tag, hexLabel);
        if (moved)
        {
            Critter critter = game.getBattle().getCritter(tag);
            String startingHexLabel = critter.getStartingHexLabel();
            allTellDidBattleMove(tag, startingHexLabel, hexLabel);
        }
    }

    void allTellDidBattleMove(int tag, String startingHexLabel,
        String endingHexLabel)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.didBattleMove(tag, startingHexLabel, endingHexLabel);
        }
    }


    public void strike(int tag, String hexLabel)
    {
        Battle battle = game.getBattle();
        battle.getActiveLegion().getCritterByTag(tag).strike(
            battle.getCritter(hexLabel));
    }

    // TODO Error checks.
    public void applyCarries(String hexLabel)
    {
        Battle battle = game.getBattle();
        Critter target = battle.getCritter(hexLabel);
        battle.applyCarries(target);
    }


    public Set getCarryTargets()
    {
        Battle battle = game.getBattle();
        return battle.getCarryTargets();
    }


    public void undoBattleMove(String hexLabel)
    {
        game.getBattle().undoMove(hexLabel);
    }


    // XXX Stringify the return value.
    public int [] getCritterTags(String hexLabel)
    {
        Battle battle = game.getBattle();
        List critters = battle.getCritters(hexLabel);
        int [] tags = new int[critters.size()];
        int i = 0;
        Iterator it = critters.iterator();
        while (it.hasNext())
        {
            Critter critter = (Critter)it.next();
            tags[i++] = critter.getTag();
        }
        return tags;
    }


    /** Return a set of hexLabels. */
    public Set findMobileCritters()
    {
        Battle battle = game.getBattle();
        return battle.findMobileCritters();
    }

    /** Return a set of hexLabels. */
    public Set showBattleMoves(int tag)
    {
        Battle battle = game.getBattle();
        return battle.showMoves(tag);
    }

    /** Return a set of hexLabels. */
    public Set findStrikes(int tag)
    {
        Battle battle = game.getBattle();
        return battle.findStrikes(tag);
    }

    /** Return a set of hexLabels. */
    public Set findCrittersWithTargets()
    {
        Battle battle = game.getBattle();
        return battle.findCrittersWithTargets();
    }


    /** Return the player name for the critter tag.  Only works in battle. */
    public String getPlayerNameByTag(int tag)
    {
        Battle battle = game.getBattle();
        Legion legion = battle.getActiveLegion();
        if (legion.getCritterByTag(tag) != null)
        {
            return legion.getPlayerName();
        }
        legion = battle.getInactiveLegion();
        if (legion.getCritterByTag(tag) != null)
        {
            return legion.getPlayerName();
        }
        return "";
    }


    void allTellStrikeResults(Critter striker, Critter target,
        int strikeNumber, int [] rolls, int damage, int carryDamageLeft, 
        Set carryTargetDescriptions)
    {
        // Save strike info so that it can be reused for carries.
        this.striker = striker;
        this.target = target;
        this.strikeNumber = strikeNumber;
        this.damage = damage;
        this.rolls = rolls;

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellStrikeResults(striker.getDescription(), 
                striker.getTag(), target.getDescription(), target.getTag(),
                strikeNumber, rolls, damage, target.isDead(), false, 
                carryDamageLeft, carryTargetDescriptions);
        }
    }

    void allTellCarryResults(Critter carryTarget, int carryDamageDone, 
        int carryDamageLeft, Set carryTargetDescriptions)
    {
        if (striker == null || target == null || rolls == null)
        {
            Log.error("Called allTellCarryResults() without setup.");
            if (striker == null)
            {
                Log.error("null striker");
            }
            if (target == null)
            {
                Log.error("null target");
            }
            if (rolls == null)
            {
                Log.error("null rolls");
            }
            return;
        }
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellStrikeResults(striker.getDescription(),
                striker.getTag(), carryTarget.getDescription(), 
                carryTarget.getTag(), strikeNumber, rolls, carryDamageDone, 
                carryTarget.isDead(), true, carryDamageLeft, 
                carryTargetDescriptions);
        }
    }

    void allTellDriftDamageResults(Critter target, int damage)
    {
        this.target = target;
        this.damage = damage;

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.tellStrikeResults("hex damage", -1, target.getDescription(),
                target.getTag(), 0, null, damage, target.isDead(), false, 
                0, null);
        }
    }


    void allSetBattleWaitCursor()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setBattleWaitCursor();
        }
    }

    void allSetBattleDefaultCursor()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setBattleDefaultCursor();
        }
    }

    /** Takes a Set of PenaltyOptions. */
    void askChooseStrikePenalty(SortedSet penaltyOptions)
    {
        String playerName = game.getBattle().getActivePlayerName();
        Client client = getClient(playerName);
        ArrayList choices = new ArrayList();
        Iterator it = penaltyOptions.iterator();
        while (it.hasNext())
        {
            PenaltyOption po = (PenaltyOption)it.next();
            striker = po.getStriker();
            choices.add(po.toString());
        }
        client.askChooseStrikePenalty(choices);
    }

    public void assignStrikePenalty(String playerName, String prompt)
    {
        striker.assignStrikePenalty(prompt);
        striker = null;
    }

    void allInitBattle(String masterHexLabel)
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.initBattle(masterHexLabel, battle.getTurnNumber(),
                battle.getActivePlayerName(), battle.getPhase(),
                battle.getAttackerId(), battle.getDefenderId());
        }
    }


    void allCleanupBattle()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.cleanupBattle();
        }
    }


    // TODO Undo all moves.  (Currently the client does it.)
    public void mulligan(String playerName)
    {
        if (!playerName.equals(game.getActivePlayerName()))
        {
            Log.error(playerName + "illegally called mulligan()"); 
            return;
        }
        int roll = game.mulligan();
        Log.event(playerName + " takes a mulligan and rolls " + roll);
        if (roll != -1)
        {
            allTellMovementRoll(roll);
        }
    }


    public void undoSplit(String playerName, String splitoffId)
    {
        if (playerName != game.getActivePlayerName())
        {
            return;
        }
        game.getPlayer(playerName).undoSplit(splitoffId);
    }

    void undidSplit(String splitoffId)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.undidSplit(splitoffId);
        }
    }


    public void undoMove(String playerName, String markerId)
    {
        if (playerName != game.getActivePlayerName())
        {
            return;
        }
        Legion legion = game.getLegionByMarkerId(markerId);
        String formerHexLabel = legion.getCurrentHexLabel();
        game.getPlayer(playerName).undoMove(markerId);
        String currentHexLabel = legion.getCurrentHexLabel();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.undidMove(markerId, formerHexLabel, currentHexLabel);
        }
    }


    public void undoRecruit(String playerName, String markerId)
    {
        if (playerName != game.getActivePlayerName())
        {
            return;
        }
        game.getPlayer(playerName).undoRecruit(markerId);
    }


    public void doneWithSplits(String playerName)
    {
        if (playerName != game.getActivePlayerName())
        {
            Log.error(playerName + "illegally called doneWithSplits()"); 
            return;
        }
        if (game.getTurnNumber() == 1 && 
            game.getPlayer(playerName).getNumLegions() == 1)
        {
            showMessageDialog(playerName, "Must split initial legion");
            return;
        }
        game.advancePhase(Constants.SPLIT, playerName);
    }

    public void doneWithMoves(String playerName)
    {
        if (playerName != game.getActivePlayerName())
        {
            Log.error(playerName + "illegally called doneWithMoves()"); 
            return;
        }

        Player player = game.getPlayer(playerName);

        // If any legion has a legal non-teleport move, then
        // the player must move at least one legion.
        if (player.legionsMoved() == 0 &&
            player.countMobileLegions() > 0)
        {
            showMessageDialog(playerName, "At least one legion must move.");
            return;
        }
        // If legions share a hex and have a legal
        // non-teleport move, force one of them to take it.
        else if (player.splitLegionHasForcedMove())
        {
            showMessageDialog(playerName, "Split legions must be separated.");
            return;
        }
        // Otherwise, recombine all split legions still in
        // the same hex, and move on to the next phase.
        else
        {
            player.recombineIllegalSplits();
            game.advancePhase(Constants.MOVE, playerName);
        }
    }

    public void doneWithEngagements(String playerName)
    {
        if (playerName != game.getActivePlayerName())
        {
            Log.error(playerName + "illegally called doneWithEngagements()"); 
            return;
        }
        // Advance only if there are no unresolved engagements.
        if (game.findEngagements().size() > 0)
        {
            showMessageDialog(playerName, "Must resolve engagements");
            return;
        }
        game.advancePhase(Constants.FIGHT, playerName);
    }

    public void doneWithRecruits(String playerName)
    {
        if (playerName != game.getActivePlayerName())
        {
            Log.error(playerName + "illegally called doneWithRecruits()"); 
            return;
        }
        Player player = game.getPlayer(playerName);
        player.commitMoves();

        // Mulligans are only allowed on turn 1.
        player.setMulligansLeft(0);

        game.advancePhase(Constants.MUSTER, playerName);
    }

    /** Advance the phase without error checks, for debug only. */
    public void forceAdvancePhase()
    {
        game.advancePhase(game.getPhase(), game.getActivePlayerName());
    }

    /** Advance the battle phase without error checks, for debug only. */
    public void forceAdvanceBattlePhase()
    {
        game.getBattle().advancePhase();
    }


    // XXX Need to support inactive players quitting.
    // XXX If player quits while engaged, might need to set slayer.
    // TODO Notify all players.
    public void withdrawFromGame(String playerName)
    {
        game.getPlayer(playerName).die(null, true);
        game.advancePhase(game.getPhase(), playerName);
    }


    public String getPlayerNameByMarkerId(String markerId)
    {
        return game.getPlayerByMarkerId(markerId).getName();
    }


    public int getMulligansLeft(String playerName)
    {
        Player player = game.getPlayer(playerName);
        return player.getMulligansLeft();
    }

    public void setDonor(String hexLabel)
    {
        Player player = game.getActivePlayer();
        Legion donor = game.getFirstFriendlyLegion(hexLabel, player);
        if (donor != null)
        {
            player.setDonor(donor);
        }
    }

    public String getDonorId(String playerName)
    {
        Player player = game.getPlayer(playerName);
        Legion donor = player.getDonor();
        if (donor != null)
        {
            return donor.getMarkerId();
        }
        else
        {
            return null;
        }
    }

    // TODO Move logic to client side once legion contents are tracked.
    public boolean donorHas(String playerName, String name)
    {
        Player player = game.getPlayer(playerName);
        Legion donor = player.getDonor();
        if (donor != null)
        {
            return (donor.numCreature(Creature.getCreatureByName(name)) 
                    >= 1);
        }
        else
        {
            return false;
        }
    }

    // XXX Stringify the return value.
    private String [] getPlayerInfo()
    {
        String [] info = new String[game.getNumPlayers()];
        Iterator it = game.getPlayers().iterator();
        int i = 0;
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            info[i++] = player.getStatusInfo(); 
        }
        return info;
    }


    public int getLegionHeight(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (legion != null)
        {
            return legion.getHeight();
        }
        return 0;
    }


    // XXX Delete
    /** Return the available legion markers for playerName. */
    public Set getMarkersAvailable(String playerName)
    {
        Player player = game.getPlayer(playerName);
        return player.getMarkersAvailable();
    }

    public void doSplit(String parentId, String childId, String results)
    {
        game.doSplit(parentId, childId, results);
    }

    /** Callback from game after this legion was split off. */
    void didSplit(String hexLabel, String parentId, String childId, int height)
    {
        allUpdatePlayerInfo();

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.didSplit(hexLabel, parentId, childId, height);
        }
    }


    public void doMove(String markerId, String hexLabel, String entrySide,
        boolean teleport, String teleportingLord)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        String startingHexLabel = legion.getCurrentHexLabel();

        if (game.doMove(markerId, hexLabel, entrySide, teleport,
            teleportingLord))
        {
            allTellDidMove(markerId, startingHexLabel, hexLabel);
        }
    }

    void allTellDidMove(String markerId, String startingHexLabel,
        String endingHexLabel)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.didMove(markerId, startingHexLabel, endingHexLabel);
        }
    }


    void allTellAddCreature(String markerId, String creatureName)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.addCreature(markerId, creatureName);
        }
    }

    void allTellRemoveCreature(String markerId, String creatureName)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.removeCreature(markerId, creatureName);
        }
    }

    void allRevealLegion(Legion legion)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.setLegionContents(legion.getMarkerId(), 
                legion.getImageNames());
            client.setLegionHeight(legion.getMarkerId(),
                legion.getHeight());
        }
    }

    void oneRevealLegion(Legion legion, String playerName)
    {
        Client client = getClient(playerName);
        client.setLegionContents(legion.getMarkerId(),
            legion.getImageNames());
        client.setLegionHeight(legion.getMarkerId(),
            legion.getHeight());
    }

    void allFullyUpdateLegionHeights()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            Iterator it2 = game.getAllLegions().iterator();
            while (it2.hasNext())
            {
                Legion legion = (Legion)it2.next();
                client.setLegionHeight(legion.getMarkerId(),
                    legion.getHeight());
            }
        }
    }

    void allFullyUpdateOwnLegionContents()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            Client client = getClient(player.getName());

            Iterator it2 = player.getLegions().iterator();
            while (it2.hasNext())
            {
                Legion legion = (Legion)it2.next();
                oneRevealLegion(legion, player.getName()); 
            }
        }
    }

    void allFullyUpdateAllLegionContents()
    {
        Iterator it = game.getAllLegions().iterator();
        while (it.hasNext())
        {
            Legion legion = (Legion)it.next();
            allRevealLegion(legion);
        }
    }

    void allRevealCreature(Legion legion, String creatureName)
    {
        java.util.List names = new ArrayList();
        names.add(creatureName);
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            Client client = (Client)it.next();
            client.revealCreatures(legion.getMarkerId(), names);
        }
    }



    /** Return a list of Creatures. */
    public List findEligibleRecruits(String markerId, String hexLabel)
    {
        return game.findEligibleRecruits(markerId, hexLabel);
    }


    /** Return a set of hexLabels. */
    public Set findSummonableAngels(String markerId)
    {
        return game.findSummonableAngels(markerId);
    }

    /** Return a set of hexLabels. */
    public Set listTeleportMoves(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        return game.listTeleportMoves(legion, legion.getCurrentHex(),
            legion.getPlayer().getMovementRoll(), false);
    }

    /** Return a set of hexLabels. */
    public Set listNormalMoves(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        return game.listNormalMoves(legion, legion.getCurrentHex(),
            legion.getPlayer().getMovementRoll(), false);
    }

    /** Return a list of creature name strings. */
    public List listTeleportingLords(String markerId, String hexLabel)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        return legion.listTeleportingLords(hexLabel);
    }


    /** Return an int which is all possible entry sides (1, 3, 5)
     *  added together. */
    public Set getPossibleEntrySides(String markerId, String hexLabel,
        boolean teleport)
    {
        return game.getPossibleEntrySides(markerId, hexLabel, teleport);
    }


    public Set findAllUnmovedLegionHexes()
    {
        return game.findAllUnmovedLegionHexes();
    }

    public Set findTallLegionHexes()
    {
        return game.findTallLegionHexes();
    }

    public Set findEngagements()
    {
        return game.findEngagements();
    }

    public void newGame()
    {
        game.newGame();
    }

    public void loadGame(String filename)
    {
        game.loadGame(filename);
    }

    public void saveGame()
    {
        game.saveGame();
    }

    public void saveGame(String filename)
    {
        game.saveGame(filename);
    }

    /** Used to change a player name after color is assigned. */
    void setPlayerName(String playerName, String newName)
    {
        Client client = getClient(playerName);
        client.setPlayerName(newName);
        clientMap.remove(playerName);
        clientMap.put(newName, client);
    }

    void askPickColor(String playerName, Set colorsLeft)
    {
        Client client = getClient(playerName);
        client.askPickColor(colorsLeft);
    }

    public void assignColor(String playerName, String color)
    {
        game.assignColor(playerName, color);
    }

    // XXX Hack to set color on load game.
    void allSetColor()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            String name = player.getName();
            String color = player.getColor();
            Client client = getClient(name);
            client.setColor(color);
        }
    }
}
