package com.bitwig.mods.splitnotesatgrid;

import bitwig.obf.aNj; // the note editor widget inside a clip's own lane view
import bitwig.obf.big; // a selection in an editor (what a selection menu is opened on)
import bitwig.obf.bii; // a note editor's model
import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import com.bitwig.flt.widget.core.timeline.YGP; // the note editor widget for a track's or clip's notes
import com.bitwig.flt.widget.core.timeline.htp; // the note editor widget for a clip's notes
import com.bitwig.flt.widget.core.timeline.w3; // what every timeline editor widget extends

/**
 * Declares Split Notes at Grid: a real Bitwig action, with no key until the user assigns one, bound on the
 * note editors only, so a key it shares with one of Bitwig's actions stays Bitwig's in the Arranger. Also in
 * the notes' right-click menu.
 */
public final class SplitNotesActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        // Stored in users' shortcut settings: never change it.
        actions.action("split_notes_at_grid_split", "Split Notes at Grid")
               .category("detail_editor")
               .bindOn(htp.class, YGP.class, aNj.class)
               .in(Menu.selection("arranger_clip_instrument_note"), Menu.selection("arranger_instrument_note"),
                   Menu.selection("launcher_clip_instrument_note"), Menu.selection("launcher_instrument_note"))
               .onRun(context -> {
                   bii editor = editorOf(context);
                   if (editor != null) {
                       SplitNotes.split(editor);
                   }
               });
    }

    /** From a right-click menu, the editor of the selection it was opened on; from a shortcut, of the bound widget. */
    private static bii editorOf(ActionContext context) {
        if (context.selection() instanceof big selection) {
            return selection.lU_(); // the selection's editor; none for a selection that isn't an editor's
        }
        return context.widget() instanceof w3 widget ? widget.mC_() : null; // mC_: the editor's model
    }
}
