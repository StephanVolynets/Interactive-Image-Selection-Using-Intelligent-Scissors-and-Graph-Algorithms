package graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MinQueueTest {
    @DisplayName("WHEN a new MinQueue is created, THEN its size will be 0 AND it will be empty")
    @Test
    void testNew() {
        MinQueue<Integer> q = makeQueue();

        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
    }


    @DisplayName("GIVEN an empty MinQueue, WHEN an element is added, THEN its size will become 1 "
            + "AND it will no longer be empty")
    @Test
    void testAddToEmpty() {
        MinQueue<Integer> q = makeQueue();

        q.addOrUpdate(0, 0);
        assertEquals(1, q.size());
        assertFalse(q.isEmpty());
    }

    @DisplayName("GIVEN a non-empty MinQueue, WHEN a distinct element is added, "
            + "THEN its size will increase by 1")
    @Test
    void testAddDistinct() {
        // Make a non-empty queue by adding multiple distinct elements with non-distinct priorities
        MinQueue<Integer> q = makeQueue();
        for (int i = 0; i < 20; i += 1) {
            q.addOrUpdate(i, i/2);
        }
        int givenSize = q.size();
        assertEquals(20, givenSize);

        q.addOrUpdate(50, 25);
        assertEquals(givenSize + 1, q.size());
    }

    @DisplayName("GIVEN a non-empty MinQueue, WHEN it is cleared, THEN its size will be 0 "
            + "AND it will be empty")
    @Test
    void testClear() {
        // Make a non-empty queue by adding multiple distinct elements with non-distinct priorities
        MinQueue<Integer> q = makeQueue();
        for (int i = 0; i < 20; i += 1) {
            q.addOrUpdate(i, i/2);
        }

        q.clear();
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
    }

    @DisplayName("GIVEN a MinQueue containing an element x whose priority is not the minimum, "
            + "WHEN x's priority is updated to become the unique minimum, "
            + "THEN the queue's size will not change "
            + "AND getting the minimum-priority element will return x "
            + "AND getting the minimum priority will return x's updated priority")
    @Test
    void testUpdateReduce() {
        // Make a non-empty queue by adding multiple distinct elements with non-distinct priorities
        MinQueue<Integer> q = makeQueue();
        for (int i = 0; i < 20; i += 1) {
            q.addOrUpdate(i, i/2);
        }
        int givenSize = q.size();

        int x = 10;
        int xPri = -1;
        q.addOrUpdate(x, xPri);
        assertEquals(givenSize, q.size());
        assertEquals(x, q.get());
        assertEquals(xPri, q.minPriority());
    }

    @DisplayName("GIVEN a MinQueue containing an element x whose priority is the minimum, "
            + "WHEN x's priority is updated to become larger than another element y's, "
            + "THEN the queue's size will not change "
            + "AND getting the minimum-priority element will return y "
            + "AND getting the minimum priority will return y's priority")
    @Test
    void testUpdateIncrease() {
        // Make a non-empty queue by adding multiple distinct elements
        MinQueue<Integer> q = makeQueue();
        for (int i = 0; i < 20; i += 1) {
            q.addOrUpdate(i, i);
        }
        int givenSize = q.size();

        int x = 0;
        int xPri = 10;
        int y = 1;
        int yPri = 1;
        q.addOrUpdate(x, xPri);
        assertEquals(givenSize, q.size());
        assertEquals(y, q.get());
        assertEquals(yPri, q.minPriority());
    }

    @DisplayName("GIVEN a non-empty MinQueue, WHEN an element is removed,"
            + " THEN it size will decrease by 1.  IF its size was 1, THEN it will become empty.")
    @Test
    void testRemoveSize() {
        int givenSize = 20;
        MinQueue<Integer> q = makeQueue();
        for (int i = 0; i < givenSize; i += 1) {
            q.addOrUpdate(i, i/2);
        }

        for (int n = givenSize; n > 0; n -= 1) {
            assertFalse(q.isEmpty());
            q.remove();
            assertEquals(n - 1, q.size());
        }
        assertTrue(q.isEmpty());
    }

    @DisplayName("GIVEN a MinQueue containing elements whose priorities follow their natural "
            + "ordering, WHEN elements are successively removed, THEN they will be returned in "
            + "ascending order")
    @Test
    void testRemoveElementOrder() {
        MinQueue<Integer> q = makeQueue();
        int nElem = 20;

        // Add distinct elements in random order (priority equals element)
        {
            List<Integer> elems = new ArrayList<>();
            for (int i = 0; i < nElem; i += 1) {
                elems.add(i);
            }
            int seed = 1;
            Random rng = new Random(seed);
            Collections.shuffle(elems, rng);
            for (Integer x : elems) {
                q.addOrUpdate(x, x);
            }
        }

        // Remove elements and check order
        int prevElem = q.remove();
        for (int i = 1; i < nElem; ++i) {
            assertEquals(nElem - i, q.size());
            int nextElem = q.get();
            int removedElem = q.remove();
            assertEquals(nextElem, removedElem);
            assertTrue(nextElem > prevElem);
            prevElem = nextElem;
        }
        assertTrue(q.isEmpty());
    }

    @DisplayName("GIVEN a MinQueue (whose elements' priorities may have been updated), "
            + "WHEN elements are successively removed, "
            + "THEN the minimum priority will not decrease after each removal")
    @Test
    void testRemovePriorityOrder() {
        MinQueue<Integer> q = makeQueue();
        int nUpdates = 100;

        // Add random elements with random priorities to queue and randomly update some elements'
        //  priorities.
        int seed = 1;
        Random rng = new Random(seed);
        int bound = nUpdates/2;
        for (int i = 0; i < nUpdates; i += 1) {
            int key = rng.nextInt(bound);
            int priority = rng.nextInt(bound);
            q.addOrUpdate(key, priority);
        }

        // Remove until 1 left, but no more than nUpdates times (to prevent infinite loop in test)
        for (int i = 0; q.size() > 1 && i < nUpdates; i += 1) {
            int removedPriority = q.minPriority();
            q.remove();
            assertTrue(q.minPriority() >= removedPriority);
        }
        q.remove();
        assertTrue(q.isEmpty());
    }

    @DisplayName("GIVEN an empty MinQueue, WHEN attempting to query the next element "
            + "OR query the minimum priority OR remove the next element "
            + "THEN a NoSuchElementException will be thrown")
    @Test
    void testExceptions() {
        MinQueue<Integer> q = makeQueue();

        assertThrows(NoSuchElementException.class, () -> q.get());
        assertThrows(NoSuchElementException.class, () -> q.minPriority());
        assertThrows(NoSuchElementException.class, () -> q.remove());

        // Test again after adding and removing an element to ensure it doesn't only work just after
        //  construction.
        q.addOrUpdate(0, 0);
        q.remove();

        assertThrows(NoSuchElementException.class, () -> q.get());
        assertThrows(NoSuchElementException.class, () -> q.minPriority());
        assertThrows(NoSuchElementException.class, () -> q.remove());
    }

    /**
     * Helper method for constructing new, empty MinQueues.  Convenient for testing different
     * implementations.
     */
    static MinQueue<Integer> makeQueue() {
        return new HeapMinQueue<>();
    }
}