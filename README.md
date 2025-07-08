## sgdk-video-player
A video converter and video player ready to use with the [SGDK v2.0](https://github.com/Stephane-D/SGDK) for the Megadrive/Genesis system consoles.  
Originally inspired by [sgdk-video-player](https://github.com/haroldo-ok/sgdk-video-player) made by _haroldo-ok_.  


**Work In Progress. Only tested with Blastem and Nuked-MD.**

For convenience testing you can directly try the last compiled rom [videoplayer_rom.bin](videoplayer_rom.bin?raw=true "videoplayer_rom.bin").


### Features
- Supports up to 256+ colors per frame.
- Supports both NTSC and PAL systems.
- Currently running at 10~15 FPS in NTSC and 10~12 FPS in PAL, with a frame size of 272x192 pixels.
- Uses custom extensions for the [Stef's SGDK rescomp tool](https://github.com/Stephane-D/SGDK/blob/master/bin/rescomp.txt).


### Config theese first:
- You need *Image Magick v7.x* tools set in _PATH_.
- You need *ffmpeg* set in the _PATH_.
- Set `ENABLE_BANK_SWITCH` 1 in _SGDK_'s `config.h` if the rom size is bigger than 4MB, and re build the _SGDK_ lib.
- You need *NodeJs* and set *NODEJS_HOME* in your user/system variables.


### Instructions using custom tiledpalettequant app
1) `env.bat`
Set NodeJs env var.

2) By default we use resolution 272x192 pixels for the video processing and displaying.
If you change that resolution you need to update constant `VIDEO_FRAME_PLANE_ADDRESS`. See `checkCorrectPlaneAddress()` in `videoPlayer.c`.
Then edit accordingly while going through next steps.

3) `extract.bat video.mp4 tmpmv 272 192 8 15 n`
tmpmv: output folder
272: frame width (multiple of 8)
192: frame height (multiple of 8)
8: rows per strip
15: frame rate
n: color reduction (optional parameter). Max value 256 (for PNGs).

4) Use nodejs custom app `tiledpalettequant` (this isn't public yet) to generate all RGB images with palettes data.
Once rgb images with palettes were generated and before saving them ensure the next config:
- in _SGDK settings_ section:
	- check _Switch 2 Palettes positions_
	- check _Start at [PAL0,PAL1] first_
	- enter 24 (192/8=24 strips per frame) at input _Reset every N strips (This only needed if strips per frame is an odd number)_
- Download the images and move them into `res\rgb` folder.

5) Edit cache tiles configuration to analyze new tiles
Open file `res_n_header_generator.js` and edit variables `enableTilesCacheStats` and `loadTilesCache` accordingly.
Read the comments to get an idea of how they work.

6) `node res_n_header_generator.js 272 192 8 15`
frame width: 272 (multiple of 8)
frame height: 192 (multiple of 8)
rows per strip: 8
frame rate: 15

7) `compile_n_run.bat release`
Run it once to catch rescomp output to know tileset stats (resource TILESET_STATS_COLLECTOR). Then:
- edit `res/ext.resource.properties` and update next constants:
	- MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
	- MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
	- MAX_TILESET_CHUNK_<N>_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
	- MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX
- run step **5** again.
`rom.bin` generated at out folder.  
`Blastem's binary` location is set inside the bat script (edit accordingly or add *--no-emu* parameter).


### NOTES
- I recommend to use a video resize and filter program like *VirtualDub 2*, which allows you to keep a crips image when resizing, 
uses custom ratio with black regions when resizing, lets you crop the video, and also comes with all kind of useful filters. 
That way the `extract.bat` script, which calls ffmpeg, will only extract the frames without any resizing, and then extract the audio 
in correct format for the SGDK rescomp tool.


### TODO
- Update joy like in raycasting project.
- Try Enigma on tilemaps and check if the optimized decompressor is faster than current timings.
- Pre load frame 0 before starting music and see how does result with sound timing/sync.
- The Tileset chunk decompression worst case takes 249052 cycles (~519 scanlines) including all the delays added by VInt and Hint interrupts.
- Try new video from VirtualDub2 project. Better definition and correct dimensions. Frame size: 272 x 200 px (34 x 25 tiles).
- Idea to avoid sending the first 2 strips'pals (64 colors) and send only first strip's pals (32 colors):
	- DMA_QUEUE the first 2 pals (32 colors) at VInt.
	- Use flushQueue from Stef's: flushQueue(DMA_getQueueSize())
	- Add +32 and -32 accordingly in VInt and videoPlayer.c.
	- Set HINT_PALS_CMD_ADDRR_RESET_VALUE to 32 in movieHVInterrupts.h.
	- Hint now starts 1 row of tiles more than the already calculated in movieHVInterrupts.h.
	- DMA_init() needs more capacity now.
- Once the unpack/load of tileset/tilemap/pals happen during the time of an active display loop we can:
	- discard palInFrameRootPtr and just use the setPalsPointer() call made in waitVInt_AND_flushDMA() without the bool parameter resetPalsPtrsForHInt.
	- remove #define #if FORCE_NO_MISSING_FRAMES
	- remove the condition if (!((prevFrame ^ vFrame) & 1))
	- search for TODO PALS_1 and act accordingly.
	- If the first 2 strips' pals are DMA_QUEUE in waitVInt_AND_flushDMA() then use flushQueue from Stef's: flushQueue(DMA_getQueueSize())
- Try 20 FPS NTSC (16 FPS PAL). So 60/3=20 in NTSC. And 50/3=16 in PAL.
	- update MOVIE_FRAME_RATE at res_n_header_generator.js.
	- update README.md steps 2 and 4.
	- update videoPlayer in order to do the unpack/load of all the elements along 3 display loops.
- Try final frame size: 288 x 208 px (36 x 26 tiles).
- Could declaring the arrays data[] and pals_data[] directly in ASM reduce rom size and/or speed access?
- Clear mem used by sound when exiting the video loop?
- Try to change from H40 to H32 (or was it viceversa?) on HInt Callback, and hope for any any speed gain?
	See https://plutiedev.com/mirror/kabuto-hardware-notes#h40-mode-tricks
	See http://gendev.spritesmind.net/forum/viewtopic.php?p=17683&sid=e64d28235b5b42d96b82483d4d71d34b#p17683
	This technique: https://gendev.spritesmind.net/forum/viewtopic.php?f=22&t=2964&sid=395ed554dbdeb24d2a5b64c29a0abd03&start=15#p35118


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
