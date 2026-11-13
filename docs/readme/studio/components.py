import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
R = ROOT / "docs/readme"
B = ROOT / "docs/brand"
ICONS = ROOT / "docs/readme/studio/icons"
OUT = Path.home() / ".cache/simple-canvas/index.html"


def font(name):
    return base64.b64encode((ROOT / "app/src/main/res/font" / name).read_bytes()).decode()


def uri(path):
    path = Path(path)
    mime = "image/svg+xml" if path.suffix == ".svg" else "image/png"
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode()


def svg_inline(path, size):
    s = path.read_text()
    return s.replace("<svg ", f'<svg style="height:{size}px;width:auto" ', 1)


I = {
    "shield": '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/>',
    "lock": '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    "key": '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3"/>',
    "phone": '<rect x="7" y="2" width="10" height="20" rx="2.5"/><path d="M11 18h2"/>',
    "terminal": '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M7 9l3 3-3 3M12 15h5"/>',
    "offline": '<path d="M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 12.5a10 10 0 0 1 5-2.4M19 12.5a10 10 0 0 0-2.6-1.8M2 8.8a15 15 0 0 1 4.2-2.6M22 8.8A15 15 0 0 0 11 5.1M12 20h.01"/>',
    "mic": '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
    "bug": '<rect x="7" y="8" width="10" height="12" rx="5"/><path d="M12 8V5M9 5l1.5 2M15 5l-1.5 2M7 13H3M21 13h-4M7.5 17.5L4 20M16.5 17.5L20 20M7.5 9.5L4 7M16.5 9.5L20 7"/>',
    "idea": '<path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z"/>',
    "pr": '<circle cx="6" cy="6" r="2.5"/><circle cx="6" cy="18" r="2.5"/><path d="M6 8.5v7"/><circle cx="18" cy="18" r="2.5"/><path d="M18 15.5V11M15.5 3.5l5 5M20.5 3.5l-5 5"/>',
    "check": '<path d="M5 12.5l4.5 4.5L19 7"/>',
    "clock": '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    "eyeoff": '<path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A10 10 0 0 1 12 5c5 0 9 4 10 7a13 13 0 0 1-2.4 3.5M6.6 6.6A13 13 0 0 0 2 12c1 3 5 7 10 7 2 0 3.8-.6 5.4-1.6"/>',
    "userx": '<circle cx="9" cy="8" r="4"/><path d="M3 20a6 6 0 0 1 12 0M17 8l4 4M21 8l-4 4"/>',
    "cloudoff": '<path d="M3 3l18 18M7.5 8.2A5 5 0 0 0 8 18h9.5M13.2 6.2A5 5 0 0 1 19.5 10a4 4 0 0 1 1.7 6.8"/>',
    "server": '<rect x="3" y="4" width="18" height="12" rx="2.5"/><path d="M8 20h8M12 16v4"/>',
    "layers": '<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5"/>',
    "download": '<path d="M12 3v12M7 10l5 5 5-5M4 21h16"/>',
    "arrow": '<path d="M5 12h14M13 6l6 6-6 6"/>',
    "tag": '<path d="M3 12V3h9l9 9-9 9z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
    "fork": '<circle cx="6" cy="5" r="2.5"/><circle cx="18" cy="5" r="2.5"/><circle cx="12" cy="19" r="2.5"/><path d="M6 7.5v1.5a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3V7.5M12 12v4.5"/>',
    "qr": '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3"/>',
    "box": '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5l8 4.5 8-4.5M12 12v9"/>',
    "commit": '<circle cx="12" cy="12" r="3.5"/><path d="M3 12h5.5M15.5 12H21"/>',
    "eye": '<path d="M2 12c1-3 5-7 10-7s9 4 10 7c-1 3-5 7-10 7S3 15 2 12z"/><circle cx="12" cy="12" r="3"/>',
    "scale": '<path d="M12 3v18M5 21h14M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z"/>',
    "book": '<path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17H6.5A2.5 2.5 0 0 0 4 21.5zM4 21.5A2.5 2.5 0 0 1 6.5 19H20v3H6.5"/>',
}


def ic(name, size=22, cls=""):
    return f'<svg class="ic {cls}" viewBox="0 0 24 24" width="{size}" height="{size}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{I[name]}</svg>'


def img(stem, w, ext="png", base=R):
    return (f'<img class="t-light" src="{uri(f"{base}/{stem}-light.{ext}")}" width="{w}" alt="">'
            f'<img class="t-dark" src="{uri(f"{base}/{stem}-dark.{ext}")}" width="{w}" alt="">')


def chip(text, tone="", icon=None):
    i = ic(icon, 15) if icon else f'<span class="dot {tone}"></span>'
    return f'<span class="chip {tone}">{i}{text}</span>'


