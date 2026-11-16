#!/usr/bin/env python3
"""
Builds Mukhar's offline dictionary (D-056) from open data:

  * English: Open English WordNet 2025 (CC BY 4.0), https://en-word.net
    https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz
  * Hindi: English Wiktionary's Hindi entries via kaikki.org (CC BY-SA 4.0 / GFDL), https://kaikki.org
    https://kaikki.org/dictionary/Hindi/kaikki.org-dictionary-Hindi.jsonl.gz
  * Word frequency (to keep common English words and rank results): FrequencyWords en_50k and hi_full (MIT),
    Hermit Dave, https://github.com/hermitdave/FrequencyWords

Hindi words for an English entry come from inverting the Hindi entries' English glosses ("खुश: happy" gives
happy → खुश), so they are Wiktionary data too.

Writes android/core/data/src/main/assets/dict_en.jsonl and dict_hi.jsonl, one compact JSON object per line.
Run from the repo root:  python3 tools/dictionary/build_dictionary.py   (downloads into tools/dictionary/.cache)
Standard library only.
"""
import gzip
import json
import os
import re
import sys
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(ROOT, "tools", "dictionary", ".cache")
ASSETS = os.path.join(ROOT, "android", "core", "data", "src", "main", "assets")

SOURCES = {
    "oewn.xml.gz": "https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz",
    "hi.jsonl.gz": "https://kaikki.org/dictionary/Hindi/kaikki.org-dictionary-Hindi.jsonl.gz",
    "en_50k.txt": "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt",
    "hi_full.txt": "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/hi/hi_full.txt",
}

EN_TOP = 40000          # keep English words ranked in the top EN_TOP of the frequency list
EN_SENSES = 4           # senses per English word
SYNONYMS = 4            # synonyms per sense
HI_SENSES = 4           # senses per Hindi word
HI_PER_EN = 4           # Hindi words per English word
HI_FORMS = 40           # inflected forms kept per Hindi word
DEVANAGARI = re.compile(r"[\u0900-\u097F]+")
POS_EN = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
POS_HI = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "pron": "pronoun", "postp": "postposition",
          "conj": "conjunction", "intj": "interjection", "num": "number", "det": "determiner", "phrase": "phrase", "particle": "particle"}
ALIAS = re.compile(r"^(?:nuqtaless|alternative|nukta-less|informal|colloquial)? ?(?:form|spelling|variant)s? of (\S+)", re.I)
SKIP_GLOSS = re.compile(r"^(alternative|obsolete|archaic|misspelling|romanization|abbreviation)", re.I)


def fetch():
    os.makedirs(CACHE, exist_ok=True)
    for name, url in SOURCES.items():
        path = os.path.join(CACHE, name)
        if not os.path.exists(path):
            print("downloading", url, file=sys.stderr)
            urllib.request.urlretrieve(url, path)


def curated_lemmas(lang):
    with open(os.path.join(ASSETS, f"words_{lang}.json"), encoding="utf-8") as f:
        return {w["lemma"] for w in json.load(f)["words"]}


NUKTA = str.maketrans({"\u093c": None})


def ranks(name="en_50k.txt"):
    r = {}
    with open(os.path.join(CACHE, name), encoding="utf-8") as f:
        for i, line in enumerate(f):
            w = unicodedata.normalize("NFC", line.split(" ")[0].strip().lower())
            if w and w not in r:
                r[w] = i + 1
    return r


def clean_gloss(g):
    g = re.sub(r"\([^)]*\)", "", g)  # drop qualifiers: "(formal) outcome" -> "outcome"
    g = re.sub(r"\s+", " ", g).strip(" ;,.")
    return g


# ---- Hindi (Wiktionary) ----

def build_hindi():
    entries = {}
    forms = {}
    rank = ranks("hi_full.txt")
    curated = curated_lemmas("hi")
    for line in gzip.open(os.path.join(CACHE, "hi.jsonl.gz"), "rt", encoding="utf-8"):
        e = json.loads(line)
        word = e.get("word", "").strip()
        pos = POS_HI.get(e.get("pos"))
        if not word or not pos or " " in word and pos != "phrase":
            continue
        senses = e.get("senses", [])
        form_of = [f["word"] for s in senses for f in (s.get("form_of") or []) if f.get("word")]
        if form_of:
            if form_of[0] != word:
                forms.setdefault(word, form_of[0])
            continue
        # Inflected forms in Devanagari (लड़कों → लड़का, खाया → खाना), so a search for one finds the word.
        infl = []
        for f in e.get("forms", []):
            form = f.get("form", "")
            if form and form != word and DEVANAGARI.fullmatch(form) and form not in infl:
                infl.append(form)
        roman = next((f["form"] for f in e.get("forms", []) if "romanization" in f.get("tags", [])), None)
        ipa = next((s["ipa"].strip("/[]") for s in e.get("sounds", []) if s.get("ipa")), None)
        out = []
        for s in senses:
            if s.get("alt_of") or not s.get("glosses"):
                continue
            g = clean_gloss(s["glosses"][-1])
            alias = ALIAS.match(g)
            if alias:
                forms.setdefault(word, alias.group(1).strip(" .,"))
                continue
            if not g or SKIP_GLOSS.match(g):
                continue
            item = {"p": pos, "d": g}
            ex = next((x for x in s.get("examples", []) if x.get("text") and (x.get("english") or x.get("translation"))), None)
            if ex and len(ex["text"]) <= 160:
                item["x"] = ex["text"].strip()
                item["xe"] = (ex.get("english") or ex.get("translation")).strip()
            syn = [y["word"] for y in s.get("synonyms", []) if y.get("word") and y["word"] != word][:SYNONYMS]
            if syn:
                item["syn"] = syn
            out.append(item)
        if not out:
            continue
        cur = entries.setdefault(word, {"w": word})
        if infl:
            cur.setdefault("f", [])
            cur["f"] += [f for f in infl if f not in cur["f"]][:HI_FORMS]
        if roman and "t" not in cur:
            cur["t"] = roman
        if ipa and "ipa" not in cur:
            cur["ipa"] = ipa
        cur.setdefault("s", []).extend(out)
    for w, e in entries.items():
        e["s"] = e["s"][:HI_SENSES]
        # Rank by frequency (with or without nukta); hand-picked words count as common.
        r = min(rank.get(w, 10**6), rank.get(w.translate(NUKTA), 10**6))
        e["r"] = 1 if w in curated else r if r < 10**6 else len(rank) + 1
    return entries, {f: l for f, l in forms.items() if l in entries and f not in entries}


