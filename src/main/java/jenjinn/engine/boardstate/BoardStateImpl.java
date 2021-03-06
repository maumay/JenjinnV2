package jenjinn.engine.boardstate;

import static io.xyz.chains.utilities.CollectionUtil.len;
import static io.xyz.chains.utilities.CombineUtil.join;
import static io.xyz.chains.utilities.StreamUtil.stream;
import static java.util.stream.Collectors.toList;
import static jenjinn.engine.boardstate.BoardStateConstants.getStateHasher;
import static jenjinn.engine.misc.EngineUtils.printNbitBoards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import jenjinn.engine.bitboarddatabase.Bitboards;
import jenjinn.engine.enums.Side;
import jenjinn.engine.enums.Sq;
import jenjinn.engine.enums.TerminationType;
import jenjinn.engine.exceptions.AmbiguousPgnException;
import jenjinn.engine.misc.EngineUtils;
import jenjinn.engine.moves.CastleMove;
import jenjinn.engine.moves.ChessMove;
import jenjinn.engine.moves.EnPassantMove;
import jenjinn.engine.moves.PromotionMove;
import jenjinn.engine.moves.StandardMove;
import jenjinn.engine.openingdatabase.AlgebraicCommand;
import jenjinn.engine.pieces.ChessPiece;
import jenjinn.engine.pieces.PieceType;

/**
 * @author ThomasB
 * @since 19 Jul 2017
 */
public class BoardStateImpl implements BoardState
{
	private static final long EXCESS_CHOPPER = ~EngineUtils.multipleOr(
			Bitboards.RNK[2], Bitboards.RNK[3], Bitboards.RNK[4],
			Bitboards.RNK[5], Bitboards.RNK[6], Bitboards.RNK[7]);

	private static final long CASTLE_RIGHTS_GETTER = 0b11110000L << (7 * 8);

	private static final long CASTLE_STATUS_GETTER = 0b1111L << (7 * 8);

	private static final long ENPASSANT_SQUARE_GETTER = 0b11111110L << (6 * 8);

	private static final long FRIENDLY_SIDE_GETTER = 1L << (6 * 8);

	private static final long HALFMOVE_CLOCK_GETTER = 0b1111111L << (5 * 8);

	private static final long MIDGAME_LOC_EVAL_GETTER = 0b1111111111111111L << (2 * 8);

	private static final long ENDGAME_LOC_EVAL_GETTER = 0b1111111111111111L;
	//

	/**
	 * An array of four most recent board hashings (including the hash of this
	 * state) for determining repetition draws.
	 */
	private final long[] recentHashings;

	/**
	 * Record of the development progress of the 'development pieces' i.e the
	 * central pawns and minor pieces. These are the pieces any self respecting
	 * opening should develop. We use a long for simplicity so there is wasted space
	 * but we can potentially use this as a secondary meta data holder (e.g. data
	 * for evaluation) in the future.
	 */
	private final long devStatus;

	private final long metaData;

	private final long[] pieceLocations;

	private TerminationType termType;

	public BoardStateImpl(final long[] recentHashings, final long friendlySide, final long castleRights, final long castleStatus, final long enPassantSq, final long halfMoveClock, final long piecePhase, final long midPieceLocEval, final long endPieceLocEval, final long devStatus, final long[] pieceLocations)
	{
		this.recentHashings = recentHashings;
		this.devStatus = devStatus;
		this.pieceLocations = pieceLocations;

		this.metaData =
				(castleRights << 60) | // 60 = (7 * 8) + 4
				(castleStatus << 56) | // 56 = 7 * 8
				(enPassantSq << 49) | // 49 = (6 * 8) + 1
				(friendlySide << 48) | // 48 = 6 * 8
				(halfMoveClock << 40) | // 40 = (5 * 8)
				(piecePhase << 32) | // 32 = (4 * 8)
				((midPieceLocEval & EXCESS_CHOPPER) << 16) | (endPieceLocEval & EXCESS_CHOPPER);
	}

