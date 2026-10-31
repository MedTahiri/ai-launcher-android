---
name: Minimal
for: People who want a calm phone with almost nothing on the screen
permissions: []
---

# Minimal

A home screen that's nearly empty, so opening the phone doesn't pull you into anything.

## Goals

- Black background and white text, with one accent at most.
- A huge clock, and nothing else above the favorites unless the person asks for it. Turn off the default-home banner,
  and tell them how to set it: Settings > Apps > Default apps > Home app.
- Up to 5 favorite apps. Ask which ones if you don't know.
- Clean spacing, with no extra borders or decoration.
- The drawer stays available, with search. It still shows every app, so offer to hide the loudest ones with
  `drawer.hidden`.

## Starting point

```json
{
  "name": "Minimal",
  "theme": { "mode": "dark", "background": "#000000", "accent": "#FFFFFF" },
  "home": {
    "widgets": [{ "type": "clock", "options": { "size": "huge" } }],
    "columns": 5,
    "iconSize": 48,
    "showLabels": false,
    "showDefaultPrompt": false
  },
  "drawer": { "search": true }
}
```

## Check before finishing

The time is easy to read at a glance, and the favorites are still recognizable without labels. If they aren't, turn the
labels back on.
