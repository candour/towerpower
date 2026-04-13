import os
from PIL import Image

input_path = "/tmp/file_attachments/1776090307281~2.png"
output_base = "app/src/main/res"

densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def process_icon(img, size, output_path):
    # Create a square icon with padding if necessary
    target_size = (size, size)

    # Calculate aspect ratio
    width, height = img.size
    aspect = width / height

    if aspect > 1:
        new_width = size
        new_height = int(size / aspect)
    else:
        new_height = size
        new_width = int(size * aspect)

    resized_img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)

    # Create new transparent square image
    new_img = Image.new("RGBA", target_size, (0, 0, 0, 0))
    # Paste resized image into center
    upper_left = ((size - new_width) // 2, (size - new_height) // 2)
    new_img.paste(resized_img, upper_left)

    new_img.save(output_path, "PNG")

def main():
    if not os.path.exists(input_path):
        print(f"Error: {input_path} not found")
        return

    img = Image.open(input_path).convert("RGBA")

    for folder, size in densities.items():
        folder_path = os.path.join(output_base, folder)
        os.makedirs(folder_path, exist_ok=True)

        # Save ic_launcher.png
        process_icon(img, size, os.path.join(folder_path, "ic_launcher.png"))
        # Save ic_launcher_round.png
        process_icon(img, size, os.path.join(folder_path, "ic_launcher_round.png"))

    # Also update the ones in the root if they exist and seem relevant
    # ic_launcher_round.png exists in root.
    process_icon(img, 192, "ic_launcher_round.png")

if __name__ == "__main__":
    main()
