"""Blueprints: house, bakery, well (Phase 0), tavern and market stall (Phase 1).

Builds each structure with Structure Lab's builder (D:/MinecraftMods/MinecraftStructureInjector) and writes:
  - neoforge/src/main/resources/data/villagersimulator/structure/<name>.nbt          (the blueprint)
  - sim-content/src/main/resources/data/villagersimulator/villagersimulator/building_types/<name>.json
    (the building type, with points taken from the same script so they always match the blocks)

Run with Structure Lab's venv:
  D:/MinecraftMods/MinecraftStructureInjector/.venv/Scripts/python.exe tools/blueprints/blueprints.py

Conventions (docs/ARCHITECTURE.md §13.1): y=0 is the floor layer, placed so it replaces the top ground block; people
stand at y=1. Points are block positions relative to the blueprint origin (min corner).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAB = Path("D:/MinecraftMods/MinecraftStructureInjector/python")
sys.path.insert(0, str(LAB))
from structurelab.builder import Structure  # noqa: E402

STRUCTURES = ROOT / "neoforge/src/main/resources/data/villagersimulator/structure"
TYPES = ROOT / "sim-content/src/main/resources/data/villagersimulator/villagersimulator/building_types"
ANCHOR = "villagersimulator:building_anchor"


def door(s: Structure, x: int, y: int, z: int, facing: str) -> None:
    """An open oak door (villagers path through it without opening doors)."""
    for half, dy in (("lower", 0), ("upper", 1)):
        s.set((x, y + dy, z), "oak_door", {"half": half, "facing": facing, "hinge": "left", "open": True, "powered": False})


def bed(s: Structure, x: int, y: int, z_head: int) -> list[int]:
    """A bed with its head against the north wall; returns the sleeping point (the head block)."""
    s.set((x, y, z_head), "red_bed", {"part": "head", "facing": "north", "occupied": False})
    s.set((x, y, z_head + 1), "red_bed", {"part": "foot", "facing": "north", "occupied": False})
    return [x, y, z_head]


def walls(s: Structure, sx: int, sz: int, y0: int, y1: int, wall: str, corner: str) -> None:
    for y in range(y0, y1 + 1):
        for x in range(sx):
            for z in range(sz):
                edge_x = x in (0, sx - 1)
                edge_z = z in (0, sz - 1)
                if edge_x and edge_z:
                    s.set((x, y, z), corner, {"axis": "y"})
                elif edge_x or edge_z:
                    s.set((x, y, z), wall)
                else:
                    s.set((x, y, z), "air")


def roof(s: Structure, sx: int, sz: int, y: int, block: str, slab: str) -> None:
    s.fill((0, y, 0), (sx - 1, y, sz - 1), block)
    s.fill((1, y + 1, 1), (sx - 2, y + 1, sz - 2), slab, {"type": "bottom", "waterlogged": False})


def house() -> tuple[Structure, dict]:
    sx, sy, sz = 9, 6, 7
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "cobblestone")
    s.fill((1, 0, 1), (sx - 2, 0, sz - 2), "spruce_planks")
    walls(s, sx, sz, 1, 3, "oak_planks", "oak_log")
    roof(s, sx, sz, 4, "spruce_planks", "spruce_slab")
    door(s, 4, 1, sz - 1, "north")
    for z in (3,):
        s.set((0, 2, z), "glass_pane")
        s.set((sx - 1, 2, z), "glass_pane")
    for x in (2, 6):
        s.set((x, 2, 0), "glass_pane")
    beds = [bed(s, x, 1, 1) for x in (1, 3, 5, 7)]
    s.set((4, 3, 3), "lantern", {"hanging": True, "waterlogged": False})
    s.set((7, 1, 5), "barrel", {"facing": "up", "open": False})
    # A table with two chairs by the door.
    s.set((4, 1, 4), "oak_fence", {"north": False, "south": False, "east": False, "west": False, "waterlogged": False})
    s.set((4, 2, 4), "oak_pressure_plate", {"powered": False})
    anchor = [1, 0, 5]
    s.set(tuple(anchor), ANCHOR)
    return s, {
        "size": [sx, sy, sz],
        "points": {"bed": beds, "service": [[3, 1, 4], [5, 1, 4]], "wander": [[4, 1, sz]], "anchor": [anchor]},
        "services": ["home"],
        "advertisements": [
            {"id": "rest", "activity": "villagersimulator:rest", "point": "service", "duration": "1h",
             "needs": {"comfort": 40, "energy": 5}, "condition": "is_home()"},
            {"id": "nap", "activity": "villagersimulator:nap", "point": "bed", "duration": "1h",
             "needs": {"energy": 35, "comfort": 10}, "condition": "is_home() && need('energy') < 45"},
        ],
    }


def bakery() -> tuple[Structure, dict]:
    sx, sy, sz = 9, 6, 9
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "stone_bricks")
    s.fill((1, 0, 1), (sx - 2, 0, sz - 2), "polished_andesite")
    walls(s, sx, sz, 1, 3, "bricks", "spruce_log")
    roof(s, sx, sz, 4, "spruce_planks", "spruce_slab")
    door(s, 4, 1, sz - 1, "north")
    for z in (3, 6):
        s.set((0, 2, z), "glass_pane")
        s.set((sx - 1, 2, z), "glass_pane")
    # Kitchen along the north wall: smokers with a work spot in front of each.
    work = []
    for x in (2, 4, 6):
        s.set((x, 1, 1), "smoker", {"facing": "south", "lit": False})
        work.append([x, 1, 2])
    # Counter with a gap in the middle, then the dining side.
    for x in (1, 2, 3, 5, 6, 7):
        s.set((x, 1, 4), "smooth_stone_slab", {"type": "top", "waterlogged": False})
    s.set((7, 1, 7), "barrel", {"facing": "up", "open": False})
    s.set((4, 3, 3), "lantern", {"hanging": True, "waterlogged": False})
    s.set((4, 3, 6), "lantern", {"hanging": True, "waterlogged": False})
    anchor = [1, 0, 7]
    s.set(tuple(anchor), ANCHOR)
    service = [[2, 1, 6], [6, 1, 6], [3, 1, 7], [5, 1, 7], [2, 1, 5], [6, 1, 5]]
    return s, {
        "size": [sx, sy, sz],
        "points": {"work": work, "service": service, "wander": [[4, 1, sz]], "anchor": [anchor]},
        "job": {"id": "villagersimulator:baker", "slots": 3, "activity": "villagersimulator:bake"},
        "services": ["eat"],
        "initial_stock": {"bread": 12},
        "advertisements": [
            {"id": "eat", "activity": "villagersimulator:eat", "point": "service", "duration": "30m",
             "needs": {"hunger": 55, "comfort": 5}, "consumes": {"bread": 1},
             "condition": "hour() >= 6 && hour() < 22"},
        ],
    }


def well() -> tuple[Structure, dict]:
    sx, sy, sz = 5, 4, 5
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "cobblestone")
    s.set((2, 0, 2), "water", {"level": 0})
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            if (x, z) == (2, 2):
                s.set((x, 1, z), "water", {"level": 0})
            else:
                s.set((x, 1, z), "cobblestone_wall", {"up": True, "north": "none", "south": "none", "east": "none",
                                                      "west": "none", "waterlogged": False})
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):
        s.set((x, 2, z), "oak_fence", {"north": False, "south": False, "east": False, "west": False, "waterlogged": False})
    s.fill((1, 3, 1), (3, 3, 3), "oak_slab", {"type": "bottom", "waterlogged": False})
    anchor = [0, 0, 0]
    s.set(tuple(anchor), ANCHOR)
    wander = [[0, 1, 0], [2, 1, 0], [4, 1, 0], [0, 1, 2], [4, 1, 2], [0, 1, 4], [2, 1, 4], [4, 1, 4],
              [-2, 1, 2], [6, 1, 2], [2, 1, -2], [2, 1, 6]]
    return s, {
        "size": [sx, sy, sz],
        "points": {"wander": wander, "anchor": [anchor]},
        "services": ["gather"],
        "advertisements": [
            {"id": "gather", "activity": "villagersimulator:socialize", "point": "wander", "duration": "1h",
             "needs": {"social": 20, "fun": 5}},
            {"id": "wash", "activity": "villagersimulator:wash", "point": "wander", "duration": "20m",
             "needs": {"hygiene": 60},
             "effects": [{"type": "add_modifier", "stat": "hygiene_decay", "mult": -0.5, "duration": "6h",
                          "source": "villagersimulator:washed"}]},
        ],
    }


def tavern() -> tuple[Structure, dict]:
    sx, sy, sz = 11, 6, 9
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "cobblestone")
    s.fill((1, 0, 1), (sx - 2, 0, sz - 2), "dark_oak_planks")
    walls(s, sx, sz, 1, 3, "spruce_planks", "dark_oak_log")
    roof(s, sx, sz, 4, "dark_oak_planks", "dark_oak_slab")
    door(s, 5, 1, sz - 1, "north")
    for z in (3, 6):
        s.set((0, 2, z), "glass_pane")
        s.set((sx - 1, 2, z), "glass_pane")
    # Bar along the north wall with barrels behind it.
    for x in range(2, 9):
        s.set((x, 1, 2), "spruce_slab", {"type": "top", "waterlogged": False})
    for x in (2, 4, 6, 8):
        s.set((x, 1, 1), "barrel", {"facing": "south", "open": False})
    # Two tables.
    for x in (3, 7):
        s.set((x, 1, 5), "oak_fence", {"north": False, "south": False, "east": False, "west": False, "waterlogged": False})
        s.set((x, 2, 5), "oak_pressure_plate", {"powered": False})
    for x in (3, 5, 7):
        s.set((x, 3, 4), "lantern", {"hanging": True, "waterlogged": False})
    anchor = [9, 0, 7]
    s.set(tuple(anchor), ANCHOR)
    service = [[2, 1, 3], [4, 1, 3], [6, 1, 3], [8, 1, 3], [2, 1, 5], [4, 1, 5], [6, 1, 5], [8, 1, 5],
               [3, 1, 6], [7, 1, 6]]
    return s, {
        "size": [sx, sy, sz],
        "points": {"service": service, "wander": [[5, 1, sz]], "anchor": [anchor]},
        "services": ["drink"],
        "advertisements": [
            {"id": "drink", "activity": "villagersimulator:drink", "point": "service", "duration": "1h",
             "needs": {"social": 35, "fun": 30, "hunger": 5}, "condition": "hour() >= 11 && hour() < 24"},
        ],
    }


def market_stall() -> tuple[Structure, dict]:
    sx, sy, sz = 5, 4, 3
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "gravel")
    for x in (0, sx - 1):
        for z in (0, sz - 1):
            s.set((x, 1, z), "oak_fence", {"north": False, "south": False, "east": False, "west": False, "waterlogged": False})
            s.set((x, 2, z), "oak_fence", {"north": False, "south": False, "east": False, "west": False, "waterlogged": False})
    for x in range(sx):
        for z in range(sz):
            s.set((x, 3, z), "white_wool" if (x + z) % 2 == 0 else "red_wool")
    for x in (1, 2, 3):
        s.set((x, 1, 0), "barrel", {"facing": "up", "open": False})
    anchor = [2, 0, 2]
    s.set(tuple(anchor), ANCHOR)
    return s, {
        "size": [sx, sy, sz],
        "points": {"service": [[1, 1, 3], [2, 1, 3], [3, 1, 3]], "wander": [[2, 1, 4]], "anchor": [anchor]},
        "services": ["browse"],
        "advertisements": [
            {"id": "browse", "activity": "villagersimulator:browse", "point": "service", "duration": "45m",
             "needs": {"fun": 20, "social": 10}, "condition": "hour() >= 8 && hour() < 18"},
        ],
    }


def main() -> None:
    STRUCTURES.mkdir(parents=True, exist_ok=True)
    TYPES.mkdir(parents=True, exist_ok=True)
    for name, build in (("house", house), ("bakery", bakery), ("well", well), ("tavern", tavern),
                        ("market_stall", market_stall)):
        structure, meta = build()
        out = structure.save(STRUCTURES / f"{name}.nbt")
        Path(out["views_path"]).unlink(missing_ok=True)  # no camera views for game blueprints
        definition = {"blueprint": f"villagersimulator:{name}", **meta}
        (TYPES / f"{name}.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {len(structure.blocks)} blocks, {len(meta['points'].get('bed', []))} beds")


if __name__ == "__main__":
    main()
