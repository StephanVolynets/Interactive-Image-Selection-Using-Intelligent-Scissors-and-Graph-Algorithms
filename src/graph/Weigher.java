package graph;

/**
 * Provides integer weights on edges (of type `EdgeType`) in a graph.
 */
public interface Weigher<EdgeType extends Edge> {

    /**
     * Return the weight of the edge `edge`.
     */
    int weight(EdgeType edge);
}
