import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** The action, declared through the Action and Menu framework. */
public final class AddAutomationPointsActions implements ActionProvider {
    /** Bitwig's two kinds of time selection on automation lanes: in the arranger and in a clip. */
    private static final String ARRANGER = "arranger_automation_time_selection";
    private static final String CLIP = "clip_automation_time_selection";

    @Override
    public void declare(Actions actions) {
        actions.action(AddAutomationPoints.ACTION_ID, AddAutomationPoints.ACTION_LABEL)
               .menuLabel(AddAutomationPoints.ACTION_MENU_LABEL)
               .in(Menu.selection(ARRANGER), Menu.selection(CLIP),
                   Menu.selectionToolbar(ARRANGER), Menu.selectionToolbar(CLIP))
               .visibleWhen(AddAutomationPoints::canRun)
               .onRun(AddAutomationPoints::run);
    }
}
