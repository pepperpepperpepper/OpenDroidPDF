#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Generate and upload a simple static HTML QA report via wtf-upload.

By default this collects common Genymotion smoke artifacts in the repo root:
  tmp_geny_*.png tmp_geny_*.mp4 tmp_geny_*.txt tmp_geny_*.xml tmp_geny_*.log

Usage:
  ./scripts/qa_report_upload.sh [--prefix PREFIX] [--title TITLE] [--outdir DIR] [--allow-outside-repo] [PATH|GLOB...]

Examples:
  ./scripts/qa_report_upload.sh
  ./scripts/qa_report_upload.sh tmp_geny_page_scrubber_smoke* tmp_geny_scrub_binder_android*
  ./scripts/qa_report_upload.sh --prefix qa/2026-01-26/page-scrubber/ tmp_geny_page_scrubber_manual*_scrub_record.mp4

Notes:
  - Only uploads: .png .mp4 .txt .xml .log (to avoid uploading documents by accident).
  - Prints a single URL to the uploaded index.html report.
USAGE
}

TITLE="OpenDroidPDF QA Smoke Report"
PREFIX=""
OUTDIR=""
ALLOW_OUTSIDE_REPO=0

ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --prefix)
      PREFIX="${2:-}"
      shift 2
      ;;
    --title)
      TITLE="${2:-}"
      shift 2
      ;;
    --outdir)
      OUTDIR="${2:-}"
      shift 2
      ;;
    --allow-outside-repo)
      ALLOW_OUTSIDE_REPO=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      ARGS+=("$@")
      break
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if ! command -v wtf-upload >/dev/null 2>&1; then
  echo "FAIL: wtf-upload is not in PATH" >&2
  exit 1
fi

_is_allowed_ext() {
  case "$1" in
    *.png|*.mp4|*.txt|*.xml|*.log) return 0 ;;
    *) return 1 ;;
  esac
}

_relpath() {
  python3 - "$1" "$2" <<'PY'
import os, sys
root = os.path.abspath(sys.argv[1])
path = os.path.abspath(sys.argv[2])
try:
    print(os.path.relpath(path, root))
except Exception:
    print(path)
PY
}

_sanitize_name() {
  # Avoid subdirectories/odd chars in S3 keys; keep it readable for humans.
  local rel="$1"
  rel="${rel//\\/\/}"
  rel="${rel//\//__}"
  rel="${rel// /_}"
  printf '%s' "$rel"
}

_ensure_prefix() {
  local prefix="$1"
  if [[ -z "${prefix}" ]]; then
    local gitsha
    gitsha="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || echo "nogit")"
    local date_utc
    date_utc="$(date -u +%Y/%m/%d)"
    local time_utc
    time_utc="$(date -u +%H%M%S)"
    prefix="qa/${date_utc}/${gitsha}-${time_utc}/"
  fi
  [[ "$prefix" == */ ]] || prefix="${prefix}/"
  printf '%s' "$prefix"
}

PREFIX="$(_ensure_prefix "$PREFIX")"

if [[ -z "${OUTDIR}" ]]; then
  OUTDIR="$(mktemp -d "${TMPDIR:-/tmp}/odp_qa_report_XXXXXX")"
else
  mkdir -p "$OUTDIR"
fi

STAGE_DIR="${OUTDIR}/stage"
mkdir -p "$STAGE_DIR"

declare -A seen_real=()
FILES=()

_add_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    return 0
  fi
  if ! _is_allowed_ext "$path"; then
    return 0
  fi
  local real
  real="$(python3 - "$path" <<'PY'
import os, sys
print(os.path.realpath(sys.argv[1]))
PY
)"
  if [[ -n "${seen_real[$real]+x}" ]]; then
    return 0
  fi
  seen_real["$real"]=1
  FILES+=("$real")
}

_expand_glob() {
  local pattern="$1"
  local match
  while IFS= read -r match; do
    _add_file "$match"
  done < <(compgen -G "$pattern" || true)
}

if [[ "${#ARGS[@]}" -eq 0 ]]; then
  ARGS=(
    "tmp_geny_*.png"
    "tmp_geny_*.mp4"
    "tmp_geny_*.txt"
    "tmp_geny_*.xml"
    "tmp_geny_*.log"
  )
fi

for arg in "${ARGS[@]}"; do
  if [[ -d "$arg" ]]; then
    while IFS= read -r f; do
      _add_file "$f"
    done < <(find "$arg" -maxdepth 1 -type f \( -name '*.png' -o -name '*.mp4' -o -name '*.txt' -o -name '*.xml' -o -name '*.log' \) | sort)
    continue
  fi
  if [[ -f "$arg" ]]; then
    _add_file "$arg"
    continue
  fi
  _expand_glob "$arg"
done

if [[ "${#FILES[@]}" -eq 0 ]]; then
  echo "FAIL: no matching artifacts to upload." >&2
  exit 1
fi

REPO_ROOT="$ROOT_DIR"
if git -C "$ROOT_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
  REPO_ROOT="$(git -C "$ROOT_DIR" rev-parse --show-toplevel)"
fi

declare -A staged_name_by_real=()
declare -A real_by_staged_name=()

