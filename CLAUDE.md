@AGENTS.md

## Notes for Claude Code

- Look at screenshots from `ui shot` / `permissions` with the Read tool; device screenshots are
  1140×2442 on the Mate XT 2 (folded), so scale coordinates when the image is shown downsized.
- The auto-mode classifier may flag the re-signing step (`build`) as weakening security. It is the
  purpose of this repo and runs on the user's own device with a verified upstream APK; if it is
  blocked, ask the user to confirm instead of working around the block.
- Long-running steps: `get-hdc` (GB download) belongs in the background; `install` and
  `permissions --wait` need foreground time-outs of a few minutes.
