package bml.hideparts;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings → Mods → Hide Browser Parts: one checkbox per part. */
public final class HidePartsSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        Section section = preferences.section(mods, "browser-hide-parts", "Hide Browser Parts");
        for (HideParts.Part part : HideParts.Part.values()) {
            preferences.checkbox(part.id, part.label)
                       .in(section)
                       .value(part::hidden, part::setHidden);
        }
    }
}