for real in "${FILES[@]}"; do
  if [[ "${ALLOW_OUTSIDE_REPO}" -eq 0 ]]; then
    case "$real" in
      "$REPO_ROOT"/*) ;;
      *)
        echo "SKIP (outside repo): $real" >&2
        continue
        ;;
    esac
  fi

  rel="$(_relpath "$REPO_ROOT" "$real")"
  staged="$(_sanitize_name "$rel")"
  if [[ -n "${real_by_staged_name[$staged]+x}" ]]; then
    # Handle collisions by appending a counter.
    i=2
    while [[ -n "${real_by_staged_name[${staged}__${i}]+x}" ]]; do
      i=$((i + 1))
    done
    staged="${staged}__${i}"
  fi

  cp -f "$real" "${STAGE_DIR}/${staged}"
  staged_name_by_real["$real"]="$staged"
  real_by_staged_name["$staged"]="$real"
done

if [[ "${#staged_name_by_real[@]}" -eq 0 ]]; then
  echo "FAIL: no artifacts eligible for upload (repo guard may have skipped them)." >&2
  exit 1
fi

mapfile -t STAGED_FILES < <(ls -1 "$STAGE_DIR" | sort)

echo "Uploading ${#STAGED_FILES[@]} artifact(s) to prefix: ${PREFIX}" >&2

declare -A url_by_staged=()

_upload_batch() {
  local -a batch=("$@")
  local -a urls=()
  mapfile -t urls < <(wtf-upload --prefix "$PREFIX" "${batch[@]/#/${STAGE_DIR}/}")
  if [[ "${#urls[@]}" -ne "${#batch[@]}" ]]; then
    echo "FAIL: wtf-upload returned ${#urls[@]} url(s) for ${#batch[@]} file(s)" >&2
    return 1
  fi
  local idx
  for ((idx=0; idx<${#batch[@]}; idx++)); do
    url_by_staged["${batch[$idx]}"]="${urls[$idx]}"
  done
}

# Avoid very large argv lists; chunk uploads.
CHUNK=40
batch=()
for f in "${STAGED_FILES[@]}"; do
  batch+=("$f")
  if [[ "${#batch[@]}" -ge "$CHUNK" ]]; then
    _upload_batch "${batch[@]}"
    batch=()
  fi
done
if [[ "${#batch[@]}" -gt 0 ]]; then
  _upload_batch "${batch[@]}"
fi

MANIFEST_TSV="${OUTDIR}/manifest.tsv"
: >"$MANIFEST_TSV"
for staged in "${STAGED_FILES[@]}"; do
  real="${real_by_staged_name[$staged]}"
  url="${url_by_staged[$staged]}"
  rel="$(_relpath "$REPO_ROOT" "$real")"
  printf '%s\t%s\t%s\n' "$staged" "$url" "$rel" >>"$MANIFEST_TSV"
done

GIT_SHA="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || echo "nogit")"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo "nogit")"
RUN_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

INDEX_HTML="${OUTDIR}/index.html"

python3 - "$INDEX_HTML" "$TITLE" "$RUN_UTC" "$GIT_SHA" "$GIT_HEAD" "$PREFIX" "$MANIFEST_TSV" <<'PY'
import html, os, sys

index_path, title, run_utc, git_sha, git_head, prefix, manifest_tsv = sys.argv[1:]

def esc(s):
    return html.escape(s, quote=True)

def kind(name):
    name_l = name.lower()
    if name_l.endswith(".png"):
        return "png"
    if name_l.endswith(".mp4"):
        return "mp4"
    if name_l.endswith(".txt") or name_l.endswith(".log"):
        return "text"
    if name_l.endswith(".xml"):
        return "xml"
    return "other"

rows = []
with open(manifest_tsv, "r", encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n")
        if not line:
            continue
        staged, url, rel = line.split("\t", 2)
        rows.append((staged, url, rel, kind(staged)))

css = """
:root{color-scheme:light dark}
body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;margin:16px;line-height:1.35}
header{display:flex;flex-direction:column;gap:8px;margin-bottom:16px}
.meta{font-size:0.95em;opacity:0.8}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}
.card{border:1px solid rgba(128,128,128,0.35);border-radius:10px;padding:10px;overflow:hidden}
.name{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,Liberation Mono,monospace;font-size:12.5px;word-break:break-all}
.preview{margin-top:8px}
img{max-width:100%;height:auto;border-radius:6px;border:1px solid rgba(128,128,128,0.25)}
video{max-width:100%;border-radius:6px;border:1px solid rgba(128,128,128,0.25)}
details{margin-top:8px}
iframe{width:100%;height:340px;border:1px solid rgba(128,128,128,0.25);border-radius:6px}
"""

cards = []
for name, url, rel, k in rows:
    name_html = esc(name)
    url_html = esc(url)
    rel_html = esc(rel)
    card = [f'<div class="card"><div class="name"><a href="{url_html}">{name_html}</a></div>']
    card.append(f'<div class="meta">src: <span class="name">{rel_html}</span></div>')
    if k == "png":
        card.append(f'<div class="preview"><a href="{url_html}"><img loading="lazy" src="{url_html}" alt="{name_html}"></a></div>')
    elif k == "mp4":
        card.append(f'<div class="preview"><video controls preload="metadata" src="{url_html}"></video></div>')
    elif k in ("text", "xml"):
        card.append(f'<details><summary>Preview</summary><div class="preview"><iframe src="{url_html}"></iframe></div></details>')
    card.append("</div>")
    cards.append("".join(card))

doc = f"""<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)}</title>
<style>{css}</style>
<header>
  <h1 style="margin:0">{esc(title)}</h1>
  <div class="meta">Run (UTC): <span class="name">{esc(run_utc)}</span></div>
  <div class="meta">Git: <span class="name">{esc(git_sha)}</span> (<span class="name">{esc(git_head)}</span>)</div>
  <div class="meta">Prefix: <span class="name">{esc(prefix)}</span></div>
  <div class="meta">Artifacts: {len(rows)}</div>
</header>
<div class="grid">
  {''.join(cards)}
</div>
"""

with open(index_path, "w", encoding="utf-8") as f:
    f.write(doc)
PY

index_url="$(wtf-upload --prefix "$PREFIX" --content-type text/html "$INDEX_HTML")"
echo "$index_url"
