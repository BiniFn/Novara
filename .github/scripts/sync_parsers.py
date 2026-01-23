#!/usr/bin/env python3
import pathlib
import re
import os
import subprocess
import sys
import json
import urllib.request

file_path = pathlib.Path("gradle/libs.versions.toml")
gradle_path = pathlib.Path("app/build.gradle")
if not file_path.exists():
    print(f"::error file={file_path}::版本文件不存在")
    sys.exit(1)
if not gradle_path.exists():
    print(f"::error file={gradle_path}::build.gradle 文件不存在")
    sys.exit(1)

targets = {
    "parsers": "https://github.com/skepsun/kototoro-parsers.git",
    "kotatsuParsers": "https://github.com/YakaTeam/kotatsu-parsers.git",
}

text = file_path.read_text(encoding="utf-8")
updated = False
changes = []
tag_to_create = ""
new_version = ""

for key, repo in targets.items():
    commit_full = (
        subprocess.check_output(["git", "ls-remote", repo, "HEAD"], text=True)
        .split()[0]
        .strip()
    )
    commit_short = commit_full[:10]
    pattern = rf'({re.escape(key)}\s*=\s*")([0-9a-fA-F]+)(")'
    match = re.search(pattern, text)
    if not match:
        print(f"::error file={file_path}::{key} 未找到")
        sys.exit(1)
    old = match.group(2)
    if old == commit_short:
        print(f"{key} 已是最新 {commit_short}")
        continue
    text = re.sub(pattern, rf"\g<1>{commit_short}\g<3>", text, count=1)
    updated = True
    repo_path = repo.split("github.com/")[-1].removesuffix(".git")
    changes.append((key, repo_path, commit_full, old))
    print(f"{key}: {old} -> {commit_short}")

if updated:
    file_path.write_text(text, encoding="utf-8")
    print("已更新 libs.versions.toml")
    
    # # bump versionCode / versionName unless latest release tag is lower than current versionName
    # gradle_text = gradle_path.read_text(encoding="utf-8")
    # # 使用更宽松的正则表达式，处理可能的空白字符
    # vc_pattern = r"(versionCode\s*=\s*)(\d+)"
    # vn_pattern = r"(versionName\s*=\s*['\"])([^'\"]+)(['\"])"

    # vc_match = re.search(vc_pattern, gradle_text)
    # vn_match = re.search(vn_pattern, gradle_text)
    
    # if not vc_match:
    #     print(f"::error file={gradle_path}::未找到 versionCode")
    #     print(f"::debug::gradle_text 前500字符: {gradle_text[:500]}")
    #     sys.exit(1)
    # if not vn_match:
    #     print(f"::error file={gradle_path}::未找到 versionName")
    #     print(f"::debug::gradle_text 前500字符: {gradle_text[:500]}")
    #     sys.exit(1)

    # current_vc = int(vc_match.group(2))
    # current_version = vn_match.group(2)
    # print(f"当前版本: versionCode={current_vc}, versionName={current_version}")

    # def parse_version(v):
    #     if not v:
    #         return None
    #     v = v.lstrip("vV")
    #     parts = v.split(".")
    #     nums = []
    #     for p in parts:
    #         if not p.isdigit():
    #             return None
    #         nums.append(int(p))
    #     return nums

    # def cmp_versions(a, b):
    #     from itertools import zip_longest
    #     for x, y in zip_longest(a, b, fillvalue=0):
    #         if x < y:
    #             return -1
    #         if x > y:
    #             return 1
    #     return 0

    # def latest_release_tag(repo_name):
    #     if not repo_name:
    #         print("::warning ::GITHUB_REPOSITORY 环境变量未设置")
    #         return None
    #     token = os.environ.get("GITHUB_TOKEN", "")
    #     url = f"https://api.github.com/repos/{repo_name}/releases/latest"
    #     print(f"正在获取最新 release: {url}")
    #     req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    #     if token:
    #         req.add_header("Authorization", f"Bearer {token}")
    #     try:
    #         with urllib.request.urlopen(req, timeout=15) as resp:
    #             if resp.status >= 400:
    #                 print(f"::warning ::API 返回状态码 {resp.status}")
    #                 return None
    #             data = json.loads(resp.read().decode())
    #             tag = data.get("tag_name")
    #             print(f"最新 release tag: {tag}")
    #             return tag
    #     except urllib.error.HTTPError as e:
    #         if e.code == 404:
    #             print("::notice ::尚无 release，将创建首个版本")
    #         else:
    #             print(f"::warning ::获取最新 release 失败: HTTP {e.code}")
    #         return None
    #     except Exception as e:
    #         print(f"::warning ::获取最新 release 失败: {e}")
    #         return None

    # latest_tag = latest_release_tag(os.environ.get("GITHUB_REPOSITORY", ""))
    # latest_version = parse_version(latest_tag) if latest_tag else None
    # current_version_list = parse_version(current_version)
    
    # print(f"版本比较: latest_tag={latest_tag}, latest_version={latest_version}, current_version_list={current_version_list}")

    # skip_bump = False
    # if latest_version and current_version_list:
    #     cmp_result = cmp_versions(latest_version, current_version_list)
    #     print(f"版本比较结果: {cmp_result} (-1: latest<current, 0: 相等, 1: latest>current)")
    #     if cmp_result < 0:
    #         skip_bump = True
    #         print(f"最新发布 {latest_tag} < 当前版本 {current_version}，跳过版本号递增和打标签")
    # else:
    #     print("无法获取最新 release 或解析版本号，将执行版本递增")

    # if not skip_bump:
    #     new_vc = int(vc_match.group(2)) + 1
    #     gradle_text = re.sub(vc_pattern, rf"\g<1>{new_vc}", gradle_text, count=1)

    #     parts = current_version.split(".")
    #     if not parts or not parts[-1].isdigit():
    #         print(f"::error file={gradle_path}::versionName {current_version} 尾段非数字，无法递增")
    #         sys.exit(1)
    #     parts[-1] = str(int(parts[-1]) + 1)
    #     new_version = ".".join(parts)
    #     gradle_text = re.sub(vn_pattern, rf"\g<1>{new_version}\g<3>", gradle_text, count=1)
    #     gradle_path.write_text(gradle_text, encoding="utf-8")
    #     tag_to_create = f"v{new_version}"
    #     print(f"版本已提升为 {new_version} (versionCode {new_vc})")
    #     print(f"已成功写入 {gradle_path}")
    # else:
    #     print("保留现有 versionCode / versionName，不创建 tag")

    out = pathlib.Path(os.environ["GITHUB_OUTPUT"])
    with out.open("a", encoding="utf-8") as fh:
        fh.write("updated=true\n")
        if new_version:
            fh.write(f"version_name={new_version}\n")
        fh.write(f"tag={tag_to_create}\n")
        lines = ["chore: sync parser commits"]
        for key, repo_path, commit_full, old in changes:
            short = commit_full[:10]
            repo_url = f"https://github.com/{repo_path}"
            lines.append(f"- {key}: {repo_path}@{short} ({repo_url}/commit/{commit_full})")
        fh.write("commit_message<<EOF\n")
        fh.write("\n".join(lines) + "\n")
        fh.write("EOF\n")
else:
    print("无需更新")
    out = pathlib.Path(os.environ["GITHUB_OUTPUT"])
    with out.open("a", encoding="utf-8") as fh:
        fh.write("updated=false\n")
