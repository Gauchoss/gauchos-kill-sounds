package de.gaucho.killsounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Das Ingame-Menue (Standardtaste: G).
 * Startseite: Kill-Sound auswaehlen und anhoeren. ⚙ = Aussehen (Design, Farbe, Hintergrund).
 */
public class KillSoundsScreen extends Screen {
	private static final String PAGE_SOUNDS = "sounds";
	private static final String PAGE_SETTINGS = "settings";

	private static final int HEADER = 30;
	private static final int PAD = 14;
	private static final int ROW = 20;
	private static final int ROW_GAP = 3;
	private static final int GAP = 6;
	private static final float[] VOLUMES = {0.25f, 0.5f, 0.75f, 1.0f};
	private static final int[] DIMS = {0, 20, 40, 60, 80};

	private final Screen parent;
	private String page = PAGE_SOUNDS;
	private int scroll = 0;
	private int seenVersion = -1;
	private int tickCounter = 0;

	// Layout (wird in init() berechnet)
	private Theme t;
	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private final List<Label> labels = new ArrayList<>();
	private final Map<Identifier, Boolean> loaded = new HashMap<>();

	private record Label(String text, int x, int y, int color) {}

	public KillSoundsScreen(Screen parent) {
		super(Component.literal("Gauchos Kill Sounds"));
		this.parent = parent;
	}

	// =============================================================== Aufbau

	@Override
	protected void init() {
		seenVersion = GauchosKillSounds.dataVersion();
		t = Theme.current();
		labels.clear();

		pw = Math.min(this.width - 20, 440);
		ph = Math.min(this.height - 20, 340);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		cx = px + PAD;
		cy = py + HEADER + 12;
		cw = pw - PAD * 2;
		ch = py + ph - 12 - cy;

		buildHeader();
		if (PAGE_SETTINGS.equals(page)) buildSettings();
		else buildSounds();
	}

	private void buildHeader() {
		int size = 18;
		int y = py + (HEADER - size) / 2;

		if (PAGE_SETTINGS.equals(page)) {
			add(new UiButton(px + 8, y, size, size, "Zurück", (g, b, hov) -> iconButton(g, b, hov, "←", false), this::goBack))
					.tooltip("Zurück zu den Sounds");
		}

		int x = px + pw - 8 - size;
		add(new UiButton(x, y, size, size, "Schließen", (g, b, hov) -> iconButton(g, b, hov, "✕", false), this::closeMenu))
				.tooltip("Schließen");
		x -= size + 4;
		add(new UiButton(x, y, size, size, "Aussehen",
				(g, b, hov) -> iconButton(g, b, hov, "⚙", PAGE_SETTINGS.equals(page)), this::toggleSettings))
				.tooltip("Aussehen");
		x -= size + 4;
		UiButton reload = add(new UiButton(x, y, size, size, "Neu laden",
				(g, b, hov) -> iconButton(g, b, hov, "↻", false), GauchosKillSounds::reload))
				.tooltip("Neue Sounds von GitHub laden");
		reload.active = !GauchosKillSounds.repository().isLoading();
	}

	// ---------- Startseite: Sounds ----------

