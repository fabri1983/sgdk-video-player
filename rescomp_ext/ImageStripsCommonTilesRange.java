package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.CommonTilesRangeManager;
import sgdk.rescomp.tool.CommonTilesRangeOptimizerV1;
import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CommonTilesRange;
import sgdk.rescomp.type.CommonTilesRangeResData;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.Tile;

public class ImageStripsCommonTilesRange extends Resource
{
    final int hc;
    final List<CommonTilesRangeResData> commonTilesRangeResData;

    public ImageStripsCommonTilesRange(String id, List<List<String>> allStripsInList, String tilesCacheId, int minCommonTilesNum, boolean enable, 
    		CompressionCustom compressionCustom) throws Exception
    {
        super(id);
 
        if (enable) {
        	final Compression compression;
        	final CompressionCustom compressionCustomFinal;
	        if (CompressionCustom.isOneOfSgdkCompression(compressionCustom)) {
	        	compression = CompressionCustom.getSgdkCompression(compressionCustom);
	        	compressionCustomFinal = CompressionCustom.NONE;
	        }
	        else {
	        	compression = Compression.NONE;
	        	compressionCustomFinal = compressionCustom;
	        }

	        final int maxCommonTilesNum = ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
        	List<CommonTilesRange> optimizedRangeList = CommonTilesRangeOptimizerV1.generateOptimizedCommonTiles(
        			allStripsInList, tilesCacheId, minCommonTilesNum, maxCommonTilesNum);
//	        List<CommonTilesRange> optimizedRangeList = CommonTilesRangeOptimizerV2.generateOptimizedCommonTiles(
//	        		allStripsInList, tilesCacheId, minCommonTilesNum, maxCommonTilesNum);

        	CommonTilesRangeManager.saveForResId(id, optimizedRangeList);

        	// Generates bin resource for each list of tiles
        	commonTilesRangeResData = optimizedRangeList.stream()
        			.map( range -> {
        				String binId = id + "_" + range.getStartingImgIdx() + "_" + range.getEndingImgIdx() + "_bin";
        				// build the binary bloc
        		        final int[] data = new int[range.getNumTiles() * 8];
        		        int offset = 0;
        		        for (Tile t : range.getTiles())
        		        {
        		            System.arraycopy(t.data, 0, data, offset, 8);
        		            offset += 8;
        		        }
        		        // build BIN (tiles data) with wanted compression
        		        final BinCustom binResource = new BinCustom(binId + "_data", data, compression, compressionCustomFinal);
        		        // internal
        		        binResource.global = false;
        		        final BinCustom bin = (BinCustom) addInternalResource(binResource);
        				return new CommonTilesRangeResData(range.getNumTiles(), range.getStartingImgIdx(), range.getEndingImgIdx(), bin);
        			})
        			.collect(Collectors.toList());
        }
        else
        	commonTilesRangeResData = Collections.emptyList();

		// compute hash code
		hc = commonTilesRangeResData.hashCode();
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof ImageStripsCommonTilesRange)
        {
            final ImageStripsCommonTilesRange inst = (ImageStripsCommonTilesRange) obj;
            return hc == inst.hc;
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return new ArrayList<>();
    }

    @Override
    public int shallowSize()
    {
    	return (2 + 2 + 2 + 4) * commonTilesRangeResData.size();
    }

    @Override
    public int totalSize()
    {
    	int totalBinDataSize = commonTilesRangeResData.stream().map(CommonTilesRangeResData::getBin).mapToInt(BinCustom::totalSize).sum();
    	return shallowSize() + totalBinDataSize;
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// Store the array under name id
		if (!commonTilesRangeResData.isEmpty()) {

			Util.declArray(outS, outH, CustomDataTypes.ImageCommonTilesRange.getValue(), id, commonTilesRangeResData.size(), 2, global);
			commonTilesRangeResData.forEach( range -> {
				outS.append("    dc.w    " + range.getStartingIdx() + "\n");
				outS.append("    dc.w    " + range.getEndingIdx() + "\n");
				outS.append("    dc.w    " + range.getNumTiles() + "\n");
				outS.append("    dc.l    " + range.getBin().id + "\n");
			});
			Util.declArrayEnd(outS, outH, CustomDataTypes.ImageCommonTilesRange.getValue(), id, commonTilesRangeResData.size(), 2, global);
			
			outS.append("\n");
			
			// Declare as a constant how many elements the array holds
			String arraySizeDeclarationConstant = "#define " + id + "_NUM_ELEMS " + commonTilesRangeResData.size() + "\n\n";
			outH.append(arraySizeDeclarationConstant);
			
			// Declare as a constant the use of this resource
			String definitionOfUse = "#define COMMON_TILES_IN_RANGE\n\n";
			outH.append(definitionOfUse);
		}
    }
}