def term(lines, title="", w=None):
    body = "".join(f'<div><span class="pr">{p}</span>{t}</div>' for p, t in lines)
    style = f' style="width:{w}px"' if w else ""
    return f'<div class="term"{style}><div class="tbar"><i></i><i></i><i></i><span>{title}</span></div><div class="tbody">{body}</div></div>'


def agent(logo, name, state, tone):
    return (f'<div class="agent"><div class="alogo">{svg_inline(ICONS / logo, 30)}</div>'
            f'<div class="aname">{name}</div>{chip(state, tone)}</div>')


def stat(value, unit, label, icon):
    return f'<div class="stat">{ic(icon, 20, "muted")}<div class="sv">{value}<small>{unit}</small></div><div class="sl">{label}</div></div>'


def tile(icon, title, line, tone="accent"):
    return f'<div class="tile"><div class="tic {tone}">{ic(icon, 24)}</div><div class="tt">{title}</div><div class="tl">{line}</div></div>'


def link_pill(icon, text, tone=""):
    return f'<a class="lpill {tone}" href="#">{ic(icon, 17)}<span>{text}</span>{ic("arrow", 15, "muted")}</a>'


def codeblock(text):
    return f'<div class="gh-code"><pre>{text}</pre><span class="copy" title="GitHub copy button">{ic("box", 14)}</span></div>'


ITEMS = []


def add(id, name, x, y, w, h, html, kind="cmp"):
    ITEMS.append(dict(id=id, name=name, x=x, y=y, w=w, h=h, html=html, kind=kind))


PAGE_W = 880
COLS = {"readme": 0, "security": 1000, "contributing": 2000}


def stack(page, entries, gap=56):
    y = 0
    for id, name, h, html, *kind in entries:
        add(id, name, COLS[page], y, PAGE_W, h, html, *(kind or ["cmp"]))
        y += h + gap


stack("readme", [
    ("r-hero", "README · Hero", 514, img("hero", 880, "svg"), "raw"),
    ("r-lockup", "README · Name and promise", 236, f'''
<div class="lockup">
  <img src="{uri(B / "icon.png")}" width="76" height="76" class="appicon" alt="">
  <div class="wordmark">simple</div>
  <div class="promise">Your coding agents, in your pocket.</div>
  <div class="chips">{chip("Open source", "", "book")}{chip("No cloud", "", "cloudoff")}{chip("No account", "", "userx")}{chip("No telemetry", "", "eyeoff")}</div>
</div>'''),
    ("r-nav", "README · Jump pills", 60, f'''
<div class="nav">{link_pill("download", "Install")}{link_pill("layers", "How it works")}{link_pill("shield", "Security")}{link_pill("bug", "Issues")}{link_pill("clock", "Side project")}</div>''', "raw"),
    ("r-cards", "README · Feature cards", 580, f'''
<div class="grid2">{img("card-start", 430)}{img("card-approve", 430)}{img("card-chat", 430)}{img("card-review", 430)}</div>''', "raw"),
    ("r-tiles", "README · Small wins", 214, f'''
<div class="row3">{tile("offline", "Works offline", "Sends wait on the phone and go out once.", "green")}{tile("terminal", "Real terminal", "Attach the TUI with a key bar and a Nerd Font.", "accent")}{tile("mic", "Voice on device", "Dictate prompts. Audio never leaves the phone.", "amber")}</div>''', "raw"),
    ("r-screens", "README · Screens strip", 444, img("screens", 880), "raw"),
    ("r-agents", "README · Agents", 236, f'''
<div class="pad"><div class="kicker">{ic("layers", 16)}Runs the agents you already use</div>
<div class="agents">{agent("claude-code.svg", "Claude Code", "Tested", "green")}{agent("opencode-icon.svg", "OpenCode", "Tested", "green")}{agent("cursor.svg", "Cursor", "Tested", "green")}{agent("openai.svg", "Codex", "Next", "amber")}</div></div>'''),
    ("r-install", "README · Install", 452, f'''
<div class="pad"><div class="kicker">{ic("download", 16)}Install in two lines</div>
<div class="steps">
  <div class="step"><div class="sn">1</div><div class="sh">{ic("server", 18)}On your computer</div>
    {term([("$", "curl -fsSL …/install.sh | sh"), ("$", "simpled pair")], "Linux", 372)}
    {term([("PS&gt;", "irm …/install.ps1 | iex"), ("PS&gt;", "simpled pair")], "Windows", 372)}
    <div class="note">{chip("macOS builds ship, not tested yet", "amber")}</div></div>
  <div class="arrowcol">{ic("arrow", 26, "muted")}</div>
  <div class="step"><div class="sn">2</div><div class="sh">{ic("phone", 18)}On your phone</div>
    <div class="phonebox">{ic("qr", 64, "muted")}<div class="pt">Install simple.apk, then scan the code</div>{chip("Both on Tailscale", "accent", "lock")}</div></div>
</div></div>'''),
    ("r-install-copy", "README · Copyable commands (real text)", 150, f'''
<div class="copyzone"><div class="cz-note">{ic("check", 15)}Kept as real text so GitHub's copy button works</div>
{codeblock("curl -fsSL https://github.com/HoneyPaptan/simple/releases/latest/download/install.sh | sh")}
{codeblock('irm https://github.com/HoneyPaptan/simple/releases/latest/download/install.ps1 | iex')}</div>''', "raw"),
    ("r-how", "README · How it works", 272, f'''
<div class="pad"><div class="kicker">{ic("layers", 16)}How it works</div>
<div class="flow">
  <div class="node">{ic("phone", 26)}<b>Phone</b><span>the app</span></div>
  <div class="wire"><span>pinned TLS<br>over Tailscale</span></div>
  <div class="node hl">{ic("server", 26)}<b>simpled</b><span>one Go binary</span></div>
  <div class="wire short"></div>
  <div class="node">{ic("layers", 26)}<b>herdr</b><span>panes</span></div>
  <div class="wire short"></div>
  <div class="node">{ic("terminal", 26)}<b>Agents</b><span>your tools</span></div>
</div>
<div class="chips center">{chip("No relay", "", "cloudoff")}{chip("No telemetry", "", "eyeoff")}{chip("Loopback and tailnet only", "", "lock")}{chip("Token per phone", "", "key")}</div></div>'''),
    ("r-numbers", "README · Numbers", 168, f'''
<div class="row4">{stat("7.1", "MB", "Android app", "phone")}{stat("9.3", "MB", "Daemon", "server")}{stat("297", "ms", "Cold start, 2018 phone", "clock")}{stat("0", "", "Servers of ours", "cloudoff")}</div>''', "raw"),
    ("r-side", "README · Side project", 270, f'''
<div class="side"><img src="{uri(B / "mascot-sleep.png")}" width="150" height="150" alt="">
<div class="sidebody"><div class="st">A side project</div><div class="ss">Built by one person in spare time. Replies can take a few days.</div>
<div class="lpills">{link_pill("bug", "Issues welcome", "green")}{link_pill("pr", "Pull requests closed for now", "amber")}{link_pill("shield", "Security reports stay private", "accent")}</div></div></div>'''),
    ("r-foot", "README · Footer", 64, f'''
<div class="foot">{chip("Apache 2.0", "", "scale")}{chip("Fonts under OFL 1.1", "", "book")}{chip("Agent logos belong to their owners", "", "tag")}</div>''', "raw"),
])

