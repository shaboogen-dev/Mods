package com.bitwig.mods.linewaveforms;

import com.bitwig.graphics.SUv; // how path segments join
import com.bitwig.graphics.SYo; // a graphics context
import com.bitwig.graphics.gJi; // a graphics context that wraps another

import bitwig.obf.iok; // a colour

/**
 * Draws one lane's waveform. One per painting thread, so its buffers are reused and nothing is allocated per
 * point: only one colour object per run of a colour.
 *
 * <p>How a lane is drawn depends on how many samples fall on one step of the drawing. A step is what Bitwig
 * reads per column: up to 4 per pixel at 100% UI scale.
 * <ul>
 * <li>up to {@link #SAMPLES_PER_COLUMN_FOR_LINE} per column: a line through every step's value;
 * <li>above that: per column the lowest and highest value, in time order, joined to the next column, so a
 *     lane never takes more than about two points per column at any zoom;
 * <li>from {@link #FADE_START} to {@link #FADE_END} per step: that trace over Bitwig's fill, trading alpha;
 * <li>from {@link #FADE_END}, Bitwig reads its peak cache instead of samples: Bitwig's fill alone.
 * </ul>
 */
final class LanePainter {
    private static final ThreadLocal<LanePainter> CURRENT = ThreadLocal.withInitial(LanePainter::new);

    private static final double SAMPLES_PER_COLUMN_FOR_LINE = 1.0;
    private static final double FADE_START = 16.0;
    /** Bitwig's own limit for reading samples rather than its peak cache. */
    private static final double FADE_END = 64.0;
    /** A long path is stroked in pieces of this many points. */
    private static final int POINTS_PER_STROKE = 2048;
    /** A point is left out when the line stays within this many pixels of it. */
    private static final double SIMPLIFY_PIXELS = 0.125;

    static LanePainter current() {
        return CURRENT.get();
    }

    // The lane being read. Every channel of every clip is read before any channel is drawn.
    private double samplesPerStep;
    private boolean rawRead;
    private boolean drawStarted;

    // The channel about to be drawn, from prepare to draw.
    private boolean lineReady;
    private float[] linear = new float[0];
    private int[] plainColours;
    private double gain;
    private boolean perceptual;

    // The path being built.
    private SYo output;
    private double centre;
    private double half;
    private double top;
    private double bottom;
    private double tolerance;
    private boolean open;
    private int pointsInStroke;
    private double anchorX;
    private double anchorValue;
    private double lastX;
    private double lastValue;
    private double steepest;
    private double shallowest;

    void noteRead(final double samples) {
        if (drawStarted) { // a new lane
            samplesPerStep = 0.0;
            rawRead = false;
            drawStarted = false;
        }
        samplesPerStep = Math.max(samplesPerStep, samples);
    }

    void noteRawRead() {
        rawRead = true;
    }

    /** Keeps the values for the line, then gives Bitwig's painter what it expects: zero in every range. */
    void prepare(final float[] values, final int[] colours, final double gain, final boolean perceptual) {
        drawStarted = true;
        lineReady = false;
        if (!rawRead) {
            return; // only peak cache data: Bitwig's values are untouched
        }
        if (samplesPerStep < FADE_END && colours != null) {
            if (linear.length < values.length) {
                linear = new float[values.length];
            }
            System.arraycopy(values, 0, linear, 0, values.length);
            plainColours = colours;
            this.gain = gain;
            this.perceptual = perceptual;
            lineReady = true;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            final float a = values[i];
            final float b = values[i + 1];
            values[i] = Math.min(0f, Math.min(a, b));
            values[i + 1] = Math.max(0f, Math.max(a, b));
        }
    }

    void draw(final SYo graphics, final long x, final long y, final int columns, final int height,
              final boolean[] shown, final float[] values, final int[] colours, final int steps, final boolean fromZero) {
        if (!lineReady) {
            graphics.cfM(x, y, columns, height, shown, values, colours, steps, fromZero); // Bitwig's own drawing
            return;
        }
        lineReady = false;
        final double fill = fillAmount(samplesPerStep);
        if (fill > 0.0) {
            graphics.cfM(x, y, columns, height, shown, values, faded(colours, fill), steps, fromZero); // Bitwig's fill, part faded
        }
        final boolean line = samplesPerStep * steps <= SAMPLES_PER_COLUMN_FOR_LINE;
        stroke(graphics, x, y, columns, height, shown, steps, (float) (1.0 - fill), line);
    }

    /** 0 is no fill, 1 is all fill; even steps per doubling of samples. */
    private static double fillAmount(final double samples) {
        if (samples <= FADE_START) {
            return 0.0;
        }
        return Math.min(1.0, Math.log(samples / FADE_START) / Math.log(FADE_END / FADE_START));
    }

    /** Bitwig's colours here have alpha multiplied into each channel, so all four bytes scale alike. */
    private int[] faded(final int[] colours, final double amount) {
        // Bitwig's painter made this array for the call, unless it held a single colour for every column.
        final int[] out = colours == plainColours ? colours.clone() : colours;
        final int scale = (int) Math.round(amount * 256.0);
        for (int i = 0; i < colours.length; i++) {
            final int c = colours[i];
            out[i] = ((c >>> 8 & 0x00FF00FF) * scale & 0xFF00FF00) | ((c & 0x00FF00FF) * scale >>> 8 & 0x00FF00FF);
        }
        return out;
    }

