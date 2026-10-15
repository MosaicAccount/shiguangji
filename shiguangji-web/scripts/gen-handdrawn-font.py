#!/usr/bin/env python3
"""
生成手绘标题字体（马善政）的自托管子集，供 /plan 手绘清单页使用。

方案（实测迭代而来，见 docs/design/plan-design.md 结论 99）：
均匀 unicode-range 分片在中文场景不划算——站点用字约 600 个但散布整个 CJK 区间，
512 码位一片也要命中 ~40 片（~1MB+）。故改为「核心片 + 稀疏片」：

  1. 核心片 ms-core.woff2：恰好包含「站点语料」用字（本脚本同目录 handdrawn-corpus.txt，
     含库内标题/字典与前台固定文案，~600 字 ≈ 100KB），unicode-range 逐字列出；
  2. 稀疏片 ms-rXXXX.woff2：其余字形按 512 码位区间成片（区间内剔除核心片已有的字，
     与核心片零重叠），空片跳过——新标题里的罕见字落到这里，按片懒加载。

语料怎么维护：库里加了带新字的内容后，重跑一遍本脚本即可（见下方「更新语料」）；
不重跑也不坏——新字回落到系统字体（font-family 兜底），只是混排。

用法（需要 fonttools + brotli）：
  python3 -m pip install --target .pylibs fonttools brotli
  curl -sL -o /tmp/LongCang-Regular.ttf \\
    https://raw.githubusercontent.com/google/fonts/main/ofl/mashanzheng/MaShanZheng-Regular.ttf
  PYTHONPATH=../../.pylibs python3 scripts/gen-handdrawn-font.py /tmp/LongCang-Regular.ttf

更新语料（把库内新标题并进核心片）：
  docker exec local-mysql mysql -ujlian -p*** --default-character-set=utf8mb4 shiguangji \\
    -N -e "select title from sgj_item union select dict_label from sys_dict_data;" \\
    >> scripts/handdrawn-corpus.txt   # 去重交给本脚本

输出：src/assets/fonts/handdrawn/longcang/lc-*.woff2 与 mashanzheng.css。
字体为 SIL OFL 1.1 授权，随包分发需附许可证，见同目录 OFL.txt。
"""
import os
import subprocess
import sys

from fontTools.ttLib import TTFont

CJK_START, CJK_END, SPARSE_STEP = 0x4E00, 0x9FFF, 512
SYMBOL_RANGES = [(0x0020, 0x007E), (0x2000, 0x206F), (0x2190, 0x21FF), (0x3000, 0x303F), (0xFF00, 0xFFEF)]


def fmt_cp(cp: int) -> str:
    return f"U+{cp:04X}"


def fmt_range(start: int, end: int) -> str:
    return f"U+{start:04X}-{end:04X}" if start != end else fmt_cp(start)


def subset(ttf: str, out: str, unicodes: list[int]) -> None:
    text = ','.join(fmt_cp(cp) for cp in unicodes)
    subprocess.run([
        sys.executable, '-m', 'fontTools.subset', ttf,
        f'--unicodes={text}', f'--output-file={out}', '--flavor=woff2',
        '--layout-features=*', '--no-hinting', '--desubroutinize',
    ], check=True)


def main(ttf_path: str) -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.normpath(os.path.join(here, '..', 'src', 'assets', 'fonts', 'handdrawn', 'mashanzheng'))
    os.makedirs(out_dir, exist_ok=True)
    for old in (f for f in os.listdir(out_dir) if f.endswith('.woff2') or f == 'mashanzheng.css'):
        os.remove(os.path.join(out_dir, old))

    cmap = TTFont(ttf_path).getBestCmap()

    corpus_path = os.path.join(here, 'handdrawn-corpus.txt')
    corpus = {ord(c) for c in open(corpus_path, encoding='utf-8').read()} & set(cmap)
    corpus |= {cp for s, e in SYMBOL_RANGES for cp in range(s, e + 1) if cp in cmap}  # 符号常备
    print(f'核心片语料：{len(corpus)} 字（含符号区）')

    faces = []

    # 1) 核心片：语料本身
    core_out = os.path.join(out_dir, 'ms-core.woff2')
    subset(ttf_path, core_out, sorted(corpus))
    core_range = ','.join(fmt_cp(cp) for cp in sorted(corpus))
    faces.append(
        f"@font-face{{font-family:'Ma Shan Zheng';font-style:normal;font-weight:400;font-display:swap;"
        f"src:url('./ms-core.woff2') format('woff2');unicode-range:{core_range};}}"
    )

    # 2) 稀疏片：其余字形按区间成片（与核心片零重叠），空片跳过。
    #    unicode-range 必须逐字列出而不是声明整段区间：浏览器按「区间与页面字符有交集」
    #    决定下载哪片，整段区间会包含核心片的字，导致稀疏片被白白拉取（实测踩过）。
    kept, sparse_total = 0, 0
    for start in range(CJK_START, CJK_END + 1, SPARSE_STEP):
        end = min(start + SPARSE_STEP - 1, CJK_END)
        cps = [cp for cp in range(start, end + 1) if cp in cmap and cp not in corpus]
        if not cps:
            continue
        name = f'ms-r{start:04X}'
        out = os.path.join(out_dir, f'{name}.woff2')
        subset(ttf_path, out, cps)
        kept += 1
        sparse_total += os.path.getsize(out)
        sparse_range = ','.join(fmt_cp(cp) for cp in cps)
        faces.append(
            f"@font-face{{font-family:'Ma Shan Zheng';font-style:normal;font-weight:400;font-display:swap;"
            f"src:url('./{name}.woff2') format('woff2');unicode-range:{sparse_range};}}"
        )

    css_path = os.path.join(out_dir, 'mashanzheng.css')
    header = (
        '/* 马善政（SIL OFL 1.1）自托管子集 —— 由 scripts/gen-handdrawn-font.py 生成，勿手改。\n'
        f' * 核心片（站点语料 {len(corpus)} 字）+ 稀疏片 {kept} 片兜底 {sparse_total / 1024:.0f} KB；\n'
        ' * 常规浏览只加载核心片，新标题的罕见字按片懒加载。 */\n'
    )
    with open(css_path, 'w', encoding='utf-8') as fh:
        fh.write(header + '\n'.join(faces) + '\n')

    core_kb = os.path.getsize(core_out) / 1024
    print(f'核心片 {core_kb:.0f} KB；稀疏片 {kept} 片共 {sparse_total / 1024:.0f} KB → {out_dir}')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/LongCang-Regular.ttf')
