# Updating the OpenNOW revision and crate pins

Everything in this layer is pinned, so changing the OpenNOW revision (or
`Cargo.lock`) is a *mechanical* two-step process. This manual explains it.

## What is pinned where

| File | Holds |
|------|-------|
| `recipes-games/opennow/opennow-source.inc` | `SRCREV` + `SRC_URI` of the OpenNOW git checkout |
| `recipes-games/opennow/opennow-crates.inc` | every `crate://crates.io/<name>/<ver>` with its sha256 (`SRC_URI[<pin>.sha256sum]`) |
| `recipes-games/opennow/opennow-sdl.inc` | the SDL2 fork pins (`gitsm`, vendored the same way) |

All three are regenerated automatically from the OpenNOW checkout; you never
edit them by hand.

## Updating the revision

With an OpenNOW workspace checked out at the commit you want:

```bash
# from the layer root, pointing at your OpenNOW checkout:
python3 scripts/update-sources.py --revision <commit> [--opennow-root /path/to/OpenNOW]
```

This re-pins `opennow-source.inc` and regenerates `opennow-crates.inc` /
`opennow-sdl.inc` from that revision's `Cargo.lock` files.

## Verifying the pins are current

```bash
python3 scripts/update-sources.py --check
python3 tests/test_sources.py
```

Both fail (non-zero exit) if the layer's pins drift from the pinned revision.
They are also wired into CI.

## Why offline crates

Every crate is `crate://crates.io/<name>/<ver>` with a sha256. `do_fetch` is
the only task allowed to touch the network; the bitbake crate fetcher unpacks
each crate into `${CARGO_HOME}/bitbake` and `opennow-cargo.bbclass` writes a
cargo config that replaces `crates-io` with that directory and forces
`--frozen` offline resolution. `do_compile` therefore never touches the
network.
