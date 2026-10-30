#!/usr/bin/env python3
"""Extract BETA26 infusion registration facts from javap, without loading Minecraft.

The disassembly is a local reference input, never an output committed to the port.
This deliberately small JVM interpreter rejects instructions/methods it does not
understand. It executes loops, snapshots registrations, and distinguishes fake
research-book examples from actual crafting recipes.
"""
from __future__ import annotations

import argparse
import collections
import copy
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "src/main/resources/data/thaumcraft/infusion/beta26_source_catalog.json"
MAPPINGS = DEFAULT_OUTPUT.with_name("beta26_legacy_mappings.json")
RUNTIME = DEFAULT_OUTPUT.with_name("runtime_recipes.json")
DEPENDENCIES = DEFAULT_OUTPUT.with_name("beta26_dependencies.json")
ACQUISITION = DEFAULT_OUTPUT.with_name("beta26_acquisition_source.json")
METHOD = "initializeInfusionRecipes"
BASE = "thaumcraft/api/crafting/InfusionRecipe"
RUNIC = "thaumcraft/common/lib/crafting/InfusionRunicAugmentRecipe"
ENCHANT = "thaumcraft/common/lib/crafting/InfusionEnchantmentRecipe"
STACK = "net/minecraft/item/ItemStack"
ASPECTS = "thaumcraft/api/aspects/AspectList"
DYES = "WHITE ORANGE MAGENTA LIGHT_BLUE YELLOW LIME PINK GRAY SILVER CYAN PURPLE BLUE BROWN GREEN RED BLACK".split()
ENCHANTMENTS = {
    "BURROWING": (2, 1, ["axe", "pickaxe"]),
    "COLLECTOR": (0, 1, ["axe", "pickaxe", "shovel", "weapon"]),
    "DESTRUCTIVE": (1, 1, ["axe", "pickaxe", "shovel"]),
    "REFINING": (4, 4, ["pickaxe"]),
    "SOUNDING": (3, 4, ["pickaxe"]),
    "ARCING": (5, 4, ["weapon"]),
    "ESSENCE": (6, 5, ["weapon"]),
    "LAMPLIGHT": (12, 1, ["axe", "pickaxe", "shovel"]),
}


def instructions(source: str):
    method = source.split("  public static void initializeInfusionRecipes();", 1)[1].split(
        "  public static void initializeNormalRecipes", 1)[0]
    code = []
    for line in method.splitlines():
        match = re.match(r"\s+(\d+): (\w+)\s*(.*)", line)
        if match:
            pc, op, rest = match.groups()
            operand, _, comment = rest.partition("// ")
            code.append((int(pc), op, operand.strip(), comment.strip()))
    assert code and code[-1][1] == "return"
    return method, code


def argument_count(descriptor: str):
    return len(re.findall(r"\[*L[^;]+;|\[*[ZBCSIJFD]", descriptor.split(")", 1)[0][1:]))


def field_value(ref: str):
    if ref == "defaultGroup": return {"kind": "recipe_group", "legacy_field": "ConfigRecipes.defaultGroup"}
    owner, name = ref.rsplit(".", 1)
    if owner == "thaumcraft/common/config/ConfigItems" and name.endswith("_CRYSTAL"):
        return {"kind": "aspect_crystal", "aspect": name.removesuffix("_CRYSTAL"), "count": 1,
                "legacy_field": ref}
    if owner == "thaumcraft/api/aspects/Aspect":
        return {"kind": "aspect", "name": name}
    if owner.endswith("EnumInfusionEnchantment"):
        return {"kind": "infusion_enchantment", "name": name}
    if owner.endswith("EnumDyeColor"):
        return {"kind": "dye", "name": name}
    if owner.endswith("ItemsTC") or owner == "net/minecraft/init/Items":
        return {"kind": "item", "legacy_field": ref}
    if owner.endswith("BlocksTC") or owner == "net/minecraft/init/Blocks":
        return {"kind": "block", "legacy_field": ref}
    raise ValueError("Unknown field " + ref)


