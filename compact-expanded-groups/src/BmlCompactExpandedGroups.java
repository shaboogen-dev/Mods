import bitwig.obf.bDS; // a plain observable boolean value
import bitwig.obf.btF; // an arranger track's header
import bitwig.obf.btu; // a group track's arranger lane layout
import bitwig.obf.cNP; // a colour transform
import bitwig.obf.dAd; // a button
import bitwig.obf.dns; // value bindings
import bitwig.obf.dve; // a widget
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.dXk; // a resizable widget: the group's lane
import bitwig.obf.foz; // a value listener
import bitwig.obf.FVE; // a group's display settings
import bitwig.obf.iok; // a colour
import bitwig.obf.OpO; // the height rule of the folder button
import bitwig.obf.rMe; // an observable value
import bitwig.obf.ujq; // an observable number
import bitwig.obf.VGL; // a value-changed event
import bitwig.obf.w3; // Bitwig's standard widget widths
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Settings;
import com.bitwig.base.geom.Dgc; // a widget's alignment
import com.bitwig.base.geom.DlD; // a clip rectangle
import com.bitwig.flt.document.core.iface.kWY; // a group track
import com.bitwig.flt.document.core.iface.WSA; // a track
import com.bitwig.flt.widget.core.timeline.BAM; // a group track's arranger timeline
import com.bitwig.graphics.SYo; // a graphics context
import com.bitwig.graphics.WzZ; // a graphics context that transforms colours
import com.bitwig.ramona.core.lBQ; // a boolean value
import com.bitwig.ramona.core.pCj; // a bindable number
import com.bitwig.ramona.core.uzc; // a bindable value
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * While a group track is expanded, its arranger lane uses a compact height that can't be resized,
 * and the summary of its child clips is drawn faded. Collapsed groups keep Bitwig's own height,
 * resizing and summary. The two values are set on the Settings > Mods page
 * ({@link CompactExpandedGroupsSettings}) and kept in the mod's settings. A chevron next to the
 * folder button shows an expanded group at its stored height; that choice is kept per group until
 * the project closes.
 */
public final class BmlCompactExpandedGroups {
    static final int MIN_HEIGHT = 14, MAX_HEIGHT = 64, DEFAULT_HEIGHT = 21, DEFAULT_OPACITY = 15;

    private static final Bml.Mod MOD = Bml.mod("compact-expanded-groups");
    private static final Settings STORE = MOD.settings();
    private static final String HEIGHT_KEY = "height_px", OPACITY_KEY = "opacity_percent";

    private static volatile int compactHeight =
        clamp(STORE.getInt(HEIGHT_KEY, DEFAULT_HEIGHT), MIN_HEIGHT, MAX_HEIGHT);
    private static volatile int expandedSummaryPercent =
        clamp(STORE.getInt(OPACITY_KEY, DEFAULT_OPACITY), 0, 100);

    /** Notifies the bound group heights when the compact height changes. */
    private static final HeightSetting HEIGHT_SETTING = new HeightSetting();
    private static final cNP SUMMARY_FADE = (r, g, b, a) ->
        new double[]{r, g, b, a * (expandedSummaryPercent / 100.0)};

    /** The group and the lane of the binding being made: set and used within one call of btu.QOl. */
    private static final ThreadLocal<kWY> HEIGHT_GROUP = new ThreadLocal<>();
    private static final ThreadLocal<dXk> HEIGHT_LANE = new ThreadLocal<>();

    /** Whether Bitwig made each lane resizable, read before the mod first binds it. */
    private static final Map<dXk, Boolean> NATIVE_RESIZABLE =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Which groups are shown at full height. Never saved: Bitwig's document isn't touched. */
    private static final Map<kWY, FullHeight> FULL_HEIGHT =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** The timelines that painted an expanded group, to repaint when the opacity changes. Weak: never extends their lifetime. */
    private static final Map<BAM, Boolean> GROUP_TIMELINES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private BmlCompactExpandedGroups() {}

