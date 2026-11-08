package net.example.replaynv.client;

import net.example.replaynv.ReplayEnvironment;
import net.example.replaynv.ReplayEnvironmentOptions;
import net.example.replaynv.ReplayState;
import net.example.replaynv.XPizzaXReplay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import xpizzax.unified.ui.ForegroundScreen;
import xpizzax.unified.ui.UiTheme;

import java.util.Locale;

/** Non-pausing, keyboard-accessible controls, with an unobscured world preview. */
public final class ReplayEnvironmentScreen extends ForegroundScreen {
    private static final int PANEL_HEIGHT = 232;
    private static final int[] PRESETS = {-1, 23000, 1000, 6000, 12000, 18000};
    private static final String[] PRESET_NAMES = {"recorded", "dawn", "morning", "day", "evening", "night"};
    private final ReplayControls.ReturnTarget returnTarget;
    private final ButtonWidget[] timeButtons = new ButtonWidget[PRESETS.length];
    private final ButtonWidget[] weatherButtons = new ButtonWidget[4];
    private int left, top, panelWidth;
    private TextFieldWidget timeInput;
    private ButtonWidget applyButton, lightButton;
    private TimeSlider timeSlider;
    private PrecipitationSlider precipitationSlider;
    private RainOpacitySlider rainOpacitySlider;
    private boolean synchronizing;

    public ReplayEnvironmentScreen(Screen parent) {
        super(Text.literal("WEATHER CONTROL"));
        this.returnTarget = new ReplayControls.ReturnTarget(parent);
    }
    @Override public boolean shouldPause() { return false; }

    @Override protected void init() {
        panelWidth = Math.min(400, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - PANEL_HEIGHT) / 2);
        int inner = panelWidth - 24;
        int gap = 4;
        int presetWidth = (inner - gap * 5) / 6;
        for (int i = 0; i < PRESETS.length; i++) {
            final int ticks = PRESETS[i];
            timeButtons[i] = addDrawableChild(xpizzax.unified.ui.RpmButton.builder(
                    label("time." + PRESET_NAMES[i]), button -> {
                        var current = ReplayEnvironment.options();
                        change(ticks < 0 ? current.withRecordedTime() : current.withTime(ticks), true);
                    }).dimensions(left + 12 + i * (presetWidth + gap), top + 54, presetWidth, 18).build());
        }
        timeSlider = addDrawableChild(new TimeSlider(left + 12, top + 77, inner));
        timeInput = addDrawableChild(new TextFieldWidget(textRenderer,
                left + 102, top + 102, inner - 164, 18, label("time.input")));
        timeInput.setMaxLength(12);timeInput.setEditableColor(UiTheme.TEXT);timeInput.setUneditableColor(UiTheme.MUTED);
        timeInput.setChangedListener(value -> {
            if (!synchronizing) updateApplyButton();
        });
        applyButton = addDrawableChild(xpizzax.unified.ui.RpmButton.builder(label("apply"), button -> applyInput())
                .dimensions(left + panelWidth - 74, top + 101, 62, 20).build());

