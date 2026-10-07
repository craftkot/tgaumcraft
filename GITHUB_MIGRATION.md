# GitHub migration

This repository is the working source tree for the Thaumcraft 6.1.BETA26 -> Minecraft 1.21.1 NeoForge port.

## Important
- Keep the repository **private** while it contains ported/copied Thaumcraft assets and compatibility reference material.
- Do **not** commit the supplied original `Thaumcraft-1.12.2-6.1.BETA26.jar`.
- The original JAR remains in the ChatGPT Library durable backup and is used only as a behavior/reference oracle.

## Verification
Run:

```bash
./scripts/test_core.sh
```

This compiles the loader-independent Java 21 core, runs `CoreParityTest`, and runs the Stage 10 audit.

GitHub Actions additionally attempts a real NeoForge Gradle build using Gradle 9.2.1.

## Current durable baseline
- Overall port: 27%
- CoreParityTest: 4667 assertions OK
- Stage 10 audit: AUDIT OK
- Original reference JAR SHA-256: `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`
