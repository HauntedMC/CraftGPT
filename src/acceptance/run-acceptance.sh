#!/usr/bin/env bash
set -euo pipefail

artifact="${CRAFTGPT_ARTIFACT:?Missing CraftGPT artifact}"
paper_version="${CRAFTGPT_PAPER_VERSION:?Missing Paper runtime version}"
paper_build="${CRAFTGPT_PAPER_BUILD:?Missing Paper runtime build}"
paper_sha256="${CRAFTGPT_PAPER_SHA256:?Missing Paper runtime checksum}"
work_directory="${CRAFTGPT_ACCEPTANCE_WORK_DIRECTORY:-$(mktemp -d)}"
keep_work_directory="${CRAFTGPT_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
paper_pid=""
paper_input_fd=""

fail() { echo "CraftGPT acceptance failure: $*" >&2; exit 1; }
wait_for_log() {
  local expected=$1 deadline=$((SECONDS + 180))
  while ((SECONDS < deadline)); do
    grep -Eq -- "$expected" "$work_directory/paper/paper.log" 2>/dev/null && return
    if grep -Eq 'Exception in thread|Error occurred while enabling|Could not load plugin' "$work_directory/paper/paper.log" 2>/dev/null; then
      fail "Paper reported a plugin failure while waiting for $expected"
    fi
    sleep 1
  done
  fail "Timed out waiting for $expected"
}
# ShellCheck cannot see that EXIT invokes this function.
# shellcheck disable=SC2317
cleanup() {
  local result=$?
  if [[ -n "$paper_input_fd" ]]; then printf 'stop\n' >&"$paper_input_fd" || true; fi
  if [[ -n "$paper_pid" ]]; then
    local deadline=$((SECONDS + 30))
    while kill -0 "$paper_pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 1; done
    kill "$paper_pid" 2>/dev/null || true
    wait "$paper_pid" 2>/dev/null || true
  fi
  if [[ "$result" -ne 0 && -f "$work_directory/paper/paper.log" ]]; then tail -n 180 "$work_directory/paper/paper.log" >&2; fi
  if [[ "$keep_work_directory" == true || -n "${CRAFTGPT_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    echo "CraftGPT acceptance logs retained in $work_directory" >&2
  else
    rm -rf "$work_directory"
  fi
  exit "$result"
}
trap cleanup EXIT

for command in curl sha256sum java; do command -v "$command" >/dev/null || fail "Missing $command"; done
[[ -f "$artifact" ]] || fail "Missing jar: $artifact"
mkdir -p "$work_directory/paper/plugins"
paper_url="https://fill-data.papermc.io/v1/objects/$paper_sha256/paper-$paper_version-$paper_build.jar"
curl --fail --silent --show-error --location --output "$work_directory/paper/paper.jar" "$paper_url"
[[ "$(sha256sum "$work_directory/paper/paper.jar" | cut -d ' ' -f1)" == "$paper_sha256" ]] || fail 'Paper runtime checksum mismatch'
worldedit_url="https://ci.enginehub.org/repository/download/bt10/29913:id/worldedit-bukkit-7.4.5-dist.jar?branch=version/7.4.x&guest=1"
worldedit_sha256="e5696a6d064b9969437a8888be91b0941148a28e0c3736de1554a00254a5d142"
curl --fail --silent --show-error --location --output "$work_directory/paper/plugins/worldedit.jar" "$worldedit_url"
[[ "$(sha256sum "$work_directory/paper/plugins/worldedit.jar" | cut -d ' ' -f1)" == "$worldedit_sha256" ]] || fail 'WorldEdit checksum mismatch'
cp "$artifact" "$work_directory/paper/plugins/CraftGPT.jar"
printf 'eula=true\n' >"$work_directory/paper/eula.txt"
printf 'server-port=0\nlevel-type=minecraft:flat\n' >"$work_directory/paper/server.properties"
mkfifo "$work_directory/paper/console.in"
(cd "$work_directory/paper" && exec "${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/java" -Xms512M -Xmx1G -jar paper.jar --nogui <console.in >paper.log 2>&1) &
paper_pid=$!
exec {paper_input_fd}>"$work_directory/paper/console.in"
wait_for_log 'Done \('
wait_for_log 'Enabling WorldEdit'
wait_for_log 'Enabling CraftGPT'
[[ -d "$work_directory/paper/plugins/CraftGPT" ]] || fail 'Plugin did not initialize its data directory'
before_lines=$(wc -l <"$work_directory/paper/paper.log")
printf 'craftgpt reload\n' >&"$paper_input_fd"
local_deadline=$((SECONDS + 45))
while ((SECONDS < local_deadline)); do
  if tail -n "+$((before_lines + 1))" "$work_directory/paper/paper.log" | grep -Eqi 'CraftGPT configuration reloaded'; then
    echo 'CraftGPT Paper boot and command smoke passed.'
    exit 0
  fi
  sleep 1
done
fail 'No response to craftgpt reload command'
