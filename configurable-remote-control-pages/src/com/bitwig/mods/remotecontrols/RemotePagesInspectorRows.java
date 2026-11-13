package com.bitwig.mods.remotecontrols;

import bml.inspector.Blocks;
import bml.inspector.InspectorProvider;

/** The "Remote Pages Visible" row at the end of Bitwig's Device block in the Device Inspector. */
public final class RemotePagesInspectorRows implements InspectorProvider {
    @Override
    public void declare(Blocks blocks) {
        blocks.deviceRows("remote-pages-visible")
              .content(context -> ConfigurableRemoteControlPages.inspectorRow(context.panel()));
    }
}
