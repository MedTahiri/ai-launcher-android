---
name: Seniors
for: Older relatives, or anyone who finds phones hard to see and use
permissions: []
---

# Seniors

Big, clear and hard to get wrong. Ask who it's for and which apps they really use, usually calls, messages, photos and
perhaps a video call app.

## Goals

- High contrast: dark text on a light background, with a strong accent for buttons.
- Large text, using a `textScale` of about 1.5, and large icons of 90 or more.
- Few apps, usually 6 at most, in 2 columns, always with labels.
- Every target is easy to hit, with plenty of space between targets (`home.spacing`) and a large "All apps" button.
- Few ways to go wrong: no swipe to open the drawer, and no Uninstall in the long-press menu.
- A clear clock and date. A battery reading helps, since a dead phone is a common problem.
- Rename the launcher something friendly, such as "Home".
- Keep the drawer short with `drawer.only`, listing the apps they use. Tell the person what you left out; those apps
  stay installed, and removing that setting brings them back.

## Starting point

```json
{
  "name": "Home",
  "theme": { "mode": "light", "background": "#FFFFFF", "accent": "#0B57D0", "textScale": 1.5 },
  "home": {
    "widgets": [{ "type": "clock", "options": { "size": "medium" } }, { "type": "battery" }],
    "columns": 2,
    "iconSize": 96,
    "spacing": 12,
    "drawerButton": "button",
    "swipeUpOpensDrawer": false,
    "allowUninstall": false
  },
  "drawer": { "columns": 2, "iconSize": 96 }
}
```

## Check before finishing

Nothing is cut off at this text size, including the date and the longest app label, and all favorites fit on screen
without scrolling. Translate the launcher's text if the phone isn't in English, including safe mode: this person may
see it.
