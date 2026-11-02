#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
# Run this script from the project root after extracting ZIP into Termux.
if [ ! -f 'settings.gradle.kts' ] || [ ! -d 'app' ]; then
  echo 'ERROR: Open the rrt-neon-reader directory first.'; exit 1
fi
pkg install -y git >/dev/null
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  gh auth setup-git || true
fi
if [ -z "$(git config user.name || true)" ]; then
  echo 'ERROR: Configure git once: git config --global user.name YOUR_GITHUB_USERNAME'; exit 1
fi
if [ -z "$(git config user.email || true)" ]; then
  echo 'ERROR: Configure git once: git config --global user.email YOUR_GITHUB_EMAIL'; exit 1
fi
if [ ! -d .git ]; then git init; fi
git branch -M main
git remote remove origin >/dev/null 2>&1 || true
git remote add origin https://github.com/hald2020aa-hue/rrt.git
git add .
if ! git diff --cached --quiet; then git commit -m 'Initial RRT Neon Reader Kotlin app'; fi
git push -u origin main
echo 'Upload finished. Check GitHub Actions for debug APK.'
