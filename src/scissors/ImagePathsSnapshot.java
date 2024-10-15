package scissors;

import graph.PathfindingSnapshot;
import java.awt.Point;

/**
 * A wrapper around `PathfindingSnapshot` with convenience methods suitable for ImageGraphs. Allows
 * querying the "discovered" and "settled" statuses of `Point` locations.
 */
public class ImagePathsSnapshot {

    private ImageGraph graph;
    private PathfindingSnapshot paths;

    ImagePathsSnapshot(ImageGraph graph, PathfindingSnapshot paths) {
        this.graph = graph;
        this.paths = paths;
    }

    /**
     * Return whether the pixel at location `p` is known to be reachable from the pathfinder's
     * starting location.  Requires `p` is a point in the image.
     */
    public boolean discovered(Point p) {
        return paths.discovered(graph.idAt(p));
    }

    /**
     * Return whether a shortest path is known from the pathfinder's starting location to the pixel
     * at location `p` Requires `p` is a point in the image.
     */
    public boolean settled(Point p) {
        return paths.settled(graph.idAt(p));
    }
}
