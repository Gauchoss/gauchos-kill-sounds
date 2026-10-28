package de.gaucho.killsounds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Laedt Kill-Sounds und Hintergrundbilder aus dem GitHub-Repo.
 * Erkannt werden die Ordner "killsounds/" und "backgrounds/" (auch unter "gallery/", wie in der alten Version).
 * Nur wer Schreibrechte auf das Repo hat (= du), kann dort Dateien hochladen.
 */
public class SoundRepository {

	public static final class KillSound {
		public final String name;
		public final String sha;
		public final int index;

		KillSound(String name, String sha, int index) {
			this.name = name;
			this.sha = sha;
			this.index = index;
		}
	}

	/** Hintergrundbild fuers Menue. */
	public static final class Background {
		public final String name;
		public final String sha;
		public final int index;
		public final int width;
		public final int height;

		Background(String name, String sha, int index, int width, int height) {
			this.name = name;
			this.sha = sha;
			this.index = index;
			this.width = width;
			this.height = height;
		}
	}

	public record Snapshot(List<KillSound> killSounds, List<Background> backgrounds) {
		public static final Snapshot EMPTY = new Snapshot(List.of(), List.of());

		public KillSound findSound(String name) {
			if (name == null) return null;
			for (KillSound s : killSounds) {
				if (s.name.equals(name)) return s;
			}
			return null;
		}

		public Background findBackground(String name) {
			if (name == null) return null;
			for (Background b : backgrounds) {
				if (b.name.equals(name)) return b;
			}
			return null;
		}
	}

	private static final String SOUND_FOLDER = "killsounds";
	private static final String BACKGROUND_FOLDER = "backgrounds";

	private final Path cacheDir = FabricLoader.getInstance().getConfigDir().resolve("gauchos_killsounds_cache");
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final AtomicBoolean loading = new AtomicBoolean(false);
	private final String repo;
	private final String branch;

	private volatile Snapshot snapshot = Snapshot.EMPTY;
	private volatile String status = "Sounds werden noch nicht geladen";