	@Override
	public List<ChessMove> getMoves()
	{
		final Side friendlySide = getFriendlySide();
		final long friendlyPieces = getSideLocations(friendlySide);
		final long enemyPieces = getSideLocations(friendlySide.otherSide());

		final List<ChessMove> moves = new ArrayList<>(getCastleMoves(enemyPieces | friendlyPieces));

		final byte upperBound = (byte) (5 + friendlySide.index()), lowerBound = friendlySide.index();
		for (byte i = upperBound; i > lowerBound; i--) {
			// Get the most valuable piece moves first
			final ChessPiece p = ChessPiece.get(i);

			for (final byte loc : EngineUtils.getSetBits(pieceLocations[i])) {
				addStandardMoves(moves, loc, p.getMoveset(loc, friendlyPieces, enemyPieces));
			}
		}

		// Add Pawn moves.
		final ChessPiece p = ChessPiece.get(lowerBound);
		if (getEnPassantSq() != BoardState.NO_ENPASSANT) {
			for (final byte loc : EngineUtils.getSetBits(pieceLocations[lowerBound])) {
				addPawnStandardAndPromotionMoves(moves, loc, p.getMoveset(loc, friendlyPieces, enemyPieces));
				if (((p.getAttackset(loc, enemyPieces | friendlyPieces) & (1L << getEnPassantSq())) != 0)) {
					moves.add(EnPassantMove.get(loc, getEnPassantSq()));
				}
			}
		}
		else {
			for (final byte loc : EngineUtils.getSetBits(pieceLocations[lowerBound])) {
				addPawnStandardAndPromotionMoves(moves, loc, p.getMoveset(loc, friendlyPieces, enemyPieces));
			}
		}

		return moves;
	}

	@Override
	public List<ChessMove> getAttackMoves()
	{
		final Side friendlySide = getFriendlySide();
		final long enemyPieces = getSideLocations(friendlySide.otherSide());
		final long allPieces = getOccupiedSquares();

		final List<ChessMove> moves = new ArrayList<>();

		final byte upperBound = (byte) (5 + friendlySide.index()), lowerBound = friendlySide.index();
		for (byte i = upperBound; i > lowerBound; i--) // Get the most valuable piece moves first
		{
			final ChessPiece p = ChessPiece.get(i);

			for (final byte loc : EngineUtils.getSetBits(pieceLocations[i])) {
				addStandardMoves(moves, loc, p.getAttackset(loc, allPieces) & enemyPieces);
			}
		}

		// Add Pawn moves.
		final ChessPiece p = ChessPiece.get(lowerBound);
		if (getEnPassantSq() != BoardState.NO_ENPASSANT) {
			for (final byte loc : EngineUtils.getSetBits(pieceLocations[lowerBound])) {
				addPawnStandardAndPromotionMoves(moves, loc, p.getAttackset(loc, allPieces) & enemyPieces);
				if (((p.getAttackset(loc, allPieces) & (1L << getEnPassantSq())) != 0)) {
					moves.add(EnPassantMove.get(loc, getEnPassantSq()));
				}
			}
		}
		else {
			for (final byte loc : EngineUtils.getSetBits(pieceLocations[lowerBound])) {
				addPawnStandardAndPromotionMoves(moves, loc, p.getAttackset(loc, allPieces) & enemyPieces);
			}
		}
		return moves;
	}

	/**
	 * Have a simple cache mechanism for multiple calls whilst allowing for the case
	 * where we don't need to calculate the term state.
	 */
	@Override
	public TerminationType getTerminationState()
	{
		if (termType != null) {
			return termType;
		}

		if (getClockValue() == 100) {
			termType = TerminationType.DRAW;
			return termType;
		}

		// First check for taking of king
		final Side friendlySide = getFriendlySide();

		if (isTerminalWin()) {
			termType = friendlySide == Side.W ? TerminationType.WHITE_WIN : TerminationType.BLACK_WIN;
			return termType;
		}

		if (stream(recentHashings).distinct().count() < 3) {
			int sameCount = 1;
			for (int i = 1; i < len(recentHashings); i++) {
				sameCount += recentHashings[i] == recentHashings[0] ? 1 : 0;
			}
			if (sameCount != 2) {
				termType = TerminationType.DRAW;
				return TerminationType.DRAW;
			}
		}

		if (isStaleMate()) {
			termType = TerminationType.DRAW;
			return termType;
		}

		termType = TerminationType.NOT_TERMINAL;
		return termType;
	}

