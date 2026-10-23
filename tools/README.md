# tools/ — PC-side developer tools

Nothing in this directory ships in the APK. Everything here runs on a developer
machine (no Android SDK, no Gradle) and is intentionally small, scriptable, and
callable from a future designer GUI.

## Convention

* **`tools/<domain>/<tool>.py`** for anything with more than one file.
  Single-file tools may sit directly in `tools/`.
* **Python 3, standard library only.** No pip installs — a fresh clone must be
  able to run these.
* Every tool supports **`--check`** (report, write nothing) and, where it makes
  sense, **`--json`** on stdout with human output on stderr and documented exit
  codes. That is the integration contract for the GUI.
* Tools that generate game source print the "regenerate me" command in the
  generated file header, and the generated file says **do not edit by hand**.
* Generators are deterministic: same input → byte-identical output, so
  regeneration shows up as a reviewable diff (or no diff at all).

## Inventory

| tool | what it does |
|------|--------------|
| [`run_headless_test.sh`](run_headless_test.sh) | Compiles `Renderer` + the simulation against `stub/android/graphics` and runs both headless suites. |
| [`RendererSmoke.java`](RendererSmoke.java) | Executes the real draw path on a recording `Canvas`; optional `DUMP_FRAME`/`DUMP_SHOTS` rasterisation. |
| [`HeadlessTest.java`](HeadlessTest.java) | Drives the real simulation: levels, save formats, careers, store, combat, controls. |
| [`Bench.java`](Bench.java), [`run_bench.sh`](run_bench.sh) | Frame-time benchmark using the stub `Canvas` counters. |
| [`make_levels.py`](make_levels.py) | Level source → `Levels.java`, with fairness validation. |
| [`artgen/`](artgen/README.md) | SVG → Java `Path` art pipeline (`svg2java.py`, `svgpath.py`, `selftest.py`). |
| [`dump_frames.sh`](dump_frames.sh) | Records consecutive frames on a device (ghost/trail debugging). |
| [`stub/android/graphics/`](stub/android/graphics) | Minimal `Canvas`/`Paint`/`Path`/… stand-ins so the renderer runs on a plain JVM. |

## Adding a tool

1. Create `tools/<domain>/` if the tool is more than one file.
2. Write the CLI first: positional input, `--out`, `--check`, `--json`,
   documented exit codes. The GUI designer will shell out to it.
3. If it emits game source, put `// GENERATED FILE - DO NOT EDIT` and the exact
   regenerate command in the header (see `make_levels.py`, `artgen/svg2java.py`).
4. Add a `selftest.py` or `--check` mode that fails loudly, and list the tool in
   the table above.
