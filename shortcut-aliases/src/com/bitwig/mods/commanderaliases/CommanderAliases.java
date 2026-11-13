package com.bitwig.mods.commanderaliases;

import bitwig.obf.LJt; // a value property (the text editor's text)
import bitwig.obf.OpO; // Bitwig's standard button size
import bitwig.obf.Sxr; // a fixed size in pixels
import bitwig.obf.cNB; // the Commander's text-filter factory
import bitwig.obf.dAa; // a compact (icon) button
import bitwig.obf.dJV; // a table column's cell factory
import bitwig.obf.dRZ; // the Settings > Shortcuts page
import bitwig.obf.dWb; // a horizontal / vertical widget group
import bitwig.obf.dmQ; // an action binding (one Commander result)
import bitwig.obf.dmX; // the action a binding runs
import bitwig.obf.dmZ; // an action
import bitwig.obf.dnF; // a styled container
import bitwig.obf.dpN; // the application
import bitwig.obf.dtX; // a modal popup with a primary button and Cancel
import bitwig.obf.dut; // a text label
import bitwig.obf.dve; // a widget
import bitwig.obf.dyC; // a focusable widget
import bitwig.obf.ecV; // a one-line text editor
import bitwig.obf.ecl; // a message label
import bitwig.obf.eek; // a container style
import bitwig.obf.ekE; // an icon
import bitwig.obf.ehp; // the theme's shortcut styles
import bitwig.obf.eht; // the shortcut chip style
import bitwig.obf.eiH; // the theme
import bitwig.obf.ejE; // a font style
import bitwig.obf.ejt; // a text style
import bitwig.obf.emX; // the Shortcuts table
import bitwig.obf.emu; // a table column
import bitwig.obf.pCi; // a Commander search filter
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Commander: Assign Aliases to Actions.
 *
 * Adds an "Alias" column to Settings > Shortcuts where each action can be given one alias,
 * stored in {@code commander-aliases.cfg} (alias=actionId) in the mod's preserved config
 * directory. Commander search filters are wrapped so a query matching an alias also
 * matches its action.
 */
public final class CommanderAliases {
    private static final Bml.Mod MOD = Bml.mod("shortcut-aliases");
    private static AliasTable aliasTable;
    private static IOException aliasLoadError; // set once if the alias file can't be read; not retried until restart
    private static final Set<Object> aliasColumnTables = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<String, String> actionNamesById = Collections.synchronizedMap(new HashMap<>());

    private CommanderAliases() {
    }

    // ---- Settings > Shortcuts: alias column ----

    /** After the Shortcuts page adds its columns: inserts the Alias column once per table. */
    @Hook(target = dRZ.class, method = "cfM(LdSl;Ljava/util/Collection;)V", at = At.BEFORE_RETURN, id = "install-alias-column")
    public static void installAliasColumn(@This dRZ page) {
        if (page == null) {
            return;
        }
        try {
            AliasTable aliases = aliases();
            emX table = page.Hnt; // the page's shortcuts table
            if (table == null) {
                throw new IllegalStateException("Shortcuts table field is null");
            }
            synchronized (aliasColumnTables) {
                if (aliasColumnTables.contains(table)) {
                    return;
                }
                emu column = emu.cfM("Alias"); // a column with this title
                column.ucQ(1);    // visual index
                column.cfM(100);  // width
                column.IwV(70);   // minimum width
                column.lYc(100);  // preferred width
                column.MRY(0.0f); // weight
                column.cfM(new AliasCellFactory(aliases)); // cell factory
                table.cfM(column, 1); // insert at definition position 1
                aliasColumnTables.add(table);
                log("ALIAS_COLUMN_INSTALLED page=" + page.getClass().getName()
                        + " table=" + table.getClass().getName()
                        + " definition_position=1 visual_index=1 width=100 min_width=70 preferred_width=100 weight=0 aliases="
                        + aliases.aliasCount());
            }
        } catch (Throwable t) {
            log("ALIAS_COLUMN_ERROR page=" + page.getClass().getName() + " error=" + rootMessage(t));
        }
    }

    /** The cell for one action row: its alias chip, or an add button when it has none. */
    private static dve createAliasControls(String actionId, String actionName, String alias) {
        NativeAliasResources resources = NativeAliasResources.resolve();
        dWb group = dWb.ELS(); // a table-cell group
        group.UNj(0.5f);       // centre children across
        if (alias != null) {
            dve[] chipRef = new dve[1];
            Runnable remove = () -> removeAliasFromCell(actionId, actionName, alias, group, chipRef[0], resources);
            dve chip = createNativeAliasChip(alias, remove, resources);
            chipRef[0] = chip;
            group.HL(chip); // add child
        } else {
            group.HL(createAddAliasButton(actionId, actionName, group, resources)); // add child
        }
        return group;
    }

