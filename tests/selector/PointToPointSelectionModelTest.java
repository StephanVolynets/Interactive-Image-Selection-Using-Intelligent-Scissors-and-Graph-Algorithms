package selector;

import static org.junit.jupiter.api.Assertions.*;
import static selector.SelectionModel.SelectionState.*;

import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A test suite for `PointToPointSelectionModel`, which also covers inherited methods defined in
 * `SelectionModel`.
 */
class PointToPointSelectionModelTest {

    // Note: All selection models are constructed with `notifyOnEdt=false` so that property change
    //  listeners will be notified immediately on the test thread, rather than asynchronously on
    //  Swing's EDT.

    @DisplayName("WHEN a new model is constructed without providing a previous selection, THEN it "
            + "will be in the NO_SELECTION state, AND its selection will be empty")
    @Test
    void testDefaultConstruction() {
        SelectionModel model = new PointToPointSelectionModel(false);
        assertEquals(NO_SELECTION, model.state());
        assertTrue(model.selection().isEmpty());
    }


    @DisplayName("GIVEN a model in the NO_SELECTION state, WHEN a point is added, THEN the model "
            + "will transition to the SELECTING state, notifying listeners that its 'state' "
            + "property has changed, AND the selection will still be empty, AND the model's last "
            + "point will be the provided point.")
    @Test
    void testStart() {
        // Set up the test scenario and start listening for events
        SelectionModel model = new PointToPointSelectionModel(false);
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        // Perform the test action
        Point newPoint = new Point(0, 0);
        model.addPoint(newPoint);

        // Verify the consequences
        // Note: A point-to-point model should never enter PROCESSING, so no need to wait
        observer.assertChangedTo("state", SELECTING);
        assertEquals(SELECTING, model.state());

        observer.assertNotChanged("selection");
        assertTrue(model.selection().isEmpty());

        assertEquals(newPoint, model.lastPoint());
    }

    @DisplayName("GIVEN a model whose start point has been chosen but whose selection is currently "
            + "empty, WHEN a live wire is requested to a location, THEN it will return a straight "
            + "line segment from its start to the location.")
    @Test
    void testLiveWireEmpty() {
        // Set up the test scenario
        SelectionModel model = new PointToPointSelectionModel(false);
        Point startPoint = new Point(0, 0);
        model.addPoint(startPoint);

        // Perform the test action
        Point mouseLocation = new Point(1, 2);
        PolyLine wire = model.liveWire(mouseLocation);

        // Verify the consequences
        PolyLine expectedWire = new PolyLine(startPoint, mouseLocation);
        assertEquals(expectedWire, wire);
    }

    @DisplayName("GIVEN a model whose start point has been chosen but whose selection is currently "
            + "empty, WHEN an undo is requested, THEN it will transition to the NO_SELECTION "
            + "state AND its selection will still be empty.")
    @Test
    void testUndoEmpty() {
        // Set up the test scenario
        SelectionModel model = new PointToPointSelectionModel(false);
        model.addPoint(new Point(0, 0));

        // Only listen for events after we are done with test setup
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        // Perform the test action
        model.undo();

        // Verify the consequences
        observer.assertChangedTo("state", NO_SELECTION);
        assertEquals(NO_SELECTION, model.state());

        assertTrue(model.selection().isEmpty());
    }

    @DisplayName("GIVEN a model in the SELECTING state, WHEN a point is added, THEN the model "
            + "will remain in the SELECTING state, AND listeners will be notified that the "
            + "selection has changed, AND the selection will end with a straight line segment to "
            + "the new point, AND the model's last point will be the provided point.")
    @Test
    void testAppend() {
        // Set up the test scenario
        SelectionModel model = new PointToPointSelectionModel(false);
        model.addPoint(new Point(0, 0));

        // Only listen for events after we are done with test setup
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        // Perform the test action
        Point newPoint = new Point(1, 2);
        model.addPoint(newPoint);

        // Verify the consequences
        observer.assertNotChanged("state");
        assertEquals(SELECTING, model.state());

        observer.assertChanged("selection");
        assertEquals(1, model.selection().size());
        PolyLine lastSegment = model.selection().getLast();
        // A straight segment should only have two points
        assertEquals(2, lastSegment.size());
        assertEquals(newPoint, lastSegment.end());

        assertEquals(newPoint, model.lastPoint());
    }

    // TODO 2D: Add a test case covering `liveWire()` when the selection path is non-empty.  Check
    //  returned value.

    // TODO 2F: Add a test case covering `undo()` when the selection path is non-empty.  Check
    //  expected state, absence of state change notification, expected selection size, occurrence of
    //  selection change notification, and expected last point.  See `testUndoSelected()` for
    //  inspiration.

    @DisplayName("GIVEN a model in the SELECTING state with a non-empty selection path, WHEN the "
            + "selection is finished, THEN it will transition to the SELECTED state, notifying "
            + "listeners that its 'state' property has changed, AND its selection path will have "
            + "one additional segment, ending at its start point, AND listeners will be notified "
            + "that its selection has changed")
    @Test
    void testFinishSelection() {
        // Set up the test scenario
        PointToPointSelectionModel model = new PointToPointSelectionModel(false);
        model.addPoint(new Point(0, 0));
        model.addPoint(new Point(10, 0));
        model.addPoint(new Point(10, 10));
        model.addPoint(new Point(0, 10));

        // Only listen for events after we are done with test setup
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        // Perform the test action
        model.finishSelection();

        // Verify the consequences
        observer.assertChangedTo("state", SELECTED);
        assertEquals(SELECTED, model.state());

        observer.assertChanged("selection");
        assertEquals(4, model.selection().size());
        assertEquals(new Point(0, 0), model.lastPoint());
    }

