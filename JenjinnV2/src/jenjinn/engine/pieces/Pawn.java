/**
 * Written by Tom Ball 2017.
 *
 * This code is unlicensed but please don't plagiarize.
 */

package jenjinn.engine.pieces;

import jenjinn.engine.bitboarddatabase.BBDB;
import jenjinn.engine.enums.Side;

/**
 * A class representing a Pawn for use in the engine
 *
 * @author TB
 * @date 24 Jan 2017
 */
public class Pawn extends ChessPiece
{
	Pawn(final Side side)
	{
		super(PieceType.P, side, side == Side.W ? 1_000_000 : -1_000_000);
	}

	/* (non-Javadoc)
	 *
	 * @see jenjinn.engine.pieces.ChessPiece#getAttackset(byte, long, long) */
	@Override
	public long getAttackset(final byte loc, final long occupiedSquares)
	{
		return BBDB.EBA[getSide().ordinal()][loc];
	}

	private boolean inFirstMoveZone(final byte loc)
	{
		return isWhite() ? loc < 16 : 47 < loc;
	}

	@Override
	public long getMoveset(final byte loc, final long friendlyPieces, final long enemyPieces)
	{
		final long attck = super.getMoveset(loc, friendlyPieces, enemyPieces);
		long push = (1L << (loc + getSide().orientation() * 8)) & ~(friendlyPieces | enemyPieces);

		if (inFirstMoveZone(loc) && push != 0)
		{
			push |= ((1L << (loc + getSide().orientation() * 16)) & ~(friendlyPieces | enemyPieces));
		}
		return attck | push;
	}

	@Override
	public long getStartBitboard()
	{
		return 0b1111111100000000L << 40 * (getSide().isWhite() ? 0 : 1);
	}
}