    private static dAa createAddAliasButton(String actionId, String actionName, dWb group, NativeAliasResources resources) {
        dAa[] buttonRef = new dAa[1];
        Runnable open = () -> openAliasModal(actionId, actionName, group, buttonRef[0], resources);
        dAa button = createNativeIconButton(resources.addIcon, open);
        buttonRef[0] = button;
        OpO size = OpO.cfM(); // the standard shortcut button size
        button.a_(size);  // horizontal size
        button.MRY(size); // vertical size
        return button;
    }

    // ---- Assign-alias modal ----

    private static void openAliasModal(String actionId, String actionName, dWb group, dAa addButton, NativeAliasResources resources) {
        try {
            dtX modal = dtX.ycx();
            ecV editor = ecV.bB();
            ecl validationMessage = createAliasValidationMessage();
            dWb body = dWb.qFI(); // a vertical group
            body.HL(editor);            // add child
            body.HL(validationMessage); // add child
            modal.MJ("Assign alias for " + friendlyActionName(actionName, actionId)); // title
            modal.IwV((dve) body); // content
            modal.BDm(true);      // show the primary button
            modal.jFL(true);      // show Cancel
            modal.Hnt((dyC) editor); // focus widget
            dAa primary = modal.ODA; // the primary button
            if (primary == null) {
                throw new IllegalStateException("Alias modal primary button is null");
            }
            primary.Vr("add_shortcut.add"); // Bitwig's "Add" label
            primary.Hnt(() -> commitAliasFromModal(actionId, actionName, group, addButton, modal, editor, validationMessage, resources)); // action
            modal.FzB(addButton); // show anchored to the add button
            log("ALIAS_MODAL_OPEN action_id=" + actionId + " action_name=" + quote(actionName));
        } catch (Throwable t) {
            log("ALIAS_MODAL_ERROR action_id=" + actionId + " error=" + rootMessage(t));
        }
    }

    private static void commitAliasFromModal(String actionId, String actionName, dWb group, dAa addButton, dtX modal,
                                             ecV editor, ecl validationMessage, NativeAliasResources resources) {
        try {
            String candidate = readAliasEditorText(editor);
            AliasTable aliases = aliases();
            String alias;
            try {
                alias = aliases.addAlias(actionId, candidate);
            } catch (IllegalArgumentException e) {
                String reason = aliasValidationText(actionId, candidate, e.getMessage());
                setAliasValidationMessage(validationMessage, reason, true);
                log("ALIAS_ADD_REJECTED action_id=" + actionId + " candidate=" + quote(candidate == null ? "" : candidate) + " reason=" + quote(reason));
                return;
            }
            group.MJ(addButton); // remove child
            dve[] chipRef = new dve[1];
            Runnable remove = () -> removeAliasFromCell(actionId, actionName, alias, group, chipRef[0], resources);
            dve chip = createNativeAliasChip(alias, remove, resources);
            chipRef[0] = chip;
            group.HL(chip); // add child
            modal.ke_();    // confirm and close
            log("ALIAS_ADDED action_id=" + actionId + " alias=" + quote(alias) + " persisted=" + quote(aliases.path().toString()));
        } catch (Throwable t) {
            log("ALIAS_ADD_ERROR action_id=" + actionId + " error=" + rootMessage(t));
        }
    }

    private static String readAliasEditorText(ecV editor) {
        LJt property = editor.iF_(); // the editor's text
        if (property == null) {
            return "";
        }
        Object value = property.get();
        return value == null ? "" : value.toString();
    }

    private static ecl createAliasValidationMessage() {
        ecl message = ecl.x3t();
        message.t_(false); // visible
        message.Grk(true); // wrap
        return message;
    }

    private static void setAliasValidationMessage(ecl message, String text, boolean visible) {
        message.SiF().cfM(text == null ? "" : text); // set the text
        message.t_(visible);
    }

    private static String aliasValidationText(String actionId, String candidate, String fallback) throws IOException {
        String owner = aliases().actionForAlias(candidate);
        if (owner != null && !owner.equals(actionId)) {
            String ownerName = actionNamesById.get(owner);
            if (ownerName != null && !ownerName.isBlank()) {
                return "This alias has already been assigned to the action " + ownerName;
            }
            return "This alias has already been assigned to another action";
        }
        return fallback == null || fallback.isBlank() ? "Invalid alias" : fallback;
    }

