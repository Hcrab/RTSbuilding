package com.rtsbuilding.rtsbuilding.client.util.render.model;

public final class NineSliceTiler {

    
    @FunctionalInterface
    public interface TileCallback {
        
        void accept(int srcX, int srcY, int srcW, int srcH,
                    int dstX, int dstY, int dstW, int dstH);
    }

    
    public static void forEachTile(int srcLeft, int srcTop, int srcW, int srcH, int border,
                                    int dstX, int dstY, int dstW, int dstH,
                                    TileCallback renderer) {
        int b = border;
        int innerW = dstW - 2 * b;
        int innerH = dstH - 2 * b;
        int srcInnerW = srcW - 2 * b;
        int srcInnerH = srcH - 2 * b;

        
        renderer.accept(srcLeft, srcTop, b, b, dstX, dstY, b, b);
        renderer.accept(srcLeft + srcW - b, srcTop, b, b, dstX + dstW - b, dstY, b, b);
        renderer.accept(srcLeft, srcTop + srcH - b, b, b, dstX, dstY + dstH - b, b, b);
        renderer.accept(srcLeft + srcW - b, srcTop + srcH - b, b, b, dstX + dstW - b, dstY + dstH - b, b, b);

        
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                renderer.accept(srcLeft + b, srcTop, tileW, b, dx, dstY, tileW, b);
            }
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                renderer.accept(srcLeft + b, srcTop + srcH - b, tileW, b, dx, dstY + dstH - b, tileW, b);
            }
        }

        
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                renderer.accept(srcLeft, srcTop + b, b, tileH, dstX, dy, b, tileH);
            }
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                renderer.accept(srcLeft + srcW - b, srcTop + b, b, tileH, dstX + dstW - b, dy, b, tileH);
            }
        }

        
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                    renderer.accept(srcLeft + b, srcTop + b, tileW, tileH, dx, dy, tileW, tileH);
                }
            }
        }
    }

    private NineSliceTiler() {}
}
