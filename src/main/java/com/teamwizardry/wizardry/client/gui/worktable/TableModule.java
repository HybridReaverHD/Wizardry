package com.teamwizardry.wizardry.client.gui.worktable;

import com.teamwizardry.librarianlib.client.gui.EnumMouseButton;
import com.teamwizardry.librarianlib.client.gui.GuiComponent;
import com.teamwizardry.librarianlib.client.gui.components.ComponentSprite;
import com.teamwizardry.librarianlib.client.gui.mixin.ButtonMixin;
import com.teamwizardry.librarianlib.client.gui.mixin.DragMixin;
import com.teamwizardry.librarianlib.common.util.math.Vec2d;
import com.teamwizardry.wizardry.api.spell.Module;
import com.teamwizardry.wizardry.lib.LibSprites;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

public class TableModule {

	public ComponentSprite component;

	public TableModule(WorktableGui table, Module module, boolean draggable) {
		ComponentSprite sprite = new ComponentSprite(LibSprites.Worktable.MODULE_DEFAULT, 0, 0, 12, 12);
		if (draggable) sprite.addTag("draggable");

		ComponentSprite glow = new ComponentSprite(LibSprites.Worktable.MODULE_DEFAULT_GLOW, 0, 0, 12, 12);
		glow.setVisible(false);
		sprite.add(glow);

		// TODO: The thing's icon here
		ComponentSprite icon = new ComponentSprite(LibSprites.Worktable.MODULE_DEFAULT, 2, 2, 8, 8);
		sprite.add(icon);

		new ButtonMixin<>(sprite, () -> {
		});
		sprite.BUS.hook(GuiComponent.MouseInEvent.class, (event) -> {
			glow.setVisible(true);
		});

		sprite.BUS.hook(GuiComponent.MouseOutEvent.class, (event) -> {
			glow.setVisible(false);
		});

		sprite.BUS.hook(GuiComponent.MouseDownEvent.class, (event) -> {
			if (event.getButton() == EnumMouseButton.LEFT) {
				if (!draggable) {
					TableModule item = new TableModule(table, module, true);
					item.component.addTag("selected");
					item.component.setPos(new Vec2d(50, 50));
					List<GuiComponent<?>> selected = table.paper.getByTag("selected");
					if (selected.isEmpty()) table.paper.add(item.component);
					else {
						for (GuiComponent<?> comp : selected) {
							if (comp.equals(item.component))
								return;
						}
						table.paper.removeByTag("selected");
						table.paper.add(item.component);
					}

					DragMixin drag = new DragMixin<>(item.component, vec2d -> vec2d);
					drag.setMouseDown(event.getButton());
					event.cancel();
				}
			}
		});

		sprite.BUS.hook(GuiComponent.PostDrawEvent.class, (event) -> {
			if (event.getComponent().getMouseOver()) {
				List<String> txt = new ArrayList<>();
				txt.add(TextFormatting.GOLD + module.getReadableName());
				event.getComponent().setTooltip(txt);
			}
		});

		component = sprite;
	}
}