    private static void removeAliasFromCell(String actionId, String actionName, String alias, dWb group, dve chip,
                                            NativeAliasResources resources) {
        try {
            AliasTable aliases = aliases();
            boolean removed = aliases.removeAlias(actionId, alias);
            if (!removed) {
                log("ALIAS_REMOVE_STALE action_id=" + actionId + " alias=" + quote(alias));
                return;
            }
            group.MJ(chip); // remove child
            group.HL(createAddAliasButton(actionId, actionName, group, resources)); // add child
            log("ALIAS_REMOVED action_id=" + actionId + " alias=" + quote(alias) + " persisted=" + quote(aliases.path().toString()));
        } catch (Throwable t) {
            log("ALIAS_REMOVE_ERROR action_id=" + actionId + " alias=" + quote(alias) + " error=" + rootMessage(t));
        }
    }

    // ---- Native widgets ----

    /** The alias in Bitwig's shortcut chip, with the chip's remove button. */
    private static dve createNativeAliasChip(String alias, Runnable onRemove, NativeAliasResources resources) {
        dWb group = dWb.IwV(0); // a horizontal group, no spacing
        group.UNj(0.5f);        // centre children across
        group.HL(createNativeChipLabel(alias, resources)); // add child
        dAa removeButton = createNativeIconButton(resources.removeIcon, onRemove);
        Sxr size = new Sxr(13L);
        removeButton.a_(size);  // horizontal size
        removeButton.MRY(size); // vertical size
        group.HL(removeButton); // add child
        dnF container = dnF.FXc();
        container.cfM(group);                // child
        container.cfM(resources.chipStyle);  // style
        return container;
    }

    private static dut createNativeChipLabel(String text, NativeAliasResources resources) {
        dut label = dut.lDU();
        label.Grk(text == null ? "" : text);   // text
        label.Hnt(resources.chipTextStyle);    // text style
        label.cfM(resources.chipFontStyle);    // font
        return label;
    }

    private static dAa createNativeIconButton(ekE icon, Runnable action) {
        dAa button = dAa.mtS();
        button.cfM(icon);   // icon
        button.Hnt(action); // action
        return button;
    }

    // ---- Actions ----

    private static String actionNameForAction(dmZ action, String actionId) {
        try {
            String name = action.IwV; // the action's cached display name
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Throwable t) {
            log("ALIAS_ACTION_NAME_FALLBACK action_id=" + actionId + " error=" + rootMessage(t));
        }
        return actionId == null ? "Action" : actionId;
    }

    private static String friendlyActionName(String actionName, String actionId) {
        if (actionName != null && !actionName.isBlank()) {
            return actionName;
        }
        return actionId == null || actionId.isBlank() ? "Action" : actionId;
    }

    // ---- Commander search ----

    /** Wraps the Commander's filter when the query could match an alias. */
    @Hook(target = cNB.class, method = "createFilter", at = At.WRAP_RETURN, id = "wrap-commander-text-filter")
    public static pCi wrapFilter(@Return pCi filter, @Arg(0) String query) {
        if (filter == null || query == null || query.isBlank() || aliasLoadError != null) {
            return filter;
        }
        try {
            AliasTable aliases = aliases();
            String[] words = queryWords(query);
            if (words.length == 0 || !aliases.queryCouldMatchAnyAlias(words)) {
                return filter;
            }
            return new AliasFilter(filter, query, words, aliases);
        } catch (Throwable t) {
            log("ALIAS_WRAP_ERROR query=" + quote(query) + " error=" + rootMessage(t));
            return filter;
        }
    }

    private static boolean matchesAlias(Object binding, String query, String[] words, AliasTable aliases) {
        if (!(binding instanceof dmQ actionBinding)) { // only action bindings can have an alias
            return false;
        }
        try {
            dmX action = actionBinding.Ucl(); // the binding's action
            String actionId = action == null ? null : action.TMF(); // the action's ID
            if (actionId == null) {
                return false;
            }
            String aliasKey = aliases.aliasKeyFor(actionId);
            return aliasKey != null && matchesLikeCommander(aliasKey, words);
        } catch (Throwable t) {
            log("ALIAS_MATCH_ERROR query=" + quote(query) + " binding=" + binding.getClass().getName() + " error=" + rootMessage(t));
        }
        return false;
    }

    /** The query's lower-case, whitespace-separated words; empty for a blank query. */
    private static String[] queryWords(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return needle.isEmpty() ? new String[0] : needle.split("\\s+");
    }

