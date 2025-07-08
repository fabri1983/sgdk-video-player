package sgdk.rescomp.tool;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * RLE compression algorithm that combines Variable-Length Encoding, Block Based Encoding, and Run-Length Limited (RLL) Coding.</br>
 * The source array is treated as a multi row of words, each row is treated as an independent block of RLE encoding with a limited max length.</br>
 * It runs 3 RLE phases which aim to reduce the size of the final encoding, with some configurable parameters to slightly speedup decompression.</br>
 * <b>NOTE:</b> only supports arrays up to 63 words due to 6 bits dedicated for length.</br>
 * IS USEFUL IF YOUR TARGET MAP BUFFER HAS AN EXTENDED WIDTH TO [32, 64, 128] TILES (GOOD FOR FASTER DMA OPERATION).</br>
 * THIS WAY YOU CAN DECOMPRESS A BLOCK AND LEAVE UNTOUCHED THE EXTRA SPACE USED TO FULFILL THE WIDTH UP TO [32, 64, 128] TILES.</br>
 *  
 * @author fabri1983
 */
public class RLEWCompressor {

	public static final String RLE_PROPERTY_SUFFIX_WORDS_PER_ROW = "_WORDS_PER_ROW";

	/**
	 * Only 6 bits used for the length (in words), hence (2^6)-1=63.
	 */
	private static final int RLE_MAX_RUN_LENGTH = 63;
	/**
	 * Value must be >= 2</br>
	 * Play with this value to see how much the size of the encoded output changes.</br>
	 * This has an impact in the unpack algorithm time..
	 */
	private static final int RLE_MIN_SEQUENCE_OF_INCREMENTAL_OCCURRENCES = Integer.MAX_VALUE; // Use a big value to disable this strategy
	/**
	 * Value must be >= 2</br>
	 * Play with this value to see how much the size of the encoded output changes.</br>
	 * This has an impact in the unpack algorithm time. SMALLER values produce slightly faster decompression 
	 * because the copy of words is straight forward avoiding intermediate checks for descriptors and lengths.
	 */
	private static final int RLE_MIN_SEQUENCE_OF_LENGTH_1_OCCURRENCE = 2;
	/**
	 * Value must be >= 2.</br>
	 * Play with this value to see how much the size of the encoded output changes.</br>
	 * This has an impact in the unpacker algorithm time. BIGGER values produce slightly faster decompression 
	 * because the preparation of the high common byte into a word consumes time, plus the additional checks for 
	 * descriptors and lengths in case the sequences are short.
	 */
	private static final int RLE_MIN_COMMON_HIGH_BYTE_SEQUENCE = 2;
	/**
	 * Maximum ratio of reduction between phase 2 and phase 3. Put in other words, it says that phase 3 
	 * encoding size must be <= than the N percentage of phase 2 encoding size.   
	 */
	private static final double RLE_THRESHOLD_PHASE_2_TO_PHASE_3 = 0.8;

	public static class WordInfo {
		int count;
		List<Integer> posInEncodedRLE;

		public WordInfo() {
			this.count = 0;
			this.posInEncodedRLE = new ArrayList<>();
		}
	}

	/**
	 * Method A: lower compression ratio but faster decompression time.<br/>
	 * Compress an array of words using RLE for 16 bits words. Only up to {@link RLEWCompressor#RLE_MAX_RUN_LENGTH} word per row.
	 * @param data
	 * @param binId Used to extract from the properties file some parameters
	 * @return
	 */
	public static byte[] compress_A (byte[] data, String binId) {
		if ((data.length % 2) != 0)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": data[] length " + data.length + " + is not even");

		// this is the width in words of the data region containing valid data (not the extended width in the case of a map).
		// up to RLE_MAX_RUN_LENGTH because we use 6 bits for the length
		int wordsPerRow = getIntProperty(binId + RLE_PROPERTY_SUFFIX_WORDS_PER_ROW);
		if (wordsPerRow == 0) {
			wordsPerRow = RLE_MAX_RUN_LENGTH;
			System.out.println("WARN: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow was invalid, now is " + RLE_MAX_RUN_LENGTH);
		}
		// ensure it does not exceeds the limit, otherwise throw error
		if (wordsPerRow > RLE_MAX_RUN_LENGTH) {
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow > " + RLE_MAX_RUN_LENGTH);
		}

		byte[] packed = methodA(data, wordsPerRow);

		checkCorrectRowLength_A(packed, wordsPerRow);
//		Map<String, WordInfo> wordInfo_A = decodeRLEforStats_A(packed);
//		printStats(wordInfo_A);

