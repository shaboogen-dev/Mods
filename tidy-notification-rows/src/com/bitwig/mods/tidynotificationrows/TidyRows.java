package com.bitwig.mods.tidynotificationrows;

import bml.notifications.Centre;
import bml.notifications.CentreProvider;
import bml.notifications.Part;
import bml.notifications.TaskRow;
import bml.notifications.TextLook;

/**
 * Every task row in Bitwig's notification centre, Bitwig's own and every mod's, as title, status (same font
 * and colour as the title, wrapping), bar, then Cancel at the right. Sub-tasks and images stay where Bitwig
 * puts them. The bar shows for as long as the task runs, busy while its progress isn't known. Runs once each
 * time Bitwig builds a row, never per paint. Messages keep Bitwig's layout.
 */
public final class TidyRows implements CentreProvider {
    @Override
    public void declare(final Centre centre) {
        centre.taskRows(TidyRows::tidy);
    }

    private static void tidy(final TaskRow row) {
        Part status = row.status();
        if (status != null) {
            final Part wrapping = row.wrappingStatus(TextLook.TITLE); // follows the status, hidden while empty
            status.replaceWith(wrapping);
            status = wrapping;
        }
        row.column().order(row.titleLine(), status, row.barBox(), row.cancel()); // sub-tasks stay after these
        if (row.hasProgress()) {
            titleAtLeftEdge(row);
        }
        row.barShownWhileRunning();
        row.barWaitingWhileUnknown();
    }

    /**
     * A progress task's title starts at the column's left edge, where the status and the bar start.
     * Bitwig hides the spinner for these tasks but keeps the 4 px spacer it puts after it, so the title
     * sat 4 px right of the status. The title line holds the spinner, that spacer and the title: take
     * out what is neither. Rows with a spinner (tasks without a progress value) keep it, spacer and all.
     */
    private static void titleAtLeftEdge(final TaskRow row) {
        final Part spinner = row.spinner();
        final Part title = row.title();
        for (final Part part : row.titleLine().parts()) {
            if (part != spinner && part != title) {
                part.remove();
            }
        }
    }
}
