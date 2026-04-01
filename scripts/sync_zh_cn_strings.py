#!/usr/bin/env python3

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from copy import deepcopy
from pathlib import Path
from typing import Iterable
from xml.sax.saxutils import escape


REPO_ROOT = Path(__file__).resolve().parents[1]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
TARGET_DIR = RES_DIR / "values-zh-rCN"
DONOR_DIRS = [RES_DIR / "values-zh", RES_DIR / "values-zh-rTW"]
DEFAULT_BASE_FILES = (
    "strings.xml",
    "strings_discover_categories.xml",
    "strings_explore_tabs.xml",
    "strings_subtitle_settings.xml",
)

MANUAL_TRANSLATIONS = {
    "reader_translation_quality_filter_enabled": "翻译质量过滤",
    "reader_translation_quality_filter_enabled_summary": "隐藏看起来像乱码、未变化或疑似 OCR 噪声的翻译。关闭后会渲染所有非空翻译。",
    "reader_translation_bypass_hint_default": "当前漫画与目标语言一致，将跳过翻译。",
    "reader_translation_source_lang_summary": "使用“自动”可跟随详情页中显示的当前漫画或来源语言。",
    "reader_translation_quick_actions": "翻译快捷操作",
    "reader_translation_quick_change_source": "更改源语言",
    "reader_translation_quick_change_target": "更改目标语言",
    "reader_translation_quick_swap_languages": "交换源语言和目标语言",
    "reader_translation_source_lang_updated": "源语言：%1$s",
    "reader_translation_target_lang_updated": "目标语言：%1$s",
    "reader_translation_languages_swapped": "语言已交换：%1$s → %2$s",
    "reader_translation_swap_auto_unsupported": "交换前请先设置固定源语言",
    "reader_translation_render_pending": "正在生成翻译图层…",
    "discord_logout_summary": "清除已保存的 Discord Token，并退出内置 Discord 会话。",
    "reader_translation_bubble_grouping_enabled": "启发式气泡分组回退",
    "reader_translation_bubble_grouping_enabled_summary": "即使关闭此项，只要已下载可用模型，气泡检测器仍会运行。关闭后，未匹配到气泡的 OCR 文本块将按单块翻译，而不是再做启发式合并。",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="同步 values-zh-rCN 中缺失的字符串资源，优先复用现有中文资源。"
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="将可补齐的缺失项直接写入 values-zh-rCN。",
    )
    parser.add_argument(
        "--files",
        nargs="*",
        default=list(DEFAULT_BASE_FILES),
        help="要处理的基础资源文件名，默认处理主 strings 文件。",
    )
    return parser.parse_args()


def is_cjk_text(text: str) -> bool:
    return any("\u4e00" <= ch <= "\u9fff" for ch in text)


def load_strings(path: Path) -> dict[str, ET.Element]:
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    return {
        child.attrib["name"]: child
        for child in root
        if child.tag == "string" and "name" in child.attrib
    }


def build_manual_element(base: ET.Element, translated_text: str) -> ET.Element:
    elem = ET.Element("string", attrib=dict(base.attrib))
    elem.text = translated_text
    return elem


def serialize_element(elem: ET.Element) -> str:
    attr_escape_map = {'"': "&quot;"}
    attrs = "".join(
        f' {name}="{escape(value, attr_escape_map)}"'
        for name, value in elem.attrib.items()
    )
    text = escape(elem.text or "")
    return f"    <string{attrs}>{text}</string>"


def insert_before_resources_end(path: Path, lines: Iterable[str]) -> None:
    original = path.read_text(encoding="utf-8")
    marker = "</resources>"
    index = original.rfind(marker)
    if index < 0:
        raise RuntimeError(f"{path} 不是合法的 Android resources 文件")
    prefix = original[:index].rstrip()
    suffix = original[index:]
    payload = "\n".join(lines)
    updated = f"{prefix}\n{payload}\n{suffix}"
    path.write_text(updated, encoding="utf-8")


def collect_translatable_entries(
    file_name: str,
) -> tuple[list[ET.Element], list[str], list[str]]:
    base_path = RES_DIR / "values" / file_name
    target_path = TARGET_DIR / file_name
    base_map = load_strings(base_path)
    target_map = load_strings(target_path)
    donor_maps = [load_strings(donor_dir / file_name) for donor_dir in DONOR_DIRS]

    additions: list[ET.Element] = []
    unresolved: list[str] = []
    copied_from: list[str] = []

    for key, base_elem in base_map.items():
        if key in target_map:
            continue

        selected: ET.Element | None = None
        for donor_map in donor_maps:
            donor_elem = donor_map.get(key)
            if donor_elem is not None and (donor_elem.text or "").strip():
                selected = deepcopy(donor_elem)
                copied_from.append(f"{file_name}:{key} <= donor")
                break

        if selected is None:
            manual = MANUAL_TRANSLATIONS.get(key)
            if manual is not None:
                selected = build_manual_element(base_elem, manual)
                copied_from.append(f"{file_name}:{key} <= manual")

        if selected is None:
            base_text = (base_elem.text or "").strip()
            if base_text and is_cjk_text(base_text):
                selected = deepcopy(base_elem)
                copied_from.append(f"{file_name}:{key} <= source-cjk")

        if selected is None:
            unresolved.append(f"{file_name}:{key}")
            continue

        additions.append(selected)

    return additions, unresolved, copied_from


def ensure_target_file(file_name: str) -> Path:
    target_path = TARGET_DIR / file_name
    if target_path.exists():
        return target_path
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n',
        encoding="utf-8",
    )
    return target_path


def main() -> int:
    args = parse_args()
    total_added = 0
    total_unresolved: list[str] = []

    for file_name in args.files:
        additions, unresolved, copied_from = collect_translatable_entries(file_name)
        total_unresolved.extend(unresolved)
        print(f"[{file_name}] 可补齐 {len(additions)} 项，未解决 {len(unresolved)} 项")
        for item in copied_from[:20]:
            print(f"  + {item}")
        if len(copied_from) > 20:
            print(f"  ... 其余 {len(copied_from) - 20} 项省略")

        if args.apply and additions:
            target_path = ensure_target_file(file_name)
            insert_before_resources_end(
                target_path,
                [serialize_element(elem) for elem in additions],
            )
            total_added += len(additions)

    if total_unresolved:
        print("\n未自动补齐的键：")
        for item in total_unresolved:
            print(f"  - {item}")

    if args.apply:
        print(f"\n已写入 {total_added} 项到 values-zh-rCN。")
    else:
        print("\n未写入文件；使用 --apply 执行补齐。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
