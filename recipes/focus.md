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
- During those hours, hidden apps don't appear in the drawer or favorites. Put this rule in `AppFilter.kt`, so it's in
  one place and easy to change.
- Show a small line in the drawer during those hours, such as "3 apps hidden until 18:00". Hiding apps silently makes a
  phone seem broken.
- Add a way to show them anyway, one press away, lasting until they leave the launcher. This is friction, not a lock.
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
  }
}
```

## Check before finishing

Test both states. Hidden apps must reappear after the set time, and when the person chooses to show them. Keep the time
logic in a small function that takes the time as a parameter, with a unit test covering midnight and the edges of the
hours. Don't change the phone's clock.
