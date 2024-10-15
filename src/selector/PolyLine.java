package selector;

import java.awt.Point;
import java.awt.Polygon;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an immutable path made up of straight line segments.  While it is intended to be
 * immutable, its interface sacrifices some encapsulation in order to be used efficiently for AWT
 * graphics (that is, it does not create defensive copies of arrays), so clients must not modify the
 * contents of arrays returned by instances of this class.
 */
public class PolyLine {

    /**
     * Sequence of the x coordinates of the points along this poly-line.  The first element
     * corresponds to the starting point.  Length must be at least 2.
     */
    private final int[] xs;

    /**
     * Sequence of the y coordinates of the points along this poly-line.  The first element
     * corresponds to the starting point.  Length must match `xs.length`.
     */
    private final int[] ys;

    /**
     * Create a straight line segment starting at `start` and ending at `end`.
     */
    public PolyLine(Point start, Point end) {
        xs = new int[]{start.x, end.x};
        ys = new int[]{start.y, end.y};
    }

    /**
     * Create a poly-line whose points have x-coordinates `xs` and y-coordinates `ys`.  Rep
     * exposure: the new object takes ownership of the argument arrays (it does not make defensive
     * copies), so clients must not mutate the contents of these arguments after construction.
     * Requires that `xs` and `ys` have the same length no less than 2.
     */
    public PolyLine(int[] xs, int[] ys) {
        assert xs.length >= 2;
        assert xs.length == ys.length;
        this.xs = xs;
        this.ys = ys;
    }

    /**
     * Return the sequence of the x coordinates of the points along this poly-line, in start-to-end
     * order.  Rep exposure: clients must not mutate the contents of the returned array.
     */
    public int[] xs() {
        return xs;
    }

    /**
     * Return the sequence of the y coordinates of the points along this poly-line, in start-to-end
     * order.  Rep exposure: clients must not mutate the contents of the returned array.
     */
    public int[] ys() {
        return ys;
    }

    /**
     * Return the number of points along this poly-line, including both endpoints.  Will be at least
     * 2.  The number of straight-line segments is therefore `size() - 1`.
     */
    public int size() {
        return xs.length;
    }

    /**
     * Return the first (starting) point along this poly-line.
     */
    public Point start() {
        return new Point(xs[0], ys[0]);
    }

    /**
     * Return the last (ending) point along this poly-line.
     */
    public Point end() {
        return new Point(xs[xs.length - 1], ys[ys.length - 1]);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || other.getClass() != getClass()) {
            return false;
        }
        PolyLine otherLine = (PolyLine) other;
        return Arrays.equals(xs, otherLine.xs) && Arrays.equals(ys, otherLine.ys);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(new Object[]{xs, ys});
    }

    /**
     * Return an AWT Polygon enclosed by the sequence of poly-lines in `segments`.  The end of the
     * last segment in the sequence will be joined to the start of the first segment in the
     * sequence.  Any duplicate points where segments join are omitted.  Requires `segments` is
     * non-empty.
     */
    public static Polygon makePolygon(List<PolyLine> segments) {
        // Determine maximum number of points and allocate sufficiently large arrays to store all
        // coordinates.
        int maxSize = 0;
        for (PolyLine segment : segments) {
            maxSize += segment.size();
        }
        int[] xs = new int[maxSize];
        int[] ys = new int[maxSize];

        // Append points from segments to coordinate arrays, skipping duplicate points when the end
        // of one segment coincides with the start of the next (wrapping around).
        int size = 0;
        Point prevEnd = segments.getLast().end();
        for (PolyLine segment : segments) {
            int iStart = segment.start().equals(prevEnd) ? 1 : 0;
            int nPoints = segment.size() - iStart;
            System.arraycopy(segment.xs, iStart, xs, size, nPoints);
            System.arraycopy(segment.ys, iStart, ys, size, nPoints);
            size += nPoints;
            prevEnd.x = xs[size - 1];
            prevEnd.y = ys[size - 1];
        }
        return new Polygon(xs, ys, size);
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder("PolyLine({");
        for (int i = 0; i < size(); ++i) {
            if (i > 0) {
                ans.append(", ");
            }
            ans.append("(" + xs[i] + "," + ys[i] + ")");
        }
        ans.append("})");
        return ans.toString();
    }

}
