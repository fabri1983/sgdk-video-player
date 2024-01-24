## sgdk-video-player
A video converter and video player ready to use with the [SGDK v1.9](https://github.com/Stephane-D/SGDK) for the Megadrive/Genesis system consoles.  
Originally inspired by [sgdk-video-player](https://github.com/haroldo-ok/sgdk-video-player) made by _haroldo-ok_.  


### Features
- Supports up to 252 colors per frame (at the expense of a slightly smaller frame size).
- Supports both NTSC and PAL console systems.
- Currently running at 15 FPS in NTSC and 12 FPS in PAL, with a frame size of 272x176 pixels.
- Uses custom extensions for the [Stef's SGDK rescomp tool](https://github.com/Stephane-D/SGDK/blob/master/bin/rescomp.txt).


### Config theese first:
- You need to have *Image Magick v7.x* tools installed and set in _PATH_.
- You need to have *ffmpeg* installed and set in the _PATH_.
- Set `ENABLE_BANK_SWITCH` 1 in _SGDK_'s `config.h` if the rom size is bigger than 4MB, and re build the _SGDK_ lib.
- You need to have *NodeJs* installed and set *NODEJS_HOME* in your user/system variables.


### Instructions using custom tiledpalettequant app
1) `env.bat`
Set NodeJs env var.

2) `extract.bat video.mp4 tmpmv 272 176 8 15 256`
tmpmv: output folder
272: frame width
176: frame height
8: rows per strip
15: frame rate
256: color reduction per frame (optional param)

3) Use nodejs custom app tiledpalettequant (this isn't public yet) to generate all RGB images with palettes data.
Once rgb images with palettes were generated and before saving them ensure the next config:
- in _SGDK settings_ section:
	- check _Switch 2 Palettes positions_
	- check _Start at [PAL0,PAL1] first_
	- enter 22 (strips per frame) at input _Reset every N strips (This only needed if strips per frame is an odd number)_
- Download the images and move them at res\rgb folder.

4) `node header_generator.js 272 176 8 15`
frame width: 272
frame height: 176
rows per strip: 8
frame rate: 15

5) `compile_n_run.bat`
Run it once to catch rescomp output to know max tiles number and then:
- manually set VIDEO_FRAME_TILESET_CHUNK_SIZE constant at `videoPlayer.h`.
- manually set VIDEO_FRAME_TILESET_TOTAL_SIZE constant at `videoPlayer.h`.
- edit `res/ext.resource.properties` and update same constants having suffix SPLIT2 or SPLIT3 accordingly to your case.
rom.bin generated at out folder.  
Blastem binary location set in the bat script.  


### NOTES
- I recommend to use a video resize and filter program like *VirtualDub 2*, which allows you to keep image crisp when resizing, 
uses custom ratio with black regions when resizing, lets you crop the video, and also comes with all kind of useful filters. 
That way the extract.bat script which calls ffmpeg will only extract the frames without resizing and extract the audio in correct 
format for the SGDK rescomp tool.


### TODO
- If the use of new compression/decompression methods speed up the decompression over Stef's LZ4W then:
	- use new video (better definition and correct width and height) from VirtualDub2 project.
	- new dims are 272 width x 200 height (34 * 25 tiles).
	- update this README.txt steps.
- Try MOVIE_FRAME_RATE 15 once I finished all optimization changes.
- Idea: call waitVInt_AND_flushDMA() with immediate flag so it starts flushing DMA. 
	- Move the enable/disable VDP into HInt (use conditions when not need to start the pals swap steps).
- Idea to avoid sending the first 2 strips'pals and send only first strip's pals:
	- DMA_QUEUE the first 32 colors (2 pals) at VInt.
	- Use flushQueue from Stef's dma_a.s and flushQueue(DMA_getQueueSize())
	- Add +32 and -32 accordingly in VInt and videoPlayer.c.
	- Set HINT_PALS_CMD_ADDRR_RESET_VALUE to 32 in movieHVInterrupts.h.
	- Hint now starts 1 row of tiles more than the already calculated in movieHVInterrupts.h.
	- DMA_init() needs more capacity now.
- Once the unpack/load of tileset/tilemap/pals happen during the time of an active display loop we can:
	- discard palInFrameRootPtr and just use the setPalsPointer() call made in waitVInt_AND_flushDMA() without the bool parameter resetPalsPtrsForHInt.
	- remove #define #if FORCE_NO_MISSING_FRAMES
	- remove the condition if (!((prevFrame ^ vFrame) & 1))
	- use ++dataPtr; instead of dataPtr += vFrame - prevFrame; (same for palsDataPtr)
	- search for TODO PALS_1 and act accordingly.
	- If the first 2 strips' pals are DMA_QUEUE in waitVInt_AND_flushDMA() then use flushQueue from Stef's dma_a.s and flushQueue(DMA_getQueueSize())
- Clear mem used by sound when exiting the video loop?
- Try using XGM PCM driver:
	- extract audio with sample rate 14k (or 13.3k for XGMv2 yet to be released)
		(I used VirtualDub2 to extract as 8bit signed 14KHz 1 channel (mono), but the extract.bat script is the correct approach and needs to be updated)
	- in movie_sound.res: WAV sound_wav "sound/sound.wav" XGM
	- Use the XGM_PCM methods
- Implement custom rescomp plugin to create a cache of most common tiles.
	- Read all tiles from all frames and count their occurrences so we can cached them at the start of tileset VRAM section.
	- At least 34 tiles should be cached = 34*32=1088 bytes
	- Check for big frames (min 600 tiles?) that at least have cached 34 tiles.
	- Then in SGDK pre load cached tiles into VRAM for both tile index 1 and 716+1.
- Try 20 FPS NTSC (16 FPS PAL). So 60/3=20 in NTSC. And 50/3=16 in PAL.
	- update MOVIE_FRAME_RATE at movie_data_consts.h.
	- update README.md steps 2 and 4.
	- update videoPlayer in order to do the unpack/load of all the elements along 3 display loops.
- Could declaring the arrays data[] y pals_data[] directly in ASM reduce rom size and/or speed access?
- Try to change from H40 to H32 on HInt Callback, and hope for any any speed gain?
	See https://plutiedev.com/mirror/kabuto-hardware-notes#h40-mode-tricks
	See http://gendev.spritesmind.net/forum/viewtopic.php?p=17683&sid=e64d28235b5b42d96b82483d4d71d34b#p17683


----
### (OLD/OUTDATED) Instructions using custom quantization lua script
1) env.bat

2) extract.bat "video.mp4" tmpmv 16

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

6) node header_generator.js

7) compile_n_run.bat
rom.bin generated at out folder
Blastem binary location set in the bat script
