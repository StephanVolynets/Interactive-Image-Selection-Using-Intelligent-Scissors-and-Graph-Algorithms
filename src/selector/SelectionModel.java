package selector;

import static selector.SelectionModel.SelectionState.*;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.event.SwingPropertyChangeSupport;

/**
 * Represents the process of selecting a region of an image by appending segments to the end of a
 * selection path.  Supports removing segments in the opposite order that they were added and moving
 * the points that join segments after the selection has been completed.
 */
public abstract class SelectionModel {

    /**
     * Indicates a selection model's current mode of operation.
     */
    public enum SelectionState {
        /**
         * No selection is currently in progress (no starting point has been selected).
         */
        NO_SELECTION,

        /**
         * Currently assembling a selection.  A starting point has been selected, and the selection
         * path may contain a sequence of segments, which can be appended to by adding points.
         */
        SELECTING,
        /**
         * The selection path represents a closed selection that start and ends at the same point.
         * Points may be moved, but no additional points may be added.  The selected region of the
         * image may be extracted and saved from this state.
         */
        SELECTED,

        /**
         * The selection model is currently performing asynchronous processing in order to extend
         * the selection path or move one of its points.
         */
        PROCESSING
    }

    /**
     * The current state of this SelectionModel.
     */
    private SelectionState state;

    /**
     * The starting point of the current selection, or null if no selection has been started. Note:
     * `Point` is a mutable type, so care must be taken to avoid rep exposure.
     */
    protected Point start;

    /**
     * The sequence of segments representing the current selection path.  If the list is empty while
     * our state is SELECTING, then only the starting point has been selected so far. The start
     * point of the first segment in this list must equal `start`, and the start point of each
     * subsequent segment must equal the end point of the previous segment (ensuring continuity).
     * The most recently added segment is therefore at the end of the list.  If our state is
     * SELECTED, then this list must be non-empty, and the end point of the last segment must also
     * equal `start`.  Whenever the contents of this list change, a "selection" property change
     * event must be fired.
     */
    protected LinkedList<PolyLine> selection;

    /**
     * The image we are selecting from (may be null, in which case no operations should be attempted
     * until the image has been set).
     */
    protected BufferedImage img;

    /**
     * Helper object for managing property change notifications.
     */
    protected SwingPropertyChangeSupport propSupport;


    /**
     * If `notifyOnEdt` is true, property change listeners will be notified on Swing's Event
     * Dispatch thread, regardless of which thread the event was fired from.  It should generally be
     * set to "true" when this model will be used with a GUI, and "false" when unit testing.  The
     * image will initially be set to null.
     */
    protected SelectionModel(boolean notifyOnEdt) {
        state = NO_SELECTION;
        selection = new LinkedList<>();
        propSupport = new SwingPropertyChangeSupport(this, notifyOnEdt);
    }

    /**
     * Initialize this model to represent the same selection of the same image as `copy`.  Does NOT
     * copy any listeners from `copy`.  This constructor must only be invoked on Swing's Event
     * Dispatch Thread, since `copy`'s state may be shared with a background processing thread.
     */
    protected SelectionModel(SelectionModel copy) {
        state = copy.state;
        // If the copy is currently processing, assume the previous state was SELECTING
        //  (FIXME: this assumption is invalid if it was processing a move).
        if (state == PROCESSING) {
            state = SELECTING;
        }
        start = copy.start;
        selection = new LinkedList<>(copy.selection);
        img = copy.img;
        propSupport = new SwingPropertyChangeSupport(this, copy.propSupport.isNotifyOnEDT());
    }

    /* Client interface */

    /**
     * Return the status of this model's current selection.
     */
    public SelectionState state() {
        return state;
    }

    /**
     * Return the sequence of poly-line segments forming the current selection path.  The returned
     * list is not modifiable, but it will reflect subsequent changes made to this model.  Clients
     * must not mutate the constituent PolyLines.
     */
    public List<PolyLine> selection() {
        return Collections.unmodifiableList(selection);
    }

    /**
     * Return the image we are currently selecting from.
     */
    public BufferedImage image() {
        return img;
    }

    /**
     * Select from `newImg` instead of any previous set image.  Resets the selection.  Notifies
     * listeners that the "image" property has changed.
     */
    public void setImage(BufferedImage newImg) {
        BufferedImage oldImg = img;
        img = newImg;
        reset();
        propSupport.firePropertyChange("image", oldImg, img);
    }

