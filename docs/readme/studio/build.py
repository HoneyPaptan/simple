import base64
import io
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE.parent
SHOTS = HERE / "shots"
FONT_SANS = ROOT / "app/src/main/res/font/geist.ttf"
FONT_MONO = ROOT / "app/src/main/res/font/geist_mono.ttf"

THEMES = {
    "light": dict(
        bg="#F4F3EF", dots="#E2E0D9", window="#FFFFFF", bar="#F7F6F3", line="#E3E1DA",
        text="#121212", muted="#6F6E69", faint="#A3A19B", pane="#FAFAF8",
        amber="#A86A00", green="#2F7D4F", accent="#5B3FD6", frame="#121212", wire="#B9B6AD",
        ripple="#5B3FD6",
    ),
    "dark": dict(
        bg="#09090A", dots="#1C1C1F", window="#121214", bar="#18181B", line="#26262B",
        text="#F2F2F2", muted="#93939A", faint="#5E5E64", pane="#161619",
        amber="#E6B04A", green="#6CC28E", accent="#9C86FF", frame="#2A2A2E", wire="#3A3A40",
        ripple="#FFFFFF",
    ),
}

W, H = 1760, 1028
WIN = (40, 170, 1060, 830)
SCREEN_W, SCREEN_H = 432, 960
PHONE_X, PHONE_Y = 1250, 20
BEZEL = 14
LOOP = 9


