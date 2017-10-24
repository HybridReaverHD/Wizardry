package com.teamwizardry.wizardry.client.gui.book;

import com.teamwizardry.librarianlib.features.gui.EnumMouseButton;
import com.teamwizardry.librarianlib.features.gui.component.GuiComponent;
import com.teamwizardry.librarianlib.features.gui.component.GuiComponentEvents;
import com.teamwizardry.librarianlib.features.math.Vec2d;
import com.teamwizardry.librarianlib.features.utilities.client.ScissorUtil;
import com.teamwizardry.wizardry.api.block.IStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class ComponentStructure extends GuiComponent {

	private boolean dragging = false;
	private Vec2d prevPos = Vec2d.ZERO;
	private Vec2d panVec = Vec2d.ZERO;
	private Vec2d rotVec = Vec2d.ZERO;
	private double zoom = 0;

	public ComponentStructure(BookGui bookGui, int x, int y, int width, int height, IStructure structure) {
		super(x, y, width, height);

		ComponentStructureList list = new ComponentStructureList(structure.getStructure());
		ComponentBookmark bookmark = new ComponentBookmark(new Vec2d(-getSize().getXi() - 35, 0), bookGui, this, bookGui.bookmarkIndex, list, "Materials", false);
		add(bookmark);

		BUS.hook(GuiComponentEvents.MouseWheelEvent.class, event -> {
			if (event.getDirection() == GuiComponentEvents.MouseWheelDirection.UP) zoom += 1;
			else zoom -= 1;
			zoom = MathHelper.clamp(zoom, 0, 20);
		});

		BUS.hook(GuiComponentEvents.MouseDragEvent.class, event -> {
			Vec2d untransform = event.component.thisPosToOtherContext(event.component, event.getMousePos());
			Vec2d diff;
			if (dragging) diff = untransform.sub(prevPos).mul(1 / 5.0);
			else diff = event.getMousePos().mul(1 / 100.0);

			if (event.getButton() == EnumMouseButton.RIGHT) rotVec = rotVec.add(diff);
			else if (event.getButton() == EnumMouseButton.LEFT) panVec = panVec.add(diff.mul(2));

			prevPos = untransform;
			dragging = true;
		});

		BUS.hook(GuiComponentEvents.MouseUpEvent.class, event -> {
			prevPos = Vec2d.ZERO;
			dragging = false;
		});


		BUS.hook(GuiComponentEvents.PostDrawEvent.class, event -> {
			Vec2d root = event.component.thisPosToOtherContext(null);
			Vec2d size = event.component.thisPosToOtherContext(null, event.component.getSize());
			
			ScissorUtil.push();
			ScissorUtil.set(root.getXi(), root.getYi(), size.getXi(), size.getYi());
			ScissorUtil.enable();

			GlStateManager.pushMatrix();

			GlStateManager.enableAlpha();
			GlStateManager.enableBlend();
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			GlStateManager.enableCull();
			GlStateManager.enableRescaleNormal();

			GlStateManager.translate(width / 2.0, (height / 2.0), 500);

			GlStateManager.translate(panVec.getX(), panVec.getY(), 0);
			GlStateManager.rotate((float) (35 + rotVec.getY()), -1, 0, 0);
			GlStateManager.rotate((float) ((45 + rotVec.getX())), 0, 1, 0);
			GlStateManager.scale(16 + zoom, -16 - zoom, 16 + zoom);
			GlStateManager.translate(-structure.offsetToCenter().getX(), -structure.offsetToCenter().getY(), -structure.offsetToCenter().getZ());
			GlStateManager.translate(-0.5, -0.5, -0.5);

			Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
			structure.getStructure().draw();

			GlStateManager.popMatrix();
			
			ScissorUtil.pop();
		});
	}

	@Override
	public void drawComponent(Vec2d mousePos, float partialTicks) {

	}
}