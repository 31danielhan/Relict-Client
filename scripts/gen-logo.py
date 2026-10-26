#!/usr/bin/env python3
"""Writes branding/relict-logo.svg: a radar scope with a sweeping beam and a glazed donut at its centre.

Everything is plain SVG paths and gradients, so it stays sharp at any size. Edit the numbers and re-run:

    python3 scripts/gen-logo.py
"""
import math
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "branding" / "relict-logo.svg"
C = 128  # centre of the 256x256 canvas


def pt(r, deg):
    a = math.radians(deg)
    return C + r * math.cos(a), C + r * math.sin(a)


def wedge(r, a0, a1):
    x0, y0 = pt(r, a0)
    x1, y1 = pt(r, a1)
    return f"M{C},{C} L{x0:.2f},{y0:.2f} A{r},{r} 0 0 1 {x1:.2f},{y1:.2f} Z"


def scallop(base, amp, lobes, steps=180):
    """A closed wavy outline (the glaze's drippy edge) as a smooth polygon."""
    pts = []
    for i in range(steps):
        deg = 360 * i / steps
        r = base + amp * math.sin(math.radians(deg) * lobes)
        x, y = pt(r, deg)
        pts.append(f"{x:.2f},{y:.2f}")
    return "M" + " L".join(pts) + " Z"


def ring(r_out, r_in):
    """A flat ring (outer circle minus hole), drawn with the even-odd rule."""
    def circle(r):
        return f"M{C - r},{C} a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 {-2 * r},0 Z"
    return circle(r_out) + " " + circle(r_in)


# The beam: overlapping wedges that all end at the leading edge and start a little later each time, so the
# opacity builds up smoothly toward the edge with no seams between slices.
BEAM_FROM, BEAM_SPAN, SLICES = -150, 85, 34
beam = []
for i in range(SLICES):
    a0 = BEAM_FROM + BEAM_SPAN * i / SLICES
    beam.append(f'<path d="{wedge(100, a0, BEAM_FROM + BEAM_SPAN)}" fill="url(#beam)" opacity="0.032"/>')
lead_x, lead_y = pt(100, BEAM_FROM + BEAM_SPAN)

sprinkles = []
for deg, color, tilt in [(-120, "#ffffff", 20), (-60, "#ffe066", -35), (-5, "#7be0ff", 60), (50, "#ffffff", -10),
                         (110, "#a6f3c4", 40), (165, "#ffe066", -50), (205, "#7be0ff", 15)]:
    x, y = pt(31, deg)
    sprinkles.append(f'<rect x="{x - 5:.2f}" y="{y - 1.8:.2f}" width="10" height="3.6" rx="1.8" fill="{color}" '
                     f'transform="rotate({tilt} {x:.2f} {y:.2f})"/>')

bx, by = pt(86, -35)
svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <radialGradient id="bg" cx="50%" cy="42%" r="75%">
      <stop offset="0" stop-color="#1b2540"/>
      <stop offset="1" stop-color="#090c14"/>
    </radialGradient>
    <linearGradient id="beam" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#6f9bff" stop-opacity="0.15"/>
      <stop offset="1" stop-color="#4c7dff"/>
    </linearGradient>
    <radialGradient id="dough" cx="42%" cy="35%" r="75%">
      <stop offset="0" stop-color="#f2b872"/>
      <stop offset="1" stop-color="#b9692c"/>
    </radialGradient>
    <linearGradient id="glaze" x1="0" y1="0" x2="0.8" y2="1">
      <stop offset="0" stop-color="#ff9ccf"/>
      <stop offset="1" stop-color="#ff4f9a"/>
    </linearGradient>
    <radialGradient id="ping" cx="50%" cy="50%" r="50%">
      <stop offset="0" stop-color="#5dffb0" stop-opacity="0.9"/>
      <stop offset="1" stop-color="#5dffb0" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="scope"><circle cx="{C}" cy="{C}" r="100"/></clipPath>
  </defs>

  <rect width="256" height="256" rx="58" fill="url(#bg)"/>
  <rect x="1" y="1" width="254" height="254" rx="57" fill="none" stroke="#4c7dff" stroke-opacity="0.35" stroke-width="2"/>

  <g fill="none" stroke="#4c7dff" stroke-linecap="round">
    <circle cx="{C}" cy="{C}" r="100" stroke-opacity="0.55" stroke-width="3"/>
    <circle cx="{C}" cy="{C}" r="72" stroke-opacity="0.32" stroke-width="2.2"/>
    <circle cx="{C}" cy="{C}" r="48" stroke-opacity="0.22" stroke-width="2"/>
    <path d="M{C},28 V228 M28,{C} H228" stroke-opacity="0.16" stroke-width="2"/>
  </g>

  <g clip-path="url(#scope)">
    {chr(10).join("    " + b for b in beam).strip()}
  </g>
  <path d="M{C},{C} L{lead_x:.2f},{lead_y:.2f}" stroke="#9db9ff" stroke-width="2.4" stroke-linecap="round" opacity="0.95"/>

  <circle cx="{bx:.2f}" cy="{by:.2f}" r="14" fill="url(#ping)"/>
  <circle cx="{bx:.2f}" cy="{by:.2f}" r="4.2" fill="#5dffb0"/>

  <g>
    <ellipse cx="{C}" cy="{C + 4}" rx="48" ry="46" fill="#000" opacity="0.35"/>
    <path d="{ring(46, 16)}" fill="url(#dough)" fill-rule="evenodd"/>
    <path d="{scallop(40, 2.8, 9)} M{C - 17},{C} a17,17 0 1 0 34,0 a17,17 0 1 0 -34,0 Z" fill="url(#glaze)" fill-rule="evenodd"/>
    <path d="M{pt(33, -150)[0]:.2f},{pt(33, -150)[1]:.2f} A33,33 0 0 1 {pt(33, -95)[0]:.2f},{pt(33, -95)[1]:.2f}" fill="none" stroke="#fff" stroke-opacity="0.55" stroke-width="3.2" stroke-linecap="round"/>
    {chr(10).join("    " + s for s in sprinkles).strip()}
  </g>
</svg>
"""
OUT.parent.mkdir(exist_ok=True)
OUT.write_text(svg)
print(f"Wrote {OUT}")
