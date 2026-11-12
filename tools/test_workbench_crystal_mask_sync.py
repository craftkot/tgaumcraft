#!/usr/bin/env python3
"""Guard the Arcane Workbench's server-to-client primal requirement DataSlot."""
from pathlib import Path
import re

SOURCE = Path("src/neoforge/java/dev/ehtid/thaumcraft/menu/ArcaneWorkbenchMenu.java")
source = SOURCE.read_text(encoding="utf-8")


def method_body(signature: str) -> tuple[str, str]:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"missing constructor/method: {signature}")
    opening = source.find("{", start)
    if opening < 0:
        raise AssertionError(f"missing body: {signature}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:opening], source[opening + 1:index]
    raise AssertionError(f"unterminated body: {signature}")


checks = 0


def require(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


client_signature, client_body = method_body(
    "public ArcaneWorkbenchMenu(int id, Inventory inventory)"
)
require(
    re.search(
        r"DataSlot\\.standalone\\(\\)\\s*,\\s*"
        r"DataSlot\\.standalone\\(\\)\\s*,\\s*"
        r"DataSlot\\.standalone\\(\\)",
        client_body,
    ) is not None,
    "client menu must own a writable standalone slot for the required crystal mask",
)

server_signature, server_body = method_body(
    "public ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench)"
)
require(
    "crystalMaskSlot(bench)" in server_body,
    "server menu must compute the required crystal mask from its BlockEntity",
)

private_signature, private_body = method_body(
    "private ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench,"
)
require(
    "DataSlot requiredCrystalMask" in private_signature,
    "shared constructor must receive the side-specific crystal-mask DataSlot",
)
require(
    "this.requiredCrystalMask = requiredCrystalMask;" in private_body,
    "shared constructor must retain the side-specific crystal-mask DataSlot",
)
require(
    "requiredCrystalMask = crystalMaskSlot(bench)" not in private_body,
    "client constructor must not recompute the mask from its empty SimpleContainer",
)
require(
    "addDataSlot(requiredCrystalMask);" in private_body,
    "required crystal mask must participate in vanilla menu synchronization",
)

mask_signature, mask_body = method_body(
    "private static DataSlot crystalMaskSlot(Container bench)"
)
require(
    re.search(r"matchesThaumometerPattern\\(\\)\\s*return 0b111111;", mask_body)
    is not None,
    "BETA26 Thaumometer preview must require all six primal crystals",
)

print(f"Workbench crystal-mask DataSlot wiring: PASS ({checks} assertions)")
