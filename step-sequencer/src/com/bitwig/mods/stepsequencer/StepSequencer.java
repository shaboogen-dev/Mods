package com.bitwig.mods.stepsequencer;

import bitwig.obf.Shc; // Drum/Hybrid mode
import bitwig.obf.Vlw; // fixed widget size
import bitwig.obf.aIL; // Detail Editor mode button
import bitwig.obf.aMi; // Detail Editor panel
import bitwig.obf.aZK; // Detail Editor mode action
import bitwig.obf.bjf; // Detail Editor controller
import bitwig.obf.dAi; // native icon toggle
import bitwig.obf.dWx; // native row or column
import bitwig.obf.dvm; // widget
import bitwig.obf.gIW; // icon sizing
import bitwig.obf.vWT; // mutable boolean observable
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.base.geom.sD;
import com.bitwig.flt.widget.core.timeline.FLM; // Detail Editor mode strip
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class StepSequencer {
    static final Bml.Mod MOD = Bml.mod("step-sequencer");
    private static final Map<aMi, WeakReference<View>> VIEWS = new WeakHashMap<>();
    private static final Map<bjf, Boolean> ACTIVE_EDITORS = new WeakHashMap<>();

    private StepSequencer() {}

    private static View view(aMi panel) {
        WeakReference<View> reference = VIEWS.get(panel);
        View view = reference == null ? null : reference.get();
        if (view == null) {
            view = new View(panel);
            VIEWS.put(panel, new WeakReference<>(view));
        }
        return view;
    }

    @Hook(target = aIL.class, method = "vhT(LDQJ;Lbjf;LaMi;Z)LaIL;", at = At.WRAP_RETURN)
    public static aIL addStepsButton(@Return aIL modeButton) {
        if (!(modeButton.tFJ().wvR() instanceof Shc)) {
            return modeButton;
        }
        try {
            View view = view(modeButton.Dvd);
            dAi steps = dAi.WkC();
            dAi.vhT(steps.Cvo(), view.active);
            steps.vhT(modeButton.jl_().W22().vhT("Borderless"));
            steps.lUH(0.5f);
            steps.THY("show_grid.svg");
            steps.iUE("Show Steps");
            steps.HEY(gIW.vhT());
            steps.HEY(() -> view.toggle(modeButton));
            steps.E3I(new Vlw(22L));

            dvm nativeButton = modeButton.n_(0);
            if (nativeButton instanceof dWx column) {
                column.znu(steps); // Scale Fold already put Drum/Hybrid and Scale in a column.
            } else {
                dWx column = dWx.JQk(1);
                column.xwW(0.0f);
                nativeButton.E3I(new Vlw(22L));
                modeButton.Xzi(nativeButton);
                column.znu(nativeButton);
                column.znu(steps);
                modeButton.vhT(column);
            }
        } catch (RuntimeException | LinkageError failure) {
            MOD.log("Could not add the Steps button", failure);
        }
        return modeButton;
    }

    @Hook(target = aMi.class, method = "E3I(LYot;Lbjf;LaZK;)V", at = At.BEFORE_RETURN)
    public static void mount(@This aMi panel) {
        try {
            dWx layout = panel.lXL;
            dvm left = layout.n_(0).n_(0);
            dWx right = (dWx) layout.n_(1);
            if (right.fQN() != 2 || right.n_(1) != panel.qQD || left.fQN() != 2) {
                return;
            }
            ArrayList<dvm> nativeContent = new ArrayList<>();
            if (!collectHeaderContent(left.n_(1), nativeContent)) {
                MOD.log("Could not find the Detail Editor mode strip");
                return;
            }
            nativeContent.add(right.n_(0));
            nativeContent.add(right.n_(1));
            view(panel).mount(right, nativeContent);
        } catch (RuntimeException | LinkageError failure) {
            MOD.log("Could not mount the Steps view", failure);
        }
    }

    private static boolean collectHeaderContent(dvm widget, List<dvm> content) {
        if (widget instanceof FLM) {
            return true;
        }
        for (int i = 0; i < widget.fQN(); i++) {
            if (collectHeaderContent(widget.n_(i), content)) {
                for (int j = 0; j < widget.fQN(); j++) {
                    if (j != i) {
                        content.add(widget.n_(j));
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Hook(target = aZK.class, method = "vhT(LaRB;Lbjf;Ljava/util/EnumSet;)V", at = At.ENTRY)
    public static void nativeMode(@Arg(1) bjf editor) {
        close(editor);
    }

    @Hook(target = bjf.class, method = "dQQ()V", at = At.ENTRY)
    public static void pianoMode(@This bjf editor) {
        close(editor);
    }

    @Hook(target = bjf.class, method = "fpo()V", at = At.ENTRY)
    public static void drumMode(@This bjf editor) {
        close(editor);
    }

    private static void close(bjf editor) {
        setActive(editor, false);
    }

    private static void setActive(bjf editor, boolean active) {
        if (active) {
            ACTIVE_EDITORS.put(editor, true);
        } else {
            ACTIVE_EDITORS.remove(editor);
        }
        for (WeakReference<View> reference : List.copyOf(VIEWS.values())) {
            View view = reference.get();
            if (view != null && view.panel.fpo() == editor) {
                view.setActive(active);
            }
        }
    }

    static final class View {
        final aMi panel;
        final vWT active = new vWT(false);
        private StepWidget steps;
        private dvm[] nativeContent;
        private boolean[] wasVisible;

        View(aMi panel) {
            this.panel = panel;
        }

        void mount(dWx right, List<dvm> content) {
            nativeContent = content.toArray(dvm[]::new);
            wasVisible = new boolean[nativeContent.length];
            steps = new StepWidget(panel.fpo(), this);
            steps.H_();
            steps.eHZ(false);
            right.znu(steps);
            setActive(ACTIVE_EDITORS.containsKey(panel.fpo()));
        }

        void toggle(aIL button) {
            if (steps == null) {
                return;
            }
            bjf editor = panel.fpo();
            if (active.getBoolValue()) {
                close(editor);
                return;
            }
            // Scale Fold uses Drum/Hybrid underneath. Selecting it through Bitwig also exits Scale Fold.
            if (editor.Ptj() != null && editor.Ptj().wvR() instanceof Shc) {
                panel.xZH().vhT(button.tFJ().wvR(), editor, EnumSet.of(sD.E3I));
            }
            StepSequencer.setActive(editor, true);
        }

        void setActive(boolean enabled) {
            if (steps == null || active.getBoolValue() == enabled) {
                return;
            }
            if (!enabled) {
                steps.deactivate();
            }
            active.setBoolValue(enabled);
            for (int i = 0; i < nativeContent.length; i++) {
                if (enabled) {
                    wasVisible[i] = nativeContent[i].YgK();
                }
                nativeContent[i].eHZ(!enabled && wasVisible[i]);
            }
            steps.eHZ(enabled);
        }
    }
}
