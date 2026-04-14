from PIL import Image
import os

def remove_black_background(input_path, output_path):
    img = Image.open(input_path).convert("RGBA")
    datas = img.getdata()
    
    newData = []
    for item in datas:
        # Get brightness (average of RGB or just max)
        r, g, b, a = item
        # Calculate luminance
        lum = (r + g + b) // 3
        
        # We want bright pixels (the green mist) to be opaque and green
        # We want dark pixels (the black background) to be transparent
        # So we can set alpha = luminance. And perhaps boost the green.
        
        # If it's mostly black, make it transparent
        newData.append((r, g, b, int(lum * 1.5) if lum * 1.5 < 255 else 255))
        
    img.putdata(newData)
    img.save(output_path, "PNG")

if __name__ == "__main__":
    input_file = r"C:\Users\mihab\Licenta\app\src\main\res\drawable\img_magic_mist.png"
    output_file = r"C:\Users\mihab\Licenta\app\src\main\res\drawable\img_magic_mist_transparent.png"
    remove_black_background(input_file, output_file)
    os.replace(output_file, input_file)
    print("Background removed.")
