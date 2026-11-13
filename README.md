# Mods

One folder per mod. The ten frameworks (Actions and Menus, Settings, Inspector, BTE Theme Loader, Custom Nitro Registration, Enhanced Panel Management, Browser Configuration, Plug-in Panels, Piano Roll, Notifications) are in the BML repository (`BML/frameworks/`); the table below covers them too. Build with BML's tools from the workspace (`BML/tools/README.md`). Each has `releases/` (every packaged ZIP), and optionally `test/` (offline tests) and `test-components/` (components installed into test suites only).

## Layout (migrated mods)

| Path | Contents |
|---|---|
| `mod.json` | The shipped manifest, copied verbatim. `mod.version` names the release. For a mod built with the mod kit, the manifest without the `@Hook` hooks; the shipped one is generated (`modkit/README.md`). |
| `build.json` | `release` (javac `--release`), optional `release_overrides` (`{"<path under src>": 17}`), optional `classpath_mods` (mods whose `helper.jar` is needed to compile: `"<mod-id>"` for its accepted version, `"<mod-id>@<version>"` for another release), optional `debug` (`javac -g`), optional `kit` (`true`: build with the mod kit). |
| `src/` | `*.java` is compiled into `helper.jar`; every other file (e.g. `bml-actions/*.properties`) goes into `helper.jar` unchanged. |
| `README.md` | Required. The mod's one README: shipped in the release folder, and shown by GitHub. |
| `images/` | Optional. The README's images (PNG, JPEG or GIF), linked by relative path (`![Alt](images/window.png)`); each one the README references ships at the same path, so GitHub and Patchbay show the same README. |
| `package/` | Optional. Copied into the release folder unchanged (descriptors, `resources/...`); never a `README.md`. |

`python BML/tools/build_mod.py <id>` (from the workspace folder) builds the release. Every accepted mod is in this layout, and every accepted release was rebuilt from source on 26 Oct 2026.

This is the one acceptance table for the frameworks and the mods. The **Accepted** column is read by `BML/tools/bml_build.py` and `build_mod.py`: it installs `releases/<id>-<accepted>.zip`. Change it here when a new version is accepted.

