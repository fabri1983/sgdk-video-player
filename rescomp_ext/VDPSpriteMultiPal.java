package sgdk.rescomp.resource.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.Bin;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.SpriteCell;
import sgdk.rescomp.type.Tile;

public class VDPSpriteMultiPal extends Resource
{
    public final int offsetX;
    public final int offsetY;
    public final int wt;
    public final int ht;
    public final int offsetYFlip;
    public final int offsetXFlip;
    public final int palId;

    final int hc;

    public VDPSpriteMultiPal(String id, int offX, int offY, int w, int h, int wf, int hf, int palId)
    {
        super(id);

        if ((offX < 0) || (offX > 255) || (offY < 0) || (offY > 255))
            throw new IllegalArgumentException("Error: sprite '" + id + "' offset X / Y is out of range (< 0 or > 255)");
        // if ((offX < -128) || (offX > 127) || (offY < -128) || (offY > 127))
        // throw new IllegalArgumentException(
        // "Error: sprite '" + id + "' offset X / Y is out of range (< -128 or > 127)");

        this.offsetX = offX;
        this.offsetY = offY;
        this.wt = w;
        this.ht = h;
        this.offsetXFlip = (wf * 8) - (offX + (w * 8));
        this.offsetYFlip = (hf * 8) - (offY + (h * 8));
        this.palId = palId;

        // compute hash code
        hc = (offsetX << 0) ^ (offsetXFlip << 0) ^ (offsetY << 8) ^ (offsetYFlip << 8) ^ (wt << 16) ^ (ht << 24) ^ (palId << 30);
    }

    public VDPSpriteMultiPal(String id, SpriteCell sprite, int wf, int hf, int palId)
    {
        this(id, sprite.x, sprite.y, sprite.width / 8, sprite.height / 8, wf, hf, palId);
    }

    public int getFormattedSize()
    {
        return ((wt - 1) << 2) | (ht - 1);
    }
    
    void internalOutS(StringBuilder outS)
    {
        // respect field order: offsetY, offsetYFlip, size, offsetX, offsetXFlip, numTile, palId, offsetSharedTiles
        outS.append("    dc.w    " + ((offsetY << 8) | ((offsetYFlip << 0) & 0xFF)) + "\n");
        outS.append("    dc.w    " + ((getFormattedSize() << 8) | ((offsetX << 0) & 0xFF)) + "\n");
        outS.append("    dc.w    " + ((offsetXFlip << 8) | (((ht * wt) << 0) & 0xFF)) + "\n");
        outS.append("    dc.w    " + (palId << Tile.TILE_PALETTE_SFT) + "\n");
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof VDPSpriteMultiPal)
        {
            final VDPSpriteMultiPal vdpSprite = (VDPSpriteMultiPal) obj;
            return (offsetX == vdpSprite.offsetX) && (offsetY == vdpSprite.offsetY) && (wt == vdpSprite.wt) && (ht == vdpSprite.ht)
                    && (offsetXFlip == vdpSprite.offsetXFlip) && (offsetYFlip == vdpSprite.offsetYFlip) && (palId == vdpSprite.palId);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return new ArrayList<>();
    }

    @Override
    public String toString()
    {
        return id + ": [" + offsetX + "," + offsetY + "-" + (wt * 8) + "," + (ht * 8) + "," + palId + "]";
    }

    @Override
    public int shallowSize()
    {
        return 8;
    }

    @Override
    public int totalSize()
    {
        return shallowSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH) throws IOException
    {
        // FrameVDPSprite structure
        Util.decl(outS, outH, "FrameVDPSpriteWithPal", id, 2, global);

        internalOutS(outS);
        // write to binary buffer, respect field order: offsetY, offsetYFlip, size, offsetX, offsetXFlip, numTile
        outB.write(offsetY);
        outB.write(offsetYFlip);
        outB.write(getFormattedSize());
        outB.write(offsetX);
        outB.write(offsetXFlip);
        outB.write(ht * wt);
        outB.write(palId);

        outS.append("\n");
    }
}