    // ---------------------------------------------------------------------
    // Hooks
    // ---------------------------------------------------------------------

    /** The group lane's height binding reads the group's display settings: note them for the binding below. */
    @Hook(target = btu.class, method = "QOl", at = At.REPLACE_CALL, callOwner = kWY.class, call = "nfq",
          id = "capture-group-display-settings")
    public static FVE captureGroupDisplaySettings(final kWY group) {
        final FVE settings = group == null ? null : group.nfq();
        HEIGHT_GROUP.set(settings == null ? null : group);
        return settings;
    }

    /** The lane whose height is bound next: note it, so its resizing follows the compact height. */
    @Hook(target = btu.class, method = "QOl", at = At.REPLACE_CALL, callOwner = dXk.class, call = "qFI",
          id = "capture-group-lane")
    public static pCj captureGroupLane(final dXk lane) {
        HEIGHT_LANE.set(lane);
        return lane.qFI(); // the lane's height
    }

    /**
     * Binds the lane height. Bitwig's stored height is kept; only the bound value becomes compact
     * while the group is expanded and not shown at full height. The lane can't be resized meanwhile.
     */
    @Hook(target = btu.class, method = "QOl", at = At.REPLACE_CALL, callOwner = btu.class, call = "cfM",
          id = "bind-expanded-compact-height")
    public static void bindGroupHeight(final uzc destination, final rMe nativeHeightSource, final boolean flag) {
        final kWY group = HEIGHT_GROUP.get();
        final dXk lane = HEIGHT_LANE.get();
        HEIGHT_GROUP.remove();
        HEIGHT_LANE.remove();
        if (group == null) {
            dns.cfM(destination, nativeHeightSource, flag);
            return;
        }
        final lBQ expanded = group.nfq().FzB();
        final FullHeight full = fullHeight(group);
        // Bitwig's lane widget casts its height's source to a number when it writes the height back, so a
        // numeric source gets a numeric wrapper. In 6.1.4 both of Bitwig's sources here are numeric.
        dns.cfM(destination, nativeHeightSource instanceof ujq numeric
            ? new NumericCompactHeightValue(numeric, expanded, full)
            : new CompactHeightValue(nativeHeightSource, expanded, full), flag);
        if (lane != null) bindResizing(lane, expanded, full);
    }

    /**
     * The lane's "vertical resizing enabled": off while the compact height shows, else as Bitwig set
     * it. The lane keeps the bound height while resizing is off (Bitwig's default for its lanes).
     */
    private static void bindResizing(final dXk lane, final lBQ expanded, final FullHeight full) {
        try {
            final boolean nativeResizable = NATIVE_RESIZABLE.computeIfAbsent(lane, dXk::bkb);
            dns.cfM(lane.lDU(), new ResizableValue(nativeResizable, expanded, full), true);
        } catch (Throwable t) {
            MOD.log("The compact lane stays resizable", t);
        }
    }

    /**
     * The header's folder button is finished by this last call of its builder (only group tracks
     * get here): put the full-height chevron next to it.
     */
    @Hook(target = btF.class, method = "FzB(Lcom/bitwig/flt/document/core/iface/WSA;)Ldve;",
          at = At.REPLACE_CALL, callOwner = btF.class, call = "cfM(Lcom/bitwig/flt/document/core/iface/WSA;Ldve;)Ldve;",
          id = "add-full-height-button")
    public static dve addFullHeightButton(final btF header, final WSA track, final dve widget) {
        final dve folder = header.cfM(track, widget); // Bitwig's own last step
        try {
            return track instanceof kWY group ? withFullHeightButton(folder, group) : folder;
        } catch (Throwable t) {
            MOD.log("Could not add the full height button", t);
            return folder;
        }
    }

