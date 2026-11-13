import bitwig.obf.LJt; // what a drag shows next to the pointer
import bitwig.obf.Tgk; // an automation lane
import bitwig.obf.VGL; // a value change event
import bitwig.obf.aL; // a parameter's value domain: its range, units and formatting
import bitwig.obf.bDS; // a boolean value
import bitwig.obf.dAd; // a toggle button
import bitwig.obf.dJM; // a drag in progress
import bitwig.obf.dND; // a number field
import bitwig.obf.dNF; // a number field's drag action
import bitwig.obf.dNH; // how a number field shows its value, part by part
import bitwig.obf.dOZ; // a side, for placing a popup
import bitwig.obf.dOu; // a number formatter: number to text and back
import bitwig.obf.dPi; // a popup placed next to a widget
import bitwig.obf.dWb; // a layout box: a row or a column
import bitwig.obf.dXp; // a separator line
import bitwig.obf.doW; // a described mouse action
import bitwig.obf.dpN; // Bitwig's application (translations)
import bitwig.obf.drX; // a pointer action
import bitwig.obf.dsP; // where a popup is placed
import bitwig.obf.dtX; // Bitwig's modal dialog
import bitwig.obf.duY; // a label
import bitwig.obf.dve; // a widget
import bitwig.obf.eeY; // a theme colour
import bitwig.obf.eoJ; // where an input came from
import bitwig.obf.eoV; // the modifier keys
import bitwig.obf.eoW; // a kind of pointer event
import bitwig.obf.fY; // parses text into a value of a domain
import bitwig.obf.foz; // a value listener
import bitwig.obf.iok; // a colour
import bitwig.obf.ujq; // a number value
import bitwig.obf.vjp; // drag feedback text
import com.bitwig.base.geom.FloatRectangle;
import com.bitwig.base.geom.IBe; // a layout axis
import com.bitwig.graphics.SYo; // a graphics context
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The "Add Points" dialog: Point 1 and Point 2, each with an on/off toggle and a value in the
 * automated parameter's own units, and the curve between them, edited like the curve of an ADSR
 * segment (drag, Ctrl+click to type, double-click to reset). Built from Bitwig's own widgets.
 */
final class AutomationPointsDialog extends dtX {
    private static volatile eeY ruleColour;

    private final Tgk lane;
    private final aL domain;
    private final double start, end;
    private final bDS addPoint1, addPoint2;
    private final Num point1, point2, curve;
    private final dND point1Field, point2Field, curveField;
    private dWb buttons;
    private boolean applying;

    /** {@code value1} and {@code value2} are the automation's values (0..1) at start and end. */
    AutomationPointsDialog(Tgk lane, aL domain, double start, double end, double value1, double value2) {
        super();
        this.lane = lane;
        this.domain = domain;
        this.start = start;
        this.end = end;
        if (domain != null) {
            double lo = Math.min(domain.FzB(), domain.UNj()), hi = Math.max(domain.FzB(), domain.UNj()); // its range
            point1 = new Num(domain.MRY(value1), lo, hi); // 0..1 to the parameter's value
            point2 = new Num(domain.MRY(value2), lo, hi);
            DomainFormatter format = new DomainFormatter(domain);
            point1Field = field(point1, format);
            point2Field = field(point2, format);
        } else {
            point1 = new Num(value1, 0, 1);
            point2 = new Num(value2, 0, 1);
            point1Field = field(point1, PERCENT);
            point2Field = field(point2, PERCENT);
        }
        curve = new Num(0, -1, 1);
        CurveField curveField = CurveField.create(curve, point1, point2);
        this.curveField = curveField;
        point1.attachPreview(curveField); // the preview follows both point values
        point2.attachPreview(curveField);
        addPoint1 = toggleValue();
        addPoint2 = toggleValue();
        zqN(); // set the dialog up
        H_();
        MJ("Add Points"); // its title
        BDm(true);
        jFL(true);
    }

