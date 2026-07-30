#!/bin/sh
set -eu

PACKAGE="${PACKAGE:-com.kairo.reader.debug}"
BACKUP_ROOT="${KAIRO_BACKUP_ROOT:-$HOME/Library/Application Support/Kairo/android-device-backups}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${BACKUP_FILE:-$BACKUP_ROOT/$PACKAGE-$TIMESTAMP.tar}"
INSTALL_AFTER_CLEAN=0
CONFIRMED=0

usage() {
    cat <<USAGE
Usage: $0 --yes [--install]

Creates an off-repo backup of PACKAGE private data, then uninstalls PACKAGE so
the next install is fresh for screenshots and demos.

Options:
  --yes      Required. Confirms PACKAGE will be uninstalled after backup.
  --install  Run ./gradlew :app:installDebug after uninstalling.

Environment overrides:
  PACKAGE=$PACKAGE
  KAIRO_BACKUP_ROOT=$BACKUP_ROOT
  BACKUP_FILE=$BACKUP_FILE
USAGE
}

die() {
    printf '%s\n' "$*" >&2
    exit 1
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes)
            CONFIRMED=1
            ;;
        --install)
            INSTALL_AFTER_CLEAN=1
            ;;
        -h|--help|help)
            usage
            exit 0
            ;;
        *)
            usage
            die "Unknown option: $1"
            ;;
    esac
    shift
done

[ "$CONFIRMED" -eq 1 ] || {
    usage
    die "Refusing to uninstall without --yes."
}

command -v adb >/dev/null 2>&1 || die "adb not found on PATH."
devices="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
[ "$devices" -eq 1 ] || die "Expected exactly one connected adb device, found $devices."

adb shell pm path "$PACKAGE" >/dev/null 2>&1 || die "$PACKAGE is not installed on the device."
adb shell run-as "$PACKAGE" sh -c "pwd >/dev/null" >/dev/null 2>&1 ||
    die "run-as failed for $PACKAGE. Install a debuggable build first."

mkdir -p "$BACKUP_ROOT"
TMP_BACKUP="$BACKUP_FILE.tmp"

printf 'Stopping %s before backup...\n' "$PACKAGE"
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true

printf 'Backing up %s private data to %s...\n' "$PACKAGE" "$BACKUP_FILE"
adb exec-out run-as "$PACKAGE" sh -c '
    cd "$HOME" || exit 1
    set --
    for path in databases files shared_prefs; do
        [ -e "$path" ] && set -- "$@" "$path"
    done
    [ "$#" -gt 0 ] || exit 3
    tar -cf - "$@"
' > "$TMP_BACKUP"

[ -s "$TMP_BACKUP" ] || die "Backup produced an empty file: $TMP_BACKUP"
mv "$TMP_BACKUP" "$BACKUP_FILE"
printf '%s\n' "$BACKUP_FILE" > "$BACKUP_ROOT/latest-$PACKAGE.txt"

printf 'Uninstalling %s for a fresh next install...\n' "$PACKAGE"
adb uninstall "$PACKAGE" >/dev/null

if [ "$INSTALL_AFTER_CLEAN" -eq 1 ]; then
    printf 'Installing fresh debug build...\n'
    REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
    (cd "$REPO_ROOT" && ./gradlew :app:installDebug)
fi

printf 'Clean install prep complete.\n'
printf 'Backup saved outside the repo: %s\n' "$BACKUP_FILE"
