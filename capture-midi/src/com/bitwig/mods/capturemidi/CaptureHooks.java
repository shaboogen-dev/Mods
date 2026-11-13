package com.bitwig.mods.capturemidi;

import bitwig.obf.CjY; // the receivers of one MIDI input port
import bitwig.obf.FV2; // the connection to the audio engine's MIDI (sends the computer keyboard's notes)
import bitwig.obf.HDm; // a MIDI message
import bitwig.obf.UlV; // a short MIDI message (status and two data bytes, packed)
import bitwig.obf.aDt; // the computer keyboard's note input
import bitwig.obf.gPa; // the thread that reads incoming MIDI from the audio engine
import bml.api.At;
import bml.api.Hook;

import java.io.IOException;

/**
 * Listens to what is played, without changing it: each hook records the message, then makes
 * Bitwig's own call unchanged. Runs on Bitwig's MIDI reader thread and its UI thread; recording is
 * a few array writes.
 */
public final class CaptureHooks {
    private CaptureHooks() {
    }

    /** Every short message from a MIDI input port, as Bitwig's reader hands it to the port's receivers. */
    @Hook(target = gPa.class, method = "hUR()V", at = At.REPLACE_CALL, callOwner = CjY.class, call = "cfM(LHDm;)V",
          occurrence = 1, id = "midi-in")
    public static void midiIn(CjY port, HDm message) {
        try {
            if (message instanceof UlV shortMessage) NoteBuffer.record(System.identityHashCode(port) | 1, shortMessage.cfM); // the packed bytes
        } catch (Throwable ignored) {
            // recording never stands in the way of the MIDI
        }
        port.cfM(message);
    }

    /** A computer keyboard key pressed: Bitwig sends the note-on to the engine. */
    @Hook(target = aDt.class, method = "cfM(IF)V", at = At.REPLACE_CALL, callOwner = FV2.class, call = "cfM(IIF)V",
          occurrence = 1, id = "computer-keyboard-on")
    public static void keyboardOn(FV2 engine, int port, int pitch, float velocity) throws IOException {
        keyboard(0x90, pitch, velocity);
        engine.cfM(port, pitch, velocity);
    }

    /** A computer keyboard key released: Bitwig sends the note-off to the engine. */
    @Hook(target = aDt.class, method = "MRY(IF)V", at = At.REPLACE_CALL, callOwner = FV2.class, call = "MRY(IIF)V",
          occurrence = 1, id = "computer-keyboard-off")
    public static void keyboardOff(FV2 engine, int port, int pitch, float velocity) throws IOException {
        keyboard(0x80, pitch, velocity);
        engine.MRY(port, pitch, velocity);
    }

    private static void keyboard(int status, int pitch, float velocity) {
        try {
            if (pitch < 0 || pitch > 127) return;
            int v = Float.isFinite(velocity) ? Math.max(status == 0x90 ? 1 : 0, Math.min(127, Math.round(velocity * 127))) : 100;
            NoteBuffer.record(NoteBuffer.COMPUTER_KEYBOARD, status | pitch << 8 | v << 16);
        } catch (Throwable ignored) {
            // recording never stands in the way of the keyboard
        }
    }
}