    /** A point's on/off value; switching it updates the fields. */
    private bDS toggleValue() {
        return new bDS(true) {
            @Override
            public void setBoolValue(boolean value) {
                boolean changed = value != getBoolValue();
                super.setBoolValue(value);
                if (changed) syncPointStates();
            }
        };
    }

    private static dND field(Num value, dOu format) {
        dND f = dND.Ktv(); // a plain number field
        f.Hnt(value); // its value
        // A number field formats twice: once while it is edited (the formatter) and once to show
        // the value (the parts); both use the parameter's units.
        f.cfM(new FormatterParts(format, value.min, value.max));
        f.FzB(format.jD_()); // the formatter for editing
        return f;
    }

    /** Lays out the dialog's content. */
    @Override
    public void Hnt(dWb root) {
        dAd point1Toggle = dAd.cfM(addPoint1); // Bitwig's text toggle button
        point1Toggle.IwV("Point 1"); // its text
        dAd point2Toggle = dAd.cfM(addPoint2);
        point2Toggle.IwV("Point 2");
        // Toggle buttons take all the width they are given; a fixed-width cell keeps them their
        // size when the dialog is resized, as in Bitwig's own dialogs (Quantize).
        dWb point1Cell = new FixedWidthBox(128, point1Toggle);
        dWb point2Cell = new FixedWidthBox(128, point2Toggle);
        syncPointStates();

        dWb outer = dWb.UNj(0); // a column

        dWb point1Row = dWb.IwV(10); // a row
        point1Row.HL(point1Cell); // add
        point1Row.Hnt(12); // a gap
        point1Row.cfM(point1Field, 0.0f); // add, not stretched

        dWb point2Row = dWb.IwV(10);
        point2Row.HL(point2Cell);
        point2Row.Hnt(12);
        point2Row.cfM(point2Field, 0.0f);

        dWb pointGroup = dWb.UNj(0);
        pointGroup.HL(point1Row);
        pointGroup.Hnt(10);
        pointGroup.HL(point2Row);

        // The label sits 4 px lower so it is centred next to the curve preview.
        dWb curveLabel = dWb.UNj(0);
        curveLabel.Hnt(4);
        curveLabel.HL(duY.MIO("Curve")); // a label

        dWb curveGroup = dWb.IwV(8);
        curveGroup.HL(curveLabel);
        curveGroup.Hnt(8);
        curveGroup.cfM(curveField, 0.0f);

        // The point group and the curve group, split by a vertical separator as in Quantize;
        // extra width goes to empty space after them.
        dWb options = dWb.IwV(12);
        options.cfM(new NaturalWidthBox(pointGroup), 0.0f);
        options.Hnt(14);
        options.cfM(dXp.gfw(), 0.0f); // a vertical separator
        options.Hnt(14);
        options.cfM(new NaturalWidthBox(curveGroup), 0.0f);
        options.JJY(); // stretchable space
        outer.HL(options);

        // A darker horizontal rule above the buttons, as in Quantize (sampled #545454).
        outer.Hnt(12);
        outer.HL(dXp.cfM(ruleColour())); // a horizontal separator in that colour
        outer.Hnt(12);
        buttons = dWb.IwV(8);
        outer.HL(buttons);
        root.HL(outer);
    }

    /** Adds the buttons: right-aligned OK and Cancel, as in Bitwig's dialogs. */
    @Override
    public void cfM(dWb root) {
        dWb row = buttons != null ? buttons : root;
        row.JJY(); // stretchable space
        E3(row); // Bitwig's standard dialog buttons
        lYc(row);
        hUR(row);
        TMF(row);
        UNj(row);
        IwV(row);
        wGc(row);
        FzB(row);
    }

    /** OK: adds the points; the dialog closes only if that worked. */
    @Override
    public void ke_() {
        if (applying) return;
        applying = true;
        boolean ok = false;
        try {
            ok = AutomationPointsEdit.apply(lane, start, end, addPoint1.getBoolValue(), addPoint2.getBoolValue(),
                    normalized(point1.getDoubleValue()), normalized(point2.getDoubleValue()),
                    curve.getActual()); // the signed curve, not the drag view
        } catch (Throwable t) {
            AddAutomationPoints.MOD.log("adding automation points failed", t);
        } finally {
            applying = false;
        }
        if (ok) super.ke_(); // close
    }

