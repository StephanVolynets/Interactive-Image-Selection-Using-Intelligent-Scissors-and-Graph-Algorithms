package selector;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A Swing component that displays an image and facilitates interaction with it in order to select
 * a region of the image.  The image and selection model can both be changed, and a placeholder
 * label is shown if no valid image has been set.
 */
public class ImagePanel extends JPanel {

    /**
     * Label for drawing the image when a valid image has been set.
     */
    private final JLabel pic;

    /**
     * Component for interactively building a selection; must be placed on top of `pic` with their
     * upper-left corners aligned in order for coordinates within this component to match pixel
     * locations in `pic`.
     */
    private final SelectionComponent selector;

    public ImagePanel() {
        // Create components to show when a valid image is set.
        pic = new JLabel();
        // Set pic's icon alignment so that its coordinates will match those of any component added
        //  to `pic`.
        pic.setHorizontalAlignment(SwingConstants.LEFT);
        pic.setVerticalAlignment(SwingConstants.TOP);

        // Default to using a point-to-point selection model
        SelectionModel selection = new PointToPointSelectionModel(true);
        selector = new SelectionComponent(selection);
        // Add `selector` on top of `pic`.  Adding to the center of a BorderLayout ensures that
        //  `selector` is scaled to the same size as `pic`.
        pic.setLayout(new BorderLayout());
        pic.add(selector);

        // Create components to show when no valid image has been set.
        JLabel placeholder = new JLabel("No image loaded.");
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        placeholder.setVerticalAlignment(SwingConstants.CENTER);
        placeholder.setFont(pic.getFont().deriveFont(48.0f));

        // Use a CardLayout to easily toggle between showing different components when an image is
        //  set vs. when one isn't.
        setLayout(new CardLayout());
        // Placeholder is first, pic is last
        add(placeholder);
        add(pic);
    }

    /**
     * Return the selection model we are currently controlling.
     */
    public SelectionModel selection() {
        return selector.getModel();
    }

    /**
     * Return the image we are currently displaying and selecting from.  Returns null if no image is
     * currently set.
     */
    public BufferedImage image() {
        return selection().image();
    }

    /**
     * Have our selection interactions control `newModel` instead of our current model.  The new
     * model will be set to use our current image and will initialize its selection path to our
     * current model's path if possible.
     */
    public void setSelectionModel(SelectionModel newModel) {
        // Have the new model use our current image
        if (image() == null || !image().equals(newModel.image())) {
            newModel.setImage(image());
        }

        selector.setModel(newModel);
    }

    /**
     * Display and select from `img` instead of our current image.  If `img` is null, then do not
     * display any image or support any selection interaction (a placeholder message will be shown
     * instead).  This will set the image on our selection model (which may reset its selection).
     */
    public void setImage(BufferedImage img) {
        // Update or remove image in selection model
        selection().setImage(img);

        // We set our own layout manager, so it should still be a CardLayout.
        CardLayout cards = (CardLayout)getLayout();
        if (img != null) {
            // Update and show image label
            pic.setIcon(new ImageIcon(img));
            cards.last(this);
        } else {
            // Free image and display placeholder
            pic.setIcon(null);
            cards.first(this);
        }
    }
}
