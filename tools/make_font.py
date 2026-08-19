#!/usr/bin/env python3
"""Noto Sans KR 가변 폰트를 Thin(100)으로 굳히고, DB에 실제로 쓰인 글자만 남긴다.

전체 CJK를 넣으면 10MB가 넘는다. 3급 기출에 나오는 글자만 남기면 1MB 아래로
떨어지고, 앱에서 한자를 크고 얇게 띄우는 데 필요한 건 그것뿐이다.
"""
import re, sqlite3, subprocess, sys, os

SRC = os.path.join(os.path.dirname(__file__), 'NotoSansKR-var.ttf')
DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'hanja-mock', 'data', 'hanja3.db')
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'font',
                   'noto_sans_kr_thin.ttf')
UI = ('0123456789 .,:;-~()[]{}/·…※○△▲◇◆①②③④⑤⑥⑦⑧⑨⑩'
      '㉠㉡㉢㉣㉤㉥㉦㉧㉨㉩㉮㉯㉰㉱↔問急級回年月日')


def charset():
    db = sqlite3.connect(DB)
    txt = UI
    for q in ('SELECT question FROM items', 'SELECT question_html FROM items',
              'SELECT target FROM items', 'SELECT answer FROM items',
              'SELECT instruction FROM sections', 'SELECT gloss FROM hunmeum'):
        txt += ''.join(r[0] or '' for r in db.execute(q))
    # 앱 UI 문구
    txt += '한자능력검정3급기출회차문항정답보기가리기다시풀기전체구역년월일시행'
    txt += '진행률준비중없음불러오는중오류'
    txt += ''.join(chr(c) for c in range(0x20, 0x7f))
    return sorted(set(txt) - {'\n', '\r'})


def main():
    var = os.path.abspath(SRC)
    thin = var.replace('-var.ttf', '-Thin.ttf')
    subprocess.run([sys.executable, '-m', 'fontTools.varLib.instancer',
                    var, 'wght=100', '-o', thin], check=True)
    cs = charset()
    print('글자 %d자' % len(cs))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    subprocess.run([sys.executable, '-m', 'fontTools.subset', thin,
                    '--text=%s' % ''.join(cs),
                    '--layout-features=*', '--no-hinting', '--desubroutinize',
                    '--name-IDs=*', '--output-file=' + OUT], check=True)
    print('%s  %.0f KB' % (OUT, os.path.getsize(OUT) / 1024))


if __name__ == '__main__':
    main()
