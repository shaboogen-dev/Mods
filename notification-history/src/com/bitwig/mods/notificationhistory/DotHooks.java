package com.bitwig.mods.notificationhistory;

import bitwig.obf.dML; // the notification dot at the top right of the window
import bitwig.obf.drX; // a widget's press handler
import bitwig.obf.eoW; // a mouse button
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;

/**
 * The mod's hooks, both on the dot's press-handler factory: the left button gets the panel, the right
 * button the dot's menu, everything else Bitwig's own handler. Nothing they meet reaches Bitwig.
 */
public final class DotHooks {
    private DotHooks() { }

    /** The mouse button of the press the dot is making a handler for (UI thread; read by the next hook). */
    private static eoW button;

    /**
     * The dot's press-handler factory, on entry: notes which button was pressed. The method only makes
     * the handler (no branch, no stack-map frame), so an entry hook is safe there.
     */
    @Hook(target = dML.class, method = "MRY(LeoW;DDLeoV;I)LdrX;", at = At.ENTRY, id = "dot-press")
    public static void dotPress(@Arg(0) eoW pressed) {
        button = pressed;
    }

    /**
     * The dot's press handler, made on each press: for the left button the mod's handler (its click opens
     * or closes the notification panel), for the right button one whose press opens the dot's menu
     * ("Show notifications"); other buttons and any failure get Bitwig's own.
     */
    @Hook(target = dML.class, method = "MRY(LeoW;DDLeoV;I)LdrX;", at = At.WRAP_RETURN, id = "dot-click")
    public static drX dotClick(@Return drX stock) {
        eoW pressed = button;
        button = null;
        try {
            DotRoute.Button which = pressed == eoW.cfM ? DotRoute.Button.LEFT    // the left button
                                  : pressed == eoW.Hnt ? DotRoute.Button.RIGHT   // the right button
                                  : DotRoute.Button.OTHER;
            switch (DotRoute.of(stock != null, which)) {
                case PANEL -> {
                    return new DotClick();
                }
                case MENU -> {
                    return new DotMenu();
                }
                default -> {
                    return stock;
                }
            }
        } catch (Throwable t) {
            Log.MOD.log("the dot's click stays Bitwig's own", t);
            return stock;
        }
    }
}
