package net.sf.colossus.server;


import net.sf.colossus.game.Legion;


/**
 *  IServer is an interface for the client-accessible parts of Server.
 *  @version $Id$
 *  @author David Ripton
 */
public interface IServer
{
    public void leaveCarryMode();

    public void doneWithBattleMoves();

    public void doneWithStrikes();

    public void acquireAngel(Legion legion, String angelType);

    public void doSummon(Legion receivingLegion, Legion donorLegion,
        String angel);

    public void doRecruit(Legion legion, String recruitName,
        String recruiterName);

    public void engage(String hexLabel);

    public void concede(Legion legion);

    public void doNotConcede(Legion legion);

    public void flee(Legion legion);

    public void doNotFlee(Legion legion);

    public void makeProposal(String proposalString);

    public void fight(String hexLabel);

    public void doBattleMove(int tag, String hexLabel);

    public void strike(int tag, String hexLabel);

    public void applyCarries(String hexLabel);

    public void undoBattleMove(String hexLabel);

    public void assignStrikePenalty(String prompt);

    public void mulligan();

    public void undoSplit(String splitoffId);

    public void undoMove(Legion legion);

    public void undoRecruit(Legion legion);

    public void doneWithSplits();

    public void doneWithMoves();

    public void doneWithEngagements();

    public void doneWithRecruits();

    public void withdrawFromGame();

    public void disconnect();

    public void stopGame();

    public void setDonor(Legion legion);

    public void doSplit(Legion parent, String childMarker, String results);

    public void doMove(Legion legion, String hexLabel, String entrySide,
        boolean teleport, String teleportingLord);

    public void assignColor(String color);

    public void assignFirstMarker(String markerId);

    // XXX Disallow the following methods in network games
    public void newGame();

    public void loadGame(String filename);

    public void saveGame(String filename);
}
