package com.bitwig.mods.alllanesview;

import java.util.Map;

import bitwig.obf.OJv; // a Bitwig (native) device
import bitwig.obf.R1Z; // a Bitwig device's contents: its type, chains and expanded panel
import bitwig.obf.aBW; // a main window's controller (handles the global device shortcuts)
import bitwig.obf.aDr; // opens a dockable dialog in its own window
import bitwig.obf.dAd; // a button
import bitwig.obf.dns; // binds a widget property to a model value
import bitwig.obf.doF; // where a dialog is shown: docked in a panel or in its own window
import bitwig.obf.dpN; // Bitwig's application (event loop, modifier keys)
import bitwig.obf.dve; // a widget
import bitwig.obf.jsd; // a device
import bitwig.obf.rMe; // a value a widget property can be bound to
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.app.CHR; // the application
import com.bitwig.flt.app.TL; // the application: decides where expanded device views dock
import com.bitwig.flt.document.core.master.device.SaG; // a Bitwig device's contents (the class)
import com.bitwig.flt.document.core.master.device.xlG; // a device in the document (its name)
import com.bitwig.flt.widget.core.device.TIh; // a Bitwig device's header strip

import com.bitwig.ramona.core.uzc; // a widget property that can be bound
import com.google.common.collect.MapMaker;

/**
 * Device: All Lanes View.
 *
 * Listed devices ({@link ListedDevices}) get Bitwig's expanded-view button in their header, and
 * Bitwig's "Toggle Expanded Device View of Selected Device" opens and closes their All Lanes view
 * ({@link LanesView}). It is shown where Bitwig shows The Grid's expanded view: docked from the
 * device panel, in its own window when undocked or opened from elsewhere. Every other device keeps
 * Bitwig's behaviour.
 */
public final class AllLanesView {
    static final Bml.Mod MOD = Bml.mod("all-lanes-view");

    /** The open view of each device. */
    private static final Map<R1Z, LanesView> OPEN = new MapMaker().weakKeys().makeMap();
    /** The device headers showing the button, and the device each shows. */
    private static final Map<TIh, R1Z> HEADERS = new MapMaker().weakKeys().makeMap();

    private AllLanesView() {
    }

    /** Whether the mod handles this device: listed, and Bitwig has no expanded view for it. */
    static boolean handles(R1Z device) {
        return device != null && device.vpw() == null && ListedDevices.listed(device); // vpw: its expanded panel
    }

    // --- H1: the header button ---

    /**
     * A device header was bound to its device. Bitwig hides its expanded-view button on devices
     * without an expanded panel; on a listed device, show it and point it at the All Lanes view.
     * Bitwig's own detach (TIh.MRY(qnQ)) clears the click and unbinds the button.
     */
    @Hook(target = TIh.class, method = "cfM(LqnQ;)V", at = At.BEFORE_RETURN, id = "header-button")
    public static void headerBound(@This TIh header) {
        try {
            // The device it was bound to, as Bitwig's own TIh.wZs() finds it. (The method's argument
            // isn't live at its return, so a BEFORE_RETURN hook can't take it.)
            OJv shown = header.Bpn();
            R1Z device = shown != null ? shown.UNj() : null; // its contents
            if (!handles(device)) return;
            dAd button = header.TtY; // Bitwig's expanded-view button in the header
            dns.cfM(button.DQO(), header.ifN()); // visible as Bitwig's is: while the header shows its buttons
            button.Hnt(() -> toggle(header, device)); // its click
            button.LIK().setBoolValue(isShown(device)); // lit while the view is shown
            HEADERS.put(header, device);
        } catch (Throwable t) {
            MOD.log("could not set up the header button", t);
        }
    }

    // --- H2, H3: Bitwig's "Toggle Expanded Device View of Selected Device" ---

