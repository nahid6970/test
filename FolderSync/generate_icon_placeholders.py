#!/usr/bin/env python3
"""
Generate placeholder PNG icons for Android app
Run this script to create placeholder ic_launcher.png files in all required sizes
"""

try:
    from PIL import Image, ImageDraw, ImageFont
    PIL_AVAILABLE = True
except ImportError:
    PIL_AVAILABLE = False
    print("PIL/Pillow not available. Install with: pip install Pillow")

import os

# Icon sizes for different densities
ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

def create_placeholder_icon(size, output_path):
    """Create a simple placeholder icon"""
    if not PIL_AVAILABLE:
        print(f"Cannot create {output_path} - PIL not available")
        return
    
    # Create image with blue background
    img = Image.new('RGBA', (size, size), (33, 150, 243, 255))  # Material Blue
    draw = ImageDraw.Draw(img)
    
    # Draw white circle for sync symbol
    margin = size // 8
    circle_size = size - (margin * 2)
    draw.ellipse([margin, margin, margin + circle_size, margin + circle_size], 
                outline=(255, 255, 255, 255), width=max(2, size//24))
    
    # Draw sync arrows (simplified)
    center = size // 2
    arrow_size = size // 6
    
    # Top arrow (pointing right)
    draw.polygon([
        (center - arrow_size//2, center - arrow_size),
        (center + arrow_size//2, center - arrow_size//2),
        (center - arrow_size//2, center)
    ], fill=(255, 255, 255, 255))
    
    # Bottom arrow (pointing left)
    draw.polygon([
        (center + arrow_size//2, center + arrow_size),
        (center - arrow_size//2, center + arrow_size//2),
        (center + arrow_size//2, center)
    ], fill=(255, 255, 255, 255))
    
    # Save the image
    img.save(output_path, 'PNG')
    print(f"Created: {output_path} ({size}x{size})")

def main():
    """Generate all placeholder icons"""
    base_path = "app/src/main/res"
    
    if not PIL_AVAILABLE:
        print("Creating empty placeholder files...")
        # Create empty files as placeholders
        for folder, size in ICON_SIZES.items():
            folder_path = os.path.join(base_path, folder)
            os.makedirs(folder_path, exist_ok=True)
            
            placeholder_path = os.path.join(folder_path, "ic_launcher.png")
            with open(placeholder_path, 'wb') as f:
                # Write minimal PNG header (will be invalid but shows file exists)
                f.write(b'\x89PNG\r\n\x1a\n')
            print(f"Created empty placeholder: {placeholder_path}")
        
        print("\nTo create proper icons:")
        print("1. Install Pillow: pip install Pillow")
        print("2. Run this script again")
        print("3. Or manually create PNG files with the sizes above")
        return
    
    print("Generating placeholder icons...")
    
    for folder, size in ICON_SIZES.items():
        folder_path = os.path.join(base_path, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        icon_path = os.path.join(folder_path, "ic_launcher.png")
        create_placeholder_icon(size, icon_path)
    
    print(f"\n✅ Created {len(ICON_SIZES)} placeholder icons!")
    print("\nNext steps:")
    print("1. Replace these placeholder PNG files with your custom icons")
    print("2. Keep the same filenames: ic_launcher.png")
    print("3. Maintain the exact sizes for each density folder")
    print("\nIcon sizes needed:")
    for folder, size in ICON_SIZES.items():
        print(f"  {folder}/ic_launcher.png → {size}×{size}px")

if __name__ == "__main__":
    main()