stack("security", [
    ("s-head", "SECURITY · Header", 190, f'''
<div class="dochead"><div class="dhic accent">{ic("shield", 40)}</div><div><div class="dt">Security</div>
<div class="ds">A paired phone can run your agents and touch your files. Reports matter.</div></div></div>'''),
    ("s-report", "SECURITY · Report privately", 232, f'''
<div class="pad"><div class="kicker">{ic("lock", 16)}Found something?</div>
<div class="cta"><div class="ctab">{ic("shield", 20)}Report a vulnerability</div><div class="path">{chip("Security tab", "")}{ic("arrow", 15, "muted")}{chip("Report a vulnerability", "")}</div></div>
<div class="chips">{chip("Never a public issue", "amber", "eyeoff")}{chip("Answer within a week", "", "clock")}{chip("Include app and simpled versions", "", "tag")}{chip("Steps to reproduce", "", "check")}</div></div>'''),
    ("s-model", "SECURITY · What protects you", 530, f'''
<div class="pad"><div class="kicker">{ic("key", 16)}What protects the link</div>
<div class="grid2 tight">{tile("lock", "Pinned certificate", "The QR carries the fingerprint. Nothing in between can read the link.", "accent")}{tile("clock", "One time code", "Works once, expires in 5 minutes.", "amber")}{tile("key", "A token per phone", "Revoke any phone with simpled devices rm.", "green")}{tile("server", "Never every interface", "Listens on loopback and your tailnet only.", "accent")}</div>
<div class="morelink">{link_pill("book", "Full model in docs/security.md")}</div></div>'''),
    ("s-versions", "SECURITY · Supported versions", 92, f'''
<div class="banner">{ic("tag", 20)}<b>Only the latest release gets fixes</b>{chip("Update: run the install command again", "")}</div>'''),
])

