#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="$HOME/rrt-neon-reader"
ZIP="$HOME/storage/downloads/rrt-neon-reader-v3.zip"
if [ ! -f "$ZIP" ]; then echo "الملف غير موجود: $ZIP" >&2; exit 1; fi
if [ ! -d "$PROJECT/.git" ]; then echo "مشروع Git السابق غير موجود: $PROJECT" >&2; exit 1; fi
cd "$PROJECT"
if [ -n "$(git status --porcelain)" ]; then
  echo "لديك تغييرات محلية غير محفوظة. احفظها قبل الترقية." >&2
  git status --short
  exit 1
fi
git pull --ff-only origin main
unzip -o "$ZIP" -d "$HOME" > /dev/null
cd "$PROJECT"
git add -A
git diff --cached --check
if git diff --cached --quiet; then
  echo "الملفات مطابقة للإصدار الموجود. لا يوجد شيء لرفعه."
else
  git commit -m "RRT v0.3: save adult and unsupported sources before detection"
  git push origin main
fi
echo "تمت الترقية/الرفع. تابع البناء: https://github.com/hald2020aa-hue/rrt/actions"
