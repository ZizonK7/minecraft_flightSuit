#!/bin/bash
# Sends a Discord message when Claude finishes a turn.
# Needs DISCORD_WEBHOOK_URL in the environment; silently does nothing without it.
[ -z "$DISCORD_WEBHOOK_URL" ] && exit 0

input=$(cat)
# Avoid loops when a stop hook is already continuing the turn.
[ "$(echo "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

branch=$(git -C "$CLAUDE_PROJECT_DIR" branch --show-current 2>/dev/null)
last=$(git -C "$CLAUDE_PROJECT_DIR" log -1 --format=%s 2>/dev/null)
msg="FlightSuit 작업 턴이 끝났어요 (브랜치: ${branch:-?})"$'\n'"마지막 커밋: ${last:-없음}"

curl -sS -m 10 -H 'Content-Type: application/json' \
  -d "$(jq -n --arg c "$msg" '{content:$c}')" \
  "$DISCORD_WEBHOOK_URL" >/dev/null 2>&1 || true
exit 0
