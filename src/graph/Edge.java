package graph;

/**
 * Represents a directed edge between two vertices in a graph.  Weights are not part of this
 * interface (see `Weigher` for a way to specify extrinsic weights).
 */
public interface Edge {

    /**
     * Return the ID of the vertex that this edge leaves from (the "source" vertex).
     */
    int startId();

    /**
     * Return the ID of the vertex that this edge leads to (the "destination" vertex).
     */
    int endId();
}