| Mod | ID | Accepted | Active dev |
|---|---|---|---|
| Framework: Actions and Menus | `action-menu-composition-framework` | 0.8.0-dev.1 | |
| Arranger: Add Automation Points | `add-automation-points` | 1.1.0-dev.2 | 1.1.0-dev.3 |
| Framework: Themes | `bte-theme-loader` | 0.9.0-dev.4 | 0.10.0-dev.1 |
| Commander: Run Order Crash Fix | `commander-dispatch-fix` | 1.2.0-dev.1 | 1.2.0-dev.2 |
| Commander: Enter Selects First Result | `commander-first-result` | 1.1.0-dev.3 | 1.1.0-dev.4 |
| Arranger: Compact Expanded Groups | `compact-expanded-groups` | 1.4.0-dev.2 | 2.0.0-dev.1 |
| Device: More Remote Control Pages | `configurable-remote-control-pages` | 1.1.0-dev.1 | 1.1.0-dev.2 |
| Arranger: Cue-Based Arrangement Helper | `cue-based-arrangement-helper` | 1.1.0-dev.1 | 1.1.0-dev.2 |
| Framework: Nitro Devices | `custom-device-registrar` | 1.2.0-dev.4 | 1.3.0-dev.3 |
| Device: Keep Plug-in Windows Closed on Insert | `device-window-helper` | 1.1.0-dev.3 | 1.2.0-dev.1 |
| Track: Exclusive Arm | `exclusive-arm` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Browser (side): Search Bar Shortcut | `focus-side-browser-search` | 0.3.0-dev.1 | 0.3.0-dev.2 |
| Framework: Browser | `browser-framework` | 0.2.0-dev.2 | 0.3.0-dev.1 |
| Framework: Inspector | `inspector-composition-framework` | 0.2.0-dev.11 | 0.3.0-dev.1 |
| Framework: Piano Roll | `piano-roll-framework` | 0.1.0-dev.10 | 0.2.0-dev.1 |
| Framework: Notifications | `notifications-framework` | 0.2.0-dev.3 | 0.3.0-dev.1 |
| Framework: Plug-in Panels | `plugin-panels-framework` | 0.1.0-dev.14 | 0.3.0-dev.2 |
| Framework: Panels | `panel-management-framework` | 0.1.0-dev.9 | 0.2.0-dev.2 |
| Piano Roll: Pencil Pitch Slides | `pencil-modifiers` | 0.1.0-dev.3 | 0.1.0-dev.4 |
| Piano Roll: Overlaps to Pitch Slides | `mpe-overlap-to-pitch-slide` | 0.2.0-dev.8 | 0.3.0-dev.2 |
| Panel: Streamlined Views | `panel-sides` | 0.4.0-dev.9 | 0.5.0-dev.1 |
| Panel: Mix Map | `mix-map` | 0.1.0-dev.4 | 0.2.0-dev.1 |
| Panel: Shared Tool Mode | `shared-tool-mode` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Clips: Edit New Clips Reliably | `edit-clips-reliably` | 0.1.0-dev.3 | 0.1.0-dev.4 |
| Arranger: Paste Time Makes Room | `paste-time` | 1.1.0-dev.12 | 1.1.0-dev.13 |
| Device: Optional Plug-in Undo Steps | `plugin-undo` | 1.1.0-dev.2 | 1.1.0-dev.3 |
| Framework: Settings | `preferences-composition-framework` | 1.0.0 | |
| Browser: Enable Project File Filter | `projects-in-browser` | 1.2.0-dev.4 | 1.2.0-dev.5 |
| Browser: Pre-5.0 Style Side Browser | `pre-5-style-side-browser` | 0.1.0-dev.6 | 0.2.0-dev.1 |
| Device: Always Show Remote Control Page Buttons | `remote-control-page-buttons-always-visible` | 1.2.0-dev.1 | 1.3.0-dev.1 |
| Commander: Assign Aliases to Actions | `shortcut-aliases` | 1.2.0-dev.4 | 1.2.0-dev.5 |
| Shortcuts: Share a Key | `shortcut-share-key` | 0.1.0-dev.1 | |
| Browser (side): Enter Inserts Selected Result | `side-browser-enter-selected-device` | 0.3.0-dev.6 | 0.3.0-dev.7 |
| Parameter: Randomise Hovered Parameter | `single-parameter-randomizer` | 1.4.0-dev.1 | 1.4.0-dev.2 |
| Device: Randomise Remote Controls | `visible-remote-controls-randomizer` | 1.1.0-dev.1 | 1.1.0-dev.2 |
| Browser: Add to Drum Machine | `add-to-drum-machine` | 0.1.0-dev.3 | 0.1.0-dev.4 |
| Piano Roll: Pitch Expression Pointer | `pitch-expression-pointer` | 0.1.0-dev.8 | 0.1.0-dev.9 |
| Interface: Smooth Scrolling | `look-and-feel` | 0.4.0-dev.1 | 0.4.0-dev.2 |
| Helper: Audio Analysis | `audio-analysis-runtime` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Audio Clip: Detect Key | `audio-clip-detect-key` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Audio Clip: Separate Stems | `audio-clip-separate-stems` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Audio Clip: Audio to MIDI | `audio-clip-to-midi` | 0.1.0-dev.4 | 0.1.0-dev.5 |
| Transport: Capture MIDI | `capture-midi` | 0.1.0-dev.2 | 0.1.0-dev.4 |
| Device: Collapse and Expand Shortcuts | `device-collapse-keys` | 0.1.0-dev.2 | 0.2.0-dev.1 |
| Detail Editor: Drag Selection Follows Content | `drag-selection-follows-content` | 0.1.0-dev.2 | 0.1.0-dev.3 |
| Track: Hibernate Tracks | `hibernate-tracks` | 0.1.0-dev.9 | 0.1.0-dev.12 |
| Piano Roll: MIDI Tools | `midi-tools` | 0.1.0-dev.13 | 0.1.0-dev.15 |
| Piano Roll: Ramp Velocity | `ramp-velocity` | 0.1.0-dev.2 | 0.1.0-dev.4 |
| Arranger: Shift-Select Across Groups | `shift-select-across-groups` | 0.1.0-dev.1 | 0.1.0-dev.2 |
| Inspector: Show Chord Name | `show-chord-name` | 0.1.0-dev.1 | 0.1.0-dev.2 |
| Export: Stems with Bus Effects | `stem-export-bus-fx` | 0.1.0-dev.1 | 0.1.0-dev.2 |
| Piano Roll: Nudge and Resize by Grid | `sub-division-based-operations` | 0.1.0-dev.6 | 0.2.0-dev.1 |
| Arranger: Collapse and Expand Shortcuts | `track-collapse-keys` | 0.1.0-dev.2 | 0.2.0-dev.1 |
| Device: All Lanes View | `all-lanes-view` | 0.1.0-dev.9 | 0.1.0-dev.10 |
| Automation: Quantize Points | `quantize-automation-points` | 0.1.0-dev.2 | 0.1.0-dev.3 |
| Device: Selected Tracks View | `selected-tracks-view` | 0.1.0-dev.2 | 0.1.0-dev.3 |
| Arranger: Default Track Height | `default-track-height` | | 0.1.0-dev.1 |
| Device: Toggle Enabled Shortcut | `device-toggle-enabled-key` | | 0.1.0-dev.1 |
| Inspector: Minimise Blocks | `inspector-minimise-blocks` | | 0.1.0-dev.1 |
| Notifications: History | `notification-history` | | 0.1.0-dev.1 |
| Panel: Scale Each Panel | `panel-scale` | | 0.1.0-dev.1 |
| Arranger: Paste Time and Cut Time in Menus | `paste-cut-time-menus` | | 0.1.0-dev.1 |
| Notifications: Pop-ups in the Centre | `popups-in-notification-centre` | | 0.1.0-dev.1 |
| Device: Right-Click Page Buttons to Edit Remote Controls | `remote-control-page-buttons-edit` | | 0.1.0-dev.1 |
| Device: Toggle Selected Devices' Windows Together | `selected-device-windows` | | 0.1.0-dev.1 |
| Arranger: Track Headers on the Right | `track-headers-right` | | 0.1.0-dev.1 |
| Browser: Hide Parts | `browser-hide-parts` | | 0.1.0-dev.1 |
| Panel: Choose Sides | `choose-panel-sides` | | 0.1.0-dev.1 |
| Piano Roll: Make Legato (Later → Earlier) | `legato-later-to-earlier` | | 0.1.0-dev.2 |
| Detail Editor: Open Maximised | `open-detail-editor-maximised` | | 0.1.0-dev.1 |
| Panel: Separate Browser Panels | `separate-browser-panels` | | 0.1.0-dev.1 |
| Piano Roll: Split Notes at Grid | `split-notes-at-grid` | | 0.1.0-dev.1 |
| Grid: Straight and Triplet Only | `straight-triplet-grid` | | 0.1.0-dev.1 |
| Notifications: Tidy Task Rows | `tidy-notification-rows` | | 0.1.0-dev.1 |

Accepted versions are for Windows. **Loader 0.35.0-dev.4 (1 Nov):** the 22 mods above plus Enhanced Panel Management, Streamlined Views and Mix Map passed loaded together on Windows 6.1.4, Linux 6.1.4 (WSL), macOS 6.1.4 and Windows 6.1 (`testing/results/prerequisites4-614`, `prerequisites-linux-614`, `prerequisites-mac-614`, `full-suite-61`); the three panel mods are accepted with that pass. **Linux (Bitwig 6.1.4):** the five frameworks and Device: Always Show Remote Control Page Buttons, at the versions above, passed (31 Oct, WSL2 Ubuntu 24.04, `testing/results/linux-step8-614`), and so did all 22 accepted mods loaded together (31 Oct, loader 0.34.0-dev.20, `testing/results/linux-all-614`) except Window Helpers and VST / CLAP Plugin Undo Toggle, which need a third-party plug-in and were not run in WSL; the user accepted them on their Windows and macOS passes. **macOS (Bitwig 6.1.4):** all 22 accepted mods passed together (31 Oct, loader 0.34.0-dev.20, MacBook Pro M1, `testing/results/mac-all-614`; the five frameworks and Always Show Remote Control Page Buttons first in `testing/results/mac-step8-614`).


