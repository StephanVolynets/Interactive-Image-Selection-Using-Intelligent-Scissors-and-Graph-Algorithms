package graph;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Supports incrementally solving for shortest paths from a starting vertex in a graph with vertices
 * of type `VertexType` and edges of type `EdgeType`.  Can provide preliminary pathfinding results
 * and report progress towards a full solution.
 */
public class ShortestPaths<VertexType extends Vertex<EdgeType>, EdgeType extends Edge> {

    /**
     * The graph we are searching for paths in.
     */
    private final Graph<VertexType> graph;

    /**
     * Weigher to use in order to query edge weights in our graph.
     */
    private final Weigher<EdgeType> weigher;

    /**
     * The ID of the starting vertex for our current search, or -1 if no starting vertex has yet
     * been selected.
     */
    private int startId;

    /**
     * `distances[id]` is the weight of the shortest known path from our starting vertex to the
     * vertex with ID `id`, or -1 if no such path is currently known.
     */
    private final int[] distances;

    /**
     * `predecessors[id]` is the ID of the penultimate vertex along the shortest known path from our
     * starting vertex to the vertex with ID `id`, or -1 if `id==startId` or if no such path is
     * currently known.
     */
    private final int[] predecessors;

    /**
     * Queue of vertex IDs currently known to be reachable from the starting vertex but for whom the
     * shortest possible path has not yet been determined.  Ordered by weight of the shortest known
     * path from the starting vertex.
     */
    private final MinQueue<Integer> frontier;

    /**
     * `settledIds.get(id)` is true if the shortest path has been determined from our starting
     * vertex to the vertex with ID `id`.
     */
    private final BitSet settledIds;

    /**
     * Create a new shortest paths solver for the graph `graph` whose edge weights are determined by
     * `weigher`.
     */
    public ShortestPaths(Graph<VertexType> graph, Weigher<EdgeType> weigher) {
        this.graph = graph;
        this.weigher = weigher;
        distances = new int[graph.vertexCount()];
        predecessors = new int[graph.vertexCount()];
        settledIds = new BitSet(graph.vertexCount());

        // TODO A6.3f: Replace this with a `HeapMinQueue` after implementing and testing the latter.
        frontier = new HeapMinQueue<>();

        reset();
    }

    /**
     * Clear any intermediate pathfinding results in preparation for finding paths from a new
     * starting point.
     */
    private void reset() {
        Arrays.fill(distances, -1);
        Arrays.fill(predecessors, -1);
        frontier.clear();
        settledIds.clear();
        startId = -1;
    }

    /**
     * Return the number of vertices for which the final shortest paths from the current starting
     * point are known.
     */
    public int settledCount() {
        return settledIds.cardinality();
    }

    /**
     * Return the total number of vertices in the graph we are finding shortest paths in.
     */
    public int vertexCount() {
        return graph.vertexCount();
    }

    /**
     * Return whether shortest paths have been found for all vertices reachable from the current
     * starting point.  Returns false if no starting point has been set.
     */
    public boolean allPathsFound() {
        // The second condition is needed in case we were just constructed and have no starting
        //  point.
        return frontier.isEmpty() && startId >= 0;
    }

    /**
     * Change our starting point to `start`, discarding any pathfinding results from any previous
     * starting point.
     */
    public void setStart(int startId) {
        reset();
        this.startId = startId;
        distances[startId] = 0;
        frontier.addOrUpdate(startId, 0);
    }

    /**
     * Find the shortest paths from `start` to every vertex in our graph reachable from `start`,
     * returning an object from which those paths can be queried.
     */
    public PathfindingSnapshot findAllPaths(int startId) {
        setStart(startId);
        PathfindingSnapshot paths = extendSearch(vertexCount());
        assert allPathsFound();
        return paths;
    }

    /**
     * Find the shortest paths from our current starting point to the next `maxToSettle` closest
     * vertices for which shortest paths are not yet known.  Results can be queried from the
     * returned object, which will also provide candidate paths to farther "frontier" vertices found
     * during the pathfinding process.
     * <p>
     * If fewer than `maxToSettle` reachable vertices do not have the shortest paths to them known,
     * then this method returns when shortest paths have been found for all reachable vertices in
     * the graph.  If multiple vertices tie for being the next closest, it is unspecified which will
     * have its shortest path found first.  Requires `maxToSettle` is non-negative and that a
     * starting vertex has been set.
     */
    public PathfindingSnapshot extendSearch(int maxToSettle) {
        assert startId >= 0;

        int settledVertices = 0;

        while (settledVertices < maxToSettle && !frontier.isEmpty()) {
            int currentVertexId = frontier.remove();
            VertexType currentVertex = graph.getVertex(currentVertexId);

            for (EdgeType edge : currentVertex.outgoingEdges()) {
                int neighborId = edge.endId();
                int tentativeDistance = distances[currentVertexId] + weigher.weight(edge);

                if (distances[neighborId] == -1 || tentativeDistance < distances[neighborId]) {
                    distances[neighborId] = tentativeDistance;
                    predecessors[neighborId] = currentVertexId;
                    frontier.addOrUpdate(neighborId, tentativeDistance);
                }
            }

            settledIds.set(currentVertexId);
            settledVertices++;
        }

        return new PathfindingSnapshot(startId, distances.clone(), predecessors.clone(), (BitSet) settledIds.clone());
    }
}
