package selector;

import static selector.SelectionModel.SelectionState.*;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import scissors.ScissorsSelectionModel;
import scissors.ScissorsWeights;

/**
 * A graphical application for selecting and extracting regions of images.
 */
public class SelectorApp implements PropertyChangeListener {

    private final JFrame frame;
    private final ImagePanel imgPanel;
    private SelectionModel model;

    private JMenuItem saveItem;
    private JMenuItem undoItem;
    private JButton cancelButton;
    private JButton undoButton;
    private JButton resetButton;
    private JButton finishButton;
    private final JLabel statusLabel;
    private JProgressBar processingProgress;

    public SelectorApp() {
        frame = new JFrame("Selector");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        statusLabel = new JLabel("Status");
        imgPanel = new ImagePanel();

        JScrollPane scrollPane = new JScrollPane(imgPanel);
        scrollPane.setPreferredSize(new Dimension(500, 500));
        frame.add(scrollPane, BorderLayout.CENTER);

        frame.setJMenuBar(makeMenuBar());

        JPanel controlPanel = makeControlPanel();
        frame.add(controlPanel, BorderLayout.EAST);

        frame.add(statusLabel, BorderLayout.SOUTH);
        setSelectionModel(new PointToPointSelectionModel(true));

        processingProgress = new JProgressBar();
        frame.add(processingProgress, BorderLayout.PAGE_START);
    }

    private JMenuBar makeMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openItem = new JMenuItem("Open...");
        fileMenu.add(openItem);
        saveItem = new JMenuItem("Save...");
        fileMenu.add(saveItem);
        JMenuItem closeItem = new JMenuItem("Close");
        fileMenu.add(closeItem);
        JMenuItem exitItem = new JMenuItem("Exit");
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        undoItem = new JMenuItem("Undo");
        editMenu.add(undoItem);

        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));

        openItem.addActionListener(e -> openImage());
        closeItem.addActionListener(e -> imgPanel.setImage(null));
        saveItem.addActionListener(e -> saveSelection());
        exitItem.addActionListener(e -> frame.dispose());
        undoItem.addActionListener(e -> model.undo());

        return menuBar;
    }

    private JPanel makeControlPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 5, 5));

        cancelButton = new JButton("Cancel");
        undoButton = new JButton("Undo");
        resetButton = new JButton("Reset");
        finishButton = new JButton("Finish");

        String[] modelOptions = {"Point-to-Point", "Intelligent scissors: gray"};
        JComboBox<String> modelComboBox = new JComboBox<>(modelOptions);

        panel.add(cancelButton);
        panel.add(undoButton);
        panel.add(resetButton);
        panel.add(finishButton);
        panel.add(modelComboBox);

        cancelButton.addActionListener(e -> model.cancelProcessing());
        undoButton.addActionListener(e -> model.undo());
        resetButton.addActionListener(e -> model.reset());
        finishButton.addActionListener(e -> model.finishSelection());

        modelComboBox.addActionListener(e -> {
            String selectedModel = (String) modelComboBox.getSelectedItem();
            if ("Point-to-Point".equals(selectedModel)) {
                setSelectionModel(new PointToPointSelectionModel(model));
            } else if ("Intelligent scissors: gray".equals(selectedModel)) {
                setSelectionModel(new ScissorsSelectionModel("CrossGradMono", model));
            }
        });

        return panel;
    }

    public void start() {
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("state".equals(evt.getPropertyName())) {
            reflectSelectionState(model.state());
            if (model.state() == PROCESSING) {
                processingProgress.setIndeterminate(true);
            } else {
                processingProgress.setValue(0);
                processingProgress.setIndeterminate(false);
            }
        }
        if ("progress".equals(evt.getPropertyName())) {
            processingProgress.setValue((int) evt.getNewValue());
            processingProgress.setIndeterminate(false);
        }
    }

    private void reflectSelectionState(SelectionModel.SelectionState state) {
        statusLabel.setText(state.name());

        boolean isSelecting = state == SELECTING;
        boolean isSelected = state == SELECTED;
        boolean isProcessing = state == PROCESSING;
        imgPanel.setEnabled(!isProcessing);
        cancelButton.setEnabled(isProcessing);
        undoButton.setEnabled(isSelecting || isSelected);
        resetButton.setEnabled(isSelecting || isSelected);
        finishButton.setEnabled(isSelecting);
        saveItem.setEnabled(isSelected);
        undoItem.setEnabled(isSelecting || isSelected);
    }

    public SelectionModel getSelectionModel() {
        return model;
    }

    public void setSelectionModel(SelectionModel newModel) {
        if (model != null) {
            model.removePropertyChangeListener(this);
        }

        imgPanel.setSelectionModel(newModel);
        model = imgPanel.selection();
        model.addPropertyChangeListener("state", this);

        reflectSelectionState(model.state());

        model.addPropertyChangeListener("progress", this);
    }

    public void setImage(BufferedImage img) {
        imgPanel.setImage(img);
    }

    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        chooser.setFileFilter(new FileNameExtensionFilter("Image files",
                ImageIO.getReaderFileSuffixes()));

        boolean notOpen = true;
        while (notOpen) {
            int dialog = chooser.showOpenDialog(frame);
            if (dialog == JFileChooser.APPROVE_OPTION) {
                try {
                    BufferedImage img = ImageIO.read(chooser.getSelectedFile());
                    if (img != null) {
                        this.setImage(img);
                        notOpen = false;
                    } else {
                        JOptionPane.showMessageDialog(frame,
                                "Could not read the image at " + chooser.getSelectedFile().getAbsolutePath(),
                                "Unsupported image format", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not read the image at " + chooser.getSelectedFile().getAbsolutePath(),
                            "Unsupported image format", JOptionPane.ERROR_MESSAGE);
                }
            }
            if (dialog == JFileChooser.CANCEL_OPTION) {
                return;
            }
        }
    }

    private void saveSelection() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));

        boolean notSaved = true;
        while (notSaved) {
            try {
                int chose = chooser.showSaveDialog(frame);
                if (chose == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    if (!file.getName().endsWith("png")) {
                        String name = file.getName() + ".png";
                        file = new File(file.getParent(), name);
                    }
                    if (file.exists()) {
                        int res = JOptionPane.showConfirmDialog(null,
                                "This file already exists. Overwrite?",
                                "Confirm Overwrite", JOptionPane.YES_NO_CANCEL_OPTION);
                        if (res == JOptionPane.YES_OPTION) {
                            try (FileOutputStream stream = new FileOutputStream(file)) {
                                model.saveSelection(stream);
                                notSaved = false;
                            }
                        } else if (res == JOptionPane.CANCEL_OPTION) {
                            return;
                        }
                    } else {
                        try (FileOutputStream stream = new FileOutputStream(file)) {
                            model.saveSelection(stream);
                            notSaved = false;
                        }
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, e.getMessage(),
                        e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
            }

            SelectorApp app = new SelectorApp();
            app.start();
        });
    }
}
