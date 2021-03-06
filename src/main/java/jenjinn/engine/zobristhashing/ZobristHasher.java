package jenjinn.engine.zobristhashing;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import jenjinn.engine.enums.Sq;
import jenjinn.engine.misc.EngineUtils;
import jenjinn.engine.pieces.ChessPiece;

/**
 * @author ThomasB
 * @since 7 Jul 2017
 */
public class ZobristHasher
{
	private static final long DEFAULT_SEED = 0x110894L;// 0x73abc76L;//

	private long[][] squarePieceFeatures = new long[64][12];

	private long[] castleFeatures = new long[4];

	private long blackToMove;

	private long[] enPassantFileFeatures = new long[8];

	private ZobristHasher()
	{
	}

	public static final ZobristHasher getDefault()
	{
		return getFrom(DEFAULT_SEED);
	}

	public static final ZobristHasher getFrom(final long seed)
	{
		if (!seedIsValid(seed)) {
			throw new AssertionError();
		}

		final ZobristHasher hasher = new ZobristHasher();

		final Random r = new Random(seed);

		addSquarePieceFeatures(r, hasher);
		addAuxilaryFeatures(r, hasher);

		return hasher;
	}

	private static void addAuxilaryFeatures(final Random r, final ZobristHasher hasher)
	{
		for (int i = 0; i < 4; i++) {
			hasher.castleFeatures[i] = r.nextLong();
		}

		hasher.blackToMove = r.nextLong();

		for (int i = 0; i < 8; i++) {
			hasher.enPassantFileFeatures[i] = r.nextLong();
		}
	}

	private static void addSquarePieceFeatures(final Random r, final ZobristHasher hasher)
	{
		int i = 0;
		for (@SuppressWarnings("unused")
		final Sq sq : Sq.values()) {
			for (int j = 0; j < 12; j++) {
				hasher.squarePieceFeatures[i][j] = r.nextLong();
			}
			i++;
		}
	}

	private static boolean seedIsValid(final long seed)
	{
		final Random r = new Random(seed);
		final Set<Long> values = new HashSet<>();
		for (int i = 0; i < 800; i++) {
			values.add(r.nextLong());
		}
		return values.size() == 800;
	}

	public long getSquarePieceFeature(final byte loc, final ChessPiece piece)
	{
		return squarePieceFeatures[loc][piece.index()];
	}

	public long getCastleFeature(final int i)
	{
		return castleFeatures[i];
	}

	public long getEnpassantFeature(final int fileNum)
	{
		return enPassantFileFeatures[fileNum];
	}

	public long getBlackToMove()
	{
		return blackToMove;
	}

	public static void main(final String[] args)
	{
		getDefault();
	}

	public long generateStartHash()
	{
		long startHash = EngineUtils.multipleXor(castleFeatures);

		final long[] startPieceLocs = EngineUtils.getStartingPieceLocs();

		for (int i = 0; i < 12; i++) {
			final byte[] locs = EngineUtils.getSetBits(startPieceLocs[i]);

			for (final byte loc : locs) {
				startHash ^= getSquarePieceFeature(loc, ChessPiece.get(i));
			}
		}
		return startHash;
	}
}
