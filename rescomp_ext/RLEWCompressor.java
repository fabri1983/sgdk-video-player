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
 * RLE compression algorithm that combines Variable-Length Encoding, Block Based Encoding, Run-Length Limited (RLL) Encoding, and Back Reference Encoding.</br>
 * The source array is treated as a multi row of words, each row is treated as an independent block of RLE encoding with a limited max 
 * length of {@link RLEWCompressor#RLE_MAX_RUN_LENGTH} words due to the 6 bits dedicated for length.</br>
 * It runs 3 RLE phases which aim to reduce the size of the final encoding, with some configurable parameters to slightly speedup decompression.</br>
 * IS USEFUL IF YOUR TARGET TILEMAP HAS AN EXTENDED WIDTH TO [32, 64, 128] TILES (GOOD FOR FASTER DMA OPERATION (NOTE: there is a faster way!)).</br>
 * THIS WAY YOU CAN DECOMPRESS A BLOCK AND LEAVE UNTOUCHED THE EXTRA SPACE USED TO FULFILL THE WIDTH UP TO [32, 64, 128] TILES.</br>
 *  
 * @author fabri1983
 */
public class RLEWCompressor {

	public static final String RLE_PROPERTY_SUFFIX_WORDS_PER_ROW = "_WORDS_PER_ROW";

	/**
	 * Only 6 bits used for the length value, but we leave 0x3F value for special treatment.
	 */
	private static final int RLE_MAX_RUN_LENGTH = 62;

	/**
	 * Only 6 bits used for the length: (2^6)-1 = 63
	 */
	private static final int LENGTH_MASK = 0b00111111;

	private static final int BITS_DESCRIPTOR_MASK = 0b11000000;

	private static final int BIT_END_OF_ROW_A = 0b10000000;
	private static final int BIT_STREAM_OF_WORDS_A = 0b01000000;
	private static final int BYTE_BACKWARD_REF_A = 0b00111111;

	private static final int BYTE_END_OF_ROW_B = 0;
	private static final int BITS_INCREMENTAL_RLE_B = 0b01000000;
	private static final int BITS_STREAM_OF_WORDS_B = 0b10000000;
	private static final int BITS_HIGH_COMMON_BYTE_B = 0b11000000;

	private static final int PARITY_BYTE_A = 0;
	private static final int PARITY_BYTE_B = 0b01000000;

	private static final int MIN_LENGTH_STREAM_OF_WORDS_FOR_BACKWARD_REF = 4;
	/**
	 * Strategy 1 rapidly searches for best matching stream, but is also considered by Strategy 2
	 */
	private static final boolean ENABLE_BACKWARD_REF_STRATEGY_1 = false;
	/**
	 * Strategy 2 searches for the best matching stream accounting different sizes for a stream.
	 */
	private static final boolean ENABLE_BACKWARD_REF_STRATEGY_2 = true;
	private static final String CMD_RLE = "RLE";
	private static final String CMD_STREAM_WORDS = "STREAM_WORDS";

	/**
	 * Value must be >= 2</br>
	 * Play with this value to see how much the size of the encoded output changes.</br>
	 * This has an impact in the unpack algorithm time.</br>
	 * Use a big value to disable this strategy.
	 */
	private static final int RLE_MIN_SEQUENCE_OF_INCREMENTAL_OCCURRENCES = Integer.MAX_VALUE;
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

		int wordsPerRow = getWordsPerRow(binId);

		byte[] packed = methodA(data, wordsPerRow);

		checkCorrectRowLength_A(packed, wordsPerRow);
//		Map<String, WordInfo> wordInfo_A = decodeRLEforStats_A(packed);
//		printStats(wordInfo_A);

		int rows = data.length / (wordsPerRow * 2); // converts wordsPerRow into bytes
		if (rows >= 256)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": rows >= 256");
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

		int wordsPerRow = getWordsPerRow(binId);

		byte[] packed = methodB(data, wordsPerRow);

		checkCorrectRowLength_B(packed, wordsPerRow);
//		Map<String, WordInfo> wordInfo_B = decodeRLEforStats_B(packed);
//		printStats(wordInfo_B);

		int rows = data.length / (wordsPerRow * 2); // converts wordsPerRow into bytes
		if (rows >= 256)
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": rows >= 256");
		byte[] packedWithHeader = addHeader(packed, rows);
		byte[] packedFinal = addParityBytes_B(packedWithHeader);
