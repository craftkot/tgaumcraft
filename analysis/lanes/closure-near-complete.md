

### 2026-11-15 — exact-head CI confirms the generator-anchor failure

- Native scan-rune run [#43745642987](https://github.com/craftkot/tgaumcraft/actions/runs/43745642987) on `ea4bcd3` passed the 5,321 orientation assertions and 11 log-parser assertions, then failed in the Python generator regression. The generated Bash validation and Minecraft client steps were skipped; no world or particle behavior was tested.
- The test subprocess captured its stderr, so the Actions log shows only `CalledProcessError`. Direct source inspection identifies the cause: the generator expected a literal shell `${create_world_x}`/`${create_world_y}` anchor but retained a backslash in the Python string, so the exact source match failed. Corrected that anchor in `91798a8` and added an assertion for the emitted local-coordinate marker in `71329cd`.
- Exact-head scan-rune reruns [#43745765631](https://github.com/craftkot/tgaumcraft/actions/runs/43745765631) (`71329cd`, code+test) and [#43745771475](https://github.com/craftkot/tgaumcraft/actions/runs/43745771475) (`3901777`, docs-only) are queued. Do not claim the corrected generator passed until the first rerun completes its static preflight and Bash syntax steps.
