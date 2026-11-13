import bitwig.obf.LEn; // an automation lane's document link
import bitwig.obf.Tgk; // an automation lane
import bitwig.obf.aL; // a parameter's value domain: its range, units and formatting
import bitwig.obf.big; // a view's selection
import bitwig.obf.bih; // the context of an automation time selection
import bitwig.obf.bnW; // a lane row, which shows an automation lane
import bitwig.obf.dve; // a widget
import bitwig.obf.lkr; // an automation lane that exists in the document
import bitwig.obf.qiX; // a parameter's description, which holds its value domain
import bitwig.obf.wSr; // the automation editor's time selection
import bitwig.obf.wyB; // a time selection's range
import bitwig.obf.yiy; // an automation lane with its own automation
import bml.actions.ActionContext;
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.ramona.core.AVL; // a document object, which can look up its ancestors
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Add Automation Points: adds a point at the start and the end of an automation time selection,
 * with the values and the curve chosen in a dialog.
 *
 * Bitwig's time selection is followed through three hooks (its range, whether it is active, and
 * the lane it was made on); the action "Add Automation Points" is declared through the Action and
 * Menu framework ({@link AddAutomationPointsActions}) and opens {@link AutomationPointsDialog}.
 *
 * The mod's classes are in Java's unnamed package, like Bitwig's obfuscated classes: the curve
 * field overrides a package-private method of Bitwig's number field, which only a class in the
 * same package can.
 */
public final class AddAutomationPoints {
    static final String ACTION_ID = "add_automation_points";
    static final String ACTION_LABEL = "Add Automation Points";
    static final String ACTION_MENU_LABEL = "Add Automation Points…";

    static final Bml.Mod MOD = Bml.mod("add-automation-points");

    private static volatile double from = Double.NaN;
    private static volatile double to = Double.NaN;
    private static volatile boolean active;
    private static volatile WeakReference<Tgk> currentLane = new WeakReference<>(null);

    private AddAutomationPoints() {
    }

    // ---------------------------------------------------------------------
    // Bitwig's time selection
    // ---------------------------------------------------------------------

    /** A time selection's start is set. */
    @Hook(target = wyB.class, method = "UNj(D)V", at = At.ENTRY)
    public static void timeSelectionFrom(@Arg(0) double value) {
        from = value;
    }

    /** A time selection's end is set. */
    @Hook(target = wyB.class, method = "hUR(D)V", at = At.ENTRY)
    public static void timeSelectionTo(@Arg(0) double value) {
        to = value;
    }

    /** The automation editor's time selection is switched on or off. */
    @Hook(target = wSr.class, method = "PD3(Z)V", at = At.ENTRY)
    public static void timeSelectionActive(@Arg(0) boolean value) {
        active = value;
    }

    /** A time selection's context changes its range: remember the lane it is on. */
    @Hook(target = bih.class, method = "cfM(DD)V", at = At.ENTRY)
    public static void timeSelectionContext(@This bih context) {
        try {
            Tgk lane = asLane(context.LEd()); // what the time selection is on
            if (lane != null) currentLane = new WeakReference<>(lane);
        } catch (Throwable t) {
            MOD.log("following the time selection's lane failed", t);
        }
    }

    static boolean hasTimeSelection() {
        return active && Double.isFinite(from) && Double.isFinite(to) && Math.abs(to - from) > 1e-12;
    }

    // ---------------------------------------------------------------------
    // The action
    // ---------------------------------------------------------------------

    /**
     * Whether the action is shown in a menu: there is a time selection on an automation lane, and
     * its points have one place to go (the lane's own curve or one automation clip).
     */
    static boolean canRun(ActionContext context) {
        if (!hasTimeSelection()) return false;
        Tgk lane = lane(context);
        return lane != null && problem(lane) == null;
    }

    /** Opens the dialog for the time selection. */
    static void run(ActionContext context) {
        Tgk lane = lane(context);
        if (!hasTimeSelection() || lane == null) {
            MOD.log("no time selection on an automation lane; nothing to do");
            return;
        }
        String problem = problem(lane);
        if (problem != null) {
            MOD.log(problem + "; nothing to do");
            return;
        }
        if (!(context.widget() instanceof dve parent)) {
            MOD.log("no window to open the dialog in");
            return;
        }
        currentLane = new WeakReference<>(lane);
        double start = Math.min(from, to), end = Math.max(from, to);
        aL domain = valueDomain(lane);
        double startValue = valueAt(lane, start, 0.25);
        double endValue = valueAt(lane, end, 0.75);
        new AutomationPointsDialog(lane, domain, start, end, startValue, endValue).FzB(parent); // show it over the window
    }

