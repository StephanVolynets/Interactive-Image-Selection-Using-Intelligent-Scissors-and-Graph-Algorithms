package graph;

import java.util.*;

/**
 * A reference implementation of a MinQueue implemented using Java's `PriorityQueue` class.  Note
 * that this implementation's `addOrUpdate()` method is NOT time-efficient (O(N) cost).
 */
public class RefMinQueue<KeyType> implements MinQueue<KeyType> {

    /**
     * Pairs an element `key` with its associated priority `priority`.
     */
    private record Entry<KeyType>(KeyType key, int priority) implements Comparable<Entry<KeyType>> {

        @Override
        public int compareTo(Entry<KeyType> other) {
            return Integer.compare(priority, other.priority);
        }
    }

    /**
     * A standard library priority queue of "entries" that pair items and their (current)
     * priorities.
     */
    private final PriorityQueue<Entry<KeyType>> contents;

    /**
     * Create an empty priority queue.
     */
    public RefMinQueue() {
        contents = new PriorityQueue<>();
    }

    @Override
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    @Override
    public int size() {
        return contents.size();
    }

    @Override
    public KeyType get() {
        return contents.element().key();
    }

    @Override
    public int minPriority() {
        return contents.element().priority();
    }

    @Override
    public void addOrUpdate(KeyType key, int priority) {
        boolean removed = contents.removeIf(e -> e.key().equals(key));
        contents.add(new Entry<>(key, priority));
    }

    @Override
    public KeyType remove() {
        return contents.remove().key();
    }

    @Override
    public void clear() {
        contents.clear();
    }
}