    /**
     * If no selection has been started, start selecting from `p`.  Otherwise, if a selection is in
     * progress, append a segment from its last point to point `p`.  Subclasses determine the path
     * of the new segment.  Listeners will be notified if the "state" or "selection" properties are
     * changed.
     */
    public void addPoint(Point p) {
        if (state() == SelectionState.NO_SELECTION) {
            startSelection(p);
        } else if (state() == SelectionState.SELECTING) {
            // Defer to our subclass to append a segment ending at `p` to our selection.
            appendToSelection(p);

            // Notify observers that the selection has changed.  There is no reason to include an
            //  old value, but we do include an unmodifiable copy of the current selection as the
            //  new value.
            propSupport.firePropertyChange("selection", null, selection());
        } else {
            throw new IllegalStateException("Cannot add point in state " + state());
        }
    }

    /**
     * Return the last (end) point along the current selection path.  If no segments have been added
     * to the selection yet, or if the selection has finished, this will be the starting point.
     * Throws an `IllegalStateException` if our state is NO_SELECTION.
     */
    public Point lastPoint() {
        if (state == NO_SELECTION) {
            throw new IllegalStateException(
                    "Cannot query last point when not selection has been started");
        }
        // TODO 2A: If there are no segments currently in the selection path, return the starting
        //  point.  Otherwise, return the endpoint of the last segment of the current selection
        //  path.
        //  Test immediately with `testStart()` (also covered by `testAppend()` after
        //  implementing `appendToSelection()`).

        if (selection.isEmpty()) {
            return start;
        } else {
            return selection.getLast().end();
        }
    }

    /**
     * Return the path from our last point to `p` that would be appended to the selection if `p`
     * were to be added with `addPoint()`.
     */
    public abstract PolyLine liveWire(Point p);

    /**
     * If we are still processing the most recently added point, cancel that operation.  Otherwise,
     * remove the last segment from the selection path.  If the selection path does not contain any
     * segments, reset the selection to clear our starting point.  Listeners will be notified if the
     * "state" or "selection" properties are changed.  Removal of a point other than the start may
     * require asynchronous processing.
     */
    public void undo() {
        if (state == PROCESSING) {
            cancelProcessing();
        } else {
            undoPoint();
        }
    }

    /**
     * Close the current selection path by connecting the last segment to the starting point and
     * transitioning to the SELECTED state.  If no segments have been added yet, reset this
     * selection instead.  Listeners will be notified if the "state" or "selection" properties are
     * changed.  Throws an `IllegalStateException` if the selection is already finished.
     */
    public void finishSelection() {
        if (state == SELECTED) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (selection.isEmpty()) {
            reset();
        } else {
            addPoint(start);
            setState(SELECTED);
        }
    }

    /**
     * Clear the current selection path and any starting point and transition to the NO_SELECTION
     * state.  Listeners will be notified if the "state" or "selection" properties are changed.
     */
    public void reset() {
        start = null;
        selection.clear();
        setState(NO_SELECTION);
        propSupport.firePropertyChange("selection", null, selection());
    }

    /**
     * Return the index of the segment in our model's selection whose starting point is the closest
     * to `p`, as long as the square of its distance to `p` is no greater than `maxDistanceSq`.  If
     * no control point along the selection is close enough, return -1.  If multiple points are tied
     * for closest, any of their indices may be returned.  Throws an IllegalStateException if our
     * selection is not yet finished.
     */
    public int closestPoint(Point p, int maxDistanceSq) {
        assert state == SELECTED; // Ensure we are in the correct state to perform this operation.
        // TODO 4D: Implement as specified.  Note that the argument is the _square_ of the maximum
        //  distance; you can take advantage of this to avoid doing any floating-point math.
        //  Test immediately with the provided `testClosestPoint*()` cases, and add additional tests
        //  per the corresponding task in the test suite (consider writing the tests first).
        //  Note that, by this indexing convention, the index of `start` is 0.

        int index = -1; // Initialize the closest index to an invalid value.
        int closestDistanceSq = maxDistanceSq; // Set the closest distance to the maximum allowed.

        // Start at the first segment, which is indexed at 0.
        int i = 0;

        // Iterate over each segment in the selection path.
        for (PolyLine segment : selection) {
            // Calculate the squared distance from the starting point of the segment to the point `p`
            int distanceSq = (int) segment.start().distanceSq(p);

            // If this distance is less than the closest distance found so far, update the closest
            // distance and index.
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                index = i;
            }

            // Move to the next segment index.
            i++;
        }