def english_heads(gloss):
    """The English words a Hindi gloss stands for: "to eat; to consume" -> eat, consume."""
    for part in re.split(r"[;,]", gloss):
        p = part.strip().lower()
        p = re.sub(r"^(to|a|an|the) ", "", p)
        if p and re.fullmatch(r"[a-z][a-z'-]*", p):
            yield p


# ---- English (WordNet) ----

def build_english(hindi):
    rank = ranks()
    keep_extra = {w.lower() for w in curated_lemmas("en")}
    root = ET.parse(gzip.open(os.path.join(CACHE, "oewn.xml.gz"))).getroot()
    lex = root.find("Lexicon")
    synsets = {}
    entry_lemma = {}
    for s in lex.iter("Synset"):
        d = s.findtext("Definition") or ""
        ex = [x.text for x in s.findall("Example") if x.text]
        synsets[s.get("id")] = (d.strip(), ex[0].strip().strip('"') if ex else None, s.get("members", "").split())
    for le in lex.iter("LexicalEntry"):
        entry_lemma[le.get("id")] = le.find("Lemma").get("writtenForm")

    out = {}
    for le in lex.iter("LexicalEntry"):
        lemma_el = le.find("Lemma")
        w = lemma_el.get("writtenForm")
        key = w.lower()
        if not re.fullmatch(r"[a-z][a-z'-]*", key) or w != key and key not in keep_extra:
            continue
        if rank.get(key, 10**9) > EN_TOP and key not in keep_extra:
            continue
        pos = POS_EN[lemma_el.get("partOfSpeech")]
        e = out.setdefault(key, {"w": key, "r": rank.get(key, EN_TOP + 1), "s": []})
        ipa = next((p.text for p in lemma_el.findall("Pronunciation") if p.get("variety") == "US"), None) or \
            next((p.text for p in lemma_el.findall("Pronunciation")), None)
        if ipa and "ipa" not in e:
            e["ipa"] = ipa
        fs = [f.get("writtenForm") for f in le.findall("Form")]
        if fs:
            e.setdefault("f", [])
            e["f"] += [f for f in fs if f not in e["f"]]
        for sense in le.findall("Sense"):
            d, x, members = synsets[sense.get("synset")]
            item = {"p": pos, "d": d}
            if x:
                item["x"] = x
            syn = [entry_lemma[m] for m in members if entry_lemma.get(m) and entry_lemma[m].lower() != key]
            syn = [s for s in syn if " " not in s][:SYNONYMS]
            if syn:
                item["syn"] = syn
            e["s"].append(item)

    # Interleave parts of speech (the one with most senses first, e.g. run: verb) so a word that's both noun and verb shows both in its first senses.
    for e in out.values():
        groups = defaultdict(list)
        for s in e["s"]:
            groups[s["p"]].append(s)
        by_pos = dict(sorted(groups.items(), key=lambda kv: -len(kv[1])))
        mixed = []
        while any(by_pos.values()) and len(mixed) < EN_SENSES:
            for p in list(by_pos):
                if by_pos[p] and len(mixed) < EN_SENSES:
                    mixed.append(by_pos[p].pop(0))
        e["s"] = mixed

    # Hindi words for English entries, from the Hindi glosses (a first-sense match first, then the more common word).
    scored = defaultdict(dict)
    for hw, he in hindi.items():
        for si, s in enumerate(he["s"]):
            for gi, en in enumerate(english_heads(s["d"])):
                if en in out:
                    score = (0 if si == 0 else 1, he["r"], gi)
                    if hw not in scored[en] or scored[en][hw] > score:
                        scored[en][hw] = score
    for en, m in scored.items():
        out[en]["hi"] = [w for w, _ in sorted(m.items(), key=lambda kv: (kv[1], len(kv[0])))][:HI_PER_EN]
    return out


def write(path, rows):
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(path, len(rows), os.path.getsize(path) // 1024, "KB", file=sys.stderr)


def main():
    fetch()
    hindi, forms = build_hindi()
    english = build_english(hindi)
    write(os.path.join(ASSETS, "dict_en.jsonl"), sorted(english.values(), key=lambda e: e["w"]))
    hi_rows = sorted(hindi.values(), key=lambda e: e["w"]) + [{"w": f, "of": l} for f, l in sorted(forms.items())]
    write(os.path.join(ASSETS, "dict_hi.jsonl"), hi_rows)


if __name__ == "__main__":
    main()
