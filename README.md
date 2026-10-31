# Android Launcher

**Describe your dream launcher. Your AI coding agent builds it and puts it on your phone.**

This is a small, working Android home screen made to be changed by an AI agent. Say what you want in plain words, and
your agent edits it, installs it, checks that it runs, and looks at the result.

> *"Make this a black minimalist launcher with 5 apps, a big clock and a focus timer."*

<table>
  <tr>
    <td align="center"><a href="gallery/glass/"><img src="gallery/glass/home.png" width="240" alt="Swipeable home screen with glass clock and calendar cards and a dock, over a colorful wallpaper"></a><br><b>Glass</b></td>
    <td align="center"><a href="gallery/minimal/"><img src="gallery/minimal/home.png" width="240" alt="A black home screen with a huge clock and a row of five icons"></a><br><b>Minimal</b></td>
    <td align="center"><a href="gallery/seniors/"><img src="gallery/seniors/home.png" width="240" alt="A white home screen with large app icons and a large button"></a><br><b>Seniors</b></td>
    <td align="center"><a href="gallery/focus/"><img src="gallery/focus/home.png" width="240" alt="A dark home screen with a clock and a focus timer"></a><br><b>Focus</b></td>
  </tr>
</table>

Real screenshots of launchers built by Claude Code from one sentence each, on a real phone. See their prompts in the
[gallery](gallery/).

## Try it

You need a computer, an Android phone (8.0 or newer) or emulator, and a coding agent that can run commands. Open your
agent and paste this, changing the last sentence to what you want:

> Clone github.com/MedTahiri/android-launcher, read its AGENTS.md and follow it. Build me a launcher with big buttons and
> only my phone, messages and photos apps.

The agent checks your computer first and asks before installing anything. It can walk you through turning on debugging
on your phone.

Not sure what to ask for? Try a [recipe](recipes/): [minimal](recipes/minimal.md), [seniors](recipes/seniors.md),
[focus](recipes/focus.md) or [glass](recipes/glass.md).

## What's included

- **Two styles:** `simple`, with widgets, favorites and an app drawer; and `glass`, with swipeable pages over your
  wallpaper, a dock, search, app groups, and icons you arrange by dragging.
- **One settings file:** [`launcher.json`](launcher.json) sets the style, colors, widgets, apps and more.
- **Widgets:** digital and analog clocks, calendar, battery and focus timer. A new one takes a single file.
- **Tools for agents:** [`tools/doctor.sh`](tools/doctor.sh) checks the setup, and [`tools/verify.sh`](tools/verify.sh)
  installs the launcher, looks for crashes and takes screenshots.

## Safe to try

- **Your current launcher stays installed.** This one only becomes your home screen if you choose it. To go back:
  Settings > Apps > Default apps > Home app.
- **No internet by default.** The app has no `INTERNET` permission, and [`tools/check.sh`](tools/check.sh) fails if any
  permission appears that isn't in [`permissions.allow`](permissions.allow). Your agent is told to ask you before adding
  one.
- **A way out of crashes.** If a change makes the launcher crash 3 times within 5 minutes, a basic safe mode opens,
  with your apps and a button to switch launchers.
- **Your agent runs code from this repo,** so read what you run, as with any project. Recipes are limited to design
  goals and checked for commands and links, and every change to them or to the tools needs maintainer review. That
  reduces the risk but can't remove it.

## What's been tested

| | |
|---|---|
| **Agents** | Claude Code. Others should work through `AGENTS.md` but are untested. |
| **Devices** | Samsung Galaxy A17 on Android 16. Others are untested, and older Android versions may have problems. |
| **Not tested yet** | Running as the default home screen, the app list updating after an install or removal, work profiles, large screens, emulators. |

Tried it on something else? Please open an issue with what worked and what didn't.

## Known limits

- No user-made folders, icon packs, Android widgets or notification dots yet. Only the `glass` style has drag and drop,
  and icons can't be dragged into or out of the dock.
- The focus timer makes no sound and sends no notification when it ends.
- The launcher's text is English until your agent translates it. Dates follow your phone's language.
- There's no prebuilt APK: your agent builds it, since your launcher is meant to be your own.

## Share yours

Built something good? Add it to the [gallery](gallery/) with a screenshot and the prompt you used. Before pushing code,
run `tools/check.sh`.

## License

[MIT](LICENSE)
