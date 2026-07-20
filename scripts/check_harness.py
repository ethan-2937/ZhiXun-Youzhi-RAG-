from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "harness" / "quality-baseline.json"
IGNORED_PARTS = {".git", "target", "node_modules", "dist", "coverage", "__pycache__"}
SECRET_SCAN_EXTENSIONS = {".java", ".py", ".js", ".ts", ".vue", ".yml", ".yaml", ".properties", ".json"}
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(
        r"(?i)\b(?:client|app|jwt)[_-]?secret\b\s*[:=]\s*[\"']?"
        r"(?!fictional|test|example|change-me)(?![A-Za-z_$][A-Za-z0-9_$]*\s*\()[A-Za-z0-9._~+/=-]{16,}"
    ),
    re.compile(
        r"(?i)\baccess[_-]?token\b\s*[:=]\s*[\"']?"
        r"(?!fictional|test|example)(?![A-Za-z_$][A-Za-z0-9_$]*\s*\()[A-Za-z0-9._~+/=-]{20,}"
    ),
)


def relative(path: Path, root: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def load_baseline(root: Path) -> dict[str, Any]:
    path = root / "harness" / "quality-baseline.json"
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("BASELINE_INVALID") from exc
    if not isinstance(value, dict) or value.get("version") != 1:
        raise ValueError("BASELINE_VERSION_INVALID")
    return value


def repository_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if path.is_file() and not any(part in IGNORED_PARTS for part in path.parts):
            yield path


def tracked_paths(root: Path) -> tuple[str, ...]:
    if not (root / ".git").exists():
        return ()
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        return ()
    return tuple(item.decode("utf-8", errors="replace") for item in result.stdout.split(b"\0") if item)


def check_repository(root: Path = ROOT) -> tuple[str, ...]:
    failures: list[str] = []
    try:
        config = load_baseline(root)
    except ValueError as exc:
        return (str(exc),)

    for required in config.get("requiredFiles", []):
        if not (root / required).is_file():
            failures.append(f"REQUIRED_FILE_MISSING:{required}")

    for path in (root / "harness").rglob("*.json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError):
            failures.append(f"JSON_CONTRACT_INVALID:{relative(path, root)}")

    max_agent_lines = int(config.get("agentGuideMaxLines", 120))
    for path in repository_files(root):
        if path.name == "AGENTS.md":
            count = len(path.read_text(encoding="utf-8").splitlines())
            if count > max_agent_lines:
                failures.append(f"AGENT_GUIDE_TOO_LARGE:{relative(path, root)}:{count}>{max_agent_lines}")

    defaults = config.get("defaultLineBudgets", {})
    exact_budgets = config.get("fileLineBudgets", {})
    for source_root_name in config.get("sourceRoots", []):
        source_root = root / source_root_name
        if not source_root.exists():
            continue
        for path in source_root.rglob("*"):
            if not path.is_file() or any(part in IGNORED_PARTS for part in path.parts):
                continue
            name = relative(path, root)
            budget = exact_budgets.get(name, defaults.get(path.suffix.lower()))
            if budget is None:
                continue
            count = len(path.read_text(encoding="utf-8").splitlines())
            if count > int(budget):
                failures.append(f"LINE_BUDGET_EXCEEDED:{name}:{count}>{budget}")

    forbidden_imports = tuple(str(item).lower() for item in config.get("forbiddenControllerImports", []))
    for path in root.rglob("*.java"):
        name = relative(path, root)
        if "/controller/" not in f"/{name.lower()}":
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            compact = line.strip().lower()
            if compact.startswith("import ") and any(f".{item}." in compact for item in forbidden_imports):
                failures.append(f"CONTROLLER_LAYER_VIOLATION:{name}:{line_number}")

    tracked = tracked_paths(root)
    sensitive_prefixes = tuple(str(item).replace("\\", "/") for item in config.get("sensitiveTrackedPrefixes", []))
    for name in tracked:
        normalized = name.replace("\\", "/")
        if normalized in {".env.example", "config/.env.example"}:
            continue
        if any(normalized == prefix or normalized.startswith(prefix) for prefix in sensitive_prefixes):
            failures.append(f"SENSITIVE_PATH_TRACKED:{normalized}")

    gitignore = (root / ".gitignore").read_text(encoding="utf-8") if (root / ".gitignore").is_file() else ""
    ignore_lines = {line.strip().rstrip("/") for line in gitignore.splitlines() if line.strip() and not line.startswith("#")}
    for prefix in sensitive_prefixes:
        expected = prefix.rstrip("/")
        if expected and expected not in ignore_lines:
            failures.append(f"SENSITIVE_PATH_NOT_IGNORED:{prefix}")

    scan_roots = [root / item for item in config.get("sourceRoots", [])]
    scan_roots.extend(path for path in (root / "config",) if path.exists())
    for scan_root in scan_roots:
        if not scan_root.exists():
            continue
        for path in scan_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in SECRET_SCAN_EXTENSIONS:
                continue
            for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                if any(pattern.search(line) for pattern in SECRET_PATTERNS):
                    failures.append(f"SECRET_SHAPE_FOUND:{relative(path, root)}:{line_number}")

    backend = root / "backend"
    if (backend / "pom.xml").is_file() and not any((backend / "src" / "test" / "java").rglob("*Test.java")):
        failures.append("BACKEND_TESTS_MISSING")

    frontend_package = root / "frontend" / "package.json"
    if frontend_package.is_file():
        try:
            package = json.loads(frontend_package.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError):
            failures.append("FRONTEND_PACKAGE_INVALID")
        else:
            if not package.get("scripts", {}).get("test"):
                failures.append("FRONTEND_TEST_SCRIPT_MISSING")

    return tuple(dict.fromkeys(failures))


def main() -> int:
    failures = check_repository()
    if failures:
        print(f"Harness checks failed ({len(failures)}):", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1
    print("Harness checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
