package xerca.xercapaint.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import xerca.xercapaint.PaletteUtil;

import java.util.Locale;

/**
 * A Minecraft-styled hex / HSV colour picker, modelled after the "Colour Picker" screen in Axiom
 * (saturation-value square + hue bar + hex input), but standalone: it picks a colour and adds it to
 * the painting palette instead of matching it to a block. Opened as a popup from {@link BasePalette}
 * (the parent palette screen is suspended, not closed, so nothing is saved prematurely).
 */
@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class HexColorPickerScreen extends Screen {
    // Classic raised-panel Minecraft menu colours.
    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF373737;
    private static final int PANEL_BORDER = 0xFF000000;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int MARKER_LIGHT = 0xFFFFFFFF;
    private static final int MARKER_DARK = 0xFF000000;

    // Bar types
    private static final int BAR_HUE = 0;
    private static final int BAR_SAT = 1;
    private static final int BAR_BRI = 2;

    private final BasePalette parent;

    private float hue;
    private float saturation;
    private float brightness;

    private int panelX;
    private int panelY;
    private final int panelW = 184;
    private final int panelH = 200;
    private int sqX;
    private int sqY;
    private final int sqW = 96;
    private final int sqH = 96;
    private final int barW = 14;
    private int barY;
    private int hueX;
    private int satX;
    private int briX;
    private int prevX;
    private int prevY;
    private final int prevW = 48;
    private final int prevH = 16;

    private EditBox hexField;
    private Button addButton;
    private Button cancelButton;
    private boolean syncingFields = false;
    private int dragging = 0; // 0 none, 1 sat/val square, 2 hue bar, 3 sat bar, 4 bri bar

    public HexColorPickerScreen(BasePalette parent) {
        super(Component.translatable("palette.hexPicker.title"));
        this.parent = parent;

        PaletteUtil.Color start = parent.currentColor != null ? parent.currentColor : new PaletteUtil.Color(0xFFB02E26);
        float[] hsb = new float[3];
        rgbToHsb(start.r, start.g, start.b, hsb);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    protected void init() {
        // The canvas editor hides the OS cursor for its brush; make sure it's visible in the picker.
        if (this.minecraft != null) {
            GLFW.glfwSetInputMode(this.minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }

        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        sqX = panelX + 10;
        sqY = panelY + 34;
        barY = sqY;
        hueX = sqX + sqW + 8;
        satX = hueX + barW + 8;
        briX = satX + barW + 8;

        int hexFieldY = sqY + sqH + 8;
        prevX = sqX + 114;
        prevY = hexFieldY - 1;

        hexField = new EditBox(this.font, sqX + 28, hexFieldY, 78, 14, Component.translatable("palette.hexPicker.hex"));
        hexField.setMaxLength(9);
        hexField.setHint(Component.literal("#RRGGBB"));
        hexField.setResponder(this::onHexTyped);
        addRenderableWidget(hexField);

        int buttonsY = panelY + panelH - 26;
        addButton = addRenderableWidget(Button.builder(Component.translatable("palette.hexPicker.add"), b -> {
            parent.addPickedColor(currentColor());
            returnToParent();
        }).bounds(panelX + 10, buttonsY, 108, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> returnToParent())
                .bounds(panelX + 122, buttonsY, 52, 20).build());

        syncHexField();
    }

    private void returnToParent() {
        parent.suppressRemove = false;
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        drawPanel(g, panelX, panelY, panelW, panelH);
        g.drawString(this.font, this.title, panelX + 10, panelY + 8, LABEL_COLOR, false);

        drawSatValSquare(g);

        // Bar labels
        g.drawCenteredString(this.font, "H", hueX + barW / 2, sqY - 12, LABEL_COLOR);
        g.drawCenteredString(this.font, "S", satX + barW / 2, sqY - 12, LABEL_COLOR);
        g.drawCenteredString(this.font, "B", briX + barW / 2, sqY - 12, LABEL_COLOR);

        drawBar(g, hueX, BAR_HUE, hue);
        drawBar(g, satX, BAR_SAT, 1.0f - saturation);
        drawBar(g, briX, BAR_BRI, 1.0f - brightness);

        // Hex label + preview swatch (to the right of the hex field)
        g.drawString(this.font, Component.translatable("palette.hexPicker.hex"), sqX, hexField.getY() + 3, LABEL_COLOR, false);
        g.fill(prevX - 1, prevY - 1, prevX + prevW + 1, prevY + prevH + 1, PANEL_BORDER);
        g.fill(prevX, prevY, prevX + prevW, prevY + prevH, currentColor().rgbVal());

        // Widgets (hex field + buttons) on top of the panel
        hexField.render(g, mouseX, mouseY, partialTick);
        addButton.render(g, mouseX, mouseY, partialTick);
        cancelButton.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        g.fill(x, y, x + w, y + h, PANEL_FILL);
        g.fill(x, y, x + w, y + 1, PANEL_HIGHLIGHT);
        g.fill(x, y, x + 1, y + h, PANEL_HIGHLIGHT);
        g.fill(x + w - 1, y + 1, x + w, y + h, PANEL_SHADOW);
        g.fill(x + 1, y + h - 1, x + w, y + h, PANEL_SHADOW);
    }

    private void drawSatValSquare(GuiGraphics g) {
        g.fill(sqX - 1, sqY - 1, sqX + sqW + 1, sqY + sqH + 1, PANEL_BORDER);
        for (int i = 0; i < sqW; i++) {
            float s = (float) i / (sqW - 1);
            int top = hsbToArgb(hue, s, 1.0f);
            g.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + sqH, top, 0xFF000000);
        }
        int mx = sqX + Math.round(saturation * (sqW - 1));
        int my = sqY + Math.round((1.0f - brightness) * (sqH - 1));
        drawRing(g, mx, my);
    }

    // A vertical H/S/B bar (top = high value). markerFrac is 0 at the top, 1 at the bottom.
    private void drawBar(GuiGraphics g, int x, int type, float markerFrac) {
        g.fill(x - 1, barY - 1, x + barW + 1, barY + sqH + 1, PANEL_BORDER);
        for (int j = 0; j < sqH; j++) {
            float frac = (float) j / (sqH - 1);
            int col = switch (type) {
                case BAR_HUE -> hsbToArgb(frac, 1.0f, 1.0f);
                case BAR_SAT -> hsbToArgb(hue, 1.0f - frac, brightness);
                case BAR_BRI -> hsbToArgb(hue, saturation, 1.0f - frac);
                default -> 0xFF000000;
            };
            g.fill(x, barY + j, x + barW, barY + j + 1, col);
        }
        int my = barY + Math.round(markerFrac * (sqH - 1));
        g.fill(x - 2, my - 1, x + barW + 2, my, MARKER_DARK);
        g.fill(x - 2, my, x + barW + 2, my + 1, MARKER_LIGHT);
        g.fill(x - 2, my + 1, x + barW + 2, my + 2, MARKER_DARK);
    }

    // Small hollow square marker (white with a dark outline) for the sat/val cursor.
    private void drawRing(GuiGraphics g, int cx, int cy) {
        int r = 3;
        g.fill(cx - r - 1, cy - r - 1, cx + r + 2, cy - r, MARKER_DARK);
        g.fill(cx - r - 1, cy + r + 1, cx + r + 2, cy + r + 2, MARKER_DARK);
        g.fill(cx - r - 1, cy - r - 1, cx - r, cy + r + 2, MARKER_DARK);
        g.fill(cx + r + 1, cy - r - 1, cx + r + 2, cy + r + 2, MARKER_DARK);
        g.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, MARKER_LIGHT);
        g.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, MARKER_LIGHT);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, MARKER_LIGHT);
        g.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, MARKER_LIGHT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (inRect(mx, my, sqX, sqY, sqW, sqH)) {
                dragging = 1;
                updateSatVal(mx, my);
                return true;
            }
            if (inRect(mx, my, hueX, barY, barW, sqH)) {
                dragging = 2;
                updateBar(BAR_HUE, my);
                return true;
            }
            if (inRect(mx, my, satX, barY, barW, sqH)) {
                dragging = 3;
                updateBar(BAR_SAT, my);
                return true;
            }
            if (inRect(mx, my, briX, barY, barW, sqH)) {
                dragging = 4;
                updateBar(BAR_BRI, my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        switch (dragging) {
            case 1 -> { updateSatVal(mx, my); return true; }
            case 2 -> { updateBar(BAR_HUE, my); return true; }
            case 3 -> { updateBar(BAR_SAT, my); return true; }
            case 4 -> { updateBar(BAR_BRI, my); return true; }
            default -> { return super.mouseDragged(mx, my, button, dx, dy); }
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        return super.mouseReleased(mx, my, button);
    }

    private void updateSatVal(double mx, double my) {
        saturation = clamp01((float) (mx - sqX) / (sqW - 1));
        brightness = clamp01(1.0f - (float) (my - sqY) / (sqH - 1));
        syncHexField();
    }

    private void updateBar(int type, double my) {
        float frac = clamp01((float) (my - barY) / (sqH - 1));
        switch (type) {
            case BAR_HUE -> hue = frac;
            case BAR_SAT -> saturation = 1.0f - frac;
            case BAR_BRI -> brightness = 1.0f - frac;
            default -> { }
        }
        syncHexField();
    }

    private void onHexTyped(String input) {
        if (syncingFields) {
            return;
        }
        String cleaned = input.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        } else if (cleaned.startsWith("0x")) {
            cleaned = cleaned.substring(2);
        }
        cleaned = cleaned.replaceAll("[^0-9a-f]", "");
        if (cleaned.length() != 3 && cleaned.length() != 6) {
            hexField.setTextColor(0xFFFF5555);
            return;
        }
        if (cleaned.length() == 3) {
            // Expand shorthand (#abc -> #aabbcc)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(cleaned.charAt(i)).append(cleaned.charAt(i));
            }
            cleaned = sb.toString();
        }
        try {
            int rgb = Integer.parseInt(cleaned, 16);
            float[] hsb = new float[3];
            rgbToHsb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsb);
            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];
            hexField.setTextColor(0xFFFFFFFF);
        } catch (NumberFormatException e) {
            hexField.setTextColor(0xFFFF5555);
        }
    }

    private void syncHexField() {
        if (hexField == null) {
            return;
        }
        syncingFields = true;
        PaletteUtil.Color c = currentColor();
        hexField.setValue(String.format("#%02X%02X%02X", c.r, c.g, c.b));
        hexField.setTextColor(0xFFFFFFFF);
        syncingFields = false;
    }

    private PaletteUtil.Color currentColor() {
        float[] rgb = new float[3];
        hsbToRgb(hue, saturation, brightness, rgb);
        return new PaletteUtil.Color(Math.round(rgb[0]), Math.round(rgb[1]), Math.round(rgb[2]));
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static int hsbToArgb(float h, float s, float b) {
        float[] rgb = new float[3];
        hsbToRgb(h, s, b, rgb);
        return 0xFF000000 | (Math.round(rgb[0]) << 16) | (Math.round(rgb[1]) << 8) | Math.round(rgb[2]);
    }

    // Standard HSB<->RGB (matches java.awt.Color / Axiom's ColourUtils), output 0..255 floats.
    private static void hsbToRgb(float hue, float saturation, float brightness, float[] rgb) {
        float r = 0, g = 0, b = 0;
        if (saturation == 0.0f) {
            r = g = b = brightness * 255.0f;
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - saturation * (1.0f - f));
            switch ((int) h) {
                case 0 -> { r = brightness * 255.0f; g = t * 255.0f; b = p * 255.0f; }
                case 1 -> { r = q * 255.0f; g = brightness * 255.0f; b = p * 255.0f; }
                case 2 -> { r = p * 255.0f; g = brightness * 255.0f; b = t * 255.0f; }
                case 3 -> { r = p * 255.0f; g = q * 255.0f; b = brightness * 255.0f; }
                case 4 -> { r = t * 255.0f; g = p * 255.0f; b = brightness * 255.0f; }
                case 5 -> { r = brightness * 255.0f; g = p * 255.0f; b = q * 255.0f; }
                default -> { }
            }
        }
        rgb[0] = r;
        rgb[1] = g;
        rgb[2] = b;
    }

    private static void rgbToHsb(int red, int green, int blue, float[] hsb) {
        int cmax = Math.max(Math.max(red, green), blue);
        int cmin = Math.min(Math.min(red, green), blue);
        float brightness = cmax / 255.0f;
        float saturation = cmax != 0 ? (float) (cmax - cmin) / cmax : 0.0f;
        float hue;
        if (saturation == 0.0f) {
            hue = 0.0f;
        } else {
            float redc = (float) (cmax - red) / (cmax - cmin);
            float greenc = (float) (cmax - green) / (cmax - cmin);
            float bluec = (float) (cmax - blue) / (cmax - cmin);
            if (red == cmax) {
                hue = bluec - greenc;
            } else if (green == cmax) {
                hue = 2.0f + redc - bluec;
            } else {
                hue = 4.0f + greenc - redc;
            }
            hue /= 6.0f;
            if (hue < 0) {
                hue += 1.0f;
            }
        }
        hsb[0] = hue;
        hsb[1] = saturation;
        hsb[2] = brightness;
    }
}
