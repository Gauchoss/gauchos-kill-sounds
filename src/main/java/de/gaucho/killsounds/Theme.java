package de.gaucho.killsounds;

import java.util.LinkedHashMap;
import java.util.Map;

/** Farben des Menues. Jeder Spieler waehlt Hell/Dunkel und eine Akzentfarbe selbst. */
public record Theme(
		boolean dark,
		int overlay,      // ueber dem Hintergrund (ohne Bild)
		int panel,        // Hauptfenster
		int sidebar,      // linke Leiste
		int surface,      // Kacheln, Zeilen
		int surfaceHover,
		int border,
		int text,
		int textMuted,
		int accent,
		int onAccent      // Textfarbe auf Akzent-Flaechen
) {
	public static final Map<String, Integer> ACCENTS = new LinkedHashMap<>();

	static {
		ACCENTS.put("Gold", 0xFFF2B53A);
		ACCENTS.put("Orange", 0xFFFF8A3D);
		ACCENTS.put("Rot", 0xFFFF5A5F);
		ACCENTS.put("Pink", 0xFFFF6EC7);
		ACCENTS.put("Lila", 0xFFA77BFF);
		ACCENTS.put("Blau", 0xFF4C8DFF);
		ACCENTS.put("Türkis", 0xFF2ED3C6);
		ACCENTS.put("Grün", 0xFF3DD68C);
	}

	public static Theme current() {
		KillSoundsConfig cfg = GauchosKillSounds.config();
		int accent = ACCENTS.getOrDefault(cfg.accent, ACCENTS.get("Gold"));
		int onAccent = luminance(accent) > 0.55 ? 0xFF15151A : 0xFFFFFFFF;
		if ("light".equals(cfg.theme)) {
			return new Theme(false,
					0x66F0F0F5,
					0xEEF6F6F9,
					0xEEEBEBF1,
					0xFFFFFFFF,
					0xFFE4E4EC,
					0xFFD3D3DD,
					0xFF1B1B22,
					0xFF6C6C7A,
					accent, onAccent);
		}
		return new Theme(true,
				0x88000000,
				0xE6141419,
				0xEE0E0E12,
				0xFF1E1E26,
				0xFF2A2A35,
				0xFF2F2F3B,
				0xFFEDEDF2,
				0xFF8C8C9A,
				accent, onAccent);
	}

	/** Farbe mit anderem Alpha (0-255). */
	public static int alpha(int color, int alpha) {
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	private static double luminance(int c) {
		double r = ((c >> 16) & 0xFF) / 255.0;
		double g = ((c >> 8) & 0xFF) / 255.0;
		double b = (c & 0xFF) / 255.0;
		return 0.299 * r + 0.587 * g + 0.114 * b;
	}
}
