package de.gaucho.killsounds;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Erkennt Kills rein auf deinem Client (funktioniert auf jedem Server):
 *  - Spieler, die du geschlagen hast,
 *  - Spieler in der Naehe eines End-Kristalls, den du zerschlaegst,
 *  - Spieler in der Naehe eines Seelenankers, den du benutzt.
 * Stirbt so ein Spieler in den naechsten Sekunden, wird dein Kill-Sound gespielt.
 * Zusaetzlich wird die Todesnachricht im Chat geprueft (falls der Server Leben versteckt).
 */
public final class KillTracker {
	private static final long TAG_DURATION_MS = 7000;
	private static final double EXPLOSION_RADIUS = 12.0;

	private record Tag(int entityId, String name, long expiresAt) {}

	private final Map<Integer, Tag> tags = new HashMap<>();
	private final Map<String, Long> recentKills = new HashMap<>();

	public void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) onAttack(entity);
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockPos pos = hitResult.getBlockPos();
				if (level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
					tagNearby(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				}
			}
			return InteractionResult.PASS;
		});
		// Nur System-Nachrichten (dort stehen Todesnachrichten), nicht der Spieler-Chat
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onChat(message);
		});
	}

	private void onAttack(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity instanceof Player p && p != mc.player) {
			tag(p);
		} else if (entity.getType() == EntityType.END_CRYSTAL) {
			tagNearby(entity.getX(), entity.getY(), entity.getZ());
		}
	}

	private void tagNearby(double x, double y, double z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		for (Player p : mc.level.players()) {
			if (p != mc.player && p.distanceToSqr(x, y, z) <= EXPLOSION_RADIUS * EXPLOSION_RADIUS) {
				tag(p);
			}
		}
	}

	private void tag(Player p) {
		tags.put(p.getId(), new Tag(p.getId(), p.getName().getString(), System.currentTimeMillis() + TAG_DURATION_MS));
	}

	/** Jeden Client-Tick aufrufen. */
	public void tick(Minecraft mc) {
		if (tags.isEmpty()) return;
		if (mc.level == null) {
			tags.clear();
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Tag> it = tags.values().iterator();
		while (it.hasNext()) {
			Tag t = it.next();
			if (now > t.expiresAt()) {
				it.remove();
				continue;
			}
			Entity e = mc.level.getEntity(t.entityId());
			if (e instanceof LivingEntity living && living.isDeadOrDying()) {
				it.remove();
				onKill(t.name());
			}
		}
	}

	private void onChat(Component message) {
		if (tags.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String text = message.getString();
		String me = mc.player.getName().getString();
		if (!text.contains(me)) return;
		long now = System.currentTimeMillis();
		Iterator<Tag> it = tags.values().iterator();
		while (it.hasNext()) {
			Tag t = it.next();
			if (now <= t.expiresAt() && text.contains(t.name())) {
				it.remove();
				onKill(t.name());
			}
		}
	}

	private void onKill(String victim) {
		long now = System.currentTimeMillis();
		Long last = recentKills.get(victim);
		if (last != null && now - last < 3000) return; // nicht doppelt
		recentKills.put(victim, now);
		recentKills.values().removeIf(time -> now - time > 10000);
		playKillSound();
	}

	public static void playKillSound() {
		KillSoundsConfig cfg = GauchosKillSounds.config();
		SoundRepository.KillSound sound = GauchosKillSounds.repository().snapshot().findSound(cfg.killSound);
		if (sound != null) playSound(sound, cfg.killSoundVolume);
	}

	public static void playSound(SoundRepository.KillSound sound, float volume) {
		Identifier id = Identifier.fromNamespaceAndPath(PackBuilder.NAMESPACE, PackBuilder.soundEventPath(sound.index));
		Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0f, volume));
	}
}
