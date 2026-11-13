import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The mod's section on the Settings > Mods page. */
public final class CompactExpandedGroupsSettings implements PreferenceProvider {
    @Override
    public void declare(final Preferences preferences) {
        final Page mods = preferences.page("mods", "Mods"); // the page every mod shares: this exact ID
        final Section groups = preferences.section(mods, "compact-expanded-groups", "Compact Expanded Groups");
        preferences.slider("expanded_height", "Compact height",
                           BmlCompactExpandedGroups.MIN_HEIGHT, BmlCompactExpandedGroups.MAX_HEIGHT).wholeNumbers()
                   .in(groups)
                   .value(BmlCompactExpandedGroups::height, BmlCompactExpandedGroups::setHeight);
        preferences.slider("expanded_opacity", "Summary opacity", 0, 100).wholeNumbers()
                   .in(groups)
                   .value(BmlCompactExpandedGroups::opacity, BmlCompactExpandedGroups::setOpacity);
    }
}
