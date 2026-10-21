#!/usr/bin/env python3
"""M07 regression: controls must win over list/grid node click targets.

Checks the real ResearchIndexScreen source because the NeoForge Screen class
is not available in the low-cost standalone Java 21 test environment.
"""
from pathlib import Path

source = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/ResearchIndexScreen.java"
              ).read_text(encoding="utf-8")
start = source.index("public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)")
end = source.index("public void onClose()", start)
method = source[start:end]
first = "if (super.mouseClicked(mouseX, mouseY, mouseButton)) return true;"
assert first in method, "GUI buttons must consume clicks before research rows"
assert method.index(first) < method.index("nodeAt(currentEntries()")
assert method.index(first) < method.index("viewport.entryIndexAtRow(")
assert "return false;" in method, "unhandled clicks should not be consumed"

# The original collision was real: row 10 bounds (0-based row 9)
# intersect the List/Tree toggle inside book coordinates.
last_row = (63 + 9*23, 63 + 10*23)
toggle = (284, 304)
assert max(last_row[0], toggle[0]) < min(last_row[1], toggle[1])
assert "bounds(x + 395, y + 284, 67, 20)" in source
assert "bounds(x + 398, y + 308, 35, 20)" in source
assert "selectedEntry = entries.get(index).key();" in source
assert "return false;" in method
print("M07 research book widget-priority/click routing: PASS")