	private void buildSounds() {
		KillSoundsConfig cfg = GauchosKillSounds.config();
		List<SoundRepository.KillSound> sounds = GauchosKillSounds.repository().snapshot().killSounds();
		SoundRepository.KillSound active = GauchosKillSounds.repository().snapshot().findSound(cfg.killSound);

		// Aktiver Sound (grosse Karte mit Test-Knopf)
		int cardH = 34;
		String activeName = active != null ? active.name : "Kein Sound";
		add(new UiButton(cx, cy, cw, cardH, "Aktiver Sound", (g, b, hov) -> {
			box(g, b, Theme.alpha(t.accent(), active != null ? 0x30 : 0x18));
			g.fill(b.getX(), b.getY(), b.getX() + 3, b.getY() + b.getHeight(), t.accent());
			text(g, "AKTIVER KILL-SOUND", b.getX() + 10, b.getY() + 6, t.textMuted());
			text(g, fit(activeName, b.getWidth() - 50), b.getX() + 10, b.getY() + 19, active != null ? t.text() : t.textMuted());
			// Test-Knopf rechts
			int bx = b.getX() + b.getWidth() - 30, by = b.getY() + 7;
			g.fill(bx, by, bx + 22, by + 20, active == null ? t.surface() : hov ? Theme.alpha(t.accent(), 0xDD) : t.accent());
			centered(g, "▶", bx + 12, by + 6, active == null ? t.textMuted() : t.onAccent());
		}, () -> {
			if (active != null) KillTracker.playSound(active, cfg.killSoundVolume);
		})).tooltip(active != null ? "Anhören" : "Wähle unten einen Sound aus");

		// Lautstaerke
		int y = cy + cardH + 10;
		labels.add(new Label("Lautstärke", cx, y + 4, t.textMuted()));
		int x = cx + 64;
		for (float v : VOLUMES) {
			String txt = Math.round(v * 100) + "%";
			boolean sel = Math.abs(v - cfg.killSoundVolume) < 0.01f;
			add(new UiButton(x, y, 36, 16, txt, (g, b, hov) -> pill(g, b, hov, txt, sel), () -> {
				cfg.killSoundVolume = v;
				cfg.save();
				if (active != null) KillTracker.playSound(active, v);
				this.rebuildWidgets();
			}));
			x += 40;
		}

		// Liste aller Sounds
		int top = y + 26;
		labels.add(new Label("ALLE SOUNDS", cx, top, t.textMuted()));
		String count = sounds.size() + (sounds.size() == 1 ? " Sound" : " Sounds");
		labels.add(new Label(count, cx + cw - this.font.width(count), top, t.textMuted()));
		top += 14;

		List<SoundRepository.KillSound> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(sounds);
		int visible = Math.max(1, (cy + ch - top + ROW_GAP) / (ROW + ROW_GAP));
		scroll = clamp(scroll, 0, Math.max(0, entries.size() - visible));

		for (int i = 0; i < visible; i++) {
			int idx = scroll + i;
			if (idx >= entries.size()) break;
			SoundRepository.KillSound s = entries.get(idx);
			String name = s == null ? null : s.name;
			String label = s == null ? "Kein Sound" : s.name;
			boolean sel = Objects.equals(active == null ? null : active.name, name);
			int ry = top + i * (ROW + ROW_GAP);
			int w = s == null ? cw : cw - ROW - ROW_GAP;
			add(new UiButton(cx, ry, w, ROW, label, (g, b, hov) -> {
				box(g, b, sel ? Theme.alpha(t.accent(), 0x40) : (hov ? t.surfaceHover() : t.surface()));
				if (sel) g.fill(b.getX(), b.getY(), b.getX() + 2, b.getY() + b.getHeight(), t.accent());
				int tx = b.getX() + 8;
				if (sel) {
					text(g, "✔", tx, b.getY() + 6, t.accent());
					tx += 12;
				}
				text(g, fit(label, b.getX() + b.getWidth() - 8 - tx), tx, b.getY() + 6, s == null ? t.textMuted() : t.text());
			}, () -> {
				cfg.killSound = name;
				cfg.save();
				if (s != null) KillTracker.playSound(s, cfg.killSoundVolume);
				this.rebuildWidgets();
			})).tooltip(s == null ? "Keinen Sound abspielen" : "Auswählen");
			if (s != null) {
				add(new UiButton(cx + cw - ROW, ry, ROW, ROW, "Anhören", (g, b, hov) -> {
					box(g, b, hov ? t.surfaceHover() : t.surface());
					centered(g, "▶", b.getX() + b.getWidth() / 2 + 1, b.getY() + 6, hov ? t.accent() : t.textMuted());
				}, () -> KillTracker.playSound(s, cfg.killSoundVolume))).tooltip("Anhören");
			}
		}

		if (sounds.isEmpty()) {
			labels.add(new Label(GauchosKillSounds.repository().isLoading() ? "Lade Sounds …"
					: "Noch keine Sounds – lade .ogg-Dateien in den Ordner killsounds/ hoch.", cx, top + ROW + 10, t.textMuted()));
		}
		if (scroll + visible < entries.size()) {
			String more = "▼ scrollen";
			labels.add(new Label(more, cx + cw - this.font.width(more), py + ph - 10, t.textMuted()));
		}
	}

	// ---------- Aussehen ----------

