import bitwig.obf.DEl; // an automation point with a value and a curve
import bitwig.obf.Dxn; // an automation lane shown but not created yet
import bitwig.obf.GVR; // a lane's automation: its own curve and its automation clips
import bitwig.obf.PQT; // an automation curve: its points
import bitwig.obf.Tgk; // an automation lane
import bitwig.obf.YvI; // a point as the curve inserts it
import bitwig.obf.cU; // Bitwig's mapping from arranger time to a clip's own time
import bitwig.obf.hMP; // a point the curve created
import bitwig.obf.jRs; // a point's kind
import bitwig.obf.la1; // an automation clip
import bitwig.obf.lkr; // an automation lane that exists in the document
import bitwig.obf.qae; // a point as the curve removes and indexes it
import bitwig.obf.vcm; // the curve's point factory
import bitwig.obf.yiy; // an automation lane with its own automation
import com.bitwig.flt.document.core.master.IOJ; // an undoable change
import com.bitwig.ramona.core.oDc; // a document object that undoable changes belong to
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the points into the automation, as one Undo step ("Add Automation Points").
 *
 * Points are created by the curve's own factory and inserted after any point already at the same
 * time. Bitwig's Insert Point operation isn't used: it changes the curve of the point before and
 * inserts before points at the same time.
 *
 * Where the points go is decided as Bitwig decides where a lane's value comes from: inside an
 * automation clip, the clip's own curve, at the clip's own time (its position, play start and
 * loop, mapped by Bitwig's function); elsewhere, the lane's own curve.
 */
final class AutomationPointsEdit {
    private static final double EPS = 1e-9;

    private AutomationPointsEdit() {
    }

    /**
     * Where a time selection's points go: a curve and the selection's start and end in that curve's
     * time; or, when that can't be done exactly, why ({@code points} null).
     */
    record Target(PQT points, double start, double end, String problem) {
        static Target refused(String problem) {
            return new Target(null, Double.NaN, Double.NaN, problem);
        }
    }

    /**
     * The target of {@code start..end} (arranger time) on a lane's automation: the lane's own curve
     * when no automation clip overlaps the range; the clip's curve when the range lies inside one
     * clip and inside one pass of its loop; refused otherwise (partly over a clip, over several
     * clips, or across the end of a clip's loop), since no single place holds those points.
     */
    static Target target(GVR automation, double start, double end) {
        List<la1> overlapping = new ArrayList<>();
        for (Object event : automation.cfM()) { // its events: the automation clips
            if (event instanceof la1 clip && clip.x3t() < end - EPS && clip.QWG() > start + EPS) overlapping.add(clip); // start, end
        }
        if (overlapping.isEmpty()) {
            PQT own = automation.tSS(); // the lane's own curve
            return own != null ? new Target(own, start, end, null) : Target.refused("the lane has no automation curve");
        }
        if (overlapping.size() > 1) return Target.refused("the time selection covers more than one automation clip");
        la1 clip = overlapping.get(0);
        if (start < clip.x3t() - EPS || end > clip.QWG() + EPS)
            return Target.refused("the time selection is only partly inside an automation clip");
        double clipStart = clipTime(clip, start);
        double clipEnd = clipStart + (end - start);
        // Within one pass of the clip, its time runs with the arranger's; the end mapped on its own
        // must agree, except at the end of a loop pass, where Bitwig's mapping starts the next pass.
        double mappedEnd = clipTime(clip, end);
        boolean atLoopEnd = clip.sut() // loop on
                && Math.abs(clipEnd - (clip.LIK().x3t() + clip.LIK().nfq())) < EPS // loop start + length
                && Math.abs(mappedEnd - clip.LIK().x3t()) < EPS;
        if (Math.abs(mappedEnd - clipEnd) > EPS && !atLoopEnd)
            return Target.refused("the time selection crosses the end of the automation clip's loop");
        PQT points = clip.SVQ().TD2(); // the clip's content, its curve
        return points != null ? new Target(points, clipStart, clipEnd, null) : Target.refused("the automation clip has no curve");
    }

    /** An arranger time in a clip's own time, as Bitwig maps it when it reads a value from the clip. */
    private static double clipTime(la1 clip, double time) {
        var loop = clip.LIK(); // its loop
        return cU.cfM(clip.x3t(), clip.dC_(), clip.sut(), loop.x3t(), loop.nfq(), time); // start, play start, loop on, loop start, length
    }

    /**
     * Adds the chosen points over {@code start..end}: Point 1 at the start with {@code value1},
     * Point 2 at the end with {@code value2} (values 0..1); {@code curve} (-1..1) is the curve from
     * Point 1, or from the last point in the range when only Point 2 is added. With both points,
     * the points already in the range are replaced. {@code start..end} is in arranger time (or the
     * lane's own time); see {@link #target}. Returns whether it worked; on failure what it did is
     * undone.
     */
    static boolean apply(Tgk lane, double start, double end, boolean addPoint1, boolean addPoint2,
                         double value1, double value2, double curve) {
        if (!addPoint1 && !addPoint2) return true; // nothing to add, no Undo step
        IOJ change = null;
        PQT points = null;
        hMP inserted1 = null, inserted2 = null;
        DEl curved = null;
        double oldCurve = Double.NaN;
        List<Removed> removed = new ArrayList<>();
        try {
            if (lane == null) throw new IllegalStateException("no automation lane");
            if (!(Double.isFinite(start) && Double.isFinite(end)) || end <= start)
                throw new IllegalArgumentException("bad range " + start + ".." + end);
            value1 = clamp(value1, 0, 1);
            value2 = clamp(value2, 0, 1);
            curve = clamp(curve, -1, 1);

            if (!(AddAutomationPoints.document(lane) instanceof oDc document))
                throw new IllegalStateException("the lane's document can't be found");
            change = new IOJ();
            if (!change.cfM(document, "Add Automation Points")) { // begin
                change = null;
                throw new IllegalStateException("the Undo step couldn't be started");
            }

            if (lane instanceof Dxn notCreated) {
                lkr created = notCreated.jG(); // create it
                if (created == null) throw new IllegalStateException("the automation lane couldn't be created");
                lane = created;
            }
            if (!(lane instanceof yiy own)) throw new IllegalStateException("not an automation lane: " + lane.getClass().getName());
            var automation = own.LaE(); // its automation
            if (automation == null) throw new IllegalStateException("the lane has no automation");
            Target target = target(automation, start, end);
            if (target.points() == null) throw new IllegalStateException(target.problem());
            points = target.points();
            start = target.start();
            end = target.end();

            if (addPoint1 && addPoint2) removeInRange(points, start, end, removed);
            if (addPoint1) {
                inserted1 = insertAfterEqual(points, start, value1);
                if (!(inserted1 instanceof DEl p1)) throw new IllegalStateException("Point 1 has no curve: " + inserted1.getClass().getName());
                curved = p1;
                oldCurve = p1.dS_(); // its curve
            } else {
                curved = lastInRange(points, start, end);
                if (curved != null) oldCurve = curved.dS_();
            }
            if (addPoint2) inserted2 = insertAfterEqual(points, end, value2);
            if (curved != null) curved.MRY(curve); // set its curve
            return true;
        } catch (Throwable t) {
            AddAutomationPoints.MOD.log("adding automation points failed; undoing", t);
            if (curved != null && Double.isFinite(oldCurve)) try { curved.MRY(oldCurve); } catch (Throwable ignored) { }
            if (points != null && inserted2 != null) try { points.cfM((qae) inserted2); } catch (Throwable ignored) { } // remove
            if (points != null && inserted1 != null) try { points.cfM((qae) inserted1); } catch (Throwable ignored) { }
            restore(points, removed);
            return false;
        } finally {
            if (change != null) {
                try {
                    change.MRY(); // finish the Undo step
                } catch (Throwable t) {
                    AddAutomationPoints.MOD.log("finishing the Undo step failed", t);
                }
            }
        }
    }

    /** Creates a point and inserts it after every point already at the same time. */
    private static hMP insertAfterEqual(PQT points, double time, double value) {
        vcm factory = points.bS_(); // the curve's point factory
        hMP point = points.cfM(time, clamp(value, 0, 1), jRs.MRY, 0.0, factory); // a new point
        if (point == null) throw new IllegalStateException("the curve created no point");
        int index = points.Hnt(time, false); // false: after the last point at that time
        points.Hnt((YvI) point, index); // insert
        points.MRY((qae) point); // index it
        return point;
    }

    /** The last point with start <= time < end (of several at one time, the last). */
    private static DEl lastInRange(PQT points, double start, double end) {
        DEl last = null;
        double lastTime = Double.NEGATIVE_INFINITY;
        for (Object o : points.cfM()) { // its points
            if (o instanceof DEl p) {
                double t = p.x3t(); // its time
                if (t + EPS >= start && t < end - EPS && t > lastTime - EPS) {
                    last = p;
                    lastTime = t;
                }
            }
        }
        return last;
    }

    private record Removed(Object point, int index) {
    }

    /** Removes the points in start..end (both included), noting them so a failure can restore them. */
    private static void removeInRange(PQT points, double start, double end, List<Removed> removed) {
        List<?> before = new ArrayList<>(points.cfM()); // its points
        for (int i = 0; i < before.size(); i++) {
            if (!(before.get(i) instanceof DEl p)) continue;
            double t = p.x3t(); // its time
            if (!Double.isFinite(t) || t < start - EPS || t > end + EPS) continue;
            removed.add(new Removed(p, i));
            points.cfM((qae) p); // remove
        }
    }

    /** Puts removed points back where they were (after a failure). */
    private static void restore(PQT points, List<Removed> removed) {
        if (points == null) return;
        for (Removed r : removed) {
            try {
                hMP point = (hMP) r.point();
                if (points.cfM().stream().anyMatch(o -> o == point)) continue; // still there
                int index = Math.max(0, Math.min(r.index(), points.cfM().size()));
                points.Hnt(point, index); // insert
                points.MRY((qae) point); // index it
            } catch (Throwable t) {
                AddAutomationPoints.MOD.log("restoring a removed point failed", t);
            }
        }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
