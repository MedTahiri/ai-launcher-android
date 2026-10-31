---
name: Glass
for: People who want a rich, good-looking home screen over their own wallpaper
permissions: []
---

# Glass

Swipeable home pages over the wallpaper, a dock, search, a page of app groups, and icons you can arrange by dragging.

## Goals

- Use the `glass` style, with the system wallpaper as the background.
- Two cards at the top of the first page. `analog_clock` and `calendar` are a good pair.
- Choose an accent that suits the person's taste. It colors the second hand, the weekday and the cursor.
- Pick up to 5 dock apps with `home.favorites`, usually the ones they use most.
- If they only want some apps on the home pages, use `drawer.only`.
- Turn off the banner with `home.showDefaultPrompt` if they don't want it, and tell them how to set the default home app:
  Settings > Apps > Default apps > Home app.

## Starting point

```json
{
  "name": "Glass",
  "theme": { "mode": "dark", "background": "wallpaper", "accent": "#7FB3FF" },
  "home": {
    "style": "glass",
    "widgets": [{ "type": "analog_clock" }, { "type": "calendar" }],
    "columns": 4
  }
}
```

## Check before finishing

Text stays readable over their wallpaper, and icons and labels aren't cut off. Tell them how to use what isn't obvious:
long-press an app for its menu, choose "Edit home screen" to drag icons, and drop an icon at the edge of the screen to
move it to another page.