	/**
	 * @return
	 */
	private boolean isStaleMate()
	{
		/* Can't imagine stalemate before this point. */
		if (getPiecePhase() > 15) {
			return false;
		}

		/* If we are in check then not stalemate */
		if ((getSquaresAttackedBy(getEnemySide()) & pieceLocations[getFriendlySide().index() + 5]) != 0) {
			return false;
		}

		final long friendly = getSideLocations(getFriendlySide()), enemy = getSideLocations(getEnemySide());
		final int lower = getFriendlySideValue() * 6, upper = lower + 6;

		for (int i = lower; i < upper; i++) {
			final ChessPiece p = ChessPiece.get(i);
			for (final byte loc : EngineUtils.getSetBits(pieceLocations[i])) {
				final long mvset = p.getMoveset(loc, friendly, enemy);

				for (final byte targ : EngineUtils.getSetBits(mvset)) {
					final BoardStateImpl evolved = (BoardStateImpl) StandardMove.get(loc, targ).evolve(this);
					if (!evolved.isTerminalWin()) {
						return false;
					}
				}
			}
		}

		/* If we here, not in check and every move puts us in check, so stalemate */
		return true;
	}

	private boolean isTerminalWin()
	{
		final Side friendlySide = getFriendlySide();
		return (getSquaresAttackedBy(friendlySide) & pieceLocations[friendlySide.otherSide().index() + 5]) != 0;
	}

	/**
	 * For non pawns!
	 *
	 * @param moves
	 * @param loc
	 * @param mvset
	 */
	private void addStandardMoves(final List<ChessMove> moves, final byte loc, final long mvset)
	{
		final byte[] targets = EngineUtils.getSetBits(mvset);
		for (final byte target : targets) {
			moves.add(StandardMove.get(loc, target));
		}
	}

	/**
	 * For pawns!
	 *
	 * @param moves
	 * @param loc
	 * @param mvset
	 */
	private void addPawnStandardAndPromotionMoves(final List<ChessMove> moves, final byte loc, long mvset)
	{
		final long backRank = 0b11111111L << ((1 - getFriendlySideValue()) * 56), backRankMvs = mvset & backRank;
		mvset &= ~backRank;

		addStandardMoves(moves, loc, mvset);

		final byte[] backRankTargets = EngineUtils.getSetBits(backRankMvs);
		for (final byte target : backRankTargets) {
			moves.add(PromotionMove.get(loc, target, PieceType.Q));
		}
	}

	private List<CastleMove> getCastleMoves(final long allPieces)
	{
		final List<CastleMove> cmvs = new ArrayList<>(2);

		// for rights and status retrieval
		final byte sideShift = (byte) (getFriendlySideValue() * 2);

		// If we have not already castled
		if ((getCastleStatus() & (0b11 << sideShift)) == 0) {
			final boolean hasKsideRights = (getCastleRights() & (0b1 << (sideShift))) != 0;
			final boolean hasQsideRights = (getCastleRights() & (0b10 << (sideShift))) != 0;

			if (hasKsideRights) {
				// if squares are clear
				if (((0b110L << (getFriendlySideValue() * 56)) & allPieces) == 0) {
					// if squares are not attacked
					if (((0b1110L << (getFriendlySideValue() * 56)) & getSquaresAttackedBy(getEnemySide())) == 0) {
						cmvs.add(getFriendlySideValue() == 0 ? CastleMove.WHITE_KINGSIDE : CastleMove.BLACK_KINGSIDE);
					}
				}
			}
			if (hasQsideRights) {
				// if squares are clear
				if (((0b1110000L << (getFriendlySideValue() * 56)) & allPieces) == 0) {
					// if squares are not attacked
					if (((0b1111000L << (getFriendlySideValue() * 56)) & getSquaresAttackedBy(getEnemySide())) == 0) {
						cmvs.add(getFriendlySideValue() == 0 ? CastleMove.WHITE_QUEENSIDE : CastleMove.BLACK_QUEENSIDE);
					}
				}
			}
		}
		return cmvs;
	}

	@Override
	public ChessPiece getPieceAt(final byte loc)
	{
		for (byte index = 0; index < 12; index++) {
			if (((1L << loc) & pieceLocations[index]) != 0) {
				return ChessPiece.PIECES[index];
			}
		}
		return null;
	}

	@Override
	public ChessPiece getPieceAt(final byte loc, final Side s)
	{
		final byte lowerBound = s.index(), upperBound = (byte) (s.index() + 6);

		for (byte index = lowerBound; index < upperBound; index++) {
			if (((1L << loc) & pieceLocations[index]) != 0) {
				return ChessPiece.PIECES[index];
			}
		}
		return null;
	}

	@Override
	public long getPieceLocations(final int pieceIndex)
	{
		return pieceLocations[pieceIndex];
	}