        var weatherValues = ReplayEnvironmentOptions.Weather.values();
        int weatherWidth = (inner - gap * 3) / 4;
        for (int i = 0; i < weatherValues.length; i++) {
            final var weather = weatherValues[i];
            weatherButtons[i] = addDrawableChild(xpizzax.unified.ui.RpmButton.builder(weatherLabel(weather), button ->
                    change(ReplayEnvironment.options().withWeather(weather), false))
                    .dimensions(left + 12 + i * (weatherWidth + gap), top + 145, weatherWidth, 18).build());
        }
        precipitationSlider = addDrawableChild(new PrecipitationSlider(left + 12, top + 167, inner));
        rainOpacitySlider = addDrawableChild(new RainOpacitySlider(left + 12, top + 186, inner));
        int footerWidth = (inner - gap * 2) / 3;
        addDrawableChild(xpizzax.unified.ui.RpmButton.builder(label("reset"), button ->
                change(ReplayEnvironmentOptions.RECORDED, true))
                .dimensions(left + 12, top + 204, footerWidth, 20).build());
        lightButton = addDrawableChild(xpizzax.unified.ui.RpmButton.builder(lightLabel(), button -> {
            XPizzaXReplay.toggleNightVision();
            lightButton.setMessage(lightLabel());
        }).dimensions(left + 12 + footerWidth + gap, top + 204, footerWidth, 20).build());
        addDrawableChild(xpizzax.unified.ui.RpmButton.builder(label("done"), button -> close())
                .dimensions(left + 12 + (footerWidth + gap) * 2, top + 204, footerWidth, 20).build());
        synchronize(true);
    }

    private void change(ReplayEnvironmentOptions options, boolean resetInput) {
        ReplayEnvironment.setOptions(options);
        synchronize(resetInput);
    }
    private int selectedTicks() {
        var options = ReplayEnvironment.options();
        return !options.fixedTime() && client != null && client.world != null
                ? (int) Math.floorMod(client.world.getTimeOfDay(), 24000) : options.timeTicks();
    }
    private void synchronize(boolean resetInput) {
        synchronizing = true;
        var options = ReplayEnvironment.options();
        for (int i = 0; i < PRESETS.length; i++) {
            boolean selected = PRESETS[i] < 0 ? !options.fixedTime()
                    : options.fixedTime() && options.timeTicks() == PRESETS[i];
            ((xpizzax.unified.ui.RpmButton)timeButtons[i]).selected(selected);
            timeButtons[i].setMessage(label("time." + PRESET_NAMES[i])
                    .copy().formatted(selected ? Formatting.AQUA : Formatting.WHITE));
        }
        for (var weather : ReplayEnvironmentOptions.Weather.values()) {
            ((xpizzax.unified.ui.RpmButton)weatherButtons[weather.ordinal()]).selected(options.weather()==weather);
            weatherButtons[weather.ordinal()].setMessage(weatherLabel(weather).copy()
                    .formatted(options.weather() == weather ? Formatting.AQUA : Formatting.WHITE));
        }
        timeSlider.setTicks(selectedTicks());
        precipitationSlider.setAmount(options.precipitation());
        precipitationSlider.active = options.weather() == ReplayEnvironmentOptions.Weather.RAIN
                || options.weather() == ReplayEnvironmentOptions.Weather.THUNDER;
        rainOpacitySlider.setAmount(options.rainOpacity());
        rainOpacitySlider.active = options.weather() != ReplayEnvironmentOptions.Weather.CLEAR;
        if (resetInput) timeInput.setText(Integer.toString(selectedTicks()));
        synchronizing = false;
        updateApplyButton();
    }
    private void updateApplyButton() {
        if (applyButton != null) applyButton.active = ReplayEnvironmentOptions.parseTime(timeInput.getText()).isPresent();
    }
    private void applyInput() {
        var ticks = ReplayEnvironmentOptions.parseTime(timeInput.getText());
        if (ticks.isPresent()) change(ReplayEnvironment.options().withTime(ticks.getAsInt()), true);
    }

    @Override protected void renderForeground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No full-screen blur: selected lighting/weather stays visible around the panel.
        UiTheme.backdrop(context, width, height);
        UiTheme.panel(context, left, top, panelWidth, PANEL_HEIGHT);
        UiTheme.logo(context,left+12,top+7,52);
        UiTheme.brandText(context,textRenderer,"WEATHER CONTROL",left+76,top+16);
        draw(context, label("time.title"), 12, 42, UiTheme.accent());
        String clock = ReplayEnvironmentOptions.clockTime(client != null && client.world != null
                ? client.world.getTimeOfDay() : ReplayEnvironment.options().timeTicks());
        context.drawText(textRenderer, clock, left + panelWidth - 12 - textRenderer.getWidth(clock),
                top + 42, UiTheme.TEXT, false);
        draw(context, label("time.input"), 12, 107, UiTheme.MUTED);
        boolean invalid = timeInput != null && !timeInput.getText().isBlank()
                && ReplayEnvironmentOptions.parseTime(timeInput.getText()).isEmpty();
        draw(context, label(invalid ? "time.invalid" : "time.format"), 12, 123,
                invalid ? 0xFFFF9696 : UiTheme.MUTED);
        draw(context, label("weather.title"), 12, 134, UiTheme.accent());
        renderWidgets(context, mouseX, mouseY, delta);
    }
    private void draw(DrawContext context, Text text, int x, int y, int color) {
        context.drawText(textRenderer, textRenderer.trimToWidth(text.getString(), panelWidth - x - 12),
                left + x, top + y, color, false);
    }
    private static Text label(String suffix) {
        return Text.translatable("replay_nv.environment." + suffix);
    }
    private static Text weatherLabel(ReplayEnvironmentOptions.Weather weather) {
        return label("weather." + weather.name().toLowerCase(Locale.ROOT));
    }
    private static Text lightLabel() {
        return label(XPizzaXReplay.isNightVisionEnabled ? "light.on" : "light.off");
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && timeInput.isFocused()) {
            applyInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void tick() {
        if (!ReplayState.isReplayOpen()) close();
    }
    @Override public void close() {
        if (client != null) returnTarget.restore(client);
    }
    @Override public void removed() {
        if (!ReplayEnvironment.savePending() && client != null && client.player != null)
            client.player.sendMessage(label("save.failed"), false);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean handled = super.mouseReleased(x, y, button);
        ReplayEnvironment.savePending();
        return handled;
    }

    private final class TimeSlider extends xpizzax.unified.ui.RpmSlider {
        TimeSlider(int x, int y, int width) {
            super(x, y, width, 18, Text.empty(), 0);
        }
        int ticks() { return (int) Math.round(value * 23999); }
        void setTicks(int ticks) { value = ticks / 23999.0; updateMessage(); }
        @Override protected void updateMessage() {
            setMessage(Text.translatable("replay_nv.environment.time.slider",
                    ReplayEnvironmentOptions.clockTime(ticks()), ticks()));
        }
        @Override protected void applyValue() {
            if (!synchronizing) change(ReplayEnvironment.options().withTime(ticks()), true);
        }
    }
    private final class PrecipitationSlider extends xpizzax.unified.ui.RpmSlider {
        PrecipitationSlider(int x, int y, int width) {
            super(x, y, width, 16, Text.empty(), 1);
        }
        void setAmount(float amount) { value = amount; updateMessage(); }
        @Override protected void updateMessage() {
            setMessage(Text.translatable("replay_nv.environment.weather.amount", Math.round(value * 100)));
        }
        @Override protected void applyValue() {
            if (!synchronizing) change(ReplayEnvironment.options()
                    .withPrecipitation((float) Math.round(value * 100) / 100F), false);
        }
    }
    private final class RainOpacitySlider extends xpizzax.unified.ui.RpmSlider {
        RainOpacitySlider(int x, int y, int width) {
            super(x, y, width, 16, Text.empty(), ReplayEnvironmentOptions.DEFAULT_RAIN_OPACITY);
        }
        void setAmount(float amount) { value = amount; updateMessage(); }
        @Override protected void updateMessage() {
            setMessage(Text.translatable("replay_nv.environment.weather.opacity", Math.round(value * 100)));
        }
        @Override protected void applyValue() {
            if (!synchronizing) change(ReplayEnvironment.options()
                    .withRainOpacity((float) Math.round(value * 100) / 100F), false);
        }
    }
}
