package graph;

/**
 * A min priority queue of distinct elements of type `KeyType` associated with (extrinsic) integer
 * priorities.
 */
interface MinQueue<KeyType> {
    /**
     * Return whether this queue contains no elements.
     */
    boolean isEmpty();

    /**
     * Return the number of elements contained in this queue.
     */
    int size();

    /**
     * Return an element associated with the smallest priority in this queue.  This is the same
     * element that would be removed by a call to `remove()` (assuming no mutations in between).
     * Throws NoSuchElementException if this queue is empty.
     */
    KeyType get();

    /**
     * Return the minimum priority associated with an element in this queue.  Throws
     * NoSuchElementException if this queue is empty.
     */
    int minPriority();

    /**
     * If `key` is already contained in this queue, change its associated priority to `priority`.
     * Otherwise, add it to this queue with that priority.
     */
    void addOrUpdate(KeyType key, int priority);

    /**
     * Remove and return the element associated with the smallest priority in this queue.  If
     * multiple elements are tied for the smallest priority, an arbitrary one will be removed.
     * Throws NoSuchElementException if this queue is empty.
     */
    KeyType remove();

    /**
     * Remove all elements from this queue (making it empty).
     */
    void clear();
}
