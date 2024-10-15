package scissors;

import static selector.SelectionModel.SelectionState.*;

import graph.PathfindingSnapshot;
import graph.ShortestPaths;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import selector.PolyLine;
import selector.SelectionModel;

/**
 * Models a selection tool that connects each added point with a path determined by the "intelligent
 * scissors" algorithm using a configurable weight function.
 */
public class ScissorsSelectionModel extends SelectionModel {

    /**
     * The graph representation of the current image being rendered and traced (null if inherited
     * `img` is null).
     */
    private ImageGraph graph;

    /**
     * Name of Weigher that will determine edge weights between neighboring pixels.  Must be
     * recognized by the `ScissorsWeights` factory.
     */
    private String weightName;

    /**
     * The shortest paths computed from the last committed point for the current image.  May be null
     * if we are not in the SELECTING state.
     */
    private PathfindingSnapshot paths;

    /**
     * The most recent intermediate paths from the current shortest paths solve.  Null if no ongoing
     * solve or if solve has not reported any progress yet.
     */
    private PathfindingSnapshot pendingPaths;

    /**
     * The SwingWorker currently being used to solve for shortest paths on a background thread. Note
     * that "progress" events from `worker` will be forwarded to our own property change listeners.
     * Null if not PROCESSING.  Note: To support asynchronous cancellation, workers should not make
     * any changes to this model or forward any events if `worker` does not currently point to
     * them.
     */
    private ShortestPathsWorker worker;

    /**
     * The state we last transitioned into PROCESSING from (and which we will return to if the
     * processing is cancelled).  Value is unspecified if we are not in PROCESSING.
     */
    private SelectionState previousState;

    /**
     * Create a `ScissorsSelectionModel` in which the "intelligent scissors" algorithm will use the
     * weight function named `weightName` (as recognized by the `ScissorsWeights` factory). See
     * `SelectionModel` for interpretation of `notifyOnEdt`.
     */
    public ScissorsSelectionModel(String weightName, boolean notifyOnEdt) {
        super(notifyOnEdt);
        this.weightName = weightName;
    }

    /**
     * Create a `ScissorsSelectionModel` in which the "intelligent scissors" algorithm will use the
     * weight function named `weightName` (as recognized by the `ScissorsWeights` factory).
     * Initialize this model to represent the same selection of the same image as `copy` (but does
     * not copy any listeners from `copy`).  See `SelectionModel` for thread restrictions.
     */
    public ScissorsSelectionModel(String weightName, SelectionModel copy) {
        super(copy);
        this.weightName = weightName;
        if (image() != null) {
            graph = new ImageGraph(image());
        }
        if (state() == SELECTING) {
            findPaths(graph.idAt(lastPoint()));
        }
    }

    @Override
    public void reset() {
        // Overridden due to the need to cancel background processing.

        // Cancel any background tasks
        if (state() == PROCESSING) {
            cancelProcessing();
        }

        // Since the inherited behavior will immediately transition to the NO_SELECTION state,
        // we need to maintain our invariant regarding `worker` by setting to null.  Any running
        // worker will notice this and refrain from changing us.
        worker = null;

        super.reset();
    }

    @Override
    public void setImage(BufferedImage img) {
        // Overridden due to the need to update our graph

        super.setImage(img);

        // In addition to whatever our superclass does to set the image, we also need to replace our
        //  graph with one that represents the new image.
        if (img != null) {
            graph = new ImageGraph(img);
        } else {
            graph = null;
        }
    }

    @Override
    protected void startSelection(Point start) {
        // Overridden to do processing when selection has been started
        // Does not call super implementation in order to skip transitioning to the SELECTING state
        //  before processing

        if (state() != NO_SELECTION) {
            throw new IllegalStateException("Cannot start selection from state " + state());
        }
        this.start = new Point(start);

        // Find shortest paths from our start node so we will know what segment to connect to the
        //  next added (or live wire) point.
        int committedId = graph.idAt(start);
        findPaths(committedId);
    }

    @Override
    public void finishSelection() {
        // Overridden to skip the processing that otherwise runs when a point is added

        if (state() == SELECTED) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (selection.isEmpty()) {
            reset();
        } else {
            int committedId = graph.idAt(start);
            PolyLine newSegment = graph.pathToPolyLine(paths.pathTo(committedId));
            selection.addLast(newSegment);
            setState(SELECTED);
        }
    }

    @Override
    protected void undoPoint() {
        // Overridden to do processing when selection endpoint has changed
        super.undoPoint();

        if (state() == SELECTING) {
            findPaths(graph.idAt(lastPoint()));
        }
    }