	private void buildSettings() {
		KillSoundsConfig cfg = GauchosKillSounds.config();
		int y = cy;
		int lx = cx;
		int vx = cx + 80;

		labels.add(new Label("Design", lx, y + 4, t.textMuted()));
		int x = vx;
		for (String[] opt : new String[][]{{"dark", "Dunkel"}, {"light", "Hell"}}) {
			boolean sel = opt[0].equals("light") == "light".equals(cfg.theme);
			int w = this.font.width(opt[1]) + 16;
			add(new UiButton(x, y, w, 16, opt[1], (g, b, hov) -> pill(g, b, hov, opt[1], sel), () -> {
				cfg.theme = opt[0];
				saveSettings();
			}));
			x += w + 4;
		}

		y += 26;
		labels.add(new Label("Farbe", lx, y + 4, t.textMuted()));
		x = vx;
		for (Map.Entry<String, Integer> e : Theme.ACCENTS.entrySet()) {
			String name = e.getKey();
			int color = e.getValue();
			boolean sel = name.equals(cfg.accent) || (!Theme.ACCENTS.containsKey(cfg.accent) && name.equals("Gold"));
			if (x + 16 > cx + cw) break;
			add(new UiButton(x, y, 16, 16, name, (g, b, hov) -> {
				int bx = b.getX(), by = b.getY();
				if (sel || hov) outline(g, bx, by, 16, 16, 1, sel ? t.text() : t.textMuted());
				g.fill(bx + 2, by + 2, bx + 14, by + 14, color);
			}, () -> {
				cfg.accent = name;
				saveSettings();
			})).tooltip(name);
			x += 20;
		}

		y += 26;
		labels.add(new Label("Hintergrund", lx, y + 4, t.textMuted()));
		List<SoundRepository.Background> bgs = GauchosKillSounds.repository().snapshot().backgrounds();
		SoundRepository.Background active = effectiveBackground();
		int thumbW = 64, thumbH = 36;
		x = vx;
		int rowY = y;
		List<SoundRepository.Background> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(bgs);
		for (SoundRepository.Background bg : entries) {
			if (x + thumbW > cx + cw) {
				x = vx;
				rowY += thumbH + GAP;
			}
			boolean sel = Objects.equals(active, bg);
			Identifier img = bg == null ? null : backgroundId(bg.index);
			add(new UiButton(x, rowY, thumbW, thumbH, bg == null ? "Kein Bild" : bg.name, (g, b, hov) -> {
				box(g, b, hov ? t.surfaceHover() : t.surface());
				if (bg == null) {
					centered(g, "Kein Bild", b.getX() + thumbW / 2, b.getY() + thumbH / 2 - 4, t.textMuted());
				} else if (isLoaded(img)) {
					blitCover(g, img, b.getX() + 2, b.getY() + 2, thumbW - 4, thumbH - 4, bg.width, bg.height);
				} else {
					centered(g, "…", b.getX() + thumbW / 2, b.getY() + thumbH / 2 - 4, t.textMuted());
				}
				if (sel) outline(g, b.getX(), b.getY(), thumbW, thumbH, 2, t.accent());
			}, () -> {
				cfg.background = bg == null ? "-" : bg.name;
				saveSettings();
			})).tooltip(bg == null ? "Ohne Hintergrundbild" : bg.name);
			x += thumbW + GAP;
		}
		y = rowY + thumbH;
		if (bgs.isEmpty()) {
			labels.add(new Label("Bilder kommen aus backgrounds/", vx + thumbW + GAP, y - thumbH + 14, t.textMuted()));
		}

		y += 12;
		labels.add(new Label(t.dark() ? "Abdunkeln" : "Aufhellen", lx, y + 4, t.textMuted()));
		x = vx;
		for (int d : DIMS) {
			String txt = d + "%";
			boolean sel = cfg.backgroundDim == d;
			add(new UiButton(x, y, 36, 16, txt, (g, b, hov) -> pill(g, b, hov, txt, sel), () -> {
				cfg.backgroundDim = d;
				saveSettings();
			}));
			x += 40;
		}

		labels.add(new Label("Diese Einstellungen gelten nur für dich.", cx, y + 30, t.textMuted()));
	}

	// =============================================================== Logik

	private <T extends UiButton> T add(T w) {
		return this.addRenderableWidget(w);
	}

	private void toggleSettings() {
		page = PAGE_SETTINGS.equals(page) ? PAGE_SOUNDS : PAGE_SETTINGS;
		scroll = 0;
		this.rebuildWidgets();
	}

	private void goBack() {
		page = PAGE_SOUNDS;
		scroll = 0;
		this.rebuildWidgets();
	}

	private void saveSettings() {
		GauchosKillSounds.config().save();
		this.rebuildWidgets();
	}

	private SoundRepository.Background effectiveBackground() {
		KillSoundsConfig cfg = GauchosKillSounds.config();
		SoundRepository.Snapshot snap = GauchosKillSounds.repository().snapshot();
		if ("-".equals(cfg.background)) return null;
		SoundRepository.Background b = snap.findBackground(cfg.background);
		if (b != null) return b;
		return snap.backgrounds().isEmpty() ? null : snap.backgrounds().get(0);
	}

	private static Identifier backgroundId(int index) {
		return Identifier.fromNamespaceAndPath(PackBuilder.NAMESPACE, PackBuilder.backgroundPath(index));
	}

	private boolean isLoaded(Identifier id) {
		Boolean known = loaded.get(id);
		if (known != null) return known;
		boolean present = this.minecraft != null && this.minecraft.getResourceManager().getResource(id).isPresent();
		loaded.put(id, present);
		return present;
	}

