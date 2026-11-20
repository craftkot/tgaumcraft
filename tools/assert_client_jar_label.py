#!/usr/bin/env python3
"""Fail-closed pixel and runtime-marker gate for the BETA26 jar aspect label."""
from pathlib import Path
import sys

from PIL import Image, ImageDraw


def inspect(image_path: Path) -> tuple[int, int]:
    image = Image.open(image_path).convert("RGB")
    if image.size != (1280, 800):
        raise SystemExit(f"JAR_LABEL_SCREEN_SIZE_UNEXPECTED={image.size}")

    paper = image.crop((450, 390, 575, 505))
    warm_pixels = sum(
        1 for red, green, blue in paper.getdata()
        if red >= 145 and green >= 120 and red - green >= 6
        and green - blue >= 18 and red - blue >= 35
    )

    glyph = image.crop((480, 420, 545, 475))
    dark_pixels = sum(
        1 for red, green, blue in glyph.getdata()
        if max(red, green, blue) <= 100
    )
    return warm_pixels, dark_pixels


def verify(log_path: Path, image_path: Path) -> None:
    log = log_path.read_text(encoding="utf-8", errors="replace")
    required = (
        "TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=aer facing=2",
        "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=aer texture=thaumcraft:textures/aspects/aer.png",
        "JAR_ASPECT_LABEL_FIXTURE_READY",
    )
    for marker in required:
        if marker not in log:
            raise SystemExit(f"JAR_LABEL_REQUIRED_MARKER_MISSING={marker}")

    warm_pixels, dark_pixels = inspect(image_path)
    print(f"BETA26 jar label paper pixels: {warm_pixels}; Aer glyph dark pixels: {dark_pixels}")
    if warm_pixels < 800:
        raise SystemExit(f"JAR_LABEL_PAPER_PIXEL_GATE_FAILED={warm_pixels}")
    if dark_pixels < 100:
        raise SystemExit(f"JAR_LABEL_ASPECT_GLYPH_PIXEL_GATE_FAILED={dark_pixels}")
    print("BETA26 jar aspect label real-client evidence: PASS")


def self_test() -> None:
    from tempfile import TemporaryDirectory
    with TemporaryDirectory() as tmp:
        root = Path(tmp)
        image_path = root / "synthetic.png"
        log_path = root / "client.log"
        image = Image.new("RGB", (1280, 800), (35, 40, 45))
        draw = ImageDraw.Draw(image)
        draw.rectangle((470, 410, 555, 490), fill=(245, 225, 168))
        draw.polygon(((500, 425), (525, 425), (538, 450), (525, 475), (500, 475), (487, 450)),
                     fill=(45, 42, 34))
        image.save(image_path)
        log_path.write_text(
            "TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=aer facing=2 crooked=0 "
            "texture=thaumcraft:textures/models/label.png\n"
            "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=aer "
            "texture=thaumcraft:textures/aspects/aer.png\n"
            "JAR_ASPECT_LABEL_FIXTURE_READY\n",
            encoding="utf-8",
        )
        verify(log_path, image_path)


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
    elif len(sys.argv) == 3:
        verify(Path(sys.argv[1]), Path(sys.argv[2]))
    else:
        raise SystemExit("usage: assert_client_jar_label.py <client.log> <100-jar-aspect-label.png> | --self-test")
