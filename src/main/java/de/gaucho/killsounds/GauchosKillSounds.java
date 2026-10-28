package de.gaucho.killsounds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class GauchosKillSounds implements ClientModInitializer {
	public static final String MOD_ID = "gauchos_killsounds";
	public static final Logger LOGGER = LoggerFactory.getLogger("GauchosKillSounds");

	private static KillSoundsConfig config;
	private static SoundRepository repository;
	private static final KillTracker KILL_TRACKER = new KillTracker();
	private static KeyMapping openKey;
	/** Wird hochgezaehlt, wenn neue Sounds/Hintergruende da sind (Menue baut sich dann neu auf). */
	private static volatile int dataVersion = 0;

	@Override
	public void onInitializeClient() {
		PackBuilder.cleanupOldVersion();
		config = KillSoundsConfig.load();
		repository = new SoundRepository();
		KILL_TRACKER.register();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.gauchos_killsounds.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.screen == null) mc.setScreen(new KillSoundsScreen(null));
			}
			KILL_TRACKER.tick(mc);
		});

		// Beim Spielstart Sounds + Hintergruende von GitHub laden
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> reload());
	}

	public static KillSoundsConfig config() {
		return config;
	}

	public static SoundRepository repository() {
		return repository;
	}

	public static int dataVersion() {
		return dataVersion;
	}

	/** Holt den neuesten Stand von GitHub und macht ihn im Spiel verfuegbar. */
	public static void reload() {
		CompletableFuture<SoundRepository.Snapshot> f = repository.refresh();
		if (f == null) return; // laeuft schon
		Minecraft mc = Minecraft.getInstance();
		Executor renderThread = mc::execute;
		f.thenApply(snap -> {
			try {
				return PackBuilder.build(snap, repository);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}).thenAcceptAsync(changed -> {
			PackBuilder.apply(mc, changed);
			dataVersion++;
		}, renderThread).exceptionally(e -> {
			LOGGER.error("Sounds konnten nicht eingebunden werden", e);
			return null;
		});
	}
}
