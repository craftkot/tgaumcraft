#!/usr/bin/env python3
"""Original Thaumcraft 6.1.BETA26 BASIC caster registration + interaction guard."""
from pathlib import Path
import hashlib
import json

base=Path("src/main/resources/assets/thaumcraft")
png=(base/"textures/entity/armor/caster_basic.png").read_bytes()
assert png[:8]==bytes.fromhex("89504e470d0a1a0a")
assert hashlib.sha256(png).hexdigest()=="e686075c5b3c5b41946086021860c0310cdfddc3daefd07609a495474b7279c1"
gauntlet=json.loads((base/"models/item/caster_gauntlet.json").read_text())
basic=json.loads((base/"models/item/caster_basic.json").read_text())
assert gauntlet["ambientocclusion"] is False
assert [e["from"] for e in gauntlet["elements"]]==[
    [6,1.5,6],[6.25,0,6.25],[6,3,6]]
assert [e["to"] for e in gauntlet["elements"]]==[
    [10,7,10],[9.75,6,9.75],[10,6,10]]
assert all("tintindex" in side for element in gauntlet["elements"]
           for side in element["faces"].values())
assert "firstperson_righthand" in gauntlet["display"]
assert basic["parent"]=="thaumcraft:item/caster_gauntlet"
assert basic["textures"]["skin"]=="thaumcraft:entity/armor/caster_basic"

item=Path("src/neoforge/java/dev/ehtid/thaumcraft/item/BasicCasterItem.java").read_text()
matrix=Path("src/neoforge/java/dev/ehtid/thaumcraft/blockentity/InfusionMatrixBlockEntity.java").read_text()
block=Path("src/neoforge/java/dev/ehtid/thaumcraft/block/InfusionMatrixBlock.java").read_text()
registry=Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftItems.java").read_text()
creative=Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftCreativeTabs.java").read_text()
assert "onItemUseFirst(ItemStack stack, UseOnContext context)" in item
assert "instanceof InfusionMatrixBlockEntity matrix" in item
assert "player.mayUseItemAt" in item
assert "context.getLevel().isClientSide()" in item
assert "if (!matrix.active())" in item and "else if (!matrix.crafting())" in item
assert "matrix.tryActivateFromCaster()" in item
assert "matrix.beginVerifiedCrystalFarmerFromFutureCaster(serverPlayer)" in item
assert "return beginVerifiedInfusionFromCaster(player);" in matrix
assert "InfusionRecipeAdapter.prepare(this, knowledge)" in matrix
assert "player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA)" in matrix
assert "ritualLedger = LegacyInfusionRitualLedger.begin" in matrix
assert "public boolean tryActivateFromCaster()" in matrix
assert "useWithoutItem" not in block and "useItemOn" not in block
assert '"caster_basic", BasicCasterItem::new' in registry
assert "ThaumcraftItems.CASTER_BASIC.get()" in creative
for lang in ("en_us","ru_ru"):
    translated=json.loads((base/"lang"/f"{lang}.json").read_text())
    assert translated["item.thaumcraft.caster_basic"]
smoke=Path("tools/client_smoke.sh").read_text()
assert "xdotool click 3" in smoke
assert "CASTER_CLICK_ACTIVATED_MATRIX_OK" in smoke
assert "CASTER_NO_UNVERIFIED_RECIPE_OK" in smoke
print("BETA26 BASIC caster: original model/texture, player-only two-step activation, research gate")
