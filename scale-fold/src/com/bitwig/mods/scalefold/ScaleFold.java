package com.bitwig.mods.scalefold;

import bitwig.obf.DQJ; // Detail Editor mode
import bitwig.obf.JGM; // observable value
import bitwig.obf.K2Z; // Detail Editor lane model
import bitwig.obf.OqT; // group of note rows
import bitwig.obf.Shc; // Drum/Hybrid mode
import bitwig.obf.VJL; // UI color
import bitwig.obf.Vlw; // fixed size
import bitwig.obf.aIL; // editor mode button
import bitwig.obf.aKK; // compact note grid
import bitwig.obf.aMi; // Detail Editor widget
import bitwig.obf.aNi; // note selection view
import bitwig.obf.aRB; // editor mode settings
import bitwig.obf.aZK; // native mode switching action
import bitwig.obf.b1l; // scale definition
import bitwig.obf.bjf; // Detail Editor controller
import bitwig.obf.boq; // note lane
import bitwig.obf.bpb; // group of compact lanes
import bitwig.obf.bpe; // editor lane
import bitwig.obf.dAi; // native icon toggle
import bitwig.obf.dTS; // scroll pane
import bitwig.obf.dTq; // vertical scroll model
import bitwig.obf.dWx; // widget column
import bitwig.obf.dyN; // widget child
import bitwig.obf.epG; // transient pitch row
import bitwig.obf.eob; // widget coordinate conversion
import bitwig.obf.gIW; // native icon sizing
import bitwig.obf.isf; // editor session
import bitwig.obf.jTf; // note row collection
import bitwig.obf.mPN; // key signature shown in the transport
import bitwig.obf.qSV; // conditional observable value
import bitwig.obf.rgb; // value listener
import bitwig.obf.vWT; // mutable boolean observable
import bitwig.obf.yRM; // observable equality
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.base.geom.jJa;
import com.bitwig.base.geom.mTn;
import com.bitwig.base.geom.sD;
import com.bitwig.flt.widget.core.timeline.dIn; // note grid focus interface
import com.bitwig.flt.widget.core.timeline.lsK; // compact row widget
import com.bitwig.flt.widget.core.timeline.rCU; // compact row header
import com.bitwig.flt.widget.core.timeline.yjO; // timeline paint pass
import com.bitwig.graphics.NEs;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ScaleFold {
    private static final Map<isf, WeakReference<EditorState>> EDITORS = new WeakHashMap<>();
    private static final VJL ROOT_SHADE = VJL.vhT(0.0f, 0.0f, 0.0f, 0.30f);
    private static final VJL FIFTH_SHADE = VJL.vhT(0.0f, 0.0f, 0.0f, 0.15f);

    private ScaleFold() {}

    @Hook(target = aIL.class, method = "E3I(LDQJ;Lbjf;LaMi;Z)V", at = At.REPLACE_CALL,
            callOwner = aIL.class, call = "vhT(LdyN;)V")
    public static void addScaleButton(aIL widget, dyN child) {
        DQJ drumMode = widget.tFJ();
        if (!(drumMode.wvR() instanceof Shc) || !(child instanceof dAi drumButton)) {
            widget.vhT(child);
            return;
        }

        aMi panel = widget.Dvd;
        bjf editor = panel.fpo();
        isf session = editor.Msw();
        EditorState existing = stateFor(session);
        EditorState state = existing != null ? existing : new EditorState();
        EDITORS.put(session, new WeakReference<>(state));
        drumButton.HEY(drumButton.Cvo());
        dAi.vhT(drumButton.Cvo(), new qSV(state.enabled, new yRM(editor.TPv(), drumMode), false));

        dAi scaleButton = dAi.WkC();
        dAi.vhT(scaleButton.Cvo(), state.enabled);
        scaleButton.vhT(widget.jl_().W22().vhT("Borderless"));
        scaleButton.lUH(0.5f);
        scaleButton.THY("scales.svg");
        scaleButton.iUE("Show Scale Editor");
        scaleButton.HEY(gIW.vhT());
        scaleButton.HEY(() -> activate(state, panel, editor, drumMode.wvR()));
        drumButton.E3I(new Vlw(22L));
        scaleButton.E3I(new Vlw(22L));

        dWx column = dWx.JQk(1);
        column.xwW(0.0f);
        column.znu(drumButton);
        column.znu(scaleButton);
        widget.vhT(column);
    }

    private static void activate(EditorState state, aMi panel, bjf editor, aRB drumMode) {
        if (state.enabled.getBoolValue()) {
            return;
        }

        state.selectingDrumMode = true;
        try {
            mPN key = editor.THY().aNC().Ptj().vhT();
            state.listen(key.JQk().lUH(), key.Dvd().lUH(), editor.TPv(), panel);
            state.centerPending = true;
            state.enabled.setBoolValue(true);
            if (editor.Ptj().wvR() == drumMode) {
                // Rebuild the grid so it measures its height using the scale rows.
                editor.dQQ();
            }
            panel.xZH().vhT(drumMode, editor, EnumSet.of(sD.E3I));
            if (editor.Ptj().k31() instanceof K2Z model) {
                fold(model);
            }
        } finally {
            state.selectingDrumMode = false;
        }
    }

    @Hook(target = aZK.class, method = "vhT(LaRB;Lbjf;Ljava/util/EnumSet;)V", at = At.ENTRY)
    public static void nativeModeSelected(@Arg(0) aRB mode, @Arg(1) bjf editor) {
        EditorState state = stateFor(editor.Msw());
        if (state != null && state.enabled.getBoolValue() && !state.selectingDrumMode) {
            state.stop();
            if (mode instanceof Shc) {
                // Selecting Drum/Hybrid again needs a fresh native row model.
                editor.dQQ();
            }
        }
    }

    @Hook(target = bjf.class, method = "dQQ()V", at = At.ENTRY)
    public static void pianoAction(@This bjf editor) {
        EditorState state = stateFor(editor.Msw());
        if (state != null && !state.selectingDrumMode) {
            state.stop();
        }
    }

    @Hook(target = bjf.class, method = "fpo()V", at = At.ENTRY)
    public static void drumAction(@This bjf editor) {
        nativeModeSelected(editor.Msw().wvR().kpO(), editor);
    }

    @Hook(target = K2Z.class, method = "JJF()V", at = At.BEFORE_RETURN)
    public static void fold(@This K2Z model) {
        EditorState state = activeState(model);
        if (state != null) {
            state.models.add(model);
            for (Object group : model.GCK()) {
                state.fold(model, (OqT) group);
            }
        }
    }

    @Hook(target = aMi.class, method = "aboutToPaint(LdpW;)V", at = At.ENTRY)
    public static void centerScaleView(@This aMi panel) {
        bjf editor = panel.fpo();
        EditorState state = editor != null ? stateFor(editor.Msw()) : null;
        if (state == null || !state.centerPending || !state.enabled.getBoolValue()) {
            return;
        }
        DQJ mode = editor.Ptj();
        if (mode == null || !(mode.wvR() instanceof Shc) || !(mode.k31() instanceof K2Z model)
                || model.GCK().isEmpty()) {
            return;
        }
        dTq scroll = panel.vhT(mode.wvR());
        if (scroll == null || scroll.Kz() <= 0) {
            return;
        }

        OqT rows = (OqT) model.GCK().getFirst();
        int middle = state.pitches.nextSetBit(60);
        for (int index = 0; index < rows.JJF(); index++) {
            if (rows.vhT(index).THY() == middle) {
                scroll.vhT((long) scroll.kpO((double) index), 1L, 0.5f, false);
                state.centerPending = false;
                return;
            }
        }
    }

    @Hook(target = yjO.class, method = "vhT(Lcom/bitwig/graphics/NEs;DDLcom/bitwig/base/geom/mTn;)V",
            at = At.REPLACE_CALL, callOwner = yjO.class,
            call = "E3I(Lcom/bitwig/graphics/NEs;DDLcom/bitwig/base/geom/mTn;)V")
    public static void shadeScaleRow(yjO timeline, NEs graphics, double start, double end, mTn clip) {
        if (timeline instanceof lsK row && row.fOG() != null && row.THY(aKK.class) instanceof aKK grid) {
            shadePitch(graphics, grid.VEs(), row.fOG().THY());
        }
        timeline.E3I(graphics, start, end, clip);
    }

    @Hook(target = rCU.class, method = "vhT(Lcom/bitwig/graphics/NEs;)V", at = At.ENTRY)
    public static void shadeScaleHeader(@This rCU header, @Arg(0) NEs graphics) {
        if (header.Fho() != null && header.BXY() != null) {
            shadePitch(graphics, header.Fho().fpo(), header.BXY().THY());
        }
    }

    private static void shadePitch(NEs graphics, bjf editor, int pitch) {
        EditorState state = editor != null ? stateFor(editor.Msw()) : null;
        if (state == null || !state.enabled.getBoolValue() || !state.includes(pitch)) {
            return;
        }
        switch (Math.floorMod(pitch - state.rootPitchClass, 12)) {
            case 0 -> graphics.vhT(graphics.nw_(), ROOT_SHADE);
            case 7 -> graphics.vhT(graphics.nw_(), FIFTH_SHADE);
            default -> {}
        }
    }

    @Hook(target = aNi.class, method = "vhT(Lcom/bitwig/base/geom/jJa;Lcom/bitwig/flt/widget/core/timeline/dIn;)V",
            at = At.REPLACE_CALL, callOwner = dIn.class, call = "vhT(Lbpe;Lcom/bitwig/base/geom/jJa;Z)V")
    public static void revealNoteRow(dIn grid, bpe lane, jJa pitches, boolean zoom) {
        if (!(grid instanceof aKK compact) || !(lane instanceof boq noteLane)
                || !(compact.THY(aMi.class) instanceof aMi panel) || panel.fpo() == null) {
            grid.vhT(lane, pitches, zoom);
            return;
        }
        EditorState state = stateFor(panel.fpo().Msw());
        if (state == null || !state.enabled.getBoolValue()) {
            grid.vhT(lane, pitches, zoom);
            return;
        }
        if (!(compact.THY(dTS.class) instanceof dTS pane) || pane.jE_() == null) {
            return;
        }

        bpb group = noteLane.pKm();
        int pitch = (int) pitches.vhT();
        // Measure the laid-out row. Row index times nominal height omits gaps and group headers.
        for (var rows = compact.lUH(lsK.class).iterator(); rows.hasNext();) {
            lsK row = (lsK) rows.next();
            if (row.fOG().KD() == group && row.fOG().THY() == pitch) {
                long top = eob.vhT(row, pane.jE_()).k31();
                pane.OpD().vhT(top, row.FeQ(), -1.0f, true);
                return;
            }
        }
    }

    @Hook(target = jTf.class, method = "E3I(LepG;I)V", at = At.REPLACE_CALL,
            callOwner = jTf.class, call = "vhT(LepG;I)V")
    public static void addRow(jTf rows, epG row, int index) {
        EditorState state = rows instanceof OqT group ? activeState(group.gu_()) : null;
        if (state == null || state.includes(row.THY())) {
            rows.vhT(row, index);
        }
    }

    @Hook(target = OqT.class, method = "E3I(LXCq;LVkM;Ljava/util/Comparator;)V", at = At.REPLACE_CALL,
            callOwner = OqT.class, call = "vhT(LepG;Z)V")
    public static void keepScaleRow(OqT group, epG row, boolean update) {
        EditorState state = activeState(group.gu_());
        if (state == null || !state.includes(row.THY())) {
            group.vhT(row, update);
        } else {
            row.xwW();
        }
    }

    private static EditorState activeState(K2Z model) {
        if (model == null || model.NoB() == null || !(model.NoB().wvR() instanceof Shc)) {
            return null;
        }
        EditorState state = stateFor(model.qQD());
        return state != null && state.enabled.getBoolValue() ? state : null;
    }

    private static EditorState stateFor(isf session) {
        WeakReference<EditorState> ref = EDITORS.get(session);
        return ref == null ? null : ref.get();
    }

    private static final class EditorState {
        final vWT enabled = new vWT(false);
        final Set<K2Z> models = Collections.newSetFromMap(new WeakHashMap<>());
        boolean selectingDrumMode;
        boolean centerPending;
        private JGM rootValue;
        private JGM scaleValue;
        private JGM selectedMode;
        private WeakReference<aMi> panel;
        private int rootPitchClass;
        private BitSet pitches = ScalePitches.of(0, new BitSet());
        private final rgb keyChanged = event -> updateScale();
        private final rgb modeChanged = event -> {
            if (enabled.getBoolValue() && !selectingDrumMode && selectedMode.getValue() instanceof DQJ mode
                    && !(mode.wvR() instanceof Shc)) {
                stop();
            }
        };

        void listen(JGM root, JGM scale, JGM mode, aMi detailPanel) {
            rootValue = root;
            scaleValue = scale;
            selectedMode = mode;
            panel = new WeakReference<>(detailPanel);
            updateScale();
            root.addListener(keyChanged);
            scale.addListener(keyChanged);
            mode.addListener(modeChanged);
        }

        void stop() {
            if (!enabled.getBoolValue()) {
                return;
            }
            enabled.setBoolValue(false);
            centerPending = false;
            rootValue.removeListener(keyChanged);
            scaleValue.removeListener(keyChanged);
            selectedMode.removeListener(modeChanged);
            rootValue = null;
            scaleValue = null;
            selectedMode = null;
            panel = null;
            models.clear();
        }

        boolean includes(int pitch) {
            return pitch >= 0 && pitch < 128 && pitches.get(pitch);
        }

        private void updateScale() {
            if (!(rootValue.getValue() instanceof Number root) || !(scaleValue.getValue() instanceof Number scale)) {
                return;
            }
            b1l definition = b1l.vhT(scale.intValue());
            BitSet intervals = definition != null ? definition.k31() : BitSet.valueOf(new long[]{scale.intValue() & 0xfff});
            int updatedRoot = Math.floorMod(root.intValue(), 12);
            boolean rootChanged = updatedRoot != rootPitchClass;
            rootPitchClass = updatedRoot;
            BitSet updated = ScalePitches.of(updatedRoot, intervals);
            if (!updated.equals(pitches)) {
                pitches = updated;
                for (K2Z model : new ArrayList<>(models)) {
                    ScaleFold.fold(model);
                }
            }
            // Some scales keep the same visible rows when the root changes.
            aMi detailPanel = panel.get();
            if (rootChanged && detailPanel != null && detailPanel.Pgr()) {
                detailPanel.msa();
            }
        }

        void fold(K2Z model, OqT group) {
            for (Object item : new ArrayList<>(group.tFJ())) {
                epG row = (epG) item;
                if (!includes(row.THY())) {
                    group.vhT(row, true);
                }
            }

            // Use Bitwig's comparator so channel-separated rows retain their native order.
            aRB mode = model.NoB().wvR();
            int channel = model.NoB().fpo().eCM() ? mode.Kxv() : -1;
            for (int pitch = pitches.nextSetBit(0); pitch >= 0; pitch = pitches.nextSetBit(pitch + 1)) {
                if (group.E3I(channel, pitch, model.Gtj) == null) {
                    group.vhT(channel, pitch, model.Gtj);
                }
            }
        }
    }
}
