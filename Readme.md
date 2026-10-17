# LibLink
Android Git client (JGit) — Arabic/English, Start.io ads, optional Firebase.

1. Put your Start.io App ID in `app/src/main/res/values/config.xml`.
2. (Optional) put `google-services.json` in `app/` to enable Firebase.
3. Build: Android Studio, or push to GitHub and download the APK from Actions → Build APK.
4. In the app: Settings → add GitHub username + Token (repo scope).

## Storage
Organized projects: `Download/LibLink/<project>` (needs all-files access, otherwise the app folder).
Manual: pick any parent folder; each project gets its own sub-folder.

## Menu (like MGit)
Pull, Push, Add all to stage, Commit, Reset (HARD), Merge, Fetch, Rebase, Cherry Pick, Diff,
New File, New Directory, Add Remote, Remove Remote, Delete, Raw config, Config + in-app file browser.
