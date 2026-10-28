package de.gaucho.killsounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Flacher Button ohne Minecraft-Standardgrafik. Wie er aussieht, bestimmt der Painter –
 * so passt alles zum gewaehlten Theme.
 */
public class UiButton extends AbstractButton {
	@FunctionalInterface
	public interface Painter {
		void paint(GuiGraphics g, UiButton b, boolean hovered);
	}

	private final Painter painter;
	private final Runnable action;

	public UiButton(int x, int y, int w, int h, String narration, Painter painter, Runnable action) {
		super(x, y, w, h, Component.literal(narration));
		this.painter = painter;
		this.action = action;
	}

	public UiButton tooltip(String text) {
		if (text != null && !text.isEmpty()) this.setTooltip(Tooltip.create(Component.literal(text)));
		return this;
	}

	@Override
	protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
		painter.paint(g, this, this.active && this.isHovered());
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