def stack(item, count=1, metadata=0):
    return {"kind": "stack", "item": item, "count": count, "metadata": metadata}


def ingredient(value):
    if isinstance(value, str):
        return {"kind": "ore", "alias": value}
    if value.get("kind") in ("item", "block"):
        return {"kind": "item_any_metadata", "item": value}
    return value


def recipe(research, output, instability, aspects, central, sides):
    if isinstance(output, list):
        assert len(output) == 2 and isinstance(output[0], str) and output[1]["kind"] == "nbt"
        output = {"kind": "copy_center_set_nbt", "key": output[0], "value": output[1]}
    else:
        output = {"kind": "stack", "stack": output}
    return {"research": research, "output": output, "instability": instability,
            "aspects": aspects["values"], "center": ingredient(central),
            "sides": list(map(ingredient, sides))}


class Interpreter:
    def __init__(self, code):
        self.code = code
        self.pc_index = {row[0]: index for index, row in enumerate(code)}
        self.stack = []
        self.locals = {}
        self.actual = []
        self.fake = []
        self.executed = collections.Counter()

    def call(self, owner, name, args, receiver, pc):
        if name == '"<init>"':
            receiver["constructor_offset"] = pc
            if owner == STACK:
                receiver.update(stack(*args))
            elif owner == "net/minecraft/util/ResourceLocation":
                receiver.update(kind="resource", value=args[0])
            elif owner == ASPECTS:
                receiver.update(kind="aspects", values={})
            elif owner.startswith("net/minecraft/nbt/NBTTag"):
                receiver.update(kind="nbt", type=owner.rsplit("NBTTag", 1)[1].lower(), value=args[0])
            elif owner == "java/lang/StringBuilder":
                receiver.update(kind="builder", value="")
            elif owner == BASE:
                receiver.update(recipe(*args))
            elif owner == RUNIC:
                receiver.update(research="RUNICSHIELDING", instability={"formula": "5 + trunc(oldRunic / 2)"},
                                aspects={"formula": "PROTECT=20+int(20*pow(2,oldRunic)); CRYSTAL=ENERGY=trunc(PROTECT/2)"},
                                center={"kind": "armor_or_bauble"} if not args else ingredient(args[0]),
                                sides=[{"kind": "item_any_metadata", "item": field_value("thaumcraft/api/items/ItemsTC.salisMundus")},
                                       {"kind": "ore", "alias": "gemAmber", "count_formula": "1 + max(0, oldRunic)"}],
                                output={"kind": "copy_center_runic", "key": "TC.RUNIC", "nbt_type": "byte", "formula": "oldRunic + 1"})
            elif owner == ENCHANT:
                if args[0].get("kind") == "infusion_enchantment":
                    name = args[0]["name"]
                    ordinal, maximum, tools = ENCHANTMENTS[name]
                    receiver.update(research="INFUSIONENCHANTMENT", instability=4, aspects=args[1]["values"],
                                    aspect_multiplier="float(oldLevel + 1) + float(otherInfusionEnchantmentCount) * 0.33f",
                                    center={"kind": "infusion_enchantable", "tool_classes": tools}, sides=list(map(ingredient, args[2])),
                                    output={"kind": "copy_center_infusion_enchantment", "enchantment": name,
                                            "ordinal": ordinal, "max_level": maximum, "level_formula": "oldLevel + 1",
                                            "warp": {"key": "TC.WARP", "type": "byte", "increment": 1,
                                                     "condition": "random.nextInt(10) < existingInfusionEnchantmentCount"}})
                else:
                    receiver.update(copy.deepcopy(args[0]))
                    receiver["class"] = ENCHANT
                    receiver["center"] = ingredient(args[1])
            elif owner == "thaumcraft/api/crafting/IngredientNBTTC":
                receiver.update(kind="stack_nbt", stack=args[0])
            else:
                raise ValueError("Unknown constructor " + owner)
            return None
        if owner == ASPECTS and name == "add":
            key = args[0]["name"]
            receiver["values"][key] = receiver["values"].get(key, 0) + args[1]
            return receiver
        if owner == STACK and name == "func_77983_a":
            receiver.setdefault("nbt", {})[args[0]] = args[1]
            return None
        if owner == "java/lang/StringBuilder":
            if name == "append":
                receiver["value"] += str(args[0]); return receiver
            if name == "toString":
                return receiver["value"]
        if owner == "java/util/HashMap" and name == "get":
            assert receiver["legacy_field"] == "thaumcraft/api/blocks/BlocksTC.nitor"
            return {"kind": "block", "legacy_field": receiver["legacy_field"], "map_key": args[0]["name"]}
        if owner == "net/minecraft/item/EnumDyeColor" and name == "values":
            return [{"kind": "dye", "name": dye} for dye in DYES]
        if owner == "thaumcraft/api/ThaumcraftApiHelper" and name == "makeCrystal":
            return {"kind": "aspect_crystal", "aspect": args[0]["name"], "count": 1,
                    "helper": owner + "." + name}
        if owner == "thaumcraft/api/golems/GolemHelper" and name == "getSealStack":
            return {"kind": "seal_stack", "seal": args[0], "count": 1, "helper": owner + "." + name}
        if owner == "net/minecraft/item/crafting/Ingredient":
            if name == "func_193367_a":
                return {"kind": "item_any_metadata", "item": args[0]}
            if name == "func_193369_a":
                return {"kind": "any_of", "alternatives": args[0]}
        if owner.endswith("EnumInfusionEnchantment") and name == "addInfusionEnchantment":
            args[0].setdefault("infusion_enchantments", []).append({"enchantment": args[1]["name"], "level": args[2]})
            return None
        if owner == "thaumcraft/api/ThaumcraftApi":
            value = copy.deepcopy(args[1])
            value["id"] = args[0]["value"]
            value["registration_offset"] = pc
            if name == "addInfusionCraftingRecipe":
                self.actual.append(value)
            elif name == "addFakeCraftingRecipe":
                self.fake.append(value)
            else:
                raise ValueError(name)
            return None
        raise ValueError(f"Unknown method {owner}.{name}")

    def run(self):
        index = 0
        steps = 0
        while index < len(self.code):
            pc, op, operand, comment = self.code[index]
            self.executed[pc] += 1
            steps += 1
            assert steps < 100000, "Loop did not terminate"
            index += 1
            s = self.stack
            try:
                if op == "new":
                    s.append({"class": comment.removeprefix("class ")})
                elif op == "dup": s.append(s[-1])
                elif op.startswith("iconst_"): s.append(int(op[7:]))
                elif op in ("bipush", "sipush"): s.append(int(operand))
                elif op in ("ldc", "ldc_w"):
                    assert comment.startswith("String "), comment
                    s.append(comment[7:])
                elif op == "getstatic":
                    s.append(field_value(comment.removeprefix("Field ").split(":", 1)[0]))
                elif op == "anewarray": s.append([None] * s.pop())
                elif op == "aastore":
                    value, array_index, array = s.pop(), s.pop(), s.pop()
                    array[array_index] = value
                elif op == "aaload":
                    array_index, array = s.pop(), s.pop(); s.append(array[array_index])
                elif op == "arraylength": s.append(len(s.pop()))
                elif op.startswith(("astore", "istore")):
                    key = int(op.split("_")[1]) if "_" in op else int(operand)
                    self.locals[key] = s.pop()
                elif op.startswith(("aload", "iload")):
                    key = int(op.split("_")[1]) if "_" in op else int(operand)
                    s.append(self.locals[key])
                elif op == "iinc":
                    key, value = map(int, operand.split(", ")); self.locals[key] += value
                elif op == "i2b": s.append((s.pop() + 128) % 256 - 128)
                elif op == "if_icmpge":
                    right, left = s.pop(), s.pop()
                    if left >= right: index = self.pc_index[int(operand)]
                elif op == "ifle":
                    if s.pop() <= 0: index = self.pc_index[int(operand)]
                elif op == "goto": index = self.pc_index[int(operand)]
                elif op == "checkcast": pass
                elif op.startswith("invoke"):
                    method = comment.removeprefix("Method ")
                    target, descriptor = method.split(":", 1)
                    owner, name = target.rsplit(".", 1)
                    n = argument_count(descriptor)
                    args = s[-n:] if n else []
                    if n: del s[-n:]
                    receiver = None if op == "invokestatic" else s.pop()
                    result = self.call(owner, name, args, receiver, pc)
                    if descriptor.split(")", 1)[1] != "V": s.append(result)
                elif op == "return":
                    assert not s, s
                    break
                else: raise ValueError("Unknown opcode " + op)
            except Exception as error:
                raise ValueError(f"At bytecode {pc} ({op} {comment}): {error}") from error
        return self.actual, self.fake