	public SoundRepository() {
		String r = "";
		String b = "main";
		try (InputStream in = SoundRepository.class.getResourceAsStream("/gauchos_killsounds_source.json")) {
			if (in != null) {
				JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				r = o.get("repo").getAsString().trim();
				b = o.get("branch").getAsString().trim();
			}
		} catch (Exception e) {
			GauchosKillSounds.LOGGER.error("gauchos_killsounds_source.json fehlt/kaputt", e);
		}
		this.repo = r;
		this.branch = b.isEmpty() ? "main" : b;
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	public String status() {
		return status;
	}

	public boolean isLoading() {
		return loading.get();
	}

	public Path cacheFile(String sha) {
		return cacheDir.resolve(sha);
	}

	/** Startet das Laden im Hintergrund. Gibt null zurueck, wenn schon geladen wird. */
	public CompletableFuture<Snapshot> refresh() {
		if (!loading.compareAndSet(false, true)) return null;
		return CompletableFuture.supplyAsync(() -> {
			try {
				Snapshot s = load();
				snapshot = s;
				return s;
			} catch (Exception e) {
				GauchosKillSounds.LOGGER.error("Sounds konnten nicht geladen werden", e);
				status = "Fehler: " + e.getMessage();
				return snapshot;
			} finally {
				loading.set(false);
			}
		});
	}

	private Snapshot load() throws Exception {
		if (repo.isEmpty() || repo.contains("DEIN-GITHUB-NAME")) {
			status = "Kein GitHub-Repo eingestellt (siehe README)";
			return Snapshot.EMPTY;
		}
		Files.createDirectories(cacheDir);
		status = "Lade von GitHub …";

		// 1) Dateiliste des Repos holen (ein einziger API-Aufruf)
		String treeJson;
		Path treeCache = cacheDir.resolve("tree.json");
		boolean offline = false;
		try {
			String url = "https://api.github.com/repos/" + repo + "/git/trees/" + enc(branch) + "?recursive=1";
			HttpResponse<String> res = http.send(request(url).header("Accept", "application/vnd.github+json").build(),
					HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				throw new IOException("GitHub antwortet mit " + res.statusCode()
						+ (res.statusCode() == 404 ? " (Repo privat oder falscher Name?)" : "")
						+ (res.statusCode() == 403 ? " (zu viele Anfragen – später nochmal)" : ""));
			}
			treeJson = res.body();
			Files.writeString(treeCache, treeJson, StandardCharsets.UTF_8);
		} catch (Exception e) {
			if (!Files.exists(treeCache)) throw e;
			GauchosKillSounds.LOGGER.warn("GitHub nicht erreichbar, nutze letzten Stand: {}", e.getMessage());
			treeJson = Files.readString(treeCache, StandardCharsets.UTF_8);
			offline = true;
		}

		// 2) Dateiliste auswerten
		Map<String, String> sounds = new LinkedHashMap<>();      // Name -> sha
		Map<String, String> backgrounds = new LinkedHashMap<>(); // Name -> sha
		Map<String, String> shaToPath = new LinkedHashMap<>();

		JsonArray tree = JsonParser.parseString(treeJson).getAsJsonObject().getAsJsonArray("tree");
		for (JsonElement el : tree) {
			JsonObject entry = el.getAsJsonObject();
			if (!"blob".equals(entry.get("type").getAsString())) continue;
			String path = entry.get("path").getAsString();
			String sha = entry.get("sha").getAsString();
			String rel = path.startsWith("gallery/") ? path.substring("gallery/".length()) : path;
			String[] parts = rel.split("/");
			if (parts.length != 2) continue;
			String folder = parts[0].toLowerCase();
			String file = parts[1];

			if (folder.equals(SOUND_FOLDER) && file.toLowerCase().endsWith(".ogg")) {
				sounds.putIfAbsent(stripExt(file, ".ogg"), sha);
				shaToPath.put(sha, path);
			} else if (folder.equals(BACKGROUND_FOLDER)) {
				String ext = imageExtension(file);
				if (ext != null) {
					backgrounds.putIfAbsent(stripExt(file, ext), sha);
					shaToPath.put(sha, path);
				}
			}
		}

		// 3) Fehlende Dateien herunterladen (bereits geladene liegen im Cache)
		List<String> missing = new ArrayList<>();
		for (String sha : shaToPath.keySet()) {
			if (!Files.exists(cacheFile(sha))) missing.add(sha);
		}
		if (!missing.isEmpty() && !offline) download(shaToPath, missing);

		// 4) Ergebnis bauen
		List<String> soundNames = new ArrayList<>(sounds.keySet());
		soundNames.sort(Comparator.comparing(String::toLowerCase));
		List<KillSound> soundList = new ArrayList<>();
		for (String name : soundNames) {
			String sha = sounds.get(name);
			if (Files.exists(cacheFile(sha))) soundList.add(new KillSound(name, sha, soundList.size()));
		}

		List<String> bgNames = new ArrayList<>(backgrounds.keySet());
		bgNames.sort(Comparator.comparing(String::toLowerCase));
		List<Background> bgList = new ArrayList<>();
		int skipped = 0;
		for (String name : bgNames) {
			String sha = backgrounds.get(name);
			if (!Files.exists(cacheFile(sha))) continue;
			String key = sha;
			int[] size = pngSize(cacheFile(key));
			if (size == null) {
				// JPG/GIF/BMP -> in PNG umwandeln, Minecraft kann nur PNG
				key = sha + ".png";
				Path converted = cacheFile(key);
				if (!Files.exists(converted)) {
					try {
						ImageConverter.toPng(cacheFile(sha), converted);
					} catch (Throwable e) {
						GauchosKillSounds.LOGGER.warn("Hintergrund {} konnte nicht umgewandelt werden: {}", name, e.toString());
					}
				}
				size = Files.exists(converted) ? pngSize(converted) : null;
			}
			if (size == null) {
				skipped++;
				continue;
			}
			bgList.add(new Background(name, key, bgList.size(), size[0], size[1]));
		}

		status = (offline ? "Offline – " : "") + soundList.size() + " Sounds · " + bgList.size() + " Hintergründe"
				+ (skipped > 0 ? " (" + skipped + " Bild(er) unlesbar)" : "");
		return new Snapshot(List.copyOf(soundList), List.copyOf(bgList));
	}

	private void download(Map<String, String> shaToPath, List<String> missing) {
		AtomicInteger done = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<CompletableFuture<Void>> jobs = new ArrayList<>();
			for (String sha : missing) {
				jobs.add(CompletableFuture.runAsync(() -> {
					try {
						downloadOne(shaToPath.get(sha), sha);
					} catch (Exception e) {
						GauchosKillSounds.LOGGER.warn("Download fehlgeschlagen: {} – {}", shaToPath.get(sha), e.getMessage());
					}
					status = "Lade Dateien … " + done.incrementAndGet() + "/" + missing.size();
				}, pool));
			}
			CompletableFuture.allOf(jobs.toArray(new CompletableFuture<?>[0])).join();
		} finally {
			pool.shutdown();
		}
	}

	private void downloadOne(String path, String sha) throws IOException, InterruptedException {
		StringBuilder encPath = new StringBuilder();
		for (String seg : path.split("/")) {
			if (!encPath.isEmpty()) encPath.append('/');
			encPath.append(enc(seg));
		}
		String url = "https://raw.githubusercontent.com/" + repo + "/" + enc(branch) + "/" + encPath;
		Path tmp = cacheDir.resolve(sha + ".part" + Thread.currentThread().threadId());
		HttpResponse<Path> res = http.send(request(url).build(), HttpResponse.BodyHandlers.ofFile(tmp));
		if (res.statusCode() != 200) {
			Files.deleteIfExists(tmp);
			throw new IOException("HTTP " + res.statusCode());
		}
		Files.move(tmp, cacheFile(sha), StandardCopyOption.REPLACE_EXISTING);
	}

	private static String imageExtension(String file) {
		String lower = file.toLowerCase();
		for (String ext : new String[]{".png", ".jpg", ".jpeg", ".gif", ".bmp"}) {
			if (lower.endsWith(ext)) return ext;
		}
		return null;
	}

	/** Liest Breite/Hoehe aus dem PNG-Header (ohne das Bild zu laden). */
	private static int[] pngSize(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] h = in.readNBytes(24);
			if (h.length < 24 || (h[0] & 0xFF) != 0x89 || h[1] != 'P' || h[2] != 'N' || h[3] != 'G') return null;
			int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
			int ht = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
			return (w > 0 && ht > 0) ? new int[]{w, ht} : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", "GauchosKillSounds");
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String stripExt(String name, String ext) {
		return name.substring(0, name.length() - ext.length());
	}
}
