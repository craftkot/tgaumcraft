#!/usr/bin/env python3
"""Independent BETA26 ConfigRecipes golden fixtures for the optional M06 resource."""
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/data/thaumcraft/golem/infusion_recipes.json"


def item(name):
    return {"itemIds": [name], "tagIds": [], "crystalAspect": None}


class GolemInfusionRecipesTest(unittest.TestCase):
    def test_original_recipe_costs_inputs_order_and_distinct_metadata_products(self):
        # Source bytecode initializeInfusionRecipes:0..400 and3804..3902.
        actual = json.loads(RESOURCE.read_text(encoding="utf-8"))
        expected = [
            ("thaumcraft:SealHarvest", "SEALHARVEST", "seal_blank", "seal_harvest", 0,
             {"herba": 10, "sensus": 10, "humanus": 10},
             [item("minecraft:" + i) for i in ["wheat_seeds", "pumpkin_seeds", "melon_seeds", "beetroot_seeds", "sugar_cane", "cactus"]]),
            ("thaumcraft:SealButcher", "SEALBUTCHER", "seal_guard", "seal_butcher", 0,
             {"bestia": 10, "sensus": 10, "humanus": 10},
             [{"itemIds": ["minecraft:leather"], "tagIds": ["c:leathers"], "crystalAspect": None},
              {"itemIds": [], "tagIds": ["minecraft:wool"], "crystalAspect": None}]
             + [item("minecraft:" + i) for i in ["rabbit_hide", "porkchop", "mutton", "beef"]]),
            ("thaumcraft:SealBreak", "SEALBREAK", "seal_blank", "seal_breaker", 1,
             {"instrumentum": 10, "perditio": 10, "humanus": 10},
             [item("minecraft:" + i) for i in ["golden_axe", "golden_pickaxe", "golden_shovel"]]),
            ("thaumcraft:MindBiothaumic", "MINDBIOTHAUMIC", "mind", "mind_biothaumic", 4,
             {"cognitio": 50, "machina": 25},
             [item("thaumcraft:brain"), item("thaumcraft:mechanism_complex")]),
        ]
        self.assertEqual(4, len(actual))
        self.assertEqual(4, len({r["legacyId"] for r in actual}))
        for recipe, (rid, research, center, output, instability, costs, sides) in zip(actual, expected):
            with self.subTest(recipe=rid):
                self.assertEqual({"legacyId", "research", "center", "components", "outputItemId", "outputCount", "aspects", "instability"}, set(recipe))
                self.assertEqual(rid, recipe["legacyId"])
                self.assertEqual(research, recipe["research"])
                self.assertEqual(item("thaumcraft:" + center), recipe["center"])
                self.assertEqual("thaumcraft:" + output, recipe["outputItemId"])
                self.assertEqual(1, recipe["outputCount"])
                self.assertEqual(instability, recipe["instability"])
                self.assertEqual(costs, recipe["aspects"])
                self.assertEqual(sides, recipe["components"])


if __name__ == "__main__":
    unittest.main()
