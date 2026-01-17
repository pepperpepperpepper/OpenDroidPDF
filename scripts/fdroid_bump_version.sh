#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat >&2 <<'EOF'
Usage:
  ./scripts/fdroid_bump_version.sh --next
  ./scripts/fdroid_bump_version.sh --version-name X.Y.Z --version-code N

What it updates:
  - platform/android/gradle.properties (opendroidpdf.versionName/versionCode)
  - platform/android/AndroidManifest.xml (android:versionName/versionCode)
  - fdroid/metadata/*.yml (CurrentVersion/CurrentVersionCode + prepends a new Builds entry)

Notes:
  - Use --next to increment versionCode by 1 and bump the patch component of versionName (X.Y.Z -> X.Y.(Z+1)).
  - If your versionName is not X.Y.Z, pass --version-name explicitly.
EOF
}

version_name=""
version_code=""
want_next="0"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --next)
      want_next="1"
      shift
      ;;
    --version-name)
      version_name="${2:-}"
      shift 2
      ;;
    --version-code)
      version_code="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[fdroid_bump_version] Unknown arg: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ "${want_next}" != "1" && ( -z "${version_name}" || -z "${version_code}" ) ]]; then
  usage
  exit 2
fi

python3 - "${ROOT_DIR}" "${want_next}" "${version_name}" "${version_code}" <<'PY'
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT_DIR = Path(sys.argv[1])
want_next = sys.argv[2] == "1"
version_name_arg = sys.argv[3].strip()
version_code_arg = sys.argv[4].strip()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def require_match(pattern: str, text: str, *, path: Path) -> None:
    if re.search(pattern, text, flags=re.MULTILINE) is None:
        raise SystemExit(f"[fdroid_bump_version] Expected pattern not found in {path}: {pattern}")


def replace_line(text: str, *, key: str, value: str, path: Path) -> str:
    pattern = rf"^(?P<k>{re.escape(key)})=.*$"
    require_match(pattern, text, path=path)
    return re.sub(pattern, rf"\g<k>={value}", text, flags=re.MULTILINE)


def replace_yaml_scalar(text: str, *, key: str, value: str, path: Path) -> str:
    pattern = rf"^(?P<k>{re.escape(key)}):\s*.*$"
    require_match(pattern, text, path=path)
    return re.sub(pattern, rf"\g<k>: {value}", text, flags=re.MULTILINE)


def parse_gradle_prop(text: str, key: str, path: Path) -> str:
    m = re.search(rf"^{re.escape(key)}=(.+)$", text, flags=re.MULTILINE)
    if not m:
        raise SystemExit(f"[fdroid_bump_version] Missing {key} in {path}")
    return m.group(1).strip()


gradle_props_path = ROOT_DIR / "platform/android/gradle.properties"
gradle_props = read_text(gradle_props_path)
current_name = parse_gradle_prop(gradle_props, "opendroidpdf.versionName", gradle_props_path)
current_code_str = parse_gradle_prop(gradle_props, "opendroidpdf.versionCode", gradle_props_path)

try:
    current_code = int(current_code_str)
except ValueError as e:
    raise SystemExit(f"[fdroid_bump_version] Invalid current versionCode '{current_code_str}' in {gradle_props_path}") from e

if want_next:
    if not version_code_arg:
        version_code = current_code + 1
    else:
        version_code = int(version_code_arg)

    if version_name_arg:
        version_name = version_name_arg
    else:
        m = re.match(r"^(\d+)\.(\d+)\.(\d+)$", current_name)
        if not m:
            raise SystemExit(
                "[fdroid_bump_version] Current versionName is not X.Y.Z; pass --version-name explicitly"
            )
        major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))
        version_name = f"{major}.{minor}.{patch + 1}"
else:
    try:
        version_code = int(version_code_arg)
    except ValueError as e:
        raise SystemExit(f"[fdroid_bump_version] Invalid --version-code '{version_code_arg}'") from e
    version_name = version_name_arg

if not version_name:
    raise SystemExit("[fdroid_bump_version] versionName must be non-empty")

if version_code <= 0:
    raise SystemExit("[fdroid_bump_version] versionCode must be > 0")

if version_code <= current_code:
    raise SystemExit(
        f"[fdroid_bump_version] Refusing to decrease/reuse versionCode: current={current_code} new={version_code}"
    )

print(f"[fdroid_bump_version] {current_name} ({current_code}) -> {version_name} ({version_code})")

# 1) platform/android/gradle.properties
gradle_props = replace_line(gradle_props, key="opendroidpdf.versionName", value=version_name, path=gradle_props_path)
gradle_props = replace_line(gradle_props, key="opendroidpdf.versionCode", value=str(version_code), path=gradle_props_path)
write_text(gradle_props_path, gradle_props)

# 2) platform/android/AndroidManifest.xml (keep in sync; Gradle also uses the properties)
manifest_path = ROOT_DIR / "platform/android/AndroidManifest.xml"
manifest = read_text(manifest_path)
require_match(r'android:versionCode="[^"]+"', manifest, path=manifest_path)
require_match(r'android:versionName="[^"]+"', manifest, path=manifest_path)
manifest = re.sub(r'android:versionCode="[^"]+"', f'android:versionCode="{version_code}"', manifest)
manifest = re.sub(r'android:versionName="[^"]+"', f'android:versionName="{version_name}"', manifest)
write_text(manifest_path, manifest)


@dataclass(frozen=True)
class BuildTemplate:
    gradle_tasks: list[str]
    output: str


templates: dict[str, BuildTemplate] = {
    "fdroid/metadata/org.opendroidpdf.yml": BuildTemplate(
        gradle_tasks=["clean", "assembleRelease"],
        output="app/build/outputs/apk/release/app-release.apk",
    ),
    "fdroid/metadata/org.opendroidpdf.officepack.yml": BuildTemplate(
        gradle_tasks=["clean", ":officepack:assembleRelease"],
        output="officepack/build/outputs/apk/release/officepack-release.apk",
    ),
    "fdroid/metadata/org.opendroidpdf.xfapack.yml": BuildTemplate(
        gradle_tasks=["clean", ":xfapack:assembleRelease"],
        output="xfapack/build/outputs/apk/release/xfapack-release.apk",
    ),
}


def prepend_build_entry(text: str, *, tpl: BuildTemplate, path: Path) -> str:
    lines = text.splitlines(keepends=True)
    builds_idx = next((i for i, ln in enumerate(lines) if ln.startswith("Builds:")), None)
    if builds_idx is None:
        raise SystemExit(f"[fdroid_bump_version] Missing 'Builds:' in {path}")

    # Parse the first existing build entry (if any).
    first_name = None
    first_code = None
    first_idx = next((i for i in range(builds_idx + 1, len(lines)) if re.match(r"^\s+-\s+versionName:\s*", lines[i])), None)
    if first_idx is not None:
        m_name = re.match(r"^\s+-\s+versionName:\s*(\S+)\s*$", lines[first_idx].strip("\n"))
        if m_name:
            first_name = m_name.group(1)
        for j in range(first_idx + 1, min(first_idx + 15, len(lines))):
            m_code = re.match(r"^\s+versionCode:\s*(\d+)\s*$", lines[j].strip("\n"))
            if m_code:
                first_code = int(m_code.group(1))
                break

    if first_name == version_name and first_code == version_code:
        return text

    entry: list[str] = []
    entry.append(f"  - versionName: {version_name}\n")
    entry.append(f"    versionCode: {version_code}\n")
    entry.append("    commit: master\n")
    entry.append("    subdir: platform/android\n")
    entry.append("    gradle:\n")
    for task in tpl.gradle_tasks:
        entry.append(f"      - {task}\n")
    entry.append(f"    output: {tpl.output}\n")

    # Insert directly after the `Builds:` line.
    lines[builds_idx + 1:builds_idx + 1] = entry
    return "".join(lines)


# 3) fdroid metadata yml files
for rel_path, tpl in templates.items():
    path = ROOT_DIR / rel_path
    text = read_text(path)
    text = replace_yaml_scalar(text, key="CurrentVersion", value=version_name, path=path)
    text = replace_yaml_scalar(text, key="CurrentVersionCode", value=str(version_code), path=path)
    text = prepend_build_entry(text, tpl=tpl, path=path)
    write_text(path, text)

print("[fdroid_bump_version] updated files:")
print(f"  - {gradle_props_path.relative_to(ROOT_DIR)}")
print(f"  - {manifest_path.relative_to(ROOT_DIR)}")
for rel_path in templates.keys():
    print(f"  - {rel_path}")
PY