	@Override
	public long getSquaresAttackedBy(final Side side)
	{
		// TODO - Could perform optimisation on pawn attacks
		final long occupiedSquares = getOccupiedSquares();
		long attackedSquares = 0L;

		for (byte i = side.index(); i < side.index() + 6; i++) {
			final byte[] locs = EngineUtils.getSetBits(pieceLocations[i]);
			final ChessPiece p = ChessPiece.get(i);

			for (final byte loc : locs) {
				attackedSquares |= p.getAttackset(loc, occupiedSquares);
			}
		}
		return attackedSquares;
	}

	@Override
	public long getDevelopmentStatus()
	{
		return devStatus;
	}

	@Override
	public long getHashing()
	{
		return recentHashings[0];
	}

	@Override
	public byte getCastleStatus()
	{
		return (byte) ((metaData & CASTLE_STATUS_GETTER) >>> 56);
	}

	@Override
	public byte getCastleRights()
	{
		return (byte) ((metaData & CASTLE_RIGHTS_GETTER) >>> 60);
	}

	@Override
	public byte getEnPassantSq()
	{
		return (byte) ((metaData & ENPASSANT_SQUARE_GETTER) >>> 49);
	}

	@Override
	public byte getClockValue()
	{
		return (byte) ((metaData & HALFMOVE_CLOCK_GETTER) >>> 40);
	}

	@Override
	public byte getFriendlySideValue()
	{
		return (byte) ((metaData & FRIENDLY_SIDE_GETTER) >>> 48);
	}

	@Override
	public byte getPiecePhase()
	{
		int pPhase = 24;
		for (int i = 1; i < 5; i++) {
			pPhase -= Long.bitCount(pieceLocations[i] | pieceLocations[i + 6]) * ChessMove.PIECE_PHASES[i];
		}
		return (byte) Math.max(0, pPhase);
	}

	@Override
	public short getMidgamePositionalEval()
	{
		return (short) ((metaData & MIDGAME_LOC_EVAL_GETTER) >>> 16);
	}

	@Override
	public short getEndgamePositionalEval()
	{
		return (short) (metaData & ENDGAME_LOC_EVAL_GETTER);
	}

	@Override
	public long[] getNewRecentHashings(final long newHash)
	{
		final long[] newRecentHashings = { newHash, 0L, 0L, 0L };
		System.arraycopy(recentHashings, 0, newRecentHashings, 1, 3);
		return newRecentHashings;
	}

	@Override
	public Side getFriendlySide()
	{
		return getFriendlySideValue() == 0 ? Side.W : Side.B;
	}

	@Override
	public Side getEnemySide()
	{
		return getFriendlySideValue() == 0 ? Side.B : Side.W;
	}

	@Override
	public long getSideLocations(final Side s)
	{
		long locs = 0L;
		final byte upperBound = (byte) (s.index() + 6);
		for (byte index = s.index(); index < upperBound; index++) {
			locs |= pieceLocations[index];
		}
		return locs;
	}

	@Override
	public long getOccupiedSquares()
	{
		return EngineUtils.multipleOr(pieceLocations);
	}

	@Override
	public long[] getPieceLocationsCopy()
	{
		final long[] copy = new long[12];
		System.arraycopy(pieceLocations, 0, copy, 0, 12);
		return copy;
	}

	public static BoardState getStartBoard()
	{
		final long startHash = getStateHasher().generateStartHash();

		return new BoardStateImpl(
				new long[] { startHash, 1L, 2L, 3L },
				0,
				0b1111,
				0,
				BoardState.NO_ENPASSANT,
				0,
				0,
				0,
				0,
				EngineUtils.getStartingDevStatus(),
				EngineUtils.getStartingPieceLocs());
	}