class ArcaneInterpreter(Interpreter):
    def call(self, owner, name, args, receiver, pc):
        if owner in ("thaumcraft/api/crafting/ShapedArcaneRecipe", "thaumcraft/api/crafting/ShapelessArcaneRecipe"):
            assert name == '"<init>"'
            group, research, vis, crystals, output, inputs = args
            receiver.update(constructor_offset=pc, research=research, vis=vis,
                            crystals=crystals["values"], output=output if output["kind"] == "stack" else stack(output))
            if owner.endswith("ShapedArcaneRecipe"):
                pattern = []
                while isinstance(inputs[len(pattern)], str): pattern.append(inputs[len(pattern)])
                pairs = inputs[len(pattern):]
                assert len(pairs) % 2 == 0
                receiver["pattern"] = pattern
                receiver["key"] = {pairs[index]["value"]: ingredient(pairs[index + 1]) for index in range(0, len(pairs), 2)}
            else:
                receiver["ingredients"] = list(map(ingredient, inputs))
            return None
        if owner == "java/lang/Character" and name == "valueOf":
            return {"kind": "character", "value": chr(args[0])}
        if owner == "thaumcraft/api/ThaumcraftApi" and name == "addArcaneCraftingRecipe":
            row = copy.deepcopy(args[1])
            row.update(id=args[0]["value"], registration_offset=pc)
            self.actual.append(row)
            return None
        return super().call(owner, name, args, receiver, pc)