	@Override
	public void tick() {
		super.tick();
		if (seenVersion != GauchosKillSounds.dataVersion()) {
			loaded.clear();
			this.rebuildWidgets();
		} else if (++tickCounter % 20 == 0) {
			loaded.values().removeIf(present -> !present);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
		if (delta == 0) return false;
		scroll += delta;
		this.rebuildWidgets();
		return true;
	}

	/** ESC: von Aussehen zurueck zu den Sounds, sonst schliessen. */
	@Override
	public void onClose() {
		if (PAGE_SETTINGS.equals(page)) {
			goBack();
			return;
		}
		closeMenu();
	}

	private void closeMenu() {
		if (this.minecraft != null) this.minecraft.setScreen(parent);
	}

	// =============================================================== Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (t == null) t = Theme.current();
		SoundRepository.Background bg = effectiveBackground();
		Identifier id = bg == null ? null : backgroundId(bg.index);
		if (id != null && isLoaded(id)) {
			blitCover(g, id, 0, 0, this.width, this.height, bg.width, bg.height);
			int dim = GauchosKillSounds.config().backgroundDim * 255 / 100;
			if (dim > 0) g.fill(0, 0, this.width, this.height, Theme.alpha(t.dark() ? 0x000000 : 0xFFFFFF, dim));
		} else {
			super.renderBackground(g, mouseX, mouseY, delta);
			g.fill(0, 0, this.width, this.height, t.overlay());
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(px, py, px + pw, py + ph, t.panel());
		g.fill(px, py + HEADER - 1, px + pw, py + HEADER, t.border());
		g.fill(px, py, px + pw, py + 2, t.accent());

		int ty = py + (HEADER - 8) / 2 + 1;
		int tx;
		int titleEnd;
		if (PAGE_SETTINGS.equals(page)) {
			tx = px + 8 + 18 + 8;
			text(g, "Aussehen", tx, ty, t.text());
			titleEnd = tx + this.font.width("Aussehen");
		} else {
			tx = px + 12;
			text(g, "♪ Gauchos", tx, ty, t.accent());
			text(g, " Kill Sounds", tx + this.font.width("♪ Gauchos"), ty, t.text());
			titleEnd = tx + this.font.width("♪ Gauchos Kill Sounds");
		}

		// Status rechts neben dem Titel (nur wenn Platz)
		int statusStart = titleEnd + 16;
		int statusEnd = px + pw - 8 - 18 * 3 - 8 - 10;
		if (statusEnd - statusStart > 40) {
			boolean loading = GauchosKillSounds.repository().isLoading();
			String status = fit((loading ? "● " : "") + GauchosKillSounds.repository().status(), statusEnd - statusStart);
			text(g, status, statusEnd - this.font.width(status), ty, loading ? t.accent() : t.textMuted());
		}

		for (Label l : labels) text(g, l.text(), l.x(), l.y(), l.color());

		super.render(g, mouseX, mouseY, delta);
	}

	// ---- Zeichen-Helfer ----

	private void iconButton(GuiGraphics g, UiButton b, boolean hov, String glyph, boolean sel) {
		if (sel) box(g, b, Theme.alpha(t.accent(), 0x55));
		else if (hov) box(g, b, t.surfaceHover());
		int color = !b.active ? Theme.alpha(t.textMuted(), 0x80) : (sel || hov ? t.text() : t.textMuted());
		centered(g, glyph, b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 8) / 2, color);
	}

	private void pill(GuiGraphics g, UiButton b, boolean hov, String txt, boolean sel) {
		box(g, b, sel ? t.accent() : (hov ? t.surfaceHover() : t.surface()));
		centered(g, txt, b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 8) / 2, sel ? t.onAccent() : t.text());
	}

	private static void box(GuiGraphics g, UiButton b, int color) {
		g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), color);
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int thick, int color) {
		g.fill(x, y, x + w, y + thick, color);
		g.fill(x, y + h - thick, x + w, y + h, color);
		g.fill(x, y + thick, x + thick, y + h - thick, color);
		g.fill(x + w - thick, y + thick, x + w, y + h - thick, color);
	}

	/** Bild so skalieren, dass es die Flaeche ausfuellt (Raender werden abgeschnitten). */
	private static void blitCover(GuiGraphics g, Identifier id, int x, int y, int w, int h, int texW, int texH) {
		float scale = Math.max((float) w / texW, (float) h / texH);
		int regionW = Math.max(1, Math.min(texW, Math.round(w / scale)));
		int regionH = Math.max(1, Math.min(texH, Math.round(h / scale)));
		float u = (texW - regionW) / 2f;
		float v = (texH - regionH) / 2f;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH);
	}

	private void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(this.font, s, x, y, color, false);
	}

	private void centered(GuiGraphics g, String s, int centerX, int y, int color) {
		g.drawString(this.font, s, centerX - this.font.width(s) / 2, y, color, false);
	}

	private String fit(String s, int maxW) {
		if (maxW <= 0) return "";
		if (this.font.width(s) <= maxW) return s;
		return this.font.plainSubstrByWidth(s, Math.max(0, maxW - this.font.width("…"))) + "…";
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
