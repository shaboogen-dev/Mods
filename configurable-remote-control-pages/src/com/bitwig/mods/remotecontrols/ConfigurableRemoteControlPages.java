package com.bitwig.mods.remotecontrols;

import bitwig.obf.CSG; // something that owns Remote Controls pages
import bitwig.obf.FyM; // a Remote Controls page
import bitwig.obf.NBQ; // what a Remote Controls chooser shows: a device's pages
import bitwig.obf.VGL; // a value-change event
import bitwig.obf.aJc; // the Device Inspector panel
import bitwig.obf.dAa; // a compact icon button
import bitwig.obf.dDE; // a page source with a device's own pages
import bitwig.obf.dWb; // a group of widgets
import bitwig.obf.deH; // an integer property of a schema
import bitwig.obf.deN; // a property of a schema
import bitwig.obf.deS; // a schema: the properties of one kind of object
import bitwig.obf.dve; // a widget
import bitwig.obf.foz; // a value listener
import bitwig.obf.jsd; // a device
import bitwig.obf.ujq; // a property value the Inspector can show
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.document.core.master.device.WSA; // the device schema
import com.bitwig.flt.widget.core.device.FUR; // the Remote Controls page chooser
import com.bitwig.flt.widget.core.device.VA; // a rendered Remote Controls page
import com.bitwig.flt.widget.core.device.yPU; // the Remote Controls content panel
import com.bitwig.ramona.core.OLR; // an object with a schema
import com.bitwig.ramona.core.oDc; // an object with a schema, as the property calls take it
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A per-device "Remote Pages Visible" property (1 to 3), stored with the project like Bitwig's
 * own device properties, and a Remote Controls panel that shows that many pages side by side.
 *
 * The property is an integer property added to Bitwig's device schema. Its value is held here:
 * the six calls through which Bitwig's integer property reads, writes and watches a value are
 * routed to this class for this property, and go to Bitwig's schema as before for every other.
 */
public final class ConfigurableRemoteControlPages {
    static final Bml.Mod MOD = Bml.mod("configurable-remote-control-pages");

    private static final String PROPERTY_NAME = "remote_pages_visible";
    private static final int PROPERTY_ID = 16001;
    private static final int PROPERTY_FLAGS = 410;
    private static final int DEFAULT_VALUE = 1;
    private static final int MAX_VALUE = 3;

    private static final List<ValueEntry> VALUES = new ArrayList<>();
    private static final List<ListenerEntry> LISTENERS = new ArrayList<>();
    private static final List<ChooserEntry> CHOOSERS = new ArrayList<>();
    private static final ThreadLocal<List<?>> SCHEMA_PROPERTIES = new ThreadLocal<>();
    private static final ThreadLocal<FUR> INITIALIZING_CHOOSER = new ThreadLocal<>();
    private static final ThreadLocal<jsd> INSPECTED_DEVICE = new ThreadLocal<>();
    private static final ThreadLocal<RenderContext> RENDER_CONTEXT = new ThreadLocal<>();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile deH descriptor;
    /** The property is in the device schema: only then are ID 16001's calls routed here. */
    private static volatile boolean registered;
    /** Randomise Remote Controls' publish method once looked up; NOT_INSTALLED when it isn't there. */
    private static volatile Object randomizerPublish;
    private static final Object NOT_INSTALLED = new Object();
    private static volatile dve lastLaidOutPage;

    private ConfigurableRemoteControlPages() {
    }

    // ---- The property in the device schema --------------------------------------------------

    /** Bitwig lists the device schema's properties: note the list. */
    @Hook(target = WSA.class, method = "cfM(Ljava/util/List;)V", at = At.ENTRY,
          id = "begin-register-remote-pages-descriptor")
    public static void beginDescriptorAppend(@Arg(0) List<?> properties) {
        SCHEMA_PROPERTIES.set(properties);
    }

