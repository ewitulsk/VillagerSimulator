"""Villager model and animations for GeckoLib (docs/ROADMAP.md Phase 4).

Writes:
  neoforge/src/main/resources/assets/villagersimulator/geo/entity/villager.geo.json
  neoforge/src/main/resources/assets/villagersimulator/animations/entity/villager.animation.json

The texture isn't a file: the client paints one per appearance from the villager's genes
(neoforge/.../client/VillagerTextures.java), using the UV layout below. Keep the two in sync.

Run: python tools/models/villager.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villagersimulator"

# name, parent, pivot, cube origin, size, uv, inflate
# Minecraft units: 16 = one block. The model faces north (-z), like Blockbench's Bedrock entities.
BONES = [
    ("root", None, [0, 0, 0], None, None, None, 0),
    ("body", "root", [0, 11, 0], [-4, 11, -3], [8, 11, 6], [16, 20], 0),
    ("robe", "body", [0, 11, 0], [-4, 5, -3], [8, 6, 6], [16, 37], 0.25),
    ("head", "body", [0, 22, 0], [-4, 22, -4], [8, 10, 8], [0, 0], 0),
    ("nose", "head", [0, 24, -4], [-1, 23, -6], [2, 4, 2], [0, 18], 0),
    ("hair", "head", [0, 22, 0], [-4, 28, -4], [8, 4, 8], [32, 0], 0.3),
    ("hair_long", "head", [0, 22, 0], [-4, 20, 4], [8, 8, 1], [32, 50], 0.2),
    ("right_arm", "body", [-6, 21, 0], [-8, 11, -2], [4, 11, 4], [44, 20], 0),
    ("left_arm", "body", [6, 21, 0], [4, 11, -2], [4, 11, 4], [44, 35], 0),
    ("right_leg", "root", [-2, 11, 0], [-4, 0, -2], [4, 11, 4], [0, 24], 0),
    ("left_leg", "root", [2, 11, 0], [0, 0, -2], [4, 11, 4], [0, 39], 0),
]


def geometry() -> dict:
    bones = []
    for name, parent, pivot, origin, size, uv, inflate in BONES:
        bone: dict = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if origin:
            cube = {"origin": origin, "size": size, "uv": uv}
            if inflate:
                cube["inflate"] = inflate
            bone["cubes"] = [cube]
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.villagersimulator.villager",
                "texture_width": 64, "texture_height": 64,
                "visible_bounds_width": 2, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


def keys(frames: dict[float, list[float]]) -> dict:
    return {f"{t:g}": v for t, v in frames.items()}


def animations() -> dict:
    a = {}
    a["animation.villager.idle"] = {"loop": True, "animation_length": 4, "bones": {
        "head": {"rotation": keys({0: [0, 0, 0], 1.5: [2, 6, 0], 3: [-1, -4, 0], 4: [0, 0, 0]})},
        "right_arm": {"rotation": keys({0: [0, 0, 3], 2: [2, 0, 5], 4: [0, 0, 3]})},
        "left_arm": {"rotation": keys({0: [0, 0, -3], 2: [2, 0, -5], 4: [0, 0, -3]})},
        "body": {"position": keys({0: [0, 0, 0], 2: [0, 0.15, 0], 4: [0, 0, 0]})},
    }}
    a["animation.villager.walk"] = {"loop": True, "animation_length": 1, "bones": {
        "right_leg": {"rotation": keys({0: [30, 0, 0], 0.5: [-30, 0, 0], 1: [30, 0, 0]})},
        "left_leg": {"rotation": keys({0: [-30, 0, 0], 0.5: [30, 0, 0], 1: [-30, 0, 0]})},
        "right_arm": {"rotation": keys({0: [-25, 0, 3], 0.5: [25, 0, 3], 1: [-25, 0, 3]})},
        "left_arm": {"rotation": keys({0: [25, 0, -3], 0.5: [-25, 0, -3], 1: [25, 0, -3]})},
        "body": {"position": keys({0: [0, 0, 0], 0.25: [0, 0.4, 0], 0.5: [0, 0, 0], 0.75: [0, 0.4, 0], 1: [0, 0, 0]})},
    }}
    # Hammering / kneading with the right arm.
    a["animation.villager.work"] = {"loop": True, "animation_length": 0.8, "bones": {
        "right_arm": {"rotation": keys({0: [-60, 0, 0], 0.3: [-115, 0, 0], 0.45: [-35, 0, 0], 0.8: [-60, 0, 0]})},
        "left_arm": {"rotation": keys({0: [-30, 10, 0], 0.4: [-35, 10, 0], 0.8: [-30, 10, 0]})},
        "body": {"rotation": keys({0: [4, 0, 0], 0.45: [8, 0, 0], 0.8: [4, 0, 0]})},
        "head": {"rotation": keys({0: [12, 0, 0], 0.8: [12, 0, 0]})},
    }}
    a["animation.villager.eat"] = {"loop": True, "animation_length": 1.2, "bones": {
        "right_arm": {"rotation": keys({0: [-45, -20, 0], 0.5: [-95, -35, 0], 0.8: [-95, -35, 0], 1.2: [-45, -20, 0]})},
        "head": {"rotation": keys({0: [0, 0, 0], 0.6: [-6, 0, 0], 1.2: [0, 0, 0]})},
    }}
    a["animation.villager.talk"] = {"loop": True, "animation_length": 2.4, "bones": {
        "head": {"rotation": keys({0: [0, 0, 0], 0.4: [7, 0, 0], 0.8: [0, 0, 0], 1.4: [-3, 12, 0], 2: [2, -6, 0], 2.4: [0, 0, 0]})},
        "left_arm": {"rotation": keys({0: [0, 0, -3], 0.6: [-45, 0, -18], 1.2: [-30, 10, -10], 1.8: [0, 0, -3], 2.4: [0, 0, -3]})},
        "right_arm": {"rotation": keys({0: [0, 0, 3], 1.5: [0, 0, 3], 1.9: [-25, 0, 12], 2.4: [0, 0, 3]})},
    }}
    # The lying-down pose comes from the entity's sleeping pose; this just keeps everything still.
    a["animation.villager.sleep"] = {"loop": True, "animation_length": 4, "bones": {
        "head": {"rotation": keys({0: [0, 0, 0], 4: [0, 0, 0]})},
        "body": {"position": keys({0: [0, 0, 0], 2: [0, 0.2, 0], 4: [0, 0, 0]})},
    }}
    return {"format_version": "1.8.0", "animations": a}


def main() -> None:
    geo = ASSETS / "geo/entity/villager.geo.json"
    anim = ASSETS / "animations/entity/villager.animation.json"
    geo.parent.mkdir(parents=True, exist_ok=True)
    anim.parent.mkdir(parents=True, exist_ok=True)
    geo.write_text(json.dumps(geometry(), indent=2) + "\n", encoding="utf-8")
    anim.write_text(json.dumps(animations(), indent=2) + "\n", encoding="utf-8")
    print(f"wrote {geo.relative_to(ROOT)} ({len(BONES)} bones) and {anim.relative_to(ROOT)} ({len(animations()['animations'])} animations)")


if __name__ == "__main__":
    main()
