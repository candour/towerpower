import os
from PIL import Image

# Configuration
STALLS_SHEET_PATH = "app/src/main/res/drawable/stalls.png"
OUTPUT_DIR = "stall_verification"
HTML_FILE = "index.html"

# Rects from SpriteConstants.kt (left, top, right, bottom)
STALLS = [
    ("Teh Tarik", (22, 41, 330, 451)),
    ("Satay", (358, 41, 666, 451)),
    ("Chicken Rice", (22, 500, 330, 930)),
    ("Ice Kachang", (358, 500, 666, 930))
]

def main():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    if not os.path.exists(STALLS_SHEET_PATH):
        print(f"Error: {STALLS_SHEET_PATH} not found")
        return

    img = Image.open(STALLS_SHEET_PATH)

    html_content = """
    <html>
    <head>
        <title>Stall Verification Report</title>
        <style>
            table { border-collapse: collapse; width: 100%; }
            th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
            img { border: 1px solid #000; background-color: #f0f0f0; }
        </style>
    </head>
    <body>
        <h1>Stall Verification Report</h1>
        <table>
            <tr>
                <th>Name</th>
                <th>Rect (L, T, R, B)</th>
                <th>Sprite</th>
            </tr>
    """

    for name, rect in STALLS:
        crop = img.crop(rect)
        filename = f"{name.lower().replace(' ', '_')}.png"
        crop.save(os.path.join(OUTPUT_DIR, filename))

        html_content += f"""
            <tr>
                <td>{name}</td>
                <td>{rect}</td>
                <td><img src="{filename}"></td>
            </tr>
        """

    html_content += """
        </table>
    </body>
    </html>
    """

    with open(os.path.join(OUTPUT_DIR, HTML_FILE), "w") as f:
        f.write(html_content)

    print(f"Report generated at {os.path.join(OUTPUT_DIR, HTML_FILE)}")

if __name__ == "__main__":
    main()
