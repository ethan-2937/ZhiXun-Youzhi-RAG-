from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import stat
import tempfile
import unicodedata
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path, PurePosixPath
from typing import BinaryIO, Iterable
MAX_INPUT_FILES = 100
MAX_INPUT_BYTES = 50 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 1_000
MAX_ARCHIVE_BYTES = 100 * 1024 * 1024
MAX_ENTRY_BYTES = 10 * 1024 * 1024
MAX_COMPRESSION_RATIO = 100
MAX_DOCUMENTS = 200
MAX_DOCUMENT_CHARS = 100_000
MAX_OUTPUT_BYTES = 5 * 1024 * 1024
SUPPORTED_FILES = {".md", ".pptx", ".txt", ".zip"}
SUPPORTED_ARCHIVE_DOCUMENTS = {".md", ".pptx", ".txt"}
IGNORED_ARCHIVE_NAMES = {"manifest.txt", "package-manifest.txt", "readme.md", "readme.txt"}
PRESENTATION_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
PARAGRAPH_TAG = f"{{{PRESENTATION_NS}}}p"
TEXT_TAG = f"{{{PRESENTATION_NS}}}t"
HEADING_RE = re.compile(r"^(#{1,3})\s+(.+?)\s*$")
IMAGE_RE = re.compile(r"!\[([^\]]*)\]\([^)]*\)")
LINK_RE = re.compile(r"\[([^\]]+)\]\([^)]*\)")
HTML_RE = re.compile(r"<[^>]+>")
UPLOADER_RE = re.compile(r"^.{1,40}?_-_\s*")
class KnowledgeImportError(ValueError):
    pass
@dataclass(frozen=True)
class ParsedSection:
    source_key: str
    source_title: str
    section: str
    content: str
    updated_at: str
    node_key: str
@dataclass
class ImportStats:
    sources: int = 0
    skipped_entries: int = 0
def fail(code: str) -> KnowledgeImportError:
    return KnowledgeImportError(code)


def stable_id(prefix: str, value: str) -> str:
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:24]
    return f"{prefix}-{digest}"
def clean_title(name: str) -> str:
    title = Path(name).stem.strip()
    title = UPLOADER_RE.sub("", title).replace("_", " ").strip()
    return title[:200] or "未命名资料"


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFC", value).replace("\x00", "")
    lines = [re.sub(r"[ \t]+", " ", line).strip() for line in value.splitlines()]
    return "\n".join(line for line in lines if line).strip()
def validate_user_ids(user_ids: Iterable[str]) -> list[str]:
    result = list(dict.fromkeys(value.strip() for value in user_ids if value and value.strip()))
    if not result or len(result) > 500:
        raise fail("IMPORT_ACL_REQUIRED")
    if any(len(value) > 128 or any(ord(char) < 32 for char in value) for value in result):
        raise fail("IMPORT_ACL_INVALID")
    return result


def safe_archive_name(name: str) -> PurePosixPath:
    normalized = name.replace("\\", "/")
    path = PurePosixPath(normalized)
    if not normalized or path.is_absolute() or ".." in path.parts or re.match(r"^[A-Za-z]:", normalized):
        raise fail("IMPORT_UNSAFE_ARCHIVE")
    return path
