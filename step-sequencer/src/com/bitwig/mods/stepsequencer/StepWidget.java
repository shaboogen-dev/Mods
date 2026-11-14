package com.bitwig.mods.stepsequencer;

import bitwig.obf.VJL; // native color
import bitwig.obf.bjf; // Detail Editor controller
import bitwig.obf.dJU; // mouse press
import bitwig.obf.dJW; // mouse release
import bitwig.obf.dKa; // mouse drag
import bitwig.obf.drm; // subscription to a window's display frames
import bitwig.obf.dse; // native mouse gesture
import bitwig.obf.dvm; // native widget
import bitwig.obf.epe; // modifiers
import bitwig.obf.epf; // mouse button
import bml.pianoroll.Edit;
import bml.pianoroll.Listening;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import com.bitwig.base.geom.IqL; // widget size
import com.bitwig.base.sound.BkR; // native pitch names
import com.bitwig.graphics.NEs; // native drawing surface
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class StepWidget extends dvm {
    private static final VJL BACKGROUND = new VJL(0.117f, 0.121f, 0.128f, 1f);
    private static final VJL STRIP = new VJL(0.155f, 0.163f, 0.172f, 1f);
    private static final VJL CELL = new VJL(0.207f, 0.219f, 0.229f, 1f);
    private static final VJL BEAT = new VJL(0.252f, 0.263f, 0.272f, 1f);
    private static final VJL ACCENT = new VJL(0.98f, 0.59f, 0.30f, 1f);
    private static final VJL PLAYHEAD = new VJL(0.34f, 0.58f, 0.63f, 1f);
    private static final VJL ERASE = new VJL(0.95f, 0.36f, 0.32f, 1f);
    private static final long REFRESH_INTERVAL = 150_000_000L;

    private final bjf editor;
    private final StepSequencer.View view;
    private EditorClip target;
    private List<StepGrid.Event<StepNote>> notes = List.of();
    private Parameter parameter = Parameter.VELOCITY;
    private int page;
    private int firstRow;
    private int playingColumn = -1;
    private CellGesture gesture;
    private drm frames;
    private Listening changes;
    private boolean subscribing;
    private long nextRefresh;

    private enum Parameter {
        VELOCITY("Velocity", 1, 127, 1),
        OFFSET("Offset", Integer.MIN_VALUE, Integer.MAX_VALUE, 12),
        LENGTH("Length", Integer.MIN_VALUE, Integer.MAX_VALUE, 8),
        REPEAT("Repeat", 1, 128, 8),
        PITCH("Pitch", 0, 127, 8),
        CHANCE("Chance", 0, 100, 1);

        final String label;
        final int min;
        final int max;
        final int pixelsPerUnit;

        Parameter(String label, int min, int max, int pixelsPerUnit) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.pixelsPerUnit = pixelsPerUnit;
        }

        int read(StepGrid.Event<StepNote> event) {
            return switch (this) {
                case VELOCITY -> StepGrid.velocity127(event.velocity());
                case REPEAT -> event.note().repeatCount();
                case CHANCE -> (int) Math.round(event.note().chance() * 100);
                case PITCH -> event.key();
                case OFFSET, LENGTH -> 0; // drag delta, so an existing off-grid value is preserved
            };
        }

        String display(int value) {
            return switch (this) {
                case CHANCE -> value + "%";
                case REPEAT -> value == 1 ? "Off" : Integer.toString(value);
                case PITCH -> BkR.vhT(value, true);
                default -> Integer.toString(value);
            };
        }

        boolean changesPlacement() {
            return this == OFFSET || this == LENGTH || this == PITCH;
        }

        StepGrid.Placement placement(StepGrid.Event<StepNote> note, double start, double clipEnd, int value) {
            return switch (this) {
                case OFFSET -> StepGrid.offset(note, start, clipEnd, value);
                case LENGTH -> StepGrid.length(note, clipEnd, value);
                case PITCH -> new StepGrid.Placement(value, note.start(), note.end());
                default -> new StepGrid.Placement(note.key(), note.start(), note.end());
            };
        }

        String displayPlacement(StepGrid.Placement placement, double start) {
            return switch (this) {
                case OFFSET -> "+" + Math.clamp(Math.round((placement.start() - start) / StepGrid.STEP * 100), 0, 99) + "%";
                case LENGTH -> String.format(java.util.Locale.ROOT, "%.2f step", (placement.end() - placement.start()) / StepGrid.STEP);
                case PITCH -> display(placement.key());
                default -> throw new IllegalStateException();
            };
        }

        void apply(StepNote note, int value) {
            switch (this) {
                case VELOCITY -> PianoRoll.edit("Set Step Velocity").setVelocity(note.note(), value / 127.0).apply();
                case REPEAT -> note.setRepeatCount(value);
                case CHANCE -> note.setChance(value);
                case OFFSET, LENGTH, PITCH -> throw new IllegalStateException();
            }
        }
    }

    private static final Parameter[] PARAMETERS = Parameter.values();

    StepWidget(bjf editor, StepSequencer.View view) {
        this.editor = editor;
        this.view = view;
    }

    @Override public IqL mL_() { return IqL.vhT(); }
    @Override public IqL nj_() { return IqL.E3I(); }
    @Override public dvm Xzi() { return this; }

    void deactivate() {
        gesture = null;
        playingColumn = -1;
        if (frames != null) {
            frames.vhT();
            frames = null;
        }
        if (changes != null) {
            changes.stop();
            changes = null;
        }
        nextRefresh = 0;
    }

    @Override
    public void iy_() {
        deactivate();
        super.iy_();
    }

    private void watch() {
        if (frames != null || subscribing) {
            return;
        }
        subscribing = true;
        PianoRoll.later(() -> {
            subscribing = false;
            if (frames == null && view.active.getBoolValue() && NhY()) {
                changes = PianoRoll.onChange(() -> nextRefresh = 0);
                frames = drm.vhT(frame -> {
                    if (!view.active.getBoolValue() || !NhY()) {
                        deactivate();
                    } else {
                        if (System.nanoTime() - nextRefresh >= 0) {
                            refresh();
                        }
                        int column = target == null ? -1 : target.playbackColumn(editor, page, frame.HEY());
                        if (column != playingColumn) {
                            playingColumn = column;
                            msa();
                        }
                    }
                }, this);
                refresh();
            }
        });
    }

    private void refresh() {
        nextRefresh = System.nanoTime() + REFRESH_INTERVAL;
        try {
            EditorClip current = EditorClip.read(editor);
            boolean clipChanged = target == null ? current != null
                    : current == null || !target.clip().equals(current.clip());
            if (gesture != null && !gesture.matches(current)) {
                gesture = null;
                msa();
            }
            if (clipChanged) {
                playingColumn = -1;
                page = 0;
                firstRow = current != null && current.chromatic() ? 63 : 0;
            }
            if (current != null) {
                page = Math.min(page, current.range().pages() - 1);
                firstRow = Math.min(firstRow, maxFirstRow(current));
            }
            List<StepGrid.Event<StepNote>> currentNotes = current == null ? List.of() : current.notes(
                    current.range().beat(page, 0), Math.min(current.range().end(), current.range().beat(page, StepGrid.STEPS)));
            if (!Objects.equals(target, current) || !notes.equals(currentNotes)) {
                target = current;
                notes = currentNotes;
                msa();
            }
        } catch (RuntimeException | LinkageError failure) {
            deactivate();
            target = null;
            notes = List.of();
            StepSequencer.MOD.log("Could not read the active clip", failure);
        }
    }

    private double labels() { return Math.min(cLM(156), lWC() * 0.33); }
    private double column() { return Math.max(1, (lWC() - labels()) / StepGrid.STEPS); }
    private double gridRight() { return labels() + StepGrid.STEPS * column(); }
    private double rowHeight() { return Math.max(cLM(20), Math.min(cLM(42), column() * 0.70)); }
    private double top() { return cLM(48); }
    private double bottom() { return Math.max(top(), FeQ() - cLM(25)); }
    private double tabStart() { return Math.max(cLM(65), Math.min(labels() + cLM(3), lWC() - PARAMETERS.length * cLM(52))); }
    private double tabWidth() { return Math.min(cLM(70), Math.max(1, (lWC() - tabStart()) / PARAMETERS.length)); }
    private int visibleRows() { return Math.max(1, (int) Math.ceil((bottom() - top()) / rowHeight())); }
    private int maxFirstRow(EditorClip clip) { return Math.max(0, clip.rows().size() - visibleRows()); }

    private int cellAt(double x, double y) {
        int column = (int) Math.floor((x - labels()) / column());
        int row = firstRow + (int) Math.floor((y - top()) / rowHeight());
        if (y < top() || y >= bottom() || column < 0 || column >= StepGrid.STEPS || row >= target.rows().size()) {
            return -1;
        }
        return row * StepGrid.STEPS + column;
    }

    @Override
    public void vhT(NEs graphics) {
        if (!view.active.getBoolValue()) {
            return;
        }
        watch();
        graphics.aAX();
        try {
            graphics.vhT(kI_().vhT(ZyT(), this));
            VJL text = Pby().vhT(this);
            rectangle(graphics, 0, 0, lWC(), FeQ(), BACKGROUND);
            rectangle(graphics, 0, 0, lWC(), cLM(26), STRIP);
            graphics.vhT(cLM(9), cLM(17), "16 Steps  ·  1/16", text);
            if (target == null) {
                graphics.vhT(cLM(9), cLM(45), "Open a single editable MIDI clip to use Steps.", text);
                return;
            }
            paintControls(graphics, text);
            if (playingColumn >= 0) {
                rectangle(graphics, labels() + playingColumn * column(), cLM(44), column() - cLM(2), cLM(2), PLAYHEAD);
            }
            paintGrid(graphics, text);
        } finally {
            graphics.UlY();
        }
    }

    private void paintControls(NEs graphics, VJL text) {
        double right = gridRight() - cLM(6);
        button(graphics, right - cLM(126), cLM(4), "‹", page > 0, text);
        graphics.vhT(right - cLM(96), cLM(17), (page + 1) + " / " + target.range().pages(), text);
        button(graphics, right - cLM(26), cLM(4), "›", page + 1 < target.range().pages(), text);
        for (int column = 0; column < StepGrid.STEPS; column += 4) {
            graphics.vhT(labels() + column * column() + cLM(3), cLM(41), Integer.toString(column + 1), text.vhT(0.6f));
        }
        rectangle(graphics, 0, bottom(), lWC(), cLM(25), STRIP);
        button(graphics, cLM(5), bottom() + cLM(3), "↑", firstRow > 0, text);
        button(graphics, cLM(34), bottom() + cLM(3), "↓", firstRow < maxFirstRow(target), text);
        for (Parameter tab : PARAMETERS) {
            double x = tabStart() + tab.ordinal() * tabWidth();
            boolean selected = parameter == tab;
            rectangle(graphics, x, bottom() + cLM(3), tabWidth() - cLM(2), cLM(18), selected ? BEAT : CELL);
            if (selected) {
                rectangle(graphics, x, bottom() + cLM(20), tabWidth() - cLM(2), cLM(1), ACCENT);
            }
            graphics.vhT(x + cLM(7), bottom() + cLM(16), tab.label, text.vhT(selected ? 1f : 0.72f));
        }
        if (gesture != null && gesture.dragged) {
            graphics.vhT(tabStart() + PARAMETERS.length * tabWidth() + cLM(8), bottom() + cLM(16),
                    gesture.display(), text);
        }
    }

    private void button(NEs graphics, double x, double y, String label, boolean enabled, VJL text) {
        rectangle(graphics, x, y, cLM(25), cLM(18), CELL);
        graphics.vhT(x + cLM(9), y + cLM(13), label, text.vhT(enabled ? 1f : 0.3f));
    }

    private void paintGrid(NEs graphics, VJL text) {
        VJL color = target.color() == null || target.color().Dvd() == 0f ? ACCENT : target.color().vhT();
        int rows = Math.min(target.rows().size() - firstRow, visibleRows());
        for (int row = 0; row < rows; row++) {
            EditorClip.Row pitch = target.rows().get(firstRow + row);
            double y = top() + row * rowHeight();
            double height = Math.min(rowHeight() - cLM(2), bottom() - y);
            if (height <= 0) {
                break;
            }
            rectangle(graphics, 0, y, labels() - cLM(2), height, STRIP);
            if (height >= cLM(15)) {
                String name = pitch.name();
                int limit = Math.max(4, (int) ((labels() - cLM(15)) / Math.max(1, cLM(7))));
                graphics.vhT(cLM(8), y + height * 0.5 + cLM(4),
                        name.length() > limit ? name.substring(0, limit - 1) + "…" : name, text);
            }
            for (int column = 0; column < StepGrid.STEPS; column++) {
                paintCell(graphics, pitch, (firstRow + row) * StepGrid.STEPS + column, y, height, color, text);
            }
        }
    }

    private void paintCell(NEs graphics, EditorClip.Row row, int index, double y, double height, VJL color, VJL text) {
        int column = index % StepGrid.STEPS;
        double x = labels() + column * column();
        double width = Math.max(0, column() - cLM(2));
        double start = target.range().beat(page, column);
        double end = Math.min(start + StepGrid.STEP, target.range().end());
        if (end <= start) {
            rectangle(graphics, x, y, width, height, STRIP);
            return;
        }
        StepGrid.Cell<StepNote> cell = StepGrid.cell(notes, row.key(), target.channel(), start, end);
        rectangle(graphics, x, y, width, height, column % 4 == 0 ? BEAT : CELL);
        if (column == playingColumn) {
            rectangle(graphics, x, y, width, height, PLAYHEAD.vhT(0.36f));
        }
        boolean pending = gesture != null && gesture.pending.get(index);
        if (cell.starts() > 0) {
            rectangle(graphics, x, y, width, height, color.vhT(0.45f));
            if (cell.note() == null) {
                graphics.vhT(x + cLM(4), y + Math.min(height - 1, cLM(15)), Integer.toString(cell.starts()), text);
            } else {
                boolean dragging = gesture != null && gesture.dragged && gesture.origin == index;
                StepNote note = cell.note().note();
                double velocity = dragging && parameter == Parameter.VELOCITY ? gesture.value / 127.0 : cell.note().velocity();
                double chance = dragging && parameter == Parameter.CHANCE ? gesture.value / 100.0 : note.chance();
                double fill = height * (parameter == Parameter.CHANCE ? chance : velocity);
                fill = Math.min(height, Math.max(cLM(2), fill));
                rectangle(graphics, x, y + height - fill, width, fill, color);
                int repeats = dragging && parameter == Parameter.REPEAT ? gesture.value : note.repeatCount();
                paintRepeats(graphics, x, y, width, height, repeats);
                if (height >= cLM(15) && width >= cLM(34)) {
                    String label = null;
                    if (parameter == Parameter.CHANCE) {
                        label = Parameter.CHANCE.display((int) Math.round(chance * 100));
                    } else if (parameter.changesPlacement()) {
                        int value = dragging ? gesture.value : parameter.read(cell.note());
                        StepGrid.Placement placement = parameter.placement(cell.note(), start, target.range().end(), value);
                        label = parameter.displayPlacement(placement, start);
                    }
                    if (label != null) {
                        graphics.vhT(x + cLM(5), y + cLM(13), label, text);
                    }
                }
            }
        } else if (pending && !gesture.erase) {
            double fill = height * gesture.source.velocity();
            rectangle(graphics, x, y, width, height, color.vhT(0.45f));
            rectangle(graphics, x, y + height - fill, width, fill, color.vhT(0.85f));
        } else if (cell.continuation()) {
            rectangle(graphics, x, y + height * 0.45, width, height * 0.16, color.vhT(0.55f));
        }
        boolean erasing = gesture != null && cell.note() != null && cell.note().equals(gesture.erased.get(index));
        if (erasing || column == playingColumn && cell.starts() > 0) {
            VJL outline = erasing ? ERASE : text;
            if (erasing) {
                rectangle(graphics, x, y, width, height, BACKGROUND.vhT(0.55f));
            }
            double edge = Math.min(cLM(2), Math.min(width, height) / 2);
            rectangle(graphics, x, y, width, edge, outline);
            rectangle(graphics, x, y + height - edge, width, edge, outline);
            rectangle(graphics, x, y, edge, height, outline);
            rectangle(graphics, x + width - edge, y, edge, height, outline);
        }
    }

    private void paintRepeats(NEs graphics, double x, double y, double width, double height, int repeats) {
        int segments = Math.min(repeats, (int) (width / Math.max(1, cLM(4))));
        for (int i = 1; i < segments; i++) {
            rectangle(graphics, x + i * width / segments, y + height * 0.16, cLM(1), height * 0.68, BACKGROUND.vhT(0.9f));
        }
    }

    private static void rectangle(NEs graphics, double x, double y, double width, double height, VJL color) {
        if (width > 0 && height > 0) {
            graphics.vhT(x, y, width, height, color);
        }
    }

    @Override
    public dse vhT(epf button, double x, double y, epe modifiers, int clicks) {
        if (!view.active.getBoolValue() || target == null) {
            return null;
        }
        if (button == epf.HEY || button == epf.vhT && modifiers.vhT()) {
            int index = cellAt(x, y);
            return index < 0 ? null : new CellGesture(target, index, x, y, null, true);
        }
        if (button != epf.vhT) {
            return null;
        }
        double right = gridRight() - cLM(6);
        if (y < cLM(26) && x >= right - cLM(126) && x < right) {
            if (x <= right - cLM(101)) {
                return control(() -> changePage(-1));
            }
            if (x >= right - cLM(26)) {
                return control(() -> changePage(1));
            }
        }
        if (y >= bottom() && x <= cLM(59)) {
            int direction = x < cLM(32) ? -1 : 1;
            return control(() -> scrollRows(direction * Math.max(1, visibleRows() - 1)));
        }
        if (y >= bottom() && x >= tabStart() && x < tabStart() + PARAMETERS.length * tabWidth()) {
            Parameter selected = PARAMETERS[(int) ((x - tabStart()) / tabWidth())];
            return control(() -> {
                parameter = selected;
                msa();
            });
        }
        int index = cellAt(x, y);
        if (index < 0) {
            return null;
        }
        int column = index % StepGrid.STEPS;
        double start = target.range().beat(page, column);
        double end = Math.min(start + StepGrid.STEP, target.range().end());
        if (end <= start) {
            return null;
        }
        int key = target.rows().get(index / StepGrid.STEPS).key();
        StepGrid.Cell<StepNote> cell = StepGrid.cell(notes, key, target.channel(), start, end);
        return cell.note() != null || cell.canAdd() ? new CellGesture(target, index, x, y, cell.note(), false) : null;
    }

    private dse control(Runnable action) {
        return new dse() {
            @Override public void vhT(dJW release) {
                try {
                    action.run();
                } finally {
                    QJx();
                }
            }
        };
    }

    private void changePage(int delta) {
        if (target != null) {
            page = Math.max(0, Math.min(target.range().pages() - 1, page + delta));
            playingColumn = -1;
            refresh();
            msa();
        }
    }

    private void scrollRows(int delta) {
        if (target != null) {
            firstRow = Math.max(0, Math.min(maxFirstRow(target), firstRow + delta));
            msa();
        }
    }

    private final class CellGesture extends dse {
        private final EditorClip source;
        private final int sourcePage = page;
        private final int origin;
        private final double pressX;
        private final double pressY;
        private final StepGrid.Event<StepNote> note;
        private final Parameter parameter = StepWidget.this.parameter;
        private final BitSet pending = new BitSet();
        private final Map<Integer, StepGrid.Event<StepNote>> erased = new HashMap<>();
        private final boolean erase;
        private int previous;
        private int value;
        private boolean dragged;
        private boolean moved;

        CellGesture(EditorClip source, int index, double pressX, double pressY, StepGrid.Event<StepNote> note, boolean erase) {
            this.source = source;
            origin = index;
            previous = index;
            this.pressX = pressX;
            this.pressY = pressY;
            this.note = note;
            this.erase = erase;
            value = note == null ? 0 : parameter.read(note);
        }

        @Override public boolean jC_() { return true; }

        @Override
        public void vhT(dJU press) {
            gesture = this;
            if (note == null) {
                addToStroke(origin);
                msa();
            }
        }

        @Override
        public void vhT(dKa move) {
            if (gesture == this) {
                dragTo(move.BS(), move.uOI());
                msa();
            }
        }

        private void dragTo(double x, double y) {
            if (note == null) {
                int index = cellAt(x, y);
                if (index < 0) {
                    previous = -1;
                } else {
                    if (previous < 0) {
                        addToStroke(index);
                    } else {
                        StepGrid.trace(previous, index, this::addToStroke);
                    }
                    previous = index;
                }
                return;
            }
            double dy = pressY - y;
            dragged |= Math.abs(dy) >= cLM(4);
            moved |= dragged || Math.abs(pressX - x) >= cLM(4);
            if (dragged) {
                value = Math.clamp(parameter.read(note) + (int) Math.round(dy / Math.max(1, cLM(parameter.pixelsPerUnit))),
                        parameter.min, parameter.max);
            }
        }

        private void addToStroke(int index) {
            double start = source.range().beat(sourcePage, index % StepGrid.STEPS);
            double end = Math.min(start + StepGrid.STEP, source.range().end());
            int key = source.rows().get(index / StepGrid.STEPS).key();
            if (end <= start) {
                return;
            }
            StepGrid.Cell<StepNote> cell = StepGrid.cell(notes, key, source.channel(), start, end);
            if (erase && cell.note() != null) {
                erased.putIfAbsent(index, cell.note());
                pending.set(index);
            } else if (!erase && cell.canAdd()) {
                pending.set(index);
            }
        }

        @Override
        public void vhT(dJW release) {
            try {
                if (gesture == this && view.active.getBoolValue()) {
                    dragTo(release.BS(), release.uOI());
                    commit();
                    refresh();
                }
            } catch (Refused refused) {
                PianoRoll.notification(refused.getMessage());
            } catch (RuntimeException | LinkageError failure) {
                StepSequencer.MOD.log("Could not edit the step", failure);
            } finally {
                QJx();
            }
        }

        private void commit() {
            EditorClip current = EditorClip.read(editor);
            if (!matches(current)) {
                return;
            }
            if (note == null) {
                commitStroke(current);
                return;
            }
            double start = current.range().beat(sourcePage, origin % StepGrid.STEPS);
            double end = Math.min(start + StepGrid.STEP, current.range().end());
            int key = current.rows().get(origin / StepGrid.STEPS).key();
            StepGrid.Cell<StepNote> cell = StepGrid.cell(current.notes(start, end), key, current.channel(), start, end);
            if (note.equals(cell.note())) {
                if (dragged && value != parameter.read(note)) {
                    if (parameter.changesPlacement()) {
                        commitPlacement(current, cell.note(), start);
                    } else {
                        parameter.apply(cell.note().note(), value);
                    }
                } else if (!moved) {
                    PianoRoll.edit("Remove Step").remove(cell.note().note().note()).apply();
                }
            }
        }

        private String display() {
            if (parameter.changesPlacement()) {
                double start = source.range().beat(sourcePage, origin % StepGrid.STEPS);
                return parameter.displayPlacement(parameter.placement(note, start, source.range().end(), value), start);
            }
            return parameter.display(value);
        }

        private void commitPlacement(EditorClip current, StepGrid.Event<StepNote> event, double cellStart) {
            StepGrid.Placement placement = parameter.placement(event, cellStart, current.range().end(), value);
            if (placement.equals(new StepGrid.Placement(event.key(), event.start(), event.end()))) {
                return;
            }
            if (!StepGrid.canPlace(event, placement, current.notes(placement.start(), placement.end()))) {
                throw new Refused("This edit would overlap another note on the same pitch and MIDI channel.");
            }
            var note = event.note().note();
            Edit edit = PianoRoll.edit("Set Step " + parameter.label);
            switch (parameter) {
                case OFFSET -> edit.move(note, placement.start());
                case LENGTH -> edit.setEnd(note, placement.end());
                case PITCH -> edit.setKey(note, placement.key());
                default -> throw new IllegalStateException();
            }
            edit.apply();
        }

        private boolean matches(EditorClip current) {
            return current != null && current.clip().equals(source.clip()) && current.range().equals(source.range())
                    && current.channel() == source.channel() && current.rows().equals(source.rows()) && page == sourcePage;
        }

        private void commitStroke(EditorClip current) {
            StepGrid.Range range = current.range();
            List<StepGrid.Event<StepNote>> existing = current.notes(range.beat(sourcePage, 0),
                    Math.min(range.end(), range.beat(sourcePage, StepGrid.STEPS)));
            Edit edit = PianoRoll.edit(erase ? "Erase Steps" : pending.cardinality() == 1 ? "Add Step" : "Draw Steps");
            for (int index = pending.nextSetBit(0); index >= 0; index = pending.nextSetBit(index + 1)) {
                double start = range.beat(sourcePage, index % StepGrid.STEPS);
                double end = Math.min(start + StepGrid.STEP, range.end());
                int key = current.rows().get(index / StepGrid.STEPS).key();
                StepGrid.Cell<StepNote> cell = StepGrid.cell(existing, key, current.channel(), start, end);
                if (erase && erased.get(index).equals(cell.note())) {
                    edit.remove(cell.note().note().note());
                } else if (!erase && cell.canAdd()) {
                    edit.add(current.clip(), current.channel(), key, start, end - start, source.velocity(), null);
                }
            }
            edit.apply();
        }

        @Override
        public void JQk() {
            if (gesture == this) {
                gesture = null;
                msa();
            }
            super.JQk();
        }
    }
}
