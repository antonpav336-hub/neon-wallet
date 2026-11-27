from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageOps

OUT = Path("app/src/main/res/drawable-nodpi")
OUT.mkdir(parents=True, exist_ok=True)
W, H = 1024, 2048

def make_background(name, top, bottom, glows, seed):
    # Smooth vertical graphite gradient.
    strip = Image.new("RGB", (1, H))
    px = strip.load()
    for y in range(H):
        t = y / max(1, H - 1)
        px[0, y] = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
    image = strip.resize((W, H), Image.Resampling.BICUBIC)

    # Large, soft lavender light fields; used as actual UI surface artwork.
    for cx, cy, rx, ry, color, strength in glows:
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        for step in range(28, 0, -1):
            k = step / 28
            alpha = int(strength * (1 - k) ** 2 * 0.8)
            box = (cx - rx * k, cy - ry * k, cx + rx * k, cy + ry * k)
            draw.ellipse(box, fill=(*color, alpha))
        layer = layer.filter(ImageFilter.GaussianBlur(52))
        image = Image.alpha_composite(image.convert("RGBA"), layer).convert("RGB")

    # Fine, restrained grain prevents flat gradients and gives the surfaces depth.
    noise = Image.effect_noise((W, H), 18).convert("L")
    noise = noise.point(lambda p: max(0, min(255, 128 + int((p - 128) * 0.16))))
    noise_rgb = Image.merge("RGB", (noise, noise, noise))
    image = ImageChops.add(image, noise_rgb, scale=1, offset=-128)

    # Delicate orbital lines and pinpoints, kept low contrast.
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for i in range(5):
        inset = 80 + i * 34
        draw.arc((W - 390 - inset, 120 - inset, W + 220 + inset, 730 + inset),
                 start=205, end=330, fill=(202, 184, 239, 18), width=2)
    for x, y, r in [(150, 440, 3), (850, 1020, 2), (760, 1510, 3), (240, 1780, 2), (610, 300, 2)]:
        draw.ellipse((x-r, y-r, x+r, y+r), fill=(222, 209, 250, 55))
    overlay = overlay.filter(ImageFilter.GaussianBlur(1.2))
    image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    image.save(OUT / name, format="PNG", optimize=False, compress_level=1)

make_background(
    "bg_neon_auth.png",
    (18, 16, 25), (8, 8, 13),
    [(790, 250, 580, 650, (150, 119, 205), 44),
     (100, 1550, 540, 700, (91, 73, 132), 34)],
    17
)
make_background(
    "bg_neon_surface.png",
    (15, 14, 21), (9, 8, 14),
    [(900, 520, 520, 700, (125, 103, 172), 36),
     (140, 1420, 470, 560, (80, 68, 116), 28)],
    29
)

make_background(
    "bg_neon_card.png",
    (27, 24, 35), (10, 9, 15),
    [(760, 420, 600, 650, (166, 139, 211), 42),
     (220, 1660, 460, 520, (112, 93, 154), 30)],
    41
)
make_background(
    "bg_neon_payments.png",
    (18, 19, 29), (8, 9, 15),
    [(880, 360, 540, 650, (113, 105, 190), 40),
     (100, 1450, 500, 600, (73, 92, 151), 30)],
    53
)
make_background(
    "bg_neon_profile.png",
    (25, 21, 32), (9, 8, 14),
    [(740, 330, 560, 600, (164, 130, 205), 38),
     (180, 1550, 520, 620, (101, 80, 145), 30)],
    67
)
make_background(
    "bg_neon_pin.png",
    (21, 16, 31), (7, 7, 13),
    [(510, 420, 580, 700, (133, 100, 190), 40),
     (850, 1650, 460, 560, (77, 66, 130), 28)],
    79
)
make_background(
    "bg_neon_history.png",
    (17, 18, 26), (8, 8, 13),
    [(860, 500, 560, 620, (111, 121, 177), 34),
     (130, 1450, 480, 600, (106, 84, 150), 26)],
    89
)
make_background(
    "bg_neon_qr.png",
    (10, 16, 22), (7, 8, 13),
    [(840, 420, 560, 700, (84, 119, 158), 32),
     (150, 1550, 500, 620, (116, 87, 160), 28)],
    97
)
