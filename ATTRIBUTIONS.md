# Asset Attributions

Track here every third-party visual asset shipped in the APK. Required before Play Store launch — Apache-2.0 deps + many illustration sources require attribution in distributed product.

## Onboarding Doodle SVGs (`app/src/main/res/raw/`)

| File | Source | License | Attribution required? |
|------|--------|---------|-----------------------|
| `a_bit_more_about_you.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `doodle_logged_in.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `doodle_privacy.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `doodle_screen.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `doodle_survey.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `doodle_thank_you.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `how_much_do_you_juggle.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `notations.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `thank_you.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `what_describes_you_best.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `what_should_be_call_you.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |
| `wildlife.svg` | Streamline HQ (Figma Community) | CC BY 4.0 / Streamline Free | YES — link to streamlinehq.com |

**Figma Community file**: https://www.figma.com/community/file/1105485675187256849
**Streamline profile**: https://www.figma.com/@streamline_hq
**License terms**: https://streamlinehq.com/license

## Doodle PNGs (`app/src/main/res/drawable/`)

| File | Source | License | Attribution required? |
|------|--------|---------|-----------------------|
| `doodle_a_bit_more_about_you.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |
| `doodle_best_describes_you.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |
| `doodle_how_much_do_you_juggle.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |
| `doodle_notations.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |
| `doodle_what_should_we_call_you.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |
| `doodle_wildlife.png` | Streamline HQ (rasterized from SVG) | CC BY 4.0 / Streamline Free | YES |

## Attribution Required in App

Streamline free assets require visible attribution with a link to https://streamlinehq.com in the app.
This should be added to Settings → Legal → Open-source licenses or a dedicated "Illustration Credits" section.

## Resolved Items

1. ✅ All onboarding doodles confirmed sourced from Streamline HQ Figma Community (free / CC BY 4.0).
2. ✅ `doodle_croods.svg` — previously deleted (DreamWorks "Croods" franchise trademark risk; was unreferenced in code).

## Remaining Action Items

1. **Add Streamline attribution** to the in-app OSS Licenses / About screen with link to `https://streamlinehq.com`.
2. **Delete** `ui/doodles/Croods Sitting on Floor.svg` — still present in the doodles source directory (DreamWorks trademark risk).
3. **Verify quantity limit** — Streamline free license allows up to ~50 illustrations per project. Current usage (12 SVGs + 6 PNGs = 18 unique assets) is within limits.
