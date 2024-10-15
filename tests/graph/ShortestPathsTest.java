package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortestPathsTest {
    /*
     * Text graph format ([weight] is optional):
     * Directed edge: startLabel -> endLabel [weight]
     * Undirected edge: startLabel -- endLabel [weight]
     */

    // Example graph from Prof. Myers's notes
    static final String graph1 = """
        A -> B 9
        A -> C 14
        A -> D 15
        B -> E 23
        C -> E 17
        C -> D 5
        C -> F 30
        D -> F 20
        D -> G 37
        E -> F 3
        E -> G 20
        F -> G 16""";

    // Bidirectional graph that will encounter a priority reduction that will reorder the frontier
    static final String graph2 = """
        A -- D 5
        D -- E 1
        B -- C 1
        A -- C 6
        C -- E 1
        A -- B 1
        A -- E 4""";

    @Test
    void testMyersExample() {
        SimpleGraph g = SimpleGraph.fromText(graph1);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Find all shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        PathfindingSnapshot paths = pathfinder.findAllPaths(start.id());
        assertTrue(pathfinder.allPathsFound());

        // Check distance of shortest path to "G"
        SimpleVertex end = g.getVertexByLabel("G");
        assertEquals(50, paths.distanceTo(end.id()));

        // All nodes are reachable from "A", so check that they are all discovered and settled.
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        for (int id = 0; id < g.vertexCount(); ++id) {
            assertTrue(paths.discovered(id));
            assertTrue(paths.settled(id));
        }

        // Check the (unique) shortest path from A to G
        List<Integer> path = paths.pathTo(end.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathLabels = path.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A", "C", "E", "F", "G"}, pathLabels);
    }

    @DisplayName("WHEN a change in priority changes the next node in the frontier")
    @Test
    void testPriorityInversion() {
        SimpleGraph g = SimpleGraph.fromText(graph2);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Find all shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        PathfindingSnapshot paths = pathfinder.findAllPaths(start.id());
        assertTrue(pathfinder.allPathsFound());

        // Check distance of shortest path to "G"
        SimpleVertex end = g.getVertexByLabel("D");
        assertEquals(4, paths.distanceTo(end.id()));

        // This graph is connected, so check that all vertices are discovered and settled
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        for (int id = 0; id < g.vertexCount(); ++id) {
            assertTrue(paths.discovered(id));
            assertTrue(paths.settled(id));
        }

        // Check the (unique) shortest path from A to D
        List<Integer> path = paths.pathTo(end.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathLabels = path.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A", "B", "C", "E", "D"}, pathLabels);
    }

    @DisplayName("WHEN the graph is disconnected")
    @Test
    void testDisconnected() {
        SimpleGraph g = new SimpleGraph();
        SimpleVertex a = g.addVertex("A");
        SimpleVertex b = g.addVertex("B");
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Finding paths in a disconnected graph should work fine (no exceptions, no infinite loops)
        PathfindingSnapshot paths = pathfinder.findAllPaths(a.id());
        assertTrue(pathfinder.allPathsFound());

        // The distance from the start to itself is 0
        assertEquals(0, paths.distanceTo(a.id()));

        // Unreachable nodes should be neither discovered nor reachable
        assertEquals(1, pathfinder.settledCount());
        assertFalse(paths.discovered(b.id()));
        assertFalse(paths.settled(b.id()));
    }

    @DisplayName("Extending a search should not settle more than the specified number of vertices")
    @Test
    void testExtendSearch() {
        SimpleGraph g = SimpleGraph.fromText(graph1);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Start finding shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        pathfinder.setStart(start.id());
        int maxToSettle = 3;
        PathfindingSnapshot snapshot = pathfinder.extendSearch(maxToSettle);
        assertEquals(maxToSettle, pathfinder.settledCount());

        // Extend search
        int additionalToSettle = 2;
        snapshot = pathfinder.extendSearch(additionalToSettle);
        assertEquals(maxToSettle + additionalToSettle, pathfinder.settledCount());

        // Extend by more than remaining vertices
        snapshot = pathfinder.extendSearch(2*g.vertexCount());
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        assertTrue(pathfinder.allPathsFound());
    }
}

/*
 * The classes below provide a simple implementation of the Graph interfaces where edge weights are
 * intrinsic to the Edge objects.  For convenience, a mutable set is used to store each vertex's
 * outgoing edges.
 */

class SimpleGraph implements Graph<SimpleVertex> {

    private final List<SimpleVertex> vertices = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    public int vertexCount() {
        return vertices.size();
    }

    public SimpleVertex getVertex(int id) {
        return vertices.get(id);
    }

    SimpleVertex addVertex(String label) {
        SimpleVertex newVertex = new SimpleVertex(vertices.size(), label, new HashSet<>());
        vertices.add(newVertex);
        index.put(label, newVertex.id());
        return newVertex;
    }

    SimpleVertex getVertexByLabel(String label) {
        return getVertex(index.get(label));
    }

    void addEdge(int startId, int endId, int weight) {
        getVertex(startId).outgoingEdges().add(new SimpleEdge(startId, endId, weight));
    }

    static SimpleGraph fromText(String text) {
        SimpleGraph g = new SimpleGraph();
        Scanner lines = new Scanner(text);
        Map<String, Integer> labelIndex = new HashMap<>();
        while (lines.hasNextLine()) {
            // Tokenize line
            String[] tokens = lines.nextLine().trim().split("\\s+");
            if (tokens.length == 0) {
                // Skip blank lines
                continue;
            }
            String startLabel = tokens[0];
            String edgeType = tokens[1];
            String endLabel = tokens[2];
            // If no weight token, default weight is 1
            int weight = (tokens.length > 3) ? Integer.parseInt(tokens[3]) : 1;

            // Look up vertex IDs from labels, adding new vertices as necessary
            int startId = labelIndex.computeIfAbsent(startLabel, label -> g.addVertex(label).id());
            int endId = labelIndex.computeIfAbsent(endLabel, label -> g.addVertex(label).id());

            // Add edge(s)
            if ("->".equals(edgeType)) {
                g.addEdge(startId, endId, weight);
            } else if ("--".equals(edgeType)) {
                g.addEdge(startId, endId, weight);
                g.addEdge(endId, startId, weight);
            } else {
                throw new IllegalArgumentException("Unexpected edge type: " + edgeType);
            }
        }
        return g;
    }
}

record SimpleVertex(int id, String label, Set<SimpleEdge> outgoingEdges)
        implements Vertex<SimpleEdge> {

}

record SimpleEdge(int startId, int endId, int weight) implements Edge {

}

class SimpleWeigher implements Weigher<SimpleEdge> {

    @Override
    public int weight(SimpleEdge edge) {
        return edge.weight();
    }
}
