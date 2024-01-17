package sgdk.rescomp.type;

import sgdk.rescomp.type.Basics.Compression;

public class TilemapCreationData {

	public String id;
	public short[] data;
	public int w;
	public int h;
	public Compression compression;
	public CompressionCustom compressionCustom;

	public TilemapCreationData(String id, short[] data, int w, int h, Compression compression, CompressionCustom compressionCustom) {
		super();
		this.id = id;
		this.data = data;
		this.w = w;
		this.h = h;
		this.compression = compression;
		this.compressionCustom = compressionCustom;
	}
}