## sgdk-video-player
A converter and video player for the [SGDK v1.9](https://github.com/Stephane-D/SGDK).  
Supports up to 252 colors per frame (at the expense of a smaller frame size), currently running at 15 FPS with a frame size of 272x176 pixels.  


### Config theese first:
- You need to have *Image Magick v7.x* tools installed and set in _PATH_.
- You need to have *ffmpeg* installed and set in the _PATH_.
- Set `ENABLE_BANK_SWITCH` 1 in _SGDK_'s `config.h` if the rom size is bigger than 4MB, and re build the _SGDK_ lib.


### Instructions using custom tiledpalettequant app
1) env.bat
Set NodeJs env var.

2) extract.bat "Genesis Does What Nintendon't - v2 HD by RVGM.mp4" tmpmv 8 256
(color reduction param is optional)
This creates frames and strips, width next data
frame width: 272   (set in the script)
frame height: 176  (set in the script)
rows per strip: 8
color reduction: 256 (optional param)

3) Use nodejs custom app tiledpalettequant to generate all RGB images with palettes.
Once rgb images with palettes were generated and before saving them ensure the next config:
	in SGDK settings section:
		check Switch 2 Palettes positions
		check Start at [PAL0,PAL1] first
		set 22 at Reset every N strips (This only needed if strips per frame is an odd number)
Export/download the images and move them at res\rgb folder.

4) node generator.js 272 176 8
frame width: 272
frame height: 176
rows per strip: 8

5) compile_n_run.bat
Run it once to catch rescomp output to know max tiles number and then 
	- manually set it for VIDEO_FRAME_MAX_TILESET_NUM constant at main.c.
	- edit TilemapCustom.java and manually set the argument for setMapBaseTileInd() method call.
	- compile class TilemapCustom.java, move produced TilemapCustom.class into rescomp_ext.jar accordingly.
rom.bin generated at out folder
Blastem binary location set in the bat script


### TODO
- Replace loop logic for waiting VIDEO_FRAME_RATE with just checking if prevFrame - vTimer >= 4 (3 for PAL). Where: 60/15=4 NTSC and 50/15=3.33 PAL
- Is there a better way to loop-wait until VInt happens instead of using `while (vtimer == t) {;}`?
- Could declaring the arrays data[] y pals_data[] directly in ASM reduce rom size and/or speed access?
- Maybe helps? ==> `VDP_waitDMACompletion(); // safe to check for DMA completion before dealing with VDP (this also clear internal VDP latch)`
- Clear mem used for sound when exiting the video loop?
- Try to change from H40 to H32 on HInt Callback and see any speed gain. See https://plutiedev.com/mirror/kabuto-hardware-notes#h40-mode-tricks
- Read all tiles of all frames and count their occurrences so we can cached them at the start of tileset VRAM. At least 34 tilesshould be cached.
- Implement custom consecutive frame compressor, like a delta compression or whatever a simple video compressor does.
- Try 20 FPS once we make every frame to be displayed only in 3 display loops (only doable with frame delta compression with cached common tiles).
So 60/3=20 in NTSC. And 50/3=16 in PAL.


----
### (OLD/OUTDATED) Instructions using custom quantization lua script
1) env.bat

2) extract.bat "Genesis Does What Nintendon't - v2 HD by RVGM.mp4" tmpmv 16

3) create_palettes_run_all.bat tmpmv\frame_*.png true
The true argument means a single instance of palette creation process will be executed per image.
Use next command from other cmd window in order to kill current aseprite execution and to continue with next image:
	taskkill /f /fi "imagename eq aseprite.exe"

4) If some images failed or you had to manually stop the process, then leave the folder tmpmv only with images 
from failed folder, and do:
* Edit create_palettes_for_md.lua and set APPROXIMATION_ITERS other value (default is 0).
* Run step 3 again.

5) If nothing of the above generates the RGB images, then run
reduce_colors.bat tmpmv\frame_*.png 14
Repeat step 3.

6) node generator.js

7) compile_n_run.bat
rom.bin generated at out folder
Blastem binary location set in the bat script
