"""cmp_tw.py <ref png> <native png> <out png>: score + side-by-side (ref | native | diff x3)"""
import sys
from PIL import Image, ImageChops, ImageStat
a = Image.open(sys.argv[1]).convert('RGB')
b = Image.open(sys.argv[2]).convert('RGB').resize(a.size)
d = ImageChops.difference(a, b)
print('%.1f%%' % (100 - sum(ImageStat.Stat(d.convert('L')).mean) / 2.55))
w = a.width
c = Image.new('RGB', (w * 3, a.height))
c.paste(a, (0, 0)); c.paste(b, (w, 0)); c.paste(d.point(lambda v: min(255, v * 3)), (2 * w, 0))
c.save(sys.argv[3])
