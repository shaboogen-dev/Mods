package com.bitwig.mods.exclusivearm;

import bitwig.obf.MoT; // the on/off model behind a track header toggle button (here: arm)
import bitwig.obf.RPX; // base of the track header models; lists the objects a model controls
import bitwig.obf.aNV; // the arranger track header part that holds the arm button
import bitwig.obf.bEe; // the mixer strip header (mute, solo, arm buttons)
import bitwig.obf.dAd; // a toggle button widget
import bitwig.obf.iKU; // a track, as the mixer strip sees it
import bitwig.obf.xJo; // a track in the project (audio, instrument, hybrid, group, effect)
import bitwig.obf.xlf; // the mixer strip's label settings for its buttons
import bml.actions.Clicks;
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.document.core.master.jJs; // the base of every channel that has a record-arm state

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Track: Exclusive Arm. The helper class: the hooks, and the on/off setting.
 *
 * Plain arming: every user path that arms a track (the arm button, the arm shortcut
 * and actions, controllers) goes through the channel's arm setter, which calls
 * jJs.emh with the new state once the track has accepted it. Loading a project and
 * undo set the state without it, so they are left alone.
 *
 * Modifier clicks mirror Bitwig's solo button with Exclusive Solo (bCr/bCs): Shift-click
 * does the opposite of the setting (with it on, it arms without disarming the others;
 * with it off, it toggles exclusively, as the exclusive solo toggle does), and Ctrl-click (Cmd on macOS) always toggles the arm
 * without touching the other tracks. Alt-click is left to Bitwig. Both are registered on the
 * arm buttons of the mixer strip and the arranger track header through Framework: Actions and
 * Menus, which names them in the status bar hint; the plain click stays Bitwig's own, with a
 * hint that follows the setting. The wording follows the solo button's.
 */
public final class ExclusiveArm {
    static final Bml.Mod MOD = Bml.mod("exclusive-arm");

    private static final bml.api.Settings STORE = MOD.settings();
    private static final String KEY_ENABLED = "enabled";

    private static volatile boolean enabled = readEnabled();

    /** True while a modifier click arms tracks itself; the setter hook then stays out of the way. */
    private static final ThreadLocal<Boolean> CLICK_ARMING = ThreadLocal.withInitial(() -> false);

    /** The track whose mixer strip arm button is being built (between the two mixer hooks). */
    private static final ThreadLocal<iKU> MIXER_TRACK = new ThreadLocal<>();

    private ExclusiveArm() {
    }

    /** Before a track's arm state is set: when it is being armed, disarm every track first. */
    @Hook(id = "exclusive-arm", target = jJs.class, method = "emh", at = At.ENTRY)
    public static void beforeArm(@This jJs channel, @Arg(0) boolean arm) {
        try {
            if (!arm || !enabled || CLICK_ARMING.get() || !(channel instanceof xJo track)) {
                return;
            }
            if (track.VpW()) { // already armed: nothing changes
                return;
            }
            track.qLy(); // Bitwig's own "disarm every track in the project" (used by its exclusive-arm command)
        } catch (Throwable t) {
            MOD.log("could not disarm the other tracks", t);
        }
    }

    /** Mixer strip: note the track whose arm button bEe is about to build. */
    @Hook(id = "mixer-arm-button-track", target = bEe.class, method = "MRY(Ldve;LiKU;Lxlf;)LdAd;", at = At.ENTRY)
    public static void beforeMixerArmButton(@Arg(1) iKU track) {
        MIXER_TRACK.set(track);
    }

    /** Mixer strip: give the arm button bEe built its Shift-click and Ctrl-click actions. */
    @Hook(id = "mixer-arm-shift-click", target = bEe.class, method = "MRY(Ldve;LiKU;Lxlf;)LdAd;", at = At.WRAP_RETURN)
    public static dAd afterMixerArmButton(@Return dAd button) {
        iKU track = MIXER_TRACK.get();
        MIXER_TRACK.remove();
        try {
            if (button != null && track != null) {
                addModifierClicks(button, () -> List.of(track));
            }
        } catch (Throwable t) {
            MOD.log("could not add Shift-click to a mixer arm button", t);
        }
        return button;
    }

    /** Arranger track header: build the arm button as Bitwig does, then give it its Shift-click and Ctrl-click actions. */
    @Hook(id = "arranger-arm-shift-click", target = aNV.class, method = "Hnt(LdWb;)V", at = At.REPLACE_CALL,
          callOwner = aNV.class, call = "cfM(Ljava/lang/String;LMoT;)LdAd;", occurrence = 1)
    public static dAd arrangerArmButton(aNV header, String icon, MoT model) {
        dAd button = header.cfM(icon, model); // Bitwig's own toggle button builder, bound to the arm model
        try {
            if (button != null && model instanceof RPX objects) {
                addModifierClicks(button, objects::fg_); // the tracks the arm model controls, read at click time
            }
        } catch (Throwable t) {
            MOD.log("could not add Shift-click to an arranger arm button", t);
        }
        return button;
    }

