package de.gaucho.killsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Was der Spieler eingestellt hat. Liegt in .minecraft/config/gauchos_killsounds.json */
public class KillSoundsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path FILE = DIR.resolve("gauchos_killsounds.json");
	/** Einstellungen der alten "Texture Gallery"-Version werden einmalig uebernommen. */
	private static final Path OLD_FILE = DIR.resolve("gauchos_gallery.json");

	/** Name des Kill-Sounds, null = kein Sound. */
	public String killSound = null;
	/** 0.0 - 1.0 */
	public float killSoundVolume = 1.0f;

	// ---- Aussehen des Menues (jeder Spieler fuer sich) ----
	/** "dark" oder "light" */
	public String theme = "dark";
	/** Name der Akzentfarbe, siehe Theme.ACCENTS */
	public String accent = "Gold";
	/** null = automatisch (erstes Bild), "-" = kein Bild, sonst Name des Bildes */
	public String background = null;
	/** Wie stark das Hintergrundbild abgedunkelt/aufgehellt wird, 0-80 % */
	public int backgroundDim = 40;

	public static KillSoundsConfig load() {
		Path source = Files.exists(FILE) ? FILE : Files.exists(OLD_FILE) ? OLD_FILE : null;
		if (source != null) {
			try (Reader r = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				KillSoundsConfig cfg = GSON.fromJson(r, KillSoundsConfig.class);
				if (cfg != null) {
					if (source == OLD_FILE) cfg.save();
					return cfg;
				}
			} catch (Exception e) {
				GauchosKillSounds.LOGGER.warn("Konnte Einstellungen nicht lesen, nutze Standard", e);
			}
		}
		return new KillSoundsConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			GauchosKillSounds.LOGGER.error("Konnte Einstellungen nicht speichern", e);
		}
	}
}
