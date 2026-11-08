package xpizzax.unified.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL11;

/** One foreground pass for mod screens, without inheriting tint/depth from inventory or HUD. */
public abstract class ForegroundScreen extends Screen {
    protected ForegroundScreen(Text title) { super(title); }

    @Override
    public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.draw();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        context.getMatrices().push();
        try {
            renderForeground(context, mouseX, mouseY, delta);
            context.draw();
        } finally {
            context.getMatrices().pop();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }
    }

    protected abstract void renderForeground(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    public final void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Our screens draw their backdrop before the panel. In Minecraft 1.20.4
        // Screen.render draws this background again when rendering widgets;
        // that second pass would cover the panel or darken it in a world.
    }

    protected final void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
