package com.bitwig.mods.alllanesview;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import bitwig.obf.R1Z; // a Bitwig device's contents: its type, chains and expanded panel
import com.bitwig.flt.device.Wkj; // a device type's ID

/**
 * The devices that get the All Lanes view: Bitwig devices with several chains attached (FX,
 * instrument or note FX chains). Every other device keeps Bitwig's behaviour.
 *
 * Devices are keyed by Bitwig's device ID, the UUID a device type keeps on every Bitwig version and
 * platform (the "device_id" in a .bwpreset, and the ID the controller API matches devices by). The
 * names are for reading only; a device is never matched by its name.
 *
 * A device's lanes come from one of two models ({@link Model}):
 * <ul>
 * <li>a lane list (the layers and selectors): the lanes the user adds, removes, renames and colours,
 * read from the device's container ({@code R1Z.TMF()});</li>
 * <li>fixed slots (Multiband, Mid-Side, Stereo Split, XY, the Spectral splitters): the device's
 * nested chain slots, one rule for all: <b>Pre first, the bands, Post last.</b> A slot is Pre or Post
 * when Bitwig's name for it starts with "Pre" or "Post" ("Pre FX", "Post"), or its ID starts with
 * PRE or POST. The bands are the listed slot IDs ({@code bands}), or, with none listed, every other
 * slot of the device. They are in the listed order, or with {@code natural} in the natural order of
 * Bitwig's names (numbers by value, then letters: 1, 2, 3, 4; A, B, C, D; Harmonics A, Harmonics B,
 * Nons). A slot that is neither Pre, Post nor a listed band isn't shown.</li>
 * </ul>
 * Slot IDs are the slots' preset identifiers (the IDs in the devices' Default.bwpreset files, the same
 * from 6.0 to 6.1.3). Every row shows Bitwig's own name for the slot; the listed name only while
 * Bitwig gives it none.
 *
 * Provisional list (9 Nov), until the final one is decided. Edit it here and rebuild.
 */
final class ListedDevices {
    private ListedDevices() {
    }

    /** A band slot: its ID, and the name shown if Bitwig gives it none. */
    record Slot(String id, String name) {
    }

    /**
     * How a device's lanes are found: its lane list always (if it has one); then, for a device with
     * fixed slots ({@code slots}), its Pre, its bands ({@code bands}, or every other slot when empty),
     * and its Post.
     */
    record Model(boolean slots, List<Slot> bands, boolean natural) {
    }

    private static final Model LANES = new Model(false, List.of(), false);
    /** Every slot: Pre, the others in the natural order of their names, Post. */
    private static final Model ALL_NATURAL = new Model(true, List.of(), true);