    /** The same size, alignment and binding as the folder button; shown only while expanded. */
    private static dve withFullHeightButton(final dve folder, final kWY group) {
        final FullHeight full = fullHeight(group);
        final dAd button = dAd.gG(); // a plain button
        button.MRY(w3.cfM); // the folder button's width
        button.a_(OpO.cfM()); // the folder button's height
        button.iHb("Borderless"); // Bitwig's style without state icons: hover and press keep the chevron
        full.show(button);
        button.z3I("Show the expanded group at its full track height"); // the status bar text
        button.cfM(Dgc.IwV); // the folder button's alignment
        dns.cfM(button.LIK(), new FullHeightClick(full)); // what a click does
        dns.cfM(button.DQO(), group.nfq().FzB()); // visible while expanded
        return dWb.cfM(0, new dve[] {folder, button}); // a row with no gap
    }

    /** The group timeline paints its child-clip summary twice (two passes); both go through here. */
    @Hook(target = BAM.class, method = "Hnt(Lcom/bitwig/graphics/SYo;DDLcom/bitwig/base/geom/DlD;)V",
          at = At.REPLACE_CALL, callOwner = BAM.class, call = "cfM", occurrence = 1,
          id = "fade-expanded-group-summary-1")
    public static void paintGroupChildSummary1(
            final BAM timeline, final SYo graphics, final kWY group,
            final double start, final double end, final DlD clip, final boolean pass) {
        paintGroupChildSummary(timeline, graphics, group, start, end, clip, pass);
    }

    @Hook(target = BAM.class, method = "Hnt(Lcom/bitwig/graphics/SYo;DDLcom/bitwig/base/geom/DlD;)V",
          at = At.REPLACE_CALL, callOwner = BAM.class, call = "cfM", occurrence = 2,
          id = "fade-expanded-group-summary-2")
    public static void paintGroupChildSummary2(
            final BAM timeline, final SYo graphics, final kWY group,
            final double start, final double end, final DlD clip, final boolean pass) {
        paintGroupChildSummary(timeline, graphics, group, start, end, clip, pass);
    }

    /**
     * Calls Bitwig's own summary painter without changing the document. Only the graphics context
     * changes, and only while this group is expanded: at 100 % nothing is wrapped, at 0 % the
     * summary isn't painted at all.
     */
    private static void paintGroupChildSummary(
            final BAM timeline, final SYo graphics, final kWY group,
            final double start, final double end, final DlD clip, final boolean pass) {
        final FVE settings = group == null ? null : group.nfq();
        if (settings == null || !settings.FzB().getBoolValue()) { // collapsed: Bitwig's own
            timeline.cfM(graphics, group, start, end, clip, pass);
            return;
        }
        GROUP_TIMELINES.put(timeline, Boolean.TRUE);
        final int percent = expandedSummaryPercent;
        if (percent <= 0) return;
        // Bitwig's colour-transform decorator: the painter still draws exactly the same content.
        timeline.cfM(percent >= 100 ? graphics : new AlphaGraphics(graphics), group, start, end, clip, pass);
    }

    /**
     * The decorator transforms colours set through the RGBA and pattern paths. Its separate
     * colour-object path forwards straight to the wrapped context, so fade that one here.
     */
    private static final class AlphaGraphics extends WzZ {
        AlphaGraphics(final SYo base) { super(base, SUMMARY_FADE); }
        @Override public void MRY(final iok colour) { // set the colour
            super.MRY(colour.cfM((float) (colour.hUR() * (expandedSummaryPercent / 100.0)))); // with alpha
        }
    }

    private static FullHeight fullHeight(final kWY group) {
        return FULL_HEIGHT.computeIfAbsent(group, unused -> new FullHeight());
    }

    /** Bitwig's icon path as its widgets name icons: relative to the icons folder. */
    private static String iconName(final String resourceId) {
        return MOD.bitwigResource(resourceId).substring("icons/".length());
    }

