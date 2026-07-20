from __future__ import annotations

import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from import_knowledge import KnowledgeImportError, import_directory  # noqa: E402


SLIDE_XML = """<?xml version="1.0" encoding="UTF-8"?>
<root xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <a:p><a:r><a:t>{title}</a:t></a:r></a:p>
  <a:p><a:r><a:t>{body}</a:t></a:r></a:p>
</root>
"""


def write_presentation(path: Path) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("ppt/slides/slide1.xml", SLIDE_XML.format(title="虚构标题", body="虚构正文一"))
        archive.writestr("ppt/slides/slide2.xml", SLIDE_XML.format(title="第二页", body="虚构正文二"))


class KnowledgeImportTests(unittest.TestCase):
    def test_pptx_and_archive_markdown_generate_acl_bound_jsonl(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            write_presentation(source / "test-user_-_虚构分享.pptx")
            with zipfile.ZipFile(source / "export.zip", "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("bundle/report.md", "# 虚构报告\n总览\n## 虚构结论\n结论正文")
                archive.writestr("bundle/README.md", "# 构建说明\n不应进入知识文档")
                archive.writestr("bundle/dist/package-manifest.txt", "不应进入知识文档")
                archive.writestr("bundle/scripts/build.py", "print('never execute')")
            output = root / "documents.jsonl"

            summary = import_directory(
                source,
                output,
                ["test-user-demo-001"],
                "space-test",
                "虚构测试空间",
            )

            documents = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(4, summary["documents"])
            self.assertEqual(2, summary["sources"])
            self.assertEqual(3, summary["skippedEntries"])
            self.assertEqual(4, len({item["documentId"] for item in documents}))
            self.assertTrue(all(item["allowedUserIds"] == ["test-user-demo-001"] for item in documents))
            self.assertTrue(all(item["sourceFile"] in {"test-user_-_虚构分享.pptx", "export.zip"} for item in documents))
            self.assertNotIn("构建说明", output.read_text(encoding="utf-8"))

    def test_missing_acl_fails_before_writing_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            (source / "sample.md").write_text("# 虚构资料\n正文", encoding="utf-8")

            with self.assertRaisesRegex(KnowledgeImportError, "IMPORT_ACL_REQUIRED"):
                import_directory(source, root / "documents.jsonl", [], "space-test", "虚构空间")

    def test_archive_path_traversal_is_rejected_and_existing_output_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            with zipfile.ZipFile(source / "unsafe.zip", "w") as archive:
                archive.writestr("../escape.md", "# 虚构资料")
            output = root / "documents.jsonl"
            output.write_text("existing", encoding="utf-8")

            with self.assertRaisesRegex(KnowledgeImportError, "IMPORT_UNSAFE_ARCHIVE"):
                import_directory(source, output, ["test-user-1"], "space-test", "虚构空间")
            self.assertEqual("existing", output.read_text(encoding="utf-8"))

    def test_archive_entry_size_limit_is_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            with zipfile.ZipFile(source / "large.zip", "w") as archive:
                archive.writestr("report.md", "12345")

            with patch("import_knowledge.MAX_ENTRY_BYTES", 4):
                with self.assertRaisesRegex(KnowledgeImportError, "IMPORT_ARCHIVE_ENTRY_SIZE"):
                    import_directory(source, root / "documents.jsonl", ["test-user-1"], "space-test", "虚构空间")

    def test_output_payload_limit_is_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            (source / "sample.txt").write_text("完全虚构的测试正文", encoding="utf-8")

            with patch("import_knowledge.MAX_OUTPUT_BYTES", 16):
                with self.assertRaisesRegex(KnowledgeImportError, "IMPORT_OUTPUT_SIZE"):
                    import_directory(source, root / "documents.jsonl", ["test-user-1"], "space-test", "虚构空间")

    def test_unsupported_only_input_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "raw"
            source.mkdir()
            (source / "script.py").write_text("print('never execute')", encoding="utf-8")

            with self.assertRaisesRegex(KnowledgeImportError, "IMPORT_NO_DOCUMENTS"):
                import_directory(source, root / "documents.jsonl", ["test-user-1"], "space-test", "虚构空间")


if __name__ == "__main__":
    unittest.main()
