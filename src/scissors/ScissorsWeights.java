package scissors;

import graph.Weigher;
import java.awt.image.BandCombineOp;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.util.Arrays;
import java.util.List;

/**
 * Factory for edge weight functions appropriate for use by the Intelligent Scissors algorithm.
 */
public class ScissorsWeights {

    /**
     * Create a `Weigher` whose type is determined by `weightName`.  The weigher will be capable of
     * weighing edges in the graph `graph`.  Throws IllegalArgumentException if `weightName` is not
     * among this factory's recognized `weightNames()`.
     */
    static Weigher<ImageEdge> makeWeigher(String weightName, ImageGraph graph) {
        return switch (weightName) {
            case "CrossGradMono" -> new CrossGradMonoWeight(graph);
            case "LuminanceGradient" -> new LuminanceGradientWeigher(graph);
            default -> throw new IllegalArgumentException("Unknown weigher: " + weightName);
        };
    }

    /**
     * Return the names of weight functions that this factory can create.
     */
    static Iterable<String> weightNames() {
        // TODO A6.4c: Add your weigher's name to this list.  If you add a bunch of weighers, you
        //  might find this method useful when populating your app's combo box.
        return List.of("CrossGradMono", "LuminanceGradient");
    }

    /**
     * Return the magnitude of the slope of the image intensity of `img` in band `b` perpendicular
     * to the direction `dir` from the pixel at location (`x`, `y`), multiplied by the distance to
     * the neighboring pixel in that direction.
     * <p>
     * The conventions for `dir` are the same as in `ImageVertex`: it is an integer in [0..8] where
     * 0 points right and 2 points up.
     */
    static int crossGrad(Raster img, int x, int y, int b, int dir) {
        int width = img.getWidth();
        int height = img.getHeight();

        // Note: Image boundaries are given slightly less than the maximum value, making it easier
        //  to select subjects that are cut off by the image's border without trying too hard to
        //  find paths that cut into the subject.
        int borderWeight = 180 - 64;

        return switch (dir) {
            case 0 -> (y == 0 || y == height - 1) ? borderWeight :
                    Math.abs((img.getSample(x, y + 1, b) + img.getSample(x + 1, y + 1, b)) -
                            (img.getSample(x, y - 1, b) + img.getSample(x + 1, y - 1, b))) / 4;
            case 1 -> Math.abs(img.getSample(x + 1, y, b) - img.getSample(x, y - 1, b));
            case 2 -> (x == 0 || x == width - 1) ? borderWeight :
                    Math.abs((img.getSample(x + 1, y - 1, b) + img.getSample(x + 1, y, b)) -
                            (img.getSample(x - 1, y - 1, b) + img.getSample(x - 1, y, b))) / 4;
            case 3 -> Math.abs(img.getSample(x, y - 1, b) - img.getSample(x - 1, y, b));
            case 4 -> (y == 0 || y == height - 1) ? borderWeight :
                    Math.abs((img.getSample(x, y - 1, b) + img.getSample(x - 1, y - 1, b)) -
                            (img.getSample(x, y + 1, b) + img.getSample(x - 1, y + 1, b))) / 4;
            case 5 -> Math.abs(img.getSample(x - 1, y, b) - img.getSample(x, y + 1, b));
            case 6 -> (x == 0 || x == width - 1) ? borderWeight :
                    Math.abs((img.getSample(x - 1, y + 1, b) + img.getSample(x - 1, y, b)) -
                            (img.getSample(x + 1, y + 1, b) + img.getSample(x + 1, y, b))) / 4;
            case 7 -> Math.abs(img.getSample(x, y + 1, b) - img.getSample(x + 1, y, b));
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Weight edges less if they run perpendicular to a large brightness gradient in an ImageGraph's
     * image.  Only considers the brightness of a grayscale (band-averaged) version of the image.
     */
    static class CrossGradMonoWeight implements Weigher<ImageEdge> {

        /**
         * The graph that the edges to be weighed will come from.
         */
        private ImageGraph graph;

        /**
         * A grayscale copy of the image represented by `graph`.
         */
        private Raster grayImage;

        /**
         * Create a new weigher capable of weighing edges in `graph`.
         */
        CrossGradMonoWeight(ImageGraph graph) {
            this.graph = graph;

            // Extract the "raster" for our graph's image, from which we can query pixel
            //  brightnesses.
            Raster src = graph.raster();

            // Convert our graph's image to black-and-white by averaging its bands
            // This involves linear algebra; we do not expect most students to understand how this
            //  works yet (consider CS 4670 if you are interested).
            float weight = 1.0f / src.getNumBands();
            float[][] avgMatrix = new float[3][src.getNumBands()];
            Arrays.fill(avgMatrix[0], weight);
            Arrays.fill(avgMatrix, avgMatrix[0]);
            RasterOp op = new BandCombineOp(avgMatrix, null);
            grayImage = op.filter(src, null);
        }

        @Override
        public int weight(ImageEdge edge) {
            // Get location of pixel at edge's start
            ImageVertex src = graph.getVertex(edge.startId());
            int x = src.x();
            int y = src.y();

            // Compute the largest possible slope, multiplied by the edge's length, that could be
            //  observed perpendicular to this edge's direction (even directions are horizontal or
            //  vertical; odd edges are diagonal).  By subtracting a "reward" quantity from this,
            //  we convert the reward into a "cost".
            int eGradMax = ((edge.dir() % 2) == 0) ? 180 : 255;

            // Compute the magnitude of the slope perpendicular to this edge, multiplied by this
            //  edge's length, then subtract it from the best possible value.
            return eGradMax - crossGrad(grayImage, x, y, 0, edge.dir());
        }
    }

    // TODO A6.4a: Define a new nested Weigher class that takes color into account.
    //  Start by mimicking the structure of `CrossGradMonoWeight`, but skip the grayscale conversion
    //  (just save the graph's image's raster to a field).  Then apply `crossGrad()` to each band
    //  in the image and combine the results to get a single valid weight.
    //  That is probably the simplest approach, but you can also be creative and try something
    //  totally different!  The requirements are just:
    //  1. Must work for every edge in the graph.
    //  2. Weights must be non-negative.
    //  3. Must work better than "CrossGradMono" on images with different colors of similar
    //     brightness (like challenge_1.png).

    static class LuminanceGradientWeigher implements Weigher<ImageEdge> {
        private final ImageGraph graph;
        private final Raster luminanceRaster;

        LuminanceGradientWeigher(ImageGraph graph) {
            this.graph = graph;
            this.luminanceRaster = createLuminanceRaster(graph.raster());
        }

        private Raster createLuminanceRaster(Raster src) {
            float[][] luminanceMatrix = {
                    {0.2126f, 0.7152f, 0.0722f},
                    {0.2126f, 0.7152f, 0.0722f},
                    {0.2126f, 0.7152f, 0.0722f}
            };
            RasterOp op = new BandCombineOp(luminanceMatrix, null);
            return op.filter(src, null);
        }

        @Override
        public int weight(ImageEdge edge) {
            ImageVertex startVertex = graph.getVertex(edge.startId());
            int x = startVertex.x();
            int y = startVertex.y();

            int maxGradient = ((edge.dir() % 2) == 0) ? 180 : 255;

            return maxGradient - crossGrad(luminanceRaster, x, y, 0, edge.dir());
        }
    }
}
