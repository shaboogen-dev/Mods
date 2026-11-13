# Commander: Enter Selects First Result

Makes Bitwig Commander's **Enter** key behave more directly after a search.

When the Commander query is non-empty and no result row has been explicitly selected, pressing **Enter** uses Bitwig's own native selection routine to select the first visible result. Bitwig's normal Enter handler then executes that result.

If a result was explicitly selected with the mouse or keyboard, that selection remains authoritative.

This mod is independent of Commander Aliases and is useful with stock Commander search on its own.

## Release

- Mod ID: `commander-first-result`
- Version: `1.0.1`
- Author: `shaboogen`
- Built against: Bitwig Studio 6.1.3 / Windows x64
- Loader baseline: BML v2 Preview 2 / map23 (with parser and case-sensitive compatibility-index fixes)
- Same-build acceptance: Bitwig Studio 6.1.3 / Windows x64

## Debug Mode support traces

The release keeps a narrow support-oriented trace surface for diagnosing bugs under BML Debug Mode. It covers the Commander query/filter hook, the Enter listener hook, both helper bridge methods, and Bitwig's native `commander.select_next_result` operation.

Normal non-Debug launches do not enable tracing.

**Privacy note:** Debug traces may contain text entered into Commander. Review trace contents before sharing them publicly.

## Compatibility

This release is built against and accepted on Bitwig Studio 6.1.3 / Windows x64. Cross-build target mappings exist in the BML v2 compatibility data, but runtime acceptance on other Bitwig builds should be treated separately until tested.
