package sgdk.rescomp.type;

public class TileCacheMatch {

	private Tile tile;
	private int indexInCache;

	public TileCacheMatch(Tile tile, int indexInCache) {
		super();
		this.tile = tile;
		this.indexInCache = indexInCache;
	}

	public Tile getTile() {
		return tile;
	}

	public int getIndexInCache() {
		return indexInCache;
	}

}
