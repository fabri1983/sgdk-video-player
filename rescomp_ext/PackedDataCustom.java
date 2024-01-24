package sgdk.rescomp.type;

import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.PackedData;

public class PackedDataCustom extends PackedData {

	public CompressionCustom compressionCustom;

	public PackedDataCustom(byte[] data, CompressionCustom compressionCustom) {
		super(data, Compression.AUTO);
		this.compressionCustom = compressionCustom;
	}

}
