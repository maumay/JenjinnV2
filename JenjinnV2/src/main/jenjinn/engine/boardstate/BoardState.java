/**
 *
 */
package jenjinn.engine.boardstate;

import java.util.List;

import jenjinn.engine.enums.Side;
import jenjinn.engine.enums.TerminationType;
import jenjinn.engine.evaluation.PieceSquareTable;
import jenjinn.engine.evaluation.piecetablesimpl.EndGamePSTimplV1;
import jenjinn.engine.evaluation.piecetablesimpl.MiddleGamePSTimplV1;
import jenjinn.engine.exceptions.AmbiguousPgnException;
import jenjinn.engine.moves.ChessMove;
import jenjinn.engine.openingdatabase.AlgebraicCommand;
import jenjinn.engine.pieces.ChessPiece;
import jenjinn.engine.zobristhashing.ZobristHasher;

/**
 * Completely immutable representation of a state of a chess board. IMPORTANT
 * CONVENTION: We denote the side to move by friendly for brevity.
 * The side which isn't to move is then denoted as enemy.
 *
 * // We need to make this class more memory efficient.
 *
 * @author TB
 * @date 28 Jan 2017
 */
public interface BoardState
{
	/**
	 * Static {@link PieceSquareTable} instances for incremental update to the stored evaluations,
	 * we store two such evaluations in the boardstate, one for midgame and the other for endgame.
	 */
	static PieceSquareTable MID_TABLE = new MiddleGamePSTimplV1(), END_TABLE = new EndGamePSTimplV1();

	static byte NO_ENPASSANT = 127;

	static ZobristHasher HASHER = ZobristHasher.getDefault();

	byte getFriendlySideValue();

	Side getFriendlySide();

	Side getEnemySide();

	TerminationType getTerminationState();

	List<ChessMove> getMoves();

	List<ChessMove> getAttackMoves();

	ChessMove generateMove(final AlgebraicCommand com) throws AmbiguousPgnException;

	/**
	 * In general this hashing function is not what would be used during the tree search.
	 *
	 * @return
	 */
	long zobristHash();

	ChessPiece getPieceAt(final byte loc);

	ChessPiece getPieceAt(final byte loc, Side s);

	long getPieceLocations(int pieceIndex);

	long[] getPieceLocationsCopy();

	long getSideLocations(Side s);

	long getOccupiedSquares();

	long getSquaresAttackedBy(final Side side);

	byte getCastleStatus();

	byte getCastleRights();

	byte getClockValue();

	byte getPiecePhase();

	default short getGamePhase()
	{
		return (short) ((getPiecePhase() * 256 + 12) / 24);
	}

	long getDevelopmentStatus();

	long getHashing();

	byte getEnPassantSq();

	long[] getNewRecentHashings(long newHash);

	void print();

	void printMoves();

	short getMidgamePositionalEval();

	short getEndgamePositionalEval();

}