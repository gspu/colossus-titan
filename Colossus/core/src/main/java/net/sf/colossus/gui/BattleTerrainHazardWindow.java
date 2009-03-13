package net.sf.colossus.gui;


import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.colossus.client.Client;
import net.sf.colossus.util.KDialog;
import net.sf.colossus.variant.BattleHex;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.HazardConstants;
import net.sf.colossus.variant.HazardHexside;
import net.sf.colossus.variant.HazardTerrain;
import net.sf.colossus.variant.Hazards;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.variant.Variant;


/**
 * Class BattleTerrainHazardWindow shows a GUI representation of the 
 * Hazard Chart 
 * This is still ALPHA.
 * @version $Id: BattleTerrainHazardWindow.java 2975 2008-01-06 10:34:55Z peterbecker $
 * @author Dranathi
 */
public class BattleTerrainHazardWindow extends KDialog
{
    private static final int HEX_SIZE = 15;
    private static final int EFFECT_SIZE = 20;
    private static final int CREATURE_SIZE = 30;
    private static final int STRIKE_SIZE = 15;

    private static GridBagConstraints GBC_DEFAULT = new GridBagConstraints();
    static
    {
        GBC_DEFAULT.anchor = GridBagConstraints.NORTH;
        GBC_DEFAULT.insets = new Insets(2, 2, 5, 2);
        GBC_DEFAULT.weightx = 0.01;
    }
    private static GridBagConstraints GBC_NORTHWEST = (GridBagConstraints)GBC_DEFAULT
        .clone();
    static
    {
        GBC_NORTHWEST.anchor = GridBagConstraints.NORTHWEST;
    }
    private static GridBagConstraints GBC_NORTHEAST = (GridBagConstraints)GBC_DEFAULT
        .clone();
    static
    {
        GBC_NORTHEAST.anchor = GridBagConstraints.NORTHEAST;
    }

    private final MasterHex hex;
    private final Variant variant;
    private final List<CreatureType> creatures;
    private Map<String, HazardTerrain> hazardsDisplayed;
    private Map<String, HazardHexside> hexsidesDisplayed;

    public BattleTerrainHazardWindow(JFrame frame, Client client, MasterHex hex)

    {
        super(frame, "Battle Terrain Hazards for "
            + hex.getTerrain().getDisplayName(), false);

        assert SwingUtilities.isEventDispatchThread() : "Constructor should be called only on the EDT";

        this.hex = hex;
        variant = client.getGame().getVariant();
        creatures = variant.getCreatureTypes();
        getContentPane().setLayout(new GridBagLayout());
        useSaveWindow(client.getOptions(), "BattleTerrainHazard", null);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                dispose();
            }
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setupHeader(getContentPane());
        setupChart(getContentPane());