def acquisition_rows(source):
    """Five requested acquisition registrations, not an invented infusion tier."""
    text = source.read_text().split("  public static void initializeArcaneRecipes(", 1)[1].split(
        "  public static void initializeInfusionRecipes();", 1)[0]
    code = []
    for line in text.splitlines():
        match = re.match(r"\s+(\d+): (\w+)\s*(.*)", line)
        if match:
            pc, op, rest = match.groups()
            operand, _, comment = rest.partition("// ")
            code.append((int(pc), op, operand.strip(), comment.strip()))
    rows = []
    for first, last in ((2174, 2253), (4445, 4584), (4587, 4740), (9689, 9890), (9893, 9959)):
        segment = [line for line in code if first <= line[0] <= last]
        segment.append((last + 1, "return", "", ""))
        actual, fake = ArcaneInterpreter(segment).run()
        assert len(actual) == 1 and not fake
        row = actual[0]
        result = {"id": row["id"], "recipe_class": row["class"],
                  "source": {"method": "initializeArcaneRecipes", "start_offset": first,
                             "constructor_offset": row["constructor_offset"], "registration_offset": last}}
        result.update({key: clean(value) for key, value in row.items()
                       if key not in ("class", "id", "constructor_offset", "registration_offset")})
        rows.append(result)
    by_id = {row["id"]: row for row in rows}
    motion = by_id["thaumcraft:MatrixMotion"]
    cost = by_id["thaumcraft:MatrixCost"]
    assert motion["vis"] == cost["vis"] == 500
    assert motion["pattern"] == ["SNS", "NGN", "SNS"]
    assert cost["pattern"] == ["SAS", "AGA", "SAS"]
    assert motion["key"]["G"]["item"]["legacy_field"] == cost["key"]["G"]["item"]["legacy_field"] == "net/minecraft/init/Blocks.field_150484_ah"
    assert by_id["thaumcraft:RedstoneInlay"]["output"]["count"] == 2
    assert by_id["thaumcraft:Stabilizer"]["vis"] == 250
    assert by_id["thaumcraft:Stabilizer"]["pattern"] == ["SRS", "BVB", "IMI"]
    assert by_id["thaumcraft:mirrorglass"]["vis"] == 50
    return {"schema": "thaumcraft:beta26_infusion_acquisition_source/v1", "recipe_count": 5,
            "legacy_mappings": {"net/minecraft/init/Blocks.field_150484_ah": "minecraft:diamond_block",
                                "thaumcraft/api/blocks/BlocksTC.matrixSpeed": "thaumcraft:matrix_speed",
                                "thaumcraft/api/blocks/BlocksTC.matrixCost": "thaumcraft:matrix_cost",
                                "thaumcraft/api/blocks/BlocksTC.stabilizer": "thaumcraft:stabilizer",
                                "thaumcraft/api/blocks/BlocksTC.inlay": "thaumcraft:inlay",
                                "thaumcraft/api/blocks/BlocksTC.slabArcaneStone": "thaumcraft:slab_arcane_stone"},
            "source_mapping_evidence": {"net/minecraft/init/Blocks.field_150484_ah": "MCP stable39 fields.csv: DIAMOND_BLOCK",
                                        "thaumcraft/api/blocks/BlocksTC.slabArcaneStone": "ConfigBlocks.initBlocks@488..509",
                                        "thaumcraft/api/blocks/BlocksTC.matrixSpeed": "ConfigBlocks.initBlocks@1928..1938",
                                        "thaumcraft/api/blocks/BlocksTC.matrixCost": "ConfigBlocks.initBlocks@1945..1955",
                                        "thaumcraft/api/blocks/BlocksTC.inlay": "BlockInlay constructor, ConfigBlocks.initBlocks@2291",
                                        "thaumcraft/api/blocks/BlocksTC.stabilizer": "BlockStabilizer constructor, ConfigBlocks.initBlocks@3070"},
            "recipes": rows}


