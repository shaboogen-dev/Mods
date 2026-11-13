package com.bitwig.mods.minimiseblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import bitwig.obf.dAd; // a toggle widget (the minimise arrow)
import bitwig.obf.dXD; // Bitwig's title + accessory header row
import bitwig.obf.dnF; // a block header (a framed widget)
import bitwig.obf.dvW; // a widget's base: children and visibility
import bitwig.obf.dve; // a widget
import bml.api.Bml;
import bml.inspector.Blocks;
import bml.inspector.BuiltBlock;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;

/**
 * Gives Inspector blocks Bitwig's own fold arrow beside their title: every mod's block, and Bitwig's own blocks named
 * below. Clicking it hides or shows the block's body; the header stays. Whether a block is minimised is kept until
 * Bitwig is closed.
 */
public final class MinimiseBlocks implements InspectorProvider {
    static final Bml.Mod MOD = Bml.mod("inspector-minimise-blocks");

    /**
     * Bitwig's blocks that get an arrow, by their (lower-case) title. Bitwig's block builder is only given a title, so
     * the title is what identifies a block.
     */
    private static final Set<String> BITWIG_BLOCKS = Set.of(
            "instrument track", "audio track", "fx track", "hybrid track",
            "arranger clip", "launcher clip", "slot", "scene",
            "note", "operators", "expressions", "device", "module", "modulator", "cue marker");

    /** Whether each block is minimised, per block and context; kept for the session. */
    private static final Map<String, AtomicBoolean> COLLAPSED = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void declare(Blocks blocks) {
        blocks.onBlockBuilt(MinimiseBlocks::built);
    }

    private static void built(BuiltBlock block) {
        if (!(block.widget() instanceof dvW group)) return;
        Context context = block.context();
        String where = context == null ? "other" : context.id();
        String key;
        if (block.bitwigBlock()) {
            String title = kind(block.title());
            if (title == null) return;
            // Bitwig's instrument track block has this shape for one track; other shapes are left alone.
            if (title.equals("instrument track") && group.jm_().size() != 6) return;
            key = "bitwig|" + where + "|" + title;
        } else {
            key = "mod|" + block.modId() + "/" + block.blockId() + "|" + where;
        }
        try {
            install(group, COLLAPSED.computeIfAbsent(key, k -> new AtomicBoolean(false)));
        } catch (Throwable t) {
            if (REPORTED.add(key)) MOD.log("minimise arrow for " + key + " failed; the block is shown without one", t);
        }
    }

    /**
     * Which of Bitwig's blocks a title names, or null. Bitwig titles a panel's main block with the selection's live
     * text: "NOTE" for one note, "NOTES (8)" for eight. So a count in brackets is dropped and a plural matched to its
     * singular, and the block keeps its arrow and its state however many are selected when the panel is built.
     */
    static String kind(String title) {
        if (title == null) return null;
        String t = title.toLowerCase(Locale.ROOT).trim().replaceFirst("\\s*\\(\\d+\\)$", "");
        if (BITWIG_BLOCKS.contains(t)) return t;
        if (t.endsWith("s") && BITWIG_BLOCKS.contains(t.substring(0, t.length() - 1))) return t.substring(0, t.length() - 1);
        return null;
    }

    /**
     * Puts the arrow beside the block's title: the header (the block's first child) is replaced by Bitwig's title +
     * accessory row holding the arrow and the header, and the arrow hides the rest of the block. Blocks of another
     * shape are left alone.
     */
    private static void install(dvW group, AtomicBoolean collapsed) {
        List<?> contents = group.jm_(); // the children
        int count = contents.size();
        if (count < 2 || count > 24) return;
        if (!(contents.get(0) instanceof dve header) || header.getClass() != dnF.class) return;
        List<dve> body = new ArrayList<>();
        for (Object child : contents.subList(1, count)) {
            if (!(child instanceof dve widget)) return;
            body.add(widget);
        }
        dAd arrow = Arrows.create(!collapsed.get(), expanded -> {
            for (dve widget : body) widget.t_(expanded); // shown or hidden
            collapsed.set(!expanded);
            return true;
        });
        dve row = null;
        boolean removed = false;
        try {
            group.MJ(header); // remove
            removed = true;
            row = dXD.cfM(new dve[]{arrow, header}); // the title + accessory row
            group.IwV(row, 0); // insert at
        } catch (RuntimeException e) {
            if (row != null) group.MJ(row);
            if (removed) group.IwV(header, 0);
            throw e;
        }
        if (collapsed.get()) {
            for (dve widget : body) widget.t_(false);
        }
    }
}
