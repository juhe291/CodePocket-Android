package com.codepocket.ide.terminal.shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable process-local identities. Selection never means stopping a process. UI thread owner. */
public final class TerminalCatalog<T> {
    public static final long TASK = 0;
    public static final int LIMIT = 8;
    public static final class Entry<T> {
        public final long id;
        public final T value;
        private String name;
        private Entry(long id, String name, T value) { this.id = id; this.name = name; this.value = value; }
        public String name() { return name; }
    }
    private final List<Entry<T>> entries = new ArrayList<>();
    private long nextId = 1;
    private long selected = TASK;
    public List<Entry<T>> entries() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    public long selectedId() { return selected; }
    public Entry<T> selected() { return find(selected); }
    public Entry<T> find(long id) { for (Entry<T> e : entries) if (e.id == id) return e; return null; }
    public Entry<T> add(T value) {
        if (entries.size() >= LIMIT) throw new IllegalStateException("最多保留8个Shell会话，请先结束一个会话");
        if (value == null) throw new IllegalArgumentException("Missing session");
        long id = nextId++;
        Entry<T> entry = new Entry<>(id, "sh " + id, value);
        entries.add(entry); selected = id; return entry;
    }
    public boolean select(long id) {
        if (id != TASK && find(id) == null) return false;
        selected = id; return true;
    }
    public boolean rename(long id, String value) {
        Entry<T> e = find(id); if (e == null) return false;
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("会话名称须为1至48个字符，不能包含控制字符");
        e.name = name; return true;
    }
    public Entry<T> remove(long id) {
        Entry<T> entry = find(id); if (entry == null) return null;
        int at = entries.indexOf(entry); entries.remove(at);
        if (selected == id) selected = entries.isEmpty() ? TASK : entries.get(Math.min(at, entries.size()-1)).id;
        return entry;
    }
}
