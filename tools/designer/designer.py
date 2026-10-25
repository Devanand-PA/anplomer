#!/usr/bin/env python3
"""Anplomer designer: a local web GUI for gameplay stats and SVG -> Java builds.

Usage:
    python3 tools/designer/designer.py                     # serve the repo on :8765
    python3 tools/designer/designer.py --port 9000         # another port
    python3 tools/designer/designer.py --root some/dir     # another project root
    python3 tools/designer/designer.py --no-browser        # don't open a tab
    python3 tools/designer/designer.py --check             # run the selftest

The server binds 127.0.0.1 only. `--check` runs selftest.py in-process and
exits non-zero when any check fails.

Exit codes: 0 ok, 2 bad usage, 3 port already in use / bind failure.
"""
from __future__ import annotations

import argparse
import os
import sys
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="designer.py",
        description="Local web GUI for stats editing and SVG -> Java builds.")
    parser.add_argument("--root", default=DEFAULT_ROOT,
                        help="project root (default: this repository)")
    parser.add_argument("--port", type=int, default=8765,
                        help="port to listen on (default: 8765)")
    parser.add_argument("--no-browser", action="store_true",
                        help="do not try to open a browser tab")
    parser.add_argument("--check", action="store_true",
                        help="run the selftest and exit")
    args = parser.parse_args(argv)

    sys.path.insert(0, HERE)
    if args.check:
        import selftest
        return selftest.main([])

    if not os.path.isdir(os.path.join(args.root, "art")):
        print(f"error: {args.root} has no art/ directory; is it a project root?",
              file=sys.stderr)
        return 2

    from server import make_server
    try:
        httpd = make_server(args.root, args.port)
    except OSError as exc:
        print(f"error: cannot bind port {args.port}: {exc}", file=sys.stderr)
        return 3

    url = f"http://127.0.0.1:{httpd.server_address[1]}"
    print(f"designer serving {httpd.project.root}")
    print(f"open  {url}  (Ctrl-C to stop)")
    if not args.no_browser:
        try:
            webbrowser.open(url)
        except Exception:
            pass  # headless box: the URL above is all the user needs
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("stopped")
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
