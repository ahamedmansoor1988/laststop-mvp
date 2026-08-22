# QuikLook Brand Book

This file is the implementation source of truth for the Android product. The supplied brand SVGs define the visual language; `canvas.png` defines the journey flow; `journey-board.png` defines settings and notification structure.

## Brand character

QuikLook is calm, protective, direct, and high-contrast. Screens use a cinematic black foundation, soft atmospheric color, oversized friendly headings, quiet secondary copy, and one unmistakable lime action.

## Name and writing

- Product name: **QuikLook** in user-facing copy.
- Uppercase `QUIKLOOK` is reserved for compact navigation labels.
- Use sentence case for titles, labels, buttons, and notifications.
- Prefer short reassuring instructions. Avoid alarmist or surveillance language.

## Typography

- Display, headings, navigation titles, CTA labels: **Momo Trust Sans**.
- Body copy, metadata, form labels, helper text: **Inter**.
- Display: Black/Bold, 32–42sp, 0.95–1.08 line-height.
- Screen heading: Bold, 28–32sp.
- Card title: Bold, 16–20sp.
- Body: Regular, 14–16sp, 1.35–1.5 line-height.
- Label/metadata: Bold, 11–13sp. Uppercase is allowed only for short section labels.

## Core colors

All values below are taken directly from the supplied SVG package.

| Token | Hex | Use |
|---|---:|---|
| Black | `#000000` | Primary app background |
| Lime | `#DBFF45` | Primary CTA, selected chips, success emphasis |
| White | `#FFFFFF` | Primary text and icons |
| Cool grey | `#B5C0C6` | Secondary text and inactive controls |
| Mid grey | `#6E6D66` | Disabled text on light/bright surfaces only |
| Warm border | `#E6E5DE` | Light-surface borders only |
| Pink light | `#FF69B2` | Pink gradient edge |
| Pink dark | `#540028` | Pink gradient center/dark surface |
| Blue light | `#2B8CFF` | Blue gradient edge |
| Blue dark | `#040B19` | Blue gradient center/dark cards |
| Green light | `#8BEF95` | Green gradient edge/success |
| Green dark | `#27422A` | Green gradient center |
| Red light | `#FF3964` | Critical gradient edge/error |
| Red dark | `#680F0F` | Critical gradient center |
| Magenta | `#EB0052` | Small accent gradient edge |
| Magenta dark | `#8E0132` | Small accent gradient center |

Do not introduce new visual colors without updating this book.

## Gradients

- Hero panels use **radial gradients**, dark at the visual center and bright at the edge.
- Approved pairs only: `#540028 → #FF69B2`, `#040B19 → #2B8CFF`, `#27422A → #8BEF95`, `#680F0F → #FF3964`, `#8E0132 → #EB0052`.
- Ambient page backgrounds may blend the approved dark endpoints through black: `#040B19 → #000000 → #540028`.
- Never place multiple equally strong gradients on one screen. One hero/ambient gradient is dominant; all other surfaces stay quiet.
- Do not use gradients on standard CTA buttons.

## Shape and spacing

- Hero panel: 30dp radius (60px in the 412px-wide source splash card is treated as the exceptional 40dp splash radius).
- Content card: 24dp radius.
- Form field/search: 18dp radius.
- Primary CTA: pill, 32–35dp radius, 60–70dp height.
- Secondary CTA: pill, minimum 48dp height.
- Chips/segmented controls: full pill radius.
- Icon tile: 12–17dp radius or circle when specified.
- Screen horizontal padding: 28–32dp onboarding; 24dp product screens.
- Minimum touch target: 48dp.

## CTA rules

- Primary: lime background, near-black text, Momo Trust Sans Bold, pill shape.
- One primary CTA per screen section.
- Destructive: red gradient or `#FF3964`; never lime.
- Secondary: dark-blue surface with white text, or text-only cool grey.
- Disabled: dark surface with `#6E6D66`; do not lower opacity until unreadable.

## Icons

- Interface icons must come from the **free Iconsax set**.
- Default style: Outline, 20–24dp, 1.5px visual stroke.
- Selected/critical state may use Filled when it improves recognition.
- Icons inherit white, cool grey, lime, or the foreground color of their surface.
- Emoji and Material icons are not permitted in production UI.
- The QuikLook brand mark is exempt; it is a logo, not an interface icon.

## Screen flow

1. Welcome
2. How it works
3. Keep places synced
4. Location permission
5. Profile
6. Journey dashboard / travel prompt
7. Destination or timer setup
8. Active journey
9. Exit checklist

Settings and notification actions follow the supplied journey board.

## Release checklist

- [ ] Every heading resolves to Momo Trust Sans.
- [ ] Every body/label style resolves to Inter.
- [ ] All colors exist in the approved token table.
- [ ] Gradients use approved pairs and hierarchy.
- [ ] All UI icons are free Iconsax assets.
- [ ] No emoji remains in production UI.
- [ ] Cards, fields, buttons, and chips use the radius scale.
- [ ] Primary CTAs are lime pills with dark text and at least 48dp touch height.
- [ ] The journey flow matches the supplied canvas.
- [ ] Settings and notifications match the supplied journey board.
- [ ] Android build succeeds and critical screens are visually checked on a real device.

## Audit record — 22 August 2026

| Check | Status | Evidence |
|---|---|---|
| Heading family | Pass | `TitleFontFamily` uses Momo Trust Sans; Material typography routes display/headline/title/CTA styles to it. |
| Body family | Pass | `BodyFontFamily` uses Inter; Material body and metadata styles route to it. |
| Color tokens | Pass | Static scan found no hard-coded Compose colors outside the approved table. |
| Gradient use | Pass | Onboarding uses approved radial pairs; product screens use the approved dark blue → black → dark pink ambient blend. |
| Icons | Pass | Emoji removed; interface icons in the implemented screens use free Iconsax Outline assets. The QuikLook mark remains the only custom brand symbol. |
| Radius system | Pass | Hero 30–40dp, cards 24dp, fields 18dp, CTA 25–35dp/pill, chips full-pill. |
| CTA style | Pass | Primary actions use solid lime, dark Momo Trust Sans text, pill geometry, and ≥48dp touch height. |
| Android build | Pass | `:app:assembleDebug` completed successfully. |
| Device rendering | Pass | Main journey setup visually inspected on Samsung SM-M066B after reinstall. |
| Complete product flow | In progress | Active journey, exit checklist, settings structure, and notification presentation still require their reference-layout implementation. |
