package xerca.xercapaint.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import xerca.xercapaint.CanvasType;
import xerca.xercapaint.PaletteUtil;
import xerca.xercapaint.SoundEvents;
import xerca.xercapaint.entity.EntityEasel;
import xerca.xercapaint.item.ItemCanvas;
import xerca.xercapaint.item.Items;
import xerca.xercapaint.packets.CanvasMiniUpdatePacket;
import xerca.xercapaint.packets.CanvasUpdatePacket;
import xerca.xercapaint.packets.EaselLeftPacket;
import xerca.xercapaint.packets.PaletteUpdatePacket;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class GuiCanvasEdit extends BasePalette {
    private static final int BRUSH_LEVEL_COUNT = 4;
    // Selectable brush sizes (scroll wheel / brush meter). Opacity still has BRUSH_LEVEL_COUNT levels.
    private static final int BRUSH_SIZE_COUNT = 6;
    private static final int SMALL_CANVAS_PIXEL_SCALE = 10;
    private static final int MAX_TITLE_LENGTH = 16;
    private static final int MAX_EASEL_DISTANCE_SQR = 64;
    private static final int MINI_UPDATE_INTERVAL_TICKS = 10;
    private static final int POSITION_UNSET = -1000;

    private double canvasX;
    private double canvasY;
    // One slot per canvas type (indexed by CanvasType.toByte()).
    private static final double[] CANVAS_XS = {-1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000};
    private static final double[] CANVAS_YS = {-1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000};
    private int canvasWidth;
    private int canvasHeight;
    private int brushMeterX;
    private int brushMeterY;
    private int brushOpacityMeterX;
    private int brushOpacityMeterY;
    private int canvasPixelScale;
    private final int baseCanvasPixelScale;
    private int canvasZoom;
    private final int canvasPixelWidth;
    private final int canvasPixelHeight;
    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 6;
    private static final int ZOOM_BTN_SIZE = 14;
    private static final int BUCKET_SIZE = 16;
    private boolean isFilling = false;
    private int brushSize = 0;
    private boolean touchedCanvas = false;
    private boolean undoStarted = false;
    private boolean gettingSigned;
    private boolean isCarryingCanvas;
    private boolean freshOpen = true;
    private boolean middlePanning = false;
    private boolean spacePanning = false;
    private boolean ctrlTapPending = false;
    private static final long PAN_PAINT_GRACE_MS = 200;
    private long paintGraceUntil = 0;
    private Button buttonSign;
    private Button buttonCancel;
    private Button buttonFinalize;
    private Button buttonCenter;
    private Button buttonGrid;
    private int updateCount;
    private BrushSound brushSound = null;
    private static final int CANVAS_HOLDER_HEIGHT = 10;
    private int brushOpacitySetting = 0;
    private static final float[] BRUSH_OPACITIES = {1.f, 0.75f, 0.5f, 0.25f};
    private static boolean showHelp = false;
    // Purely a visual painting aid: draws a black grid around each pixel. Never written to the
    // saved pixel data, so it doesn't appear on the finished painting.
    private boolean showGrid = false;
    private final Set<Integer> draggedPoints = new HashSet<>();

    private final Player editingPlayer;

    private final CanvasType canvasType;
    private boolean isSigned = false;
    private int[] pixels;
    private String canvasTitle = "";
    private final String canvasId;
    private int version = 0;
    private final EntityEasel easel;
    private int timeSinceLastUpdate = 0;
    private boolean skippedUpdate = false;

    private static final Vec2[] OUTLINE_POSS_1 = {
            new Vec2(0.f, 199.0f),
            new Vec2(12.f, 199.0f),
            new Vec2(34.f, 199.0f),
            new Vec2(76.f, 199.0f),
    };

    private static final Vec2[] OUTLINE_POSS_2 = {
            new Vec2(128.f, 199.0f),
            new Vec2(135.f, 199.0f),
            new Vec2(147.f, 199.0f),
            new Vec2(169.f, 199.0f),
    };

    // Brush pixel shapes (offsets from the anchor pixel), matching setPixelsAt. Used to draw a brush
    // outline that follows the real (rounded) shape when zoomed in.
    private static final int[][] BRUSH_SHAPE_0 = {{0, 0}};
    private static final int[][] BRUSH_SHAPE_1 = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
    private static final int[][] BRUSH_SHAPE_2 = {{-1, 1}, {0, 1}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-1, -2}, {0, -2}};
    private static final int[][] BRUSH_SHAPE_3 = {{-1, 2}, {0, 2}, {1, 2}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {-1, -2}, {0, -2}, {1, -2}};
    // 6-wide rounded dab (even width, so half-anchored like sizes 1 and 2).
    private static final int[][] BRUSH_SHAPE_4 = {{-1, -3}, {0, -3}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {-3, -1}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {-1, 2}, {0, 2}};
    // 7-wide rounded dab (odd width, exact-anchored like sizes 0 and 3).
    private static final int[][] BRUSH_SHAPE_5 = {{-1, -3}, {0, -3}, {1, -3}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {-3, -1}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {3, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0}, {3, 0}, {-3, 1}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {2, 2}, {-1, 3}, {0, 3}, {1, 3}};
    private static final int[][][] BRUSH_SHAPES = {BRUSH_SHAPE_0, BRUSH_SHAPE_1, BRUSH_SHAPE_2, BRUSH_SHAPE_3, BRUSH_SHAPE_4, BRUSH_SHAPE_5};
    private static final boolean[] BRUSH_HALF = {false, true, true, false, true, false};

    private static final int MAX_UNDO_LENGTH = 16;
    private final Deque<int[]> undoStack = new ArrayDeque<>(MAX_UNDO_LENGTH);

    protected GuiCanvasEdit(Player player, ItemStack canvasStack, ItemStack paletteStack, Component title, CanvasType canvasType, EntityEasel easel) {
        super(title, paletteStack);
        updateCount = 0;

        this.canvasType = canvasType;
        this.baseCanvasPixelScale = canvasType == CanvasType.SMALL ? SMALL_CANVAS_PIXEL_SCALE : 5;
        this.canvasZoom = 1;
        this.canvasPixelScale = this.baseCanvasPixelScale;
        this.canvasPixelWidth = CanvasType.getWidth(canvasType);
        this.canvasPixelHeight = CanvasType.getHeight(canvasType);
        int canvasPixelArea = canvasPixelHeight * canvasPixelWidth;
        this.canvasWidth = this.canvasPixelWidth * this.canvasPixelScale;
        this.canvasHeight = this.canvasPixelHeight * this.canvasPixelScale;
        this.easel = easel;

        this.editingPlayer = player;
        List<Integer> stackPixels = canvasStack.get(Items.CANVAS_PIXELS);
        String stackCanvasId = canvasStack.get(Items.CANVAS_ID);
        if (stackPixels != null && stackCanvasId != null) {
            this.pixels = stackPixels.stream().mapToInt(i -> i).toArray();
            this.canvasId = stackCanvasId;
            this.version = canvasStack.getOrDefault(Items.CANVAS_VERSION, 1);

            canvasTitle = canvasStack.getOrDefault(Items.CANVAS_TITLE, "");
            isSigned = !canvasTitle.isEmpty();
        } else {
            this.pixels = new int[canvasPixelArea];
            Arrays.fill(this.pixels, BASIC_COLORS[15].rgbVal());

            this.canvasId = ItemCanvas.generateName(player);
        }
    }

    @Override
    public void init() {
        if (minecraft == null) {
            return;
        }
        int typeIndex = canvasType.toByte();
        if (freshOpen) {
            // Always start centered when first opening the canvas to paint.
            resetPositions();
            freshOpen = false;
        } else {
            // Re-init mid-session (window resize, returning from the hex picker): keep the position.
            canvasX = CANVAS_XS[typeIndex];
            canvasY = CANVAS_YS[typeIndex];
            paletteX = PALETTE_XS[typeIndex];
            paletteY = PALETTE_YS[typeIndex];
            if (canvasX == POSITION_UNSET || canvasY == POSITION_UNSET || paletteX == POSITION_UNSET || paletteY == POSITION_UNSET) {
                resetPositions();
            }
        }

        updateCanvasPos(0, 0);
        updatePalettePos(0, 0);

        Window window = minecraft.getWindow();

        // Hide mouse cursor
        GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);

        int x = window.getGuiScaledWidth() - 120;
        int y = window.getGuiScaledHeight() - 30;
        this.buttonSign = this.addRenderableWidget(Button.builder(Component.translatable("canvas.signButton"), button -> {
            if (!isSigned) {
                gettingSigned = true;
                applyZoom(1);
                resetPositions();
                updateButtons();

                GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }
        }).bounds(x, y, 98, 20).build());
        // Re-centre the canvas + palette (keeps the current zoom). Fixed screen position, left of "Sign".
        this.buttonCenter = this.addRenderableWidget(Button.builder(Component.translatable("canvas.centerButton"), button -> {
            resetPositions();
            updateCanvasPos(0, 0);
            updatePalettePos(0, 0);
        }).bounds(x - 64, y, 60, 20).build());
        // Toggle the pixel grid overlay (visual guide only). Fixed screen position, left of "Center".
        this.buttonGrid = this.addRenderableWidget(Button.builder(Component.translatable("canvas.gridButton"), button -> {
            showGrid = !showGrid;
        }).bounds(x - 128, y, 60, 20).build());
        this.buttonGrid.setTooltip(Tooltip.create(Component.translatable("canvas.gridButton.tooltip")));
        this.buttonFinalize = this.addRenderableWidget(Button.builder(Component.translatable("canvas.finalizeButton"), button -> {
            if (!isSigned) {
                canvasDirty = true;
                isSigned = true;
                if (minecraft != null) {
                    minecraft.setScreen(null);
                }
            }

        }).bounds((int) canvasX - 100, 100, 98, 20).build());
        this.buttonCancel = this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {
            if (!isSigned) {
                gettingSigned = false;
                updateButtons();

                GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
            }
        }).bounds((int) canvasX - 100, 130, 98, 20).build());

        x = (int) (window.getGuiScaledWidth() * 0.95) - 21;
        y = (int) (window.getGuiScaledHeight() * 0.05);
        ToggleHelpButton toggleHelpButton = this.addRenderableWidget(new ToggleHelpButton(x, y, 21, 21, 197, 0, 21,
                PALETTE_TEXTURES, 256, 256, button -> showHelp = !showHelp));
        toggleHelpButton.setTooltip(Tooltip.create(Component.literal("Toggle help tooltips")));

        updateButtons();
    }

    private void updateButtons() {
        if (!this.isSigned) {
            this.buttonCenter.visible = !this.gettingSigned;
            this.buttonGrid.visible = !this.gettingSigned;
            this.buttonSign.visible = !this.gettingSigned;
            this.buttonCancel.visible = this.gettingSigned;
            this.buttonFinalize.visible = this.gettingSigned;
            this.buttonFinalize.active = !this.canvasTitle.trim().isEmpty();

            this.buttonFinalize.setX((int) canvasX - 100);
            this.buttonCancel.setX((int) canvasX - 100);
        }
    }

    private int getPixelAt(int x, int y) {
        return this.pixels[y * canvasPixelWidth + x];
    }

    private void setPixelAt(int x, int y, PaletteUtil.Color color, float opacity) {
        if (x >= 0 && y >= 0 && x < canvasPixelWidth && y < canvasPixelHeight && !draggedPoints.contains(y * canvasPixelWidth + x)) {
            draggedPoints.add(y * canvasPixelWidth + x);
            this.pixels[y * canvasPixelWidth + x] = PaletteUtil.Color.mix(color, new PaletteUtil.Color(this.pixels[y * canvasPixelWidth + x]), opacity).rgbVal();
        }
    }

    private void setPixelsAt(int mouseX, int mouseY, PaletteUtil.Color color, int brushSize, float opacity) {
        if (brushSize < 0 || brushSize >= BRUSH_SHAPES.length) {
            return;
        }
        // Anchor exactly like drawBrushShapeOutline so the painted dab matches the on-screen outline.
        final int pixelHalf = canvasPixelScale / 2;
        final int adjust = BRUSH_HALF[brushSize] ? pixelHalf : 0;
        final int x = (mouseX - (int) canvasX + adjust) / canvasPixelScale;
        final int y = (mouseY - (int) canvasY + adjust) / canvasPixelScale;
        for (int[] off : BRUSH_SHAPES[brushSize]) {
            setPixelAt(x + off[0], y + off[1], color, opacity);
        }
    }

    private void resetPositions() {
        final int padding = 40;
        final int paletteCanvasX = (this.width - (PALETTE_WIDTH + canvasWidth + padding)) / 2;
        canvasX = paletteCanvasX + PALETTE_WIDTH + padding;
        if (canvasType.equals(CanvasType.LONG)) {
            canvasY = 80;
        } else {
            canvasY = 40;
        }

        paletteX = paletteCanvasX;
        paletteY = 40;
    }

    @Override
    public void tick() {
        ++this.updateCount;
        ++this.timeSinceLastUpdate;

        if (easel != null) {
            if (easel.getItem().isEmpty() || easel.isRemoved() || easel.distanceToSqr(editingPlayer) > MAX_EASEL_DISTANCE_SQR) {
                this.onClose();
            }
            if (skippedUpdate && timeSinceLastUpdate > 20 && canvasDirty) {
                updateCanvas(false);
                skippedUpdate = false;
            }
        }

        super.tick();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
        if (!gettingSigned) {
            super.render(guiGraphics, mouseX, mouseY, f);
        } else {
            super.superRender(guiGraphics, mouseX, mouseY, f);
        }

        // Draw the canvas holder
        guiGraphics.fill((int) (canvasX + canvasWidth * 0.25), (int) canvasY - CANVAS_HOLDER_HEIGHT, (int) (canvasX + canvasWidth * 0.75), (int) canvasY, 0xffe1e1e1);

        // Draw the canvas
        for (int i = 0; i < canvasPixelHeight; i++) {
            for (int j = 0; j < canvasPixelWidth; j++) {
                int y = (int) canvasY + i * canvasPixelScale;
                int x = (int) canvasX + j * canvasPixelScale;
                guiGraphics.fill(x, y, x + canvasPixelScale, y + canvasPixelScale, getPixelAt(j, i));
            }
        }

        // Draw the optional pixel grid on top. Purely a painting guide; it is never part of the
        // saved pixel data, so it won't appear on the finished painting.
        if (showGrid && !gettingSigned) {
            final int gridColor = 0xFF000000;
            final int centerColor = 0xFFFF0000;
            final int left = (int) canvasX;
            final int top = (int) canvasY;
            final int right = left + canvasWidth;
            final int bottom = top + canvasHeight;
            // Black grid lines around every pixel.
            for (int j = 0; j <= canvasPixelWidth; j++) {
                int x = left + j * canvasPixelScale;
                guiGraphics.fill(x, top, x + 1, bottom + 1, gridColor);
            }
            for (int i = 0; i <= canvasPixelHeight; i++) {
                int y = top + i * canvasPixelScale;
                guiGraphics.fill(left, y, right + 1, y + 1, gridColor);
            }
            // Red center axis lines drawn on top (every canvas dimension is even, so the centre
            // falls exactly on a grid line).
            int centerX = left + (canvasPixelWidth / 2) * canvasPixelScale;
            int centerY = top + (canvasPixelHeight / 2) * canvasPixelScale;
            guiGraphics.fill(centerX, top, centerX + 1, bottom + 1, centerColor);
            guiGraphics.fill(left, centerY, right + 1, centerY + 1, centerColor);
        }

        if (!gettingSigned) {
            // Draw brush meter (BRUSH_SIZE_COUNT sizes, largest at the top)
            for (int i = 0; i < BRUSH_SIZE_COUNT; i++) {
                int y = brushMeterY + i * BRUSH_SPRITE_SIZE;
                guiGraphics.fill(brushMeterX, y, brushMeterX + 3, y + 3, currentColor.rgbVal());
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.blit(PALETTE_TEXTURES, brushMeterX, brushMeterY + (BRUSH_SIZE_COUNT - 1 - brushSize) * BRUSH_SPRITE_SIZE, 15, 246, 10, 10);
            // The texture only has icons for sizes 0-3, so the two largest reuse the size-3 icon.
            for (int i = 0; i < BRUSH_SIZE_COUNT; i++) {
                int size = BRUSH_SIZE_COUNT - 1 - i;
                int iconY = BRUSH_SPRITE_Y - BRUSH_SPRITE_SIZE * Math.min(size, 3);
                guiGraphics.blit(PALETTE_TEXTURES, brushMeterX, brushMeterY + i * BRUSH_SPRITE_SIZE, BRUSH_SPRITE_X, iconY, BRUSH_SPRITE_SIZE, BRUSH_SPRITE_SIZE);
            }

            // Draw the bucket (fill) tool above the brush meter
            drawBucketTool(guiGraphics, mouseX, mouseY);

            // Draw opacity meter
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.blit(PALETTE_TEXTURES, brushOpacityMeterX, brushOpacityMeterY, BRUSH_OPACITY_SPRITE_X, BRUSH_OPACITY_SPRITE_Y, BRUSH_OPACITY_SPRITE_SIZE, BRUSH_OPACITY_SPRITE_SIZE * 4 + 3);
            guiGraphics.blit(PALETTE_TEXTURES, brushOpacityMeterX - 1, brushOpacityMeterY - 1 + brushOpacitySetting * (BRUSH_OPACITY_SPRITE_SIZE + 1), 212, 240, 16, 16);

            // Draw zoom controls under the canvas
            drawZoomControls(guiGraphics, mouseX, mouseY);

            // Draw brush and outline
            renderCursor(guiGraphics, mouseX, mouseY);

            if (showHelp) {
                if (inBucket(mouseX, mouseY)) {
                    guiGraphics.renderComponentTooltip(font, Arrays.asList(Component.literal("Fill tool"),
                            Component.literal("Click to toggle it on, then click the canvas to flood-fill a connected area with the current color. Right-click fills with white.").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                } else if (inBrushMeter(mouseX, mouseY)) {
                    int selectedSize = (BRUSH_SIZE_COUNT - 1) - (mouseY - brushMeterY) / BRUSH_SPRITE_SIZE;
                    if (selectedSize <= BRUSH_SIZE_COUNT - 1 && selectedSize >= 0) {
                        guiGraphics.renderTooltip(font, Component.literal("Brush size (" + (selectedSize + 1) + ")"), mouseX, mouseY);
                    }
                } else if (inBrushOpacityMeter(mouseX, mouseY)) {
                    int relativeY = mouseY - brushOpacityMeterY;
                    int selectedOpacity = relativeY / (BRUSH_OPACITY_SPRITE_SIZE + 1);
                    if (selectedOpacity >= 0 && selectedOpacity <= 3) {
                        int percentage = 100 - 25 * selectedOpacity;
                        guiGraphics.renderTooltip(font, Component.literal("Brush opacity (" + percentage + "%)"), mouseX, mouseY);
                    }
                } else if (inColorPicker(mouseX - (int) paletteX, mouseY - (int) paletteY)) {
                    guiGraphics.renderComponentTooltip(font, Arrays.asList(Component.literal("Color picker"),
                            Component.literal("Select the tool, then pick up a color from the canvas and drag-and-drop it to a custom color slot.").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                } else if (inWater(mouseX - (int) paletteX, mouseY - (int) paletteY)) {
                    guiGraphics.renderComponentTooltip(font, Arrays.asList(Component.literal("Color remover"),
                            Component.literal("Pick up some water and drag-and-drop it to a custom color slot to clear it.").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                } else if (inCanvasHolder(mouseX, mouseY)) {
                    guiGraphics.renderComponentTooltip(font, Arrays.asList(Component.literal("Canvas holder"),
                            Component.literal("Pick up the canvas and move it wherever you want. You can move the palette in the same way.").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                }
            }
        } else {
            drawSigning(guiGraphics);
        }
    }

    private void renderCursor(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (isCarryingColor) {
            carriedColor.setGLColor();
            guiGraphics.blit(PALETTE_TEXTURES, mouseX - BRUSH_SPRITE_SIZE / 2, mouseY - BRUSH_SPRITE_SIZE / 2, BRUSH_SPRITE_X + BRUSH_SPRITE_SIZE, BRUSH_SPRITE_Y, DROP_SPRITE_WIDTH, BRUSH_SPRITE_SIZE);

        } else if (isCarryingWater) {
            WATER_COLOR.setGLColor();
            guiGraphics.blit(PALETTE_TEXTURES, mouseX - BRUSH_SPRITE_SIZE / 2, mouseY - BRUSH_SPRITE_SIZE / 2, BRUSH_SPRITE_X + BRUSH_SPRITE_SIZE, BRUSH_SPRITE_Y, DROP_SPRITE_WIDTH, BRUSH_SPRITE_SIZE);
        } else if (isPickingColor) {
            drawOutline(guiGraphics, mouseX, mouseY, 0);
            PaletteUtil.Color.WHITE.setGLColor();
            guiGraphics.blit(PALETTE_TEXTURES, mouseX, mouseY - COLOR_PICKER_SIZE, COLOR_PICKER_SPRITE_X, COLOR_PICKER_SPRITE_Y, COLOR_PICKER_SIZE, COLOR_PICKER_SIZE);
        } else if (isFilling) {
            drawOutline(guiGraphics, mouseX, mouseY, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.fill(mouseX, mouseY, mouseX + 3, mouseY + 3, currentColor.rgbVal());
            guiGraphics.renderItem(new ItemStack(net.minecraft.world.item.Items.BUCKET), mouseX + 2, mouseY - BUCKET_SIZE);
        } else {
            drawOutline(guiGraphics, mouseX, mouseY, brushSize);

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.fill(mouseX, mouseY, mouseX + 3, mouseY + 3, currentColor.rgbVal());

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // Only sizes 0-3 have a baked icon; the two largest reuse the size-3 icon (the outline shows true size).
            int trueBrushY = BRUSH_SPRITE_Y - BRUSH_SPRITE_SIZE * Math.min(brushSize, 3);
            guiGraphics.blit(PALETTE_TEXTURES, mouseX, mouseY, BRUSH_SPRITE_X, trueBrushY, BRUSH_SPRITE_SIZE, BRUSH_SPRITE_SIZE);
        }
    }

    private void drawOutline(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, int brushSize) {
        if (inCanvas(mouseX, mouseY)) {
            // Render drawing outline
            int x = 0;
            int y = 0;
            int outlineSize = 0;
            int pixelHalf = canvasPixelScale / 2;
            switch (brushSize) {
                case 0 -> {
                    x = ((mouseX - (int) canvasX) / canvasPixelScale) * canvasPixelScale + (int) canvasX - 1;
                    y = ((mouseY - (int) canvasY) / canvasPixelScale) * canvasPixelScale + (int) canvasY - 1;
                    outlineSize = canvasPixelScale + 2;
                }
                case 1 -> {
                    x = (((mouseX - (int) canvasX + pixelHalf) / canvasPixelScale) - 1) * canvasPixelScale + (int) canvasX - 1;
                    y = (((mouseY - (int) canvasY + pixelHalf) / canvasPixelScale) - 1) * canvasPixelScale + (int) canvasY - 1;
                    outlineSize = canvasPixelScale * 2 + 2;
                }
                case 2 -> {
                    x = (((mouseX - (int) canvasX + pixelHalf) / canvasPixelScale) - 2) * canvasPixelScale + (int) canvasX - 1;
                    y = (((mouseY - (int) canvasY + pixelHalf) / canvasPixelScale) - 2) * canvasPixelScale + (int) canvasY - 1;
                    outlineSize = canvasPixelScale * 4 + 2;
                }
                case 3 -> {
                    x = (((mouseX - (int) canvasX) / canvasPixelScale) - 2) * canvasPixelScale + (int) canvasX - 1;
                    y = (((mouseY - (int) canvasY) / canvasPixelScale) - 2) * canvasPixelScale + (int) canvasY - 1;
                    outlineSize = canvasPixelScale * 5 + 2;
                }
                default -> {
                    // Ignore unsupported brush sizes.
                }
            }

            if (canvasZoom == 1 && brushSize < OUTLINE_POSS_1.length) {
                // The baked outline sprites only exist at the two base scales (10 and 5), sizes 0-3.
                Vec2 textureVec = baseCanvasPixelScale == SMALL_CANVAS_PIXEL_SCALE ? OUTLINE_POSS_1[brushSize] : OUTLINE_POSS_2[brushSize];
                RenderSystem.setShaderColor(0.3F, 0.3F, 0.3F, 1.0F);
                guiGraphics.blit(PALETTE_TEXTURES, x, y, (int) textureVec.x, (int) textureVec.y, outlineSize, outlineSize);
            } else {
                // Draw the outline programmatically, tracing the real brush shape so rounded brushes
                // don't look square when zoomed in.
                drawBrushShapeOutline(guiGraphics, mouseX, mouseY, brushSize, pixelHalf);
            }
        }
    }

    // Traces the outline of the actual brush pixels (so size 2/3 stay rounded at any zoom).
    private void drawBrushShapeOutline(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, int brushSize, int pixelHalf) {
        if (brushSize < 0 || brushSize >= BRUSH_SHAPES.length) {
            return;
        }
        int[][] shape = BRUSH_SHAPES[brushSize];
        int adjust = BRUSH_HALF[brushSize] ? pixelHalf : 0;
        int anchorX = (mouseX - (int) canvasX + adjust) / canvasPixelScale;
        int anchorY = (mouseY - (int) canvasY + adjust) / canvasPixelScale;
        int scale = canvasPixelScale;
        int t = Math.max(1, scale / 10);
        int color = 0xFF4D4D4D;
        for (int[] off : shape) {
            int sx = (int) canvasX + (anchorX + off[0]) * scale;
            int sy = (int) canvasY + (anchorY + off[1]) * scale;
            if (!brushContains(shape, off[0] - 1, off[1])) {
                guiGraphics.fill(sx, sy, sx + t, sy + scale, color);
            }
            if (!brushContains(shape, off[0] + 1, off[1])) {
                guiGraphics.fill(sx + scale - t, sy, sx + scale, sy + scale, color);
            }
            if (!brushContains(shape, off[0], off[1] - 1)) {
                guiGraphics.fill(sx, sy, sx + scale, sy + t, color);
            }
            if (!brushContains(shape, off[0], off[1] + 1)) {
                guiGraphics.fill(sx, sy + scale - t, sx + scale, sy + scale, color);
            }
        }
    }

    private static boolean brushContains(int[][] shape, int dx, int dy) {
        for (int[] off : shape) {
            if (off[0] == dx && off[1] == dy) {
                return true;
            }
        }
        return false;
    }

    private void drawSigning(@NotNull GuiGraphics guiGraphics) {
        int i = (int) canvasX;
        int j = (int) canvasY;

        guiGraphics.fill(i + 10, j + 10, i + 150, j + 150, 0xFFEEEEEE);
        String s = this.canvasTitle;

        if (!this.isSigned) {
            if (this.updateCount / 6 % 2 == 0) {
                s = s + ChatFormatting.BLACK + "_";
            } else {
                s = s + ChatFormatting.GRAY + "_";
            }
        }
        String s1 = I18n.get("canvas.editTitle");
        int k = this.font.width(s1);
        guiGraphics.drawString(this.font, s1, (int) (i + 26 + (116 - k) / 2.0f), (j + 16 + 16), 0, false);
        int l = this.font.width(s);
        guiGraphics.drawString(this.font, s, (int) (i + 26 + (116 - l) / 2.0f), j + 48, 0, false);
        String s2 = I18n.get("canvas.byAuthor", this.editingPlayer.getName().getString());
        int i1 = this.font.width(s2);
        guiGraphics.drawString(this.font, ChatFormatting.DARK_GRAY + s2, (int) (i + 26 + (116 - i1) / 2.0f), j + 48 + 10, 0, false);
        guiGraphics.drawWordWrap(this.font, Component.translatable("canvas.finalizeWarning"), i + 26, j + 80, 116, 0);
    }

    private void playBrushSound() {
        brushSound = new BrushSound();
        playSound(brushSound);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.gettingSigned) {
            switch (Integer.valueOf(keyCode)) {
                case Integer k when k == GLFW.GLFW_KEY_BACKSPACE && !this.canvasTitle.isEmpty() -> {
                    this.canvasTitle = this.canvasTitle.substring(0, this.canvasTitle.length() - 1);
                    this.updateButtons();
                }
                case Integer k when k == GLFW.GLFW_KEY_ENTER && !this.canvasTitle.isEmpty() -> {
                    canvasDirty = true;
                    this.isSigned = true;
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }
                default -> {
                    // Do nothing
                }
            }
            return true;
        } else {
            // Track a "tap" of Ctrl on its own (no other key) so Ctrl+Z stays undo-only.
            ctrlTapPending = keyCode == GLFW_KEY_LEFT_CONTROL || keyCode == GLFW_KEY_RIGHT_CONTROL;
            if (keyCode == GLFW_KEY_SPACE) {
                spacePanning = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) == GLFW.GLFW_MOD_CONTROL) {
                if (!undoStack.isEmpty()) {
                    pixels = undoStack.pop();
                    canvasDirty = true;
                    if (easel != null) {
                        updateCanvas(false);
                    }
                }
                return true;
            } else {
                if (keyCode == GLFW_KEY_O) {
                    brushOpacitySetting += 1;
                    if (brushOpacitySetting >= BRUSH_LEVEL_COUNT) {
                        brushOpacitySetting = 0;
                    }
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW_KEY_SPACE) {
            if (spacePanning) {
                // Brief grace so a still-held left button doesn't instantly paint after panning.
                paintGraceUntil = System.currentTimeMillis() + PAN_PAINT_GRACE_MS;
            }
            spacePanning = false;
            return true;
        }
        if ((keyCode == GLFW_KEY_LEFT_CONTROL || keyCode == GLFW_KEY_RIGHT_CONTROL) && ctrlTapPending && !gettingSigned) {
            ctrlTapPending = false;
            toggleColorPicker();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void toggleColorPicker() {
        if (isPickingColor) {
            isPickingColor = false;
        } else {
            setPickingColor();
            playSound(SoundEvents.COLOR_PICKER);
        }
    }

    private static boolean isAllowedChatCharacter(char var0) {
        return var0 != 167 && var0 >= ' ' && var0 != 127;
    }

    @Override
    public boolean charTyped(char typedChar, int something) {
        super.charTyped(typedChar, something);

        if (!this.isSigned) {
            if (this.gettingSigned && this.canvasTitle.length() < MAX_TITLE_LENGTH && isAllowedChatCharacter(typedChar)) {
                this.canvasTitle = this.canvasTitle + typedChar;
                this.updateButtons();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double posX, double posY, double scrollX, double scrollY) {
        int mouseX = (int) Math.floor(posX);
        int mouseY = (int) Math.floor(posY);
        if (!gettingSigned && scrollY != 0.d) {
            if (hasShiftDown()) {
                applyZoom(canvasZoom + (scrollY > 0 ? 1 : -1));
                return true;
            }
            if (inBrushOpacityMeter(mouseX, mouseY)) {
                final int maxBrushOpacity = BRUSH_LEVEL_COUNT - 1;
                brushOpacitySetting += scrollY < 0 ? 1 : -1;
                if (brushOpacitySetting > maxBrushOpacity) brushOpacitySetting = 0;
                else if (brushOpacitySetting < 0) brushOpacitySetting = maxBrushOpacity;
                return true;
            } else {
                final int maxBrushSize = BRUSH_SIZE_COUNT - 1;
                brushSize += scrollY > 0 ? 1 : -1;
                if (brushSize > maxBrushSize) brushSize = 0;
                else if (brushSize < 0) brushSize = maxBrushSize;
                isFilling = false;
                return true;
            }
        }
        return super.mouseScrolled(posX, posY, scrollX, scrollY);
    }

    // Mouse button 0: left, 1: right
    @Override
    public boolean mouseClicked(double posX, double posY, int mouseButton) {
        if (gettingSigned) {
            return super.superMouseClicked(posX, posY, mouseButton);
        }

        int mouseX = (int) Math.floor(posX);
        int mouseY = (int) Math.floor(posY);

        // Middle mouse (or space, handled in keyPressed) starts panning the canvas; swallow the click.
        if (mouseButton == GLFW_MOUSE_BUTTON_MIDDLE) {
            middlePanning = true;
            return true;
        }
        if (middlePanning || spacePanning) {
            // A left click here is swallowed for panning; clear the stroke flag so painting that resumes
            // afterwards (still holding left after a space-pan) pushes its own undo snapshot.
            undoStarted = false;
            return true;
        }

        undoStarted = true;
        touchedCanvas = false;
        if (undoStack.size() >= MAX_UNDO_LENGTH) {
            undoStack.removeLast();
        }
        undoStack.push(pixels.clone());

        if (inCanvas(mouseX, mouseY)) {
            if (isFilling) {
                int x = (mouseX - (int) canvasX) / canvasPixelScale;
                int y = (mouseY - (int) canvasY) / canvasPixelScale;
                if (x >= 0 && y >= 0 && x < canvasPixelWidth && y < canvasPixelHeight) {
                    PaletteUtil.Color fill = mouseButton == GLFW_MOUSE_BUTTON_RIGHT ? PaletteUtil.Color.WHITE : currentColor;
                    floodFill(x, y, fill.rgbVal());
                    touchedCanvas = true;
                    playSound(SoundEvents.MIX, 0.7f);
                }
            } else if (isPickingColor) {
                int x = (mouseX - (int) canvasX) / canvasPixelScale;
                int y = (mouseY - (int) canvasY) / canvasPixelScale;
                if (x >= 0 && y >= 0 && x < canvasPixelWidth && y < canvasPixelHeight) {
                    int color = getPixelAt(x, y);
                    // Picking immediately selects the colour (and still carries it so it can be dropped on a slot).
                    carriedColor = currentColor = new PaletteUtil.Color(color);
                    setCarryingColor();
                    playSound(SoundEvents.COLOR_PICKER_SUCK);
                }
            } else {
                clickedCanvas(mouseX, mouseY, mouseButton);
                playBrushSound();
            }
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }

        if (inZoomMinus(mouseX, mouseY)) {
            applyZoom(canvasZoom - 1);
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }
        if (inZoomPlus(mouseX, mouseY)) {
            applyZoom(canvasZoom + 1);
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }

        if (inBucket(mouseX, mouseY)) {
            isFilling = !isFilling;
            playSound(SoundEvents.MIX, 0.5f);
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }

        if (inBrushMeter(mouseX, mouseY)) {
            int selectedSize = (BRUSH_SIZE_COUNT - 1) - (mouseY - brushMeterY) / BRUSH_SPRITE_SIZE;
            if (selectedSize <= BRUSH_SIZE_COUNT - 1 && selectedSize >= 0) {
                brushSize = selectedSize;
                isFilling = false;
            }
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }
        if (inBrushOpacityMeter(mouseX, mouseY)) {
            int relativeY = mouseY - brushOpacityMeterY;
            int selectedOpacity = relativeY / (BRUSH_OPACITY_SPRITE_SIZE + 1);
            if (selectedOpacity >= 0 && selectedOpacity <= 3) {
                brushOpacitySetting = selectedOpacity;
            }
            return super.superMouseClicked(mouseX, mouseY, mouseButton);
        }
        if (inCanvasHolder(mouseX, mouseY)) {
            isCarryingCanvas = true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void clickedCanvas(int mouseX, int mouseY, int mouseButton) {
        touchedCanvas = true;
        if (mouseButton == GLFW_MOUSE_BUTTON_LEFT) {
            setPixelsAt(mouseX, mouseY, currentColor, brushSize, BRUSH_OPACITIES[brushOpacitySetting]);
        } else if (mouseButton == GLFW_MOUSE_BUTTON_RIGHT) {
            // "Erase" with right click
            setPixelsAt(mouseX, mouseY, PaletteUtil.Color.WHITE, brushSize, 1.0f);
        }
        canvasDirty = true;
    }

    @Override
    public boolean mouseReleased(double posX, double posY, int mouseButton) {
        if (mouseButton == GLFW_MOUSE_BUTTON_MIDDLE) {
            middlePanning = false;
            return true;
        }
        isCarryingCanvas = false;
        if (gettingSigned) {
            return super.superMouseReleased(posX, posY, mouseButton);
        }
        draggedPoints.clear();

        if (undoStarted && !touchedCanvas) {
            undoStarted = false;
            undoStack.removeFirst();
        }

        if (brushSound != null) {
            brushSound.stopSound();
        }

        if (easel != null) {
            updateCanvas(false);
        }

        return super.mouseReleased(posX, posY, mouseButton);
    }

    @Override
    public boolean mouseDragged(double posX, double posY, int mouseButton, double deltaX, double deltaY) {
        if (gettingSigned) {
            return super.superMouseDragged(posX, posY, mouseButton, deltaX, deltaY);
        }
        if (middlePanning || spacePanning) {
            updateCanvasPos(deltaX, deltaY);
            return true;
        }
        if (!isCarryingColor && !isCarryingWater && !isPickingColor && !isCarryingPalette && !isCarryingCanvas && !isFilling) {
            if (System.currentTimeMillis() < paintGraceUntil) {
                // Grace period right after a space-pan: don't paint with the still-held left button.
                return true;
            }
            int mouseX = (int) Math.floor(posX);
            int mouseY = (int) Math.floor(posY);
            if (inCanvas(mouseX, mouseY)) {
                if (!undoStarted) {
                    // Painting resumed without a fresh click (e.g. still holding left after a space-pan);
                    // snapshot now so this stroke can be undone.
                    undoStarted = true;
                    touchedCanvas = false;
                    if (undoStack.size() >= MAX_UNDO_LENGTH) {
                        undoStack.removeLast();
                    }
                    undoStack.push(pixels.clone());
                }
                clickedCanvas(mouseX, mouseY, mouseButton);
            }

            if (brushSound != null) {
                brushSound.refreshFade();
            }
        } else if (isCarryingCanvas) {
            updateCanvasPos(deltaX, deltaY);
            return super.superMouseDragged(posX, posY, mouseButton, deltaX, deltaY);
        } else if (isCarryingPalette) {
            boolean ret = super.mouseDragged(posX, posY, mouseButton, deltaX, deltaY);
            updatePalettePos(deltaX, deltaY);
            return ret;
        }
        return super.mouseDragged(posX, posY, mouseButton, deltaX, deltaY);
    }

    private void updateCanvasPos(double deltaX, double deltaY) {
        canvasX += deltaX;
        canvasY += deltaY;

        brushMeterX = (int) canvasX + canvasWidth + 2;
        // Shift the meter up by the two new (largest) sizes so they sit above the original four;
        // the bucket is anchored to brushMeterY, so it moves up with the taller meter.
        brushMeterY = (int) canvasY + canvasHeight / 2 + 30 - 2 * BRUSH_SPRITE_SIZE;

        brushOpacityMeterX = (int) canvasX + canvasWidth + 2;
        brushOpacityMeterY = (int) canvasY;

        int typeIndex = canvasType.toByte();
        CANVAS_XS[typeIndex] = canvasX;
        CANVAS_YS[typeIndex] = canvasY;
    }

    private void updatePalettePos(double deltaX, double deltaY) {
        paletteX += deltaX;
        paletteY += deltaY;

        int typeIndex = canvasType.toByte();
        PALETTE_XS[typeIndex] = paletteX;
        PALETTE_YS[typeIndex] = paletteY;
    }

    private void applyZoom(int newZoom) {
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (newZoom == canvasZoom) {
            return;
        }
        // Keep the canvas centred on its current centre as it grows/shrinks (integer scale = pixel perfect).
        double centerX = canvasX + canvasWidth / 2.0;
        double centerY = canvasY + canvasHeight / 2.0;
        canvasZoom = newZoom;
        canvasPixelScale = baseCanvasPixelScale * canvasZoom;
        canvasWidth = canvasPixelWidth * canvasPixelScale;
        canvasHeight = canvasPixelHeight * canvasPixelScale;
        canvasX = centerX - canvasWidth / 2.0;
        canvasY = centerY - canvasHeight / 2.0;
        updateCanvasPos(0, 0);
    }

    private int zoomCenterX() {
        return (int) canvasX + canvasWidth / 2;
    }

    private int zoomBarY() {
        return (int) canvasY + canvasHeight + 5;
    }

    private boolean inZoomMinus(int x, int y) {
        int bx = zoomCenterX() - 34;
        int by = zoomBarY();
        return x >= bx && x < bx + ZOOM_BTN_SIZE && y >= by && y < by + ZOOM_BTN_SIZE;
    }

    private boolean inZoomPlus(int x, int y) {
        int bx = zoomCenterX() + 20;
        int by = zoomBarY();
        return x >= bx && x < bx + ZOOM_BTN_SIZE && y >= by && y < by + ZOOM_BTN_SIZE;
    }

    private void drawZoomControls(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int cx = zoomCenterX();
        int by = zoomBarY();
        drawZoomButton(guiGraphics, cx - 34, by, "-", canvasZoom > MIN_ZOOM, inZoomMinus(mouseX, mouseY));
        drawZoomButton(guiGraphics, cx + 20, by, "+", canvasZoom < MAX_ZOOM, inZoomPlus(mouseX, mouseY));
        guiGraphics.drawCenteredString(this.font, canvasZoom + "x", cx, by + 3, 0xFFFFFFFF);
    }

    private void drawZoomButton(@NotNull GuiGraphics guiGraphics, int x, int y, String label, boolean enabled, boolean hovered) {
        int fill = !enabled ? 0xFF8B8B8B : (hovered ? 0xFFDADADA : 0xFFC6C6C6);
        guiGraphics.fill(x - 1, y - 1, x + ZOOM_BTN_SIZE + 1, y + ZOOM_BTN_SIZE + 1, 0xFF000000);
        guiGraphics.fill(x, y, x + ZOOM_BTN_SIZE, y + ZOOM_BTN_SIZE, fill);
        guiGraphics.fill(x, y, x + ZOOM_BTN_SIZE, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + ZOOM_BTN_SIZE, 0xFFFFFFFF);
        guiGraphics.fill(x + ZOOM_BTN_SIZE - 1, y + 1, x + ZOOM_BTN_SIZE, y + ZOOM_BTN_SIZE, 0xFF555555);
        guiGraphics.fill(x + 1, y + ZOOM_BTN_SIZE - 1, x + ZOOM_BTN_SIZE, y + ZOOM_BTN_SIZE, 0xFF555555);
        guiGraphics.drawCenteredString(this.font, label, x + ZOOM_BTN_SIZE / 2, y + 3, enabled ? 0xFF202020 : 0xFF606060);
    }

    private int bucketX() {
        return brushMeterX - 3;
    }

    private int bucketY() {
        return brushMeterY - BUCKET_SIZE - 4;
    }

    private boolean inBucket(int x, int y) {
        return x >= bucketX() && x < bucketX() + BUCKET_SIZE && y >= bucketY() && y < bucketY() + BUCKET_SIZE;
    }

    private void drawBucketTool(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int bx = bucketX();
        int by = bucketY();
        if (!isFilling && inBucket(mouseX, mouseY)) {
            guiGraphics.fill(bx, by, bx + BUCKET_SIZE, by + BUCKET_SIZE, 0x44FFFFFF);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.renderItem(new ItemStack(net.minecraft.world.item.Items.BUCKET), bx, by);
    }

    // 4-connected flood fill: replace the contiguous region of the clicked color with the given color.
    private void floodFill(int startX, int startY, int replacement) {
        int target = getPixelAt(startX, startY);
        if (target == replacement) {
            return;
        }
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        while (!stack.isEmpty()) {
            int[] point = stack.pop();
            int x = point[0];
            int y = point[1];
            if (x < 0 || y < 0 || x >= canvasPixelWidth || y >= canvasPixelHeight) {
                continue;
            }
            if (getPixelAt(x, y) != target) {
                continue;
            }
            pixels[y * canvasPixelWidth + x] = replacement;
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
        canvasDirty = true;
    }

    @Override
    protected void setPickingColor() {
        super.setPickingColor();
        isFilling = false;
    }

    private boolean inCanvas(int x, int y) {
        return x < canvasX + canvasWidth && x >= canvasX && y < canvasY + canvasHeight && y >= canvasY;
    }

    private boolean inCanvasHolder(int x, int y) {
        return x < canvasX + canvasWidth * 0.75 && x >= canvasX + canvasWidth * 0.25 && y < canvasY && y >= canvasY - CANVAS_HOLDER_HEIGHT;
    }

    private boolean inBrushMeter(int x, int y) {
        return x < brushMeterX + BRUSH_SPRITE_SIZE && x >= brushMeterX && y < brushMeterY + BRUSH_SPRITE_SIZE * BRUSH_SIZE_COUNT && y >= brushMeterY;
    }

    private boolean inBrushOpacityMeter(int x, int y) {
        return x < brushOpacityMeterX + BRUSH_OPACITY_SPRITE_SIZE && x >= brushOpacityMeterX && y < brushOpacityMeterY + BRUSH_OPACITY_SPRITE_SIZE * 4 + 3 && y >= brushOpacityMeterY;
    }

    @Override
    public void removed() {
        if (suppressRemove) {
            return; // suspended while the hex picker popup is open; don't save/close yet
        }
        updateCanvas(true);
    }

    private void updateCanvas(boolean closing) {
        if (closing) {
            if (canvasDirty) {
                version++;
                int easelId = easel == null ? -1 : easel.getId();
                ClientPlayNetworking.send(new CanvasUpdatePacket(pixels, isSigned, canvasTitle, canvasId, version, easelId, customColors, canvasType));
            } else {
                if (easel != null) {
                    ClientPlayNetworking.send(new EaselLeftPacket(easel.getId()));
                }
                if (paletteDirty) {
                    PaletteUpdatePacket pack = new PaletteUpdatePacket(customColors);
                    ClientPlayNetworking.send(pack);
                }
            }
        } else {
            if (canvasDirty) {
                if (timeSinceLastUpdate < MINI_UPDATE_INTERVAL_TICKS) {
                    skippedUpdate = true;
                } else {
                    version++;
                    ClientPlayNetworking.send(new CanvasMiniUpdatePacket(pixels, canvasId, version, easel.getId(), canvasType));
                    canvasDirty = false;
                    timeSinceLastUpdate = 0;
                }
            }
        }
    }

    public static class ToggleHelpButton extends Button {
        protected final ResourceLocation resourceLocation;
        protected final int xTexStart;
        protected final int yTexStart;
        protected final int yDiffText;
        protected final int texWidth;
        protected final int texHeight;

        public ToggleHelpButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffText, ResourceLocation texture, int texWidth, int texHeight, OnPress onClick) {
            super(x, y, width, height, Component.empty(), onClick, Button.DEFAULT_NARRATION);
            this.texWidth = texWidth;
            this.texHeight = texHeight;
            this.xTexStart = xTexStart;
            this.yTexStart = yTexStart;
            this.yDiffText = yDiffText;
            this.resourceLocation = texture;
        }

        protected void postRender() {
            GlStateManager._enableDepthTest();
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.setShaderTexture(0, this.resourceLocation);
            GlStateManager._disableDepthTest();
            int yTexStartNew = this.yTexStart;
            if (this.isHovered) {
                yTexStartNew += this.yDiffText;
            }
            int xTexStartNew = this.xTexStart + (showHelp ? 0 : this.width);
            guiGraphics.blit(resourceLocation, this.getX(), this.getY(), xTexStartNew, yTexStartNew, this.width, this.height, this.texWidth, this.texHeight);
            postRender();
        }
    }
}
