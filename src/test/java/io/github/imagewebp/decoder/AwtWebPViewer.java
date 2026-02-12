package io.github.imagewebp.decoder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/** Manual AWT/Swing viewer for WebP files (test-scope only; not used by automated tests). */
public final class AwtWebPViewer {
    private AwtWebPViewer() {}

    public static void main(String[] args) throws Exception {
        File file;
        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            file = new File(args[0]);
        } else {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Open WebP");
            int res = chooser.showOpenDialog(null);
            if (res != JFileChooser.APPROVE_OPTION) {
                return;
            }
            file = chooser.getSelectedFile();
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        DecodedWebP decoded = WebPDecoder.decode(bytes);
        BufferedImage img = toBufferedImage(decoded);

        SwingUtilities.invokeLater(() -> show(img, decoded, file));
    }

    private static void show(BufferedImage img, DecodedWebP decoded, File file) {
        JFrame frame = new JFrame("WebP Viewer - " + file.getName());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JLabel info = new JLabel(decoded.width + "x" + decoded.height + (decoded.hasAlpha ? " (alpha)" : ""));
        info.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel imageLabel = new JLabel(new ImageIcon(img));
        JScrollPane scroll = new JScrollPane(imageLabel);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(info, BorderLayout.NORTH);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);

        frame.setSize(Math.min(1200, decoded.width + 40), Math.min(900, decoded.height + 80));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static BufferedImage toBufferedImage(DecodedWebP decoded) {
        int w = decoded.width;
        int h = decoded.height;
        byte[] rgba = decoded.rgba;

        int[] argb = new int[w * h];
        for (int i = 0, p = 0; i < argb.length; i++, p += 4) {
            int r = rgba[p] & 0xFF;
            int g = rgba[p + 1] & 0xFF;
            int b = rgba[p + 2] & 0xFF;
            int a = rgba[p + 3] & 0xFF;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }
}
