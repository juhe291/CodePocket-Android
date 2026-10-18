package com.codepocket.editor.surface;

import com.codepocket.editor.surface.EditorSurface.Anchor;
import com.codepocket.editor.surface.EditorSurface.Bounds;
import com.codepocket.editor.surface.EditorSurface.Placement;

/** Screen-pixel math shared by the native production editor and the pinned Sora adapter. */
public final class PopupGeometry {
    private PopupGeometry() {}
    private static boolean valid(Bounds b) { return b != null && b.left < b.right && b.top < b.bottom; }
    private static boolean fits(long n) { return n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE; }

    public static Bounds intersect(Bounds a, Bounds b) {
        if (!valid(a) || !valid(b)) return null;
        Bounds result = new Bounds(Math.max(a.left, b.left), Math.max(a.top, b.top),
            Math.min(a.right, b.right), Math.min(a.bottom, b.bottom));
        return valid(result) ? result : null;
    }

    /** Android global-visible rect is root-relative; window and text bounds are screen-relative. */
    public static Bounds visible(Bounds rootClip, int rootX, int rootY, Bounds window, Bounds text) {
        if (!valid(rootClip)) return null;
        long left = (long) rootClip.left + rootX, right = (long) rootClip.right + rootX;
        long top = (long) rootClip.top + rootY, bottom = (long) rootClip.bottom + rootY;
        if (!fits(left) || !fits(right) || !fits(top) || !fits(bottom)) return null;
        return intersect(intersect(new Bounds((int) left, (int) top, (int) right, (int) bottom), window), text);
    }

    public static Anchor anchor(String document, long revision, int offset, Bounds visible,
        double x, double top, double bottom) {
        if (document == null || document.isEmpty() || revision < 0 || offset < 0 || !valid(visible) ||
            !Double.isFinite(x) || !Double.isFinite(top) || !Double.isFinite(bottom)) return null;
        long px = Math.round(x), pt = Math.round(top), pb = Math.round(bottom);
        if (!fits(px) || !fits(pt) || !fits(pb) || px < visible.left || px > visible.right ||
            pt < visible.top || pb > visible.bottom || pb <= pt) return null;
        return new Anchor(document, revision, offset, visible, (int) px, (int) pt, (int) pb);
    }

    public static Placement place(Bounds bounds, int caretX, int caretTop, int caretBottom,
        int desiredWidth, int desiredHeight, int minimumHeight, int gap) {
        if (!valid(bounds) || desiredWidth <= 0 || desiredHeight <= 0 || minimumHeight <= 0 || gap < 0 ||
            caretX < bounds.left || caretX > bounds.right || caretTop < bounds.top ||
            caretBottom > bounds.bottom || caretBottom < caretTop) return null;
        long below = Math.max(0L, (long) bounds.bottom - caretBottom - gap);
        long above = Math.max(0L, (long) caretTop - bounds.top - gap);
        boolean useBelow = below >= desiredHeight || below >= above;
        int height = (int) Math.min(desiredHeight, useBelow ? below : above);
        if (height < minimumHeight) return null;
        int width = (int) Math.min(desiredWidth, (long) bounds.right - bounds.left);
        long x = Math.max(bounds.left, Math.min((long) caretX, (long) bounds.right - width));
        long y = useBelow ? (long) caretBottom + gap : (long) caretTop - gap - height;
        if (!fits(x) || !fits(y)) return null;
        return new Placement((int) x, (int) y, width, height);
    }
}