    /** The shortcut with the device panel focused (Bitwig's own header button also comes here). */
    @Hook(target = TIh.class, method = "wZs()V", at = At.REPLACE_CALL, id = "shortcut-device-panel",
          callOwner = TIh.class, call = "cfM(Ldve;LR1Z;)V", occurrence = 1)
    public static void shortcutInPanel(dve anchor, R1Z device) {
        if (handlesSafely(device)) {
            toggle(anchor, device);
            return;
        }
        TIh.cfM(anchor, device); // Bitwig's: toggles the device's expanded panel
    }

    /** The shortcut anywhere else: the selected track's selected device. */
    @Hook(target = aBW.class, method = "cfM(LiKU;)V", at = At.REPLACE_CALL, id = "shortcut-global",
          callOwner = jsd.class, call = "xcy()V", occurrence = 1)
    public static void shortcutGlobal(jsd device) {
        R1Z contents = device instanceof OJv nativeDevice ? nativeDevice.UNj() : null; // a Bitwig device's contents
        if (handlesSafely(contents)) {
            toggle(null, contents);
            return;
        }
        device.xcy(); // Bitwig's: shows or hides the device's window
    }

    // --- H4: the device removed ---

    /** Bitwig closes a device's expanded view when the device leaves the project; so does the mod. */
    @Hook(target = SaG.class, method = "cfM(Lcom/bitwig/flt/document/core/master/g2L;Z)V", at = At.ENTRY,
          id = "close-on-device-removed")
    public static void deviceDetached(@This SaG device, @Arg(1) boolean closing) {
        if (!closing) return;
        try {
            LanesView view = OPEN.get(device);
            if (view != null) view.Hnt(); // close it
        } catch (Throwable t) {
            MOD.log("could not close the view", t);
        }
    }

    private static boolean handlesSafely(R1Z device) {
        try {
            return handles(device);
        } catch (Throwable t) {
            MOD.log("could not check the device", t);
            return false;
        }
    }

    // --- opening and closing ---

    private static boolean isShown(R1Z device) {
        LanesView view = OPEN.get(device);
        return view != null && view.Pfd(); // shown
    }

    /**
     * Opens or closes the device's view. Where it opens follows Bitwig's expanded device view
     * (com.bitwig.flt.app.TL's handler for the expanded-panel flag): docked from the device panel,
     * in its own window when undocked (Shift inverts the choice, as in Bitwig) or when there is no
     * widget to dock from (the shortcut outside the device panel).
     */
    static void toggle(dve anchor, R1Z device) {
        try {
            LanesView view = OPEN.get(device);
            if (view != null && view.Pfd()) {
                view.Hnt(); // close
                return;
            }
            if (view != null) {
                OPEN.remove(device);
                view.iy_();
            }
            view = new LanesView(device);
            OPEN.put(device, view);
            jsd d = device.yKX(); // the device
            if (d instanceof xlG named) dns.cfM((uzc) view.Txc(), (rMe) named.mqM()); // its title: the device's name, as Bitwig's
            TL app = (TL) CHR.Grk();
            boolean shift = dpN.TVZ().Tfs().cfM(); // Shift held
            boolean undocked = shift != view.storedUndocked();
            doF where = undocked || anchor == null ? aDr.cfM : app.cfM(anchor, view); // own window, or docked
            dve owner = anchor != null ? anchor : app.SQN(); // the main window
            view.cfM(owner, where); // show it
        } catch (Throwable t) {
            MOD.log("could not open or close the All Lanes view", t);
        }
    }

    /** The view was shown or hidden: light the header buttons that show its device. */
    static void shownChanged(R1Z device) {
        boolean shown = isShown(device);
        for (Map.Entry<TIh, R1Z> e : HEADERS.entrySet()) {
            if (e.getValue() != device) continue;
            TIh header = e.getKey();
            try {
                OJv current = header.Bpn(); // the device the header shows now
                if (current == null || current.UNj() != device) {
                    HEADERS.remove(header);
                    continue;
                }
                header.TtY.LIK().setBoolValue(shown);
            } catch (Throwable t) {
                MOD.log("could not light the button", t);
            }
        }
    }

    static void disposed(LanesView view) {
        OPEN.remove(view.device, view);
        dpN.TVZ().runAtEndOfCurrentEvent(() -> shownChanged(view.device));
    }
}