    /** After Bitwig's own properties: add ours at the end. */
    @Hook(target = WSA.class, method = "cfM(Ljava/util/List;)V", at = At.BEFORE_RETURN,
          id = "register-remote-pages-descriptor")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void registerDescriptor() {
        List properties = SCHEMA_PROPERTIES.get();
        SCHEMA_PROPERTIES.remove();
        if (properties == null) return;
        try {
            if (descriptor == null) {
                deH created = new deH(PROPERTY_NAME, PROPERTY_ID, WSA.IwV, DEFAULT_VALUE, PROPERTY_FLAGS, 1, MAX_VALUE); // name, ID, schema, default, flags, min, max
                created.cfM("not_engine", Boolean.TRUE); // an attribute: the audio engine doesn't need it
                descriptor = created;
            }
            for (Object p : properties) {
                if (p == descriptor) return;
                if (p instanceof deN other && other.E3() == PROPERTY_ID) { // E3(): its ID
                    logOnce("id-taken", "Bitwig's device schema already has a property with ID " + PROPERTY_ID
                            + "; Remote Pages Visible is not added and every device shows one page", null);
                    return;
                }
            }
            properties.add(descriptor);
            descriptor.nO_(); // finish its setup
            registered = true;
        } catch (Throwable t) {
            logOnce("descriptor", "registering the Remote Pages Visible property failed", t);
        }
    }

    /** The property's value is read. */
    @Hook(target = deH.class, method = "lYc(Lcom/bitwig/ramona/core/oDc;)I", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "cfM(Lcom/bitwig/ramona/core/oDc;I)I", occurrence = 1,
          id = "route-int-get")
    public static int getInt(deS schema, oDc object, int id) {
        if (!ours(schema, id)) return schema.cfM(object, id);
        return stored(object);
    }

    /** The property's value is set while loading or copying (no listeners told). */
    @Hook(target = deH.class, method = "cfM(Lcom/bitwig/ramona/core/oDc;I)V", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "cfM(Lcom/bitwig/ramona/core/oDc;II)V", occurrence = 1,
          id = "route-int-raw-set")
    public static void rawSetInt(deS schema, oDc object, int id, int value) {
        if (!ours(schema, id)) {
            schema.cfM(object, id, value);
            return;
        }
        store(object, value, false);
    }

    /** The property's value is set by the user (listeners told). */
    @Hook(target = deH.class, method = "MRY(Lcom/bitwig/ramona/core/oDc;I)V", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "Hnt(Lcom/bitwig/ramona/core/OLR;II)V", occurrence = 1,
          id = "route-int-set")
    public static void setInt(deS schema, OLR object, int id, int value) {
        if (!ours(schema, id)) {
            schema.Hnt(object, id, value);
            return;
        }
        store(object, value, true);
    }

    /** The property's value is set by the user, the other way Bitwig does it (listeners told). */
    @Hook(target = deH.class, method = "Hnt(Lcom/bitwig/ramona/core/oDc;I)V", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "MRY(Lcom/bitwig/ramona/core/OLR;II)V", occurrence = 1,
          id = "route-int-set-other")
    public static void setIntOther(deS schema, OLR object, int id, int value) {
        if (!ours(schema, id)) {
            schema.MRY(object, id, value);
            return;
        }
        store(object, value, true);
    }

    /** A listener starts watching the property. */
    @Hook(target = deH.class, method = "cfM(Lcom/bitwig/ramona/core/oDc;Lfoz;)V", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "eLY", occurrence = 1, id = "route-int-add-listener")
    public static void addIntListener(deS schema, OLR object, int id, foz listener) {
        if (!ours(schema, id)) {
            schema.eLY(object, id, listener);
            return;
        }
        if (object == null || listener == null) return;
        synchronized (LISTENERS) {
            purgeListeners();
            for (ListenerEntry entry : LISTENERS) {
                if (entry.object.get() == object && entry.listener.get() == listener) return;
            }
            LISTENERS.add(new ListenerEntry(object, listener));
        }
    }

