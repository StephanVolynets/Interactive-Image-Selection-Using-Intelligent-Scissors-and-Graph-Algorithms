package selector;

import java.awt.Point;
import java.util.ListIterator;

/**
 * Models a selection tool that connects each added point with a straight line.
 */
public class PointToPointSelectionModel extends SelectionModel {

    public PointToPointSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
    }

    public PointToPointSelectionModel(SelectionModel copy) {
        super(copy);
    }

    /**
     * Return a straight line segment from our last point to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        // TODO 2B: Implement this method as specified by constructing and returning a new PolyLine
        //  representing the desired line segment.  This can be done with one statement.
        //  Test immediately with `testLiveWireEmpty()`, and think about how the test might change
        //  for non-empty selections (see task 2D).
        return new PolyLine(lastPoint(), p);
    }

    /**
     * Append a straight line segment to the current selection path connecting its end with `p`.
     */
    @Override
    protected void appendToSelection(Point p) {
        // TODO 2C: Create a line segment from the end of the previous segment (or from the starting
        //  point if this is only the 2nd point) to the current point `p`, then append that segment
        //  to the current selection path.  This can be done with one statement, similar to
        //  `liveWire()` above.
        //  Test immediately with `testAppend()` and `testFinishSelection()`.
        selection.add(liveWire(p));
    }

    /**
     * Move the starting point of the segment of our selection with index `index` to `newPos`,
     * connecting to the end of that segment with a straight line and also connecting `newPos` to
     * the start of the previous segment (wrapping around) with a straight line (these straight
     * lines replace both previous segments).  Notify listeners that the "selection" property has
     * changed.
     */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (state() != SelectionState.SELECTED) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= selection.size()) {
            throw new IllegalArgumentException("Invalid segment index " + index);
        }

        // Retrieve the current segments that need modification
        PolyLine currentSegment = selection.get(index);
        PolyLine previousSegment = selection.get(index == 0 ? selection.size() - 1 : index - 1);

        // Update the current segment's start to the new position if it's the first point
        if (index == 0) {
            start = new Point(newPos); // Update the starting point
        }

        // Create new segments with updated points
        PolyLine newCurrentSegment = new PolyLine(new Point(newPos), currentSegment.end());
        PolyLine newPreviousSegment = new PolyLine(previousSegment.start(), new Point(newPos));

        // Replace old segments with new ones
        selection.set(index, newCurrentSegment);
        selection.set(index == 0 ? selection.size() - 1 : index - 1, newPreviousSegment);

        // Fire property change event
        propSupport.firePropertyChange("selection", null, selection);
    }
}