	@Override
	public ChessMove generateMove(final AlgebraicCommand com) throws AmbiguousPgnException
	{
		if (com.isPromotionOrder()) {
			final Sq target = com.getTargetSq();
			final PieceType toPromoteTo = com.getToPromoteTo();
			assert toPromoteTo != null;

			final List<Byte> possStarts = new ArrayList<>();
			for (final byte pawnLoc : EngineUtils.getSetBits(getPieceLocations(getFriendlySide().index()))) {
				final long friendly = getSideLocations(getFriendlySide()), enemy = getSideLocations(getEnemySide());
				final long mvSet = ChessPiece.get(getFriendlySide().index()).getMoveset(pawnLoc, friendly, enemy);

				if ((mvSet & target.getAsBB()) != 0) {
					possStarts.add(pawnLoc);
				}
			}
			if (possStarts.size() > 1) {
				throw new AmbiguousPgnException("Multiple promotion moves!");
			}
			else if (possStarts.size() == 0) {
				throw new AssertionError();
			}
			else {
				return PromotionMove.get(possStarts.get(0), target.ordinal(), toPromoteTo);
			}
		}

		final String castleOrder = com.getCastleOrder();

		if (castleOrder != null) {
			return CastleMove.get(getFriendlySide().isWhite() ? "WHITE" + castleOrder : "BLACK" + castleOrder);
		}

		final Sq targSq = com.getTargetSq();
		final byte targ = (byte) com.getTargetSq().ordinal();

		if (com.isAttackOrder() && getPieceAt(targ, getEnemySide()) == null) {
			final int startFile = com.getStartFile();
			final Sq start = Sq.getSq(startFile, targ / 8 - getFriendlySide().orientation());
			return EnPassantMove.get(start, targSq);
		}

		final int startRnk = com.getStartRow();
		final int startFle = com.getStartFile();

		final List<StandardMove> possibleStandardMoves = getMoves()
				.stream()
				.filter(StandardMove.class::isInstance)
				.map(StandardMove.class::cast)
				.collect(toList());

		final List<StandardMove> possibleMoves = new ArrayList<>();

		for (final StandardMove mv : possibleStandardMoves) {
			final ChessPiece p = getPieceAt(mv.getStart(), getFriendlySide());
			if (p == null) {
				System.out.println();
				System.out.println(getFriendlySide().name());
				System.out.println(mv.toString());
				throw new AmbiguousPgnException();
			}
			if (mv.getTarget() == targ && p.getPieceType() == com.getPieceToMove()) {
				if (startRnk < 0 && startFle < 0) {
					possibleMoves.add(mv);
				}
				else if (startFle >= 0 && startRnk < 0) {
					if (startFle == (7 - mv.getStart() % 8)) {
						possibleMoves.add(mv);
					}
				}
				else if (startRnk >= 0 && startFle < 0) {
					if (startRnk == mv.getStart() / 8) {
						possibleMoves.add(mv);
					}
				}
				else if (startFle == (7 - mv.getStart() % 8) && startRnk == mv.getStart() / 8) {
					possibleMoves.add(mv);
				}
			}
		}
		if (possibleMoves.isEmpty() || possibleMoves.size() > 2) {
			System.out.println(com.getAsString());
			System.out.println(getFriendlySide().name());
			throw new AmbiguousPgnException("Not found a move correctly.");
		}
		else if (possibleMoves.size() == 2) {
			final BitSet pinned = new BitSet();
			for (final int i : new int[] { 0, 1 }) {
				final byte startLoc = possibleMoves.get(i).getStart();
				final ChessPiece p = getPieceAt(startLoc, getFriendlySide());
				assert p != null;

				pieceLocations[p.index()] &= ~(1L << startLoc);
				if ((getSquaresAttackedBy(getEnemySide()) & pieceLocations[5 + getFriendlySide().index()]) != 0) {
					pinned.set(i);
				}
				pieceLocations[p.index()] |= (1L << startLoc);
			}
			final int pCard = pinned.cardinality();
			if (pCard == 0 || pCard == 2) {
				throw new AmbiguousPgnException(com.getAsString());
			}
			else {
				return possibleMoves.get(pinned.nextClearBit(0));
			}
		}
		else {
			return possibleMoves.get(0);
		}
	}

	@Override
	public void print()
	{
		printNbitBoards(join(pieceLocations, new long[] { metaData, devStatus }));
	}

	@Override
	public void printMoves()
	{
		getMoves().stream().forEach(x -> System.out.println(x.toString()));
	}

	@Override
	public long[] getHashes()
	{
		return Arrays.copyOf(recentHashings, recentHashings.length);
	}

	@Override
	public ChessPiece getPieceFromBB(final long fromset)
	{
		for (int i = 0; i < 12; i++) {
			if ((pieceLocations[i] & fromset) != 0) {
				return ChessPiece.get(i);
			}
		}
		return null;
	}

	@Override
	public long getPawnHash()
	{
		long hash = 0L;
		for (final byte ploc : EngineUtils.getSetBits(pieceLocations[0])) {
			hash ^= getStateHasher().getSquarePieceFeature(ploc, ChessPiece.get(0));
		}
		for (final byte ploc : EngineUtils.getSetBits(pieceLocations[6])) {
			hash ^= getStateHasher().getSquarePieceFeature(ploc, ChessPiece.get(6));
		}
		return hash;
	}
}