    /** A value in the parameter's units back to 0..1. */
    private double normalized(double value) {
        if (domain == null) return Math.max(0, Math.min(1, value));
        try {
            return Math.max(0, Math.min(1, domain.cfM(value))); // value to 0..1
        } catch (Throwable t) {
            return 0;
        }
    }

    /** A switched-off point's field is faded and can't be edited. */
    private void syncPointStates() {
        boolean on1 = addPoint1.getBoolValue();
        boolean on2 = addPoint2.getBoolValue();
        try {
            point1Field.LzO(!on1); // block input
            point1Field.Txc(on1 ? 1.0 : 0.45); // opacity
            point1Field.jbi(); // repaint
        } catch (Throwable ignored) {
            // cosmetic
        }
        try {
            point2Field.LzO(!on2);
            point2Field.Txc(on2 ? 1.0 : 0.45);
            point2Field.jbi();
        } catch (Throwable ignored) {
            // cosmetic
        }
        point1.setSettable(on1);
        point2.setSettable(on2);
    }

    private static eeY ruleColour() {
        eeY c = ruleColour;
        if (c == null) {
            synchronized (AutomationPointsDialog.class) {
                c = ruleColour;
                if (c == null) ruleColour = c = eeY.cfM("Add Automation Points dialog rule", iok.cfM(84, 84, 84)); // a fixed colour
            }
        }
        return c;
    }

    /** A row with an exact width; the widget in it behaves normally. */
    static final class FixedWidthBox extends dWb {
        private final long width;

        FixedWidthBox(long width, dve child) {
            super();
            this.width = width;
            FzB(IBe.cfM); // horizontal
            H_();
            HL(child);
        }

        @Override
        public long TtY(IBe axis) { // minimum size
            return axis == IBe.cfM ? width : super.TtY(axis);
        }

        @Override
        public long iHb(IBe axis) { // maximum size
            return axis == IBe.cfM ? width : super.iHb(axis);
        }
    }

    /** A row no wider than it needs, so spare dialog width doesn't push the separator sideways. */
    static final class NaturalWidthBox extends dWb {
        NaturalWidthBox(dve child) {
            super();
            FzB(IBe.cfM); // horizontal
            H_();
            HL(child);
        }

        @Override
        public long iHb(IBe axis) { // maximum size: the minimum, horizontally
            return axis == IBe.cfM ? TtY(axis) : super.iHb(axis);
        }
    }

    /**
     * The curve editor, in the look of Bitwig's ADSR shape cells: a number field (its border, focus,
     * drag and Ctrl+click typing are Bitwig's) that paints a dark cell and the curve from Point 1 to
     * Point 2. Dragging shows the value next to the pointer; double-click resets it to 0%. On a
     * descending curve, dragging up bends it the way it looks (the stored value's sign is flipped
     * while dragging; see {@link Num}).
     */
    static final class CurveField extends dND {
        // The theme's colours, as Bitwig's ADSR cells use them: the dark display background, the
        // accent for the curve, and halfway between the two for the area under it (sampled from
        // Bitwig's ADSR cell: #1C1C1C, #FF8713, #8D5117). Fallbacks for a theme without them.
        private static final iok CELL = iok.cfM(28, 28, 28);
        private static final iok EDGE = iok.cfM(255, 135, 19);
        private final Num value;
        private final Num point1;
        private final Num point2;

        private CurveField(Num value, Num point1, Num point2) {
            super();
            this.value = value;
            this.point1 = point1;
            this.point2 = point2;
        }

        /** Set up as Bitwig's factory sets up a plain number field. */
        static CurveField create(Num value, Num point1, Num point2) {
            CurveField f = new CurveField(value, point1, point2);
            f.ztx(); // initialise
            f.H_();
            f.Hnt(value); // its value
            f.cfM(new BlankCurveParts()); // no text: the curve is painted
            f.FzB(PERCENT.jD_()); // typed values are percentages
            return f;
        }