def clean(value):
    if isinstance(value, dict):
        return {k: clean(v) for k, v in value.items() if k not in ("constructor_offset", "class")}
    if isinstance(value, list): return list(map(clean, value))
    return value


def extract(path):
    method, code = instructions(path.read_text())
    actual, fake = Interpreter(code).run()
    rows = []
    for row in actual:
        value = {"id": row["id"], "recipe_class": row["class"],
                 "source": {"method": METHOD, "constructor_offset": row["constructor_offset"],
                            "registration_offset": row["registration_offset"]}}
        value.update({key: clean(item) for key, item in row.items()
                      if key not in ("class", "constructor_offset", "registration_offset", "id")})
        rows.append(value)
    mappings = json.loads(MAPPINGS.read_text())
    return {"schema": "thaumcraft:beta26_infusion_source/v1", "source_version": "6.1.BETA26",
            "source_method": "thaumcraft.common.config.ConfigRecipes.initializeInfusionRecipes",
            "legacy_mappings": MAPPINGS.name,
            "source_jar_sha256": mappings["source_jar_sha256"],
            "source_bytecode_sha256": hashlib.sha256(method.encode()).hexdigest(),
            "recipe_count": len(rows), "fake_recipe_count": len(fake),
            "fake_recipe_ids": [row["id"] for row in fake],
            "recipes": rows}


