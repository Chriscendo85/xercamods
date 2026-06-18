package xerca.xercapaint;


public enum CanvasType {
    // width/height are in pixels (16 per block). The byte id is persisted in NBT/packets and must stay stable.
    SMALL(16, 16, 0),
    LARGE(32, 32, 1),
    LONG(32, 16, 2),
    TALL(16, 32, 3),
    EXTRA_LARGE(48, 48, 4),
    EXTRA_EXTRA_LARGE(64, 64, 5),
    EXTRA_LONG(48, 32, 6),
    EXTRA_EXTRA_LONG(64, 48, 7),
    EXTRA_TALL(32, 48, 8),
    EXTRA_EXTRA_TALL(48, 64, 9);

    private final int width;
    private final int height;
    private final byte id;

    CanvasType(int width, int height, int id) {
        this.width = width;
        this.height = height;
        this.id = (byte) id;
    }

    public byte toByte() {
        return id;
    }

    public static CanvasType fromByte(byte x) {
        for (CanvasType type : values()) {
            if (type.id == x) {
                return type;
            }
        }
        return null;
    }

    public static int getWidth(CanvasType canvasType) {
        return canvasType.width;
    }

    public static int getHeight(CanvasType canvasType) {
        return canvasType.height;
    }
}