    /** Why the time selection's points have no single place to go on a lane, or null when they have. */
    private static String problem(Tgk lane) {
        try {
            if (lane instanceof yiy own && own.LaE() != null) { // its automation
                double start = Math.min(from, to), end = Math.max(from, to);
                return AutomationPointsEdit.target(own.LaE(), start, end).problem();
            }
            return null; // a lane not created yet: no clips, its own curve
        } catch (Throwable t) {
            MOD.log("checking the time selection against automation clips failed", t);
            return "the automation clips couldn't be read";
        }
    }

    /**
     * The automation lane the action runs on: in a right-click menu, the lane that was clicked;
     * otherwise (and if that can't be found) the lane of the last time selection.
     */
    private static Tgk lane(ActionContext context) {
        if (context.selection() != null) {
            Tgk clicked = null;
            if (context.widget() instanceof dve widget) clicked = laneOf(widget);
            if (clicked == null && context.selection() instanceof big selection) clicked = asLane(selection.LEd()); // what it is on
            if (clicked != null) return clicked;
        }
        return currentLane.get();
    }

    /** The automation lane a widget shows, from the models it (or a parent) holds. */
    private static Tgk laneOf(dve widget) {
        for (Class<?> type : List.of(Tgk.class, yiy.class, lkr.class, bnW.class)) {
            try {
                Tgk lane = asLane(widget.TMF(type)); // the widget's model of that type
                if (lane != null) return lane;
            } catch (Throwable ignored) {
                // not one this widget has
            }
        }
        return null;
    }

    private static Tgk asLane(Object o) {
        if (o instanceof Tgk lane) return lane;
        if (o instanceof bnW row) {
            Tgk lane = row.fJR(); // the lane it shows
            return lane != null ? lane : row.E3(); // or the lane it would create
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // The automated parameter
    // ---------------------------------------------------------------------

    /**
     * The automated parameter's value domain (dB, Hz, %...), as the Inspector shows a point's value:
     * from the parameter description of the lane or its automation, else the automation curve's own.
     */
    private static aL valueDomain(Tgk lane) {
        List<Object> candidates = new ArrayList<>();
        candidates.add(lane);
        aL curveDomain = null;
        if (lane instanceof yiy own) try {
            candidates.add(own.d3()); // its parameter
            var automation = own.LaE(); // its automation
            if (automation != null) {
                candidates.add(automation);
                var curve = automation.tSS(); // the automation curve
                if (curve != null) {
                    candidates.add(curve);
                    for (Object point : curve.cfM()) { // its points
                        candidates.add(point);
                        if (candidates.size() > 24) break;
                    }
                    if (curve.bS_() instanceof aL d) curveDomain = d; // the curve's own domain
                }
            }
        } catch (Throwable t) {
            MOD.log("reading the lane's parameter failed", t);
        }
        for (Object o : candidates) {
            if (!(o instanceof AVL node)) continue;
            try {
                if (node.hUR(qiX.class) instanceof qiX description) { // itself or its nearest ancestor of that type
                    aL d = description.gfw(); // its value domain
                    if (d != null) return d;
                }
            } catch (Throwable ignored) {
                // try the next one
            }
        }
        return curveDomain;
    }

    /** The automation's value (0..1) at a time, without creating a lane that doesn't exist yet. */
    private static double valueAt(Tgk lane, double time, double fallback) {
        try {
            if (lane instanceof yiy own) {
                double v = own.cfM(time, false); // the automation's value there
                if (Double.isFinite(v)) return Math.max(0, Math.min(1, v));
            }
        } catch (Throwable t) {
            MOD.log("reading the automation's value failed", t);
        }
        return fallback;
    }

    /** The project document an automation lane belongs to (for its Undo step). */
    static Object document(Tgk lane) {
        if (lane instanceof yiy own) {
            LEn link = own.MYI(); // its document link
            if (link != null) return link.cc_(); // the project document
        }
        return null;
    }
}