stack("contributing", [
    ("c-head", "CONTRIBUTING · Header", 190, f'''
<div class="dochead"><img src="{uri(B / "mascot-wave.png")}" width="120" height="120" alt=""><div><div class="dt">Contributing</div>
<div class="ds">One person keeps simple in spare time. Here is how to help right now.</div></div></div>'''),
    ("c-ways", "CONTRIBUTING · Issues open, PRs closed", 300, f'''
<div class="row2">
<div class="big green"><div class="bh">{ic("bug", 22)}Issues are welcome</div><div class="bl">Bugs, rough edges, ideas and questions all help.</div>
<div class="btns"><a class="btn" href="#">{ic("bug", 16)}Report a bug</a><a class="btn" href="#">{ic("idea", 16)}Suggest an idea</a></div>
<div class="chips">{chip("simpled version", "", "tag")}{chip("Your OS", "", "server")}{chip("Which agent", "", "terminal")}</div></div>
<div class="big amber"><div class="bh">{ic("pr", 22)}Pull requests are closed</div><div class="bl">Reviewing outside changes takes more time than I have right now.</div>
<div class="btns"><a class="btn" href="#">{ic("bug", 16)}Describe the fix in an issue</a></div>
<div class="chips">{chip("Forks welcome", "", "fork")}{chip("Opens later", "", "clock")}</div></div>
</div>''', "raw"),
    ("c-build", "CONTRIBUTING · Build from source", 244, f'''
<div class="pad"><div class="kicker">{ic("terminal", 16)}Build from source</div>
<div class="buildrow">{term([("$", "git clone …/simple && cd simple"), ("$", "git submodule update --init"), ("$", "./scripts/check")], "everything, with tests and budgets", 520)}
<div class="reqs">{chip("Go 1.23+", "", "box")}{chip("JDK 17+", "", "box")}{chip("Android SDK", "", "phone")}{chip("scripts/check is the only CI", "accent", "check")}</div></div></div>'''),
    ("c-rules", "CONTRIBUTING · House rules", 236, f'''
<div class="pad"><div class="kicker">{ic("check", 16)}House rules for collaborators</div>
<div class="row4 inner">{stat("1", "", "change per commit", "commit")}{stat("8", "MB", "APK budget", "phone")}{stat("10", "MB", "Daemon budget", "server")}{stat("Show", "", "it working", "eye")}</div></div>'''),
])

GEIST = font("geist.ttf")
MONO = font("geist_mono.ttf")

