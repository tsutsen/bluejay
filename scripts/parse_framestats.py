import sys, re

scenario, path = sys.argv[1], sys.argv[2]
txt = open(path).read()

def grab(pat, cast=float):
    m = re.search(pat, txt)
    return cast(m.group(1)) if m else None

total = grab(r'Total frames rendered: (\d+)', int)
janky = grab(r'Janky frames: \d+ \(([\d.]+)%\)')
p50 = grab(r'50th percentile: ([\d.]+)')
p90 = grab(r'90th percentile: ([\d.]+)')
p95 = grab(r'95th percentile: ([\d.]+)')
p99 = grab(r'99th percentile: ([\d.]+)')
if total is None:
    print(f"[{scenario}] no gfxinfo parsed — is the app running in foreground?")
else:
    print(f"[{scenario}] frames={total} janky={janky}% p50={p50}ms p90={p90}ms p95={p95}ms p99={p99}ms")
