package com.bitwig.mods.parameterrandomizer;

import bitwig.obf.Czc; // something that names an undo step
import bitwig.obf.bDH; // a parameter
import bitwig.obf.z2X; // a parameter that can be set
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.master.g2L; // the project, which knows the parameter under the mouse
import com.bitwig.ramona.core.oDc; // a document object, which can run code as one undo step
import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomises one parameter: the one under the mouse (shortcut, Commander) or the
 * one right-clicked (parameter menu). The action is declared through the Action
 * and Menu framework ({@link ParameterRandomizerActions}).
 */
public final class ParameterRandomizer {
    static final String ACTION_ID = "bitwig_re_randomize_current_parameter";
    static final String ACTION_LABEL = "Randomise Hovered Parameter";

    private static final Bml.Mod MOD = Bml.mod("single-parameter-randomizer");
    private static volatile bDH currentParameter;

    private ParameterRandomizer() {
    }

    /** Bitwig's notification that the parameter under the mouse changed; the new one is already set. */
    @Hook(target = g2L.class, method = "MRY(LbDH;)V", at = At.ENTRY, id = "track-current-parameter")
    public static void rememberCurrentParameter(@This g2L project) {
        currentParameter = project.mcL(); // the parameter under the mouse
    }

    /** The parameter under the mouse, for the shortcut and the Commander. */
    static Object hoveredParameter() {
        return currentParameter;
    }

    /** True when the parameter can be set now. */
    static boolean settable(Object parameter) {
        return parameter instanceof z2X p && p.d3();
    }

    /**
     * Sets a random value as one undo step of its own.
     *
     * <p>A parameter's own setter opens an undo step that merges with the previous
     * step when it starts within 800 ms of it (that's how a knob turn is one step),
     * so presses in quick succession used to land in one step. The setter is run
     * inside an undo step that Bitwig opens without that merge rule: the setter's
     * step nests in it, and the next edit doesn't merge with it either.
     */
    static void randomize(Object parameter, String source) {
        if (!settable(parameter)) return;
        z2X settable = (z2X) parameter;
        double value = ThreadLocalRandom.current().nextDouble();
        Runnable set = () -> settable.FzB(value); // normalised value, 0 to 1
        if (parameter instanceof oDc owner) {
            owner.cfM((Czc) owner, set); // one undo step, named after the parameter
        } else {
            set.run();
        }
        MOD.log("RUN source=" + source + " normalized=" + value);
    }
}
