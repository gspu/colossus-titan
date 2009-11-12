package net.sf.colossus.game;


/**
 * Class Movement has the masterboard move logic - or that part that could
 * already be unified and pulled up from server/client sides.
 * There are still some methods that need pulling up, but they need more
 * refactoring before that can be done.
 *
 * @author Clemens Katzer (created the new combined game.Movement class)
 * @author David Ripton (e.g. original client.Movement class)
 * @author possibly: Bruce Sherrod, Romain Dolbeau (old server.Game class)
 */
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.colossus.common.Constants;
import net.sf.colossus.common.Options;
import net.sf.colossus.variant.MasterHex;

public class Movement
{
    private static final Logger LOGGER = Logger.getLogger(Movement.class
        .getName());


    protected final Game game;
    protected final Options options;

    public Movement(Game game, Options options)
    {
        // just here so that LOGGER is used :)
        LOGGER.finest("Movement instantiated");

        this.game = game;
        this.options = options;
    }

    /** Set the entry side relative to the hex label. */
    protected static EntrySide findEntrySide(MasterHex hex, int cameFrom)
    {
        int entrySide = -1;
        if (cameFrom != -1)
        {
            if (hex.getTerrain().hasStartList())
            {
                entrySide = 3;
            }
            else
            {
                entrySide = (6 + cameFrom - hex.getLabelSide()) % 6;
            }
        }
        return EntrySide.values()[entrySide];
    }

    protected static int findBlock(MasterHex hex)
    {
        int block = Constants.ARCHES_AND_ARROWS;
        for (int j = 0; j < 6; j++)
        {
            if (hex.getExitType(j) == Constants.HexsideGates.BLOCK)
            {
                // Only this path is allowed.
                block = j;
            }
        }
        return block;
    }

    /** Recursively find all unoccupied hexes within roll hexes, for
     *  tower teleport. */
    protected Set<MasterHex> findNearbyUnoccupiedHexes(MasterHex hex,
        Legion legion, int roll, int cameFrom)
    {
        // This hex is the final destination.  Mark it as legal if
        // it is unoccupied.
        Set<MasterHex> result = new HashSet<MasterHex>();

        if (!game.isOccupied(hex))
        {
            result.add(hex);
        }

        if (roll > 0)
        {
            for (int i = 0; i < 6; i++)
            {
                if (i != cameFrom
                    && (hex.getExitType(i) != Constants.HexsideGates.NONE || hex
                        .getEntranceType(i) != Constants.HexsideGates.NONE))
                {
                    result.addAll(findNearbyUnoccupiedHexes(
                        hex.getNeighbor(i), legion, roll - 1, (i + 3) % 6));
                }
            }
        }

        return result;
    }

    public boolean titanTeleportAllowed()
    {
        if (options.getOption(Options.noTitanTeleport))
        {
            return false;
        }
        if (game.getTurnNumber() == 1
            && options.getOption(Options.noFirstTurnTeleport))
        {
            return false;
        }
        return true;
    }

    protected boolean towerTeleportAllowed()
    {
        if (options.getOption(Options.noTowerTeleport))
        {
            return false;
        }
        if (game.getTurnNumber() == 1
            && options.getOption(Options.noFirstTurnTeleport))
        {
            return false;
        }
        return true;
    }

    protected boolean towerToTowerTeleportAllowed()
    {
        if (!towerTeleportAllowed())
        {
            return false;
        }
        if (game.getTurnNumber() == 1
            && options.getOption(Options.noFirstTurnT2TTeleport))
        {
            return false;
        }
        return true;
    }

    protected boolean towerToNonTowerTeleportAllowed()
    {
        if (!towerTeleportAllowed())
        {
            return false;
        }
        if (options.getOption(Options.towerToTowerTeleportOnly))
        {
            return false;
        }
        return true;
    }

    /** Return set of hexLabels describing where this legion can teleport.
     *  @return set of hexlabels
     */
    protected Set<MasterHex> listTeleportMoves(Legion legion, MasterHex hex,
        int movementRoll, boolean inAdvance)
    {
        Player player = legion.getPlayer();
        Set<MasterHex> result = new HashSet<MasterHex>();

        if (hex == null)
        {
            LOGGER.warning("listTeleportMoves called with null hex!");
            return result;
        }

        if ((!inAdvance && (movementRoll != 6 || legion.hasMoved() || player
            .hasTeleported())))
        {
            return result;
        }

        // Tower teleport
        if (hex.getTerrain().isTower() && legion.numLords() > 0
            && towerTeleportAllowed())
        {
            // Mark every unoccupied hex within 6 hexes.
            if (towerToNonTowerTeleportAllowed())
            {
                result.addAll(findNearbyUnoccupiedHexes(hex, legion, 6,
                    Constants.NOWHERE));
            }

            if (towerToTowerTeleportAllowed())
            {
                // Mark every unoccupied tower.
                for (MasterHex tower : game.getVariant().getMasterBoard()
                    .getTowerSet())
                {
                    if (!game.isOccupied(tower) && !(tower.equals(hex)))
                    {
                        result.add(tower);
                    }
                }
            }
            else
            {
                // Remove nearby towers from set.
                result.removeAll(game.getVariant().getMasterBoard()
                    .getTowerSet());
            }
        }

        // Titan teleport
        if (player.canTitanTeleport() && legion.hasTitan()
            && titanTeleportAllowed())
        {
            // Mark every hex containing an enemy stack that does not
            // already contain a friendly stack.
            for (Legion other : game.getEnemyLegions(player))
            {
                MasterHex otherHex = other.getCurrentHex();
                if (!game.isEngagement(otherHex))
                {
                    result.add(otherHex);
                }
            }
        }
        result.remove(null);
        return result;
    }

}