def validate_archive(archive: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    entries = archive.infolist()
    if len(entries) > MAX_ARCHIVE_ENTRIES:
        raise fail("IMPORT_ARCHIVE_ENTRY_LIMIT")
    total = 0
    seen: set[str] = set()
    for entry in entries:
        path = safe_archive_name(entry.filename)
        key = str(path).casefold()
        if key in seen:
            raise fail("IMPORT_ARCHIVE_DUPLICATE_PATH")
        seen.add(key)
        mode = entry.external_attr >> 16
        if stat.S_ISLNK(mode):
            raise fail("IMPORT_ARCHIVE_SYMLINK")
        if entry.file_size > MAX_ENTRY_BYTES:
            raise fail("IMPORT_ARCHIVE_ENTRY_SIZE")
        total += entry.file_size
        if total > MAX_ARCHIVE_BYTES:
            raise fail("IMPORT_ARCHIVE_TOTAL_SIZE")
        ratio = entry.file_size / max(entry.compress_size, 1)
        if entry.file_size > 1024 * 1024 and ratio > MAX_COMPRESSION_RATIO:
            raise fail("IMPORT_ARCHIVE_COMPRESSION_RATIO")
    return entries
def read_limited(stream: BinaryIO, expected_size: int | None = None) -> bytes:
    if expected_size is not None and expected_size > MAX_ENTRY_BYTES:
        raise fail("IMPORT_ENTRY_TOO_LARGE")
    data = stream.read(MAX_ENTRY_BYTES + 1)
    if len(data) > MAX_ENTRY_BYTES:
        raise fail("IMPORT_ENTRY_TOO_LARGE")
    return data


def decode_text(data: bytes) -> str:
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError as exception:
        raise fail("IMPORT_TEXT_NOT_UTF8") from exception
def markdown_sections(text: str, source_key: str, name: str, updated_at: str) -> list[ParsedSection]:
    text = IMAGE_RE.sub(lambda match: f"图示：{match.group(1)}" if match.group(1).strip() else "", text)
    text = LINK_RE.sub(r"\1", text)
    text = HTML_RE.sub("", text)
    lines = text.splitlines()
    headings = [(index, match.group(2).strip()) for index, line in enumerate(lines) if (match := HEADING_RE.match(line))]
    title = clean_title(name)
    if headings:
        title = headings[0][1][:200]
    boundaries = [item[0] for item in headings] or [0]
    sections: list[ParsedSection] = []
    for position, start in enumerate(boundaries):
        end = boundaries[position + 1] if position + 1 < len(boundaries) else len(lines)
        content = normalize_text("\n".join(lines[start:end]))
        if not content:
            continue
        section = headings[position][1] if headings else title
        section_key = f"{source_key}#section-{position + 1}"
        sections.append(section_record(section_key, title, section, content, updated_at, source_key))
    return sections
def text_section(text: str, source_key: str, name: str, updated_at: str) -> list[ParsedSection]:
    content = normalize_text(text)
    if not content:
        return []
    title = clean_title(name)
    return [section_record(source_key, title, title, content, updated_at, source_key)]
def presentation_sections(
    source: Path | io.BytesIO, source_key: str, name: str, updated_at: str
) -> list[ParsedSection]:
    try:
        with zipfile.ZipFile(source) as archive:
            entries = validate_archive(archive)
            slide_entries = sorted(
                (entry for entry in entries if re.fullmatch(r"ppt/slides/slide\d+\.xml", entry.filename)),
                key=lambda entry: int(re.search(r"slide(\d+)", entry.filename).group(1)),
            )
            if not slide_entries:
                raise fail("IMPORT_PPTX_NO_SLIDES")
            title = clean_title(name)
            sections: list[ParsedSection] = []
            for entry in slide_entries:
                slide_number = int(re.search(r"slide(\d+)", entry.filename).group(1))
                with archive.open(entry) as stream:
                    data = read_limited(stream, entry.file_size)
                paragraphs = parse_slide_paragraphs(data)
                content = normalize_text("\n".join(paragraphs))
                if not content:
                    continue
                section = f"第 {slide_number} 页 · {paragraphs[0]}"[:200]
                slide_key = f"{source_key}#slide-{slide_number}"
                sections.append(section_record(slide_key, title, section, content, updated_at, source_key))
            return sections
    except zipfile.BadZipFile as exception:
        raise fail("IMPORT_PPTX_INVALID") from exception
    except (EOFError, OSError, RuntimeError) as exception:
        raise fail("IMPORT_PPTX_READ_FAILED") from exception
def parse_slide_paragraphs(data: bytes) -> list[str]:
    import xml.etree.ElementTree as element_tree

    try:
        root = element_tree.fromstring(data)
    except element_tree.ParseError as exception:
        raise fail("IMPORT_PPTX_XML_INVALID") from exception
    paragraphs = []
    for paragraph in root.iter(PARAGRAPH_TAG):
        text = "".join(node.text or "" for node in paragraph.iter(TEXT_TAG)).strip()
        if text:
            paragraphs.append(text)
    return paragraphs
def section_record(
    source_key: str, title: str, section: str, content: str, updated_at: str, node_key: str
) -> ParsedSection:
    if len(content) > MAX_DOCUMENT_CHARS:
        raise fail("IMPORT_DOCUMENT_CHAR_LIMIT")
    return ParsedSection(source_key, title[:200], section[:200], content, updated_at, node_key)
def parse_archive(path: Path, source_key: str, updated_at: str, stats: ImportStats) -> list[ParsedSection]:
    try:
        with zipfile.ZipFile(path) as archive:
            entries = validate_archive(archive)
            sections: list[ParsedSection] = []
            for entry in entries:
                if entry.is_dir():
                    continue
                entry_path = safe_archive_name(entry.filename)
                suffix = entry_path.suffix.lower()
                if suffix not in SUPPORTED_ARCHIVE_DOCUMENTS or entry_path.name.casefold() in IGNORED_ARCHIVE_NAMES:
                    stats.skipped_entries += 1
                    continue
                with archive.open(entry) as stream:
                    data = read_limited(stream, entry.file_size)
                inner_key = f"{source_key}!{entry_path.as_posix()}"
                if suffix == ".pptx":
                    sections.extend(presentation_sections(io.BytesIO(data), inner_key, entry_path.name, updated_at))
                elif suffix == ".md":
                    sections.extend(markdown_sections(decode_text(data), inner_key, entry_path.name, updated_at))
                else:
                    sections.extend(text_section(decode_text(data), inner_key, entry_path.name, updated_at))
            return sections
    except zipfile.BadZipFile as exception:
        raise fail("IMPORT_ARCHIVE_INVALID") from exception
    except (EOFError, OSError, RuntimeError) as exception:
        raise fail("IMPORT_ARCHIVE_READ_FAILED") from exception
def parse_file(path: Path, root: Path, stats: ImportStats) -> list[ParsedSection]:
    if path.stat().st_size <= 0 or path.stat().st_size > MAX_INPUT_BYTES:
        raise fail("IMPORT_INPUT_SIZE")
    source_key = path.relative_to(root).as_posix()
    updated_at = datetime.fromtimestamp(path.stat().st_mtime).date().isoformat()
    suffix = path.suffix.lower()
    if suffix == ".pptx":
        return presentation_sections(path, source_key, path.name, updated_at)
    if suffix == ".zip":
        return parse_archive(path, source_key, updated_at, stats)
    if path.stat().st_size > MAX_ENTRY_BYTES:
        raise fail("IMPORT_ENTRY_TOO_LARGE")
    data = path.read_bytes()
    if suffix == ".md":
        return markdown_sections(decode_text(data), source_key, path.name, updated_at)
    return text_section(decode_text(data), source_key, path.name, updated_at)
def build_document(section: ParsedSection, space_id: str, space_name: str, user_ids: list[str]) -> dict[str, object]:
    source_file = section.source_key.split("!", 1)[0].split("#", 1)[0]
    return {
        "documentId": stable_id("doc", section.source_key),
        "title": section.source_title,
        "spaceId": space_id,
        "spaceName": space_name,
        "nodeId": stable_id("node", section.node_key),
        "nodeName": section.source_title[:100],
        "section": section.section,
        "updatedAt": section.updated_at,
        "content": section.content,
        "sourceFile": source_file,
        "sourceFormat": Path(source_file).suffix.removeprefix(".").lower(),
        "allowedUserIds": user_ids,
    }
def import_directory(
    input_dir: Path, output: Path, allowed_user_ids: Iterable[str], space_id: str, space_name: str
) -> dict[str, int]:
    root = input_dir.resolve()
    if not root.is_dir():
        raise fail("IMPORT_INPUT_DIRECTORY_MISSING")
    user_ids = validate_user_ids(allowed_user_ids)
    if not space_id.strip() or len(space_id.strip()) > 64 or not space_name.strip() or len(space_name.strip()) > 100:
        raise fail("IMPORT_SPACE_INVALID")
    all_paths = sorted(root.rglob("*"))
    if any(path.is_symlink() for path in all_paths):
        raise fail("IMPORT_INPUT_SYMLINK")
    files = [path for path in all_paths if path.is_file()]
    if len(files) > MAX_INPUT_FILES:
        raise fail("IMPORT_INPUT_FILE_LIMIT")
    candidates = [path for path in files if path.suffix.lower() in SUPPORTED_FILES]
    stats = ImportStats(sources=len(candidates))
    sections = [section for path in candidates for section in parse_file(path, root, stats)]
    if not sections:
        raise fail("IMPORT_NO_DOCUMENTS")
    if len(sections) > MAX_DOCUMENTS:
        raise fail("IMPORT_DOCUMENT_LIMIT")
    documents = [build_document(item, space_id.strip(), space_name.strip(), user_ids) for item in sections]
    payload = b"".join(
        (json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        for document in documents
    )
    if len(payload) > MAX_OUTPUT_BYTES:
        raise fail("IMPORT_OUTPUT_SIZE")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".knowledge-", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(payload)
        temporary.replace(output)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    return {
        "documents": len(documents),
        "sources": stats.sources,
        "skippedEntries": stats.skipped_entries,
        "outputBytes": len(payload),
    }
def main() -> int:
    parser = argparse.ArgumentParser(description="Convert bounded local exports into the RAG JSONL contract.")
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allowed-user-id", action="append", required=True)
    parser.add_argument("--space-id", default="space-local-ai-practice")
    parser.add_argument("--space-name", default="内部 AI 实践资料")
    args = parser.parse_args()
    try:
        summary = import_directory(args.input, args.output, args.allowed_user_id, args.space_id, args.space_name)
    except KnowledgeImportError as exception:
        print(json.dumps({"status": "failed", "code": str(exception)}, ensure_ascii=False))
        return 2
    print(json.dumps({"status": "ok", **summary}, ensure_ascii=False))
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
