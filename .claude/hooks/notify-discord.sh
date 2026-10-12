#!/bin/bash
# Sends Claude's final reply for the turn to Discord as embeds.
# Needs DISCORD_WEBHOOK_URL in the environment; silently does nothing without it.
[ -z "$DISCORD_WEBHOOK_URL" ] && exit 0

input=$(cat)
# Avoid loops when a stop hook is already continuing the turn.
[ "$(jq -r '.stop_hook_active // false' <<<"$input")" = "true" ] && exit 0

# Newer Claude Code passes the reply directly; otherwise read it from the transcript:
# all assistant text after the last real user prompt.
reply=$(jq -r '.last_assistant_message // empty' <<<"$input")
transcript=$(jq -r '.transcript_path // empty' <<<"$input")
if [ -z "$reply" ] && [ -f "$transcript" ]; then
  reply=$(jq -rs '
    def prompt: .type == "user" and (.isMeta | not) and
      (.message.content | type == "string" or any(.[]; .type == "text"));
    (to_entries | map(select(.value | prompt) | .key) | last // -1) as $start
    | .[$start + 1:]
    | map(select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text)
    | join("\n\n")' "$transcript" 2>/dev/null)
fi
[ -z "$reply" ] && reply="(응답 내용을 찾지 못했어요)"

branch=$(git -C "$CLAUDE_PROJECT_DIR" branch --show-current 2>/dev/null)
commit=$(git -C "$CLAUDE_PROJECT_DIR" log -1 --format='%h %s' 2>/dev/null)

# Embed descriptions hold 4096 chars; split on line breaks, at most 5 messages.
jq -c -n --arg r "$reply" --arg b "${branch:-?}" --arg c "${commit:-없음}" '
  ($r | split("\n") | reduce .[] as $l ([""];
      if (.[-1] | length) + ($l | length) + 1 > 3900
      then . + [$l[0:3900]] else .[-1] += (if .[-1] == "" then "" else "\n" end) + $l end)) as $parts
  | ($parts | length) as $n
  | $parts[0:5] | to_entries[] | .key as $i
  | {embeds: [{
      color: 15105570,
      description: (.value + (if $i == 4 and $n > 5 then "\n\n…(이후 생략)" else "" end))
    }
    + (if $i == 0 then {title: "FlightSuit 작업 완료", author: {name: "Claude Code"}} else {} end)
    + (if $i == ([$n, 5] | min) - 1 then {footer: {text: "브랜치 \($b) · 마지막 커밋 \($c)"}} else {} end)
  ]}' |
while IFS= read -r payload; do
  curl -sS -m 10 -H 'Content-Type: application/json' -d "$payload" "$DISCORD_WEBHOOK_URL" >/dev/null 2>&1 || true
done
exit 0