CSS = """
@font-face{font-family:Geist;src:url(data:font/ttf;base64,%s) format('truetype');font-weight:100 900}
@font-face{font-family:GeistMono;src:url(data:font/ttf;base64,%s) format('truetype');font-weight:100 900}
.cv{--bg:#F4F3EF;--sf:#FFFFFF;--sf2:#F7F6F3;--line:#E3E1DA;--text:#121212;--muted:#6F6E69;--faint:#A3A19B;--amber:#A86A00;--green:#2F7D4F;--accent:#5B3FD6;--gh:#ffffff;--ghline:#d1d9e0;--canvas:transparent;--codebg:#f6f8fa}
.cv[data-theme=dark]{--bg:#09090A;--sf:#121214;--sf2:#18181B;--line:#26262B;--text:#F2F2F2;--muted:#93939A;--faint:#5E5E64;--amber:#E6B04A;--green:#6CC28E;--accent:#9C86FF;--gh:#0d1117;--ghline:#3d444d;--canvas:transparent;--codebg:#151b23}
.cv{position:relative;height:860px;overflow:hidden;background:var(--canvas);border-radius:12px;cursor:default;font-family:Geist,system-ui,sans-serif;color:var(--text);overscroll-behavior:none;touch-action:none;user-select:none}
.cv .t-dark{display:none}.cv[data-theme=dark] .t-dark{display:block}.cv[data-theme=dark] .t-light{display:none}
.world{position:absolute;left:0;top:0;transform-origin:0 0;will-change:transform}
.page{position:absolute;background:var(--gh);border:1px solid var(--ghline);border-radius:10px}
.ptitle{position:absolute;font:600 15px GeistMono,monospace;color:var(--muted);letter-spacing:.02em}
.item{position:absolute}
.lab{position:absolute;left:0;top:-30px;display:flex;align-items:center;gap:6px;font:500 13px Geist,sans-serif;color:var(--muted);white-space:nowrap}
.lab button{all:unset;cursor:default;padding:2px 4px;border-radius:6px}
.lab button:focus-visible,.handle:focus-visible,.ctl button:focus-visible,.ctl select:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.lab .ok{color:var(--green)}
.handle{all:unset;display:grid;place-items:center;width:20px;height:20px;border-radius:5px;color:var(--faint);cursor:default}
.handle:hover{background:var(--sf2);color:var(--muted)}
.body{width:100%%;height:100%%;overflow:hidden}
.cmp{box-sizing:border-box;background:var(--bg);border:1px solid var(--line);border-radius:28px}
.raw{box-sizing:border-box}
.ctl{position:absolute;top:10px;left:10px;z-index:5;display:flex;gap:6px;align-items:center;background:var(--sf);border:1px solid var(--line);border-radius:999px;padding:5px 6px;font:500 13px Geist,sans-serif}
.ctl button,.ctl select{all:unset;cursor:default;padding:5px 10px;border-radius:999px;color:var(--text)}
.ctl button:hover,.ctl select:hover{background:var(--sf2)}
.ctl .z{color:var(--muted);min-width:44px;text-align:center}
.ic{flex:none;vertical-align:middle}.muted{color:var(--muted)}
.chip{display:inline-flex;align-items:center;gap:7px;padding:7px 13px;border-radius:999px;background:var(--sf);border:1px solid var(--line);font:500 14px Geist;color:var(--text);white-space:nowrap}
.chip .ic{color:var(--muted)}
.chip.green{color:var(--green)}.chip.amber{color:var(--amber)}.chip.accent{color:var(--accent)}
.chip.green .ic,.chip.amber .ic,.chip.accent .ic{color:inherit}
.dot{width:7px;height:7px;border-radius:50%%;background:var(--faint)}
.dot.green{background:var(--green)}.dot.amber{background:var(--amber)}.dot.accent{background:var(--accent)}
.chips{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}.chips.center{margin-top:22px}
.lockup{height:100%%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px}
.appicon{border-radius:20px}
.wordmark{font:650 54px Geist;letter-spacing:-.03em;line-height:1}
.promise{font:400 21px Geist;color:var(--muted);margin-bottom:10px}
.nav{display:flex;gap:10px;justify-content:center;height:100%%;align-items:center}
.lpill{display:inline-flex;align-items:center;gap:8px;padding:11px 15px;border-radius:999px;background:var(--bg);border:1px solid var(--line);color:var(--text);text-decoration:none;font:550 15px Geist}
.lpill .ic:first-child{color:var(--muted)}
.lpill.green .ic:first-child{color:var(--green)}.lpill.amber .ic:first-child{color:var(--amber)}.lpill.accent .ic:first-child{color:var(--accent)}
.grid2{display:grid;grid-template-columns:430px 430px;gap:20px}
.grid2 img{display:block;border-radius:18px}
.grid2.tight{grid-template-columns:1fr 1fr;gap:14px}
.row3{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;height:100%%}
.row4{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;height:100%%}
.row4.inner{height:auto}
.row2{display:grid;grid-template-columns:1fr 1fr;gap:20px;height:100%%}
.tile{background:var(--bg);border:1px solid var(--line);border-radius:24px;padding:24px;display:flex;flex-direction:column;gap:8px}
.tic{width:46px;height:46px;border-radius:14px;display:grid;place-items:center;background:var(--sf);border:1px solid var(--line);margin-bottom:6px}
.tic.green{color:var(--green)}.tic.amber{color:var(--amber)}.tic.accent{color:var(--accent)}
.tt{font:600 20px Geist;letter-spacing:-.01em}.tl{font:400 15px/1.45 Geist;color:var(--muted)}
.pad{padding:30px 34px;height:100%%;box-sizing:border-box}
.kicker{display:flex;align-items:center;gap:8px;font:600 13px GeistMono;color:var(--muted);text-transform:uppercase;letter-spacing:.08em;margin-bottom:22px}
.agents{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}
.agent{display:flex;flex-direction:column;align-items:center;gap:10px}
.alogo{width:64px;height:64px;border-radius:18px;background:var(--sf);border:1px solid var(--line);display:grid;place-items:center}
.cv[data-theme=dark] .alogo svg{filter:invert(.92) hue-rotate(180deg)}
.aname{font:600 16px Geist}
.steps{display:grid;grid-template-columns:1fr 40px 300px;align-items:start;gap:8px}
.step{display:flex;flex-direction:column;gap:12px}
.sn{width:28px;height:28px;border-radius:50%%;background:var(--text);color:var(--bg);display:grid;place-items:center;font:700 14px Geist}
.sh{display:flex;align-items:center;gap:8px;font:600 17px Geist}
.arrowcol{display:grid;place-items:center;height:260px}
.term{background:var(--sf);border:1px solid var(--line);border-radius:14px;overflow:hidden}
.tbar{display:flex;align-items:center;gap:6px;padding:9px 12px;border-bottom:1px solid var(--line);background:var(--sf2)}
.tbar i{width:9px;height:9px;border-radius:50%%;background:var(--line)}
.tbar span{margin-left:8px;font:500 12px GeistMono;color:var(--faint)}
.tbody{padding:12px 14px;font:450 14px/1.7 GeistMono;white-space:nowrap;font-feature-settings:'liga' 0,'calt' 0}
.tbody .pr{color:var(--faint);margin-right:10px}
.phonebox{border:1px dashed var(--line);border-radius:22px;height:232px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;background:var(--sf)}
.pt{font:500 15px Geist;color:var(--muted);text-align:center;max-width:200px}
.note{display:flex}
.copyzone{display:flex;flex-direction:column;gap:12px;padding:6px 0}
.cz-note{display:flex;align-items:center;gap:6px;font:500 13px Geist;color:var(--green)}
.gh-code{position:relative;background:var(--codebg);border-radius:6px;padding:16px;font:13.6px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--text)}
.gh-code pre{margin:0;white-space:pre;overflow:hidden}
.copy{position:absolute;right:10px;top:10px;color:var(--muted);border:1px solid var(--ghline);border-radius:6px;padding:4px;display:grid;place-items:center}
.flow{display:flex;align-items:center;justify-content:center;gap:0}
.node{display:flex;flex-direction:column;align-items:center;gap:6px;width:116px;padding:18px 0;border-radius:20px;background:var(--sf);border:1px solid var(--line)}
.node b{font:600 16px Geist}.node span{font:400 13px Geist;color:var(--muted)}
.node.hl{border-color:var(--accent);color:var(--accent)}.node.hl b{color:var(--text)}
.wire{position:relative;width:150px;height:2px;background:repeating-linear-gradient(90deg,var(--faint) 0 3px,transparent 3px 10px)}
.wire.short{width:44px}
.wire span{position:absolute;left:50%%;top:-46px;transform:translateX(-50%%);font:500 12px/1.4 GeistMono;color:var(--muted);text-align:center;white-space:nowrap}
.stat{background:var(--bg);border:1px solid var(--line);border-radius:24px;padding:22px;display:flex;flex-direction:column;gap:6px}
.sv{font:650 40px Geist;letter-spacing:-.03em;line-height:1.05}.sv small{font:500 17px Geist;color:var(--muted);margin-left:4px;letter-spacing:0}
.sl{font:500 14px Geist;color:var(--muted)}
.side{display:flex;align-items:center;gap:30px;padding:30px 40px;height:100%%;box-sizing:border-box}
.side img{border-radius:24px}
.st{font:650 30px Geist;letter-spacing:-.02em}.ss{font:400 16px/1.45 Geist;color:var(--muted);margin:6px 0 18px}
.lpills{display:flex;flex-direction:column;gap:9px;align-items:flex-start}
.foot{display:flex;gap:10px;justify-content:center;align-items:center;height:100%%}
.dochead{display:flex;align-items:center;gap:28px;padding:0 44px;height:100%%}
.dhic{width:96px;height:96px;border-radius:28px;display:grid;place-items:center;background:var(--sf);border:1px solid var(--line)}
.dhic.accent{color:var(--accent)}
.dochead img{border-radius:24px}
.dt{font:650 46px Geist;letter-spacing:-.03em}.ds{font:400 18px/1.45 Geist;color:var(--muted);max-width:560px;margin-top:6px}
.cta{display:flex;align-items:center;gap:18px;margin-bottom:22px}
.ctab{display:inline-flex;align-items:center;gap:10px;padding:16px 22px;border-radius:999px;background:var(--text);color:var(--bg);font:600 18px Geist}
.path{display:flex;align-items:center;gap:8px}
.morelink{margin-top:16px;display:flex;justify-content:center}
.banner{display:flex;align-items:center;gap:14px;padding:0 30px;height:100%%;font:16px Geist;color:var(--text)}
.banner b{font-weight:600;flex:1}
.big{background:var(--bg);border:1px solid var(--line);border-radius:28px;padding:28px;display:flex;flex-direction:column;gap:12px}
.bh{display:flex;align-items:center;gap:10px;font:650 22px Geist;letter-spacing:-.01em}
.big.green .bh .ic{color:var(--green)}.big.amber .bh .ic{color:var(--amber)}
.bl{font:400 16px/1.45 Geist;color:var(--muted)}
.btns{display:flex;flex-wrap:wrap;gap:8px;margin:6px 0 4px}
.btn{display:inline-flex;align-items:center;gap:8px;padding:11px 16px;border-radius:999px;background:var(--text);color:var(--bg);text-decoration:none;font:600 15px Geist}
.big .chips{justify-content:flex-start;margin-top:auto}
.pad .chips{justify-content:flex-start}.pad .chips.center{justify-content:center}
.buildrow{display:flex;gap:24px;align-items:center}
.reqs{display:flex;flex-direction:column;gap:9px;align-items:flex-start}
""" % (GEIST, MONO)