    // Now that we've tested `finishSelection()`, we can define a helper method that uses it.

    /**
     * Return a selection model in the SELECTED state whose selection path consists of 4
     * straight-line segments forming a square.  The path starts and ends at (0,0), the first
     * segments connects the start to (10,0), and the last segment connects (0,10) back to the
     * start.
     */
    static PointToPointSelectionModel makeSquareSelection() {
        PointToPointSelectionModel model = new PointToPointSelectionModel(false);
        model.addPoint(new Point(0, 0));
        model.addPoint(new Point(10, 0));
        model.addPoint(new Point(10, 10));
        model.addPoint(new Point(0, 10));
        model.finishSelection();
        return model;
    }

    @DisplayName("GIVEN a selection, WHEN an undo is requested, THEN it will transition to the "
            + "SELECTING state, notifying listeners that its 'state' property has changed, AND its "
            + "selection path will have one fewer segment, ending at its previous penultimate "
            + "point, AND listeners will be notified that its selection has changed.")
    @Test
    void testUndoSelected() {
        // Set up the test scenario
        SelectionModel model = makeSquareSelection();

        // Only listen for events after we are done with test setup
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        // Perform the test action
        model.undo();

        // Verify the consequences
        observer.assertChangedTo("state", SELECTING);
        assertEquals(SELECTING, model.state());

        observer.assertChanged("selection");
        assertEquals(3, model.selection().size());
        assertEquals(new Point(0, 10), model.lastPoint());
    }

    /* Tests of movePoint() */

    @DisplayName("GIVEN a selection, WHEN a point in the middle of the selection path is moved, "
            + "THEN the two segments joined at that point will have their start or end moved to "
            + "the new location as appropriate.")
    @Test
    void testMovePointMiddle() {
        SelectionModel model = makeSquareSelection();
        PclTester observer = new PclTester();
        model.addPropertyChangeListener(observer);

        model.movePoint(1, new Point(11, 12));
        observer.assertChanged("selection");
        PolyLine beforeSegment = model.selection().get(0);
        PolyLine afterSegment = model.selection().get(1);
        assertEquals(new Point(11, 12), beforeSegment.end());
        assertEquals(new Point(11, 12), afterSegment.start());
    }

    // TODO 4C: Write at least one additional test case for `movePoint()` that moves the starting
    //  point of the selection.

    /* Tests of closestPoint() */

    @DisplayName("GIVEN a selection (with no duplicate control points), WHEN querying for the "
            + "closest point to a location equal to one of the control points, THEN the index of "
            + "that control point will be returned.")
    @Test
    void testClosestPointCentered() {
        SelectionModel model = makeSquareSelection();
        assertEquals(1, model.closestPoint(new Point(10, 0), 4));
    }

    @DisplayName("GIVEN a selection, WHEN querying for the closest point to a location farther to "
            + "all of the control points than the max distance, THEN -1 will be returned.")
    @Test
    void testClosestPointTooFar() {
        SelectionModel model = makeSquareSelection();
        assertEquals(-1, model.closestPoint(new Point(100, -100), 9));
    }

    // TODO 4E: Write at least one additional test case for `closestPoint()` where the queried
    //  location is within the maximum distance of an unambiguously closest point but not directly
    //  on top of it.
}

/**
 * Helper class for testing whether a class-under-test properly notifies its observers of
 * PropertyChangeEvents.  Add an instance of this class as a listener to the object-under-test,
 * invoke that object's behavior, then use this class's methods to check for the expected event(s).
 */
class PclTester implements PropertyChangeListener {

    /**
     * List of events that we have observed.
     */
    List<PropertyChangeEvent> observedEvents = new LinkedList<>();

    /**
     * Append `evt` to our list of observed events.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        observedEvents.add(evt);
    }

    /**
     * Assert that we have not been notified of any property change events.
     */
    void assertNoChanges() {
        assertTrue(observedEvents.isEmpty());
    }

    /**
     * Assert that a property change event was received for the property named `propertyName`.
     */
    void assertChanged(String propertyName) {
        // This is a more advanced used of lambda expressions.  It's okay if you don't understand
        //  this implementation (you only need to understand the spec to make use of this helper);
        //  you can learn more about this style of coding in CS 3110.
        assertTrue(observedEvents.stream().anyMatch(e -> propertyName.equals(e.getPropertyName())));
    }

    /**
     * Assert that a property change event was received for the property named `propertyName` and
     * that its new value was `newValue`.
     */
    void assertChangedTo(String propertyName, Object newValue) {
        assertTrue(observedEvents.stream().anyMatch(
                e -> propertyName.equals(e.getPropertyName()) && newValue.equals(e.getNewValue())));
    }

    /**
     * Assert that a property change event was not received for the property named `propertyName`.
     */
    void assertNotChanged(String propertyName) {
        assertFalse(
                observedEvents.stream().anyMatch(e -> propertyName.equals(e.getPropertyName())));
    }
}