        pack();
        setVisible(true);
    }

    private void setupHeader(Container container)
    {
        // to save extra indirections with subpanels some labels will be across two columns
        GridBagConstraints dblConstraints = (GridBagConstraints)GBC_DEFAULT
            .clone();
        dblConstraints.gridwidth = 2;

        // add headers
        container.add(new JPanel(), GBC_DEFAULT);
        container.add(new JLabel("Hex"), GBC_DEFAULT);
        container.add(new JLabel("Move"), dblConstraints);
        container.add(new JLabel("Natives"), GBC_DEFAULT);
        container.add(new JLabel("Strike"), dblConstraints);
        container.add(new JLabel("Defence"), dblConstraints);
        container.add(new JLabel("Special"), dblConstraints);

        // add an empty cell to finalize the row and eat extra space
        GridBagConstraints constraints = (GridBagConstraints)GBC_DEFAULT
            .clone();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.weightx = 1;
        container.add(new JPanel(), constraints);

    }

    private void setupChart(Container container)
    {
        hazardsDisplayed = new HashMap<String, HazardTerrain>();
        hexsidesDisplayed = new HashMap<String, HazardHexside>();
        for (HazardTerrain hazard : HazardTerrain.getAllHazardTerrains())
        {
            if (hazardsDisplayed.containsKey(hazard.getName())
                || hex.getTerrain().getHazardCount(hazard) == 0)
            {
                // Ignore
            }
            else
            {
                hazardsDisplayed.put(hazard.getName(), hazard);
                addHazard(container, hazard);
            }
        }
        for (HazardHexside hazard : HazardHexside.getAllHazardHexsides())
        {
            if ("nothing".equalsIgnoreCase(hazard.getName())
                || hexsidesDisplayed.containsKey(hazard.getName())
                || hex.getTerrain().getHazardSideCount(hazard.getCode()) == 0)
            {
                // Ignore
            }
            else
            {
                hexsidesDisplayed.put(hazard.getName(), hazard);
                addHazard(container, hazard);
            }
        }

        // add an empty row that can grow with spare vSpace
        GridBagConstraints vFillConstraints = new GridBagConstraints();
        vFillConstraints.gridx = 0;
        vFillConstraints.gridwidth = GridBagConstraints.REMAINDER;
        vFillConstraints.weighty = 1;
        container.add(new JPanel(), vFillConstraints);
    }

    private void addHazard(Container container, Hazards hazard)
    {
        // hex label is always first in row, aligned vCenter but left
        GridBagConstraints hexLabelConstraints = (GridBagConstraints)GBC_DEFAULT
            .clone();
        hexLabelConstraints.gridx = 0;
        hexLabelConstraints.anchor = GridBagConstraints.NORTHWEST;
        container.add(new JLabel(hazard.getName()), hexLabelConstraints);

        addHexImage(container, hazard);
        addMovementInfo(container, hazard);
        addNativesPanel(container, hazard);
        addStrikeInfo(container, hazard);
        addDefenderInfo(container, hazard);
        addSpecialInfo(container, hazard);

        // add an empty cell to finalize the row and eat extra space
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.weightx = 1;
        container.add(new JPanel(), constraints);

    }

    // Create GUI representation of Terrain 
    private void addHexImage(Container container, Hazards hazard)
    {
        GUIBattleHex hex = new GUIBattleHex(HEX_SIZE, 0, HEX_SIZE, container,
            0, 0);
        BattleHex model = hex.getHexModel();
        if (hazard instanceof HazardTerrain)
        {
            model.setTerrain((HazardTerrain)hazard);
        }
        else
        {
            model.setTerrain(HazardTerrain.getDefaultTerrain());
            // to see the hexsides (or at least most of them) we have to configure 
            // them on the neighbors
            // TODO top is broken, three are still missing
            // TODO for a full drawn one we would have to draw two hexes at least
            //      it might be easier (and better looking) to just draw the hexside
            GUIBattleHex neighborTop = new GUIBattleHex(HEX_SIZE, -4
                * HEX_SIZE, HEX_SIZE, container, 0, 0);
            configureHexModel((HazardHexside)hazard, neighborTop.getHexModel());
            hex.setNeighbor(0, neighborTop);
            GUIBattleHex neighborTopRight = new GUIBattleHex(4 * HEX_SIZE, -2
                * HEX_SIZE, HEX_SIZE, container, 0, 0);
            configureHexModel((HazardHexside)hazard, neighborTopRight
                .getHexModel());
            hex.setNeighbor(1, neighborTopRight);
            GUIBattleHex neighborBottomRight = new GUIBattleHex(4 * HEX_SIZE,
                2 * HEX_SIZE, HEX_SIZE, container, 0, 0);
            configureHexModel((HazardHexside)hazard, neighborBottomRight
                .getHexModel());
            hex.setNeighbor(2, neighborBottomRight);
        }
        hex.setHexModel(model);

        // we give this one some extra space around it
        GridBagConstraints constraints = (GridBagConstraints)GBC_DEFAULT
            .clone();
        constraints.insets = new Insets(5, 5, 5, 5);
        container.add(hex, constraints);
    }

    private void configureHexModel(HazardHexside hazard, BattleHex model)
    {
        model.setTerrain(HazardTerrain.getDefaultTerrain());
        for (int i = 0; i <= 5; i++)
        {
            model.setHexside(i, hazard.getCode());
        }
    }

    // Show Native critters;
    private void addNativesPanel(Container container, Hazards hazard)
    {
        JPanel nativePanel = new JPanel(new GridLayout(0, 6));
        Iterator<CreatureType> it = creatures.iterator();
        while (it.hasNext())
        {
            CreatureType creature = it.next();
            if (hazard instanceof HazardTerrain)
            {
                if (creature.isNativeIn((HazardTerrain)hazard))
                {
                    Chit chit = new Chit(CREATURE_SIZE, creature.getName());
                    chit.setToolTipText(creature.getName());
                    nativePanel.add(chit);
                }
            }
            else
            {
                if ((hazard.equals(HazardHexside.DUNE) && creature
                    .isNativeDune())
                    || (hazard.equals(HazardHexside.SLOPE) && creature
                        .isNativeSlope())
                    || (hazard.equals(HazardHexside.RIVER) && creature
                        .isNativeRiver()))
                {
                    Chit chit = new Chit(CREATURE_SIZE, creature.getName());
                    chit.setToolTipText(creature.getName());
                    nativePanel.add(chit);
                }
            }
        }
        container.add(nativePanel, GBC_DEFAULT);
    }

    // Effect on Movement
    private void addMovementInfo(Container container, Hazards hazard)
    {
        Chit flySymbol = null;
        if (hazard.effectOnFlyerMovement
            .equals(HazardConstants.EffectOnMovement.BLOCKALL))
        {
            flySymbol = new Chit(EFFECT_SIZE, "FlyingBlocked");
            flySymbol.setToolTipText("FlyingBlocked");
        }
        else if (hazard.effectOnFlyerMovement
            .equals(HazardConstants.EffectOnMovement.BLOCKFOREIGNER))
        {
            flySymbol = new Chit(EFFECT_SIZE, "FlyingNativeOnly");
            flySymbol.setToolTipText("Native Flyers Only");
        }
        else if (hazard.effectOnFlyerMovement
            .equals(HazardConstants.EffectOnMovement.SLOWALL))
        {
            flySymbol = new Chit(EFFECT_SIZE, "FlyingSlow");
            flySymbol.setToolTipText("Slows Flying Creatures");
        }
        else if (hazard.effectOnFlyerMovement
            .equals(HazardConstants.EffectOnMovement.SLOWFOREIGNER))
        {
            flySymbol = new Chit(EFFECT_SIZE, "FlyingNativeSlow");
            flySymbol.setToolTipText("Slows Non-Native Flying Creatures");
        }
        else
        {
            flySymbol = new Chit(EFFECT_SIZE, "FlyingAll");
            flySymbol.setToolTipText("No effect on Flying Creatures");
        }
        container.add(flySymbol, GBC_NORTHEAST);

        Chit groundSymbol = null;
        if (hazard.effectOnGroundMovement
            .equals(HazardConstants.EffectOnMovement.BLOCKALL))
        {
            groundSymbol = new Chit(EFFECT_SIZE, "GroundBlocked");
            groundSymbol.setToolTipText("Blocks Ground Movement");
        }
        else if (hazard.effectOnGroundMovement
            .equals(HazardConstants.EffectOnMovement.BLOCKFOREIGNER))
        {
            groundSymbol = new Chit(EFFECT_SIZE, "GroundNativeOnly");
            groundSymbol.setToolTipText("Only Natives may Occupy");
        }
        else if (hazard.effectOnGroundMovement
            .equals(HazardConstants.EffectOnMovement.SLOWFOREIGNER))
        {
            groundSymbol = new Chit(EFFECT_SIZE, "GroundNativeSlow");
            groundSymbol.setToolTipText("NonNatives Slowed");
        }
        else if (hazard.effectOnGroundMovement
            .equals(HazardConstants.EffectOnMovement.SLOWALL))
        {
            groundSymbol = new Chit(EFFECT_SIZE, "GroundAllSlow");
            groundSymbol.setToolTipText("Slows Ground Movement");
        }
        else
        {
            groundSymbol = new Chit(EFFECT_SIZE, "GroundAll");
            groundSymbol.setToolTipText("No Effect On Ground Movement");
        }
        container.add(groundSymbol, GBC_NORTHWEST);
    }

    private void addSpecialInfo(Container container, Hazards hazard)
    {
        Chit rangeStrikeSymbol;
        if (hazard.rangeStrikeSpecial
            .equals(HazardConstants.RangeStrikeSpecialEffect.RANGESTRIKEBLOCKED))
        {
            rangeStrikeSymbol = new Chit(EFFECT_SIZE, "RangeStrikeBlocked");
            rangeStrikeSymbol
                .setToolTipText("Blocks normal Rangestrikes-Magic is not blocked");
        }
        else if (hazard.rangeStrikeSpecial
            .equals(HazardConstants.RangeStrikeSpecialEffect.RANGESTRIKEWALL))
        {
            rangeStrikeSymbol = new Chit(EFFECT_SIZE, "RangeStrikeWall");
            rangeStrikeSymbol
                .setToolTipText("Blocks Rangestrikes unless Hazard is"
                    + "occupied by either the Rangestriker or the target.");
        }
        else if (hazard.rangeStrikeSpecial
            .equals(HazardConstants.RangeStrikeSpecialEffect.RANGESTRIKESKILLPENALTY))
        {
            rangeStrikeSymbol = new Chit(EFFECT_SIZE, "RangeStrikeSkill");
            rangeStrikeSymbol
                .setToolTipText("Non Natives to this Hazard sill lose 1 Skill"
                    + "for each hazard of this type being crossed.");
        }
        else
        {
            rangeStrikeSymbol = new Chit(EFFECT_SIZE, "RangeStrikeFree");
            rangeStrikeSymbol.setToolTipText("No effect on Rangestrikes.");
        }
        container.add(rangeStrikeSymbol);

        if (hazard.terrainSpecial
            .equals(HazardConstants.SpecialEffect.HEALTHDRAIN))
        {
            Chit special = new Chit(EFFECT_SIZE, "HealthDrain");
            special.setToolTipText("Non natives lose 1 health per turn");
        }
        if (hazard.terrainSpecial
            .equals(HazardConstants.SpecialEffect.HEALTHDRAIN_WATERDWELLER))
        {
            Chit special = new Chit(EFFECT_SIZE, "HealthDrain");
            special.setToolTipText("Water Dweller lose 1 health per turn");
        }
    }

    private void addDefenderInfo(Container container, Hazards hazard)
    {
        container.add(makeStrikeEffect("Defending",
            hazard.effectforAttackingFromTerrain, hazard.scopeForAttackEffect,
            hazard.attackEffectAdjustment), GBC_NORTHEAST);
        container.add(makeStrikeEffect("Being Rangestruck",
            hazard.effectforRangeStrikeFromTerrain,
            hazard.scopeForRangeStrikeEffect,
            hazard.rangeStrikeEffectAdjustment), GBC_NORTHWEST);
    }

    private void addStrikeInfo(Container container, Hazards hazard)
    {
        container.add(makeStrikeEffect("Attacking",
            hazard.effectforAttackingFromTerrain, hazard.scopeForAttackEffect,
            hazard.attackEffectAdjustment), GBC_NORTHEAST);
        container.add(makeStrikeEffect("Rangestriking",
            hazard.effectforRangeStrikeFromTerrain,
            hazard.scopeForRangeStrikeEffect,
            hazard.rangeStrikeEffectAdjustment), GBC_NORTHWEST);
    }

    private Chit makeStrikeEffect(String strike,
        HazardConstants.EffectOnStrike strikeEffect,
        HazardConstants.ScopeOfEffectOnStrike scope, int effectAdjustment)
    {
        String[] overlay;
        if ("Being Rangestruck".equals(strike)
            || "Rangestriking".equals(strike))
        {
            overlay = new String[1];
            overlay[0] = "RangestrikeBase";
        }
        else
        {
            overlay = null;
        }

        Chit strikeSymbol;
        if (strikeEffect.equals(HazardConstants.EffectOnStrike.BLOCKED))
        {
            strikeSymbol = new Chit(STRIKE_SIZE, "StrikeBlocked", overlay);
            strikeSymbol.setToolTipText(strike
                + " Across Hazard is not Possible");
        }
        else if (strikeEffect
            .equals(HazardConstants.EffectOnStrike.SKILLBONUS)
            || strikeEffect
                .equals(HazardConstants.EffectOnStrike.SKILLPENALTY))
        {
            if (strikeEffect
                .equals(HazardConstants.EffectOnStrike.SKILLPENALTY))
            {
                strikeSymbol = new StrikeDie(STRIKE_SIZE, effectAdjustment,
                    "Miss", overlay);
            }
            else
            {
                strikeSymbol = new StrikeDie(STRIKE_SIZE, effectAdjustment,
                    "Hit", overlay);
            }
            StringBuilder tip = new StringBuilder();
            if (scope.equals(HazardConstants.ScopeOfEffectOnStrike.FOREIGNERS)
                || scope
                    .equals(HazardConstants.ScopeOfEffectOnStrike.IMPERIALS))
            {
                tip.append("Non-Natives ");
            }
            else if (scope
                .equals(HazardConstants.ScopeOfEffectOnStrike.NATIVES)
                || scope
                    .equals(HazardConstants.ScopeOfEffectOnStrike.PATRIOTS))
            {
                tip.append("Natives ");
            }
            else
            {
                tip.append("Everyone ");
            }
            tip.append("have skill ");
            if (strikeEffect
                .equals(HazardConstants.EffectOnStrike.SKILLPENALTY))
            {
                tip.append("decreased by ");
            }
            else
            {
                tip.append("increased by ");
            }
            tip.append(effectAdjustment);
            tip.append(" when " + strike);
            if (scope.equals(HazardConstants.ScopeOfEffectOnStrike.IMPERIALS))
            {
                tip.append("Natives");
            }
            else if (scope
                .equals(HazardConstants.ScopeOfEffectOnStrike.PATRIOTS))
            {
                tip.append("Non-Natives");
            }
            strikeSymbol.setToolTipText(tip.toString());
        }
        else if (strikeEffect
            .equals(HazardConstants.EffectOnStrike.POWERBONUS)
            || strikeEffect
                .equals(HazardConstants.EffectOnStrike.POWERPENALTY))
        {
            strikeSymbol = new StrikeDie(STRIKE_SIZE, 1, "RedBlue", overlay);
            StringBuilder tip = new StringBuilder();
            if (scope.equals(HazardConstants.ScopeOfEffectOnStrike.FOREIGNERS)
                || scope
                    .equals(HazardConstants.ScopeOfEffectOnStrike.IMPERIALS))
            {
                tip.append("Non-Natives ");
            }
            else if (scope
                .equals(HazardConstants.ScopeOfEffectOnStrike.NATIVES)
                || scope
                    .equals(HazardConstants.ScopeOfEffectOnStrike.PATRIOTS))
            {
                tip.append("Natives ");
            }
            else
            {
                tip.append("Everyone ");
            }
            if (strikeEffect
                .equals(HazardConstants.EffectOnStrike.POWERPENALTY))
            {
                tip.append("loses ");
            }
            else
            {
                tip.append("gains ");
            }
            tip.append(effectAdjustment);
            tip.append(" dice when " + strike);
            if (scope.equals(HazardConstants.ScopeOfEffectOnStrike.IMPERIALS))
            {
                tip.append("Natives");
            }
            else if (scope
                .equals(HazardConstants.ScopeOfEffectOnStrike.PATRIOTS))
            {
                tip.append("Non-Natives");
            }
            strikeSymbol.setToolTipText(tip.toString());
        }
        else
        {
            strikeSymbol = new StrikeDie(STRIKE_SIZE, 0, "RedBlue", overlay);
            strikeSymbol.setToolTipText("Normal Strike");
        }
        return strikeSymbol;
    }
}