JS = r"""
(() => {
const items = %s;
const KEY = "simple-readme-canvas:v1";
const cv = document.getElementById("cv"), world = document.getElementById("world");
const zlabel = document.getElementById("zl"), themeSel = document.getElementById("theme");
let cam = {x: 40, y: 60, z: 0.3}, pos = {}, theme = "dark";
const clampZ = z => Math.min(3, Math.max(0.08, z));
const finite = n => typeof n === "number" && isFinite(n) && Math.abs(n) < 1e6;
try {
  const s = JSON.parse(localStorage.getItem(KEY) || "null");
  if (s && s.v === 1) {
    if (s.cam && finite(s.cam.x) && finite(s.cam.y) && finite(s.cam.z)) cam = {x: s.cam.x, y: s.cam.y, z: clampZ(s.cam.z)};
    if (s.theme === "light" || s.theme === "dark") theme = s.theme;
    for (const it of items) { const p = s.pos && s.pos[it.id]; if (p && finite(p.x) && finite(p.y)) pos[it.id] = p; }
  }
} catch (e) {}
let saveT = 0;
function save() { clearTimeout(saveT); saveT = setTimeout(() => { try { localStorage.setItem(KEY, JSON.stringify({v: 1, cam, theme, pos})); } catch (e) {} }, 300); }
let raf = 0;
function apply() { if (raf) return; raf = requestAnimationFrame(() => { raf = 0; world.style.transform = `translate(${cam.x}px,${cam.y}px) scale(${cam.z})`; zlabel.textContent = Math.round(cam.z * 100) + "%%"; }); }
function setTheme(t) { theme = t; cv.dataset.theme = t; themeSel.value = t; save(); }
const els = {};
for (const it of items) {
  const el = document.createElement("div");
  el.className = "item"; el.style.width = it.w + "px"; el.style.height = it.h + "px";
  const p = pos[it.id] || it; el.style.left = p.x + "px"; el.style.top = p.y + "px";
  el.innerHTML = `<div class="lab"><button class="handle" aria-label="Move ${it.name}. Arrow keys move it." title="Drag to move"><svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor"><circle cx="3" cy="2" r="1.2"/><circle cx="9" cy="2" r="1.2"/><circle cx="3" cy="6" r="1.2"/><circle cx="9" cy="6" r="1.2"/><circle cx="3" cy="10" r="1.2"/><circle cx="9" cy="10" r="1.2"/></svg></button><button class="name" title="Click to copy this name">${it.name}</button><span class="ok" aria-live="polite"></span></div><div class="body ${it.kind}">${it.html}</div>`;
  world.appendChild(el); els[it.id] = el;
  const name = el.querySelector(".name"), ok = el.querySelector(".ok");
  name.addEventListener("click", async () => {
    if (panned) return;
    try { await navigator.clipboard.writeText(it.name); ok.textContent = "Copied"; setTimeout(() => ok.textContent = "", 1200); }
    catch (e) { const r = document.createRange(); r.selectNodeContents(name); const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r); ok.textContent = "Press Ctrl+C"; }
  });
  const h = el.querySelector(".handle");
  h.addEventListener("pointerdown", e => {
    if (e.button !== 0 || spaceDown) return;
    e.stopPropagation(); h.setPointerCapture(e.pointerId);
    const start = {x: e.clientX, y: e.clientY}, o = pos[it.id] || {x: it.x, y: it.y};
    const move = ev => { const p = {x: o.x + (ev.clientX - start.x) / cam.z, y: o.y + (ev.clientY - start.y) / cam.z}; pos[it.id] = p; el.style.left = p.x + "px"; el.style.top = p.y + "px"; };
    const up = () => { h.removeEventListener("pointermove", move); h.removeEventListener("pointerup", up); h.removeEventListener("pointercancel", up); save(); };
    h.addEventListener("pointermove", move); h.addEventListener("pointerup", up); h.addEventListener("pointercancel", up);
  });
  h.addEventListener("keydown", e => {
    const d = e.shiftKey ? 40 : 10, k = {ArrowLeft: [-d, 0], ArrowRight: [d, 0], ArrowUp: [0, -d], ArrowDown: [0, d]}[e.key];
    if (!k) return; e.preventDefault();
    const o = pos[it.id] || {x: it.x, y: it.y}, p = {x: o.x + k[0], y: o.y + k[1]}; pos[it.id] = p; el.style.left = p.x + "px"; el.style.top = p.y + "px"; save();
  });
}
const pages = %s;
for (const pg of pages) {
  const d = document.createElement("div"); d.className = "page";
  Object.assign(d.style, {left: pg.x - 40 + "px", top: pg.y - 80 + "px", width: pg.w + 80 + "px", height: pg.h + 120 + "px", zIndex: -1});
  const t = document.createElement("div"); t.className = "ptitle"; t.textContent = pg.title; t.style.left = pg.x - 40 + "px"; t.style.top = pg.y - 116 + "px";
  world.prepend(d); world.prepend(t);
}
function bounds() {
  let a = Infinity, b = Infinity, c = -Infinity, d = -Infinity;
  for (const it of items) { const p = pos[it.id] || it; a = Math.min(a, p.x - 40); b = Math.min(b, p.y - 120); c = Math.max(c, p.x + it.w + 40); d = Math.max(d, p.y + it.h + 40); }
  return {a, b, c, d};
}
function fit() { const r = cv.getBoundingClientRect(), bb = bounds(); const z = clampZ(Math.min((r.width - 40) / (bb.c - bb.a), (r.height - 80) / (bb.d - bb.b))); cam = {z, x: 20 - bb.a * z + ((r.width - 40) - (bb.c - bb.a) * z) / 2, y: 60 - bb.b * z}; apply(); save(); }
function zoomAt(f, cx, cy) { const z = clampZ(cam.z * f); const k = z / cam.z; cam = {z, x: cx - (cx - cam.x) * k, y: cy - (cy - cam.y) * k}; apply(); save(); }
function center() { const r = cv.getBoundingClientRect(); return [r.width / 2, r.height / 2]; }
document.getElementById("zin").onclick = () => zoomAt(1.25, ...center());
document.getElementById("zout").onclick = () => zoomAt(0.8, ...center());
document.getElementById("fit").onclick = fit;
document.getElementById("reset").onclick = () => { cam = {x: 40, y: 60, z: 1}; apply(); save(); };
themeSel.onchange = () => setTheme(themeSel.value);
cv.addEventListener("wheel", e => {
  e.preventDefault();
  const r = cv.getBoundingClientRect();
  if (e.ctrlKey || e.metaKey) zoomAt(Math.exp(-e.deltaY * 0.0022), e.clientX - r.left, e.clientY - r.top);
  else { cam.x -= e.deltaX; cam.y -= e.deltaY; apply(); save(); }
}, {passive: false});
let spaceDown = false, panned = false, drag = null;
addEventListener("keydown", e => { if (e.code === "Space" && !e.target.closest("button,select,input,textarea,[contenteditable]")) { spaceDown = true; e.preventDefault(); } });
addEventListener("keyup", e => { if (e.code === "Space") spaceDown = false; });
addEventListener("blur", () => { spaceDown = false; drag = null; });
cv.addEventListener("pointerdown", e => {
  const onItem = e.target.closest(".item,.ctl");
  if (!(e.button === 1 || (e.button === 0 && (spaceDown || !onItem)))) return;
  e.preventDefault(); cv.setPointerCapture(e.pointerId); panned = false;
  drag = {x: e.clientX, y: e.clientY, cx: cam.x, cy: cam.y, id: e.pointerId};
});
cv.addEventListener("pointermove", e => { if (!drag || e.pointerId !== drag.id) return; const dx = e.clientX - drag.x, dy = e.clientY - drag.y; if (Math.abs(dx) + Math.abs(dy) > 3) panned = true; cam.x = drag.cx + dx; cam.y = drag.cy + dy; apply(); });
const end = e => { if (drag && e.pointerId === drag.id) { drag = null; save(); setTimeout(() => panned = false, 0); } };
cv.addEventListener("pointerup", end); cv.addEventListener("pointercancel", end); cv.addEventListener("lostpointercapture", end);
cv.addEventListener("keydown", e => { if (e.target !== cv) return; const s = 60, m = {ArrowLeft: [s, 0], ArrowRight: [-s, 0], ArrowUp: [0, s], ArrowDown: [0, -s]}[e.key]; if (m) { e.preventDefault(); cam.x += m[0]; cam.y += m[1]; apply(); save(); } if (e.key === "+" || e.key === "=") zoomAt(1.25, ...center()); if (e.key === "-") zoomAt(0.8, ...center()); if (e.key === "0") fit(); });
setTheme(theme);
if (!localStorage.getItem || !(() => { try { return localStorage.getItem(KEY); } catch (e) { return null; } })()) requestAnimationFrame(fit); else apply();
})();
"""