    /** One group's full-height switch and the chevrons that show it. Holds nothing of the group itself. */
    private static final class FullHeight extends bDS {
        private final List<WeakReference<dAd>> buttons = new ArrayList<>();

        FullHeight() { super(false); }

        private String icon() { return iconName(getBoolValue() ? "chevron_down" : "chevron_right"); }

        /** A header can be rebuilt while the group lives on: every new button starts with the current icon. */
        synchronized void show(final dAd button) {
            buttons.removeIf(reference -> reference.get() == null);
            buttons.add(new WeakReference<>(button));
            button.TMF(icon()); // the button's icon
        }

        @Override public void setBoolValue(final boolean value) {
            final boolean changed = value != getBoolValue();
            super.setBoolValue(value); // the bound lane heights follow
            if (!changed) return;
            synchronized (this) {
                for (WeakReference<dAd> reference : buttons) {
                    final dAd button = reference.get();
                    if (button != null) button.TMF(icon());
                }
            }
        }
    }

    /**
     * The button's value: always off, so the button keeps drawing the chevron with no checked look.
     * A click turns it on through the binding: that flips the switch, and the event sets the button back.
     */
    private static final class FullHeightClick extends bDS {
        private final FullHeight full;

        FullHeightClick(final FullHeight full) {
            super(false);
            this.full = full;
        }

        @Override public void setBoolValue(final boolean value) {
            if (!value) return;
            full.setBoolValue(!full.getBoolValue());
            fireValueChanged(this, Boolean.FALSE, Boolean.TRUE);
        }
    }

    private static void repaintGroupTimelines() {
        final BAM[] snapshot;
        synchronized (GROUP_TIMELINES) {
            snapshot = GROUP_TIMELINES.keySet().toArray(new BAM[0]);
        }
        // The repaint the timeline's own group-model listener requests.
        for (BAM timeline : snapshot) timeline.qlO();
    }

    // ---------------------------------------------------------------------
    // Settings, read and written by CompactExpandedGroupsSettings
    // ---------------------------------------------------------------------

    static int height() {
        return compactHeight;
    }

    static int opacity() {
        return expandedSummaryPercent;
    }

    static void setHeight(final double proposed) {
        final int before = compactHeight, next = clamp((int) Math.round(proposed), MIN_HEIGHT, MAX_HEIGHT);
        if (before == next) return;
        compactHeight = next;
        STORE.putInt(HEIGHT_KEY, next);
        HEIGHT_SETTING.changed(before, next);
    }

    static void setOpacity(final double proposed) {
        final int next = clamp((int) Math.round(proposed), 0, 100);
        if (expandedSummaryPercent == next) return;
        expandedSummaryPercent = next;
        STORE.putInt(OPACITY_KEY, next);
        repaintGroupTimelines();
    }

    private static int clamp(final int value, final int min, final int max) { return Math.max(min, Math.min(max, value)); }

    /** Whether the compact height shows: the group is expanded and not shown at full height. */
    private static boolean compact(final lBQ expanded, final FullHeight full) {
        return expanded.getBoolValue() && !full.getBoolValue();
    }

    /** The compact height as an observable value, so bound group heights update when it changes. */
    private static final class HeightSetting implements rMe {
        private final CopyOnWriteArrayList<foz> listeners = new CopyOnWriteArrayList<>();

        void changed(final int before, final int next) {
            if (listeners.isEmpty()) return;
            // Bitwig's listeners cast the event to its value-changed event: never pass the value itself.
            final VGL event = new VGL(this, Integer.valueOf(before), Integer.valueOf(next));
            for (foz listener : listeners) listener.eventOccurred(event);
        }

        @Override public Object getValue() { return Integer.valueOf(compactHeight); }
        @Override public boolean isSettable() { return false; }
        @Override public void setValue(final Object value) { }
        @Override public boolean isObservable() { return true; }
        @Override public void addListener(final foz listener) { if (listener != null) listeners.addIfAbsent(listener); }
        @Override public void removeListener(final foz listener) { listeners.remove(listener); }
    }

