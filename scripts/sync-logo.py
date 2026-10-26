#!/usr/bin/env python3
"""Copies branding/relict-logo.svg into the in-game UI's Logo.svelte component. Run it after scripts/gen-logo.py."""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
svg = (ROOT / "branding" / "relict-logo.svg").read_text()
svg = svg.replace(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">',
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width={size} height={size} aria-hidden="true">',
)
header = (
    "<!-- Generated from branding/relict-logo.svg by scripts/sync-logo.py. -->\n"
    '<script lang="ts">\n  let { size = 22 }: { size?: number } = $props()\n</script>\n\n'
)
(ROOT / "ui" / "src" / "components" / "Logo.svelte").write_text(header + svg)
print("Wrote ui/src/components/Logo.svelte")
