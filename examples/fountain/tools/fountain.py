"""The fountain blueprint and building type for the reference addon (docs/ROADMAP.md Phase 7).

Same conventions as tools/blueprints/blueprints.py. Run with Structure Lab's venv:
  D:/MinecraftMods/MinecraftStructureInjector/.venv/Scripts/python.exe examples/fountain/tools/fountain.py

The water cauldron on the south rim becomes the fountain's `wish` point through the addon's point-block
registration (RegisterPointBlocksEvent), so the building type doesn't list it.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path("D:/MinecraftMods/MinecraftStructureInjector/python")))
from structurelab.builder import Structure  # noqa: E402

RES = HERE / "src/main/resources/data/villagersimulator_fountain"
WALL = {"up": True, "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": False}


def fountain() -> tuple[Structure, dict]:
    sx, sy, sz = 7, 3, 7
    s = Structure((sx, sy, sz))
    s.fill((0, 0, 0), (sx - 1, 0, sz - 1), "stone_bricks")
    s.fill((2, 0, 2), (4, 0, 4), "water", {"level": 0})
    for x in range(1, 6):
        for z in range(1, 6):
            if x in (1, 5) or z in (1, 5):
                s.set((x, 1, z), "stone_brick_wall", WALL)
            else:
                s.set((x, 1, z), "water", {"level": 0})
    s.set((3, 1, 5), "water_cauldron", {"level": 3})
    s.set((3, 1, 3), "stone_brick_wall", WALL)
    s.set((3, 2, 3), "sea_lantern")
    anchor = [0, 0, 0]
    s.set(tuple(anchor), "villagersimulator:building_anchor")
    wander = [[0, 1, 0], [3, 1, 0], [6, 1, 0], [0, 1, 3], [6, 1, 3], [0, 1, 6], [6, 1, 6]]
    return s, {
        "size": [sx, sy, sz],
        "points": {"wander": wander, "anchor": [anchor]},
        "services": ["gather"],
        "layout": {"per_villagers": 16, "district": "market"},
        "advertisements": [
            {"id": "wish", "activity": "villagersimulator_fountain:make_wish", "point": "wish", "duration": "30m",
             "needs": {"fun": 30, "social": 5}, "score": "wishes() < 3 ? 15 : 0"},
            {"id": "gather", "activity": "villagersimulator:socialize", "point": "wander", "duration": "1h",
             "needs": {"social": 20, "fun": 10}},
        ],
    }


def main() -> None:
    (RES / "structure").mkdir(parents=True, exist_ok=True)
    (RES / "villagersimulator/building_types").mkdir(parents=True, exist_ok=True)
    structure, meta = fountain()
    out = structure.save(RES / "structure/fountain.nbt")
    Path(out["views_path"]).unlink(missing_ok=True)
    definition = {"blueprint": "villagersimulator_fountain:fountain", **meta}
    (RES / "villagersimulator/building_types/fountain.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
    print(f"fountain: {len(structure.blocks)} blocks")


if __name__ == "__main__":
    main()
