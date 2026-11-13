#!/bin/sh
set -eu

GITHUB_REPO=HoneyPaptan/simple
DEFAULT_BASE="https://github.com/${GITHUB_REPO}/releases/latest/download"

die() {
	echo "install.sh: $*" >&2
	exit 1
}

if [ "$(id -u)" -eq 0 ]; then
	die "refuse to run as root; install as your normal user"
fi

os_name=$(uname -s)
arch=""
case "$(uname -m)" in
x86_64 | amd64) arch=amd64 ;;
aarch64 | arm64) arch=arm64 ;;
*) die "unsupported cpu: $(uname -m)" ;;
esac

platform=""
case "$os_name" in
Linux) platform=linux ;;
Darwin) platform=darwin ;;
*) die "unsupported os: $os_name (use install.ps1 on Windows)" ;;
esac

base_url="${INSTALL_BASE_URL:-$DEFAULT_BASE}"
case "$base_url" in
https://*) ;;
http://127.0.0.1:* | http://127.0.0.1/*) ;;
*) die "INSTALL_BASE_URL must start with https:// or http://127.0.0.1" ;;
esac

home="${HOME:-}"
[ -n "$home" ] || die "HOME is not set"
bindir="$home/.local/bin"
dest="$bindir/simpled"
from_source=0
uninstall=0

for arg in "$@"; do
	case "$arg" in
	--from-source) from_source=1 ;;
	--uninstall) uninstall=1 ;;
	-h | --help)
		echo "usage: install.sh [--from-source] [--uninstall]"
		exit 0
		;;
	*) die "unknown option: $arg" ;;
	esac
done

if [ "$uninstall" -eq 1 ]; then
	echo "Will run simpled uninstall if present, then remove $dest"
	if [ -x "$dest" ]; then
		"$dest" uninstall || true
	fi
	rm -f "$dest"
	echo "Uninstall finished."
	exit 0
fi

tmpdir=""
cleanup() {
	[ -n "$tmpdir" ] && rm -rf "$tmpdir"
}
trap cleanup EXIT

if [ "$from_source" -eq 1 ]; then
	if ! command -v go >/dev/null 2>&1; then
		die "--from-source needs Go installed"
	fi
	script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
	echo "Will build simpled from $script_dir/daemon for $platform/$arch"
	tmpdir=$(mktemp -d)
	(
		cd "$script_dir/daemon"
		GOOS=$platform GOARCH=$arch go build -trimpath -ldflags "-s -w" -o "$tmpdir/simpled" ./cmd/simpled
	)
	bin="$tmpdir/simpled"
else
	bin_name="simpled-$platform-$arch"
	echo "Will download $bin_name and SHA256SUMS from $base_url"
	echo "Will verify the checksum, install to $dest, and run simpled install"
	tmpdir=$(mktemp -d)
	if ! command -v curl >/dev/null 2>&1; then
		die "curl is required"
	fi
	curl -fsSL "$base_url/SHA256SUMS" -o "$tmpdir/SHA256SUMS"
	curl -fsSL "$base_url/$bin_name" -o "$tmpdir/$bin_name"
	(
		cd "$tmpdir"
		grep " $bin_name\$" SHA256SUMS | sha256sum -c -
	)
	bin="$tmpdir/$bin_name"
fi

echo "Will install simpled to $dest and enable the user systemd unit"
mkdir -p "$bindir"
cp "$bin" "$dest.new"
chmod 755 "$dest.new"
mv -f "$dest.new" "$dest"
"$dest" install

hc_bin=$(command -v hc || echo "${HC_INSTALL_DIR:-$HOME/.local/bin}/hc")
if [ ! -x "$hc_bin" ]; then
	echo "Installing herdr-core, which runs your agents"
	curl -fsSL https://releases.herdr.dev/core/install.sh | sh
	hc_bin="${HC_INSTALL_DIR:-$HOME/.local/bin}/hc"
fi
[ -x "$hc_bin" ] && "$hc_bin" server start >/dev/null 2>&1 || true

echo ""
case ":${PATH:-}:" in
*":$bindir:"*) pair_cmd="simpled pair" ;;
*)
	pair_cmd="$dest pair"
	echo "Note: $bindir is not on your PATH. Add it to run simpled by name."
	;;
esac
echo "Next step: run $pair_cmd and scan the QR code in the simple app on your phone."
