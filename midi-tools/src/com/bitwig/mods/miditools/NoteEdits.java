package com.bitwig.mods.miditools;

import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** LandLogic's edits on a plan of Bitwig's notes, and the notes around the selected ones that a landing note reaches. */
final class NoteEdits implements LandLogic.Edits<Note> {
    private final Edit edit;

    NoteEdits(Edit edit) {
        this.edit = edit;
    }

    @Override
    public Note copy(Note note, int key, double start, double end) {
        return edit.copy(note, key, start, end);
    }

    @Override
    public void trimStart(Note note, double start) {
        edit.trimStart(note, start);
    }

    @Override
    public void setEnd(Note note, double end) {
        edit.setEnd(note, end);
    }

    @Override
    public void setKey(Note note, int key) {
        edit.setKey(note, key);
    }

    @Override
    public void remove(Note note) {
        edit.remove(note);
    }

    /** The selected notes, then every other note of their clips from the earliest start to the latest end. */
    static List<Note> withNeighbours(List<Note> selected) {
        List<Note> all = new ArrayList<>(selected);
        Map<Clip, double[]> spans = new HashMap<>();
        for (Note note : selected) {
            if (note.clip() != null) {
                double[] span = spans.computeIfAbsent(note.clip(), c -> new double[] {note.start(), note.end()});
                span[0] = Math.min(span[0], note.start());
                span[1] = Math.max(span[1], note.end());
            }
        }
        spans.forEach((clip, span) -> {
            for (Note other : clip.notesBetween(span[0], span[1])) {
                if (!all.contains(other)) {
                    all.add(other);
                }
            }
        });
        return all;
    }

    /** Plain values for LandLogic; clips numbered in order of first appearance. */
    static List<LandLogic.N> plain(List<Note> notes) {
        Map<Clip, Integer> groups = new HashMap<>();
        List<LandLogic.N> plain = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            int group = note.clip() == null ? -1 - i : groups.computeIfAbsent(note.clip(), c -> groups.size());
            plain.add(new LandLogic.N(i, group, note.channel(), note.key(), note.start(), note.end()));
        }
        return plain;
    }

    /** Each note's clip as a number, as {@link #plain} numbers them. */
    static int[] groups(List<Note> notes) {
        List<LandLogic.N> plain = plain(notes);
        int[] groups = new int[notes.size()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = plain.get(i).group();
        }
        return groups;
    }
}
