#!/usr/bin/env python3
"""Download recorded IMU sessions from the private Vercel Blob store.

The store is private, so reads are authenticated with the project's
BLOB_READ_WRITE_TOKEN rather than a public URL. The token is read from the web
app's .env.local, which is gitignored.

Usage:
    python analysis/fetch_sessions.py
    python analysis/fetch_sessions.py --prefix sessions/bench01/ --out data/imu-sessions
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = REPO_ROOT / "frontend" / "visor-webapp" / ".env.local"
WEBAPP_DIR = REPO_ROOT / "frontend" / "visor-webapp"
DEFAULT_OUT = REPO_ROOT / "data" / "imu-sessions"

TOKEN_PATTERN = re.compile(r'^BLOB_READ_WRITE_TOKEN\s*=\s*"?([^"\r\n]+)"?', re.MULTILINE)


def read_token() -> str:
    if not ENV_FILE.exists():
        sys.exit(
            f"No {ENV_FILE} found.\n"
            "Run:  cd frontend/visor-webapp && vercel env pull .env.local"
        )
    match = TOKEN_PATTERN.search(ENV_FILE.read_text(encoding="utf-8"))
    if not match:
        sys.exit(f"BLOB_READ_WRITE_TOKEN not present in {ENV_FILE}")
    return match.group(1).strip()


def cli_env() -> dict[str, str]:
    """Environment for the Vercel CLI.

    VERCEL_OIDC_TOKEN must be removed: the CLI refuses to run when it is set
    without a matching BLOB_STORE_ID, and we are authenticating with the
    read-write token instead. MSYS_NO_PATHCONV stops Git Bash on Windows from
    rewriting /-prefixed arguments into Windows paths.
    """
    env = dict(os.environ)
    env.pop("VERCEL_OIDC_TOKEN", None)
    env["MSYS_NO_PATHCONV"] = "1"
    return env


def run_cli(args: list[str], token: str) -> str:
    """Run a Vercel CLI command and return everything it printed.

    The CLI writes its result tables to stderr, not stdout, so both streams are
    returned combined. Reading stdout alone silently yields nothing and looks
    exactly like an empty store.
    """
    result = subprocess.run(
        ["vercel", *args, "--rw-token", token],
        cwd=WEBAPP_DIR, env=cli_env(),
        capture_output=True, text=True, shell=(os.name == "nt"),
    )
    if result.returncode != 0:
        sys.exit(f"vercel {' '.join(args)} failed:\n{result.stderr or result.stdout}")
    return (result.stdout or "") + "\n" + (result.stderr or "")


def list_pathnames(token: str, prefix: str, limit: int) -> list[str]:
    """Parse blob pathnames out of the CLI's table output.

    The table has no machine-readable mode, but every data row ends with the
    blob URL and carries the pathname immediately before it, so parsing from
    the right is stable even if the leading columns change width or wording.
    """
    out = run_cli(["blob", "list", "--prefix", prefix, "--limit", str(limit)], token)
    pathnames: list[str] = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) < 2 or not parts[-1].startswith("https://"):
            continue
        pathname = parts[-2]
        if pathname.startswith(prefix):
            pathnames.append(pathname)
    return pathnames


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--prefix", default="sessions/", help="blob pathname prefix to fetch")
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="local destination directory")
    parser.add_argument("--limit", type=int, default=1000, help="max blobs to list")
    parser.add_argument("--force", action="store_true", help="re-download files already present")
    args = parser.parse_args()

    token = read_token()
    out_root = Path(args.out)
    out_root.mkdir(parents=True, exist_ok=True)

    pathnames = list_pathnames(token, args.prefix, args.limit)
    if not pathnames:
        print(f"No blobs found under prefix {args.prefix!r}.")
        return

    print(f"Found {len(pathnames)} blob(s) under {args.prefix!r}")
    fetched = skipped = 0
    for pathname in pathnames:
        # Strip the prefix so the local tree mirrors participant/trial structure.
        relative = pathname[len("sessions/"):] if pathname.startswith("sessions/") else pathname
        dest = out_root / relative
        if dest.exists() and not args.force:
            skipped += 1
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        run_cli(["blob", "get", pathname, "--access", "private", "--output", str(dest)], token)
        size = dest.stat().st_size if dest.exists() else 0
        print(f"  + {relative}  ({size/1024:.0f} KB)")
        fetched += 1

    print(f"\n{fetched} downloaded, {skipped} already present -> {out_root}")


if __name__ == "__main__":
    main()
