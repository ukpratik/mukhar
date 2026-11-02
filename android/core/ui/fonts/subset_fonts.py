"""Rebuilds res/font/manrope_variable.ttf and res/font/noto_sans_devanagari_variable.ttf (M6, NFR-012).

Usage (fontTools 4.60.1 installed into a throwaway folder, not a project dependency):
    python3 -m pip install --target /tmp/ft fonttools==4.60.1
    python3 -I subset_fonts.py /tmp/ft <dir with the downloaded fonts> <out dir>

Inputs, from the official google/fonts repo (SIL OFL 1.1, no Reserved Font Name, so a subset may keep its name):
    ofl/manrope/Manrope[wght].ttf                         -> saved as Manrope-wght.ttf
    ofl/notosansdevanagari/NotoSansDevanagari[wdth,wght].ttf -> saved as NotoDeva-wdth-wght.ttf

What it does: Manrope keeps its wght axis limited to 400..800 and only Latin, Latin-1, Latin Extended-A and general
punctuation (+ the rupee sign). Noto Sans Devanagari pins wdth = 100, keeps wght 400..700, and keeps Devanagari,
Devanagari Extended, Vedic Extensions, ZWJ/ZWNJ and the same Latin set (so Hinglish shares one font and baseline).
All OpenType layout features are kept (conjuncts, nukta, matras). Hinting is dropped (Android doesn't use it).
"""
import sys

sys.path.insert(0, sys.argv[1])
from fontTools.ttLib import TTFont  # noqa: E402
from fontTools.varLib import instancer  # noqa: E402
from fontTools import subset  # noqa: E402

src, out = sys.argv[2], sys.argv[3]


def sub(font, unicodes, path):
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.hinting = False
    s = subset.Subsetter(opts)
    s.populate(unicodes=unicodes)
    s.subset(font)
    font.save(path)


latin = list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2000, 0x2070)) + [
    0x20B9, 0x2122, 0x2190, 0x2192, 0x2212, 0x25CC, 0x0131, 0x02C6, 0x02DA, 0x02DC]
deva = list(range(0x0900, 0x0980)) + list(range(0xA8E0, 0xA900)) + list(range(0x1CD0, 0x1D00)) + [0x200C, 0x200D]

# Instancing and subsetting in one pass trips over lazily loaded gvar data, so save and reload in between.
m = TTFont(f"{src}/Manrope-wght.ttf")
m = instancer.instantiateVariableFont(m, {"wght": (400, 800)})
m.save(f"{out}/tmp_m.ttf")
sub(TTFont(f"{out}/tmp_m.ttf"), latin, f"{out}/manrope_variable.ttf")

n = TTFont(f"{src}/NotoDeva-wdth-wght.ttf")
n = instancer.instantiateVariableFont(n, {"wdth": 100, "wght": (400, 700)})
n.save(f"{out}/tmp_n.ttf")
sub(TTFont(f"{out}/tmp_n.ttf"), latin + deva, f"{out}/noto_sans_devanagari_variable.ttf")

for p in ["manrope_variable.ttf", "noto_sans_devanagari_variable.ttf"]:
    f = TTFont(f"{out}/{p}")
    cmap = f.getBestCmap()
    print(p, [(a.axisTag, a.minValue, a.defaultValue, a.maxValue) for a in f["fvar"].axes],
          len(f.getGlyphOrder()), "glyphs", "deva" if 0x0915 in cmap else "-", "latin" if 0x41 in cmap else "-")
