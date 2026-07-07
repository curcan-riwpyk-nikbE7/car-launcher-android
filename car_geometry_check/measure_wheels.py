"""
Измерение точных координат колёс на car_neon.png для точного наложения
векторных вращающихся дисков (без сдвига/шва).

Метод: детекция циан-цветной обводки диска (яркий cyan: высокий B/G,
низкий R, непрозрачный) через маску по цвету в отдельных окнах для
заднего и переднего колеса, затем bounding box маски даёт cx,cy,rx,ry.
"""
from PIL import Image
import numpy as np

car = Image.open('../app/src/main/res/drawable-nodpi/car_neon.png').convert('RGBA')
arr = np.array(car)
r, g, b, a = arr[..., 0].astype(int), arr[..., 1].astype(int), arr[..., 2].astype(int), arr[..., 3].astype(int)
cyan_mask = (b > 180) & (g > 150) & (r < 150) & (a > 200)


def analyze(x0, x1, y0, y1, label):
    sub = cyan_mask[y0:y1, x0:x1]
    ys, xs = np.where(sub)
    if len(xs) == 0:
        print(label, "none")
        return
    xs_full = xs + x0
    ys_full = ys + y0
    cx = (xs_full.min() + xs_full.max()) / 2
    cy = (ys_full.min() + ys_full.max()) / 2
    rx = (xs_full.max() - xs_full.min()) / 2
    ry = (ys_full.max() - ys_full.min()) / 2
    print(f"{label}: cx={cx} cy={cy} rx={rx} ry={ry}")


analyze(85, 360, 205, 470, "rear rim")
analyze(695, 1000, 190, 495, "front rim")
