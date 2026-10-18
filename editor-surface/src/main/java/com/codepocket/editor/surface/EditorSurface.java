package com.codepocket.editor.surface;

/** Read-only, UI-thread popup anchor. No editor widget, document text or Kotlin dependency. */
public interface EditorSurface {
    /** Null while unavailable, composing, selected, unfocused, detached or outside the visible area. */
    Anchor anchor();

    final class Bounds {
        public final int left, top, right, bottom;
        public Bounds(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }

    final class Anchor {
        public final String document;
        public final long revision;
        public final int offset, x, top, bottom;
        public final Bounds visible;
        Anchor(String document, long revision, int offset, Bounds visible, int x, int top, int bottom) {
            this.document = document; this.revision = revision; this.offset = offset;
            this.visible = visible; this.x = x; this.top = top; this.bottom = bottom;
        }
        public boolean matches(String document, long revision, int offset) {
            return this.document.equals(document) && this.revision == revision && this.offset == offset;
        }
    }

    final class Placement {
        public final int x, y, width, height;
        Placement(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
