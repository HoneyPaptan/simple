#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const icons = require("@hugeicons/core-free-icons");

const MAP = {
  Plus: "Add01Icon",
  Search: "SearchIcon",
  Mic: "Mic01Icon",
  X: "Cancel01Icon",
  Check: "Tick01Icon",
  ChevR: "ArrowRight01Icon",
  ChevD: "ArrowDown01Icon",
  Back: "ArrowLeft01Icon",
  More: "MoreHorizontalCircle01Icon",
  Folder: "Folder01Icon",
  Folders: "FoldersIcon",
  Bell: "Notification01Icon",
  Copy: "Copy01Icon",
  Trash: "Delete02Icon",
  Stop: "StopIcon",
  Person: "UserIcon",
  Term: "ComputerTerminalIcon",
  Grid: "LayoutGridIcon",
  Branch: "GitBranchIcon",
  Image: "Image01Icon",
  Camera: "Camera01Icon",
  Server: "ServerStack01Icon",
  Refresh: "RefreshIcon",
  Pencil: "PencilEditIcon",
  Eye: "ViewIcon",
  Qr: "QrCodeIcon",
  Pin: "PinIcon",
  Arrow: "ArrowUpRight01Icon",
  Shield: "Shield01Icon",
  FolderPlus: "FolderAddIcon",
  Up: "ArrowUp01Icon",
  Down: "ArrowDown01Icon",
  Sliders: "SlidersHorizontalIcon",
  Bolt: "FlashIcon",
  File: "File01Icon",
  Text: "TextIcon",
  Undo: "UndoIcon",
  Redo: "RedoIcon",
  Rect: "SquareIcon",
  Shell: "SourceCodeIcon",
  Fx: "SourceCodeIcon",
};

function pathsFor(exportName) {
  const raw = icons[exportName];
  if (!raw) throw new Error(`missing ${exportName}`);
  return raw
    .map(([tag, a]) => elementPath(tag, a))
    .filter(Boolean)
    .map((d) => d.replaceAll("'", "\\'"));
}

function num(v) {
  return Number(v ?? 0);
}

function ellipsePath(cx, cy, rx, ry) {
  return `M${cx - rx} ${cy}A${rx} ${ry} 0 1 0 ${cx + rx} ${cy}A${rx} ${ry} 0 1 0 ${cx - rx} ${cy}Z`;
}

function rectPath(x, y, w, h, rx, ry) {
  if (!rx && !ry) return `M${x} ${y}H${x + w}V${y + h}H${x}Z`;
  rx = Math.min(rx || ry, w / 2);
  ry = Math.min(ry || rx, h / 2);
  return [
    `M${x + rx} ${y}`,
    `H${x + w - rx}`,
    `A${rx} ${ry} 0 0 1 ${x + w} ${y + ry}`,
    `V${y + h - ry}`,
    `A${rx} ${ry} 0 0 1 ${x + w - rx} ${y + h}`,
    `H${x + rx}`,
    `A${rx} ${ry} 0 0 1 ${x} ${y + h - ry}`,
    `V${y + ry}`,
    `A${rx} ${ry} 0 0 1 ${x + rx} ${y}`,
    "Z",
  ].join("");
}

function elementPath(tag, a) {
  if (!a) return null;
  switch (tag) {
    case "path":
      return a.d || null;
    case "circle":
      return ellipsePath(num(a.cx), num(a.cy), num(a.r), num(a.r));
    case "ellipse":
      return ellipsePath(num(a.cx), num(a.cy), num(a.rx), num(a.ry));
    case "rect":
      return rectPath(num(a.x), num(a.y), num(a.width), num(a.height), num(a.rx), num(a.ry));
    default:
      throw new Error(`unsupported element ${tag}`);
  }
}

const lines = [];
lines.push("package sh.simple.android.ui.kit");
lines.push("");
lines.push("import androidx.compose.ui.graphics.SolidColor");
lines.push("import androidx.compose.ui.graphics.StrokeCap");
lines.push("import androidx.compose.ui.graphics.StrokeJoin");
lines.push("import androidx.compose.ui.graphics.vector.ImageVector");
lines.push("import androidx.compose.ui.graphics.vector.PathBuilder");
lines.push("import androidx.compose.ui.graphics.vector.PathParser");
lines.push("import androidx.compose.ui.graphics.vector.path");
lines.push("import androidx.compose.ui.unit.dp");
lines.push("import sh.simple.android.ui.theme.SimpleColors");
lines.push("");
lines.push("object IconsGenerated {");
for (const [simple, huge] of Object.entries(MAP)) {
  const paths = pathsFor(huge);
  const pathBlock = paths
    .map((d) => {
      return `        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("${d}").toNodes())
        }`;
    })
    .join("\n");
  lines.push(`    val ${simple} = ImageVector.Builder("${simple}", 24.dp, 24.dp, 24f, 24f).apply {`);
  lines.push("        val strokeColor = SolidColor(SimpleColors.iconVectorInk)");
  lines.push(pathBlock);
  lines.push("    }.build()");
  lines.push("");
}
lines.push("}");

const out = path.resolve(__dirname, "../../app/src/main/kotlin/sh/simple/android/ui/kit/IconsGenerated.kt");
fs.writeFileSync(out, lines.join("\n") + "\n");
console.log(`wrote ${out}`);
