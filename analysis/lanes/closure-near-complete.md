

### 2026-11-15 — exact-head CI confirms the generator-anchor failure

- Native scan-rune run [#43745642987](https://github.com/craftkot/tgaumcraft/actions/runs/43745642987) on `ea4bcd3` passed the 5,321 orientation assertions and 11 log-parser assertions, then failed in the Python generator regression. The generated Bash validation and Minecraft client steps were skipped; no world or particle behavior was tested.
- The test subprocess captured its stderr, so the Actions log shows only `CalledProcessError`. Direct source inspection identifies the cause: the generator expected a literal shell `${create_world_x}`/`${create_world_y}` anchor but retained a backslash in the Python string, so the exact source match failed. Corrected that anchor in `91798a8` and added an assertion for the emitted local-coordinate marker in `71329cd`.
- Exact-head scan-rune reruns [#43745765631](https://github.com/craftkot/tgaumcraft/actions/runs/43745765631) (`71329cd`, code+test) and [#43745771475](https://github.com/craftkot/tgaumcraft/actions/runs/43745771475) (`3901777`, docs-only) are queued. Do not claim the corrected generator passed until the first rerun completes its static preflight and Bash syntax steps.

### 2026-11-15 — native scan-rune rerun: generator fixed, world creation still blocked

- Exact-head native run [#43745765631](https://github.com/craftkot/tgaumcraft/actions/runs/43745765631) passes orientation, log-parser, generated-script and Bash validation; it then launches Minecraft 1.21.1 / NeoForge 21.1.250, but fails `INTEGRATED_WORLD_PLAYER_LOGIN=NOT_CONFIRMED`. The real-client log reaches recipe/advancement loading, not player login. Run [#43745771475](https://github.com/craftkot/tgaumcraft/actions/runs/43745771475) has the same step-level failure.
- The observed click is `CREATE_NEW_WORLD_CLICK=349,757 LOCAL=348,737` with window 1024x768. The recent y correction reaches the expected vertical button band, but horizontal hitbox membership is not proven. Screenshot `04a-after-create-world-click` from artifact [#14174254554](https://github.com/craftkot/tgaumcraft/actions/runs/43745765631/artifacts/14174254554) must be inspected before changing coordinates. No production Java defect is established.
- Local Git remains blocked by DNS (`Could not resolve host: github.com`); no local Gradle/client run. Do not award gameplay credit: 59/100, M16 4/6, M08 5/9. Next: inspect actual screen/button bounds and confirm world creation, then rerun authoritative gold target and both rune viewpoints; separately complete Salis chunk-boundary/reload/ticket/network acceptance.
