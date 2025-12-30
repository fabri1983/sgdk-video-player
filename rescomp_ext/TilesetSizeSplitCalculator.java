package sgdk.rescomp.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.type.Tile;

public class TilesetSizeSplitCalculator {

	/**
	 * Splits the tileset in numChunks by maximizing the number of tiles per chunk but never 
	 * exceeding maxTilesPerChunk. This way the first chunks* holds most of the tiles, while the remaining 
	 * chunks holds the reminder possible.</br>
	 *
	 * @param tileset
	 * @param numChunks How many chunks we want to split the tilemap
	 * @param maxTilesPerChunk
	 * @return The list with the splitted tilesets
	 */
	public static List<List<Tile>> splitWithMaxTilesFirst (List<Tile> tileset, int numChunks, int maxTilesPerChunk)
	{
		List<List<Tile>> result = new ArrayList<>(numChunks);
		
		if (numChunks <= 1 || maxTilesPerChunk <= 0) {
			// Add the entire tileset as first element
			result.add(tileset);
			// Complete remaining of the list with empty tilesets
			for (int i=1; i < numChunks; ++i) {
				result.add(Collections.emptyList());
			}
			return result;
		}

		int totalTiles = tileset.size();
		int index = 0;

		for (int i = 0; i < numChunks; i++) {
			if (index >= totalTiles) {
				// No tiles left - add empty chunk
				result.add(Collections.emptyList());
				continue;
			}

			int remainingTiles = totalTiles - index;
			int size = Math.min(maxTilesPerChunk, remainingTiles);

			List<Tile> chunk = tileset.subList(index, index + size);
			result.add(chunk);

			index += size;
		}

		return result;
	}

	/**
	 * Splits the tileset in numChunks by distributing as much evenly as possible the number 
	 * of tiles for the given numChunks, without exceeding maxTilesPerChunk per chunk.</br>
	 *
	 * @param tileset
	 * @param numChunks How many chunks we want to split the tilemap
	 * @param maxTilesPerChunk
	 * @return The list with the splitted tilesets
	 */
	public static List<List<Tile>> splitWithMaxEvenlyDistribution (List<Tile> tileset, int numChunks, int maxTilesPerChunk)
	{
		List<List<Tile>> result = new ArrayList<>(numChunks);

		if (numChunks <= 1 || maxTilesPerChunk <= 0) {
			
			// Add the entire tileset as first element
			result.add(tileset);
			// Complete remaining of the list with empty tilesets
			for (int i=1; i < numChunks; ++i) {
				result.add(Collections.emptyList());
			}
			return result;
		}

		int totalTiles = tileset.size();

		// Compute baseline even distribution
		int base = totalTiles / numChunks; // ideal even base
		int remainder = totalTiles % numChunks; // extra tiles to distribute 1 per chunk

		// Cap base at maxTilesPerChunk
		if (base > maxTilesPerChunk) {
			base = maxTilesPerChunk;
			remainder = 0; // no remainder distribution possible if base already at max
		} else {
			// If base <= max, remainder assignment must also observe the max cap
			if (base + 1 > maxTilesPerChunk) {
				remainder = 0; // cannot add remainder tiles without exceeding the cap
			}
		}

		int index = 0;

		for (int i = 0; i < numChunks; i++) {
			if (index >= totalTiles) {
				result.add(Collections.emptyList());
				continue;
			}

			int size = base;
			if (remainder > 0) {
				size += 1;
				remainder--;
			}

			// Make sure we don't read past the available tiles
			int end = Math.min(index + size, totalTiles);

			result.add(tileset.subList(index, end));
			index = end;
		}

		return result;
	}

}
