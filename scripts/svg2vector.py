#!/usr/bin/env python3
"""Convert a flat SVG into an Android VectorDrawable XML.

Supports: path / rect / circle, solid fills, linear & radial gradients
(userSpaceOnUse only) defined in <defs>, fill-rule=evenodd, per-shape and
inherited <g opacity> (-> android:fillAlpha), basic named colors.
Path data is passed through verbatim (no rescaling), so the SVG's viewBox
becomes the vector's viewport.

Usage: svg2vector.py input.svg output.xml [--width N] [--scale S]

--scale wraps all shapes in a <group> scaled by S around the canvas center
(useful for fitting artwork into the adaptive-icon safe zone).
"""
import sys
import xml.etree.ElementTree as ET

NS = __import__('re').compile(r'\{.*\}')


def tag(el):
    return NS.sub('', el.tag)


NAMED = {'white': 'FFFFFF', 'black': '000000', 'red': 'FF0000',
         'green': '008000', 'blue': '0000FF', 'none': None}


def android_color(c, alpha=1.0):
    """SVG color -> #AARRGGBB (or None for 'none')."""
    if c is None:
        return None
    c = c.strip()
    if c in NAMED:
        rgb = NAMED[c]
        return None if rgb is None else '#%02X%s' % (int(alpha * 255), rgb)
    c = c.lstrip('#')
    if len(c) == 3:
        c = ''.join(ch * 2 for ch in c)
    if len(c) == 6:
        return '#%02X%s' % (int(alpha * 255), c.upper())
    if len(c) == 8:  # SVG RRGGBBAA -> Android AARRGGBB
        return ('#' + c[6:8] + c[0:6]).upper()
    raise SystemExit('unsupported color: %r' % c)


def parse_offset(v):
    if v is None:
        return 0.0
    v = v.strip()
    return float(v[:-1]) / 100.0 if v.endswith('%') else float(v)


def rect_path(x, y, w, h, rx=0):
    x, y, w, h, rx = (float(v or 0) for v in (x, y, w, h, rx))
    if rx == 0:
        return 'M%s %sh%sv%sh-%sz' % (x, y, w, h, w)
    return ('M%s %sh%s a%s %s 0 0 1 %s %s v%s a%s %s 0 0 1 %s %s h-%s'
            ' a%s %s 0 0 1 %s %s v-%s a%s %s 0 0 1 %s %s z'
            % (x + rx, y, w - 2 * rx, rx, rx, rx, rx, h - 2 * rx, rx, rx,
               -rx, rx, w - 2 * rx, rx, rx, -rx, -rx, h - 2 * rx, rx, rx,
               rx, -rx))


def circle_path(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return 'M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 %s 0z' % (
        cx - r, cy, r, r, 2 * r, r, r, -2 * r)


def load_gradients(root):
    grads = {}
    for el in root.iter():
        if tag(el) in ('linearGradient', 'radialGradient'):
            stops = []
            for s in el:
                if tag(s) != 'stop':
                    continue
                off = parse_offset(s.get('offset'))
                col = android_color(s.get('stop-color') or 'black',
                                    float(s.get('stop-opacity') or 1))
                stops.append((off, col))
            if stops:
                stops[0] = (0.0, stops[0][1])
                stops[-1] = (1.0, stops[-1][1])
            grads[el.get('id')] = (tag(el), el, stops)
    return grads


def gradient_xml(kind, el, stops):
    if kind == 'linearGradient':
        coords = ['android:startX="%s"' % el.get('x1', '0'),
                  'android:startY="%s"' % el.get('y1', '0'),
                  'android:endX="%s"' % el.get('x2', '1'),
                  'android:endY="%s"' % el.get('y2', '0')]
        gtype = 'linear'
    else:
        coords = ['android:centerX="%s"' % el.get('cx', '0'),
                  'android:centerY="%s"' % el.get('cy', '0'),
                  'android:radius="%s"' % el.get('r', '0')]
        gtype = 'radial'
    items = '\n'.join(
        '        <item android:offset="%s" android:color="%s"/>' % (o, c)
        for o, c in stops)
    body = '\n          '.join(coords + ['android:type="%s"' % gtype])
    return ('    <aapt:attr name="android:fillColor">\n'
            '      <gradient\n%s>\n%s\n      </gradient>\n'
            '    </aapt:attr>' % (body, items))


def convert(svg_path, out_path, width_dp, scale=1.0):
    root = ET.parse(svg_path).getroot()
    vb = (root.get('viewBox') or '0 0 108 108').split()
    vw_f, vh_f = float(vb[2]), float(vb[3])
    vw, vh = ('%g' % vw_f, '%g' % vh_f)
    grads = load_gradients(root)

    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="%sdp"' % width_dp,
           '    android:height="%sdp"' % width_dp,
           '    android:viewportWidth="%s"' % vw,
           '    android:viewportHeight="%s">' % vh]

    body = []

    def emit(attrs, fill):
        # fill: '#AARRGGBB' (solid) or aapt:attr XML block (gradient)
        if fill.lstrip().startswith('<'):
            body.append('  <path\n      %s>' % '\n      '.join(attrs))
            body.append(fill)
            body.append('  </path>')
        else:
            body.append('  <path\n      %s\n      android:fillColor="%s" />'
                        % ('\n      '.join(attrs), fill))

    def walk(el, alpha):
        for child in el:
            t = tag(child)
            a = alpha * float(child.get('opacity', 1))
            if t == 'g':
                walk(child, a)
                continue
            if t not in ('path', 'rect', 'circle'):
                continue
            fill = child.get('fill')
            if fill is None or fill == 'none':
                continue
            if t == 'path':
                d = child.get('d')
            elif t == 'rect':
                d = rect_path(child.get('x'), child.get('y'),
                              child.get('width'), child.get('height'),
                              child.get('rx'))
            else:
                d = circle_path(child.get('cx'), child.get('cy'),
                                child.get('r'))
            if d is None:
                continue
            attrs = ['android:pathData="%s"' % d]
            if child.get('fill-rule') == 'evenodd':
                attrs.append('android:fillType="evenOdd"')
            if a < 1:
                attrs.append('android:fillAlpha="%g"' % round(a, 4))
            if fill.startswith('url(#'):
                kind, gel, stops = grads[fill[5:-1]]
                emit(attrs, gradient_xml(kind, gel, stops))
            else:
                emit(attrs, android_color(fill, a))

    walk(root, 1.0)
    if scale != 1:
        out.append('  <group\n'
                   '      android:scaleX="%g"\n'
                   '      android:scaleY="%g"\n'
                   '      android:translateX="%g"\n'
                   '      android:translateY="%g"\n' %
                   (scale, scale, vw_f * (1 - scale) / 2, vh_f * (1 - scale) / 2)
                   + '  >')
        out.extend(body)
        out.append('  </group>')
    else:
        out.extend(body)
    out.append('</vector>')
    with open(out_path, 'w') as f:
        f.write('\n'.join(out) + '\n')
    print('wrote %s' % out_path)


if __name__ == '__main__':
    args = sys.argv[1:]
    width, scale = 108, 1.0
    if '--width' in args:
        i = args.index('--width')
        width = int(args[i + 1])
        del args[i:i + 2]
    if '--scale' in args:
        i = args.index('--scale')
        scale = float(args[i + 1])
        del args[i:i + 2]
    convert(args[0], args[1], width, scale)