		final int rows = data.length / (wordsPerRow * 2); // multiply by 2 because every word entry is 2 bytes
		byte[] packedWithHeader = addHeader(packed, rows);
		byte[] packedFinal = addParityBytes_A(packedWithHeader);
//		printAsHexa(packedFinal);
		return packedFinal;
	}

	/**
	 * Method B: higher compression ratio but slower decompression time.<br/>
	 * Compress an array of words using RLE for 16 bits words. Only up to {@link RLEWCompressor#RLE_MAX_RUN_LENGTH} words per row.
	 * @param data
	 * @param binId Used to extract from the properties file some parameters
	 * @return
	 */
	public static byte[] compress_B (byte[] data, String binId) {
		if ((data.length % 2) != 0)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": data[] length " + data.length + " + is not even");

		// this is the width in words of the data region containing valid data (not the extended width in the case of a map).
		// up to RLE_MAX_RUN_LENGTH because we use 6 bits for the length
		int wordsPerRow = getIntProperty(binId + RLE_PROPERTY_SUFFIX_WORDS_PER_ROW);
		if (wordsPerRow == 0) {
			wordsPerRow = RLE_MAX_RUN_LENGTH;
			System.out.println("WARN: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow was invalid, now is " + RLE_MAX_RUN_LENGTH);
		}
		// ensure it does not exceeds the limit, otherwise throw error
		if (wordsPerRow > RLE_MAX_RUN_LENGTH) {
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow > " + RLE_MAX_RUN_LENGTH);
		}

		byte[] packed = methodB(data, wordsPerRow);

		checkCorrectRowLength_B(packed, wordsPerRow);
//		Map<String, WordInfo> wordInfo_B = decodeRLEforStats_B(packed);
//		printStats(wordInfo_B);

		final int rows = data.length / (wordsPerRow * 2); // multiply by 2 because every word entry is 2 bytes
		byte[] packedWithHeader = addHeader(packed, rows);
		byte[] packedFinal = addParityBytes_B(packedWithHeader);
