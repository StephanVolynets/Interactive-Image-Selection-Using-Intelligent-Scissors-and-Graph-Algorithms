package scissors;

import java.awt.Point;
import java.awt.Polygon;
import java.util.Arrays;
import selector.PolyLine;

public class PolyLineBuffer {
    private int[] xs;
    private int[] ys;
    private int size;

    public PolyLineBuffer() {
        this(32);
    }

    public PolyLineBuffer(int initialCapacity) {
        assert initialCapacity > 0;
        xs = new int[initialCapacity];
        ys = new int[initialCapacity];
        size = 0;
    }

    public PolyLineBuffer(Point start, Point end) {
        this(2);
        append(start);
        append(end);
    }

    public void append(Point p) {
        append(p.x, p.y);
    }

    public void append(int x, int y) {
        // Reject duplicates
        if (size > 0 && x == xs[size - 1] && y == ys[size - 1]) {
            return;
        }

        if (size == xs.length) {
            assert xs.length > 0;
            xs = Arrays.copyOf(xs, 2 * size);
            ys = Arrays.copyOf(ys, 2 * size);
        }
        xs[size] = x;
        ys[size] = y;
        size += 1;
    }

    public int[] xs() {
        return xs;
    }

    public int[] ys() {
        return ys;
    }

    public int size() {
        return size;
    }

    public Point start() {
        assert size > 0;
        return new Point(xs[0], ys[0]);
    }

    public Point end() {
        assert size > 0;
        return new Point(xs[size - 1], ys[size - 1]);
    }

    /**
     * Reverses the sequence of points in this buffer, then returns a reference to itself.
     */
    public PolyLineBuffer reverse() {
        int tmp;
        for (int i = 0; i < size/2; ++i) {
            tmp = xs[i];
            xs[i] = xs[size - 1 - i];
            xs[size - 1 - i] = tmp;

            tmp = ys[i];
            ys[i] = ys[size - 1 - i];
            ys[size - 1 - i] = tmp;
        }
        return this;
    }

    public PolyLine toPolyLine() {
        if (size == 0) {
            throw new IllegalStateException("PolyLineBuffer is empty");
        } else if (size == 1) {
            Point p = new Point(xs[0], ys[0]);
            return new PolyLine(p, p);
        }
        return new PolyLine(Arrays.copyOf(xs, size), Arrays.copyOf(ys, size));
    }

    public static Polygon makePolygon(Iterable<PolyLineBuffer> segments) {
        Polygon poly = new Polygon();
        for (PolyLineBuffer pl : segments) {
            // TODO: Dedup
            for (int i = 0; i < pl.size; ++i) {
                poly.addPoint(pl.xs()[i], pl.ys()[i]);
            }
        }
        return poly;
    }
}
