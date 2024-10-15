package scissors;

import graph.Edge;
import graph.Graph;
import graph.Vertex;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import selector.PolyLine;

/**
 * Provides a Graph structure on top of an image where pixels are treated as vertices connected to
 * their neighboring pixels (including diagonals).  Edges are interpreted as connecting pixel
 * centers and are aware of their length and direction within the image.
 */
class ImageGraph implements Graph<ImageVertex> {

    /**
     * The image we are providing a graph structure for.
     */
    private final BufferedImage img;

    /**
     * Create a new ImageGraph to provide a graph structure over the image `img`.
     */
    public ImageGraph(BufferedImage img) {
        this.img = img;
    }

    /**
     * Return the width of our image (the number of pixels in one row).
     */
    public int width() {
        return img.getWidth();
    }

    /**
     * Return the height of our image (the number of pixels in one column).
     */
    public int height() {
        return img.getHeight();
    }

    @Override
    public int vertexCount() {
        return width() * height();
    }

    @Override
    public ImageVertex getVertex(int id) {
        assert id >= 0 && id < vertexCount();
        int y = id / width();
        int x = id - y * width();
        return new ImageVertex(this, x, y);
    }

    /**
     * Return the ID of the vertex at pixel location `p`.  Requires `p` is within the bounds of the
     * image.
     */
    public int idAt(Point p) {
        assert p.x >= 0 && p.x < width();
        assert p.y >= 0 && p.y < height();
        return ImageVertex.xyToId(p.x, p.y, width());
    }

    /**
     * Return a representation of the Vertex at the image location `p`.
     */
    public ImageVertex vertexAt(Point p) {
        assert p.x >= 0 && p.x < width();
        assert p.y >= 0 && p.y < height();
        return new ImageVertex(this, p.x, p.y);
    }

    /**
     * Return the Raster backing our image.  This is convenient for querying the brightness of
     * pixels in different color bands.
     */
    public Raster raster() {
        return img.getRaster();
    }

    /**
     * Convert a sequence of vertex IDs, `path`, into a `PolyLine` that connects to the
     * corresponding pixels.
     */
    public PolyLine pathToPolyLine(List<Integer> path) {
        PolyLineBuffer buffer = new PolyLineBuffer(path.size());
        for (int id : path) {
            buffer.append(getVertex(id).point());
        }
        return buffer.toPolyLine();
    }
}

/**
 * A Vertex in the ImageGraph `image` representing a pixel at location (x, y).
 * <p>
 * Invariant: `0 <= x < image.width()`, `0 <= y < image.height()`.
 */
record ImageVertex(ImageGraph image, int x, int y) implements Vertex<ImageEdge> {

    public ImageVertex {
        // This "post-constructor" runs after the record's fields have been initialized to the
        //  constructor's arguments.  Here we just assert that the location is within the image's
        //  bounds.
        assert x >= 0 && x < image.width();
        assert y >= 0 && y < image.height();
    }

    @Override
    public int id() {
        return xyToId(x, y, image.width());
    }

    @Override
    public Iterable<ImageEdge> outgoingEdges() {
        return new Iterable<ImageEdge>() {
            @Override
            public Iterator<ImageEdge> iterator() {
                return new ImageEdgeIterator();
            }
        };
    }

    /**
     * Return the location of the pixel represented by this vertex in the image.
     */
    public Point point() {
        return new Point(x, y);
    }

    /**
     * Return whether a potential pixel neighbor in the direction `dir` is within the image's
     * bounds.  Requires `dir` in [0..7], with 0 representing "right" and 2 representing "up".
     */
    boolean validDir(int dir) {
        return switch (dir) {
            case 0 -> x + 1 < image.width();
            case 1 -> x + 1 < image.width() && y > 0;
            case 2 -> y > 0;
            case 3 -> x > 0 && y > 0;
            case 4 -> x > 0;
            case 5 -> x > 0 && y + 1 < image.height();
            case 6 -> y + 1 < image.height();
            case 7 -> x + 1 < image.width() && y + 1 < image.height();
            default -> false;
        };
    }

    /**
     * Return the ID of our neighboring vertex in the direction `dir`.  Requires that such a
     * neighbor is within the image's bounds.  Requires `dir` in [0..7], with 0 representing "right"
     * and 2 representing "up".
     */
    int neighborId(int dir) {
        assert validDir(dir);
        return switch (dir) {
            case 0 -> xyToId(x + 1, y, image.width());
            case 1 -> xyToId(x + 1, y - 1, image.width());
            case 2 -> xyToId(x, y - 1, image.width());
            case 3 -> xyToId(x - 1, y - 1, image.width());
            case 4 -> xyToId(x - 1, y, image.width());
            case 5 -> xyToId(x - 1, y + 1, image.width());
            case 6 -> xyToId(x, y + 1, image.width());
            case 7 -> xyToId(x + 1, y + 1, image.width());
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Convert a pixel location (`x`, `y`) to a vertex ID for an ImageGraph with a width of
     * `width`.
     */
    static int xyToId(int x, int y, int width) {
        assert x >= 0 && x < width;
        return x + width * y;
    }

    /**
     * An Iterator for enumerating the valid outgoing edges for this ImageVertex.
     */
    class ImageEdgeIterator implements Iterator<ImageEdge> {

        /**
         * The next edge direction to yield, or 8 if all edges have been yielded.
         */
        private int nextDir;

        public ImageEdgeIterator() {
            nextDir = 0;
            findNextValidDir();
        }

        @Override
        public boolean hasNext() {
            return nextDir < 8;
        }

        @Override
        public ImageEdge next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ImageEdge nextEdge = new ImageEdge(id(), neighborId(nextDir), nextDir);
            nextDir += 1;
            findNextValidDir();
            return nextEdge;
        }

        /**
         * Advance `nextDir` until it represents the next valid edge direction ("valid" means that
         * it points to a neighbor that is within the image's bounds).  Advances to 8 when there are
         * no more valid edge directions.
         */
        private void findNextValidDir() {
            while (nextDir < 8 && !validDir(nextDir)) {
                nextDir += 1;
            }
        }
    }
}

/**
 * An Edge in an ImageGraph connecting the vertex with ID `startId` to its neighboring vertex with
 * ID `endId`, which lies in the direction `dir` relative to the start.
 * <p>
 * Invariant: `dst.equals(src.neighbor(dir))`.
 */
record ImageEdge(int startId, int endId, int dir) implements Edge {

    public ImageEdge {
        // This "post-constructor" runs after the record's fields have been initialized to the
        //  constructor's arguments.  Here we just assert that the location is within the image's
        //  bounds.
        assert dir >= 0 && dir < 8;

        // We can't actually assert these without a reference to the ImageGraph, but we leave them
        //  here as documentation.
//        assert getVertex(startId).validDir(dir);
//        assert endId == getVertex(startId).neighborId(dir);
    }

    /**
     * Return the geometric length of this edge, in pixel units, interpreting it as connecting pixel
     * centers.
     */
    public double length() {
        if (dir % 2 == 0) {
            return 1;
        } else {
            return Math.sqrt(2);
        }
    }
}
