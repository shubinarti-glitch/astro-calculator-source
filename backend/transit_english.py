"""English transit copy; authored translations stay in the private data layer."""
from .editorial_data import text as _editorial_text
import json
from pathlib import Path
from .editorial_data import table as _editorial_table

FIELDS = ("energy", "psychology", "relationships", "realization", "risks", "advice")
ASPECTS = _editorial_table("transit_english.ASPECTS")
TIMING = _editorial_table("transit_english.TIMING")
ROLES = _editorial_table("transit_english.ROLES")


def load_authored(directory=None):
    directory = directory or Path(__file__).resolve().parent.parent / "data" / "transit_en"
    result = {}
    for path in sorted(Path(directory).glob("part*.json")):
        raw = json.loads(path.read_text(encoding="utf-8"))
        for key, value in raw.items():
            if key in result:
                raise ValueError(f"{_editorial_text('transit_english.f1038e134a6b99a5e8de46e15133472957c4b636727d85bbc99578d552c49343')}{key}")
            if not isinstance(value, dict) or any(
                not isinstance(value.get(field), str) or not value[field].strip()
                for field in FIELDS
            ):
                raise ValueError(f"{_editorial_text('transit_english.f15c9309a1e2bd12fc67dbbb4b06f0ebae35affb34aa31bd47cfc258ceda93d6')}{key}")
            result[key] = value
    return result


def phase(orbit, movement):
    if orbit is None:
        return _editorial_text('transit_english.ec8c421982c9b7a1b35a0f3b24df16a488328ac29f41538c0dcfdf2b09a50bc0')
    proximity = _editorial_text('transit_english.89c202c40548f709caf6aca95bcb6b562eeb32551a4158235f7a242173993d3a') if orbit <= 1 else _editorial_text('transit_english.8985d0008fd0bd4c95b27679b86a617a0225ded24ca58e9c0af1efaf059e93a3')
    motion = (movement or "").lower()
    if "расход" in motion or "separ" in motion:
        direction = _editorial_text('transit_english.a7d745cd8b9818e3aba7c72b88d6678461882b11a39bba51b7e56de6542b95b2')
    elif "сход" in motion or "app" in motion:
        direction = _editorial_text('transit_english.0489e6d883c5c2ba3aec76f1f7fadb30194d484e0a4d1de32258000c2bf9c9eb')
    else:
        direction = _editorial_text('transit_english.481c2c3f14920e3577703ca6c2a92bcfc4260a13e1aa83b5caf42ae743d09028')
    return f"{_editorial_text('transit_english.64a7ab6c1ea238d5f76c2d8d79ea3e8031ef0b2d993b211e248ded17efa116da')}{orbit:.2f}{_editorial_text('transit_english.59d32d92822c81d5d79d3590befcaef21677ac91c6b2f9ac6a212889d833430d')}{proximity}. {direction}"


def generic_pair(source, focus):
    return {
        "energy": f"{_editorial_text('transit_english.a5c51ce008ea55920f79c3c3a461b289c9433e7bf37211f43687e6bec7076190')}{source}{_editorial_text('transit_english.7c7bfa266e62a22198acd46cbc59ace4c7853e4b30ceccb90c4d02c6d9b1af7a')}{focus}{_editorial_text('transit_english.07df254e0d8d7ac8fe6b5feb6f258bbdd7624061948131311b6bc9c8ba3322c8')}",
        "psychology": f"{_editorial_text('transit_english.36d2262b8a5f603b16e235a535b3905bb6a45737bfcd29fa4ad964d060948156')}{focus}{_editorial_text('transit_english.a355eccc5577a873ea4e7441aacb01ce2d4b620f01d632c3f80d3d0ca8c27ded')}",
        "relationships": _editorial_text('transit_english.909eb119afb540def82a702ed4a0fac4a7731ede5e91b7ffebc136c55cd5a32d'),
        "realization": _editorial_text('transit_english.65bf8a7e5500556f43bf5227f4c713383f68c177dd68e68904ca5ac1fdfea261'),
        "risks": _editorial_text('transit_english.530ae1090e02ed0906821f401384ef55860ac766a6f2c919e764d91a2c8732fb'),
        "advice": _editorial_text('transit_english.027d014445f6818e045e0baa06c01013a2d87f7a4141da16cdd3629c8bd6fd63'),
    }


def render(pair, moving, aspect, orbit, movement):
    return (
        f"{_editorial_text('transit_english.df01f9f5cec8b75efb8df89a1fe3d13a9a29d77d2b188d4f1f7a2535134823b4')}{ASPECTS[aspect]} {TIMING.get(moving, '')}{_editorial_text('transit_english.9b92c4569214977ac2f4fd5ce465305671b7bc9de34c5d996dd958f1bad1b419')}{pair['energy']} {phase(orbit, movement)}{_editorial_text('transit_english.a28e3089e880d096a1a796ff202973c26889df1f0406da4c5db47725a53440cd')}{pair['psychology']}{_editorial_text('transit_english.cd5a787eaf403422a00e905ca779b72486517f5068a2a6b2d0fc7cb5ad8ac89d')}{pair['relationships']}{_editorial_text('transit_english.55f899853f3f2d41172b7d6168c2b6262fa7db2b3d2287ead8ea9bffff749925')}{pair['realization']}{_editorial_text('transit_english.11502aa477c51d4065f03c067142739c2989890e3d6c30a4afe338588bba5f17')}{pair['risks']}{_editorial_text('transit_english.78a1ccbbb627b562777a14b1ab5cfe76cc1f9bbbf0cc9e31b9601d48022767fd')}{pair['advice']}"
    )