//		printAsHexa(packedFinal);
		return packedFinal;
	}

	/**
	 * If the tilemap data[] array was filled per row with extra space to achieve a desired width, 
	 * this function extracts the tilemap data and discards the extra space.
	 * @param data
	 * @param origTilesWidthPerRow how many valid tilemap entries per row the data[] array is. Must be <= <b>extTilesWidthPerRow</b> (when the later is not 0).
	 * @param extTilesWidthPerRow values: [0, 32, 64, 128]
	 * @return
	 */
	public static byte[] extractTilemapDataOnly_byte (byte[] data, int origTilesWidthPerRow, int extTilesWidthPerRow) {
		if (extTilesWidthPerRow == 0)
			return data;
		if (origTilesWidthPerRow > extTilesWidthPerRow)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": origTilesWidthPerRow must be <= extTilesWidthPerRow");
		int rows = data.length / (extTilesWidthPerRow * 2); // multiply by 2 because every tilemap entry is 2 bytes
		byte[] result = new byte[rows * origTilesWidthPerRow * 2]; // multiply by 2 because every tilemap entry is 2 bytes
		for (int i=0; i < rows; ++i) {
			System.arraycopy(data, i * extTilesWidthPerRow*2, result, i * origTilesWidthPerRow*2, origTilesWidthPerRow*2);
		}
		return result;
	}

	/**
	 * If the tilemap data[] array was filled per row with extra space to achieve a desired width, 
	 * this function extracts the tilemap data and discards the extra space.
	 * @param data
	 * @param origTilesWidthPerRow how many valid tilemap entries per row the data[] array is. Must be <= <b>extTilesWidthPerRow</b> (when the later is not 0).
	 * @param extTilesWidthPerRow values: [0, 32, 64, 128]
	 * @return
	 */
	public static short[] extractTilemapDataOnly_short (short[] data, int origTilesWidthPerRow, int extTilesWidthPerRow) {
		if (extTilesWidthPerRow == 0)
			return data;
		if (origTilesWidthPerRow > extTilesWidthPerRow)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": origTilesWidthPerRow must be <= extTilesWidthPerRow");
		int rows = data.length / extTilesWidthPerRow;
		short[] result = new short[rows * origTilesWidthPerRow];
		for (int i=0; i < rows; ++i) {
			System.arraycopy(data, i * extTilesWidthPerRow, result, i * origTilesWidthPerRow, origTilesWidthPerRow);
		}
		return result;
	}
	
	/**
	 * Compress an array of words using RLE for 16 bits words. Only up to {@link RLEWCompressor#RLE_MAX_RUN_LENGTH} words per row.
	 * @param data
	 * @param wordsPerRow
	 * @return
	 */
	private static byte[] methodA (byte[] data, int wordsPerRow) {

		// PHASE 1: basic RLE
		// Uses a byte descriptor to hold the run length (first 6 LSBs) followed by a word (2 bytes) value.
		// The byte descriptor holds the end of row bit at its MSB: 1 if true, 0 if not.

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		int accumWordsThisRow = 0;

		for (int i = 0; i < data.length; i += 2) {
			// Combine two bytes into a word
			int currentWord = ((data[i] & 0xFF) << 8) | ((data[i + 1] & 0xFF));
			int runLength = 1;
			accumWordsThisRow++; // current word being analyzed per row

			while ((i+2) < data.length && runLength < RLE_MAX_RUN_LENGTH && accumWordsThisRow < wordsPerRow && 
					(data[i] == data[i + 2]) && (data[i + 1] == data[i + 3])) {
				runLength++;
				accumWordsThisRow++;
				i += 2;
			}

			boolean setEndOfRowBit = false;
			if (accumWordsThisRow == wordsPerRow) {
				accumWordsThisRow = 0;
				setEndOfRowBit = true;
			}

			ByteBuffer buffer = ByteBuffer.allocate(3);
			byte rleDescriptor = (byte) (runLength & 0b00111111); // keep only first 6 bits

			if (setEndOfRowBit)
				rleDescriptor = (byte) (rleDescriptor | 0b10000000); // set the bit marking end of row

			buffer.put(rleDescriptor); // store RLE byte descriptor
			buffer.putShort((short) currentWord); // store word value
			outputStream.write(buffer.array(), 0, 3);
		}

		byte[] rleArrayPhase1 = outputStream.toByteArray();

		// PHASE 2:
		// Now transform consecutive words having RLE byte descriptor with length 1 into one stream of at least N words.
		// The new RLE byte descriptor for such streams has its 2nd MSB set as 1 followed by the length of words to copy, 
		// and with the MSB indicating if is end of row.
		// The rest of the encoded RLE stays the same if the stream criteria is not met.

		List<Byte> rleArrayPhase2List = new ArrayList<>(rleArrayPhase1.length);

		int i = 0;
		while (i < rleArrayPhase1.length) {
			byte rleDescriptor = rleArrayPhase1[i];
			int length = rleDescriptor & 0b00111111;

			// Check if the length is 1 (single word repeat)
			if (length == 1) {
				i = collectWordsIntoStream_A(rleArrayPhase1, rleArrayPhase2List, i);
			}
			// Segment's length > 1 => copy the segment as it is
			else {
				rleArrayPhase2List.add(rleDescriptor);
				rleArrayPhase2List.add(rleArrayPhase1[i + 1]); // word high byte
				rleArrayPhase2List.add(rleArrayPhase1[i + 2]); // word low byte
				i += 3;
			}
		}

		byte[] rleArrayPhase2 = convertToByteArray(rleArrayPhase2List);
		return rleArrayPhase2;
	}

	private static int collectWordsIntoStream_A (byte[] source, List<Byte> target, int i) {
		// Start collecting a stream of single-word repeats
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 0;
		boolean isEndOfRow = false;

		// Scan forward to count how many consecutive words with length == 1 we find
		while (i < source.length && (source[i] & 0b00111111) == 1 && !isEndOfRow) {
			isEndOfRow = (source[i] & 0b10000000) != 0;
			sequenceLength++;
			i += 3; // Move to the next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_LENGTH_1_OCCURRENCE) {
			// Prepare the new descriptor with the 2nd MSB set as 1 followed by the length
			byte newDescriptor = (byte) (0b01000000 | (sequenceLength & 0b00111111));
			// Set the end of row bit?
			if (isEndOfRow)
				newDescriptor = (byte) (newDescriptor | 0b10000000);

			target.add(newDescriptor);

			// Add the collected words
			for (int j = 0; j < sequenceLength; j++) {
				target.add(source[sequenceStart + 1 + 3*j]); // word high byte
				target.add(source[sequenceStart + 2 + 3*j]); // word low byte
			}
		}
		// Not enough length to form a stream, then copy the segments as it is
		else {
			// Copy descriptor + word
			for (int j = sequenceStart; j < (sequenceStart + 3 * sequenceLength); j++)
				target.add(source[j]);
		}

		return i;
	}

	/**
	 * Compress an array of words data using RLE for 16 bits words. Only up to {@link RLEWCompressor#RLE_MAX_RUN_LENGTH} words per row.
	 * @param data
	 * @param wordsPerRow
	 * @return
	 */
	private static byte[] methodB (byte[] data, int wordsPerRow) {

		// PHASE 1: basic RLE
		// Uses a byte descriptor to hold the run length (first 6 LSBs) followed by a word (2 bytes) value.
		// Uses an additional byte with value 0 to mark the end of row.

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		int accumWordsThisRow = 0;

		for (int i = 0; i < data.length; i += 2) {
			// Combine two bytes into a word
			int currentWord = ((data[i] & 0xFF) << 8) | ((data[i + 1] & 0xFF));
			int runLength = 1;
			accumWordsThisRow++; // current word being analyzed per row

			while ((i+2) < data.length && runLength < RLE_MAX_RUN_LENGTH && accumWordsThisRow < wordsPerRow && 
					(data[i] == data[i + 2]) && (data[i + 1] == data[i + 3])) {
				runLength++;
				accumWordsThisRow++;
				i += 2;
			}

			boolean setEndOfRowBit = false;
			if (accumWordsThisRow == wordsPerRow) {
				accumWordsThisRow = 0;
				setEndOfRowBit = true;
			}

			ByteBuffer buffer = ByteBuffer.allocate(4);
			byte rleDescriptor = (byte) (runLength & 0b00111111); // keep only first 6 bits
			buffer.put(rleDescriptor); // store RLE byte descriptor
			buffer.putShort((short) currentWord); // store word value
			
			if (setEndOfRowBit) {
				buffer.put((byte)0); // add byte 0 to mark the end of a row
				outputStream.write(buffer.array(), 0, 4);
			}
			else {
				outputStream.write(buffer.array(), 0, 3);
			}
		}

		byte[] rlePhase1Array = outputStream.toByteArray();
		outputStream.reset();
		outputStream = null;

		// PHASE 2:
		List<Byte> rlePhase2List = new ArrayList<>(rlePhase1Array.length);

		for (int i = 0; i < rlePhase1Array.length;) {

			byte rleDescriptor = rlePhase1Array[i];
			
			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == 0) {
				rlePhase2List.add(rleDescriptor);
				++i;
			}
			// Try to find an incremental RLE segment only if segment is length 1
			else if ((rleDescriptor & 0b00111111) == 1) {
				i = collectIncrementalRLE_B(rlePhase1Array, rlePhase2List, i);
			}
			// Copy the RLE segment
			else {
				rlePhase2List.add(rleDescriptor);
				rlePhase2List.add(rlePhase1Array[i + 1]); // word high byte
				rlePhase2List.add(rlePhase1Array[i + 2]); // word low byte
				i += 3;
			}
		}

		byte[] rlePhase2Array = convertToByteArray(rlePhase2List);
		rlePhase1Array = null;
		rlePhase2List.clear();

		// PHASE 3:
		// Now transform consecutive words having RLE byte descriptor with length 1 into one stream of at least N words.
		// The new RLE byte descriptor for such streams has 1 as its MSB and the length in the 6 LSBs.
		// The rest of the encoded RLE stays the same if the stream criteria is not met.

		List<Byte> rlePhase3List = new ArrayList<>(rlePhase2Array.length);

		for (int i = 0; i < rlePhase2Array.length;) {

			byte rleDescriptor = rlePhase2Array[i];

			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == 0) {
				rlePhase3List.add(rleDescriptor);
				++i;
			}
			// Check if the length is 1 (single word repeat)
			else if ((rleDescriptor & 0b00111111) == 1) {
				i = collectWordsIntoStream_B(rlePhase2Array, rlePhase3List, i);
			}
			// Segment's length > 1
			else {
				// If descriptor's mask matches 0b01...... then we have an incremental RLE segment
				if ((byte)(rleDescriptor & 0b11000000) == (byte)0b01000000) {
					rlePhase3List.add(rleDescriptor);
					rlePhase3List.add(rlePhase2Array[i + 1]); // operand
					rlePhase3List.add(rlePhase2Array[i + 2]); // word high byte
					rlePhase3List.add(rlePhase2Array[i + 3]); // word low byte
					i += 4;
				}
				// Others
				else {
					rlePhase3List.add(rleDescriptor);
					rlePhase3List.add(rlePhase2Array[i + 1]); // word high byte
					rlePhase3List.add(rlePhase2Array[i + 2]); // word low byte
					i += 3;
				}
			}
		}

		byte[] rlePhase3Array = convertToByteArray(rlePhase3List);
		rlePhase2Array = null;
		rlePhase3List.clear();

		// PHASE 4:
		// Process the streams of words and extract at least N consecutive words having the same high byte. 
		// This way the common high byte can be included once at the beginning of the stream and then continue 
		// with the low byte of every remaining word in the stream. This saves up to <50% in the best case.

		List<Byte> rlePhase4List = new ArrayList<>(rlePhase3Array.length);

		for (int i = 0; i < rlePhase3Array.length; ) {

			byte rleDescriptor = rlePhase3Array[i];

			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == 0) {
				rlePhase4List.add(rleDescriptor);
				++i;
				continue;
			}

			// If descriptor has the stream bit set then we're going to analyze the stream
			if ((byte)(rleDescriptor & 0b10000000) != (byte)0) {
				i = compressStreamCommonHighBytes_B(rlePhase3Array, rlePhase4List, i);
			}
			// Others
			else {
				// If descriptor's mask matches 0b01...... then we have an incremental RLE segment
				if ((byte)(rleDescriptor & 0b11000000) == (byte)0b01000000) {
					rlePhase4List.add(rleDescriptor);
					rlePhase4List.add(rlePhase3Array[i + 1]); // operand
					rlePhase4List.add(rlePhase3Array[i + 2]); // word high byte
					rlePhase4List.add(rlePhase3Array[i + 3]); // word low byte
					i += 4;
				}
				// Others
				else {
					rlePhase4List.add(rleDescriptor);
					rlePhase4List.add(rlePhase3Array[i + 1]); // word high byte
					rlePhase4List.add(rlePhase3Array[i + 2]); // word low byte
					i += 3;
				}
			}
		}

		// Convert List<Byte> to byte[]
		byte[] rlePhase4Array = convertToByteArray(rlePhase4List);
		return rlePhase4Array;
	}

	private static int collectIncrementalRLE_B (byte[] source, List<Byte> target, int i) {
		// Start collecting a sequence of incremental words
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 1; // we start counting the first word is already in because the comparison is between 2 words
		byte[] operand = {0}; // initial operand value always 0

		// Scan forward to count how many consecutive words with an incremental nature we find.
		// We only interesting in RLE segments of length 1
		while ((i+5) < source.length && source[i] != 0 && source[i+3] != 0 && (source[i] & 0b00111111) == 1 
				&& (source[i+3] & 0b00111111) == 1 && keepSameOperand(source, i, operand)) {
			sequenceLength++;
			i += 3; // Move to the next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_INCREMENTAL_OCCURRENCES) {
			// Accommodate for the last word included in the segment
			i += 3; // Move to the next descriptor
			// Set the mask to tell this is an incremental RLE segment, including the length
			byte newDescriptor = (byte) (0b01000000 | (sequenceLength & 0b00111111));
			target.add(newDescriptor);
			target.add(operand[0]);
			target.add(source[sequenceStart + 1]); // word high byte
			target.add(source[sequenceStart + 2]); // word low byte
		}
		// Not enough length to form an incremental RLE, then copy the segments as it is
		else {
			// Accommodate for the last word used in the condition above
			i += 3; // Move to the next descriptor
			// Copy descriptor + word
			for (int j = sequenceStart; j < (sequenceStart + 3 * sequenceLength); j++)
				target.add(source[j]);
		}

		return i;
	}

	private static boolean keepSameOperand(byte[] source, int i, byte[] operand) {
		// Both high bytes must be the same so the incremental nature only applies in the lower bytes
//		if (source[i+1] != source[i+4])
//			return false;
		// Combine two bytes into a word
		int a = ((source[i+1] & 0xFF) << 8) | ((source[i+2] & 0xFF));
		int b = ((source[i+4] & 0xFF) << 8) | ((source[i+5] & 0xFF));
		byte previousOp = operand[0];
		operand[0] = (byte)(b - a); // Java casting to byte preserves sign
		return a != b && (previousOp == 0 || previousOp == operand[0]);
	}

	private static int collectWordsIntoStream_B (byte[] source, List<Byte> target, int i) {
		// Start collecting a stream of single-word repeats
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 0;

		// Scan forward to count how many consecutive words with length == 1 we find
		while (i < source.length && (source[i] & 0b00111111) == 1) {
			sequenceLength++;
			i += 3; // Move to the next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_LENGTH_1_OCCURRENCE) {
			// Set the MSB to 1 to tell this is a stream of words, followed by the length of 
			// the stream in the remaining 6 LSBs
			byte newDescriptor = (byte) (0b10000000 | (sequenceLength & 0b00111111));
			target.add(newDescriptor);

			// Add the collected words
			for (int j = 0; j < sequenceLength; j++) {
				target.add(source[sequenceStart + 3*j + 1]); // word high byte
				target.add(source[sequenceStart + 3*j + 2]); // word low byte
			}
		}
		// Not enough length to form a stream, then copy the segments as it is
		else {
			// Copy descriptor + word
			for (int j = sequenceStart; j < (sequenceStart + 3 * sequenceLength); j++)
				target.add(source[j]);
		}

		return i;
	}

	private static int compressStreamCommonHighBytes_B (byte[] rleArrayPhase2, List<Byte> rleArrayPhase3List, int i) {
		List<Byte> tempCollectorListPass1 = new ArrayList<>();
		int streamStartAt = i;
		int streamLength = rleArrayPhase2[i] & 0b00111111;
		++i; // Move to the high byte of the first word in the stream

		// Traverse all the stream of words
		for (int streamLenAux = streamLength; streamLenAux > 0; ) {
			int sequenceStart = i;
			int sequenceLength = 0;
			byte currentHighByte = rleArrayPhase2[i];

			// Scan forward to count how many consecutive words with same high byte we actually find
			while (i < rleArrayPhase2.length && rleArrayPhase2[i] == currentHighByte && streamLenAux > 0) {
				sequenceLength++;
				i += 2; // Move to the next word's high byte in the stream
				--streamLenAux; // one word less in the stream
			}

			// If at least 2 words share the same high byte then we can compress them
			if (sequenceLength >= RLE_MIN_COMMON_HIGH_BYTE_SEQUENCE) {
				// Set the first 2 MSBs to 1 to tell this is a stream of bytes using a common high byte for the following N bytes.
				byte newDescriptor = (byte) (0b11000000 | (sequenceLength & 0b00111111));
				tempCollectorListPass1.add(newDescriptor);
				tempCollectorListPass1.add(currentHighByte);
				// Add every low byte
				for (int j = 0; j < sequenceLength; j++)
					tempCollectorListPass1.add(rleArrayPhase2[sequenceStart + 1 + 2*j]); // word's low byte
			}
			// Not enough length to compress the words, then copy every word as a RLE of length 1
			else {
				for (int j = sequenceStart; j < (sequenceStart + 2 * sequenceLength); j += 2) {
					// Use a RLE descriptor with length 1 so we can convert them into a stream later on
					tempCollectorListPass1.add((byte) 0b00000001);
					// Collect the word
					tempCollectorListPass1.add(rleArrayPhase2[j]);
					tempCollectorListPass1.add(rleArrayPhase2[j+1]);
				}
			}
		}

		// Convert List<Byte> to byte[]
		byte[] tempCollectorArrayPass1 = convertToByteArray(tempCollectorListPass1);

		List<Byte> tempCollectorListPass2 = new ArrayList<>();

		// Now traverse the previous list and perform RLE only over the words having descriptor 0b10000001
		for (int j = 0; j < tempCollectorArrayPass1.length;) {

			byte descriptor = tempCollectorArrayPass1[j];

			// If the descriptor is a RLE of length 1 (previously set on purpose) then we're going to process this and 
			// and consecutive words trying to collect them into a stream
			if (descriptor == (byte) 0b00000001) {
				j = collectWordsIntoStream_B(tempCollectorArrayPass1, tempCollectorListPass2, j);
			}
			// If the descriptor is the one marking a high common byte, then we just collect the sequence
			else if ((byte)(descriptor & 0b11000000) == (byte)0b11000000) {
				tempCollectorListPass2.add(descriptor);
				j++; // move to the high common byte
				tempCollectorListPass2.add(tempCollectorArrayPass1[j++]); // collect the high common byte and move forward
				int len = descriptor & 0b00111111;
				for (int k = 0; k < len; ++k)
					tempCollectorListPass2.add(tempCollectorArrayPass1[j++]);
			}
			else
				throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": descriptor is not expected.");
		}

		// Resulting array must be at least 80% smaller than the original stream of words
		if (tempCollectorListPass2.size() <= ((1 + streamLength * 2) * RLE_THRESHOLD_PHASE_2_TO_PHASE_3))
			rleArrayPhase3List.addAll(tempCollectorListPass2);
		// Otherwise just copy all the original stream of words
		else
			rleArrayPhase3List.addAll(convertToByteList(rleArrayPhase2, streamStartAt, streamStartAt + 1 + streamLength * 2));

		return i;
	}

	private static byte[] convertToByteArray (List<Byte> list) {
		byte[] array = new byte[list.size()];
		for (int k = 0; k < list.size(); k++) {
			array[k] = list.get(k);
		}
		return array;
	}

	private static List<Byte> convertToByteList (byte[] array, int startAt, int endAtExclusive) {
		List<Byte> list = new ArrayList<>(1 + endAtExclusive - startAt);
		for (int k = startAt; k < endAtExclusive; k++) {
			list.add(array[k]);
		}
		return list;
	}

	private static byte[] addHeader (byte[] rleData, int mapTilesPerRow) {
		byte[] newArray = new byte[rleData.length + 1];
		newArray[0] = (byte) mapTilesPerRow;
		System.arraycopy(rleData, 0, newArray, 1, rleData.length);
		return newArray;
	}

	private static byte[] addParityBytes_A (byte[] rleData) {
		List<Byte> list = new ArrayList<>(rleData.length); // initial capacity
		int index = 0;
		boolean firstRun = true;

		// first byte is the header
		list.add(rleData[index++]);

		// visit the rest of the array
		while (index < rleData.length) {
			byte descriptor = rleData[index++];
			
			// if not the first run then we add parity byte and then copy with the segment
			if (!firstRun)
				list.add((byte) 0); // add the parity byte

			list.add(descriptor);

			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			if ((byte)(descriptor & 0b01000000) == (byte)0) {
				// copy the word
				list.add(rleData[index++]);
				list.add(rleData[index++]);
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				// copy the words
				int length = descriptor & 0x3F; // First 6 bits for length
				for (int i = 0; i < length; i++) {
					list.add(rleData[index++]);
					list.add(rleData[index++]);
				}
			}
			
			firstRun = false;
		}

		return convertToByteArray(list);
	}

	private static byte[] addParityBytes_B (byte[] rleData) {
		List<Byte> list = new ArrayList<>(rleData.length); // initial capacity
		int index = 0;
		int offsetAccum = 0;

		// first byte is the header
		list.add(rleData[index++]);

		// visit the rest of the array
		while (index < rleData.length) {
			byte descriptor = rleData[index++];
			list.add(descriptor);

			// descriptor == 0 is the mark for end of row
			if (descriptor == 0) {
				continue;
			}

			// test if descriptor's mask matches 0b00...... then we have basic RLE segment
			if ((byte)(descriptor & 0b11000000) == (byte)0b00000000) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte) 0b01000000); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				// copy the word
				list.add(rleData[index++]);
				list.add(rleData[index++]);
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & 0b11000000) == (byte)0b01000000) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isOdd(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte) 0b01000000); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				// copy the operand
				list.add(rleData[index++]);
				// copy the word
				list.add(rleData[index++]);
				list.add(rleData[index++]);
			}
			// test if descriptor's mask matches 0b10...... then is a stream of words
			else if ((byte)(descriptor & 0b11000000) == (byte)0b10000000) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte) 0b01000000); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				// copy the words
				int length = descriptor & 0x3F; // First 6 bits for length
				for (int i = 0; i < length; i++) {
					list.add(rleData[index++]);
					list.add(rleData[index++]);
				}
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte) 0b01000000); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				list.add(rleData[index++]); // common high byte
				int length = (descriptor & 0x3F); // length is first 6 bits
				// copy the bytes
				for (int i = 0; i < length; i++)
					list.add(rleData[index++]);
			}
		}
		
		return convertToByteArray(list);
	}

	private static boolean isEven(int i, int offset) {
		return ((i + offset) % 2) == 0;
	}

	private static boolean isOdd(int i, int offset) {
		return ((i + offset) % 2) == 1;
	}

	private static void checkCorrectRowLength_A(byte[] rleData, int wordsPerRow) {
		int rowLengthAccum = 0;
		int index = 0;

		while (index < rleData.length) {
			byte descriptor = rleData[index++];

			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			if ((byte)(descriptor & 0b01000000) == (byte)0) {
				int length = descriptor & 0x3F; // First 6 bits for length
				rowLengthAccum += length;
				index += 2; // consume the word
				// if MSB is set then it marks end of row
				if ((descriptor & 0b10000000) != 0) {
					if (rowLengthAccum != wordsPerRow)
						throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + " method A: wrong number of words in Simple RLE row.");
					rowLengthAccum = 0;
				}
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				int length = descriptor & 0x3F; // First 6 bits for length
				rowLengthAccum += length;
				index += 2 * length; // consume the all the words
				// if MSB is set then it marks end of row
				if ((descriptor & 0b10000000) != 0) {
					if (rowLengthAccum != wordsPerRow)
						throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + " method A: wrong number of words in RLE Stream row.");
					rowLengthAccum = 0;
				}
			}
		}
	}
	
	private static void checkCorrectRowLength_B(byte[] rleData, int wordsPerRow) {
		int rowLengthAccum = 0;
		int index = 0;

		while (index < rleData.length) {
			byte descriptor = rleData[index++];

			// descriptor == 0 is the mark for end of row
			if (descriptor == 0) {
				if (rowLengthAccum != wordsPerRow)
					throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + " method B: wrong number of words in row.");
				rowLengthAccum = 0;
				continue;
			}

			int length = descriptor & 0x3F; // First 6 bits for length
			rowLengthAccum += length;

			// test if descriptor's mask matches 0b00...... then we have basic RLE segment
			if ((byte)(descriptor & 0b11000000) == (byte)0b00000000) {
				index += 2; // consume the word
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & 0b11000000) == (byte)0b01000000) {
				index++; // consume operand
				index += 2; // consume the word
			}
			// test if descriptor's mask matches 0b10...... then is a stream of words
			else if ((byte)(descriptor & 0b11000000) == (byte)0b10000000) {
				index += 2 * length; // consume all the words
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else {
				index++; // consume common high byte
				index += length; // consume all the lower bytes
			}
		}
	}

	private static void printStats (Map<String, WordInfo> wordInfoMap) {
		// Collect entries into a list and sort by count in descending order
		List<Entry<String, WordInfo>> sortedEntries = wordInfoMap.entrySet().stream()
				.filter( e -> e.getValue().count > 1)
				.sorted((e1, e2) -> Integer.compare(e2.getValue().count, e1.getValue().count))
				.collect(Collectors.toList());

		// Print the sorted results
		System.out.println();
		for (Entry<String, WordInfo> entry : sortedEntries) {
			String word = entry.getKey();
			WordInfo info = entry.getValue();
			System.out.println("Word: " + word + ", Count: " + info.count + ", Pos in encoded RLE: " + info.posInEncodedRLE);
		}
	}

	private static Map<String, WordInfo> decodeRLEforStats_A (byte[] rleData) {
		Map<String, WordInfo> wordInfoMap = new HashMap<>();
		int index = 0;

		while (index < rleData.length) {
			int currentDescriptorPos = index;
			byte descriptor = rleData[index++];

			// test if descriptor != 0 then we have basic RLE entry
			if (descriptor != 0) {
				int word = ((rleData[index++] & 0xFF) << 8) | (rleData[index++] & 0xFF);
				updateWordInfo(wordInfoMap, word, currentDescriptorPos);
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				int length = descriptor & 0x3F; // First 6 bits for length
				// track position and occurrences of every word in the stream
				for (int i = 0; i < length; i++) {
					int word = ((rleData[index++] & 0xFF) << 8) | (rleData[index++] & 0xFF);
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + i);
				}
			}
		}
		return wordInfoMap;
	}

	private static Map<String, WordInfo> decodeRLEforStats_B (byte[] rleData) {
		Map<String, WordInfo> wordInfoMap = new HashMap<>();
		int index = 0;

		while (index < rleData.length) {
			int currentDescriptorPos = index;
			byte descriptor = rleData[index++];

			// descriptor == 0 is the mark for end of row
			if (descriptor == 0) {
				continue;
			}
			
			// test if descriptor's mask matches 0b00...... then we have basic RLE segment
			if ((byte)(descriptor & 0b11000000) == (byte)0b00000000) {
				int word = ((rleData[index++] & 0xFF) << 8) | (rleData[index++] & 0xFF);
				updateWordInfo(wordInfoMap, word, currentDescriptorPos);
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & 0b11000000) == (byte)0b01000000) {
				int length = descriptor & 0x3F; // First 6 bits for length
				byte operand = rleData[index++]; // operand
				int word = (rleData[index++] << 8) | (rleData[index++] & 0xFF);
				for (int i = 0; i < length-1; i++) {
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + 1 + i);
					word = word + operand;
				}
			}
			// test if descriptor's mask matches 0b10...... then is a stream of words
			else if ((byte)(descriptor & 0b11000000) == (byte)0b10000000) {
				int length = descriptor & 0x3F; // First 6 bits for length
				// track position and occurrences of every word in the stream
				for (int i = 0; i < length; i++) {
					int word = ((rleData[index++] & 0xFF) << 8) | (rleData[index++] & 0xFF);
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + i);
				}
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else {
				int length = descriptor & 0x3F; // First 6 bits for length
				byte commonHighByte = rleData[index++];
				for (int i = 0; i < length; i++) {
					int word = (commonHighByte << 8) | (rleData[index++] & 0xFF);
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + i);
				}
			}
		}
		return wordInfoMap;
	}

	private static void updateWordInfo (Map<String, WordInfo> wordInfoMap, int word, int posInEncodedRLE) {
		String wordStr = String.format("%04X", word); // Format the word as hexadecimal
		WordInfo wordInfo = wordInfoMap.computeIfAbsent(wordStr, k -> new WordInfo());
		wordInfo.count++;
		wordInfo.posInEncodedRLE.add(posInEncodedRLE);
	}

	private static void printAsHexa (byte[] array) {
		System.out.println("");
		StringBuilder hexString = new StringBuilder(array.length*3);
        for (int i = 0; i < array.length; i++) {
            hexString.append(String.format("%02X", array[i]));
            if (i < array.length - 1) {
                hexString.append(", ");
            }
        }
        System.out.println(hexString.toString());
	}
	
	private static int getIntProperty (String key) {
		String value = System.getProperty(key);
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}
}
