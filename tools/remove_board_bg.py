#!/usr/bin/env python3
from PIL import Image
import os

def remove_background(input_path, output_path, target_color=(50, 21, 16), tolerance=15):
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

if __name__ == "__main__":
    # Base directory relative to this script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)

    input_file = os.path.join(project_root, 'app/src/main/res/drawable-nodpi/board.png')

    # We overwrite the original file as requested
    remove_background(input_file, input_file)
