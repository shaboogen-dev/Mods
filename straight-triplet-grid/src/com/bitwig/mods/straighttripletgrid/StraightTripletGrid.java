package com.bitwig.mods.straighttripletgrid;

import bitwig.obf.deA; // the definition of a saved list of choices
import bitwig.obf.fo; // the grid subdivision: straight, triplet, quintuplet, septuplet
import bitwig.obf.pvS; // a grid's saved settings
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import com.bitwig.flt.widget.core.timeline.mHx; // the grid button and its menu
import com.bitwig.flt.widget.core.timeline.vNq; // the Quantize dialog's custom grid
import com.bitwig.flt.widget.core.timeline.zZo; // a subdivision chooser with size fields
import com.bitwig.ramona.core.Vzt; // a choice in a saved list

/**
 * Grid: Straight and Triplet Only.
 *
 * Takes the 5t and 7t subdivisions out of what the user can choose and what the grid uses. Bitwig's saved
 * list of four stays as it is, so projects load and save as in stock Bitwig: a project saved with 5t or 7t
 * reads as straight, and keeps its saved value until the subdivision is changed.
 *
 * Bitwig's own list ({@code fo.values()}) isn't limited, as the original Superpowers patch did: a switch
 * table built from it ({@code XWd.<clinit>}) writes the 5t and 7t ordinals, and the saved definition
 * ({@code VnH}) is built from it, so a 5t project would lose its value on load. The lists are limited where
 * they are shown.
 */
public final class StraightTripletGrid {
    private static final fo[] TWO_CHOICES = {fo.cfM, fo.MRY}; // straight, triplet

    private StraightTripletGrid() {
    }

    /**
     * Next and Previous and the subdivision buttons all go through this lookup. Next asks for the number
     * above the current one and Previous for the one below, going round the list: from straight, Previous
     * asks for 7, and from triplet, Next asks for 5. So 7 gives triplet and 5 gives straight, which makes
     * both actions go between the two in one press.
     */
    @Hook(target = fo.class, method = "cfM(I)Lfo;", at = At.WRAP_RETURN, id = "subdivision-from-number")
    public static fo subdivisionFromNumber(@Return fo found) {
        return found == fo.MRY || found == fo.FzB ? fo.MRY : fo.cfM; // MRY: triplet, FzB: 7t, cfM: straight
    }

    /** A project saved with a 5t or 7t grid reads as straight. The saved value isn't changed. */
    @Hook(target = pvS.class, method = "hbP()Lfo;", at = At.WRAP_RETURN, id = "saved-subdivision")
    public static fo savedSubdivision(@Return fo saved) {
        return saved == fo.Hnt || saved == fo.FzB ? fo.cfM : saved; // Hnt: 5t, FzB: 7t, cfM: straight
    }

    @Hook(target = mHx.class, method = "MRY(LdKW;Lgrm;)V", at = At.REPLACE_CALL,
          callOwner = deA.class, call = "MRY()[Lcom/bitwig/ramona/core/Vzt;", occurrence = 1, id = "grid-menu-choices")
    public static Vzt[] gridMenuChoices(deA definition) {
        return TWO_CHOICES.clone();
    }

    @Hook(target = zZo.class, method = "cfM(Lcom/bitwig/ramona/core/sQv;Lcom/bitwig/ramona/core/sQv;)Ldve;", at = At.REPLACE_CALL,
          callOwner = fo.class, call = "values", occurrence = 1, id = "chooser-choices")
    public static fo[] chooserChoices() {
        return TWO_CHOICES.clone();
    }

    @Hook(target = vNq.class, method = "MYI", at = At.REPLACE_CALL,
          callOwner = fo.class, call = "values", occurrence = 1, id = "quantize-choices")
    public static fo[] quantizeChoices() {
        return TWO_CHOICES.clone();
    }
}
