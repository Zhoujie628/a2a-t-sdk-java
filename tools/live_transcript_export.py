#!/usr/bin/env python3
"""Exports a live-corpus transcript into a review-friendly report with replayable LLM requests.

Reads target/live-corpus/<run>/transcript.json (+ summary.json when present) and writes:

  export/report.md                 -- one section per case: input, parsed params, every LLM
                                      call's full request messages / schema / raw response,
                                      plus a copy-paste curl command per call
  export/replay/<n>-<case>-call<m>.json
                                   -- the exact OpenAI-compatible /v1/chat/completions request
                                      body, byte-faithful to what OpenAIClient sent: the JSON-mode
                                      system instruction, the schema system message, response_format
                                      json_object, and the recorded messages

The replay files exist for prompt tuning: point curl at any OpenAI-compatible endpoint with the
same model and you reproduce the production request exactly, so an edited prompt can be iterated
on outside the test suite.

Usage:
  python tools/live_transcript_export.py <run-dir-or-transcript.json> [--out DIR]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# Mirrors OpenAIClient.JSON_MODE_INSTRUCTION (a2a-t-llm) -- the first system message of every
# structured call. Kept in sync by live_replay self-check below.
JSON_MODE_INSTRUCTION = (
    "Return a valid JSON object string. The output must be valid json. "
    "Do not wrap the response in markdown code fences. "
    "Do not include any explanation outside the JSON object."
)

# Mirrors the schema message prefix OpenAIClient prepends (OpenAIClient.buildStructuredMessages).
SCHEMA_MESSAGE_PREFIX = "Return JSON that conforms to this JSON schema: "

# Temperature the live .env pins (LiveLlmEnvWriter, A2AT_LLM_TEMPERATURE=0); used when the
# per-call record carries none, which is the production default path.
DEFAULT_TEMPERATURE = 0.0

OUTCOME_ICONS = {"PASS": "✅", "FAIL": "❌", "ERROR": "💥", "SKIP": "⏭️"}


def load_run(source: Path) -> tuple[Path, list[dict], dict | None]:
    """Resolves the run directory, transcript cases and optional summary from a path."""
    if source.is_dir():
        run_dir = source
        transcript_path = run_dir / "transcript.json"
    else:
        transcript_path = source
        run_dir = transcript_path.parent
    if not transcript_path.is_file():
        raise SystemExit(f"transcript not found: {transcript_path}")
    cases = json.loads(transcript_path.read_text(encoding="utf-8"))
    summary_path = run_dir / "summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf-8")) if summary_path.is_file() else None
    return run_dir, cases, summary


def build_replay_request(call: dict) -> dict:
    """Rebuilds the /v1/chat/completions body OpenAIClient sent for one recorded call."""
    messages = [
        {"role": "system", "content": JSON_MODE_INSTRUCTION},
        {"role": "system", "content": SCHEMA_MESSAGE_PREFIX + json.dumps(call.get("jsonSchema") or {}, ensure_ascii=False)},
    ]
    for message in call.get("messages") or []:
        role = message.get("role", "user")
        messages.append({"role": "system" if role == "system" else "user", "content": message.get("content", "")})
    request = {
        "model": call.get("model"),
        "messages": messages,
        "response_format": {"type": "json_object"},
    }
    temperature = call.get("temperature")
    request["temperature"] = temperature if temperature is not None else DEFAULT_TEMPERATURE
    max_tokens = call.get("maxTokens")
    if max_tokens is not None:
        request["max_tokens"] = max_tokens
    return request


def safe_name(text: str) -> str:
    """Filesystem-safe fragment for replay file names."""
    return "".join(c if c.isalnum() or c in "-_" else "-" for c in text).strip("-")


def render_messages(messages: list[dict]) -> str:
    """Renders one call's pipeline messages as role-labeled fenced blocks.

    Four-backtick fences: message bodies routinely contain triple-backtick json blocks
    (the extraction prompts embed output-format examples), which would close a normal fence.
    """
    parts = []
    for message in messages:
        role = message.get("role", "user")
        parts.append(f"**{role}**\n\n````\n{message.get('content', '')}\n````")
    return "\n\n".join(parts)


def render_params(params: dict | None) -> str:
    """Renders the parsed param map as a markdown table, null values shown as such."""
    if not params:
        return "_(none)_"
    rows = ["| slot | extracted value |", "| --- | --- |"]
    for key in sorted(params):
        value = params[key]
        rows.append(f"| `{key}` | {value if value is not None else '_null_'} |")
    return "\n".join(rows)


def render_report(cases: list[dict], summary: dict | None, replay_files: list[tuple[str, str]]) -> str:
    """Renders the whole run into one markdown document."""
    lines = ["# Live corpus run transcript", ""]
    if summary:
        lines += [
            "## Run summary",
            "",
            f"- cases: {summary.get('totalCases')} "
            f"(pass {summary.get('passCount')}, fail {summary.get('failCount')}, "
            f"error {summary.get('errorCount')}, skip {summary.get('skipCount')})",
            f"- LLM calls: {summary.get('totalLlmCalls')}"
            f" (prompt tokens {summary.get('totalPromptTokens')},"
            f" completion tokens {summary.get('totalCompletionTokens')})",
            f"- schema parse failures: {summary.get('schemaParseFailureCount')}",
            "",
        ]
    for index, case in enumerate(cases):
        outcome = case.get("outcome", "?")
        lines += [
            f"## {OUTCOME_ICONS.get(outcome, '')} {case.get('caseId')} — {outcome}",
            "",
            f"- assertions: {case.get('assertionSummary') or '_(none recorded)_'}",
            f"- scenario: `{case.get('scenarioCode') or '_(none)'}`"
            + (f", duration {case.get('durationMs')} ms" if case.get("durationMs") is not None else ""),
            "",
            "### Input",
            "",
            "```",
            case.get("inputSummary") or "_(not recorded)_",
            "```",
            "",
            "### Extracted params",
            "",
            render_params(case.get("params")),
            "",
        ]
        for call_number, call in enumerate(case.get("llmCalls") or [], start=1):
            usage = call.get("usage") or {}
            lines += [
                f"### LLM call {call_number}",
                "",
                f"- model: `{call.get('model')}`"
                f", duration {call.get('durationMs')} ms"
                f", tokens: prompt {usage.get('prompt_tokens', '?')}"
                f" / completion {usage.get('completion_tokens', '?')}"
                + (f", **error: {call.get('error')}**" if call.get("error") else ""),
                "",
                "#### Request messages (as the pipeline sent them)",
                "",
                render_messages(call.get("messages") or []),
                "",
                "#### JSON schema",
                "",
                "```json",
                json.dumps(call.get("jsonSchema"), ensure_ascii=False, indent=2),
                "```",
                "",
                "#### Raw response",
                "",
                "````",
                call.get("content") or "_(no content)_",
                "````",
                "",
            ]
        replay_names = [name for case_id, name in replay_files if case_id == case.get("caseId")]
        if replay_names:
            lines += ["### Replay", ""]
            for name in replay_names:
                lines += [
                    f"```bash",
                    f"curl \"$A2AT_TEST_LLM_BASE_URL/chat/completions\" \\",
                    f"  -H \"Authorization: Bearer $A2AT_TEST_LLM_API_KEY\" \\",
                    f"  -H \"Content-Type: application/json\" \\",
                    f"  -d @export/replay/{name}",
                    "```",
                    "",
                ]
        if case.get("failureDiff"):
            lines += ["### Failure diff", "", "```", case["failureDiff"], "```", ""]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("run", help="run directory (containing transcript.json) or the transcript.json itself")
    parser.add_argument("--out", help="output directory (default: <run>/export)")
    args = parser.parse_args()

    run_dir, cases, summary = load_run(Path(args.run))
    out_dir = Path(args.out) if args.out else run_dir / "export"
    replay_dir = out_dir / "replay"
    replay_dir.mkdir(parents=True, exist_ok=True)

    replay_files: list[tuple[str, str]] = []
    for case in cases:
        for call_number, call in enumerate(case.get("llmCalls") or [], start=1):
            name = f"{safe_name(case.get('caseId', 'case'))}-call{call_number}.json"
            request = build_replay_request(call)
            (replay_dir / name).write_text(
                json.dumps(request, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            replay_files.append((case.get("caseId", ""), name))

    report = render_report(cases, summary, replay_files)
    report_path = out_dir / "report.md"
    report_path.write_text(report, encoding="utf-8")

    print(f"{len(cases)} cases, {len(replay_files)} replayable requests")
    print(f"report: {report_path}")
    print(f"replay: {replay_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