    private static final Map<UUID, Model> DEVICES = Map.ofEntries(
            device("956e396b-07c5-4430-a58d-8dcfc316522a", LANES), // FX Selector
            device("a0913b7f-096b-4ac9-bddd-33c775314b42", LANES), // FX Layer
            device("214857d6-b468-4257-9bc9-92f017af1782", // Multiband FX-2
                    listed("LOW_CHAIN", "Low", "HIGH_CHAIN", "High")),
            device("f97699d1-3b8e-4363-8ede-4994e276cc97", // Multiband FX-3
                    listed("LOW_CHAIN", "Low", "MID_CHAIN", "Mid", "HIGH_CHAIN", "High")),
            device("a6c9b12f-45a5-43e3-b100-b74ecf77367b", // Mid-Side Split
                    listed("MID_CHAIN", "Mid", "SIDE_CHAIN", "Side")),
            device("96196ffe-658f-46c4-84ba-153799be3657", // Stereo Split
                    listed("LEFT_CHAIN", "Left", "RIGHT_CHAIN", "Right")),
            device("51169152-c144-4a38-95ba-1390fb579a1f", ALL_NATURAL), // XY FX: A to D, Post FX
            device("bab3f04d-d3b6-4dfa-86f9-506e0b091ca8", ALL_NATURAL), // XY Instrument: Pre, A to D, Post
            device("3f3c3200-e6aa-4578-8e06-f573ed65206e", ALL_NATURAL), // Freq Split: Pre FX, 1 to 4, Post FX
            device("6e75a854-ceab-475b-94c5-75188ee998b8", // Loud Split: Pre, Low, Mid, High, Post
                    listed("LOW_CHAIN", "Low", "MID_CHAIN", "Mid", "HIGH_CHAIN", "High")),
            device("7c3c7bb2-625d-4915-ae95-943ee9aa807d", // Transient Split: Pre, Transient, Harmonic, Post
                    listed("TRANSIENT_CHAIN", "Transient", "HARMONIC_CHAIN", "Harmonic")),
            device("c90b6d52-898b-4dad-aa58-2c58add7c94f", natural( // Harmonic Split: Pre, Harmonics A, B, Nons, Post
                    "ODD_CHAIN", "Odd", "EVEN_CHAIN", "Even", "RES_CHAIN", "Res")),
            device("5024be2e-65d6-4d40-bbfe-8b2ea993c445", LANES), // Instrument Layer (its FX slot isn't shown)
            device("9588fbcf-721a-438b-8555-97e4231f7d2c", LANES), // Instrument Selector (its FX slot isn't shown)
            device("96456481-4c52-423a-8485-4604b15d0183", LANES), // Note FX Layer
            device("8fd471db-15df-44c6-b497-4bb851d4fd46", LANES)); // Note FX Selector

    private static Map.Entry<UUID, Model> device(String id, Model model) {
        return Map.entry(UUID.fromString(id), model);
    }

    private static List<Slot> slots(String... idsAndNames) {
        Slot[] slots = new Slot[idsAndNames.length / 2];
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(idsAndNames[2 * i], idsAndNames[2 * i + 1]);
        return List.of(slots);
    }

    /** Pre, these bands in this order, Post. */
    private static Model listed(String... idsAndNames) {
        return new Model(true, slots(idsAndNames), false);
    }

    /** Pre, these bands in the natural order of their names, Post. */
    private static Model natural(String... idsAndNames) {
        return new Model(true, slots(idsAndNames), true);
    }

    private static UUID id(R1Z device) {
        if (device == null) return null;
        Wkj id = device.UNj(); // the device type's ID
        return id != null ? id.Hnt() : null; // its UUID
    }

    /** Whether this device gets the All Lanes view. */
    static boolean listed(R1Z device) {
        UUID id = id(device);
        return id != null && DEVICES.containsKey(id);
    }

    /** How the device's lanes are found; a lane list only for a device that isn't listed. */
    static Model model(R1Z device) {
        UUID id = id(device);
        Model model = id != null ? DEVICES.get(id) : null;
        return model != null ? model : LANES;
    }

    /** Whether a slot is a Pre chain: Bitwig's name starts with "Pre", or its ID with "PRE". */
    static boolean pre(String name, String id) {
        return starts(name, "pre") || (id != null && id.startsWith("PRE"));
    }

    /** Whether a slot is a Post chain: Bitwig's name starts with "Post", or its ID with "POST". */
    static boolean post(String name, String id) {
        return starts(name, "post") || (id != null && id.startsWith("POST"));
    }

    /** Whether the name's first word is this one, ignoring case ("Pre FX", "Pre", "pre-fx"; not "Present"). */
    private static boolean starts(String name, String word) {
        if (name == null) return false;
        String n = name.trim().toLowerCase();
        if (!n.startsWith(word)) return false;
        return n.length() == word.length() || !Character.isLetter(n.charAt(word.length()));
    }

    /** Natural order of two names: case ignored, runs of digits compared by value ("2" before "10"). */
    static int natural(String a, String b) {
        a = a == null ? "" : a.trim().toLowerCase();
        b = b == null ? "" : b.trim().toLowerCase();
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                int c = new java.math.BigInteger(a.substring(si, i)).compareTo(new java.math.BigInteger(b.substring(sj, j)));
                if (c != 0) return c;
            } else {
                if (x != y) return Character.compare(x, y);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
