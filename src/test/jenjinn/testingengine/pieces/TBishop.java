/**
 * Copyright � 2017 Lhasa Limited
 * File created: 19 Sep 2017 by ThomasB
 * Creator : ThomasB
 * Version : $Id$
 */
package jenjinn.testingengine.pieces;

import java.util.Arrays;

import jenjinn.engine.enums.Direction;
import jenjinn.engine.enums.Side;
import jenjinn.engine.pieces.PieceType;

/**
 * @author ThomasB
 * @since 19 Sep 2017
 */
public class TBishop extends TChessPiece
{

	/**
	 * @param type
	 * @param side
	 * @param moveDirs
	 */
	public TBishop(final Side side)
	{
		super(PieceType.B, side,
				Arrays.asList(Direction.NE, Direction.NW, Direction.SE, Direction.SW));
	}

	@Override
	public long getStartBitboard()
	{
		return 0b100100L << 56 * (getSide().isWhite() ? 0 : 1);
	}
}

/* ---------------------------------------------------------------------*
 * This software is the confidential and proprietary
 * information of Lhasa Limited
 * Granary Wharf House, 2 Canal Wharf, Leeds, LS11 5PS
 * ---
 * No part of this confidential information shall be disclosed
 * and it shall be used only in accordance with the terms of a
 * written license agreement entered into by holder of the information
 * with LHASA Ltd.
 * --------------------------------------------------------------------- */