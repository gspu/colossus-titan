/**
 * Class Critter represents an individual Titan Character.
 * @version $Id$
 * @author David Ripton
 */

class Critter extends Creature
{
    private boolean visible;
    private Creature creature;
    private Legion legion;

    private BattleMap map;
    private boolean moved = false;
    private boolean struck = false;

    private BattleHex currentHex;
    private BattleHex startingHex;

    // Damage taken
    private int hits = 0;

    // Mark whether this critter is a legal carry target.
    private boolean carryFlag = false;

    private BattleChit chit;


    Critter(Creature creature, boolean visible, Legion legion)
    {
        super(creature.name, creature.power, creature.skill, 
            creature.rangeStrikes, creature.flies, creature.nativeBramble, 
            creature.nativeDrift, creature.nativeBog, 
            creature.nativeSandDune, creature.nativeSlope, creature.lord, 
            creature.demilord, creature.count);

        if (name != null) 
        {
            this.creature = Creature.getCreatureFromName(name);
        }
        else
        {
            this.creature = null;
        }

        this.visible = visible; 
        this.legion = legion;
        if (getName().equals("Titan"))
        {
            setPower(getPlayer().getTitanPower());
        }
    }
    
    
    public void addBattleInfo(BattleHex hex, BattleMap map, BattleChit chit)
    {
        this.currentHex = hex;
        this.startingHex = hex;
        this.map = map;
        this.chit = chit;
    }
    
    
    public boolean isVisible()
    {
        return visible;
    }


    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }


    public Creature getCreature()
    {
        return creature;
    }
    
    
    public Legion getLegion()
    {
        return legion;
    }
    
    
    public BattleChit getChit()
    {
        return chit;
    }


    // All count-related functions must use the Creature archetype,
    // not this copy.

    public int getCount()
    {
        return creature.getCount();
    }


    public void setCount(int count)
    {
        creature.setCount(count);
    }


    public void takeOne()
    {
        creature.takeOne();
    }


    public void putOneBack()
    {
        creature.putOneBack();
    }


    // File.separator does not work right in jar files.  A hardcoded 
    // forward-slash does, and works in *x and Windows.  I have
    // no idea if it works on the Mac, etc.
    public String getImageName(boolean inverted)
    {
        String myName;
        if (getName().equals("Titan") && getPower() >= 6 && getPower() <= 20)
        {
            // Use Titan14.gif for a 14-power titan, etc.  Use the generic
            // Titan.gif (with X-4) for ridiculously big titans, to avoid 
            // the need for an infinite number of images.
            
            myName = getName() + getPower();
        }
        else
        {
            myName = name;
        }

        if (inverted)
        {
            return "images/i_" + myName + ".gif";
        }
        else
        {
            return "images/" + myName + ".gif";
        }
    }


    public String getImageName()
    {
        return getImageName(false);
    }



    public int getPower()
    {
        // Update Titan power if necessary.
        if (getName().equals("Titan"))
        {
            setPower(getPlayer().getTitanPower());
        }

        return power;
    }


    public int getPointValue()
    {
        return getPower() * skill;
    }


    public int getHits()
    {
        return hits;
    }


    public void setHits(int damage)
    {
        hits = damage;
    }
    
    
    public void heal()
    {
        setHits(0);
    }


    public void checkForDeath()
    {
        if (hits >= getPower())
        {
            setDead(true);
            currentHex.repaint();
        }
    }


    public boolean hasMoved()
    {
        return moved;
    }


    public void commitMove()
    {
        startingHex = currentHex;
        moved = false;
    }


    public boolean hasStruck()
    {
        return struck;
    }


    public void commitStrike()
    {
        struck = false;
    }


    public BattleHex getCurrentHex()
    {
        return currentHex;
    }


    public BattleHex getStartingHex()
    {
        return startingHex;
    }

    
    // Dead critters count as being in contact only if countDead is true.
    public int numInContact(boolean countDead)
    {
        // Offboard creatures are not in contact.
        if (currentHex.isEntrance())
        {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < 6; i++)
        {
            // Adjacent creatures separated by a cliff are not in contact.
            if (currentHex.getHexside(i) != 'c' &&
                currentHex.getOppositeHexside(i) != 'c')
            {
                BattleHex hex = currentHex.getNeighbor(i);
                if (hex != null)
                {
                    if (hex.isOccupied())
                    {
                        Critter other = hex.getCritter();
                        if (other.getPlayer() != getPlayer() &&
                            (countDead || !other.isDead()))
                        {
                            count++;
                        }
                    }
                }
            }
        }

        return count;
    }


    // Dead critters count as being in contact only if countDead is true.
    public boolean inContact(boolean countDead)
    {
        return (numInContact(countDead) > 0);
    }


    public void moveToHex(BattleHex hex)
    {
        currentHex.removeCritter(this);
        currentHex = hex;
        currentHex.addCritter(this);
        moved = true;
        map.markLastCritterMoved(this);
        map.repaint();
    }


    public void undoMove()
    {
        currentHex.removeCritter(this);
        currentHex = startingHex;
        currentHex.addCritter(this);
        moved = false;
        map.clearLastCritterMoved();
        map.repaint();
    }


    // Return the number of dice that will be rolled when striking this
    // target, including modifications for terrain.
    public int getDice(Critter target)
    {
        BattleHex targetHex = target.getCurrentHex();

        int dice = getPower();

        boolean rangestrike = !inContact(true);
        if (rangestrike)
        {
            dice /= 2;

            // Dragon rangestriking from volcano: +2
            if (getName().equals("Dragon") && 
                currentHex.getTerrain() == 'v')
            {
                dice += 2;
            }
        }
        else
        {
            // Dice can be modified by terrain.
            // Dragon striking from volcano: +2
            if (getName().equals("Dragon") && 
                currentHex.getTerrain() == 'v')
            {
                dice += 2;
            }

            // Adjacent hex, so only one possible direction.
            int direction = map.getDirection(currentHex, targetHex, false);
            char hexside = currentHex.getHexside(direction);
            char oppHexside = currentHex.getOppositeHexside(direction);

            // Native striking down a dune hexside: +2
            if (hexside == 'd' && isNativeSandDune())
            {
                dice += 2;
            }
            // Native striking down a slope hexside: +1
            else if (hexside == 's' && isNativeSlope())
            {
                dice++;
            }
            // Non-native striking up a dune hexside: -1
            else if (oppHexside == 'd' && !isNativeSandDune())
            {
                dice--;
            }
        }

        return dice;
    }


    public int getAttackerSkill(Critter target)
    {
        BattleHex targetHex = target.getCurrentHex();

        int attackerSkill = getSkill();

        boolean rangestrike = !inContact(true);

        // Skill can be modified by terrain.
        if (!rangestrike)
        {
            // Non-native striking out of bramble: -1
            if (currentHex.getTerrain() == 'r' && !isNativeBramble())
            {
                attackerSkill--;
            }

            if (currentHex.getElevation() > targetHex.getElevation())
            {
                // Adjacent hex, so only one possible direction.
                int direction = map.getDirection(currentHex, targetHex, false);
                char hexside = currentHex.getHexside(direction);
                // Striking down across wall: +1
                if (hexside == 'w')
                {
                    attackerSkill++;
                }
            }
            else if (currentHex.getElevation() < targetHex.getElevation())
            {
                // Adjacent hex, so only one possible direction.
                int direction = map.getDirection(targetHex, currentHex, false);
                char hexside = targetHex.getHexside(direction);
                // Non-native striking up slope: -1
                // Striking up across wall: -1
                if ((hexside == 's' && !isNativeSlope()) ||
                    hexside == 'w')
                {
                    attackerSkill--;
                }
            }

        }
        else if (!getName().equals("Warlock"))
        {
            // Range penalty
            if (map.getRange(currentHex, targetHex) == 4)
            {
                attackerSkill--;
            }

            // Non-native rangestrikes: -1 per intervening bramble hex
            if (!isNativeBramble())
            {
                attackerSkill -= map.countBrambleHexes(currentHex, targetHex);
            }

            // Rangestrike up across wall: -1 per wall
            boolean wall = false;
            for (int i = 0; i < 6; i++)
            {
                if (targetHex.getHexside(i) == 'w')
                {
                    wall = true;
                }
            }
            if (wall)
            {
                if (targetHex.getElevation() > currentHex.getElevation())
                {
                    attackerSkill -= (targetHex.getElevation() -
                        currentHex.getElevation());
                }
            }

            // Rangestrike into volcano: -1
            if (targetHex.getTerrain() == 'v')
            {
                attackerSkill--;
            }
        }

        return attackerSkill;
    }


    public int getStrikeNumber(Critter target)
    {
        BattleHex targetHex = target.getCurrentHex();
        boolean rangestrike = !inContact(true);

        int attackerSkill = getAttackerSkill(target);
        int defenderSkill = target.getSkill();

        int strikeNumber = 4 - attackerSkill + defenderSkill;

        // Strike number can be modified directly by terrain.
        // Native defending in bramble, from strike by a non-native: +1
        // Native defending in bramble, from rangestrike by a non-native
        //     non-warlock: +1
        if (targetHex.getTerrain() == 'r' &&
            target.isNativeBramble() &&
            !isNativeBramble() &&
            !(rangestrike && getName().equals("Warlock")))
        {
            strikeNumber++;
        }

        // Sixes always hit.
        if (strikeNumber > 6)
        {
            strikeNumber = 6;
        }

        return strikeNumber;
    }


    // Allow the player to choose whether to take a penalty
    // (fewer dice or higher strike number) in order to be
    // allowed to carry.  Return true if the penalty is taken,
    // or false if it is not.
    private boolean chooseStrikePenalty(Critter [] carryTargets)
    {
        StringBuffer prompt = new StringBuffer(
            "Take strike penalty to allow carrying to ");

        for (int i = 0; i < carryTargets.length; i++)
        {
            prompt.append(carryTargets[i].getName() + " in " +
                carryTargets[i].getCurrentHex().getTerrainName().toLowerCase());
            if (i < carryTargets.length - 1)
            {
                prompt.append(", ");
            }
        }
        prompt.append("?");

        new OptionDialog(map, "Take Strike Penalty?", prompt.toString(), 
            "Take Penalty", "Do Not Take Penalty");

        return (OptionDialog.getLastAnswer() == OptionDialog.YES_OPTION);
    }


    // Sort penalty options by number of dice (ascending), then by
    //    strike number (descending).
    private void sortPenaltyOptions(PenaltyOption [] penaltyOptions,
        int numPenaltyOptions)
    {
        for (int i = 0; i < numPenaltyOptions - 1; i++)
        {
            for (int j = i + 1; j < numPenaltyOptions; j++)
            {
                if (penaltyOptions[i].compareTo(penaltyOptions[j]) > 0)
                {
                    PenaltyOption temp = penaltyOptions[i];
                    penaltyOptions[i] = penaltyOptions[j];
                    penaltyOptions[j] = temp;
                }
            }
        }
    }


    // Calculate number of dice and strike number needed to hit target,
    // and whether any carries are possible.  Roll the dice and apply
    // damage.  Highlight legal carry targets.
    public void strike(Critter target)
    {
        // Sanity check
        if (target.getPlayer() == getPlayer())
        {
            System.out.println("Tried to strike own creature!");
            return;
        }

        BattleHex targetHex = target.getCurrentHex();

        boolean carryPossible = true;
        if (numInContact(false) < 2)
        {
            carryPossible = false;
        }

        int dice = getDice(target);

        // Carries are only possible if the striker is rolling more dice than
        // the target has hits remaining.
        if (dice <= target.getPower() - target.getHits())
        {
            carryPossible = false;
        }

        int strikeNumber = getStrikeNumber(target);

        // Figure whether number of dice or strike number needs to be
        // penalized in order to carry.
        if (carryPossible)
        {
            // Count legal carry targets.
            int numCarryTargets = 0;

            // Count strike penalty options
            int numPenaltyOptions = 0;

            PenaltyOption [] penaltyOptions = new PenaltyOption[5];

            for (int i = 0; i < 6; i++)
            {
                // Adjacent creatures separated by a cliff are not in contact.
                if (currentHex.getHexside(i) != 'c' &&
                    currentHex.getOppositeHexside(i) != 'c')
                {
                    BattleHex hex = currentHex.getNeighbor(i);
                    if (hex != null && hex != targetHex && hex.isOccupied())
                    {
                        Critter critter = hex.getCritter();
                        if (critter.getPlayer() != getPlayer() && !critter.isDead())
                        {
                            int tmpDice = getDice(critter);
                            int tmpStrikeNumber = getStrikeNumber(critter);

                            // Strikes not up across dune hexsides cannot
                            // carry up across dune hexsides.
                            if (currentHex.getOppositeHexside(i) == 'd')
                            {
                                int direction = map.getDirection(targetHex,
                                    currentHex, false);
                                if (targetHex.getHexside(direction) != 'd')
                                {
                                    critter.setCarryFlag(false);
                                }
                            }

                            else if (tmpStrikeNumber > strikeNumber ||
                                tmpDice < dice)
                            {
                                // Add this scenario to the list. 
                                penaltyOptions[numPenaltyOptions] = new 
                                    PenaltyOption(critter, tmpDice, 
                                    tmpStrikeNumber);
                                numPenaltyOptions++;
                            }

                            else
                            {
                                critter.setCarryFlag(true);
                                numCarryTargets++;
                            }
                        }
                    }
                }
            }


            // Sort penalty options by number of dice (ascending), then by
            //    strike number (descending).
            sortPenaltyOptions(penaltyOptions, numPenaltyOptions);


            // Find the group of PenaltyOptions with identical dice and
            //    strike numbers.

            int last = -1;

            while (++last < numPenaltyOptions)
            {
                int first = last; 
                while (last + 1 < numPenaltyOptions && 
                    penaltyOptions[last + 1].getDice() == 
                        penaltyOptions[first].getDice() &&
                    penaltyOptions[last + 1].getStrikeNumber() ==
                        penaltyOptions[first].getStrikeNumber())
                {
                    last++;
                }

                int tmpDice = penaltyOptions[first].getDice();
                int tmpStrikeNumber = penaltyOptions[first].getStrikeNumber();

                // Make sure the penalty is still relevant.
                if (tmpStrikeNumber > strikeNumber || tmpDice < dice) 
                {
                    Critter [] critters = new Critter[last - first + 1];
                    for (int i = first; i <= last; i++)
                    {
                        critters[i - first] = penaltyOptions[i].getCritter();
                    }
                                    
                    if (chooseStrikePenalty(critters))
                    {
                        if (tmpStrikeNumber > strikeNumber)
                        {
                            strikeNumber = tmpStrikeNumber;
                        }
                        if (tmpDice < dice)
                        {
                            dice = tmpDice;
                        }
                        for (int i = 0; i < critters.length; i++)
                        {
                            critters[i].setCarryFlag(true);
                            numCarryTargets++;
                        }
                    }
                }
            }


            if (numCarryTargets == 0)
            {
                carryPossible = false;
            }
        }


        // Roll the dice.
        int damage = 0;

        int [] rolls = new int[dice];
        for (int i = 0; i < dice; i++)
        {
            rolls[i] = (int) Math.ceil(6 * Math.random());

            if (rolls[i] >= strikeNumber)
            {
                damage++;
            }
        }

        int totalDamage = target.getHits();
        totalDamage += damage;
        int carryDamage = 0;
        int power = target.getPower();
        if (totalDamage > power)
        {
            if (carryPossible)
            {
                carryDamage = totalDamage - power;
            }
            totalDamage = power;
        }
        target.setHits(totalDamage);
        target.checkForDeath();

        target.getChit().repaint();

        // Let the attacker choose whether to carry, if applicable.
        if (carryDamage > 0)
        {
            map.highlightCarries(carryDamage);
        }

        // Record that this attacker has struck.
        struck = true;

        // Display the rolls in the showDice dialog.
        ShowDice showDice = map.getShowDice();
        showDice.setAttacker(this);
        showDice.setDefender(target);
        showDice.setTargetNumber(strikeNumber);
        showDice.setRolls(rolls);
        showDice.setHits(damage);
        showDice.setCarries(carryDamage);
        showDice.setup();
    }


    public boolean getCarryFlag()
    {
        return carryFlag;
    }


    public void setCarryFlag(boolean flag)
    {
        carryFlag = flag;
    }


    public boolean isDead()
    {
        return (getHits() >= getPower());
    }


    public void setDead(boolean dead)
    {
        if (dead) 
        {
            setHits(getPower());
        }
        chit.setDead(dead);
    }


    public Player getPlayer()
    {
        return legion.getPlayer();
    }
}
