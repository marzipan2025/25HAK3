#!/usr/bin/env python3
"""~/Downloads/AppIcon_25HAK3_2.png 을 안드로이드 런처 아이콘으로 굽는다.

원본은 노란 바탕에 검은 획이 화면 밖으로 흘러나가는 2048px 정사각 그림이다.
적응형 아이콘은 108dp 중 안쪽 72dp만 보이므로, 원본을 그대로 전경에 넣으면
획이 한 번 더 잘린다. 그래서 바탕은 원본의 노랑으로 깔고, 그림은 안전 영역에
맞춰 줄여 전경으로 넣는다. 구형 런처용 정사각/원형 아이콘은 원본 그대로 쓴다.
"""
import os
from PIL import Image

SRC = os.path.expanduser('~/Downloads/AppIcon_25HAK3_2.png')
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
YELLOW = (244, 220, 19, 255)

# mdpi 기준 배수 — 런처 아이콘 48dp, 적응형 레이어 108dp
DENSITY = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
SAFE = 72 / 108          # 적응형 아이콘에서 실제로 보이는 비율
FILL = 0.92              # 안전 영역을 이만큼 채운다


def main():
    src = Image.open(SRC).convert('RGBA')
    for name, k in DENSITY.items():
        out = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(out, exist_ok=True)

        legacy = int(48 * k)
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher.png'))
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher_round.png'))

        layer = int(108 * k)
        art = int(layer * SAFE * FILL)
        fg = Image.new('RGBA', (layer, layer), (0, 0, 0, 0))
        fg.paste(src.resize((art, art), Image.LANCZOS), ((layer - art) // 2,) * 2)
        fg.save(os.path.join(out, 'ic_launcher_foreground.png'))

        Image.new('RGBA', (layer, layer), YELLOW).save(
            os.path.join(out, 'ic_launcher_background.png'))
        print(name, legacy, layer, art)


if __name__ == '__main__':
    main()
