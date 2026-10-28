package de.gaucho.killsounds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Wandelt JPG/GIF/BMP in PNG um (bei GIFs wird das erste Bild genommen). */
final class ImageConverter {
	private ImageConverter() {}

	static void toPng(Path source, Path target) throws IOException {
		if (System.getProperty("java.awt.headless") == null) {
			System.setProperty("java.awt.headless", "true");
		}
		BufferedImage src = ImageIO.read(source.toFile());
		if (src == null) throw new IOException("unbekanntes Bildformat");
		// In normales RGBA umwandeln (GIFs sind sonst Paletten-Bilder)
		BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		if (!ImageIO.write(img, "png", tmp.toFile())) throw new IOException("PNG konnte nicht geschrieben werden");
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
	}
}
