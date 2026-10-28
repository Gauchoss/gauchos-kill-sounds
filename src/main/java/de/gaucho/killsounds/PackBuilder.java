package de.gaucho.killsounds;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Packt Kill-Sounds und Hintergrundbilder in ein kleines Resource Pack (.minecraft/resourcepacks/GauchosKillSounds),
 * damit Minecraft sie abspielen bzw. anzeigen kann. Es aendert keine Spiel-Texturen.
 */
public final class PackBuilder {
	public static final String FOLDER = "GauchosKillSounds";
	public static final String PACK_ID = "file/" + FOLDER;
	public static final String NAMESPACE = "gauchos_killsounds";

	/** Reste der alten "Texture Gallery"-Version, die beim Start entfernt werden. */
	public static final String OLD_FOLDER = "GauchosTextureGallery";
	public static final String OLD_PACK_ID = "file/" + OLD_FOLDER;

	// Minecraft 1.21.11 = Resource-Pack-Format 75
	private static final String PACK_MCMETA = """
			{
			  "pack": {
			    "description": "§6Gauchos Kill Sounds§r\\nWird automatisch von der Mod verwaltet",
			    "min_format": 75,
			    "max_format": 99
			  }
			}
			""";

	private PackBuilder() {}

	public static Path packsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
	}

	public static Path packDir() {
		return packsDir().resolve(FOLDER);
	}

	public static String backgroundPath(int index, int frame) {
		return "textures/background/b" + index + "_" + frame + ".png";
	}

	public static String soundEventPath(int index) {
		return "kill.s" + index;
	}

	/** Entfernt Ordner der alten Texture-Gallery-Version (laeuft beim Start, bevor Ressourcen geladen werden). */
	public static void cleanupOldVersion() {
		try {
			deleteRecursively(packsDir().resolve(OLD_FOLDER));
			deleteRecursively(FabricLoader.getInstance().getConfigDir().resolve("gauchos_gallery_cache"));
		} catch (IOException e) {
			GauchosKillSounds.LOGGER.warn("Alte Texture-Gallery-Dateien konnten nicht geloescht werden", e);
		}
	}

	/**
	 * Schreibt den Pack-Ordner neu (nur wenn sich etwas geaendert hat). Laeuft im Hintergrund-Thread.
	 *
	 * @return true, wenn sich der Inhalt geaendert hat und neu geladen werden muss
	 */
	public static boolean build(SoundRepository.Snapshot snap, SoundRepository repo) throws IOException {
		Map<String, String> plan = new LinkedHashMap<>();   // Zielpfad -> SHA im Cache
		Map<String, String> texts = new LinkedHashMap<>();  // Zielpfad -> Text

		// Hintergrundbilder (weich skaliert, damit grosse Bilder nicht pixelig aussehen)
		for (SoundRepository.Background b : snap.backgrounds()) {
			for (int f = 0; f < b.frames.size(); f++) {
				String p = "assets/" + NAMESPACE + "/" + backgroundPath(b.index, f);
				plan.put(p, b.frames.get(f));
				texts.put(p + ".mcmeta", "{\"texture\":{\"blur\":true}}");
			}
		}

		// Kill-Sounds
		JsonObject soundsJson = new JsonObject();
		for (SoundRepository.KillSound s : snap.killSounds()) {
			plan.put("assets/" + NAMESPACE + "/sounds/kill/s" + s.index + ".ogg", s.sha);
			JsonObject sound = new JsonObject();
			sound.addProperty("name", NAMESPACE + ":kill/s" + s.index);
			JsonArray arr = new JsonArray();
			arr.add(sound);
			JsonObject event = new JsonObject();
			event.add("sounds", arr);
			soundsJson.add(soundEventPath(s.index), event);
		}
		if (!snap.killSounds().isEmpty()) {
			texts.put("assets/" + NAMESPACE + "/sounds.json", soundsJson.toString());
		}

		// Fingerabdruck: hat sich seit dem letzten Mal etwas geaendert?
		String fingerprint = sha1(plan + "|" + texts + "|" + PACK_MCMETA);
		Path dir = packDir();
		Path stateFile = dir.resolve(".gauchos_state");
		if (Files.exists(stateFile) && Files.readString(stateFile, StandardCharsets.UTF_8).equals(fingerprint)) {
			return false;
		}

		deleteRecursively(dir);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("pack.mcmeta"), PACK_MCMETA, StandardCharsets.UTF_8);
		for (Map.Entry<String, String> e : plan.entrySet()) {
			Path source = repo.cacheFile(e.getValue());
			if (!Files.exists(source)) continue;
			Path target = dir.resolve(e.getKey());
			Files.createDirectories(target.getParent());
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		for (Map.Entry<String, String> e : texts.entrySet()) {
			Path target = dir.resolve(e.getKey());
			Files.createDirectories(target.getParent());
			Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
		}
		Files.writeString(stateFile, fingerprint, StandardCharsets.UTF_8);
		return true;
	}

	/** Aktiviert den Pack und laedt Ressourcen neu, falls noetig. Muss im Render-Thread laufen. */
	public static void apply(Minecraft mc, boolean contentChanged) {
		PackRepository packs = mc.getResourcePackRepository();
		packs.reload();
		if (!packs.getAvailableIds().contains(PACK_ID)) {
			GauchosKillSounds.LOGGER.error("Resource Pack {} wurde nicht gefunden", PACK_ID);
			return;
		}
		List<String> selected = new ArrayList<>(packs.getSelectedIds());
		boolean listChanged = selected.remove(OLD_PACK_ID);
		if (!selected.contains(PACK_ID)) {
			selected.add(PACK_ID);
			listChanged = true;
		}
		if (!listChanged && !contentChanged) return;

		if (listChanged) packs.setSelected(selected);
		List<String> before = new ArrayList<>(mc.options.resourcePacks);
		mc.options.updateResourcePacks(packs); // speichert options.txt, laedt neu falls Liste geaendert
		if (before.equals(mc.options.resourcePacks)) {
			mc.reloadResourcePacks();
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		Path root = dir.toAbsolutePath().normalize();
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				if (p.toAbsolutePath().normalize().startsWith(root)) Files.deleteIfExists(p);
			}
		}
	}

	private static String sha1(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
