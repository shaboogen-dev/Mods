package com.bitwig.mods.hibernatetracks;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** Hibernate Track, Wake Track and Make Permanent: real Bitwig actions with no key, and rows in the track header menu. */
public final class HibernateActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("hibernate_tracks_hibernate", "Hibernate Track")
               .in(Menu.selection("track"))
               .category("arranger")
               .icon("hibernate_icon")
               .visibleWhen(context -> Tracks.anyToHibernate(Tracks.selected(context)))
               .onRun(context -> Hibernate.run(Tracks.selected(context)));
        actions.action("hibernate_tracks_wake", "Wake Track")
               .in(Menu.selection("track"))
               .category("arranger")
               .icon("wake_icon")
               .visibleWhen(context -> Tracks.anyToWake(Tracks.selected(context)))
               .onRun(context -> Wake.run(Tracks.selected(context)));
        actions.action("hibernate_tracks_make_permanent", "Make Hibernated Track Permanent")
               .menuLabel("Make Permanent...")
               .in(Menu.selection("track"))
               .category("arranger")
               .visibleWhen(context -> Tracks.anyPair(Tracks.selected(context)))
               .onRun(context -> Permanent.run(Tracks.selected(context)));
    }
}
