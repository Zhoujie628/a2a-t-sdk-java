#!/usr/bin/env python3
"""Static CI gate for A2A-T prompt templates and their slot JSON Schemas."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
SLOT = re.compile(r"{{\s*([^{}\s]+)\s*}}")
TASK = {"Task Description", "Task Type", "Task Target", "Task Object", "Task Context", "Constraints", "Expected Output", "Operation Type"}
NOTIFICATION = {"Subscription Description", "Notification Topic", "Subscribe Condition", "Notification Data Format", "Expected Output"}
AUTHORIZATION = {"Authorization Policy Operation Type", "Authorization Policy Operation Description", "Dynamic Network Operation Authorization Policy List", "Expected Output"}
ALIASES = {
    "任务描述": "Task Description", "任务类型": "Task Type", "任务目标": "Task Target", "任务对象": "Task Object",
    "目标对象": "Task Object", "任务上下文": "Task Context", "约束条件": "Constraints", "预期输出": "Expected Output",
    "操作类型": "Operation Type",
    "订阅描述": "Subscription Description", "通知主题": "Notification Topic", "订阅条件": "Subscribe Condition",
    "通知数据格式": "Notification Data Format", "上报通知数据格式": "Notification Data Format",
    "授权策略的操作类型": "Authorization Policy Operation Type",
    "授权策略的操作描述": "Authorization Policy Operation Description",
    "动网操作的授权策略列表": "Dynamic Network Operation Authorization Policy List",
}


def error(path: Path, line: int, rule: str, message: str) -> str:
    return f"{path}:{line}: [{rule}] {message}"


def canonical_heading(value: str) -> str:
    value = value.strip()
    if value in TASK | NOTIFICATION:
        return value
    match = re.fullmatch(r"(.+?)\s*\(([^)]+)\)", value)
    for candidate in (value,) if match is None else (match.group(1).strip(), match.group(2).strip()):
        if candidate in ALIASES:
            return ALIASES[candidate]
        if candidate in TASK | NOTIFICATION:
            return candidate
    return value


def schema_slots(path: Path, errors: list[str]) -> set[str]:
    try:
        schema = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(error(path, 1, "schema-json", f"Cannot load JSON Schema: {exc}"))
        return set()
    properties = schema.get("properties")
    if not isinstance(properties, dict):
        errors.append(error(path, 1, "schema-properties", "Schema must define an object 'properties'."))
        return set()
    names = set(properties)
    required = schema.get("required", [])
    if not isinstance(required, list) or not all(isinstance(name, str) for name in required):
        errors.append(error(path, 1, "schema-required", "Schema 'required' must be an array of strings."))
    else:
        for name in required:
            if name not in names:
                errors.append(error(path, 1, "schema-required", f"Required slot '{name}' is not in properties."))
    return names


def lint_pair(template_path: Path, schema_path: Path) -> list[str]:
    errors: list[str] = []
    slots = schema_slots(schema_path, errors)
    try:
        lines = template_path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        return [*errors, error(template_path, 1, "template-read", f"Cannot read template: {exc}")]
    headings: list[tuple[int, str]] = []
    placeholders: list[tuple[int, str]] = []
    for line_no, line in enumerate(lines, 1):
        match = HEADING.match(line)
        if match:
            if len(match.group(1)) == 2:
                headings.append((line_no, canonical_heading(match.group(2))))
        placeholders.extend((line_no, name) for name in SLOT.findall(line))
    names = [name for _, name in headings]
    if "Authorization Policy Operation Type" in names:
        profile = "authorization"
        allowed = AUTHORIZATION
        required = {"Authorization Policy Operation Type"}
    elif "Subscription Description" in names:
        profile = "notification"
        allowed = NOTIFICATION
        required = {"Subscription Description"}
    else:
        profile = "task"
        allowed = TASK
        required = {"Task Description"}
    if not headings:
        errors.append(error(template_path, 1, "instruction-missing", "Template must contain L0 instructions marked with '##'."))
    for line_no, name in headings:
        if name not in allowed:
            errors.append(error(template_path, line_no, "instruction-name", f"'{name}' is not valid for the {profile} profile."))
    for name in sorted(required - set(names)):
        errors.append(error(template_path, 1, "instruction-required", f"Missing required instruction '{name}'."))
    for name in set(names):
        occurrences = [line_no for line_no, value in headings if value == name]
        if len(occurrences) > 1:
            errors.append(error(template_path, occurrences[1], "instruction-duplicate", f"Instruction '{name}' is repeated."))
    used: set[str] = set()
    for line_no, name in placeholders:
        if name in used:
            continue
        used.add(name)
        if name not in slots:
            errors.append(error(template_path, line_no, "slot-undefined", f"Placeholder '{{{{{name}}}}}' is missing from {schema_path.name}."))
    for name in sorted(slots - used):
        errors.append(error(schema_path, 1, "slot-unused", f"Schema slot '{name}' has no template placeholder."))
    return errors


def lint_root(root: Path) -> list[str]:
    templates, slots = root / "templates", root / "slots"
    if not templates.is_dir():
        return [error(templates, 1, "resource-root", "Missing templates directory.")]
    if not slots.is_dir():
        return [error(slots, 1, "resource-root", "Missing slots directory.")]
    errors: list[str] = []
    for template_path in sorted(templates.glob("*/v1/*/*/template.md")):
        schema_path = slots / template_path.relative_to(templates).parent / "slot.json"
        if schema_path.is_file():
            errors.extend(lint_pair(template_path, schema_path))
    for schema_path in sorted(slots.glob("*/v1/*/*/slot.json")):
        template_path = templates / schema_path.relative_to(slots).parent / "template.md"
        if not template_path.is_file():
            errors.append(error(schema_path, 1, "template-missing", f"Missing paired template: {template_path}"))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resource-root", type=Path, required=True)
    errors = lint_root(parser.parse_args().resource_root)
    for item in errors:
        print(item, file=sys.stderr)
    if errors:
        print(f"A2A-T template lint failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print("A2A-T template lint passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
