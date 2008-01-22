package net.sf.colossus.ai;


import java.util.List;
import java.util.Set;

import net.sf.colossus.client.CritterMove;
import net.sf.colossus.game.Legion;


/**
 * interface to allow for multiple AI implementations
 *
 * @author Bruce Sherrod
 * @author David Ripton
 * @version $Id$
 */
public interface AI
{

    /** make masterboard moves for current player in the Game */
    boolean masterMove();

    /** make splits for current player.  Return true if done */
    boolean split();

    /** continue making splits.  Return true if done. */
    boolean splitCallback(String parentId, String childId);

    /** make recruits for current player */
    void muster();

    /** pick one reinforcement for legion */
    void reinforce(Legion legion);

    /** choose whether legion should flee from enemy */
    boolean flee(Legion legion, Legion enemy);

    /** choose whether legion should concede to enemy */
    boolean concede(Legion legion, Legion enemy);

    /** make battle strikes for legion */
    boolean strike(Legion legion);

    /** return a list of battle moves for the active legion */
    List<CritterMove> battleMove();

    /** Try another move for creatures whose moves failed. */
    void retryFailedBattleMoves(List<CritterMove> bestMoveOrder);

    /** pick an entry side */
    String pickEntrySide(String hexLabel, Legion legion, Set<String> entrySides);

    /** pick an engagement to resolve */
    String pickEngagement();

    /** choose whether to acquire an angel or archangel */
    String acquireAngel(Legion legion, List<String> recruits);

    /** choose whether to summon an angel or archangel */
    String summonAngel(Legion summoner);

    /** pick a color of legion markers */
    String pickColor(List<String> colors, List<String> favoriteColors);

    /** pick a legion marker */
    String pickMarker(Set<String> markerIds, String preferredShortColor);

    /** choose carry target */
    void handleCarries(int carryDamage, Set<String> carryTargets);

    /** pick an optional strike penalty */
    String pickStrikePenalty(List<String> choices);
}
