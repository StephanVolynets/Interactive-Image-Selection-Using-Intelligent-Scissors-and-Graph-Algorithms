package graph;

/**
 * Represents a directed graph whose vertices are labeled by integer IDs.  Edge weights are
 * _extrinsic_ (not part of this interface); see `Weigher`.  IDs must be in the range
 * `[0..vertexCount())`.
 */
public interface Graph<VertexType extends Vertex<?>> {

    /**
     * Return the number of vertices in this graph.
     */
    int vertexCount();

    /**
     * Return the Vertex in this graph with ID `id`.  Throw NoSuchElementException` if no vertex
     * with that ID is in this graph.
     */
    VertexType getVertex(int id);
}
