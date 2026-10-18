package com.codepocket.ide.terminal.shell;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerminalCatalogTest {
    @Test public void sessionIdentitiesAreStableAcrossRenameAndSelection() {
        TerminalCatalog<Object> catalog = new TerminalCatalog<>();
        Object first = new Object(), second = new Object();
        var a = catalog.add(first); var b = catalog.add(second);
        assertTrue(catalog.rename(a.id, "学习项目")); assertTrue(catalog.select(a.id));
        assertSame(first, catalog.selected().value); assertSame(second, catalog.find(b.id).value);
        assertEquals("学习项目", catalog.selected().name());
        assertTrue(catalog.select(TerminalCatalog.TASK)); assertNull(catalog.selected());
        assertSame(first, catalog.find(a.id).value);
    }
    @Test public void endingSelectedSessionChoosesNeighbourWithoutRemovingOthers() {
        TerminalCatalog<Object> c = new TerminalCatalog<>(); var a=c.add(new Object());var b=c.add(new Object());var d=c.add(new Object());
        c.select(b.id); assertSame(b,c.remove(b.id));assertSame(d,c.selected());
        assertSame(a,c.find(a.id));c.remove(d.id);assertSame(a,c.selected());
        c.remove(a.id);assertEquals(TerminalCatalog.TASK,c.selectedId());
    }
    @Test public void oldMenuIdentityCannotSelectOrRemoveReplacement() {
        TerminalCatalog<Object> c=new TerminalCatalog<>();var old=c.add(new Object());c.remove(old.id);var next=c.add(new Object());
        assertTrue(next.id>old.id);assertFalse(c.select(old.id));assertNull(c.remove(old.id));assertSame(next,c.selected());
    }
    @Test public void endingBackgroundSessionDoesNotChangeActiveTask() {
        TerminalCatalog<Object> c=new TerminalCatalog<>();var a=c.add(new Object());c.select(TerminalCatalog.TASK);
        c.remove(a.id);assertEquals(TerminalCatalog.TASK,c.selectedId());
    }
    @Test public void snapshotListDoesNotChangeDuringMenuInteraction() {
        TerminalCatalog<Object> c=new TerminalCatalog<>();c.add(new Object());List<?> snapshot=c.entries();c.add(new Object());
        assertEquals(1,snapshot.size());assertThrows(UnsupportedOperationException.class,snapshot::clear);
    }
    @Test public void sessionLimitDoesNotReplaceOrDropExistingEntries() {
        TerminalCatalog<Object> c=new TerminalCatalog<>();for(int i=0;i<TerminalCatalog.LIMIT;i++)c.add(new Object());
        long selected=c.selectedId();assertThrows(IllegalStateException.class,()->c.add(new Object()));
        assertEquals(TerminalCatalog.LIMIT,c.entries().size());assertEquals(selected,c.selectedId());
    }
    @Test public void invalidRenameLeavesOriginalTitleAndIdentityUntouched() {
        TerminalCatalog<Object> c=new TerminalCatalog<>();var a=c.add(new Object());String original=a.name();
        for(String text:new String[]{" ","a\nb","x".repeat(49)})assertThrows(IllegalArgumentException.class,()->c.rename(a.id,text));
        assertEquals(original,a.name());assertSame(a,c.selected());
    }
}