        /*
         * Its width: 64 px, wide enough to see the curve (Bitwig would size it from its text, which
         * is blank). Set as its minimum and maximum width, as for the toggles' cells.
         */
        private static final long WIDTH = 64;

        @Override
        public long TtY(IBe axis) { // minimum size
            return axis == IBe.cfM ? WIDTH : super.TtY(axis);
        }

        @Override
        public long iHb(IBe axis) { // maximum size
            return axis == IBe.cfM ? WIDTH : super.iHb(axis);
        }

        /**
         * The rectangle of an editable part: the whole field (Bitwig sizes it from the text, which
         * is blank here). Package-private in Bitwig, so this class is in the same package.
         */
        @Override
        public FloatRectangle Hnt(int part) {
            return new FloatRectangle(0.0f, 0.0f, (float) Sxo(), (float) yiX()); // its width and height
        }

        private String feedback() {
            return String.format(Locale.ROOT, "%.1f %%", value.getActual() * 100.0);
        }

        /** The action for a pointer event: Bitwig's drag, with the value shown next to the pointer. */
        @Override
        public drX MRY(eoW type, double x, double y, eoV mods, int clicks) {
            // The drag direction must be set before Bitwig's drag action reads the value.
            value.setDragView(descending());
            drX action = super.MRY(type, x, y, mods, clicks);
            if (action instanceof dNF) {
                value.setDragView(descending());
                return new CurveAdjustAction(this, 0);
            }
            value.setDragView(false);
            return action;
        }

        private void resetToDefault() {
            value.setDoubleValue(0.0);
            try {
                jbi(); // repaint
            } catch (Throwable ignored) {
                // cosmetic
            }
        }

        /** Double-click: reset to 0% (Ctrl+click typing is a different action and stays Bitwig's). */
        @Override
        public doW cfM(double x, double y, eoV mods, eoJ source) {
            return new doW(dpN.hbP("mouse.set_to_default.desc"), ignored -> resetToDefault()); // Bitwig's "set to default" text
        }

        @Override
        public void cfM(SYo g) { // paint
            super.cfM(g); // Bitwig's border and focus
            try {
                final double w = Sxo(), h = yiX(); // width, height
                if (w < 8 || h < 8) return;
                final iok cell = themeColour("Display Background", CELL);
                final iok edge = themeColour("Accent (default)", EDGE);
                final iok fill = cell.cfM(edge, 0.5f); // halfway from the cell to the accent
                // The dark interior, leaving a 1 px rim for Bitwig's border.
                g.cfM(1.0, 1.0, Math.max(0.0, w - 2.0), Math.max(0.0, h - 2.0), cell); // fill a rectangle

                final double pad = 5.0;
                final double x0 = pad, bottom = h - pad;
                final double x3 = w - pad, top = pad;
                final double dx = x3 - x0;
                // From Point 1 to Point 2: a descending range previews a falling curve.
                final double ys = descending() ? top : bottom, ye = descending() ? bottom : top;
                final double c = Math.max(-1.0, Math.min(1.0, value.getActual()));
                // Bitwig's positive curvature rises early and flattens towards the end.
                final double bend = 0.48 * c;
                final double p1 = Math.max(0.0, Math.min(1.0, (1.0 / 3.0) + bend));
                final double p2 = Math.max(0.0, Math.min(1.0, (2.0 / 3.0) + bend));
                final double cx1 = x0 + dx / 3.0;
                final double cy1 = ys + (ye - ys) * p1;
                final double cx2 = x0 + 2.0 * dx / 3.0;
                final double cy2 = ys + (ye - ys) * p2;

                // The area under the curve in the darker orange, the curve in the bright one.
                g.nVd(); // new path
                g.MRY(x0, ys); // move to
                g.cfM(cx1, cy1, cx2, cy2, x3, ye); // curve to
                g.cfM(x3, Math.max(ys, ye)); // line to
                g.cfM(x0, Math.max(ys, ye));
                g.Hnt(fill); // fill colour
                g.wGc(); // fill

                g.nVd();
                g.MRY(edge); // stroke colour
                g.IwV(1.0); // line width
                g.MRY(x0, ys);
                g.cfM(cx1, cy1, cx2, cy2, x3, ye);
                g.hbP(); // stroke
            } catch (Throwable ignored) {
                // cosmetic
            }
        }