        // Return the index of the closest control point, or -1 if none are close enough.
        return index;
    }

    /**
     * Move the end point of the segment of the selection with index `index` to `newPos`, updating
     * the path of that segment and the next segment (wrapping around) to keep the selection
     * continuous.
     */
    public abstract void movePoint(int index, Point newPos);

    /**
     * Write a PNG image to `out` containing the pixels from the current selection.  The size of the
     * image matches the bounding box of the selection, and pixels outside of the selection are
     * transparent.  Throws an IOException if the image could not be written.  Throws an
     * IllegalStateException if our selection is not finished.
     */
    public void saveSelection(OutputStream out) throws IOException {
        assert img != null;
        if (state != SELECTED) {
            throw new IllegalStateException("Must complete selection before saving");
        }
        Polygon clip = PolyLine.makePolygon(selection);
        Rectangle bounds = clip.getBounds();
        clip.translate(-bounds.x, -bounds.y);
        BufferedImage dst = new BufferedImage(bounds.width, bounds.height,
                BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setClip(clip);
        g.drawImage(img, -bounds.x, -bounds.y, null);
        ImageIO.write(dst, "png", out);
    }

    /* Specialization interface */

    /**
     * Change our selection state to `newState` (internal operation).  This should only be used to
     * perform valid state transitions.  Notifies listeners that the "state" property has changed.
     */
    protected void setState(SelectionState newState) {
        SelectionState oldState = state;
        state = newState;
        propSupport.firePropertyChange("state", oldState, state);
    }

    /**
     * When no selection has yet been started, set our starting point to `start` and transition to
     * the SELECTING state.  Listeners will be notified that the "state" property has changed.
     * Throws an `IllegalStateException` if our state is not NO_SELECTION.
     */
    protected void startSelection(Point start) {
        if (state != NO_SELECTION) {
            throw new IllegalStateException("Cannot start selection from state " + state);
        }
        this.start = new Point(start);
        setState(SelectionState.SELECTING);
    }

    /**
     * Append a segment from our last point to `p` to our selection.  Requires that our state is
     * SELECTING.  Not responsible for notifying listeners that the selection has changed.
     */
    protected abstract void appendToSelection(Point p);

    /**
     * Remove the last segment from the selection path.  If the selection path does not contain any
     * segments, reset the selection to clear our starting point.  Listeners will be notified if the
     * "state" or "selection" properties are changed.  Removal of a point other than the start may
     * require asynchronous processing.
     */
    protected void undoPoint() {
        if (selection.isEmpty()) {
            // Reset to remove the starting point
            reset();
        } else {
            // TODO 2E: Remove the last segment of the current selection path.  If the selection was
            //  finished, transition to the SELECTING state.  Notify listeners that the "selection"
            //  property has changed (old value should be null, while new value should be provided
            //  by the `selection()` observer to minimize rep exposure).
            //  Test immediately with `testUndoSelected()`, and add additional tests per the
            //  corresponding task in the test suite (consider writing the tests first).
            // NOTE: propSupport.firePropertyChange() call should be inside the else block
            // after removing the last segment from the selection path. (Stephan)
            selection.removeLast();
            if (state == SELECTED) {
                setState(SELECTING);
            }
            // Notify listeners that the "selection" property was changed.
            propSupport.firePropertyChange("selection", null, selection());

        }
    }

    /* Observation interface */

    /**
     * Register `listener` to be notified whenever any property of this model is changed.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    /**
     * Register `listener` to be notified whenever the property named `propertyName` of this model
     * is changed.
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Stop notifying `listener` of property changes for this model (assuming it was added no more
     * than once).  Does not affect listeners who were registered with a particular property name.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    /**
     * Stop notifying `listener` of changes to the property named `propertyName` for this model
     * (assuming it was added no more than once).  Does not affect listeners who were not registered
     * with `propertyName`.
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(propertyName, listener);
    }

    /* Methods not used until A6 */

    /**
     * Cancel any asynchronous processing currently being performed on behalf of this model.
     */
    public void cancelProcessing() {
        assert state == PROCESSING;
        // Default implementation does nothing
    }

    /**
     * Return an indication of the progress of any asynchronous processing currently being performed
     * on behalf of this model.  The type of object returned will depend on the subclass.  Returns
     * null if no asynchronous processing is currently being performed.
     */
    public Object getProcessingProgress() {
        return null;
    }
}
