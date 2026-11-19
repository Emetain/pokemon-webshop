#!/usr/bin/env python3
"""
Tests the card scanner on a folder of real photos (phase 11) and reports the hit rate.

Name every photo after the card it shows, with two underscores between game, name and number:

    pokemon__Mega Rayquaza ex__138.jpg
    magic__Sheoldred, the Apocalypse__107.jpg
    one-piece__Roronoa Zoro__OP01-001 (sleeve).jpg

Anything in brackets is a note for yourself (light, sleeve, angle) and is ignored. Games: pokemon, lorcana, magic,
one-piece, riftbound. Run it against a backend with an admin account, which scans without a limit:

    python3 tools/scan-test.py --base http://localhost:8080 --email you@example.com photos/

The password is asked for. Each scan costs about $0.0001 at OpenAI and counts towards OPENAI_SCAN_MONTHLY_LIMIT_USD.
Only the Python standard library is used.
"""
import argparse
import getpass
import http.cookiejar
import json
import mimetypes
import os
import re
import ssl
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def normalize(text):
    text = unicodedata.normalize("NFD", text or "")
    text = "".join(ch for ch in text if not unicodedata.combining(ch)).lower()
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def number_key(text):
    return re.sub(r"\d+", lambda m: str(int(m.group(0))), (text or "").strip().upper())


def expected_from(path):
    stem = re.sub(r"\s*\([^)]*\)", "", path.stem)
    parts = stem.split("__")
    if len(parts) != 3:
        return None
    game, name, number = parts
    return {"game": game.strip(), "name": name.strip(), "number": number.strip()}


def same_name(found, expected):
    # The catalog adds labels such as "(Alternate Art)" that aren't printed on the card.
    found, expected = normalize(found), normalize(expected)
    return bool(found) and (found == expected or found.startswith(expected + " ") or expected.startswith(found + " "))


def same_card(card, expected):
    return same_name(card["name"], expected["name"]) and number_key(card["number"]) == number_key(expected["number"])


def multipart(field, filename, data):
    boundary = uuid.uuid4().hex
    content_type = mimetypes.guess_type(filename)[0] or "image/jpeg"
    body = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--email", required=True)
    parser.add_argument("--game-hint", action="store_true", help="send the game from the file name along")
    args = parser.parse_args()

    context = ssl.create_default_context(cafile="/etc/ssl/cert.pem") if Path("/etc/ssl/cert.pem").exists() else None
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), urllib.request.HTTPSHandler(context=context))
    api = args.base.rstrip("/") + "/api/v1"

    password = os.environ.get("SCAN_TEST_PASSWORD") or getpass.getpass("Wachtwoord: ")
    login = json.dumps({"email": args.email, "password": password}).encode()
    opener.open(urllib.request.Request(f"{api}/auth/login", login, {"Content-Type": "application/json"}))

    photos = sorted(p for p in args.folder.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"))
    rows, sure, listed, read_ok = [], 0, 0, 0
    for photo in photos:
        expected = expected_from(photo)
        if not expected:
            print(f"skip {photo.name}: name it game__name__number.jpg")
            continue
        body, content_type = multipart("photo", photo.name, photo.read_bytes())
        query = f"?game={expected['game']}" if args.game_hint else ""
        started = time.monotonic()
        try:
            response = opener.open(urllib.request.Request(f"{api}/scanner{query}", body, {"Content-Type": content_type}))
            result = json.load(response)
        except urllib.error.HTTPError as error:
            print(f"FOUT  {photo.name}: {error.code} {error.read().decode(errors='replace')[:200]}")
            continue
        seconds = time.monotonic() - started
        match = result.get("match")
        candidates = result.get("candidates", [])
        reading = result.get("reading", {})
        is_sure = bool(match) and same_card(match, expected)
        is_listed = any(same_card(card, expected) for card in candidates)
        is_read = same_name(reading.get("name"), expected["name"]) and number_key(reading.get("number")) == number_key(expected["number"])
        sure += is_sure
        listed += is_listed
        read_ok += is_read
        verdict = "RAAK" if is_sure else "LIJST" if is_listed else "MIS"
        print(f"{verdict:5} {photo.name}  gelezen: {reading.get('name')} / {reading.get('setCode')} / {reading.get('number')}  ({seconds:.1f}s)")
        rows.append((photo.name, verdict))

    total = len(rows)
    if total:
        print()
        print(f"{total} foto's")
        print(f"Meteen de goede kaart: {sure} ({sure / total:.0%})")
        print(f"Goede kaart in de lijst: {listed} ({listed / total:.0%})")
        print(f"Naam en nummer goed gelezen: {read_ok} ({read_ok / total:.0%})")
        print(f"Kosten bij OpenAI: ongeveer ${total * 0.00013:.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