def verify_original_jar(path, mappings):
    assert hashlib.sha256(path.read_bytes()).hexdigest() == mappings["source_jar_sha256"], "Wrong original JAR"
    with zipfile.ZipFile(path) as jar:
        for name, expected in mappings["source_class_sha256"].items():
            assert hashlib.sha256(jar.read(name + ".class")).hexdigest() == expected, name
        callers = sorted(name for name in jar.namelist() if name.endswith(".class")
                         and b"addInfusionCraftingRecipe" in jar.read(name))
        assert callers == ["thaumcraft/api/ThaumcraftApi.class", "thaumcraft/common/config/ConfigRecipes.class"], callers
        assets = set(jar.namelist())
        tc_ids = {field["legacy_id"] for field in mappings["fields"].values()
                  if field["legacy_id"] and field["legacy_id"].startswith("thaumcraft:")}
        assert len(tc_ids) == 77
        for item in tc_ids:
            name = item.split(":", 1)[1]
            assert any(candidate in assets for candidate in (
                f"assets/thaumcraft/models/item/{name}.json", f"assets/thaumcraft/blockstates/{name}.json")) or (
                    name == "seal" and "assets/thaumcraft/models/item/seal_blank.json" in assets), item


def runtime_rows(data, mappings):
    tags = mappings["aspect_tags"]
    result = []
    def part(item):
        kind = item["kind"]
        row = {"itemIds": [], "tagIds": [], "crystalAspect": None}
        if kind == "ore":
            if item["alias"] == "nitor": row["tagIds"] = ["thaumcraft:nitor"]
            elif item["alias"] == "gemAmber": row["itemIds"] = ["thaumcraft:amber"]
            elif item["alias"] == "ingotIron": row["tagIds"] = ["c:ingots/iron"]
            else: raise ValueError(item)
        elif kind in ("stack", "stack_nbt", "item_any_metadata"):
            value = item["stack"] if kind == "stack_nbt" else item
            assert not value.get("nbt"), "Runtime conversion must preserve nonempty NBT"
            if "metadata" in value: assert value["metadata"] == 0
            row["itemIds"] = [mappings["fields"][value["item"]["legacy_field"]]["legacy_id"]]
        else: raise ValueError(item)
        return row
    for row in data["recipes"]:
        mode = row["output"]["kind"]
        if row["id"] == "thaumcraft:MirrorEssentia":
            result.append({"legacyId": row["id"], "research": row["research"], "center": part(row["center"]),
                           "components": [part(item) for item in row["sides"]],
                           "outputItemId": mappings["fields"][row["output"]["stack"]["item"]["legacy_field"]]["legacy_id"],
                           "outputCount": row["output"]["stack"]["count"],
                           "aspects": {tags[aspect]: amount for aspect, amount in row["aspects"].items()},
                           "instability": row["instability"]})
            continue
        if mode not in ("copy_center_runic", "copy_center_infusion_enchantment"): continue
        runic = mode == "copy_center_runic"
        center = {"itemIds": [], "tagIds": [], "crystalAspect": None, "any": True}
        output = {"mode": "RUNIC" if runic else "INFUSION_ENCHANTMENT"}
        if not runic: output["enchantmentId"] = row["output"]["enchantment"]
        representative = "minecraft:iron_chestplate" if runic else (
            "minecraft:iron_sword" if row["center"]["tool_classes"] == ["weapon"] else "minecraft:iron_pickaxe")
        result.append({"legacyId": row["id"], "research": row["research"], "center": center,
                       "components": [part(item) for item in row["sides"]],
                       "outputItemId": representative, "outputCount": 1,
                       "aspects": {"praemunio": 40, "vitreus": 20, "potentia": 20} if runic else {
                           tags[aspect]: amount for aspect, amount in row["aspects"].items()},
                       "instability": 5 if runic else row["instability"], "output": output})
    assert len(result) == 10
    return result


