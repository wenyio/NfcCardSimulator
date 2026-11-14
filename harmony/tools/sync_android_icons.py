"""从 Android 矢量源同步图标；在鸿蒙项目根目录运行，依赖 Pillow。"""
from pathlib import Path
import re
import json
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'app/src/main/res/drawable'
MEDIA = ROOT / 'entry/src/main/resources/base/media'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def export_svg(name):
    vector = ET.parse(SOURCE / (name + '.xml')).getroot()
    attrs = {k: vector.get(ANDROID + k) for k in ['viewportWidth', 'viewportHeight']}
    svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg',
                     width=attrs['viewportWidth'], height=attrs['viewportHeight'],
                     viewBox=f"0 0 {attrs['viewportWidth']} {attrs['viewportHeight']}")
    mapping = {'pathData': 'd', 'fillColor': 'fill', 'strokeColor': 'stroke',
               'strokeWidth': 'stroke-width', 'strokeLineCap': 'stroke-linecap',
               'fillType': 'fill-rule'}
    for path in vector:
        values = {target: path.get(ANDROID + key) for key, target in mapping.items()
                  if path.get(ANDROID + key) is not None}
        if values.get('fill') == '@android:color/transparent':
            values['fill'] = 'none'
        if values.get('fill-rule') == 'evenOdd':
            values['fill-rule'] = 'evenodd'
        ET.SubElement(svg, 'path', values)
    target = MEDIA / (name + '.svg')
    target.write_text(ET.tostring(svg, encoding='unicode') + '\n', encoding='utf-8')
    return vector


def render_brand(vector, size=1024):
    # 品牌源仅使用绝对 M/L/H/V/Q/Z；高分辨率采样保留二次曲线与圆头描边。
    factor = size * 3 / float(vector.get(ANDROID + 'viewportWidth'))
    canvas = Image.new('RGBA', (size * 3, size * 3))
    draw = ImageDraw.Draw(canvas)
    for path in vector:
        tokens = re.findall(r'[MLHVQZ]|-?\d+(?:\.\d+)?', path.get(ANDROID + 'pathData'))
        contours, points, pos = [], [], (0, 0)
        i = 0
        while i < len(tokens):
            cmd = tokens[i]
            i += 1
            if cmd == 'Z':
                points.append(points[0])
                contours.append(points)
                points = []
                continue
            count = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'Q': 4}[cmd]
            args = list(map(float, tokens[i:i + count]))
            i += count
            if cmd == 'M':
                if points:
                    contours.append(points)
                points = []
                pos = tuple(args)
            elif cmd == 'L':
                pos = tuple(args)
            elif cmd == 'H':
                pos = (args[0], pos[1])
            elif cmd == 'V':
                pos = (pos[0], args[0])
            else:
                start = pos
                for step in range(1, 65):
                    t = step / 64
                    points.append(((1-t)**2*start[0]+2*(1-t)*t*args[0]+t*t*args[2],
                                   (1-t)**2*start[1]+2*(1-t)*t*args[1]+t*t*args[3]))
                pos = (args[2], args[3])
                continue
            points.append(pos)
        if points:
            contours.append(points)
        for contour in contours:
            scaled = [(x * factor, y * factor) for x, y in contour]
            fill = path.get(ANDROID + 'fillColor', '')
            if fill.startswith('#'):
                draw.polygon(scaled, fill=fill)
            stroke = path.get(ANDROID + 'strokeColor')
            if stroke:
                width = float(path.get(ANDROID + 'strokeWidth')) * factor
                draw.line(scaled, fill=stroke, width=round(width), joint='curve')
                if path.get(ANDROID + 'strokeLineCap') == 'round':
                    for x, y in [scaled[0], scaled[-1]]:
                        r = width / 2
                        draw.ellipse((x-r, y-r, x+r, y+r), fill=stroke)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    names = ['ic_back', 'ic_add', 'ic_logs', 'ic_settings', 'ic_more',
             'ic_info', 'ic_expand_more', 'ic_error_outline']
    for name in names:
        export_svg(name)
    brand = export_svg('ic_launcher_foreground')
    foreground = render_brand(brand)
    # Android 自适应图标把 108dp 图层中央 72dp 映射到可见图标；鸿蒙图层需补齐同一比例。
    inset = foreground.width // 6
    foreground = foreground.crop((inset, inset, foreground.width-inset, foreground.height-inset))
    foreground = foreground.resize((1024, 1024), Image.Resampling.LANCZOS)
    background_xml = ET.parse(SOURCE / 'ic_launcher_background.xml').getroot()
    color = background_xml.find('solid').get(ANDROID + 'color')
    background = Image.new('RGBA', foreground.size, color)
    for media in [MEDIA, ROOT / 'AppScope/resources/base/media']:
        foreground.save(media / 'foreground.png')
        background.save(media / 'background.png')
    render_brand(export_svg('ic_startup_card')).save(MEDIA / 'startIcon.png')
    colors = ET.parse(SOURCE.parent / 'values/colors.xml').getroot()
    startup_color = next(item.text for item in colors if item.get('name') == 'startup_background')
    for theme in ['base', 'dark']:
        path = ROOT / f'entry/src/main/resources/{theme}/element/color.json'
        palette = json.loads(path.read_text(encoding='utf-8'))
        for item in palette['color']:
            if item['name'] == 'start_window_background':
                item['value'] = startup_color
        path.write_text(json.dumps(palette, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Synced 8 UI icons, layered app icon and startup icon from Android sources.')
