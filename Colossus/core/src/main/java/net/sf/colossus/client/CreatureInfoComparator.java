package net.sf.colossus.client;


import java.util.Comparator;

import net.sf.colossus.server.CreatureTypeServerSide;
import net.sf.colossus.server.VariantSupport;


/** Sort creatures in decreasing order of importance, then by creature
 *  name, then by certainty. */
final class CreatureInfoComparator implements Comparator<CreatureInfo>
{
    public int compare(CreatureInfo info1, CreatureInfo info2)
    {
        CreatureTypeServerSide creature1 = (CreatureTypeServerSide)VariantSupport.getCurrentVariant()
            .getCreatureByName(info1.getName());
        CreatureTypeServerSide creature2 = (CreatureTypeServerSide)VariantSupport.getCurrentVariant()
            .getCreatureByName(info2.getName());
        int diff = creature2.getKillValue() - creature1.getKillValue();
        if (diff != 0)
        {
            return diff;
        }
        diff = creature1.getName().compareTo(creature2.getName());
        if (diff != 0)
        {
            return diff;
        }
        if (info1.isCertain() && !info2.isCertain())
        {
            return -1;
        }
        if (info2.isCertain() && !info1.isCertain())
        {
            return 1;
        }
        return 0;
    }
}
