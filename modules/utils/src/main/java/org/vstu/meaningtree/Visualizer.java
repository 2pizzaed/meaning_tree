package org.vstu.meaningtree;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import org.vstu.meaningtree.serializers.dot.GraphvizDotSerializer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class Visualizer extends JFrame {
    private final ImageIcon _imageIcon;
    private final JLabel _imageLabel;

    public Visualizer(MeaningTree mt) throws IOException {
        super("Meaning Tree");
        String imagePath = "tree.png";
        generatePicture(mt, imagePath);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        _imageIcon = new ImageIcon(imagePath);
        _imageLabel = new JLabel(_imageIcon);

        JScrollPane scrollPane = new JScrollPane(_imageLabel);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        scrollPane.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                int rotation = -e.getWheelRotation();
                double scale = 1.0 + rotation * 0.1;
                zoomImage(scale);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void generatePicture(MeaningTree mt, String path) throws IOException {
        String dot = new GraphvizDotSerializer().serialize(mt);
        MutableGraph tree = new Parser().read(dot);
        File outputImage = new File(path);
        Graphviz.fromGraph(tree).render(Format.PNG).toFile(outputImage);
    }

    private void zoomImage(double scale) {
        int newWidth = (int) (_imageIcon.getIconWidth() * scale);
        int newHeight = (int) (_imageIcon.getIconHeight() * scale);

        // Масштабирование изображения
        Image scaledImage = _imageIcon.getImage().getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

        // Установка масштабированного изображения в метку
        _imageIcon.setImage(scaledImage);
        _imageLabel.setIcon(_imageIcon);

        // Обновление размера JScrollPane
        _imageLabel.setPreferredSize(new Dimension(newWidth, newHeight));
        _imageLabel.revalidate();
        _imageLabel.repaint();
    }

    public void visualize() {
        setVisible(true);
    }
}