def dependency_rows(data, mappings, runtime):
    """An explicit content boundary; this file never registers missing items."""
    source = "\n".join(path.read_text() for path in sorted(
        (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry").glob("*.java")))
    source += "\n" + (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/infusion/InfusionStabilityBlocks.java").read_text()
    registered = {"thaumcraft:" + name for name in re.findall(
        r'(?:ITEMS|BLOCKS)\.register(?:Item|SimpleItem|Block|SimpleBlock)\(\s*"([^"\n]+)"', source)}
    # Explicit loops register these exact original block-item families.
    registered.update("thaumcraft:" + prefix + color for prefix in ("nitor_", "candle_")
                      for color in ["white", "orange", "magenta", "lightblue", "yellow", "lime", "pink", "gray",
                                    "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"])
    dynamic = {row["legacyId"] for row in runtime}
    golem = {"thaumcraft:SealHarvest", "thaumcraft:SealButcher", "thaumcraft:SealBreak", "thaumcraft:MindBiothaumic"}
    rows = []
    def dependencies(value, missing):
        if isinstance(value, dict):
            ref = value.get("legacy_field")
            if ref and ref in mappings["fields"]:
                item = mappings["fields"][ref]["legacy_id"]
                if item and item.startswith("thaumcraft:") and item not in registered: missing.add(item)
            if value.get("kind") == "seal_stack": missing.add("thaumcraft:seal")
            for child in value.values(): dependencies(child, missing)
        elif isinstance(value, list):
            for child in value: dependencies(child, missing)
    for row in data["recipes"]:
        missing = set(); dependencies(row, missing)
        status = "RUNTIME_DATA" if row["id"] in dynamic else "OPTIONAL_GOLEM_MODULE" if row["id"] in golem else (
            "BUILT_IN" if row["id"].startswith("thaumcraft:CrystalCluster") else (
                "EXTERNAL_CONTENT_REQUIRED" if missing else "RUNTIME_CONVERSION_REQUIRED"))
        rows.append({"legacyId": row["id"], "status": status, "missing_registered_content": sorted(missing)})
    counts = dict(collections.Counter(row["status"] for row in rows))
    assert sum(counts.values()) == 56 and counts["RUNTIME_DATA"] == 10 and counts["BUILT_IN"] == 7
    for row in runtime:
        assert row["outputItemId"].startswith("minecraft:") or row["outputItemId"] in registered
        for part in row["components"]:
            assert all(item.startswith("minecraft:") or item in registered for item in part["itemIds"])
            for tag in part["tagIds"]:
                if tag == "c:ingots/iron": continue # NeoForge common tag, tested against real iron ingot in-game.
                namespace, name = tag.split(":", 1)
                tag_data = json.loads((ROOT / f"src/main/resources/data/{namespace}/tags/item/{name}.json").read_text())
                assert len(tag_data["values"]) == 16 and set(tag_data["values"]) <= registered
    return {"schema": "thaumcraft:beta26_infusion_dependencies/v1", "source_recipe_count": 56,
            "scope": "Infusion branch content registrations; optional golem module is a separate module",
            "counts": counts,
            "dynamic_limits": ["Vanilla armor/tools supported; Baubles requires its real external equipment subsystem",
                               "Source ore aliases use the actual built-in amber and the registered 16-color nitor item tag"],
            "recipes": rows}


def golden_checks(data):
    """Independent hand-audited cases spanning every source representation."""
    rows = {row["id"]: row for row in data["recipes"]}
    assert len(rows) == data["recipe_count"] == 56
    assert data["fake_recipe_count"] == 11
    assert collections.Counter(row["recipe_class"] for row in rows.values()) == {BASE: 47, ENCHANT: 8, RUNIC: 1}
    assert not any("FAKE" in key.upper() for key in rows)
    crystal = rows["thaumcraft:CrystalClusterAir"]
    assert crystal["research"] == "CRYSTALFARMER" and crystal["instability"] == 0
    assert crystal["aspects"] == {"AIR": 10, "CRYSTAL": 10, "TRAP": 5}
    assert [side["item"]["legacy_field"] for side in crystal["sides"]] == [
        "net/minecraft/init/Items.field_151014_N", "thaumcraft/api/items/ItemsTC.salisMundus"]
    goggles = rows["thaumcraft:HelmGoggles"]
    assert goggles["output"] == {"kind": "copy_center_set_nbt", "key": "goggles",
                                 "value": {"kind": "nbt", "type": "byte", "value": 1}}
    assert goggles["center"]["metadata"] == 32767
    assert rows["thaumcraft:MaskGrinningDevil"]["output"]["value"]["value"] == 0
    assert rows["thaumcraft:MaskAngryGhost"]["output"]["value"]["value"] == 1
    assert rows["thaumcraft:MaskSippingFiend"]["output"]["value"]["value"] == 2
    assert rows["thaumcraft:IEESSENCE"]["output"]["max_level"] == 5
    assert rows["thaumcraft:RunicArmor"]["output"]["key"] == "TC.RUNIC"
    collapser = rows["thaumcraft:CausalityCollapser"]
    assert collapser["instability"] == 8 and collapser["aspects"] == {"ELDRITCH": 50, "FLUX": 50}
    assert len(collapser["sides"][3]["alternatives"]) == 16
    assert collapser["sides"][3] == collapser["sides"][7]
    pearl = rows["thaumcraft:VoidseerPearl"]
    assert pearl["center"]["metadata"] == 4
    assert pearl["sides"][3]["kind"] == "item_any_metadata"
    assert rows["thaumcraft:SealHarvest"]["output"]["stack"]["seal"] == "thaumcraft:harvest"
    assert rows["thaumcraft:VerdantHeartLife"]["sides"][2]["nbt"]["Potion"]["value"] == "minecraft:strong_healing"
    assert rows["thaumcraft:VerdantHeartSustain"]["sides"][2]["nbt"]["Potion"]["value"] == "minecraft:strong_regeneration"
    assert rows["thaumcraft:ElementalAxe"]["output"]["stack"]["infusion_enchantments"] == [
        {"enchantment": "COLLECTOR", "level": 1}, {"enchantment": "BURROWING", "level": 1}]
    assert rows["thaumcraft:VisAmulet"]["output"]["stack"]["metadata"] == 1
    assert len(rows["thaumcraft:VisAmulet"]["sides"]) == 6


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=pathlib.Path, default=pathlib.Path("/workspace/reference/javap/ConfigRecipes.txt"))
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--jar", type=pathlib.Path, default=pathlib.Path("/workspace/reference/Thaumcraft-1.12.2-6.1.BETA26.jar"))
    parser.add_argument("--javap", type=pathlib.Path, help="Also reproduce and compare infusion instructions directly from the original JAR")
    parser.add_argument("--write", action="store_true", help="Write extracted source facts, otherwise compare")
    args = parser.parse_args()
    data = extract(args.source)
    golden_checks(data)
    mappings = json.loads(MAPPINGS.read_text())
    original_fields = set(re.findall(
        r'// Field ((?:thaumcraft/api/(?:items/ItemsTC|blocks/BlocksTC)|net/minecraft/init/(?:Items|Blocks))\.[^:]+):',
        instructions(args.source.read_text())[0]))
    assert original_fields == set(mappings["fields"]) and mappings["field_count"] == 128
    assert len(mappings["aspect_tags"]) == 37
    verify_original_jar(args.jar, mappings)
    if args.javap:
        fresh = subprocess.check_output([str(args.javap), "-c", "-p", "-classpath", str(args.jar),
                                         "thaumcraft.common.config.ConfigRecipes"], text=True)
        assert instructions(fresh)[1] == instructions(args.source.read_text())[1], "Disassembly differs from original JAR"
    runtime = runtime_rows(data, mappings)
    dependencies = dependency_rows(data, mappings, runtime)
    acquisition = acquisition_rows(args.source)
    if args.write:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        for path, value in ((args.output, data), (RUNTIME, runtime), (DEPENDENCIES, dependencies), (ACQUISITION, acquisition)):
            path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    else:
        assert json.loads(args.output.read_text()) == data, "Catalog differs from original bytecode"
        assert json.loads(RUNTIME.read_text()) == runtime, "Runtime rows differ from verified original cases"
        assert json.loads(DEPENDENCIES.read_text()) == dependencies, "Dependencies differ from current registrations"
        assert json.loads(ACQUISITION.read_text()) == acquisition, "Acquisition source differs from original bytecode"
    print(f"BETA26 infusion catalog verified: {data['recipe_count']} actual recipes, {data['fake_recipe_count']} display-only examples; golden cases passed")


if __name__ == "__main__":
    main()