    /**
     * Append to the current selection the "intelligent scissors" shortest path segment connecting
     * our current selection's endpoint to `p`.
     */
    @Override
    protected void appendToSelection(Point p) {
        assert state() == SelectionState.SELECTING;

        // TODO A6.2b: Implement this method as specified.  Your implementation will probably
        //  involve the following steps:
        //  1. Look up the vertex ID associated with `p`.
        //  2. Construct the shortest path to this vertex based on our last pathfinding results.
        //  3. Convert that path from a sequence of vertices to a `PolyLine` and append it to our
        //     selection
        //  4. Call `findPaths()` to start a new shortest-paths solve from our selection's new
        //     endpoint.
        //  Hint: The `ImageGraph` class provides methods that might help with steps 1 and 3.
        assert state() == SelectionState.SELECTING;

        int endpointId = graph.idAt(p);
        List<Integer> shortestPath = paths.pathTo(endpointId);
        PolyLine segment = graph.pathToPolyLine(shortestPath);
        selection.add(segment);
        findPaths(endpointId);
    }

    /**
     * Transition to the PROCESSING state and start solving for shortest paths from the vertex with
     * ID `startId`.  Preserves invariants associated with `worker`, `pendingPaths`, and
     * `previousState`.
     */
    private void findPaths(int startId) {
        previousState = state();
        setState(PROCESSING);

        pendingPaths = null;
        worker = new ShortestPathsWorker(startId);
        worker.execute();
    }

    @Override
    public void movePoint(int index, Point newPos) {
        assert state() == SelectionState.SELECTED;
        assert selection.size() >= 2;

        // Start solving for shortest paths from the moved point, which will give us the segments to
        //  both the preceding and succeeding points.  This will transition us to the PROCESSING
        //  state.
        findPaths(graph.idAt(newPos));

        // Our worker's `done()` method is sufficient for adding points, but we need to do more
        //  in order to move one.  This is one way to tack additional work onto a task (it will run
        //  on the EDT).
        worker.addPropertyChangeListener(e -> {
            ShortestPathsWorker src = (ShortestPathsWorker) e.getSource();
            if ("state".equals(e.getPropertyName()) && e.getNewValue() == StateValue.DONE) {
                // If the solve wasn't cancelled, use its results to compute the replacement
                //  segments.
                if (src.state() == Future.State.SUCCESS) {
                    ListIterator<PolyLine> it = selection.listIterator(index + 1);
                    PolyLine oldAfter = it.previous();
                    var oaEnd = graph.vertexAt(oldAfter.end());
                    // New segment is path from moved point to successor point
                    it.set(graph.pathToPolyLine(paths.pathTo(oaEnd.id())));

                    if (!it.hasPrevious()) {
                        it = selection.listIterator(selection.size());
                        start = new Point(newPos);
                    }
                    PolyLine oldBefore = it.previous();
                    var obStart = graph.vertexAt(oldBefore.start());
                    // New segment is the reverse of the path from the moved point to its
                    //  predecessor point.
                    it.set(graph.pathToPolyLine(paths.pathTo(obStart.id()).reversed()));
                        // new commit
                    propSupport.firePropertyChange("selection", null, selection());
                }
            }
        });
    }

    /**
     * Returns the "intelligent scissors" shortest path segment connecting our current selection's
     * endpoint to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        // TODO A6.2a: Implement this method as specified.  Your implementation will probably
        //  involve the following steps:
        //  1. Look up the vertex ID associated with `p`.
        //  2. Construct the shortest path to this vertex based on our last pathfinding results.
        //  3. Convert that path from a sequence of vertices to a `PolyLine`
        //  Hint: The `ImageGraph` class provides methods that might help with steps 1 and 3.
        int vertexId = graph.idAt(p);

        List<Integer> vertexPath = paths.pathTo(vertexId);

        return graph.pathToPolyLine(vertexPath);
    }

    /**
     * Return the most recent pathfinding snapshot produced by our "intelligent scissors"
     * processing, which attempts to find good paths from the end of our selection to all other
     * pixels in the image.  Returns null if our processing has not yet produced any results.
     */
    @Override
    public ImagePathsSnapshot getProcessingProgress() {
        assert state() == PROCESSING;
        return (pendingPaths != null) ? new ImagePathsSnapshot(graph, pendingPaths) : null;
    }

    @Override
    public void cancelProcessing() {
        assert state() == PROCESSING;

        if (worker != null) {
            worker.cancel(false);
        }
    }