        /** One of the theme's named colours for this field, or the fallback. */
        private iok themeColour(String name, iok fallback) {
            try {
                eeY colour = jl_().BAa().cfM(name); // the theme's colours, by name
                iok c = colour != null ? colour.cfM(this) : null;
                return c != null ? c : fallback;
            } catch (Throwable t) {
                return fallback;
            }
        }

        private boolean descending() {
            return Double.compare(point1.getActual(), point2.getActual()) > 0;
        }

        void redraw() {
            jbi(); // repaint
        }
    }

    /** Bitwig's number field drag, with Bitwig's value bubble next to the pointer. */
    static final class CurveAdjustAction extends dNF {
        private final CurveField field;

        CurveAdjustAction(CurveField field, int part) {
            super(field, part);
            this.field = field;
        }

        /** Asked on every drag update: the text in the bubble. */
        @Override
        public LJt FzB(dJM drag) {
            return vjp.cfM(field.feedback());
        }

        /** Where the bubble goes: placed as for Bitwig's parameter drags. */
        @Override
        public dsP d_(double x, double y) {
            dOZ side = field.gkO() ? dOZ.cfM : dOZ.IwV; // which side has room
            return new dPi(field, dOZ.FzB, side, 0L, field.eLY(2L));
        }

        @Override
        public String Hnt(double x, double y, eoV mods) { // its description
            return "Adjust curve";
        }
    }

    /** The curve field's text: none (the curve is painted). */
    static final class BlankCurveParts implements dNH {
        @Override
        public int cfM() { // number of parts
            return 1;
        }

        @Override
        public String cfM(int part) { // a part's name
            return part == 0 ? "curve" : null;
        }

        @Override
        public String cfM(double v, int part) { // a part's text for a value
            return part == 0 ? "" : null;
        }

        @Override
        public double MRY(int part) { // a part's drag step
            return part == 0 ? 0.02 : 0.0;
        }

        @Override
        public String Hnt(int part) { // a part's widest text, for sizing
            return part == 0 ? " " : null;
        }
    }

    /** A point field's text: the value in the parameter's units. */
    static final class FormatterParts implements dNH {
        private final dOu format;
        private final double step;
        private final String widest;

        FormatterParts(dOu format, double min, double max) {
            this.format = format;
            double span = Math.abs(max - min);
            this.step = span > 0 && Double.isFinite(span) ? Math.max(span / 100.0, 1e-9) : 0.01;
            String a = text(min), b = text((min + max) * 0.5), c = text(max);
            String w = "0";
            for (String s : List.of(a, b, c)) if (s.length() > w.length()) w = s;
            this.widest = w;
        }

        private String text(double v) {
            try {
                String s = format.cfM(v); // format
                return s != null ? s : Double.toString(v);
            } catch (Throwable t) {
                return Double.toString(v);
            }
        }

        @Override
        public int cfM() {
            return 1;
        }

        @Override
        public String cfM(int part) {
            return part == 0 ? "value" : null;
        }

        @Override
        public String cfM(double value, int part) {
            return part == 0 ? text(value) : null;
        }

        @Override
        public double MRY(int part) {
            return part == 0 ? step : 0.0;
        }

        @Override
        public String Hnt(int part) {
            return part == 0 ? widest : null;
        }
    }

    /** Formats and parses values in the automated parameter's units. */
    static final class DomainFormatter implements dOu {
        private final aL domain;

        DomainFormatter(aL domain) {
            this.domain = domain;
        }