    /** The lane's resizing: Bitwig's own, except off while the compact height shows. Read-only. */
    private static final class ResizableValue implements rMe {
        private final boolean nativeResizable;
        private final lBQ expanded;
        private final FullHeight full;

        ResizableValue(final boolean nativeResizable, final lBQ expanded, final FullHeight full) {
            this.nativeResizable = nativeResizable;
            this.expanded = expanded;
            this.full = full;
        }

        @Override public Object getValue() { return Boolean.valueOf(nativeResizable && !compact(expanded, full)); }
        @Override public boolean isSettable() { return false; }
        @Override public void setValue(final Object value) { }
        @Override public boolean isObservable() { return true; }
        @Override public void addListener(final foz listener) {
            expanded.addListener(listener);
            full.addListener(listener);
        }
        @Override public void removeListener(final foz listener) {
            expanded.removeListener(listener);
            full.removeListener(listener);
        }
    }

    /**
     * The lane height: the compact height while the group is expanded and not at full height, else
     * Bitwig's own. While the compact height shows, writes leave Bitwig's stored height alone: the lane
     * widget writes back the height it is laid out at, and that is the compact one.
     */
    private static class CompactHeightValue implements rMe {
        private final rMe nativeSource;
        private final lBQ expanded;
        private final FullHeight full;

        CompactHeightValue(final rMe nativeSource, final lBQ expanded, final FullHeight full) {
            this.nativeSource = nativeSource;
            this.expanded = expanded;
            this.full = full;
        }

        final boolean compact() { return BmlCompactExpandedGroups.compact(expanded, full); }

        @Override public Object getValue() {
            return compact() ? Integer.valueOf(compactHeight) : nativeSource.getValue();
        }
        @Override public boolean isSettable() { return nativeSource.isSettable(); }
        @Override public void setValue(final Object value) { if (!compact()) nativeSource.setValue(value); }
        @Override public boolean isObservable() { return nativeSource.isObservable() || expanded.isObservable(); }
        @Override public void addListener(final foz listener) {
            nativeSource.addListener(listener);
            expanded.addListener(listener);
            full.addListener(listener);
            HEIGHT_SETTING.addListener(listener);
        }
        @Override public void removeListener(final foz listener) {
            nativeSource.removeListener(listener);
            expanded.removeListener(listener);
            full.removeListener(listener);
            HEIGHT_SETTING.removeListener(listener);
        }
    }

    /**
     * The same for Bitwig's numeric height. The lane widget's height setter (dXn.IwV) casts the source
     * to a number and writes through it; a plain value made it throw (resizing, reordering, the action).
     */
    private static final class NumericCompactHeightValue extends CompactHeightValue implements ujq {
        private final ujq nativeNumber;

        NumericCompactHeightValue(final ujq nativeNumber, final lBQ expanded, final FullHeight full) {
            super(nativeNumber, expanded, full);
            this.nativeNumber = nativeNumber;
        }

        @Override public Number getMinValue() { return nativeNumber.getMinValue(); }
        @Override public Number getMaxValue() { return nativeNumber.getMaxValue(); }
        @Override public int getIntegerValue() { return compact() ? compactHeight : nativeNumber.getIntegerValue(); }
        @Override public long getLongValue() { return compact() ? compactHeight : nativeNumber.getLongValue(); }
        @Override public double getDoubleValue() { return compact() ? compactHeight : nativeNumber.getDoubleValue(); }
        @Override public void setIntegerValue(final int value) { if (!compact()) nativeNumber.setIntegerValue(value); }
        @Override public void setLongValue(final long value) { if (!compact()) nativeNumber.setLongValue(value); }
        @Override public void setFloatValue(final float value) { if (!compact()) nativeNumber.setFloatValue(value); }
        @Override public void setDoubleValue(final double value) { if (!compact()) nativeNumber.setDoubleValue(value); }
    }
}