    /** Commander-style match: every query word occurs in the (lower-case) alias. */
    private static boolean matchesLikeCommander(String aliasKey, String[] words) {
        if (words.length == 0) {
            return false;
        }
        for (String word : words) {
            if (!aliasKey.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // ---- Config, logging ----

    private static synchronized AliasTable aliases() throws IOException {
        if (aliasTable != null) {
            return aliasTable;
        }
        if (aliasLoadError != null) {
            throw aliasLoadError;
        }
        Path file = MOD.resource("alias_config_directory").resolve("commander-aliases.cfg");
        try {
            aliasTable = AliasTable.load(file);
        } catch (IOException e) {
            aliasLoadError = e;
            log("ALIAS_FILE_UNREADABLE file=" + quote(file.toString()) + " error=" + rootMessage(e)
                    + " (aliases are off until the file is fixed and Bitwig restarted)");
            throw e;
        }
        log("Loaded " + aliasTable.aliasCount() + " aliases from " + file);
        return aliasTable;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void log(String message) {
        MOD.log(message);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getName() + (message == null ? "" : ": " + message);
    }

    /** The Alias column's cell factory: builds the alias cell for each action row. */
    private static final class AliasCellFactory implements dJV {
        private final AliasTable table;

        AliasCellFactory(AliasTable table) {
            this.table = table;
        }

        @Override
        public dve createWidget(Object row) {
            if (row instanceof dmZ action) { // category rows (dmS) get an empty cell
                try {
                    String actionId = action.TMF(); // the action's ID
                    if (actionId != null) {
                        String actionName = actionNameForAction(action, actionId);
                        if (actionName != null && !actionName.isBlank()) {
                            actionNamesById.put(actionId, actionName);
                        }
                        return createAliasControls(actionId, actionName, table.aliasFor(actionId));
                    }
                } catch (Throwable t) {
                    log("ALIAS_CELL_ERROR row=" + row.getClass().getName() + " error=" + rootMessage(t));
                }
            }
            return dut.lYc(""); // an empty label
        }
    }

    /** A Commander filter that also accepts bindings whose action alias matches the query. */
    private static final class AliasFilter implements pCi {
        private final pCi nativeFilter;
        private final String query;
        private final String[] words;
        private final AliasTable table;

        AliasFilter(pCi nativeFilter, String query, String[] words, AliasTable table) {
            this.nativeFilter = nativeFilter;
            this.query = query;
            this.words = words;
            this.table = table;
        }

        @Override
        public boolean accepts(Object binding) {
            return nativeFilter.accepts(binding) || matchesAlias(binding, query, words, table);
        }
    }

    /** alias=actionId file; one alias per action, aliases unique case-insensitively. */
    private static final class AliasTable {
        private final Path path;
        private final LinkedHashMap<String, String> aliasByAction;
        private final LinkedHashMap<String, String> actionByAliasKey;

        private AliasTable(Path path, LinkedHashMap<String, String> aliasByAction, LinkedHashMap<String, String> actionByAliasKey) {
            this.path = path;
            this.aliasByAction = aliasByAction;
            this.actionByAliasKey = actionByAliasKey;
        }

        static AliasTable load(Path path) throws IOException {
            LinkedHashMap<String, String> aliasByAction = new LinkedHashMap<>();
            LinkedHashMap<String, String> actionByAliasKey = new LinkedHashMap<>();
            if (!Files.exists(path)) { // no aliases yet: the file is written with the first one
                return new AliasTable(path, aliasByAction, actionByAliasKey);
            }
            int lineNumber = 0;
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0 || eq == line.length() - 1) {
                    throw new IOException("Invalid alias line " + lineNumber + ": " + raw);
                }
                String alias = line.substring(0, eq).trim();
                String actionId = line.substring(eq + 1).trim();
                if (alias.isEmpty() || actionId.isEmpty()) {
                    throw new IOException("Invalid alias line " + lineNumber + ": " + raw);
                }
                String key = alias.toLowerCase(Locale.ROOT);
                if (actionByAliasKey.containsKey(key)) {
                    throw new IOException("Duplicate alias (case-insensitive): " + alias);
                }
                if (aliasByAction.containsKey(actionId)) {
                    throw new IOException("Multiple aliases for one action are not allowed: " + actionId);
                }
                aliasByAction.put(actionId, alias);
                actionByAliasKey.put(key, actionId);
            }
            return new AliasTable(path, aliasByAction, actionByAliasKey);
        }

        /** The action's alias, or null. */
        synchronized String aliasFor(String actionId) {
            return aliasByAction.get(actionId);
        }

        /** The action's alias in lower case (its key), or null. */
        synchronized String aliasKeyFor(String actionId) {
            String alias = aliasByAction.get(actionId);
            return alias == null ? null : alias.toLowerCase(Locale.ROOT);
        }

        synchronized boolean queryCouldMatchAnyAlias(String[] words) {
            for (String aliasKey : actionByAliasKey.keySet()) {
                if (matchesLikeCommander(aliasKey, words)) {
                    return true;
                }
            }
            return false;
        }

        synchronized int aliasCount() {
            return aliasByAction.size();
        }

        Path path() {
            return path;
        }

        /** Validates, stores and persists; returns the trimmed alias. Validation failures throw IllegalArgumentException. */
        synchronized String addAlias(String actionId, String candidate) throws IOException {
            if (actionId == null || actionId.isBlank()) {
                throw new IllegalArgumentException("Action has no stable ID");
            }
            String alias = candidate == null ? "" : candidate.trim();
            if (alias.isEmpty()) {
                throw new IllegalArgumentException("Enter an alias");
            }
            if (alias.indexOf('=') >= 0 || alias.indexOf('\r') >= 0 || alias.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Alias contains an unsupported character");
            }
            if (alias.startsWith("#") || alias.startsWith(";")) {
                throw new IllegalArgumentException("Alias cannot begin with # or ;");
            }
            if (aliasByAction.containsKey(actionId)) {
                throw new IllegalArgumentException("This action already has an alias");
            }
            String key = alias.toLowerCase(Locale.ROOT);
            String owner = actionByAliasKey.get(key);
            if (owner != null) {
                throw new IllegalArgumentException("Alias is already assigned");
            }
            aliasByAction.put(actionId, alias);
            actionByAliasKey.put(key, actionId);
            try {
                persistLocked();
            } catch (IOException e) {
                aliasByAction.remove(actionId);
                actionByAliasKey.remove(key);
                throw e;
            }
            return alias;
        }

        synchronized String actionForAlias(String alias) {
            String trimmed = alias == null ? "" : alias.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return actionByAliasKey.get(trimmed.toLowerCase(Locale.ROOT));
        }

        /** Removes the action's alias if it still equals {@code expected} (null = any); persists. */
        synchronized boolean removeAlias(String actionId, String expected) throws IOException {
            String alias = aliasByAction.get(actionId);
            if (alias == null) {
                return false;
            }
            if (expected != null && !alias.equals(expected)) {
                return false;
            }
            aliasByAction.remove(actionId);
            actionByAliasKey.remove(alias.toLowerCase(Locale.ROOT));
            try {
                persistLocked();
            } catch (IOException e) {
                aliasByAction.put(actionId, alias);
                actionByAliasKey.put(alias.toLowerCase(Locale.ROOT), actionId);
                throw e;
            }
            return true;
        }

        /** Writes to a sibling .tmp file, then moves it over the config (atomically where supported). */
        private void persistLocked() throws IOException {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>(aliasByAction.size());
            for (Map.Entry<String, String> entry : aliasByAction.entrySet()) {
                lines.add(entry.getValue() + "=" + entry.getKey());
            }
            String fileName = path.getFileName().toString();
            Path tmp = path.resolveSibling(fileName + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Theme objects the alias chip and buttons reuse, so they look native. */
    private static final class NativeAliasResources {
        final eek chipStyle;
        final ejt chipTextStyle;
        final ejE chipFontStyle;
        final ekE addIcon;
        final ekE removeIcon;

        private NativeAliasResources(eek chipStyle, ejt chipTextStyle, ejE chipFontStyle, ekE addIcon, ekE removeIcon) {
            this.chipStyle = chipStyle;
            this.chipTextStyle = chipTextStyle;
            this.chipFontStyle = chipFontStyle;
            this.addIcon = addIcon;
            this.removeIcon = removeIcon;
        }

        static NativeAliasResources resolve() {
            eiH theme = dpN.TVZ().Pus();       // the application's theme
            ehp shortcutStyles = theme.O1();   // shortcut styles
            eht chipStyle = shortcutStyles.wGc(); // the shortcut chip
            return new NativeAliasResources(
                    chipStyle,
                    chipStyle.iHb(),           // the chip's text style
                    theme.Ai().lYc(),          // typography: the chip font
                    shortcutStyles.iHb(),      // the add icon
                    chipStyle.ERO());          // the chip's remove icon
        }
    }
}
