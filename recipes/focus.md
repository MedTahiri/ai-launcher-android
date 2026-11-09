---
name: Focus
for: People who want to stop opening distracting apps without thinking
permissions: []
---

# Focus

A launcher that adds a little friction to distractions, without blocking anything for real. The person stays in charge.

## Goals

- Ask which apps distract them, and the hours to hide them. A typical answer is social apps until 18:00. If they give
  only an end time, hide from midnight until then, every day.
- Write it as a `focus.rules` entry in `launcher.json` (AGENTS.md describes them). The launcher then hides those apps
  from the drawer, favorites and search during those hours, and says how many are hidden, with "Show".
- If they'd rather be slowed down than blocked, use `"action": "pause"`, which makes them wait a few seconds first.
- Tell the person the limits plainly: it does nothing until this is their default home screen, and apps can still be
  opened in other ways, such as from the app store or a notification.
- Put a `focus_timer` widget on the home screen.
- Use a calm, dark look without bright accent colors.

## Starting point

```json
{
  "name": "Focus",
  "theme": { "mode": "dark", "background": "#101418", "accent": "#A8C7FA" },
  "home": {
    "widgets": [{ "type": "clock" }, { "type": "focus_timer", "options": { "minutes": 25 } }],
    "columns": 4
  },
  "focus": {
    "rules": [{ "categories": ["social", "video", "game"], "from": "00:00", "to": "18:00" }]
  }
}
```

## Check before finishing

Test both states: set a rule's hours around the current time, check the apps are hidden and the note is there, then
press "Show". Don't change the phone's clock.
