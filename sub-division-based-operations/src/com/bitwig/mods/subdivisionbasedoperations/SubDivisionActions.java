package com.bitwig.mods.subdivisionbasedoperations;

import bitwig.obf.aNj; // the note editor widget inside a clip's own lane view
import bitwig.obf.bii; // a note editor's model
import bml.actions.Action;
import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.flt.widget.core.timeline.YGP; // the note editor widget for a track's or clip's notes
import com.bitwig.flt.widget.core.timeline.htp; // the note editor widget for a clip's notes
import com.bitwig.flt.widget.core.timeline.w3; // what every timeline editor widget extends
import java.util.function.Consumer;

/**
 * Declares the four actions: real Bitwig actions, with no key until the user assigns one. Their shortcuts
 * are bound on the note editors only, so a key they share with one of Bitwig's actions stays Bitwig's in
 * the Arranger.
 */
public final class SubDivisionActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        // Stored in users' shortcut settings: never change them.
        action(actions, "nudge_and_resize_by_grid_earlier", "Nudge Notes Earlier by Grid", editor -> NudgeAndResize.nudge(editor, -1));
        action(actions, "nudge_and_resize_by_grid_later", "Nudge Notes Later by Grid", editor -> NudgeAndResize.nudge(editor, 1));
        action(actions, "nudge_and_resize_by_grid_shorter", "Shorten Notes by Grid", editor -> NudgeAndResize.resize(editor, -1));
        action(actions, "nudge_and_resize_by_grid_longer", "Lengthen Notes by Grid", editor -> NudgeAndResize.resize(editor, 1));
    }

    private static Action action(Actions actions, String id, String label, Consumer<bii> run) {
        return actions.action(id, label)
                      .category("detail_editor")
                      .bindOn(htp.class, YGP.class, aNj.class)
                      .onRun(context -> {
                          bii editor = editorOf(context);
                          if (editor != null) {
                              run.accept(editor);
                          }
                      });
    }

    /** The editor of the bound widget the shortcut ran on. */
    private static bii editorOf(ActionContext context) {
        return context.widget() instanceof w3 widget ? widget.mC_() : null; // mC_: the editor's model
    }
}
