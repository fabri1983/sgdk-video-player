## sgdk-video-player

A video converter and video player made with the [SGDK v2.12](https://github.com/Stephane-D/SGDK) for the Megadrive/Genesis system consoles.  
Originally inspired by [sgdk-video-player](https://github.com/haroldo-ok/sgdk-video-player) made by _haroldo-ok_.  


**Work In Progress. Tested on Blastem, Nuked-MD, and real hardware.**

For convenience testing you can directly try the last compiled rom [videoplayer_rom.bin](videoplayer_rom.bin?raw=true "videoplayer_rom.bin").

[Watch the demo video on YouTube](https://www.youtube.com/watch?v=mE4HLnmD0g0)

You can find me in the SGDK Discord server: https://discord.gg/xmnBWQS


### Features

- Supports up to 256+ colors per frame.
- Currently running at 11-15 FPS in NTSC and 11-12 FPS in PAL, with a frame size of 272 x 192 pixels (34 x 24 tiles).
- Uses custom extensions for [Stef's SGDK rescomp tool](https://github.com/Stephane-D/SGDK/blob/master/bin/rescomp.txt).
- Uses custom tiledpalettequant app (not public yet).


### Config theese first:

- You need *Image Magick v7.x* tools set in _PATH_.
- You need *ffmpeg* set in the _PATH_.
- Set `ENABLE_BANK_SWITCH` 1 in _SGDK_'s `config.h` for rom size bigger than 4MB, and re build the _SGDK_ lib.
- You need *NodeJs* and its *NODEJS_HOME* env var properly set on user/system variables. This is required for `env.bat`.


### Instructions using custom tiledpalettequant app

1) `env.bat`
Sets NodeJs env var.

2) By default we use resolution 272 x 192 pixels for the video processing and displaying.
If you change that resolution you need to update constant `VIDEO_FRAME_PLANE_ADDRESS` at `videoPlayer.h`. 
See `checkCorrectPlaneAddress()` in `videoPlayer.c` to get in context.
Then edit accordingly while going through next steps.

3) `extract.bat video.mp4 tmpmv 272 192 8 15 n`
This extract frames from the input video and then generates the strips per frame with the correct color depth.
tmpmv: output folder
272: frame width (multiple of 8)
192: frame height (multiple of 8)
8: height per strip. Use 0 or frame height to skip strips creation
15: frame rate
n: color reduction (optional parameter). Max value 256 (for PNGs).

4) Use nodejs custom app `tiledpalettequant` (not public yet) to generate all RGB images with palettes data.
Once rgb images with palettes were generated and before saving them ensure the next config:
    - In _SGDK settings_ section:
        - check _Switch 2 Palettes positions_
        - check _Start at [PAL0,PAL1] first_
        - enter 24 (192/8=24 strips per frame) (This only needed if strips per frame is an odd number)_
    - Download the images and move them into `res\rgb` folder.

5) Edit cache tiles configuration to analyze new tiles
Open file `res_n_header_generator.js` and edit variables `enableTilesCacheStats` and `loadTilesCache` accordingly.
Read the comments to get an idea of how they work.

6) `node res_n_header_generator.js 272 192 8 15`
272: frame width (multiple of 8)
192: frame height (multiple of 8)
8: height per strip. Use 0 or frame height to skip strips creation
15: frame rate

7) `compile_n_run.bat release`
Run it once to catch rescomp output to know tileset stats (resource TILESET_STATS_COLLECTOR). Then:
    - Edit `res/ext.resource.properties` and update next constants:
        - MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
        - MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
        - MAX_TILESET_CHUNK_<N>_SIZE_FOR_SPLIT_IN_<split> (with suffix SPLIT2 or SPLIT3 accordingly to your case)
        - MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX
    - Run step **5** again.
`rom.bin` generated at out folder.  
`Blastem's binary` location is set inside the bat script (edit accordingly or add *--no-emu* parameter).


### NOTES

- I recommend to use a video resize and filter program like *VirtualDub 2*, which allows you to keep a crisp image when resizing, 
uses custom ratio with black regions when resizing, lets you crop the video, and also comes with all kind of useful filters. 
That way the `extract.bat` script, which calls ffmpeg, will only extract the frames without any resizing, and then extract the audio 
in correct format for the SGDK rescomp tool.


### TODO

- Update joy like in raycasting project.
- Pre load frame 0 before starting music and see how does result with sound timing/sync.
- Try new video from VirtualDub2 project. Better definition and correct dimensions. Frame size: 272 x 200 px (34 x 25 tiles).
- Idea to avoid sending the first 2 strips palettes (64 colors) in the HInt and send only first strip palettes (32 colors):
	- DMA_ELEMS_queue the first 2 pals (32 colors) at VInt.
	- Add +32 and -32 accordingly in VInt and videoPlayer.c.
	- Set HINT_PALS_CMD_ADDRR_RESET_VALUE to 32 in movieHVInterrupts.h.
	- Effective Hint now starts 1 strip ahead than the already used in the first conditions on the many hint callbacks in movieHVInts.h.
- Once the unpack/load of tileset/tilemap/pals happen during the time of an active display loop we can:
	- discard palInFrameRootPtr and just use the setPalsPointer() as it currently is.
	- remove #define #if FORCE_NO_MISSING_FRAMES
	- remove the condition if (!((prevFrame ^ vFrame) & 1))
    - Effective Hint now starts 2 strips ahead than the already used in the first conditions on the many hint callbacks in movieHVInts.h.
	- search for TODO PALS_1 and act accordingly.
- Try final frame size: 288 x 208 px (36 x 26 tiles).
- Could declaring the arrays data[] and pals_data[] directly in ASM reduce rom size and/or speed access?
- Clear mem used by sound when exiting the video loop?
- Try to change from H40 to H32 (or was it viceversa?) on Horizontal Blank (that tiny time outbounds the screen), and hope for any speed gain?
	- See https://plutiedev.com/mirror/kabuto-hardware-notes#h40-mode-tricks
	- See http://gendev.spritesmind.net/forum/viewtopic.php?p=17683&sid=e64d28235b5b42d96b82483d4d71d34b#p17683
	- This technique: https://gendev.spritesmind.net/forum/viewtopic.php?f=22&t=2964&sid=395ed554dbdeb24d2a5b64c29a0abd03&start=15#p35118


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