    private void stroke(final SYo graphics, final long x, final long y, final int columns, final int height,
                        final boolean[] shown, final int steps, final float alpha, final boolean line) {
        output = graphics;
        half = height * 0.5;
        centre = y + half;
        top = y;
        bottom = y + (double) height;
        final double pixel = SIMPLIFY_PIXELS / Math.max(half, 1.0);
        tolerance = perceptual ? pixel * pixel : pixel; // the square root is steepest near zero
        open = false;
        final SYo plain = withoutWrappers(graphics);
        final int[] colours = plainColours;
        final boolean oneColour = colours.length == 1;
        int runColour = 0;
        boolean colourSet = false;
        graphics.qoU(); // save
        try {
            graphics.IwV(Math.max(1L, Math.round(graphics.Hnt()))); // line width: 1 pixel at 100% UI scale
            graphics.cfM(SUv.Hnt); // bevel joins: mitres would spike past the peaks
            for (int column = 0; column < columns; column++) {
                if (shown != null && !shown[column]) {
                    endPath();
                    continue;
                }
                final int colour = colours[oneColour ? 0 : column];
                if (!colourSet || colour != runColour) {
                    final boolean carryOn = open;
                    final double joinX = lastX;
                    final double joinValue = lastValue;
                    endPath();
                    plain.Hnt(iok.Hnt(colour).cfM(alpha)); // already has the clip's state in it: skip wrappers that would apply it again
                    runColour = colour;
                    colourSet = true;
                    if (carryOn) {
                        start(joinX, joinValue);
                    }
                }
                if (line) {
                    addSteps(x, column, steps);
                } else {
                    addColumn(x, column, steps);
                }
            }
            endPath();
        } finally {
            graphics.ucQ(); // restore
            output = null;
        }
    }

    private static SYo withoutWrappers(final SYo graphics) {
        SYo plain = graphics;
        while (plain instanceof gJi wrapper) {
            plain = wrapper.cfM; // the graphics context it wraps
        }
        return plain;
    }

    private void addSteps(final long x, final int column, final int steps) {
        final double left = x + (double) column;
        final float[] values = linear;
        int at = column * steps * 2;
        for (int step = 0; step < steps; step++, at += 2) {
            add(left + (step + 0.5) / steps, (values[at] + values[at + 1]) * 0.5 * gain);
        }
    }

    /** The column's lowest and highest value, in the order they happen. */
    private void addColumn(final long x, final int column, final int steps) {
        final float[] values = linear;
        final int from = column * steps * 2;
        final int to = from + steps * 2;
        float least = values[from];
        float most = least;
        int leastAt = from;
        int mostAt = from;
        for (int i = from + 1; i < to; i++) {
            final float value = values[i];
            if (value < least) {
                least = value;
                leastAt = i;
            } else if (value > most) {
                most = value;
                mostAt = i;
            }
        }
        final double left = x + (double) column;
        final boolean leastFirst = leastAt <= mostAt;
        add(left + 0.25, (leastFirst ? least : most) * gain);
        add(left + 0.75, (leastFirst ? most : least) * gain);
    }

    private void start(final double x, final double value) {
        output.MRY(x, toY(value)); // move to
        pointsInStroke = 1;
        open = true;
        anchorX = x;
        anchorValue = value;
        lastX = x;
        lastValue = value;
        steepest = Double.POSITIVE_INFINITY;
        shallowest = Double.NEGATIVE_INFINITY;
    }

    /**
     * Adds a point, leaving out the one before it when a straight line from the last point drawn still passes
     * within the tolerance of everything since. Between samples Bitwig's values lie on a straight line, so what
     * remains are the real sample points, and a silent stretch is one long segment.
     */
    private void add(final double x, final double value) {
        if (!open) {
            start(x, value);
            return;
        }
        final double width = x - anchorX;
        final double rise = value - anchorValue;
        final double high = Math.min(steepest, (rise + tolerance) / width);
        final double low = Math.max(shallowest, (rise - tolerance) / width);
        if (low > high) {
            lineToLast();
            final double newWidth = x - anchorX;
            final double newRise = value - anchorValue;
            steepest = (newRise + tolerance) / newWidth;
            shallowest = (newRise - tolerance) / newWidth;
        } else {
            steepest = high;
            shallowest = low;
        }
        lastX = x;
        lastValue = value;
    }

    private void lineToLast() {
        output.cfM(lastX, toY(lastValue)); // line to
        anchorX = lastX;
        anchorValue = lastValue;
        if (++pointsInStroke >= POINTS_PER_STROKE) {
            output.hbP(); // stroke
            output.MRY(lastX, toY(lastValue)); // move to: the line carries on from here
            pointsInStroke = 1;
        }
    }

    private void endPath() {
        if (!open) {
            return;
        }
        if (lastX != anchorX) {
            lineToLast();
        }
        output.hbP(); // stroke
        open = false;
    }

    private double toY(final double value) {
        final double shaped = !perceptual ? value : value < 0.0 ? -Math.sqrt(-value) : Math.sqrt(value);
        final double y = centre - half * shaped;
        return y < top ? top : Math.min(y, bottom);
    }
}