//		printAsHexa(packedFinal);
		return packedFinal;
	}

	private static int getWordsPerRow(String binId) {
		// this is the width in words of the data region containing valid data (not the extended width in the case of a tilemap).
		int wordsPerRow = getIntProperty(binId + RLE_PROPERTY_SUFFIX_WORDS_PER_ROW);
		if (wordsPerRow == 0) {
			wordsPerRow = RLE_MAX_RUN_LENGTH;
			System.out.println("WARN: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow was invalid, now is " + RLE_MAX_RUN_LENGTH);
		}
		// ensure it does not exceeds the limit, otherwise throw error
		if (wordsPerRow > RLE_MAX_RUN_LENGTH) {
			throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": wordsPerRow > " + RLE_MAX_RUN_LENGTH);
		}
		return wordsPerRow;
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
		ByteBuffer byteBuffer = ByteBuffer.allocate(3);

		for (int i = 0; i < data.length; i += 2) {
			// Combine two bytes into a word
			int currentWord = ((data[i] & 0xFF) << 8) | ((data[i + 1] & 0xFF));
			int runLength = 1;
			accumWordsThisRow++;

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

			byte rleDescriptor = (byte) (runLength & LENGTH_MASK);
			if (setEndOfRowBit)
				rleDescriptor = (byte) (rleDescriptor | BIT_END_OF_ROW_A); // set the bit marking end of row

			byteBuffer.clear();
			byteBuffer.put(rleDescriptor); // store RLE byte descriptor
			byteBuffer.put((byte)((currentWord >> 8) & 0xFF)); // store word's higher byte
			byteBuffer.put((byte)(currentWord & 0xFF)); // store word's lower byte
			outputStream.write(byteBuffer.array(), 0, 3);
		}

		byte[] rleArrayPhase1 = outputStream.toByteArray();
		byteBuffer = null;
		outputStream.reset();
		outputStream = null;

		// PHASE 2:
		// Now transform consecutive words having RLE byte descriptor with length 1 into one stream of at least N words.
		// The new RLE byte descriptor for such streams has its 2nd MSB set as 1 followed by the length of words to copy, 
		// and with the MSB indicating if is end of row.
		// The rest of the encoded RLE stays the same if the stream criteria is not met.

		List<Byte> rleArrayPhase2List = new ArrayList<>(rleArrayPhase1.length);

		for (int i=0; i < rleArrayPhase1.length;) {
			byte rleDescriptor = rleArrayPhase1[i];
			int length = rleDescriptor & LENGTH_MASK;

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

		// PHASE 3:
		// Backward reference optimization.
		// Search for duplicated commands. Use new byte BYTE_BACKWARD_REF to mark a back reference command,
		// followed by a word with the distance in words to jump backwards, followed by a byte with the length of words to copy.

		List<Byte> rleArrayPhase3List = encodeBackReference_A(rleArrayPhase2List);

		rleArrayPhase2List = null;
		byte[] rleArrayPhase3 = convertToByteArray(rleArrayPhase3List);
		return rleArrayPhase3;
	}

	private static int collectWordsIntoStream_A (byte[] source, List<Byte> target, int i) {
		// Start collecting a stream of single-word repeats
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 0;
		boolean hasEndOfRowBit = false;

		// Scan forward to count how many consecutive words with length == 1 we find
		while (i < source.length && (source[i] & LENGTH_MASK) == 1 && !hasEndOfRowBit) {
			hasEndOfRowBit = (source[i] & BIT_END_OF_ROW_A) != 0;
			sequenceLength++;
			i += 3; // Move to the next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_LENGTH_1_OCCURRENCE) {
			// Prepare the new descriptor with the 2nd MSB set as 1 followed by the length
			byte newDescriptor = (byte) (BIT_STREAM_OF_WORDS_A | (sequenceLength & LENGTH_MASK));
			// Set the end of row bit?
			if (hasEndOfRowBit)
				newDescriptor = (byte) (newDescriptor | BIT_END_OF_ROW_A);
			target.add(newDescriptor);
			sequenceStart++; // consume the descriptor

			// Add the collected words
			for (int j = 0; j < sequenceLength; j++) {
				target.add(source[sequenceStart + 0 + 3*j]); // word high byte
				target.add(source[sequenceStart + 1 + 3*j]); // word low byte
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

	private static List<Byte> encodeBackReference_A(List<Byte> rleArrayPhase2List) {
		List<String> entryCommand = new ArrayList<>(rleArrayPhase2List.size());
		List<Integer> entryIndex = new ArrayList<>(rleArrayPhase2List.size());
		List<Integer> entryIndexDecoded = new ArrayList<>(rleArrayPhase2List.size());
		List<Integer> entryLengthInWords = new ArrayList<>(rleArrayPhase2List.size());

		// Collect commands and the index locations in coded rle buffer and decoded buffer
		for (int indexDecoded=0, index=0; index < rleArrayPhase2List.size(); ) {
			byte descriptor = rleArrayPhase2List.get(index).byteValue();
			++index; // consume the descriptor
			int length = descriptor & LENGTH_MASK;

			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			if ((byte)(descriptor & BIT_STREAM_OF_WORDS_A) == (byte)0) {
				entryCommand.add(CMD_RLE);
				entryIndex.add(index); // index points right after the descriptor
				entryIndexDecoded.add(indexDecoded);
				entryLengthInWords.add(length);
				indexDecoded += 2 * length; // consume all the words
				index += 2; // consume the word
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				entryCommand.add(CMD_STREAM_WORDS);
				entryIndex.add(index); // index points right after the descriptor
				entryIndexDecoded.add(indexDecoded);
				entryLengthInWords.add(length);
				indexDecoded += 2 * length; // consume all the words
				index += 2 * length; // consume all the words
			}
		}

		List<Byte> rleArrayPhase3List = new ArrayList<>(rleArrayPhase2List.size());

		// Search for duplicated and create new descriptor accordingly
		for (int cmdIdx=0, index=0; cmdIdx < entryCommand.size() && index < rleArrayPhase2List.size(); cmdIdx++) {
			byte descriptor = rleArrayPhase2List.get(index).byteValue();
			index++; // consume the descriptor
			int length = descriptor & LENGTH_MASK;
			boolean setEndOfRowBit = (descriptor & BIT_END_OF_ROW_A) != 0;

			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			if ((byte)(descriptor & BIT_STREAM_OF_WORDS_A) == (byte)0) {
				// No reason to create a backward reference entry for basic RLE commands since they are already a better coding choice.
				// Straight copy it.
				rleArrayPhase3List.add(descriptor);
				rleArrayPhase3List.add(rleArrayPhase2List.get(index));
				rleArrayPhase3List.add(rleArrayPhase2List.get(index + 1));
				index += 2; // consume the word
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				boolean backRefEncoded = false;

				if (length >= MIN_LENGTH_STREAM_OF_WORDS_FOR_BACKWARD_REF) {

					// STRATEGY 1: Search for a stream containing in entirety the source stream
					if (ENABLE_BACKWARD_REF_STRATEGY_1) {

						// Search from the start for matching command entries and compare their stream contents
						for (int searchCmdIdx=0; searchCmdIdx < cmdIdx; ++searchCmdIdx) {
							int lengthWordSearchingStream = entryLengthInWords.get(searchCmdIdx);

							// Different command? Length not in range?
							if (!entryCommand.get(searchCmdIdx).equals(CMD_STREAM_WORDS) || lengthWordSearchingStream < length)
								continue;

							// Search for the starting index from where the streams are duplicated.
						    // Try all possible starting positions in the searching stream.
							int startIndexDecodedSearchingStream = entryIndexDecoded.get(searchCmdIdx);
							int searchingIndex = entryIndex.get(searchCmdIdx); // is already after the descriptor
							boolean sameStreamFound = false;
						    for (; lengthWordSearchingStream >= length; 
						    		searchingIndex += 2, startIndexDecodedSearchingStream += 2, lengthWordSearchingStream--) {
						        // Compare current stream with existing stream starting at existingStart
						    	sameStreamFound = true;
						        for (int j = 0; j < 2*length; j += 2) {
						            // Compare the two words byte to byte
					            	boolean highByteDifferent = rleArrayPhase2List.get(index + j).byteValue() 
					            			!= rleArrayPhase2List.get(searchingIndex + j).byteValue();
					            	boolean lowByteDifferent = rleArrayPhase2List.get(index + 1 + j).byteValue() 
					            			!= rleArrayPhase2List.get(searchingIndex + 1 + j).byteValue();
					            	if (highByteDifferent || lowByteDifferent)
										break;
						        }

						        if (sameStreamFound)
						            break;
						    }

						    // Common Stream found. Let's try to encode it.
						    if (sameStreamFound) {

						    	// Calculate distance to jump back in the decoded buffer
						    	int distanceJumpBack = entryIndexDecoded.get(cmdIdx) - startIndexDecodedSearchingStream;
						    	// Compensate for the extra increment the output stream pointer makes in the unpacker algorithm
						    	//distanceJumpBack += 2;

						    	// This won't never happen, but if it does then something wrong happened in previous phase
						    	if (distanceJumpBack % 2 != 0)
						    		throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": distanceJumpBack is odd.");

						    	// Ensure the jump distance is only 15 bits. 2^15=32767
						    	if (distanceJumpBack < 32767) {

						    		// Add back ref byte
						    		rleArrayPhase3List.add((byte)BYTE_BACKWARD_REF_A);

						    		// Add jump back distance
						    		// Negate the jump distance as a 16 bits number
						    		short negated = (short) -((short)distanceJumpBack);
						    		rleArrayPhase3List.add((byte)((negated >> 8) & 0xFF));
						    		rleArrayPhase3List.add((byte)(negated & 0xFF));

						    		// Add the end of row bit
						    		byte lengthWithEndOfRowBit = (byte)length;
						    		if (setEndOfRowBit)
						    			lengthWithEndOfRowBit |= BIT_END_OF_ROW_A;
						    		rleArrayPhase3List.add(lengthWithEndOfRowBit);

						    		// Encoded is done
						    		backRefEncoded = true;
						    		break;
						    	}
						    }
						}
					}

					// STRATEGY 2: Search for the longest subset stream

					if (ENABLE_BACKWARD_REF_STRATEGY_2 && !backRefEncoded) {

						int bestSourceStartIndex = 0;
						int bestSourceStartIndexDecoded = 0;
						int bestMatchLength = 0;
						int bestMatchStartIndexDecoded = 0;

						// Search from the start for matching command entries
					    for (int searchCmdIdx=0; searchCmdIdx < cmdIdx; ++searchCmdIdx) {
							int lengthWordSearchingStream = entryLengthInWords.get(searchCmdIdx);

							// Different command? Skip
							if (!entryCommand.get(searchCmdIdx).equals(CMD_STREAM_WORDS))
								continue;

							int startIndexSearchingStream = entryIndex.get(searchCmdIdx); // is already after the descriptor
							int startIndexDecodedSearchingStream = entryIndexDecoded.get(searchCmdIdx);

							// Try all possible starting positions in the searching stream
			        		for (int j = 0; j < 2*lengthWordSearchingStream; j += 2) {

								int searchingIndex = startIndexSearchingStream + j;
								int searchingIndexDecoded = startIndexDecodedSearchingStream + j;
								int maxPossibleMatchLength = Math.min(length, lengthWordSearchingStream - j/2);

								int sourceIndex = entryIndex.get(cmdIdx);
								int sourceStartIndexDecoded = entryIndexDecoded.get(cmdIdx);

								// Try all possible starting positions in the source stream
								for (int k = 0; k < 2*length; k += 2, sourceIndex += 2, sourceStartIndexDecoded += 2) {

									// Find longest subset stream starting at these positions
									int matchLength = 0;
						            while (matchLength < maxPossibleMatchLength && (sourceIndex + 1 + matchLength*2) < rleArrayPhase2List.size()) {
						                // Compare the two words byte to byte
						            	boolean highByteDifferent = rleArrayPhase2List.get(sourceIndex + matchLength*2).byteValue() 
						            			!= rleArrayPhase2List.get(searchingIndex + matchLength*2).byteValue();
						            	boolean lowByteDifferent = rleArrayPhase2List.get(sourceIndex + 1 + matchLength*2).byteValue() 
						            			!= rleArrayPhase2List.get(searchingIndex + 1 + matchLength*2).byteValue();
						            	if (highByteDifferent || lowByteDifferent)
											break;
										matchLength++;
									}

									// Length meets criteria?
									if (matchLength >= MIN_LENGTH_STREAM_OF_WORDS_FOR_BACKWARD_REF && matchLength > bestMatchLength) {
										bestSourceStartIndex = sourceIndex;
										bestSourceStartIndexDecoded = sourceStartIndexDecoded;
										bestMatchLength = matchLength;
										bestMatchStartIndexDecoded = searchingIndexDecoded;
									}

									// Earlier exit if match found is the best possible
									if (bestMatchLength == length)
										break;
								}

								// Earlier exit if match found is the best possible
								if (bestMatchLength == length)
									break;
					        }

							// Earlier exit if match found is the best possible
							if (bestMatchLength == length)
								break;
					    }

					    // If we found the best partial match, encode it
					    if (bestMatchLength > 0) {

							// Calculate distance to jump back in the decoded buffer
					        int distanceJumpBack = bestSourceStartIndexDecoded - bestMatchStartIndexDecoded;
					        // Compensate for the extra increment the output stream pointer makes in the unpacker algorithm
					    	//distanceJumpBack += 2;

					    	// This won't never happen, but if it does then something wrong happened in previous phase
					    	if (distanceJumpBack % 2 != 0)
					    		throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": distanceJumpBack is odd.");

					    	// Ensure the jump distance is only 15 bits. 2^15=32767
					    	if (distanceJumpBack < 32767) {

					        	// If best starting source index is not the start of the source stream then add a new command for the excluded leading words
					        	if (bestSourceStartIndex != entryIndex.get(cmdIdx)) {
					        		int startIndexStream = entryIndex.get(cmdIdx);
					        		int newCmdLength = (bestSourceStartIndex - entryIndex.get(cmdIdx)) / 2;
					        		// if length is 1 then just create a basic RLE command
					        		if (newCmdLength == 1) {
										byte newCmdDescriptor = (byte) (newCmdLength & LENGTH_MASK);
										rleArrayPhase3List.add(newCmdDescriptor);
										rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream));
										rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + 1));
					        		}
					        		// length is > 1 then create a Stream of Words command
					        		else {
					        			byte newCmdDescriptor = (byte) (BIT_STREAM_OF_WORDS_A | (newCmdLength & LENGTH_MASK));
					        			rleArrayPhase3List.add(newCmdDescriptor);
					        			// Add the words
					        			for (int j=0; j < 2*newCmdLength; j += 2) {
											rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + j)); // word high byte
											rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + 1 + j)); // word low byte
										}
					        		}
					        	}

					    		// Add back ref byte
					    		rleArrayPhase3List.add((byte)BYTE_BACKWARD_REF_A);

					    		// Add jump back distance
					    		// Negate the jump distance as a 16 bits number
					    		short negated = (short) -((short)distanceJumpBack);
					    		rleArrayPhase3List.add((byte)((negated >> 8) & 0xFF));
					    		rleArrayPhase3List.add((byte)(negated & 0xFF));

					    		// Add the end of row bit
					    		byte lengthWithEndOfRowBit = (byte)bestMatchLength;
					    		if (setEndOfRowBit) {
					    			// Only if we didn't leave any trailing word 
					    			if (bestMatchLength == length)
					    				lengthWithEndOfRowBit = (byte) (lengthWithEndOfRowBit | BIT_END_OF_ROW_A);
					    		}
					    		rleArrayPhase3List.add(lengthWithEndOfRowBit);

					    		// Encoded is done
					    		backRefEncoded = true;

					    		// If ending index is not the end of the stream then add a new command for the excluded trailing words
					    		if ((bestSourceStartIndex + 2*bestMatchLength) != (entryIndex.get(cmdIdx) + 2*length)) {
					    			int startIndexStream = bestSourceStartIndex + 2*bestMatchLength;
					    			int newCmdLength = ((entryIndex.get(cmdIdx) + 2*length) - (bestSourceStartIndex + 2*bestMatchLength)) / 2;
					    			// if length is 1 then just create a basic RLE command
					        		if (newCmdLength == 1) {
					        			byte newCmdDescriptor = (byte) (newCmdLength & LENGTH_MASK);
					        			if (setEndOfRowBit)
					        				newCmdDescriptor = (byte) (newCmdDescriptor | BIT_END_OF_ROW_A);
					        			rleArrayPhase3List.add(newCmdDescriptor);
					        			rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream));
							    		rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + 1));
					        		}
					        		// length is > 1 then create a Stream of Words command
					        		else {
					        			byte newCmdDescriptor = (byte) (BIT_STREAM_OF_WORDS_A | (newCmdLength & LENGTH_MASK));
					        			if (setEndOfRowBit)
					        				newCmdDescriptor = (byte) (newCmdDescriptor | BIT_END_OF_ROW_A);
					        			rleArrayPhase3List.add(newCmdDescriptor);
					        			// Add the words
					        			for (int j=0; j < 2*newCmdLength; j += 2) {
											rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + j)); // word high byte
											rleArrayPhase3List.add(rleArrayPhase2List.get(startIndexStream + 1 + j)); // word low byte
										}
					        		}
					    		}
					        }
					    }
					}
				}

				// Not duplicated? Then straight copy it
				if (!backRefEncoded) {
					rleArrayPhase3List.add(descriptor);
					for (int j=0; j < 2*length; j += 2) {
						rleArrayPhase3List.add(rleArrayPhase2List.get(index + j)); // word high byte
						rleArrayPhase3List.add(rleArrayPhase2List.get(index + 1 + j)); // word low byte
					}
				}

				index += 2 * length; // consume all the words
			}
		}

		return rleArrayPhase3List;
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
		ByteBuffer byteBuffer = ByteBuffer.allocate(4);

		for (int i = 0; i < data.length; i += 2) {
			// Combine two bytes into a word
			int currentWord = ((data[i] & 0xFF) << 8) | ((data[i + 1] & 0xFF));
			int runLength = 1;
			accumWordsThisRow++;

			while ((i+2) < data.length && runLength < RLE_MAX_RUN_LENGTH && accumWordsThisRow < wordsPerRow && 
					(data[i] == data[i + 2]) && (data[i + 1] == data[i + 3])) {
				runLength++;
				accumWordsThisRow++;
				i += 2;
			}

			boolean addEndOfRowByte = false;
			if (accumWordsThisRow == wordsPerRow) {
				accumWordsThisRow = 0;
				addEndOfRowByte = true;
			}

			byte rleDescriptor = (byte) (runLength & LENGTH_MASK);

			byteBuffer.clear();
			byteBuffer.put(rleDescriptor); // store RLE byte descriptor
			byteBuffer.put((byte)((currentWord >> 8) & 0xFF)); // store word's higher byte
			byteBuffer.put((byte)(currentWord & 0xFF)); // store word's lower byte
			
			if (addEndOfRowByte) {
				byteBuffer.put((byte)BYTE_END_OF_ROW_B); // mark the end of a row
				outputStream.write(byteBuffer.array(), 0, 4);
			}
			else {
				outputStream.write(byteBuffer.array(), 0, 3);
			}
		}

		byte[] rlePhase1Array = outputStream.toByteArray();
		byteBuffer = null;
		outputStream.reset();
		outputStream = null;

		// PHASE 2:
		List<Byte> rlePhase2List = new ArrayList<>(rlePhase1Array.length);

		for (int i = 0; i < rlePhase1Array.length;) {

			byte rleDescriptor = rlePhase1Array[i];
			
			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == (byte)BYTE_END_OF_ROW_B) {
				rlePhase2List.add(rleDescriptor);
				++i;
			}
			// Try to find an incremental RLE segment only if segment is length 1
			else if ((rleDescriptor & LENGTH_MASK) == 1) {
				i = collectIncrementalRLE_B(rlePhase1Array, rlePhase2List, i, wordsPerRow);
			}
			// Copy the RLE segment
			else {
				rlePhase2List.add(rleDescriptor);
				rlePhase2List.add(rlePhase1Array[i + 1]); // word high byte
				rlePhase2List.add(rlePhase1Array[i + 2]); // word low byte
				i += 3;
			}
		}

		rlePhase1Array = null;

		// PHASE 3:
		// Now transform consecutive words having RLE byte descriptor with length 1 into one stream of at least N words.
		// The new RLE byte descriptor for such streams has 1 as its MSB and the length in the 6 LSBs.
		// The rest of the encoded RLE stays the same if the stream criteria is not met.

		List<Byte> rlePhase3List = new ArrayList<>(rlePhase2List.size());

		for (int i = 0; i < rlePhase2List.size();) {

			byte rleDescriptor = rlePhase2List.get(i).byteValue();

			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == (byte)BYTE_END_OF_ROW_B) {
				rlePhase3List.add(rleDescriptor);
				++i;
			}
			// Check if the length is 1 (single word repeat)
			else if ((rleDescriptor & LENGTH_MASK) == 1) {
				i = collectWordsIntoStream_B(rlePhase2List, rlePhase3List, i);
			}
			// Segment's length > 1
			else {
				// If descriptor's mask matches 0b01...... then we have an incremental RLE segment
				if ((byte)(rleDescriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_INCREMENTAL_RLE_B) {
					rlePhase3List.add(rleDescriptor);
					rlePhase3List.add(rlePhase2List.get(i + 1)); // operand
					rlePhase3List.add(rlePhase2List.get(i + 2)); // word high byte
					rlePhase3List.add(rlePhase2List.get(i + 3)); // word low byte
					i += 4;
				}
				// Others
				else {
					rlePhase3List.add(rleDescriptor);
					rlePhase3List.add(rlePhase2List.get(i + 1)); // word high byte
					rlePhase3List.add(rlePhase2List.get(i + 2)); // word low byte
					i += 3;
				}
			}
		}

		rlePhase2List.clear();
		rlePhase2List = null;

		// PHASE 4:
		// Process the streams of words and extract at least N consecutive words having the same high byte. 
		// This way the common high byte can be included once at the beginning of the stream and then continue 
		// with the low byte of every remaining word in the stream. This saves up to <50% in the best case.

		List<Byte> rlePhase4List = new ArrayList<>(rlePhase3List.size());

		for (int i = 0; i < rlePhase3List.size(); ) {

			byte rleDescriptor = rlePhase3List.get(i).byteValue();

			// If the descriptor is the end of row mark then collect it and continue
			if (rleDescriptor == (byte)BYTE_END_OF_ROW_B) {
				rlePhase4List.add(rleDescriptor);
				++i;
				continue;
			}

			// If descriptor's mask matches 0b10...... then we're going to analyze the stream
			if ((byte)(rleDescriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_STREAM_OF_WORDS_B) {
				i = compressStreamCommonHighBytes_B(rlePhase3List, rlePhase4List, i);
			}
			// If descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(rleDescriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_INCREMENTAL_RLE_B) {
				rlePhase4List.add(rleDescriptor);
				rlePhase4List.add(rlePhase3List.get(i + 1)); // operand
				rlePhase4List.add(rlePhase3List.get(i + 2)); // word high byte
				rlePhase4List.add(rlePhase3List.get(i + 3)); // word low byte
				i += 4;
			}
			// Others
			else {
				rlePhase4List.add(rleDescriptor);
				rlePhase4List.add(rlePhase3List.get(i + 1)); // word high byte
				rlePhase4List.add(rlePhase3List.get(i + 2)); // word low byte
				i += 3;
			}
		}

		rlePhase3List = null;
		byte[] rlePhase4Array = convertToByteArray(rlePhase4List);
		return rlePhase4Array;
	}

	private static int collectIncrementalRLE_B (byte[] source, List<Byte> target, int i, int wordsPerRow) {
		// Start collecting a sequence of incremental words
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 1; // the first word is already in because the comparison is between 2 words
		byte[] operand = {0}; // initial operand value always 0

		// Scan forward to count how many consecutive words with an incremental nature we find.
		// We only interesting in RLE segments of length 1.
		// i is descriptor's position, i+3 is the next descriptor's position
		while ((i+5) < source.length && sequenceLength <= wordsPerRow 
				&& source[i+3] != (byte)BYTE_END_OF_ROW_B 
				&& source[i] != 0 && source[i+3] != 0 && (source[i] & LENGTH_MASK) == 1 
				&& (source[i+3] & LENGTH_MASK) == 1 && keepSameOperand(source, i, operand)) {
			sequenceLength++;
			i += 3; // Consume this segment and locates at next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_INCREMENTAL_OCCURRENCES) {
			i += 3; // Consume the last segment and locates at next descriptor
			// Set the mask to tell this is an incremental RLE segment, including the length
			byte newDescriptor = (byte) (BITS_INCREMENTAL_RLE_B | (sequenceLength & LENGTH_MASK));
			target.add(newDescriptor);
			target.add(operand[0]);
			target.add(source[sequenceStart + 1]); // word high byte
			target.add(source[sequenceStart + 2]); // word low byte
		}
		// Not enough length to form an incremental RLE, then copy the segments as it is
		else {
			i += 3; // Consume the last segment and locates at next descriptor
			// Copy all the segments from the beginning up to the last segment used in the comparison loop
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

	private static int collectWordsIntoStream_B (List<Byte> source, List<Byte> target, int i) {
		// Start collecting a stream of single-word repeats
		int sequenceStart = i; // descriptor's position
		int sequenceLength = 0;

		// Scan forward to count how many consecutive words with length == 1 we find
		while (i < source.size() && (source.get(i).byteValue() & LENGTH_MASK) == 1) {
			sequenceLength++;
			i += 3; // Move to the next descriptor
		}

		if (sequenceLength >= RLE_MIN_SEQUENCE_OF_LENGTH_1_OCCURRENCE) {
			// Set the MSB to 1 to tell this is a stream of words, followed by the length of 
			// the stream in the remaining 6 LSBs
			byte newDescriptor = (byte) (BITS_STREAM_OF_WORDS_B | (sequenceLength & LENGTH_MASK));
			target.add(newDescriptor);
			sequenceStart++; // consume the descriptor

			// Add the collected words
			for (int j = 0; j < sequenceLength; j++) {
				target.add(source.get(sequenceStart + 0 + 3*j)); // word high byte
				target.add(source.get(sequenceStart + 1 + 3*j)); // word low byte
			}
		}
		// Not enough length to form a stream, then copy the segments as it is
		else {
			// Copy descriptor + word
			for (int j = sequenceStart; j < (sequenceStart + 3 * sequenceLength); j++)
				target.add(source.get(j));
		}

		return i;
	}

	private static int compressStreamCommonHighBytes_B (List<Byte> source, List<Byte> target, int i) {
		List<Byte> tempCollectorListPass1 = new ArrayList<>();
		int streamStartAt = i;
		int streamLength = source.get(i).byteValue() & LENGTH_MASK;
		++i; // Move to the high byte of the first word in the stream

		// Traverse all the stream of words
		for (int streamLenAux = streamLength; streamLenAux > 0; ) {
			int sequenceStart = i;
			int sequenceLength = 0;
			byte currentHighByte = source.get(i).byteValue();

			// Scan forward to count how many consecutive words with same high byte we actually find
			while (i < source.size() && source.get(i).byteValue() == currentHighByte && streamLenAux > 0) {
				sequenceLength++;
				i += 2; // Move to the next word's high byte in the stream
				--streamLenAux; // one word less in the stream
			}

			// If at least 2 words share the same high byte then we can compress them
			if (sequenceLength >= RLE_MIN_COMMON_HIGH_BYTE_SEQUENCE) {
				// Set the first 2 MSBs to 1 to tell this is a stream of bytes using a common high byte for the following N bytes.
				byte newDescriptor = (byte) (BITS_HIGH_COMMON_BYTE_B | (sequenceLength & LENGTH_MASK));
				tempCollectorListPass1.add(newDescriptor);
				tempCollectorListPass1.add(currentHighByte);
				// Add every low byte
				for (int j = 0; j < sequenceLength; j++)
					tempCollectorListPass1.add(source.get(sequenceStart + 1 + 2*j)); // word's low byte
			}
			// Not enough length to compress the words, then copy every word as a RLE of length 1
			else {
				for (int j = sequenceStart; j < (sequenceStart + 2 * sequenceLength); j += 2) {
					// Use a RLE descriptor with length 1 so we can convert them into a stream later on
					tempCollectorListPass1.add((byte) 0b00000001);
					// Collect the word
					tempCollectorListPass1.add(source.get(j));
					tempCollectorListPass1.add(source.get(j+1));
				}
			}
		}

		List<Byte> tempCollectorListPass2 = new ArrayList<>();

		// Now traverse the previous list and perform RLE only over the words having descriptor 0b10000001
		for (int j = 0; j < tempCollectorListPass1.size();) {

			byte descriptor = tempCollectorListPass1.get(j);

			// If the descriptor is a RLE of length 1 (previously set on purpose) then we're going to process this 
			// and consecutive words trying to collect them into a stream
			if (descriptor == (byte) 0b00000001) {
				j = collectWordsIntoStream_B(tempCollectorListPass1, tempCollectorListPass2, j);
			}
			// If the descriptor is the one marking a high common byte, then we just collect the sequence
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_HIGH_COMMON_BYTE_B) {
				tempCollectorListPass2.add(descriptor);
				j++; // move to the high common byte
				tempCollectorListPass2.add(tempCollectorListPass1.get(j++)); // collect the high common byte and move forward
				int len = descriptor & LENGTH_MASK;
				for (int k = 0; k < len; ++k)
					tempCollectorListPass2.add(tempCollectorListPass1.get(j++));
			}
			else
				throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + ": descriptor is not expected.");
		}

		// Resulting array must be at least 80% smaller than the original stream of words
		if (tempCollectorListPass2.size() <= ((1 + streamLength * 2) * RLE_THRESHOLD_PHASE_2_TO_PHASE_3))
			target.addAll(tempCollectorListPass2);
		// Otherwise just copy all the original stream of words
		else
			target.addAll(source.subList(streamStartAt, streamStartAt + 1 + streamLength*2));

		return i;
	}

	private static byte[] convertToByteArray (List<Byte> list) {
		byte[] array = new byte[list.size()];
		for (int k = 0; k < list.size(); k++) {
			array[k] = list.get(k);
		}
		return array;
	}

	private static void checkCorrectRowLength_A(byte[] rleData, int wordsPerRowLimit) {
		int rowLengthAccum = 0;
		int index = 0;
	
		while (index < rleData.length) {
			byte descriptor = rleData[index++];
	
			// it's a backward ref byte?
			if ((byte)descriptor == (byte)BYTE_BACKWARD_REF_A) {
				int rawLengthByte = rleData[index+2];
				boolean isEndOfRow = (rawLengthByte & BIT_END_OF_ROW_A) != 0;
				int length = rawLengthByte & LENGTH_MASK;
				rowLengthAccum += length;
				index += 3; // consume the jump distance (2 bytes) and the length (1 byte)
				// is end of row bit set?
				if (isEndOfRow) {
					if (rowLengthAccum != wordsPerRowLimit) {
						throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() 
								+ " method A: wrong number of words in Backward Ref command relative to current row length.");
					}
					rowLengthAccum = 0;
				}
			}
			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			else if ((byte)(descriptor & BIT_STREAM_OF_WORDS_A) == (byte)0) {
				int length = descriptor & LENGTH_MASK;
				rowLengthAccum += length;
				index += 2; // consume the word
				// if MSB is set then it marks end of row
				if ((descriptor & BIT_END_OF_ROW_A) != 0) {
					if (rowLengthAccum != wordsPerRowLimit) {
						throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() 
								+ " method A: wrong number of words in Simple RLE command relative to current row length.");
					}
					rowLengthAccum = 0;
				}
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				int length = descriptor & LENGTH_MASK;
				rowLengthAccum += length;
				index += 2 * length; // consume all the words
				// if MSB is set then it marks end of row
				if ((descriptor & BIT_END_OF_ROW_A) != 0) {
					if (rowLengthAccum != wordsPerRowLimit) {
						throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() 
								+ " method A: wrong number of words in Stream of Words command relative to current row length.");
					}
					rowLengthAccum = 0;
				}
			}
		}
	}

	private static void checkCorrectRowLength_B(byte[] rleData, int wordsPerRowLimit) {
		int rowLengthAccum = 0;
		int index = 0;
	
		while (index < rleData.length) {
			byte descriptor = rleData[index++];
	
			// is descriptor the mark for end of row?
			if (descriptor == (byte)BYTE_END_OF_ROW_B) {
				if (rowLengthAccum != wordsPerRowLimit) {
					throw new RuntimeException("ERROR: " + RLEWCompressor.class.getSimpleName() + " method B: wrong number of words in row.");
				}
				rowLengthAccum = 0;
				continue;
			}
	
			int length = descriptor & LENGTH_MASK;
			rowLengthAccum += length;
	
			// test if descriptor's mask matches 0...... then we have basic RLE segment
			if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)0) {
				index += 2; // consume the word
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_INCREMENTAL_RLE_B) {
				index++; // consume operand
				index += 2; // consume the word
			}
			// test if descriptor's mask matches 0b10...... then is a stream of words
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_STREAM_OF_WORDS_B) {
				index += 2 * length; // consume all the words
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_HIGH_COMMON_BYTE_B) {
				index++; // consume common high byte
				index += length; // consume all the lower bytes
			}
		}
	}

	private static byte[] addHeader (byte[] rleData, int mapTilesPerRow) {
		byte[] newArray = new byte[rleData.length + 1];
		newArray[0] = (byte) mapTilesPerRow;
		System.arraycopy(rleData, 0, newArray, 1, rleData.length);
		return newArray;
	}

	/**
	 * Add the parity byte before a descriptor so the next word can be read from an even address, 
	 * since M68000 CPU can't read words from odd addresses which impacts in the performance of the unpacker.
	 * @param rleData
	 * @return
	 */
	private static byte[] addParityBytes_A (byte[] rleData) {
		List<Byte> list = new ArrayList<>(rleData.length); // initial capacity
		int index = 0;
		boolean afterFirstDescriptor = false;
		boolean backRefByteAdded = false;

		// first byte is the header
		list.add(rleData[index++]);

		// visit the rest of the array
		while (index < rleData.length) {
			byte descriptor = rleData[index++];

			// if not the first run then we add the Parity Byte
			if (afterFirstDescriptor) {
				// only if Back Ref Byte wasn't added in previous loop
				if (!backRefByteAdded) {
					list.add((byte)PARITY_BYTE_A);
				}
			}

			// reset the flag for this loop
			backRefByteAdded = false;

			list.add(descriptor);

			// it's a Back Ref Byte?
			if ((byte)descriptor == (byte)BYTE_BACKWARD_REF_A) {
				// copy the jump distance (2 bytes)
				list.add(rleData[index++]);
				list.add(rleData[index++]);
				// copy the length (with end of row bit if set)
				list.add(rleData[index++]);
				// set the flag so next loop Parity Byte is not added
				backRefByteAdded = true;
			}
			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			else if ((byte)(descriptor & BIT_STREAM_OF_WORDS_A) == (byte)0) {
				// copy the word
				list.add(rleData[index++]);
				list.add(rleData[index++]);
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				int length = descriptor & LENGTH_MASK;
				// copy the words
				for (int i = 0; i < length; i++) {
					list.add(rleData[index++]);
					list.add(rleData[index++]);
				}
			}
			
			afterFirstDescriptor = true;
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

			// is descriptor the mark for end of row?
			if (descriptor == (byte)BYTE_END_OF_ROW_B) {
				continue;
			}
			// test if descriptor's mask matches 0...... then we have basic RLE segment
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)0) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte)PARITY_BYTE_B); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				// copy the word
				list.add(rleData[index++]);
				list.add(rleData[index++]);
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_INCREMENTAL_RLE_B) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isOdd(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte)PARITY_BYTE_B); // add the parity byte
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
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_STREAM_OF_WORDS_B) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte)PARITY_BYTE_B); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				// copy the words
				int length = descriptor & LENGTH_MASK;
				for (int i = 0; i < length; i++) {
					list.add(rleData[index++]);
					list.add(rleData[index++]);
				}
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_HIGH_COMMON_BYTE_B) {
				// if descriptor was at even position then we'll add before him the parity byte
				if (isEven(index - 1, offsetAccum)) {
					list.remove(list.size() - 1); // remove descriptor
					list.add((byte)PARITY_BYTE_B); // add the parity byte
					list.add(descriptor); // now add the descriptor
					++offsetAccum;
				}
				list.add(rleData[index++]); // common high byte
				int length = (descriptor & LENGTH_MASK);
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

			// it's a backward ref byte?
			if ((byte)descriptor == (byte)BYTE_BACKWARD_REF_A) {
				index += 3; // consume the jump distance (2 bytes) and the length byte
			}
			// test if descriptor 2nd MSB is 0 then we have basic RLE entry
			else if ((byte)(descriptor & BIT_STREAM_OF_WORDS_A) == (byte)0) {
				int word = (rleData[index++] << 8) | rleData[index++];
				updateWordInfo(wordInfoMap, word, currentDescriptorPos);
			}
			// descriptor 2nd MSB is 1, then we have a stream of words
			else {
				int length = descriptor & LENGTH_MASK;
				// track position and occurrences of every word in the stream
				for (int i = 0; i < length; i++) {
					int word = (rleData[index++] << 8) | rleData[index++];
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

			// is descriptor the mark for end of row?
			if (descriptor == (byte)BYTE_END_OF_ROW_B) {
				continue;
			}
			// test if descriptor's mask matches 0...... then we have basic RLE segment
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)0) {
				int word = (rleData[index++] << 8) | rleData[index++];
				updateWordInfo(wordInfoMap, word, currentDescriptorPos);
			}
			// test if descriptor's mask matches 0b01...... then we have an incremental RLE segment
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_INCREMENTAL_RLE_B) {
				int length = descriptor & LENGTH_MASK;
				byte operand = rleData[index++]; // operand
				int word = (rleData[index++] << 8) | rleData[index++];
				for (int i = 0; i < length-1; i++) {
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + 1 + i);
					word = word + operand;
				}
			}
			// test if descriptor's mask matches 0b10...... then is a stream of words
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_STREAM_OF_WORDS_B) {
				int length = descriptor & LENGTH_MASK;
				// track position and occurrences of every word in the stream
				for (int i = 0; i < length; i++) {
					int word = (rleData[index++] << 8) | rleData[index++];
					updateWordInfo(wordInfoMap, word, currentDescriptorPos + i);
				}
			}
			// descriptor's mask matches 0b11...... then is a stream with a common high byte
			else if ((byte)(descriptor & BITS_DESCRIPTOR_MASK) == (byte)BITS_HIGH_COMMON_BYTE_B) {
				int length = descriptor & LENGTH_MASK;
				byte commonHighByte = rleData[index++];
				for (int i = 0; i < length; i++) {
					int word = (commonHighByte << 8) | rleData[index++];
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
	
//	public static void main(String[] args) throws Exception
//	{
//		byte[] words = new byte[] {
//				0,46, 0,46, 0,46, 0,46, 0,46, 0,46, // basic RLE
//				1,1, 1,2, 1,3, 1,4, 1,5, 1,6, 1,7, 1,8, 1,9, 1,10, 1,11, 1,12, 1,13, 1,14, 1,15, 1,16, 1,17, // Stream of Words
//				0,58, 0,58, 0,58, 0,58, 0,58, 0,58, // basic RLE
//				1,1, 1,2, 1,3, 1,4, 1,5, 1,6, 1,7, 1,8, 1,9, 1,10, 1,11, 1,12, 1,13, 1,14, 1,15, 1,16, 1,17, // Stream of Words
//				0,69, 0,69, 0,69, 0,69, 0,69, 0,69, // basic RLE
//				0,72, 0,72, 0,72, 0,72, 0,72, 0,72, 0,72, 0,72, 0,72, 0,72, // basic RLE
//				2,1, 2,2, 1,3, 1,4, 1,5, 1,6, 1,7, 1,8, 1,9, 1,10, 2,11, 2,12, 2,13 // Stream of Words
//		};
//		compress_A(words, "");
//	}
}