def main():
    pages = []
    for name, x in COLS.items():
        ys = [it for it in ITEMS if it["x"] == x]
        h = max(it["y"] + it["h"] for it in ys)
        pages.append(dict(title={"readme": "README.md", "security": "SECURITY.md", "contributing": "CONTRIBUTING.md"}[name], x=x, y=0, w=PAGE_W, h=h))
    data = json.dumps([{k: it[k] for k in ("id", "name", "x", "y", "w", "h", "html", "kind")} for it in ITEMS])
    html = f"""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>simple docs canvas</title><style>{CSS}</style></head>
<body style="margin:0">
<div class="cv" id="cv" data-theme="dark" tabindex="0" aria-label="Docs canvas. Drag empty space to pan, Ctrl and wheel to zoom, arrow keys pan when focused.">
<div class="ctl"><button id="zout" aria-label="Zoom out">−</button><span class="z" id="zl">30%</span><button id="zin" aria-label="Zoom in">+</button><button id="fit">Fit all</button><button id="reset">100%</button>
<select id="theme" aria-label="Theme"><option value="dark">Dark</option><option value="light">Light</option></select></div>
<div class="world" id="world"></div></div>
<script>{JS % (data, json.dumps(pages))}</script></body></html>"""
    OUT.write_text(html)
    print(OUT, len(html))



if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    main()

def sheet(col, theme, start=0, count=99):
    its = [it for it in ITEMS if it["x"] == COLS[col]][start:start + count]
    body = "".join(f'<div style="margin:0 0 40px"><div style="font:500 13px Geist;color:var(--muted);margin-bottom:8px">{it["name"]}</div><div class="body {it["kind"]}" style="width:{it["w"]}px;height:{it["h"]}px">{it["html"]}</div></div>' for it in its)
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body style="margin:0"><div class="cv" data-theme="{theme}" style="height:auto;overflow:visible;padding:30px;background:var(--gh);user-select:text">{body}</div></body></html>'


