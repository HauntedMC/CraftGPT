#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
version="$(sed -n 's/.*<revision>\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)<\/revision>.*/\1/p' pom.xml | head -n 1)"
[[ -n "$version" ]] || { echo 'Missing semantic Maven revision' >&2; exit 1; }
artifact="target/CraftGPT-$version.jar"
[[ -f "$artifact" ]] || { echo "Missing distributable $artifact" >&2; exit 1; }
entries="$(jar tf "$artifact")"
descriptor="$(unzip -p "$artifact" plugin.yml)"
grep -Fxq "version: '$version'" <<<"$descriptor"
grep -Fxq 'nl/hauntedmc/craftgpt/CraftGPT.class' <<<"$entries"
grep -Fxq 'com/google/gson/Gson.class' <<<"$entries"
config="$(unzip -p "$artifact" config.yml)"
grep -Fq "\${ENV:OPENAI_API_KEY}" <<<"$config"
if grep -Eq '^(com/sk89q/worldedit/|org/bukkit/|io/papermc/paper/)' <<<"$entries"; then
  echo 'Plugin jar contains provided WorldEdit or Paper classes' >&2
  exit 1
fi
echo "Artifact audit passed: $artifact"