def font(path, size, weight="Regular"):
    f = ImageFont.truetype(str(path), size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def logo(name, size, theme):
    svg = HERE / "icons" / f"{name}.svg"
    png = subprocess.run(
        ["rsvg-convert", "-h", str(size * 2), str(svg)], check=True, capture_output=True
    ).stdout
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    im.thumbnail((size, size), Image.LANCZOS)
    if theme == "dark" and name in ("cursor", "opencode-icon", "openai"):
        r, g, b, a = im.split()
        rgb = ImageChops.invert(Image.merge("RGB", (r, g, b)))
        im = Image.merge("RGBA", (*rgb.split(), a))
    return im


def dot_grid(draw, c):
    for y in range(20, H, 28):
        for x in range(20, W, 28):
            draw.ellipse((x - 1.6, y - 1.6, x + 1.6, y + 1.6), fill=c["dots"])


PANES = [
    dict(logo="claude-code", name="Claude Code", where="acme-web", status=("Working", "muted"),
         lines=["Edit  src/checkout/useStripe.ts", "+ Stripe payment hook, errors inline"]),
    dict(logo="openai", name="Codex", where="payments-api",
         status=("Needs you", "amber"), lines=["Bash  npm test -- --runInBand auth", "Allow this command?"],
         after=dict(status=("Working", "muted"), lines=["Bash  npm test -- --runInBand auth", "Auth suite passed"], tone="green")),
    dict(logo="cursor", name="Cursor", where="mobile-app", status=("Done  12m", "faint"),
         lines=["Refactor settings screen", "3 files changed"]),
]


def pane_box(i):
    x0, y0, x1, y1 = WIN
    top = y0 + 72
    h = 172
    gap = 18
    py = top + i * (h + gap)
    return (x0 + 24, py, x1 - 24, py + h)


def draw_pane(draw, img, box, p, c, theme, state=None):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, 20, fill=c["pane"], outline=c["line"], width=2)
    lg = logo(p["logo"], 44, theme)
    img.alpha_composite(lg, (x0 + 26, y0 + 26 + (44 - lg.height) // 2))
    name_f = font(FONT_SANS, 30, "SemiBold")
    where_f = font(FONT_SANS, 26)
    mono = font(FONT_MONO, 25)
    draw.text((x0 + 88, y0 + 30), p["name"], font=name_f, fill=c["text"])
    nw = draw.textlength(p["name"], font=name_f)
    draw.text((x0 + 88 + nw + 16, y0 + 33), p["where"], font=where_f, fill=c["faint"])
    s = state or p
    label, tone = s["status"]
    sf = font(FONT_SANS, 26, "Medium")
    draw.text((x1 - 28 - draw.textlength(label, font=sf), y0 + 33), label, font=sf, fill=c[tone])
    for j, line in enumerate(s["lines"]):
        col = c["muted"]
        if j == 1 and s is not p and state.get("tone"):
            col = c[state["tone"]]
        elif j == 1 and tone == "amber":
            col = c["amber"]
        draw.text((x0 + 88, y0 + 92 + j * 36), line, font=mono, fill=col, features=["-liga", "-calt"])


def rounded_mask(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def background(theme):
    c = THEMES[theme]
    img = Image.new("RGBA", (W, H), c["bg"])
    d = ImageDraw.Draw(img)
    dot_grid(d, c)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((WIN[0], WIN[1] + 14, WIN[2], WIN[3] + 14), 28, fill=(0, 0, 0, 40 if theme == "light" else 120))
    px0, py0 = PHONE_X - BEZEL, PHONE_Y
    sd.rounded_rectangle((px0, py0 + 14, px0 + SCREEN_W + 2 * BEZEL, py0 + SCREEN_H + 2 * BEZEL + 14), 66, fill=(0, 0, 0, 50 if theme == "light" else 140))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(WIN, 28, fill=c["window"], outline=c["line"], width=2)
    d.rounded_rectangle((WIN[0] + 1, WIN[1] + 1, WIN[2] - 1, WIN[1] + 56), 28, fill=c["bar"])
    d.rectangle((WIN[0] + 1, WIN[1] + 30, WIN[2] - 1, WIN[1] + 56), fill=c["bar"])
    d.line((WIN[0], WIN[1] + 56, WIN[2], WIN[1] + 56), fill=c["line"], width=2)
    for k in range(3):
        cx = WIN[0] + 34 + k * 26
        d.ellipse((cx - 7, WIN[1] + 21, cx + 7, WIN[1] + 35), fill=c["line"])
    tf = font(FONT_SANS, 24, "Medium")
    title = "herdr  ·  studio"
    d.text(((WIN[0] + WIN[2]) / 2 - d.textlength(title, font=tf) / 2, WIN[1] + 14), title, font=tf, fill=c["muted"])
    for i, p in enumerate(PANES):
        draw_pane(d, img, pane_box(i), p, c, theme)
    d.rounded_rectangle(
        (PHONE_X - BEZEL, PHONE_Y, PHONE_X + SCREEN_W + BEZEL, PHONE_Y + SCREEN_H + 2 * BEZEL),
        64, fill=c["frame"],
    )
    lf = font(FONT_MONO, 22)
    label = "pinned TLS"
    mid = (WIN[2] + PHONE_X - BEZEL) / 2
    d.text((mid - d.textlength(label, font=lf) / 2, 520), label, font=lf, fill=c["faint"])
    return img.convert("RGB")


def pane_state(theme):
    c = THEMES[theme]
    box = pane_box(1)
    pad = 6
    img = Image.new("RGBA", (box[2] - box[0] + 2 * pad, box[3] - box[1] + 2 * pad), c["window"])
    d = ImageDraw.Draw(img)
    local = (pad, pad, pad + box[2] - box[0], pad + box[3] - box[1])
    draw_pane(d, img, local, PANES[1], c, theme, state=PANES[1]["after"])
    return img.convert("RGB"), (box[0] - pad, box[1] - pad)


def jpeg_uri(im, q=82):
    buf = io.BytesIO()
    im.convert("RGB").save(buf, "JPEG", quality=q, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def png_uri(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


STATUS_BAR = 86


def shot(theme, name):
    im = Image.open(SHOTS / theme / f"{name}.jpg").convert("RGB")
    if name == "notification":
        return im
    row = [im.getpixel((x, STATUS_BAR + 1)) for x in range(im.width)]
    fill = max(set(row), key=row.count)
    ImageDraw.Draw(im).rectangle((0, 0, im.width, STATUS_BAR), fill=fill)
    return im


def screen(theme, name):
    im = shot(theme, name)
    return im.resize((SCREEN_W, round(im.height * SCREEN_W / im.width)), Image.LANCZOS).crop((0, 0, SCREEN_W, SCREEN_H))


def banner(theme):
    src = SHOTS / theme / "notification.jpg"
    im = Image.open(src).convert("RGBA")
    w = SCREEN_W - 24
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    im.putalpha(rounded_mask(im.size, 26))
    return im


def hero(theme, allow_at):
    c = THEMES[theme]
    bg = background(theme)
    after, after_pos = pane_state(theme)
    today = screen(theme, "today")
    chat = screen(theme, "chat")
    note = banner(theme)
    sx, sy = PHONE_X, PHONE_Y + BEZEL
    bx, by = sx + 12, sy + 54
    ax, ay = bx + allow_at[0] * note.width, by + allow_at[1] * note.height
    path = f"M {WIN[2] + 10} 480 L {PHONE_X - BEZEL - 10} 480"
    css = f"""
.a{{animation-duration:{LOOP}s;animation-iteration-count:infinite;animation-timing-function:cubic-bezier(.3,.7,.2,1)}}
.chat{{opacity:1;animation-name:chat}}
.after{{opacity:1;animation-name:after}}
.note{{opacity:0;animation-name:note}}
.tap{{opacity:0;animation-name:tap;transform-origin:{ax:.0f}px {ay:.0f}px}}
.out{{opacity:0;animation-name:out}}
.back{{opacity:0;animation-name:back}}
@keyframes chat{{0%,55%{{opacity:0}}59%,94%{{opacity:1}}98%,100%{{opacity:0}}}}
@keyframes after{{0%,58%{{opacity:0}}61%,94%{{opacity:1}}98%,100%{{opacity:0}}}}
@keyframes note{{0%,27%{{opacity:0;transform:translateY(-40px)}}31%,50%{{opacity:1;transform:translateY(0)}}54%,100%{{opacity:0;transform:translateY(-40px)}}}}
@keyframes tap{{0%,43%{{opacity:0;transform:scale(.4)}}45%{{opacity:.35;transform:scale(1)}}50%,100%{{opacity:0;transform:scale(1.6)}}}}
@keyframes out{{0%,17%{{opacity:0;transform:translateX(0)}}19%{{opacity:1}}26%{{opacity:1;transform:translateX({PHONE_X - BEZEL - WIN[2] - 20}px)}}28%,100%{{opacity:0;transform:translateX({PHONE_X - BEZEL - WIN[2] - 20}px)}}}}
@keyframes back{{0%,50%{{opacity:0;transform:translateX(0)}}52%{{opacity:1}}58%{{opacity:1;transform:translateX({-(PHONE_X - BEZEL - WIN[2] - 20)}px)}}60%,100%{{opacity:0;transform:translateX({-(PHONE_X - BEZEL - WIN[2] - 20)}px)}}}}
@media (prefers-reduced-motion:reduce){{.a{{animation:none}}.note,.tap,.out,.back{{opacity:0}}}}
"""
    clip = f'<clipPath id="s"><rect x="{sx}" y="{sy}" width="{SCREEN_W}" height="{SCREEN_H}" rx="52"/></clipPath>'
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 {W} {H}" width="{W // 2}" height="{H // 2}">',
        f"<title>simple: approve a coding agent from your phone</title><style>{css}</style><defs>{clip}</defs>",
        f'<image href="{jpeg_uri(bg, 84)}" x="0" y="0" width="{W}" height="{H}"/>',
        f'<path d="{path}" stroke="{c["wire"]}" stroke-width="3" stroke-dasharray="2 12" stroke-linecap="round" fill="none"/>',
        f'<circle class="a out" cx="{WIN[2] + 10}" cy="480" r="9" fill="{c["amber"]}"/>',
        f'<circle class="a back" cx="{PHONE_X - BEZEL - 10}" cy="480" r="9" fill="{c["green"]}"/>',
        f'<image class="a after" href="{jpeg_uri(after, 88)}" x="{after_pos[0]}" y="{after_pos[1]}" width="{after.width}" height="{after.height}"/>',
        f'<g clip-path="url(#s)">',
        f'<image href="{jpeg_uri(today)}" x="{sx}" y="{sy}" width="{SCREEN_W}" height="{SCREEN_H}"/>',
        f'<image class="a chat" href="{jpeg_uri(chat)}" x="{sx}" y="{sy}" width="{SCREEN_W}" height="{SCREEN_H}"/>',
        f'<g class="a note"><image href="{png_uri(note)}" x="{bx}" y="{by}" width="{note.width}" height="{note.height}"/>',
        f'<circle class="a tap" cx="{ax:.0f}" cy="{ay:.0f}" r="34" fill="{c["ripple"]}"/></g>',
        "</g></svg>",
    ]
    return "\n".join(parts)


CARDS = [
    ("start", "Start agents from the phone", ["Pick a folder, an agent and a model,", "then type, dictate or attach a photo."], "new-agent", 0.3),
    ("approve", "Answer from the notification", ["Approvals and questions arrive with", "Allow and Deny, even with the app closed."], "shade", 0.2105),
    ("chat", "A real chat", ["Turns, tool calls and diffs, read from", "each agent's own transcript."], "chat", 0.03),
    ("review", "Review and commit", ["See what changed per file, read the", "diff, commit and push from the phone."], "changes", 0.03),
]

CARD_W, CARD_H = 860, 560


def card(theme, title, lines, name, top):
    c = THEMES[theme]
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, CARD_W - 1, CARD_H - 1), 36, fill=c["window"], outline=c["line"], width=2)
    tf = font(FONT_SANS, 38, "SemiBold")
    bf = font(FONT_SANS, 27)
    d.text((CARD_W / 2, 44), title, font=tf, fill=c["text"], anchor="mt")
    for i, line in enumerate(lines):
        d.text((CARD_W / 2, 104 + i * 36), line, font=bf, fill=c["muted"], anchor="mt")
    src = shot(theme, name)
    sw = 520
    sh = round(src.height * sw / src.width)
    ph = src.resize((sw, sh), Image.LANCZOS)
    y0 = round(top * sh)
    vis_h = CARD_H - 196
    ph = ph.crop((0, y0, sw, y0 + vis_h + 40))
    frame = Image.new("RGBA", (sw + 20, ph.height + 10), c["frame"])
    frame.putalpha(rounded_mask(frame.size, 46))
    inner = ph.convert("RGBA")
    inner.putalpha(rounded_mask(inner.size, 38))
    frame.alpha_composite(inner, (10, 10))
    fx = (CARD_W - frame.width) // 2
    img.alpha_composite(frame, (fx, 196))
    clip = rounded_mask((CARD_W, CARD_H), 36)
    out = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    out.paste(img, (0, 0), ImageChops.multiply(clip, img.getchannel("A")))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((0, 0, CARD_W - 1, CARD_H - 1), 36, outline=c["line"], width=2)
    return out


STRIP = ["today", "chat", "changes", "workspaces"]


def strip(theme):
    c = THEMES[theme]
    sw, gap = 400, 40
    shots = [shot(theme, n) for n in STRIP]
    sh = round(shots[0].height * sw / shots[0].width)
    w = len(shots) * (sw + 20) + (len(shots) - 1) * gap
    out = Image.new("RGBA", (w, sh + 20), (0, 0, 0, 0))
    for i, s in enumerate(shots):
        frame = Image.new("RGBA", (sw + 20, sh + 20), c["frame"])
        frame.putalpha(rounded_mask(frame.size, 52))
        inner = s.resize((sw, sh), Image.LANCZOS).convert("RGBA")
        inner.putalpha(rounded_mask(inner.size, 44))
        frame.alpha_composite(inner, (10, 10))
        out.alpha_composite(frame, (i * (sw + 20 + gap), 0))
    return out


def save_png(im, name):
    path = OUT / name
    im.save(path, "PNG", optimize=True)
    print(f"{name} {path.stat().st_size // 1024} KB")


def main():
    allow = tuple(float(v) for v in (sys.argv[1:3] or ["0.5", "0.8"]))
    for theme in THEMES:
        svg = hero(theme, allow)
        (OUT / f"hero-{theme}.svg").write_text(svg)
        print(f"hero-{theme}.svg {len(svg) // 1024} KB")
        for key, title, lines, shot, top in CARDS:
            if (SHOTS / theme / f"{shot}.jpg").exists():
                save_png(card(theme, title, lines, shot, top), f"card-{key}-{theme}.png")
        if all((SHOTS / theme / f"{n}.jpg").exists() for n in STRIP):
            save_png(strip(theme), f"screens-{theme}.png")


if __name__ == "__main__":
    main()
