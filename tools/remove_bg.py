#!/usr/bin/env python3
import argparse
from PIL import Image
import os

def remove_background(input_path, output_path, target_color, tolerance=15):
    """
    Removes the background color from an image with a given tolerance.
    Target color is (R, G, B).
    """
    if not os.path.exists(input_path):
        print(f"Error: {input_path} not found.")
        return

    print(f"Processing {input_path}...")
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()

    new_data = []
    tr, tg, tb = target_color

    for item in data:
        # Check if the pixel is within the tolerance of the target color
        if (abs(item[0] - tr) <= tolerance and
            abs(item[1] - tg) <= tolerance and
            abs(item[2] - tb) <= tolerance):
            # Set alpha to 0 (fully transparent)
            new_data.append((item[0], item[1], item[2], 0))
        else:
            new_data.append(item)

    img.putdata(new_data)
    img.save(output_path, "PNG")
    print(f"Background removed and saved to {output_path}")

def main():
    parser = argparse.ArgumentParser(description="Remove background color from an image.")
    parser.add_argument("input", help="Path to the input image.")
    parser.add_argument("-o", "--output", help="Path to the output image. If not specified, the input image will be overwritten.")
    parser.add_argument("-c", "--color", nargs=3, type=int, default=[255, 255, 255],
                        help="The RGB color to remove (e.g., -c 255 255 255). Default is white.")
    parser.add_argument("-t", "--tolerance", type=int, default=5,
                        help="Tolerance for color matching. Default is 5.")

    args = parser.parse_args()

    input_path = args.input
    output_path = args.output if args.output else args.input
    target_color = tuple(args.color)

    remove_background(input_path, output_path, target_color, args.tolerance)

if __name__ == "__main__":
    main()
