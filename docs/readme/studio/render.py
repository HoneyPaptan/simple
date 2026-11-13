import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

import components as c

OUT = c.R / "parts"
CHROME = os.environ.get("CHROME") or next(Path.home().glob(".cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell"), None)
SKIP = {"r-hero", "r-cards", "r-screens", "r-install-copy", "r-nav", "r-side"}

PILLS = {
    "pill-install": ("download", "Install", ""),
    "pill-how": ("layers", "How it works", ""),
    "pill-security": ("shield", "Security", ""),
    "pill-issues": ("bug", "Issues", ""),
    "pill-side": ("clock", "Side project", ""),
    "pill-issues-welcome": ("bug", "Issues welcome", "green"),
    "pill-prs-closed": ("pr", "Pull requests closed for now", "amber"),
    "pill-security-private": ("shield", "Security reports stay private", "accent"),
}

SIDE = (f'<div class="side"><img src="{c.uri(c.B / "mascot-sleep.png")}" width="150" height="150" alt="">'
        '<div class="sidebody"><div class="st">A side project</div>'
        '<div class="ss" style="margin-bottom:0">Built by one person in spare time.<br>Replies can take a few days.</div></div></div>')


def page(html, theme, w, h, kind):
    size = f"width:{w}px;height:{h}px" if w else "display:inline-block"
    return (f'<!doctype html><html><head><meta charset="utf-8"><style>{c.CSS}'
            f"html,body{{margin:0;background:transparent}}</style></head><body>"
            f'<div class="cv" data-theme="{theme}" style="{size};height:auto;overflow:visible;background:transparent;border-radius:0">'
            f'<div class="body {kind}" style="{size}">{html}</div></div></body></html>')


def shoot(html, out, w, h, crop=False):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(html)
    subprocess.run([str(CHROME), "--headless", "--hide-scrollbars", f"--window-size={w},{h}",
                    "--force-device-scale-factor=2", "--default-background-color=00000000",
                    "--virtual-time-budget=3000", f"--screenshot={out}", f"file://{f.name}"],
                   check=True, capture_output=True)
    os.unlink(f.name)
    im = Image.open(out)
    if crop:
        box = im.getchannel("A").getbbox()
        im = im.crop((box[0] - 4, box[1] - 4, box[2] + 4, box[3] + 4))
    im.save(out, optimize=True)
    return im.size


def main():
    OUT.mkdir(exist_ok=True)
    only = set(sys.argv[1:])
    jobs = [(it["id"], it["html"], it["w"], it["h"], it["kind"], False) for it in c.ITEMS if it["id"] not in SKIP]
    jobs.append(("r-side", SIDE, c.PAGE_W, 210, "cmp", False))
    jobs += [(k, f'<div style="display:inline-flex;padding:6px">{c.link_pill(*v)}</div>', 0, 0, "raw", True) for k, v in PILLS.items()]
    for id, html, w, h, kind, crop in jobs:
        if only and id not in only:
            continue
        for theme in ("light", "dark"):
            size = shoot(page(html, theme, w, h, kind), OUT / f"{id}-{theme}.png", w or 700, h or 90, crop)
            print(f"{id}-{theme}.png {size[0]}x{size[1]}")


if __name__ == "__main__":
    main()
