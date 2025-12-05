package sgdk.rescomp.type;

import sgdk.rescomp.resource.BinCustom;

public class CommonTilesRangeResData {

	private int numTiles;
	private int startingIdx;
	private int endingIdx;
	private BinCustom bin;

	public CommonTilesRangeResData (int numTiles, int startingIdx, int endingIdx, BinCustom bin) {
		super();
		this.numTiles = numTiles;
		this.startingIdx = startingIdx;
		this.endingIdx = endingIdx;
		this.bin = bin;
	}

	public int getNumTiles() {
		return numTiles;
	}

	public int getStartingIdx() {
		return startingIdx;
	}

	public void setStartingIdx(int startingIdx) {
		this.startingIdx = startingIdx;
	}

	public int getEndingIdx() {
		return endingIdx;
	}

	public void setEndingIdx(int endingIdx) {
		this.endingIdx = endingIdx;
	}

	public BinCustom getBin() {
		return bin;
	}

	public void setBin(BinCustom bin) {
		this.bin = bin;
	}

}
