from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_harness import check_repository  # noqa: E402


class RepositoryHarnessTests(unittest.TestCase):
    def test_minimal_repository_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self._write_repository(root)

            self.assertEqual((), check_repository(root))

    def test_missing_contract_and_large_agent_guide_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self._write_repository(root, required=["AGENTS.md", "docs/PRODUCT.md"], agent_limit=2)
            (root / "AGENTS.md").write_text("# Map\nline 2\nline 3\n", encoding="utf-8")

            failures = check_repository(root)

        self.assertIn("REQUIRED_FILE_MISSING:docs/PRODUCT.md", failures)
        self.assertIn("AGENT_GUIDE_TOO_LARGE:AGENTS.md:3>2", failures)

    def test_line_budget_and_controller_dependency_are_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self._write_repository(root, source_roots=["src"], default_budgets={".java": 3})
            controller = root / "src" / "main" / "java" / "example" / "controller" / "UnsafeController.java"
            controller.parent.mkdir(parents=True)
            controller.write_text(
                "package example.controller;\n"
                "import example.repository.SecretRepository;\n"
                "public class UnsafeController {\n"
                "    void run() {}\n"
                "}\n",
                encoding="utf-8",
            )

            failures = check_repository(root)

        self.assertTrue(any(item.startswith("LINE_BUDGET_EXCEEDED:") for item in failures))
        self.assertIn("CONTROLLER_LAYER_VIOLATION:src/main/java/example/controller/UnsafeController.java:2", failures)

    def test_secret_shape_reports_location_without_secret_value(self) -> None:
        secret = "A1b2C3d4E5f6G7h8I9j0"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self._write_repository(root, source_roots=["src"])
            config = root / "src" / "application.yml"
            config.parent.mkdir(parents=True)
            config.write_text(f"client_secret: {secret}\n", encoding="utf-8")

            failures = check_repository(root)

        rendered = "\n".join(failures)
        self.assertIn("SECRET_SHAPE_FOUND:src/application.yml:1", failures)
        self.assertNotIn(secret, rendered)

    def _write_repository(
        self,
        root: Path,
        *,
        required: list[str] | None = None,
        source_roots: list[str] | None = None,
        default_budgets: dict[str, int] | None = None,
        agent_limit: int = 120,
    ) -> None:
        (root / "harness").mkdir(parents=True)
        (root / "AGENTS.md").write_text("# Map\n", encoding="utf-8")
        (root / ".gitignore").write_text(".env\nconfig/.env\ndata/\n", encoding="utf-8")
        baseline = {
            "version": 1,
            "agentGuideMaxLines": agent_limit,
            "defaultLineBudgets": default_budgets or {},
            "fileLineBudgets": {},
            "sourceRoots": source_roots or [],
            "requiredFiles": required or ["AGENTS.md"],
            "sensitiveTrackedPrefixes": [".env", "config/.env", "data/"],
            "forbiddenControllerImports": ["repository", "mapper", "service.impl"],
        }
        (root / "harness" / "quality-baseline.json").write_text(
            json.dumps(baseline),
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()
