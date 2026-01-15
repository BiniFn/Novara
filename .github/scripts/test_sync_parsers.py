#!/usr/bin/env python3
"""
本地调试脚本 - 用于测试 sync_parsers.py 的版本更新逻辑
运行方式: 在 Kototoro 目录下执行 python .github/scripts/test_sync_parsers.py
"""
import pathlib
import re
import os
import subprocess
import sys
import json
import urllib.request
import tempfile

# 切换到 Kototoro 根目录
script_dir = pathlib.Path(__file__).parent.resolve()
repo_root = script_dir.parent.parent
os.chdir(repo_root)
print(f"工作目录: {os.getcwd()}")

# 模拟 GitHub Actions 环境变量
GITHUB_OUTPUT_FILE = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
os.environ["GITHUB_OUTPUT"] = GITHUB_OUTPUT_FILE.name
os.environ.setdefault("GITHUB_REPOSITORY", "skepsun/kototoro")  # 替换为你的实际仓库名

# 是否真正写入文件（设为 False 进行干运行）
DRY_RUN = False

file_path = pathlib.Path("gradle/libs.versions.toml")
gradle_path = pathlib.Path("app/build.gradle")

if not file_path.exists():
    print(f"❌ 错误: {file_path} 不存在")
    sys.exit(1)
if not gradle_path.exists():
    print(f"❌ 错误: {gradle_path} 不存在")
    sys.exit(1)

print(f"✅ 找到 {file_path}")
print(f"✅ 找到 {gradle_path}")

targets = {
    "parsers": "https://github.com/skepsun/kototoro-parsers.git",
    "kotatsuParsers": "https://github.com/YakaTeam/kotatsu-parsers.git",
}

text = file_path.read_text(encoding="utf-8")
updated = False
changes = []
tag_to_create = ""
new_version = ""

print("\n" + "="*50)
print("检查 parser 版本更新")
print("="*50)

for key, repo in targets.items():
    print(f"\n检查 {key}...")
    try:
        commit_full = (
            subprocess.check_output(["git", "ls-remote", repo, "HEAD"], text=True)
            .split()[0]
            .strip()
        )
    except Exception as e:
        print(f"  ❌ 获取远程提交失败: {e}")
        continue
        
    commit_short = commit_full[:10]
    pattern = rf'({re.escape(key)}\s*=\s*")([0-9a-fA-F]+)(")'
    match = re.search(pattern, text)
    if not match:
        print(f"  ❌ 在 libs.versions.toml 中未找到 {key}")
        continue
    old = match.group(2)
    print(f"  本地版本: {old}")
    print(f"  远程版本: {commit_short}")
    if old == commit_short:
        print(f"  ✅ 已是最新")
        continue
    text = re.sub(pattern, rf"\g<1>{commit_short}\g<3>", text, count=1)
    updated = True
    repo_path = repo.split("github.com/")[-1].removesuffix(".git")
    changes.append((key, repo_path, commit_full, old))
    print(f"  🔄 需要更新: {old} -> {commit_short}")

print("\n" + "="*50)
print("版本更新逻辑")
print("="*50)