    private static final String TOGGLE = "Toggle Arm";
    private static final String TOGGLE_EXCLUSIVE = "Toggle Arm (and clear all others)";

    /**
     * Gives the button its modifier clicks and its plain click's hint. They're chosen each time
     * Bitwig asks (on hover and on click), so the status bar hint follows the setting without a
     * restart:
     * Click (Bitwig's own; the setter hook does the rest): on, toggle and clear the others; off, toggle.
     * Shift: on, arm or disarm without touching the others; off, toggle exclusively (the others are cleared either way).
     * Ctrl (Cmd on macOS): arm or disarm without touching the others, in both states.
     */
    private static void addModifierClicks(dAd button, Supplier<List<?>> targets) {
        Clicks.Click exclusive = Clicks.click("exclusive_arm_exclusive_click", TOGGLE_EXCLUSIVE, () -> toggleExclusively(targets.get()));
        Clicks.Click keepOthers = Clicks.click("exclusive_arm_keep_others_click", TOGGLE, () -> toggleKeepingOthers(targets.get()));
        Clicks.hint(button, () -> enabled ? TOGGLE_EXCLUSIVE : TOGGLE);
        Clicks.onModifierClick(button, Clicks.Modifier.SHIFT, () -> enabled ? keepOthers : exclusive);
        Clicks.onModifierClick(button, Clicks.Modifier.SHORTCUT, () -> keepOthers);
    }

    /** Toggles the arm of the given tracks as a plain click does, leaving every other track as it is. */
    static void toggleKeepingOthers(List<?> targets) {
        if (CLICK_ARMING.get()) {
            return;
        }
        CLICK_ARMING.set(true);
        try {
            Boolean arm = null; // the first track decides, so the tracks end up alike
            for (Object o : targets) {
                if (o instanceof jJs channel) {
                    if (arm == null) {
                        arm = !channel.VpW();
                    }
                    channel.TMF(arm); // Bitwig's arm setter: refuses a track that can't be armed
                }
            }
        } catch (Throwable t) {
            MOD.log("could not toggle the arm", t);
        } finally {
            CLICK_ARMING.remove();
        }
    }

    /**
     * Toggles the arm as Bitwig's exclusive solo toggles solo (FNX.cfM(true)): the other tracks are
     * cleared either way. Not armed: arm the given tracks and disarm every other track. Already
     * armed (the first track decides, as for a plain click): disarm every track, these included.
     */
    static void toggleExclusively(List<?> targets) {
        if (CLICK_ARMING.get()) {
            return;
        }
        jJs first = null;
        xJo anyTrack = null;
        for (Object o : targets) {
            if (first == null && o instanceof jJs channel) {
                first = channel;
            }
            if (anyTrack == null && o instanceof xJo track) {
                anyTrack = track;
            }
        }
        if (first == null || !first.VpW()) { // not armed: arm exclusively
            armOnly(targets);
            return;
        }
        CLICK_ARMING.set(true);
        try {
            if (anyTrack != null) {
                anyTrack.qLy(); // Bitwig's own "disarm every track in the project"
            } else {
                for (Object o : targets) {
                    if (o instanceof jJs channel) {
                        channel.TMF(false);
                    }
                }
            }
        } catch (Throwable t) {
            MOD.log("could not disarm the tracks", t);
        } finally {
            CLICK_ARMING.remove();
        }
    }

    /** Arms the given tracks and disarms every other track. Tracks that can't be armed are left as they are. */
    static void armOnly(List<?> targets) {
        if (CLICK_ARMING.get()) {
            return;
        }
        CLICK_ARMING.set(true);
        try {
            List<jJs> armed = new ArrayList<>();
            xJo anyTrack = null;
            for (Object o : targets) {
                if (!(o instanceof jJs channel)) {
                    continue;
                }
                channel.TMF(true); // Bitwig's arm setter: refuses a track that can't be armed
                if (channel.VpW()) { // armed now
                    armed.add(channel);
                }
                if (anyTrack == null && o instanceof xJo track) {
                    anyTrack = track;
                }
            }
            if (armed.isEmpty() || anyTrack == null) {
                return; // nothing could be armed: leave every other track as it is
            }
            anyTrack.qLy(); // Bitwig's own "disarm every track in the project"
            for (jJs channel : armed) {
                channel.TMF(true);
            }
        } catch (Throwable t) {
            MOD.log("could not arm exclusively", t);
        } finally {
            CLICK_ARMING.remove();
        }
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        STORE.putBoolean(KEY_ENABLED, value);
    }

    private static boolean readEnabled() {
        return STORE.getBoolean(KEY_ENABLED, true);
    }
}
