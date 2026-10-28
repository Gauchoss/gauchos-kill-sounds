package de.gaucho.killsounds;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Wandelt JPG/GIF/BMP in PNG um und zerlegt animierte GIFs in Einzelbilder. */
final class ImageConverter {
	/** Hoechstens so viele Einzelbilder pro GIF (bei mehr werden Bilder zusammengefasst). */
	private static final int MAX_FRAMES = 150;
	/** Groesste Kante eines Einzelbilds. */
	private static final int MAX_SIZE = 1280;
	/** Pixel-Budget fuer alle Einzelbilder zusammen (haelt den Grafikspeicher klein, ca. 100 MB). */
	private static final long PIXEL_BUDGET = 25_000_000L;

	/** Groesse der Einzelbilder und Anzeigedauer jedes Bilds. */
	record GifInfo(int width, int height, int[] delaysMs) {}

	/** Bekommt jedes fertige Einzelbild sofort (wird direkt auf die Festplatte geschrieben). */
	@FunctionalInterface
	interface FrameSink {
		void accept(int index, BufferedImage image) throws IOException;
	}

	private ImageConverter() {}

	static {
		if (System.getProperty("java.awt.headless") == null) {
			System.setProperty("java.awt.headless", "true");
		}
	}

	static boolean isGif(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] h = in.readNBytes(4);
			return h.length == 4 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
		} catch (IOException e) {
			return false;
		}
	}

	static void toPng(Path source, Path target) throws IOException {
		BufferedImage src = ImageIO.read(source.toFile());
		if (src == null) throw new IOException("unbekanntes Bildformat");
		writePng(toArgb(src), target);
	}

	static void writePng(BufferedImage img, Path target) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		if (!ImageIO.write(img, "png", tmp.toFile())) throw new IOException("PNG konnte nicht geschrieben werden");
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Liest alle Einzelbilder einer GIF (fertig zusammengesetzt, wie sie ein Browser anzeigt).
	 * Jedes Bild wird sofort verkleinert und bei sehr vielen Bildern zusammengefasst,
	 * damit auch grosse GIFs wenig Arbeitsspeicher brauchen.
	 */
	static GifInfo readGif(Path file, FrameSink sink) throws IOException {
		List<Integer> delays = new ArrayList<>();
		int lastW = 0, lastH = 0;
		try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext()) throw new IOException("kein GIF-Leser");
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, false);
				int count;
				try {
					count = Math.max(1, reader.getNumImages(true));
				} catch (Exception e) {
					count = 1;
				}

				int width = 0, height = 0;
				IIOMetadata streamMeta = reader.getStreamMetadata();
				if (streamMeta != null) {
					IIOMetadataNode root = (IIOMetadataNode) streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
					IIOMetadataNode lsd = child(root, "LogicalScreenDescriptor");
					if (lsd != null) {
						width = intAttr(lsd, "logicalScreenWidth", 0);
						height = intAttr(lsd, "logicalScreenHeight", 0);
					}
				}

				int target = Math.min(count, MAX_FRAMES);
				BufferedImage canvas = null;
				int outW = 0, outH = 0;
				int lastKept = -1;
				for (int i = 0; i < count; i++) {
					BufferedImage part;
					IIOMetadata meta;
					try {
						part = reader.read(i);
						meta = reader.getImageMetadata(i);
					} catch (Exception broken) {
						// kaputtes Bild mitten in der GIF -> bis hierhin verwenden
						if (delays.isEmpty()) throw new IOException("GIF unlesbar: " + broken);
						break;
					}
					IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
					IIOMetadataNode desc = child(root, "ImageDescriptor");
					IIOMetadataNode gce = child(root, "GraphicControlExtension");
					int x = desc != null ? intAttr(desc, "imageLeftPosition", 0) : 0;
					int y = desc != null ? intAttr(desc, "imageTopPosition", 0) : 0;
					int delay = gce != null ? intAttr(gce, "delayTime", 10) * 10 : 100;
					if (delay < 20) delay = 100; // wie Browser: 0/1 = 100 ms
					String disposal = gce != null ? gce.getAttribute("disposalMethod") : "none";

					if (canvas == null) {
						if (width <= 0 || height <= 0) {
							width = part.getWidth() + x;
							height = part.getHeight() + y;
						}
						canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
						double scale = Math.min(1.0, (double) MAX_SIZE / Math.max(width, height));
						scale = Math.min(scale, Math.sqrt((double) PIXEL_BUDGET / ((double) width * height * target)));
						outW = Math.max(1, (int) Math.round(width * scale));
						outH = Math.max(1, (int) Math.round(height * scale));
						lastW = outW;
						lastH = outH;
					}
					BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;

					Graphics2D g = canvas.createGraphics();
					g.drawImage(part, x, y, null);
					g.dispose();

					// Bei zu vielen Bildern nur jedes n-te behalten, die Anzeigedauer wird aufaddiert
					int slot = (int) ((long) i * target / count);
					if (slot != lastKept) {
						sink.accept(delays.size(), scaled(canvas, outW, outH));
						delays.add(delay);
						lastKept = slot;
					} else {
						delays.set(delays.size() - 1, delays.get(delays.size() - 1) + delay);
					}

					if ("restoreToBackgroundColor".equals(disposal)) {
						Graphics2D c = canvas.createGraphics();
						c.setComposite(AlphaComposite.Clear);
						c.fillRect(x, y, part.getWidth(), part.getHeight());
						c.dispose();
					} else if (before != null) {
						canvas = before;
					}
				}
			} finally {
				reader.dispose();
			}
		}
		if (delays.isEmpty()) throw new IOException("GIF ohne Bilder");
		int[] d = new int[delays.size()];
		for (int i = 0; i < d.length; i++) d[i] = delays.get(i);
		return new GifInfo(lastW, lastH, d);
	}

	private static BufferedImage scaled(BufferedImage src, int w, int h) {
		if (src.getWidth() == w && src.getHeight() == h) return copy(src);
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return small;
	}

	private static BufferedImage toArgb(BufferedImage src) {
		BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return img;
	}

	private static BufferedImage copy(BufferedImage src) {
		return toArgb(src);
	}

	private static IIOMetadataNode child(IIOMetadataNode node, String name) {
		for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) return (IIOMetadataNode) n;
		}
		return null;
	}

	private static int intAttr(IIOMetadataNode node, String name, int def) {
		try {
			return Integer.parseInt(node.getAttribute(name));
		} catch (Exception e) {
			return def;
		}
	}
}
