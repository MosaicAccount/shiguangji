#!/usr/bin/env python3
"""
手绘字体覆盖复核（只读，不生成字体）：
把 shiguangji-web/src 里用到的汉字，跟自托管文楷的 unicode-range 比对，
报告「缺字」——缺字会回落系统字体、造成混排，属于需要重跑 gen-handdrawn-font.py 的信号。

用法：
  python3 scripts/check-handdrawn-font-coverage.py            # 在 shiguangji-web/ 下
  python3 scripts/check-handdrawn-font-coverage.py --verbose  # 列出缺字与各片统计
"""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.dirname(HERE)
FONT_CSS = os.path.join(WEB, 'src/assets/fonts/handdrawn/lxgw-wenkai/lxgw-wenkai.css')
SRC_GLOBS = ('src/**/*.vue', 'src/**/*.ts', 'src/**/*.scss', 'src/**/*.css')


def parse_ranges(css_text: str):
    """返回 (片数, 覆盖码位集合, 核心片文件名, 核心片码位数)"""
    faces = re.findall(r'@font-face\s*\{(.*?)\}', css_text, re.S)
    covered, core_name, core_count = set(), None, 0
    for face in faces:
        src = re.search(r'url\([\'"]?([^\'")]+)', face)
        rng = re.search(r'unicode-range\s*:\s*([^;]+)', face)
        if not src or not rng:
            continue
        name = os.path.basename(src.group(1))
        cps = set()
        for part in rng.group(1).split(','):
            part = part.strip()
            m = re.fullmatch(r'U\+([0-9A-Fa-f]+)-([0-9A-Fa-f]+)', part)
            if m:
                cps.update(range(int(m.group(1), 16), int(m.group(2), 16) + 1))
                continue
            m = re.fullmatch(r'U\+([0-9A-Fa-f]+)', part)
            if m:
                cps.add(int(m.group(1), 16))
        covered |= cps
        if 'core' in name:
            core_name, core_count = name, len(cps)
    return len(faces), covered, core_name, core_count


def main() -> int:
    verbose = '--verbose' in sys.argv
    chars = set()
    for pattern in SRC_GLOBS:
        for path in glob.glob(os.path.join(WEB, pattern), recursive=True):
            if 'assets/fonts' in path:
                continue
            try:
                text = open(path, encoding='utf-8').read()
            except OSError:
                continue
            chars |= {c for c in text if '\u4e00' <= c <= '\u9fff'}

    css = open(FONT_CSS, encoding='utf-8').read()
    faces, covered, core_name, core_count = parse_ranges(css)
    missing = sorted({ord(c) for c in chars} - covered)

    print(f'源码汉字种类：{len(chars)}')
    print(f'字体片数：{faces}（核心片 {core_name}，{core_count} 码位）；总覆盖码位：{len(covered)}')
    print(f'缺字：{len(missing)}')
    if missing:
        print('缺字（会回落系统字体，需重跑 gen-handdrawn-font.py）：')
        print(''.join(chr(c) for c in missing[:200]))
        return 1
    print('结论：全站文案用字已全部覆盖，无需重跑子集。')
    if verbose:
        print(f'核心片覆盖：{core_count} 码位 / 稀疏片懒加载覆盖其余 {len(covered) - core_count} 码位')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