    /** A listener stops watching the property. */
    @Hook(target = deH.class, method = "MRY(Lcom/bitwig/ramona/core/oDc;Lfoz;)V", at = At.REPLACE_CALL,
          callOwner = deS.class, call = "wGc", occurrence = 1, id = "route-int-remove-listener")
    public static void removeIntListener(deS schema, OLR object, int id, foz listener) {
        if (!ours(schema, id)) {
            schema.wGc(object, id, listener);
            return;
        }
        synchronized (LISTENERS) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                ListenerEntry entry = LISTENERS.get(i);
                Object o = entry.object.get();
                Object l = entry.listener.get();
                if (o == null || l == null || o == object && l == listener) LISTENERS.remove(i);
            }
        }
    }

    /** Our property: its ID in the device schema (or a schema built on it), once it is registered. */
    private static boolean ours(deS schema, int id) {
        return id == PROPERTY_ID && registered && schema instanceof WSA;
    }

    /** How many Remote Controls pages the device shows side by side (1 to 3). */
    public static int getVisiblePageCount(Object device) {
        return stored(device);
    }

    private static int stored(Object device) {
        if (device == null) return DEFAULT_VALUE;
        synchronized (VALUES) {
            for (int i = VALUES.size() - 1; i >= 0; i--) {
                ValueEntry entry = VALUES.get(i);
                Object d = entry.device.get();
                if (d == null) VALUES.remove(i);
                else if (d == device) return entry.value;
            }
        }
        return DEFAULT_VALUE;
    }

    private static void store(Object device, int value, boolean notify) {
        if (device == null) return;
        int next = clamp(value);
        int previous = stored(device);
        synchronized (VALUES) {
            boolean found = false;
            for (int i = VALUES.size() - 1; i >= 0; i--) {
                ValueEntry entry = VALUES.get(i);
                Object d = entry.device.get();
                if (d == null) {
                    VALUES.remove(i);
                } else if (d == device) {
                    entry.value = next;
                    found = true;
                }
            }
            if (!found) VALUES.add(new ValueEntry(device, next));
        }
        if (previous == next) return;
        if (notify) notifyListeners(device, previous, next);
        refreshRemoteControls(device);
    }

    private static void notifyListeners(Object device, int oldValue, int newValue) {
        List<foz> targets = new ArrayList<>();
        synchronized (LISTENERS) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                ListenerEntry entry = LISTENERS.get(i);
                Object o = entry.object.get();
                foz l = entry.listener.get();
                if (o == null || l == null) LISTENERS.remove(i);
                else if (o == device) targets.add(l);
            }
        }
        for (foz listener : targets) {
            try {
                listener.eventOccurred(new VGL(null, oldValue, newValue)); // source, old value, new value
            } catch (Throwable t) {
                logOnce("listener", "a Remote Pages Visible listener failed", t);
            }
        }
    }

    private static void purgeListeners() {
        LISTENERS.removeIf(e -> e.object.get() == null || e.listener.get() == null);
    }

    // ---- The row in the Device Inspector -----------------------------------------------------

    /** Bitwig builds the Device block's rows for a device: note the device for our row. */
    @Hook(target = aJc.class, method = "IwV(Ljsd;)Ldve;", at = At.ENTRY, id = "note-inspected-device")
    public static void noteInspectedDevice(@Arg(0) jsd device) {
        INSPECTED_DEVICE.set(device);
    }

    /** The "Remote Pages Visible" row, as Bitwig builds its own number rows (from the Inspector framework). */
    static dve inspectorRow(Object inspectorPanel) {
        jsd device = INSPECTED_DEVICE.get();
        deH d = descriptor;
        if (device == null || d == null || !(inspectorPanel instanceof aJc panel)) {
            throw new IllegalStateException("no device or no Remote Pages Visible property"
                    + " (device " + device + ", property " + d + ")");
        }
        dWb row = panel.Cuu(); // a new row
        panel.cfM(row, (ujq) d.cfM((oDc) device)); // the property's label and number field, bound to its value
        return row;
    }

    // ---- The page chooser: Previous/Next step by the number of pages shown -------------------

    /** The chooser is being set up: note it. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.ENTRY,
          id = "begin-page-chooser-initialize")
    public static void beginChooserInitialize(@This FUR chooser) {
        INITIALIZING_CHOOSER.set(chooser);
    }

    /** Previous: Bitwig's action, run once per page shown. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = dAa.class, call = "Hnt(Ljava/lang/Runnable;)V", occurrence = 1,
          id = "step-previous-page-by-visible-count")
    public static void stepPreviousPage(dAa button, Runnable action) {
        button.Hnt(stepped(action)); // set its click action
    }

    /** Next: the same. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.REPLACE_CALL,
          callOwner = dAa.class, call = "Hnt(Ljava/lang/Runnable;)V", occurrence = 2,
          id = "step-next-page-by-visible-count")
    public static void stepNextPage(dAa button, Runnable action) {
        button.Hnt(stepped(action));
    }

    private static Runnable stepped(Runnable action) {
        if (action == null) return null;
        Object device;
        try {
            FUR chooser = INITIALIZING_CHOOSER.get();
            device = chooser == null ? null : deviceFromChooser(chooser);
        } catch (Throwable t) {
            logOnce("chooser-device", "reading the page chooser's device failed", t);
            return action;
        }
        return () -> {
            int count = getVisiblePageCount(device);
            for (int i = 0; i < count; i++) action.run();
        };
    }

    /** The chooser is set up: remember it for its device. */
    @Hook(target = FUR.class, method = "MRY(Lcom/bitwig/flt/widget/core/device/yPU;)V", at = At.BEFORE_RETURN,
          id = "remember-page-chooser-after-initialize")
    public static void rememberChooserAfterInitialize(@This FUR chooser) {
        INITIALIZING_CHOOSER.remove();
        rememberChooser(chooser);
    }

    /** The chooser shows another page: remember it for its device. */
    @Hook(target = FUR.class, method = "cfM(LFyM;)V", at = At.BEFORE_RETURN,
          id = "remember-page-chooser-after-page-change")
    public static void rememberChooserAfterPageChange(@This FUR chooser) {
        rememberChooser(chooser);
    }

    private static void rememberChooser(FUR chooser) {
        if (chooser == null) return;
        try {
            Object device = deviceFromChooser(chooser);
            if (device == null) return;
            synchronized (CHOOSERS) {
                purgeChoosers();
                for (ChooserEntry entry : CHOOSERS) {
                    if (entry.device.get() == device) {
                        entry.chooser = new WeakReference<>(chooser);
                        return;
                    }
                }
                CHOOSERS.add(new ChooserEntry(device, chooser));
            }
        } catch (Throwable t) {
            logOnce("chooser", "remembering a page chooser failed", t);
        }
    }

    private static Object deviceFromChooser(FUR chooser) {
        NBQ shown = chooser.BDm(); // the device's pages it shows
        return shown == null ? null : shown.lYc(); // their device
    }

    private static void purgeChoosers() {
        CHOOSERS.removeIf(e -> e.device.get() == null || e.chooser.get() == null);
    }

    /** The value changed: have the device's Remote Controls panel build its pages again. */
    private static void refreshRemoteControls(Object device) {
        FUR chooser = null;
        synchronized (CHOOSERS) {
            purgeChoosers();
            for (ChooserEntry entry : CHOOSERS) {
                if (entry.device.get() == device) {
                    chooser = entry.chooser.get();
                    break;
                }
            }
        }
        if (chooser == null) return;
        try {
            yPU panel = chooser.MRY; // the chooser's content panel
            if (panel != null && panel.MRY != null) panel.MRY.MYI(); // its page renderer: build again
        } catch (Throwable t) {
            logOnce("refresh", "rebuilding the Remote Controls panel failed", t);
        }
    }

    // ---- The Remote Controls panel: a block of pages -----------------------------------------

    /** The panel builds the page it shows: note the panel and page. */
    @Hook(target = yPU.class, method = "cfM(LFyM;)Ldve;", at = At.ENTRY, id = "begin-render-configured-page-block")
    public static void beginRenderPage(@This yPU panel, @Arg(0) FyM page) {
        RENDER_CONTEXT.set(new RenderContext(panel, page));
    }

    /**
     * Bitwig built the page: with more than one page to show, return the block of pages it
     * belongs to instead, each built by Bitwig's own page renderer, in a row.
     */
    @Hook(target = yPU.class, method = "cfM(LFyM;)Ldve;", at = At.WRAP_RETURN, id = "render-configured-page-block")
    public static dve wrapRenderedPage(@Return dve rendered) {
        RenderContext context = RENDER_CONTEXT.get();
        RENDER_CONTEXT.remove();
        if (context == null || context.panel == null || context.page == null) return rendered;
        yPU panel = context.panel;
        FyM page = context.page;
        dve template = lastLaidOutPage;
        Object device = null;
        try {
            NBQ source = panel.jFL(); // the device's pages it shows
            device = source == null ? null : source.lYc();
            int count = getVisiblePageCount(device);
            if (count == 1) {
                publishVisible(device, new Object[]{page});
                lastLaidOutPage = rendered;
                return rendered;
            }
            FyM[] block = resolveBlock(source, page, count);
            publishVisible(device, block);
            dve[] widgets = new dve[count];
            dve first = null;
            for (int i = 0; i < count; i++) {
                if (block[i] == null) continue;
                widgets[i] = block[i] == page && rendered != null ? rendered : VA.cfM(block[i], panel); // Bitwig's page renderer
                if (widgets[i] == null) throw new IllegalStateException("stock renderer returned null");
                if (first == null) first = widgets[i];
            }
            dve sizeFrom = template != null ? template : first;
            for (int i = 0; i < count; i++) {
                if (widgets[i] == null) widgets[i] = RemoteControlsBlankPage.sizedLike(sizeFrom);
            }
            if (first != null) lastLaidOutPage = first;
            return dWb.cfM(3, widgets); // a row of widgets
        } catch (Throwable t) {
            logOnce("render", "showing several Remote Controls pages failed; showing one", t);
            publishVisible(device, new Object[]{page});
            lastLaidOutPage = rendered;
            return rendered;
        }
    }

    /** The block of {@code count} pages that holds {@code page}, in chooser order; null past the last page. */
    private static FyM[] resolveBlock(NBQ source, FyM page, int count) {
        count = clamp(count);
        List<FyM> pages = pagesInChooserOrder(source);
        int index = indexOfIdentity(pages, page);
        FyM[] block = new FyM[count];
        if (index < 0) {
            block[0] = page;
            return block;
        }
        int start = index / count * count;
        for (int i = 0; i < count; i++) {
            int at = start + i;
            block[i] = at < pages.size() ? pages.get(at) : null;
        }
        return block;
    }

    /** Every page the chooser lists, in its order: preset pages, then the device's, then the rest. */
    private static List<FyM> pagesInChooserOrder(NBQ source) {
        if (source == null) throw new IllegalStateException("No Remote Controls source");
        List<FyM> pages = new ArrayList<>();
        if (source.hUR() instanceof CSG presetPages) addPages(pages, presetPages.IwV()); // the preset's pages
        addPages(pages, source.cfM());
        if (source instanceof dDE withDevice && withDevice.iHb() instanceof CSG devicePages) addPages(pages, devicePages.IwV()); // the device's own pages
        addPages(pages, source.UNj());
        addPages(pages, source.FzB());
        addPages(pages, source.IwV());
        return pages;
    }

    private static void addPages(List<FyM> out, Collection<?> pages) {
        if (pages == null) return;
        for (Object p : pages) {
            if (p instanceof FyM page) out.add(page);
        }
    }

    private static void addPages(List<FyM> out, Stream<?> pages) {
        if (pages == null) return;
        try (pages) {
            pages.forEachOrdered(p -> {
                if (p instanceof FyM page) out.add(page);
            });
        }
    }

    /**
     * Tells Device: Randomise Remote Controls, when it is installed, which pages are visible, so it
     * randomises all of them. That mod is optional, so it is looked up by its class name.
     */
    private static void publishVisible(Object device, Object[] pages) {
        if (device == null) return;
        try {
            Object publish = randomizerPublish;
            if (publish == null) { // looked up once: the installed mods don't change while Bitwig runs
                try {
                    Class<?> randomizer = Class.forName("com.bitwig.mods.remotecontrols.VisibleRemoteControlsRandomizer",
                            false, device.getClass().getClassLoader());
                    publish = randomizer.getMethod("publishVisiblePagesForDevice", Object.class, Object[].class);
                } catch (ClassNotFoundException notInstalled) {
                    publish = NOT_INSTALLED; // Randomise Remote Controls is not installed
                }
                randomizerPublish = publish;
            }
            if (publish instanceof Method method) method.invoke(null, device, pages);
        } catch (Throwable t) {
            logOnce("publish", "telling Randomise Remote Controls the visible pages failed", t);
        }
    }

    // ---- Small helpers -----------------------------------------------------------------------

    /** The page's position: the same object first, then an equal one. */
    private static int indexOfIdentity(List<FyM> pages, FyM page) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) == page) return i;
        }
        return pages.indexOf(page);
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX_VALUE, value));
    }

    /** Logs a failure once per kind, so a failure on every redraw doesn't flood the log. */
    private static void logOnce(String kind, String message, Throwable t) {
        if (!LOGGED.add(kind)) return;
        if (t != null) MOD.log(message, t);
        else MOD.log(message);
    }

    private static final class ChooserEntry {
        final WeakReference<Object> device;
        WeakReference<FUR> chooser;

        ChooserEntry(Object device, FUR chooser) {
            this.device = new WeakReference<>(device);
            this.chooser = new WeakReference<>(chooser);
        }
    }

    private static final class ListenerEntry {
        final WeakReference<Object> object;
        final WeakReference<foz> listener;

        ListenerEntry(Object object, foz listener) {
            this.object = new WeakReference<>(object);
            this.listener = new WeakReference<>(listener);
        }
    }

    private record RenderContext(yPU panel, FyM page) {
    }

    private static final class ValueEntry {
        final WeakReference<Object> device;
        int value;

        ValueEntry(Object device, int value) {
            this.device = new WeakReference<>(device);
            this.value = value;
        }
    }
}