if updated:
    if not DRY_RUN:
        file_path.write_text(text, encoding="utf-8")
        print(f"\n✅ 已更新 {file_path}")
    else:
        print(f"\n🔶 [DRY RUN] 将更新 {file_path}")
    
    # bump versionCode / versionName
    gradle_text = gradle_path.read_text(encoding="utf-8")
    vc_pattern = r"(versionCode\s*=\s*)(\d+)"
    vn_pattern = r"(versionName\s*=\s*['\"])([^'\"]+)(['\"])"

    vc_match = re.search(vc_pattern, gradle_text)
    vn_match = re.search(vn_pattern, gradle_text)
    
    if not vc_match:
        print(f"\n❌ 在 build.gradle 中未找到 versionCode")
        print(f"   前500字符:\n{gradle_text[:500]}")
        sys.exit(1)
    if not vn_match:
        print(f"\n❌ 在 build.gradle 中未找到 versionName")
        print(f"   前500字符:\n{gradle_text[:500]}")
        sys.exit(1)

    current_vc = int(vc_match.group(2))
    current_version = vn_match.group(2)
    print(f"\n当前版本信息:")
    print(f"  versionCode = {current_vc}")
    print(f"  versionName = '{current_version}'")
    print(f"  匹配位置: versionCode 在第 {gradle_text[:vc_match.start()].count(chr(10))+1} 行")
    print(f"  匹配位置: versionName 在第 {gradle_text[:vn_match.start()].count(chr(10))+1} 行")

    def parse_version(v):
        if not v:
            return None
        v = v.lstrip("vV")
        parts = v.split(".")
        nums = []
        for p in parts:
            if not p.isdigit():
                return None
            nums.append(int(p))
        return nums

    def cmp_versions(a, b):
        from itertools import zip_longest
        for x, y in zip_longest(a, b, fillvalue=0):
            if x < y:
                return -1
            if x > y:
                return 1
        return 0

    def latest_release_tag(repo_name):
        if not repo_name:
            print("  ⚠️ GITHUB_REPOSITORY 环境变量未设置")
            return None
        token = os.environ.get("GITHUB_TOKEN", "")
        url = f"https://api.github.com/repos/{repo_name}/releases/latest"
        print(f"\n正在获取最新 release...")
        print(f"  URL: {url}")
        req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
        if token:
            req.add_header("Authorization", f"Bearer {token}")
            print("  🔑 使用 GITHUB_TOKEN 认证")
        else:
            print("  ⚠️ 未设置 GITHUB_TOKEN（可能会遇到 API 限流）")
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                if resp.status >= 400:
                    print(f"  ❌ API 返回状态码 {resp.status}")
                    return None
                data = json.loads(resp.read().decode())
                tag = data.get("tag_name")
                print(f"  ✅ 最新 release tag: {tag}")
                return tag
        except urllib.error.HTTPError as e:
            if e.code == 404:
                print("  📝 尚无 release，将创建首个版本")
            else:
                print(f"  ❌ 获取最新 release 失败: HTTP {e.code}")
            return None
        except Exception as e:
            print(f"  ❌ 获取最新 release 失败: {e}")
            return None

    latest_tag = latest_release_tag(os.environ.get("GITHUB_REPOSITORY", ""))
    latest_version = parse_version(latest_tag) if latest_tag else None
    current_version_list = parse_version(current_version)
    
    print(f"\n版本比较:")
    print(f"  latest_tag = {latest_tag}")
    print(f"  latest_version (parsed) = {latest_version}")
    print(f"  current_version (parsed) = {current_version_list}")

    skip_bump = False
    if latest_version and current_version_list:
        cmp_result = cmp_versions(latest_version, current_version_list)
        cmp_meaning = {-1: "latest < current", 0: "latest == current", 1: "latest > current"}
        print(f"  比较结果: {cmp_result} ({cmp_meaning.get(cmp_result, 'unknown')})")
        if cmp_result < 0:
            skip_bump = True
            print(f"\n⏭️ 最新发布 {latest_tag} < 当前版本 {current_version}，跳过版本号递增")
    else:
        print("  无法比较版本（缺少数据），将执行版本递增")

    if not skip_bump:
        new_vc = current_vc + 1
        gradle_text_new = re.sub(vc_pattern, rf"\g<1>{new_vc}", gradle_text, count=1)

        parts = current_version.split(".")
        if not parts or not parts[-1].isdigit():
            print(f"\n❌ versionName '{current_version}' 尾段非数字，无法递增")
            sys.exit(1)
        parts[-1] = str(int(parts[-1]) + 1)
        new_version = ".".join(parts)
        gradle_text_new = re.sub(vn_pattern, rf"\g<1>{new_version}\g<3>", gradle_text_new, count=1)
        
        tag_to_create = f"v{new_version}"
        
        print(f"\n🎉 版本将提升:")
        print(f"  versionCode: {current_vc} -> {new_vc}")
        print(f"  versionName: '{current_version}' -> '{new_version}'")
        print(f"  将创建 tag: {tag_to_create}")
        
        if not DRY_RUN:
            gradle_path.write_text(gradle_text_new, encoding="utf-8")
            print(f"\n✅ 已成功写入 {gradle_path}")
        else:
            print(f"\n🔶 [DRY RUN] 将写入 {gradle_path}")
    else:
        print("\n⏭️ 保留现有版本，不创建 tag")

    # 模拟 GITHUB_OUTPUT
    print("\n" + "="*50)
    print("GitHub Actions 输出")
    print("="*50)
    print(f"updated=true")
    if new_version:
        print(f"version_name={new_version}")
    print(f"tag={tag_to_create}")
    
    commit_msg_lines = ["chore: sync parser commits"]
    for key, repo_path, commit_full, old in changes:
        short = commit_full[:10]
        repo_url = f"https://github.com/{repo_path}"
        commit_msg_lines.append(f"- {key}: {repo_path}@{short} ({repo_url}/commit/{commit_full})")
    print(f"commit_message=")
    for line in commit_msg_lines:
        print(f"  {line}")
else:
    print("\n✅ 所有 parser 版本均为最新，无需更新")
    print("updated=false")

# 清理临时文件
GITHUB_OUTPUT_FILE.close()
os.unlink(GITHUB_OUTPUT_FILE.name)

print("\n" + "="*50)
print(f"调试完成！DRY_RUN = {DRY_RUN}")
if DRY_RUN:
    print("如果要实际写入文件，请将脚本中的 DRY_RUN 改为 False")
print("="*50)
