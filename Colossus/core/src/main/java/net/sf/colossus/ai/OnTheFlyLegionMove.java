package net.sf.colossus.ai;


import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import java.util.logging.Logger;

import net.sf.colossus.client.CritterMove;
import net.sf.colossus.util.DevRandom;

/**
 * Horrible kludge to generate LegionMove on the fly, rather than pre-generating
 * them before evaluation. This is put into a Collection with brute force, and
 * is a good example of what not to do in java.
 * @version $Id$
 * @author Romain Dolbeau
 */
class OnTheFlyLegionMove implements Collection<LegionMove> {
    private static final Logger LOGGER = Logger.getLogger(OnTheFlyLegionMove.class.getName());
    private final List<List<CritterMove>> allCritterMoves;
    private final int mysize;
    
    OnTheFlyLegionMove(final List<List<CritterMove>> acm) {
        allCritterMoves = acm;

        long realcount = 1;
        for (List<CritterMove> lcm : allCritterMoves) {
            realcount *= lcm.size();
        }
        if (realcount > Integer.MAX_VALUE)
            mysize = Integer.MAX_VALUE;
        else
            mysize = (int)realcount;
    }

    int getDim() {
        return allCritterMoves.size();
    }

    class OnTheFlyLegionMoveIterator implements Iterator<LegionMove> {
        private final Set<LegionMove> alreadydone = new HashSet<LegionMove>();
        private final OnTheFlyLegionMove daddy;
        private LegionMove lastone = null;
        private final Random rand = new DevRandom();
        private final int[] indexes;
        private final int dim;

        OnTheFlyLegionMoveIterator(OnTheFlyLegionMove d) {
            daddy = d;
            dim = daddy.getDim();
            indexes = new int[dim];
        }

        public boolean hasNext() {
            if (alreadydone.size() < daddy.size())
                return true;
            return false;
        }
        
        public LegionMove next() {
            // BEGIN OBSOLETE COMMENT, it can't work with a TreeSet (contains() !)
            // each newly generated LegionMove is put in lastone,
            // and is only added to alreadydone when we generate
            // the next LegionMove. Why? Because our TreeSet uses
            // value to sort, and it won't be set until later.
            // And you really don't want to scr*w up the sort order
            // when the elements are already in the TreeSet.
            // END OBSOLETE COMMENT
            // TODO: replace by a genetic algorithm :-)
            if (lastone != null)
                alreadydone.add(lastone);
            lastone = null;
            int ntry = 0;
            while ((lastone == null) && (ntry < 100)) {
                ntry ++;
                for (int i = 0 ; i < dim ; i++) {
                    indexes[i] = rand.nextInt(daddy.allCritterMoves.get(i).size());
                }
                LegionMove temp = SimpleAI.makeLegionMove(indexes, daddy.allCritterMoves);
                if (!alreadydone.contains(temp))
                    lastone = temp;
            }

            if (lastone == null) { // make one up...
                for (int i = 0 ; i < dim ; i++) {
                    indexes[i] = rand.nextInt(daddy.allCritterMoves.get(i).size());
                }
                lastone = SimpleAI.makeLegionMove(indexes, daddy.allCritterMoves);
            }
            
            return lastone;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public boolean	add(LegionMove o) {
        //Ensures that this collection contains the specified element (optional operation).
        throw new UnsupportedOperationException();
    }
    
    public boolean	addAll(Collection<? extends LegionMove> c) {
        //Adds all of the elements in the specified collection to this collection (optional operation).
        throw new UnsupportedOperationException();
    }

    public void	clear() {
        //Removes all of the elements from this collection (optional operation).
        throw new UnsupportedOperationException();
    }

    public boolean	contains(Object o) {
        // Returns true if this collection contains the specified element.
        LOGGER.warning(" should be implemented ...");
        return false;
    }

    public boolean	containsAll(Collection<?> c) {
        //Returns true if this collection contains all of the elements in the specified collection.
        LOGGER.warning(" should be implemented ...");
        return false;
    }

    public boolean	equals(Object o) {
        //Compares the specified object with this collection for equality.
        LOGGER.warning(" should be implemented ...");
        return false;
    }
    public int	hashCode() {
        //Returns the hash code value for this collection.
        return super.hashCode();
    }
    public boolean	isEmpty() {
        //Returns true if this collection contains no elements.
        return allCritterMoves.isEmpty();
    }

    public Iterator<LegionMove>	iterator() {
        //Returns an iterator over the elements in this collection.
        return new OnTheFlyLegionMoveIterator(this);
    }

    public boolean	remove(Object o) {
        // Removes a single instance of the specified element from this collection, if it is present (optional operation).
        throw new UnsupportedOperationException();
    }

    public boolean	removeAll(Collection<?> c) {
        // Removes all this collection's elements that are also contained in the specified collection (optional operation).
        throw new UnsupportedOperationException();
    }
    public boolean	retainAll(Collection<?> c) {
        //Retains only the elements in this collection that are contained in the specified collection (optional operation).
        throw new UnsupportedOperationException();
    }
    public int	size() {
        //Returns the number of elements in this collection.
        return mysize;
    }
    public Object[]	toArray() {
        //Returns an array containing all of the elements in this collection.
        LOGGER.warning(" should be implemented ...");
        return null;
    }
    public <T> T[] toArray(T[] a) {
        //Returns an array containing all of the elements in this collection; the runtime type of the returned array is that of the specified array.
        LOGGER.warning(" should be implemented ...");
        return null;
    }
}