    /**
     * SwingWorker for computing shortest paths and periodically updating progress.  This is an
     * inner class, meaning it has access to all fields of its containing TraceComponent. It
     * publishes preliminary pathfinding results (containing settled and frontier nodes) and returns
     * the final best paths (or null if cancelled).
     */
    private class ShortestPathsWorker
            extends SwingWorker<PathfindingSnapshot, PathfindingSnapshot> {

        /**
         * The shortest-paths solver this worker will use.  After construction, must only be
         * accessed from the background thread.
         */
        private final ShortestPaths<ImageVertex, ImageEdge> pathfinder;

        /**
         * Construct a worker that, when executed, will find the shortest paths from the vertex with
         * ID `startId` to every pixel in our outer model's `image`.  "progress" events will be
         * forwarded to our outer model's listeners.  This must be called from the EDT.
         */
        public ShortestPathsWorker(int startId) {
            pathfinder = new ShortestPaths<>(
                    graph, ScissorsWeights.makeWeigher(weightName, graph));
            pathfinder.setStart(startId);

            // Forward progress property changes to outer model's listeners (as long as we are
            // still the active solver).
            addPropertyChangeListener((PropertyChangeEvent evt) -> {
                if ("progress".equals(evt.getPropertyName()) && worker == this) {
                    propSupport.firePropertyChange(evt);
                }
            });
        }

        /**
         * Solve for shortest paths and return the results.  Periodically publish progress
         * percentage and preliminary shortest paths.  It is assumed that this is generally not
         * called from the EDT.  Returns null if cancelled.
         */
        @Override
        public PathfindingSnapshot doInBackground() {
            // This is executed on a separate thread; do not access outer model's fields!

            // TODO A6.2c: Implement this method as specified.  In more detail, your implementation
            //  should do the following:
            //  1. Repeatedly extend the search on `pathfinder` until all paths have been found.
            //     The batch size should be small enough so that the progress bar will update
            //     smoothly (no more than 10000).
            //  2. After each extension of the search, report the percentage of vertices that have
            //     been settled as our progress.
            //  3. Also after each extension, use `publish()` to deliver the current results
            //     snapshot over to the EDT (where it will appear as an argument to `process()`).
            //  4. Each iteration should check whether this SwingWorker has been cancelled.  If so,
            //     return immediately.
            //  5. After all paths have been found, return the final pathfinding results.
            //  Since this may execute concurrently with other methods in the same class, it is only
            //  safe to use a subset of its fields and methods, including: `pathfinder`,
            //  `setProgress()`, `publish()`, and `isCancelled()`.
            //  References:
            //  [1] https://docs.oracle.com/javase/tutorial/uiswing/concurrency/worker.html
            //  [2] https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/SwingWorker.html#isCancelled()
            final int BATCH_SIZE = 10000;
            PathfindingSnapshot currentSnapshot = null;

            while (!pathfinder.allPathsFound()) {
                if (isCancelled()) {
                    return null;
                }
                currentSnapshot = pathfinder.extendSearch(BATCH_SIZE);
                int progress = (int) ((double) pathfinder.settledCount() / pathfinder.vertexCount() * 100);
                setProgress(progress);
                publish(currentSnapshot);
            }

            return currentSnapshot;
        }

        /**
         * Save the most recent preliminary paths published by the worker's background task to our
         * outer Model's `pendingPaths` and notify listeners that the "pending-paths" property has
         * changed.  This is executed on the EDT.
         */
        @Override
        public void process(List<PathfindingSnapshot> publishedPaths) {
            // If our outer component has changed its image and state since we started, do nothing.
            if (worker != this) {
                return;
            }
            pendingPaths = publishedPaths.getLast();
            firePropertyChange("pending-paths", null, pendingPaths);
        }

        /**
         * This is executed on the EDT.  If we are still the active worker, set model's state to the
         * state it transitioned to PROCESSING from, unless it was NO_SELECTION and our task
         * finished successfully, in which case transition to SELECTING.
         */
        @Override
        public void done() {
            // TODO A6.2d: Implement this method to do the following:
            //  1. Check whether our outer object's `worker` field still points to us.  If so, it
            //     means nothing has changed in the model since we started work, so we're good to
            //     continue.  If not, it means that the model was reset, so it no longer has
            //     interest in our results (and we should return immediately).
            //  2. Assuming our background calculation finished successfully, get its final results
            //     and save them in our outer object's `paths` field.  Note that, if the previous
            //     state was NO_SELECTION, we will want to transition to SELECTING in this case.
            //  3. If our background calculation did not finish successfully, proceed as follows:
            //     A. If it was cancelled and the previous state was SELECTING, then undo the last
            //        added point.  Note that there's no need to find paths again, since our `paths`
            //        field should still have the results for that previous endpoint.
            //     B. If it threw an exception, wrap that exception in a RuntimeException and
            //        re-throw it (this will help you detect bugs in ShortestPaths).
            //     C. InterruptedExceptions can be ignored, since `get()` will not block in
            //        `done()`.
            //  4. Set our outer model's state to the state it was in before entering PROCESSING
            //     (unless our previous state was NO_SELECTION and our calculation was successful,
            //     in which case SELECTING in the desired state).  And in order to preserve our
            //     model's invariant regarding `worker`, set its worker to `null`.
            //  Since this is guaranteed to execute on the EDT, it is safe to access any members of
            //  our outer model object.
            if (worker != this) {
                return;
            }

            try {
                PathfindingSnapshot finalPaths = get();
                if (finalPaths != null) {
                    paths = finalPaths;
                    if (previousState == NO_SELECTION) {
                        setState(SELECTING);
                    } else {
                        setState(previousState);
                    }
                }
            } catch (CancellationException e) {
                if (previousState == SELECTING) {
                    undoPoint();
                }
            } catch (ExecutionException e) {
                throw new RuntimeException("Error in background path calculation", e.getCause());
            } catch (InterruptedException e) {
                // Ignore
            } finally {
                worker = null;
            }
        }
    }
}
