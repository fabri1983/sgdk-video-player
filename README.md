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

2) extract.bat "Genesis Does What Nintendon't - v2 HD by RVGM.mp4" tmpmv 15 8 256
tmpmv: output folder
15: frame rate
8: rows per strip
256: color reduction per frame (optional param)
Additionally it uses these internal params:
frame width:  272  (set inside the script)
frame height: 176  (set inside the script)

3) Use nodejs custom app tiledpalettequant to generate all RGB images with palettes.
Once rgb images with palettes were generated and before saving them ensure the next config:
	in SGDK settings section:
		check Switch 2 Palettes positions
		check Start at [PAL0,PAL1] first
		set 22 at Reset every N strips (This only needed if strips per frame is an odd number)
Export/download the images and move them at res\rgb folder.

4) node generator.js 272 176 8 15
frame width: 272
frame height: 176
rows per strip: 8
frame rate: 15

5) compile_n_run.bat
Run it once to catch rescomp output to know max tiles number and then 
	- manually set it for VIDEO_FRAME_MAX_TILESET_NUM constant at main.c.
	- edit TilemapCustom.java and manually set the argument for setMapBaseTileInd() method call.
	- compile class TilemapCustom.java, move produced TilemapCustom.class into rescomp_ext.jar accordingly.
rom.bin generated at out folder
Blastem binary location set in the bat script


### TODO
- OPT: if GET_VCOUNTER >= MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC (and PAL) then we can start the DMA_flush immediately for tileset data 
without the need to wait for start of vblank period. Keep in mind that tilemap should only be flushed in vblank period.
- Investigate how to wait for start of vblank in ASM is done.
- Investigate what is all this about 4px wide strips.
- Use VirtualDub to resize the video with the correct filter to keep image crisp.
- Idea: flush DMA without waiting for completion and move the enable VDP into HInt (use condition). This way we can:
	- quickly return to CPU to continue unpackicg (if it was doing so).
- Split tileset and tilemap in 3 chunks so the unpack is less CPU intense and we can do it inside the active display period.
- Idea to avoid sending the first 2 strips'pals and send only first strip's pals:
	- Load the first 32 colors at VInt
	- Add +32 and -32 accordingly in VInt and videoPlayer.c.
	- Set HINT_PALS_CMD_ADDRR_RESET_VALUE to 32 in movieHVInterrupts.h.
	- Hint now starts 1 row of tiles more than the already calculated in movieHVInterrupts.h.
- Once the unpack/load of tileset/tilemap/pals happen during the time of an active display loop we can:
	- discard palInFrameRootPtr and just use the setPalsPointer() call made in waitVInt_AND_flushDMA() without the bool parameter resetPalsPtrsForHInt.
	- remove the condition if (!((prevFrame ^ vFrame) & 1))
	- use ++dataPtr; instead of dataPtr += vFrame - prevFrame;
- Could declaring the arrays data[] y pals_data[] directly in ASM reduce rom size and/or speed access?
- Clear mem used by sound when exiting the video loop?
- Try using XGM PCM driver:
	- extract audio with sample rate 14k (or 13.3k for XGMv2 yet to be released)
		(I used VirtualDub2 to extract as 8bit signed 14KHz 1 channel (mono), but the extract.bat script is the correct way and needs to be updated)
	- in movie_sound.res: WAV sound_wav "sound/sound.wav" XGM
	- Use the XGM_PCM methods
- Try to change from H40 to H32 on HInt Callback, and hope for any any speed gain?
	See https://plutiedev.com/mirror/kabuto-hardware-notes#h40-mode-tricks
	See http://gendev.spritesmind.net/forum/viewtopic.php?p=17683&sid=e64d28235b5b42d96b82483d4d71d34b#p17683
- Implement custom rescomp plugin to create a cache of most common tiles.
	- Read all tiles from all frames and count their occurrences so we can cached them at the start of tileset RAM section.
	- At least 34 tiles should be cached = 34*32=1088 bytes
	- Check for big frames (min 600 tiles?) that at least have cached 34 tiles.
	- Then in SGDK pre load cached tiles into VRAM for both tile index 1 and 716+1.
- Try 20 FPS once we make every frame to be unpacked and loaded in up to 3 active display perios (only doable with lot of cached common tiles).
So 60/3=20 in NTSC. And 50/3=16 in PAL.
	- edit extract.bat and 


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
