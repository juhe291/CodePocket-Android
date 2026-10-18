package com.codepocket.ide

import com.codepocket.editor.surface.EditorSurface.Bounds
import com.codepocket.editor.surface.PopupGeometry
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class EditorSurfaceTest {
    private val bounds = Bounds(20, 100, 420, 700)
    private fun anchor(x: Double = 80.0, top: Double = 200.0, bottom: Double = 220.0) =
        PopupGeometry.anchor("workspace:file", 4, 9, bounds, x, top, bottom)

    @Test fun anchorsMatchDocumentRevisionAndUtf16OffsetIndependentlyOfScroll() {
        val old = anchor()!!
        val scrolled = anchor(top = 180.0, bottom = 200.0)!!
        assertTrue(scrolled.matches(old.document, old.revision, old.offset))
        assertFalse(old.matches("another:file", 4, 9))
        assertFalse(old.matches("workspace:file", 5, 9))
        assertFalse(old.matches("workspace:file", 4, 10))
        assertFalse(old.matches(null, 4, 9))
    }

    @Test fun invalidIdentityOffsetsAndEmptyVisibleAreasAreRejected() {
        for (key in listOf(null, "")) assertNull(PopupGeometry.anchor(key, 4, 0, bounds, 80.0, 200.0, 220.0))
        assertNull(PopupGeometry.anchor("file", -1, 0, bounds, 80.0, 200.0, 220.0))
        assertNull(PopupGeometry.anchor("file", 4, -1, bounds, 80.0, 200.0, 220.0))
        assertNull(PopupGeometry.anchor("file", 4, 0, Bounds(20, 100, 20, 700), 80.0, 200.0, 220.0))
    }

    @Test fun rootRelativeClippingTranslatesToScreenBeforeWindowAndTextClipping() {
        val result = PopupGeometry.visible(Bounds(0, 10, 500, 900), 80, 30,
            Bounds(80, 60, 580, 600), Bounds(100, 100, 560, 850))!!
        assertEquals(listOf(100, 100, 560, 600), listOf(result.left, result.top, result.right, result.bottom))
    }

    @Test fun nestedPaneClippingCannotExpandToTheWholeWindow() {
        val result = PopupGeometry.visible(Bounds(30, 120, 280, 400), 10, 20,
            Bounds(0, 0, 1000, 900), Bounds(20, 50, 500, 800))!!
        assertEquals(listOf(40, 140, 290, 420), listOf(result.left, result.top, result.right, result.bottom))
    }

    @Test fun aKeyboardBoundaryMakesThePopupUseSpaceAboveTheCaret() {
        val visible = Bounds(0, 0, 400, 320)
        val placed = PopupGeometry.place(visible, 150, 260, 280, 240, 100, 60, 3)!!
        assertEquals(157, placed.y)
        assertTrue(placed.y + placed.height <= 257)
        assertTrue(placed.y + placed.height <= visible.bottom)
    }

    @Test fun insufficientSpaceDoesNotCoverTheCaretOrKeyboard() {
        assertNull(PopupGeometry.place(Bounds(0, 0, 400, 80), 100, 30, 50, 240, 100, 40, 3))
    }

    @Test fun aPartiallyClippedCaretLineDoesNotProduceAnAnchor() {
        assertNull(anchor(top = 99.0, bottom = 120.0))
        assertNull(anchor(top = 680.0, bottom = 701.0))
        assertNull(anchor(x = 19.0))
        assertNull(anchor(x = 421.0))
        assertNull(anchor(top = 200.0, bottom = 200.0))
    }

    @Test fun nonFiniteAndOverflowingLayoutCoordinatesAreRejected() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e30, -1e30)) {
            assertNull(anchor(x = value)); assertNull(anchor(top = value)); assertNull(anchor(bottom = value))
        }
    }

    @Test fun subpixelLayoutUsesConsistentPixelRounding() {
        val placed = anchor(80.49, 200.51, 220.49)!!
        assertEquals(80, placed.x); assertEquals(201, placed.top); assertEquals(220, placed.bottom)
    }

    @Test fun narrowPanesConstrainWidthAndHorizontalPosition() {
        val placed = PopupGeometry.place(Bounds(20, 100, 160, 700), 150, 200, 220, 380, 180, 76, 3)!!
        assertEquals(20, placed.x); assertEquals(140, placed.width)
    }

    @Test fun invalidPopupBudgetsNeverProduceNegativeOrOverlappingRectangles() {
        for ((width, height, minimum, gap) in listOf(listOf(0, 100, 40, 3), listOf(100, 0, 40, 3),
            listOf(100, 100, 0, 3), listOf(100, 100, 40, -1), listOf(100, 20, 40, 3)))
            assertNull(PopupGeometry.place(bounds, 80, 200, 220, width, height, minimum, gap))
    }

    @Test fun textPaddingAndTheSoraGutterAreOutsideTheUsableAnchorArea() {
        val text = PopupGeometry.intersect(bounds, Bounds(70, 100, 410, 690))!!
        assertNull(PopupGeometry.anchor("file", 1, 0, text, 60.0, 200.0, 220.0))
        assertNotNull(PopupGeometry.anchor("file", 1, 0, text, 70.0, 200.0, 220.0))
    }

    @Test fun rightEdgeCaretClampsThePopupBackIntoThePane() {
        val placed = PopupGeometry.place(bounds, bounds.right, 200, 220, 220, 180, 76, 3)!!
        assertEquals(bounds.right - 220, placed.x)
        assertNotNull(anchor(x = bounds.right.toDouble()))
    }

    @Test fun negativeScreenCoordinatesRemainValidInAnOffsetWindow() {
        val screen = Bounds(-400, -100, -20, 600)
        val caret = PopupGeometry.anchor("file", 1, 0, screen, -200.0, 100.0, 120.0)!!
        val placed = PopupGeometry.place(screen, caret.x, caret.top, caret.bottom, 240, 120, 60, 3)!!
        assertTrue(placed.x >= screen.left); assertTrue(placed.x + placed.width <= screen.right)
    }

    @Test fun extremeScreenBoundsUseLongArithmetic() {
        val extreme = Bounds(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val placed = PopupGeometry.place(extreme, Int.MAX_VALUE, -10, 10, 300, 180, 76, 3)!!
        assertEquals(Int.MAX_VALUE - 300, placed.x)
        assertEquals(13, placed.y)
    }

    @Test fun overflowingRootTranslationsCannotWrapIntoTheVisibleWindow() {
        assertNull(PopupGeometry.visible(Bounds(0, 0, 20, 20), Int.MAX_VALUE - 4, 0, bounds, bounds))
    }

    @Test fun existingCompletionGeometryUsesTheSharedImplementation() {
        val shared = PopupGeometry.place(bounds, 80, 200, 220, 240, 140, 76, 3)!!
        val current = CompletionGeometry.place(CompletionBounds(20, 100, 420, 700), 80, 200, 220, 240, 140, 76, 3)!!
        assertEquals(listOf(shared.x, shared.y, shared.width, shared.height), listOf(current.x, current.y, current.width, current.height))
    }

    @Test fun sampledPlacementsStayInsideThePaneAndClearTheEntireCaretLine() {
        val random = Random(1846)
        repeat(1000) {
            val left = random.nextInt(401) - 200
            val top = random.nextInt(301) - 100
            val width = random.nextInt(600) + 80
            val height = random.nextInt(700) + 80
            val frame = Bounds(left, top, left + width, top + height)
            val x = left + random.nextInt(width + 1)
            val caretTop = top + random.nextInt(height - 20)
            val caretBottom = caretTop + 20
            val placed = PopupGeometry.place(frame, x, caretTop, caretBottom, 380, 180, 60, 3)
            if (placed != null) {
                assertTrue(placed.x >= frame.left && placed.y >= frame.top)
                assertTrue(placed.x.toLong() + placed.width <= frame.right)
                assertTrue(placed.y.toLong() + placed.height <= frame.bottom)
                assertTrue(placed.y >= caretBottom + 3 || placed.y + placed.height <= caretTop - 3)
                assertTrue(placed.height in 60..180 && placed.width in 1..380)
            }
        }
    }
}