        @Override
        public String cfM(double value) { // format
            try {
                return domain.wGc(value).get(); // the domain's text for it
            } catch (Throwable t) {
                return Double.toString(value);
            }
        }

        @Override
        public String cfM(double value, boolean a, boolean b) {
            return cfM(value);
        }

        @Override
        public double cfM(String text, double old) { // parse
            try {
                double v = fY.cfM(text, domain); // parse in the domain's units
                if (!Double.isFinite(v)) return old;
                return Math.max(Math.min(domain.FzB(), domain.UNj()), Math.min(Math.max(domain.FzB(), domain.UNj()), v));
            } catch (Throwable t) {
                return old;
            }
        }
    }

    /** Formats and parses 0..1 as a percentage (when the parameter's domain is unknown). */
    static final dOu PERCENT = new dOu() {
        @Override
        public String cfM(double v) {
            return String.format(Locale.ROOT, "%.2f%%", v * 100);
        }

        @Override
        public String cfM(double v, boolean a, boolean b) {
            return cfM(v);
        }

        @Override
        public double cfM(String s, double old) {
            try {
                String t = s.trim();
                boolean percent = t.contains("%");
                t = t.replace("%", "").trim();
                double v = Double.parseDouble(t);
                if (percent || Math.abs(v) > 1) v /= 100;
                return v;
            } catch (Throwable e) {
                return old;
            }
        }
    };

    /**
     * A number value for the dialog's fields. For the curve it also has a drag view: while a
     * descending curve is dragged, Bitwig's field sees the value with its sign flipped, so dragging
     * up bends the curve the way it looks; {@link #getActual()} is always the stored value.
     */
    static final class Num implements ujq {
        private final List<foz> listeners = new CopyOnWriteArrayList<>();
        final double min, max;
        private volatile double value;
        private volatile boolean settable = true;
        private volatile boolean dragView;
        private CurveField preview;

        Num(double value, double min, double max) {
            this.min = min;
            this.max = max;
            this.value = clamp(value);
        }

        private double clamp(double x) {
            return Math.max(min, Math.min(max, x));
        }

        void setSettable(boolean settable) {
            this.settable = settable;
        }

        void attachPreview(CurveField field) {
            preview = field;
        }

        /** The stored value, whatever the drag view. */
        double getActual() {
            return value;
        }

        /** Switches the drag view; Bitwig's field is told when that changes the value it sees. */
        void setDragView(boolean on) {
            double before = project(value);
            dragView = on;
            double after = project(value);
            if (Double.compare(before, after) != 0) fire(before, after);
        }

        private double project(double v) {
            return dragView ? -v : v;
        }

        private void set(double x) {
            x = project(x);
            if (!settable) return;
            double old = value, n = clamp(x);
            value = n;
            if (Double.compare(old, n) != 0) fire(project(old), project(n));
            if (preview != null) preview.redraw();
        }

        private void fire(double before, double after) {
            VGL event = new VGL(this, before, after); // a value change
            for (foz l : listeners) {
                try {
                    l.eventOccurred(event);
                } catch (Throwable ignored) {
                    // a listener's failure isn't ours
                }
            }
        }

        @Override public Object getValue() { return project(value); }
        @Override public boolean isSettable() { return settable; }
        @Override public void setValue(Object o) { if (o instanceof Number n) set(n.doubleValue()); }
        @Override public boolean isObservable() { return true; }
        @Override public Number getMinValue() { return min; }
        @Override public Number getMaxValue() { return max; }
        @Override public int getIntegerValue() { return (int) Math.round(project(value)); }
        @Override public long getLongValue() { return Math.round(project(value)); }
        @Override public double getDoubleValue() { return project(value); }
        @Override public void setIntegerValue(int x) { set(x); }
        @Override public void setLongValue(long x) { set(x); }
        @Override public void setFloatValue(float x) { set(x); }
        @Override public void setDoubleValue(double x) { set(x); }
        @Override public void addListener(foz l) { if (l != null) listeners.add(l); }
        @Override public void removeListener(foz l) { listeners.remove(l); }
    }
}
