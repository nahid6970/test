# App Icon Guide

Replace the icon files in these folders with your PNG icons:

## Icon Sizes Required:

1. **mipmap-mdpi/** (Medium density)
   - ic_launcher.png: 48x48 pixels

2. **mipmap-hdpi/** (High density)
   - ic_launcher.png: 72x72 pixels

3. **mipmap-xhdpi/** (Extra-high density)
   - ic_launcher.png: 96x96 pixels

4. **mipmap-xxhdpi/** (Extra-extra-high density)
   - ic_launcher.png: 144x144 pixels

5. **mipmap-xxxhdpi/** (Extra-extra-extra-high density)
   - ic_launcher.png: 192x192 pixels

## Steps to Replace Icons:

1. Create your icon in 5 different sizes (48, 72, 96, 144, 192 pixels)
2. Name them all `ic_launcher.png`
3. Place each size in the corresponding mipmap folder
4. Delete the existing `.webp` files if you want
5. Rebuild the app

## Optional - Round Icons:
If you want round icons (for devices that support them):
- Create round versions with the same sizes
- Name them `ic_launcher_round.png`
- Place in the same folders

## Quick Tip:
You can use online tools like:
- https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
- Upload one large icon (512x512) and it generates all sizes for you!

## Current Icon Locations:
- app/src/main/res/mipmap-mdpi/ic_launcher.png (48x48)
- app/src/main/res/mipmap-hdpi/ic_launcher.png (72x72)
- app/src/main/res/mipmap-xhdpi/ic_launcher.png (96x96)
- app/src/main/res/mipmap-xxhdpi/ic_launcher.png (144x144)
- app/src/main/res/mipmap-xxxhdpi/ic_launcher.png (192x192)
