package sgdk.rescomp.type;

import java.util.List;

public class CommonTilesRange {

	private int numTiles;
	/*
	 * Index in the original list of images. Only useful for the optimizer algorithm and statistics.
	 */
	private int startingImgIdx;
	/*
	 * Index in the original list of images. Only useful for the optimizer algorithm and statistics.
	 */
	private int endingImgIdx;
	private List<Tile> tiles;
	private int startingImgNumInName;
	private int endingImgNumInName;

	public CommonTilesRange (int numTiles, int startingImgIdx, int endingImgIdx, List<Tile> tiles, int startingImgNumInName, int endingImgNumInName) {
		super();
		this.numTiles = numTiles;
		this.startingImgIdx = startingImgIdx;
		this.endingImgIdx = endingImgIdx;
		this.tiles = tiles;
		this.startingImgNumInName = startingImgNumInName;
		this.endingImgNumInName = endingImgNumInName;
	}

	public int getNumTiles() {
		return numTiles;
	}

	public void setNumTiles(int numTiles) {
		this.numTiles = numTiles;
	}

	/*
	 * Index in the original list of images. Only useful for the optimizer algorithm and statistics.
	 */
	public int getStartingImgIdx() {
		return startingImgIdx;
	}

	public void setStartingImgIdx(int startingImgIdx) {
		this.startingImgIdx = startingImgIdx;
	}

	/*
	 * Index in the original list of images. Only useful for the optimizer algorithm and statistics.
	 */
	public int getEndingImgIdx() {
		return endingImgIdx;
	}

	public void setEndingImgIdx(int endingImgIdx) {
		this.endingImgIdx = endingImgIdx;
	}

	public List<Tile> getTiles() {
		return tiles;
	}

	public void setTiles(List<Tile> tiles) {
		this.tiles = tiles;
	}

	public int getStartingImgNumInName() {
		return startingImgNumInName;
	}

	public void setStartingImgNumInName(int startingImgNumInName) {
		this.startingImgNumInName = startingImgNumInName;
	}

	public int getEndingImgNumInName() {
		return endingImgNumInName;
	}

	public void setEndingImgNumInName(int endingImgNumInName) {
		this.endingImgNumInName = endingImgNumInName;
	}

	@Override
	public String toString() {
		return "{" + "numTiles=" + numTiles + ", startingImgIdx=" + startingImgIdx + ", endingImgIdx=" + endingImgIdx + '}';
	}

}
