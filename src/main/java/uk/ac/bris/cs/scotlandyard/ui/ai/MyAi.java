package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class

MyAi implements Ai {

    private static final int MAX_DEPTH = 7;
    private static ImmutableSet<Piece> players;
    private final Set<Integer> alreadyVisitedLocations = new HashSet<>();
    private int[][] shortestPaths;
    private Set<Integer> occupiedLocations;

    @Nonnull
    @Override
    public String name() {
        return "Sherbot Holmes";
    }


    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        players = board.getPlayers();
        occupiedLocations = new HashSet<>();
        for (Piece piece : players) {
            if (piece.isDetective()) {
                board.getDetectiveLocation((Piece.Detective) piece).ifPresent(occupiedLocations::add);
            }
        }

        precomputeShortestPaths(board.getSetup());

        List<Move> moves = new ArrayList<>(board.getAvailableMoves());

        moves.sort(Comparator.comparingDouble(move -> -(evaluateBoard(board, getDestination(move)) + evaluateMove(board, move))));
        List<Piece> pieces = new ArrayList<>(players);
        pieces.removeIf(Piece::isMrX);

        Move bestMove = moves.parallelStream().map(move -> Pair.pair(move, miniMax(simulateMrXMove(pieces, board), getDestination(move), MAX_DEPTH - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, pieces) + evaluateMove(board, move))).max(Comparator.comparingDouble(pair -> pair.right())).map(pair -> pair.left()).orElse(moves.get(0));

        updateVisitedLocations(bestMove);
        return bestMove;
    }

    private double miniMax(Board board, int mrXLocation, int depth, double alpha, double beta, List<Piece> remaining) {
        if (depth == 0 || !board.getWinner().isEmpty()) {
            return evaluateBoard(board, mrXLocation);
        }

        Piece currentPiece;
        List<Piece> nextRemaining;

        if (remaining.isEmpty()) {
            currentPiece = players.stream().filter(Piece::isMrX).findFirst().get();
            nextRemaining = players.stream().filter(piece -> !piece.isMrX()).collect(Collectors.toList());
        } else {
            currentPiece = remaining.get(0);
            nextRemaining = new ArrayList<>(remaining);
            nextRemaining.remove(0);
        }

        PriorityQueue<Move> moves = new PriorityQueue<>(Comparator.comparingDouble(move -> -(evaluateBoard(board, mrXLocation) + evaluateMove(board, move))));
        moves.addAll(board.getAvailableMoves());
        if (currentPiece.isMrX()) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (Move move : moves) {
                Board simulatedBoard = simulateMrXMove(nextRemaining, board);
                double eval = miniMax(simulatedBoard, getDestination(move), depth - 1, alpha, beta, nextRemaining) + evaluateMove(board, move);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (alpha >= beta) {
                    break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (Move candidateMove : moves) {
                Board simulatedBoard = simulateDetectiveMove(currentPiece, nextRemaining, mrXLocation, board, candidateMove);
                double eval = miniMax(simulatedBoard, mrXLocation, depth - 1, alpha, beta, nextRemaining);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private int getDestination(Move move) {
        return move.accept(new Move.Visitor<>() {
            @Override
            public Integer visit(Move.SingleMove singleMove) {
                return singleMove.destination;
            }

            @Override
            public Integer visit(Move.DoubleMove doubleMove) {
                return doubleMove.destination2;
            }
        });
    }

    private void updateVisitedLocations(Move move) {
        move.accept(new Move.Visitor<Void>() {
            @Override
            public Void visit(Move.SingleMove singleMove) {
                alreadyVisitedLocations.add(singleMove.destination);
                return null;
            }

            @Override
            public Void visit(Move.DoubleMove doubleMove) {
                alreadyVisitedLocations.add(doubleMove.destination1);
                alreadyVisitedLocations.add(doubleMove.destination2);
                return null;
            }
        });
    }

    private double evaluateBoard(Board board, int mrXLocation) {
        List<Piece> detectives = new ArrayList<>(players);
        detectives.removeIf(Piece::isMrX);
        Piece detective = detectives.get(0);
        if (board.getWinner().contains(detective)) {
            return Double.NEGATIVE_INFINITY;
        }

        // Calculate distance to the closest detective and unoccupied neighbours as freedom score
        double minDistance = calculateMinDistance(mrXLocation);
        double freedomScore = calculateFreedomScore(board, mrXLocation);

        return (minDistance * 20) + (freedomScore * 10);
    }

    private double evaluateMove(Board board, Move move) {
        // Evaluating Ticket Used
        double movePenalty = 0;
        int destination = getDestination(move);

        final int SECRET_MOVE_PENALTY = -25;
        final int DOUBLE_MOVE_PENALTY = -50;
        final int REPEATED_MOVE_PENALTY = -30;

        if (alreadyVisitedLocations.contains(destination)) {
            movePenalty += REPEATED_MOVE_PENALTY;
        }

        if (((board.getMrXTravelLog().size() < board.getSetup().moves.size() / 2) || (!(board.getSetup().moves.get(board.getMrXTravelLog().size()))) && calculateMinDistance(move.source()) > 2)) {
            movePenalty += move.tickets().iterator().next() == ScotlandYard.Ticket.SECRET ? SECRET_MOVE_PENALTY : 0;
            movePenalty += (move instanceof Move.DoubleMove) ? DOUBLE_MOVE_PENALTY : 0;
        }

        return movePenalty;
    }


    private int calculateFreedomScore(Board board, int mrXLocation) {
        int totalSum = 0;

        try {
            Set<Integer> adjacentNodes = board.getSetup().graph.adjacentNodes(mrXLocation);
            for (Integer neighbour : adjacentNodes) {
                if (!occupiedLocations.contains(neighbour)) {
                    totalSum++;
                }
            }
        } catch (Exception e) {
            return 0;
        }
        return totalSum;
    }

    private Board simulateDetectiveMove(Piece detective, List<Piece> remaining, int mrXLocation, Board board, Move move) {
        return new BoardProxy(board) {
            @Override
            @Nonnull
            public Optional<Integer> getDetectiveLocation(Piece.Detective detective2) {
                if (detective.equals(detective2)) {
                    return Optional.of(getDestination(move));
                }
                return super.getDetectiveLocation(detective2);
            }

            @Override
            @Nonnull
            public ImmutableSet<Move> getAvailableMoves() {
                if (remaining.isEmpty()) {
                    return ImmutableSet.copyOf(getMrXMoves(board, mrXLocation, board.getSetup()));
                }
                return ImmutableSet.copyOf(getDetectiveMoves(board, remaining.get(0), board.getSetup()));
            }
        };
    }


    private Board simulateMrXMove(List<Piece> remaining, Board board) {
        return new BoardProxy(board) {

            @Override
            @Nonnull
            public ImmutableSet<Move> getAvailableMoves() {
                return ImmutableSet.copyOf(getDetectiveMoves(board, remaining.get(0), board.getSetup()));
            }
        };
    }

    private Set<Move> getMrXMoves(Board board, int source, GameSetup setup) {
        if (!board.getWinner().isEmpty()) {
            return ImmutableSet.of();
        }
        Set<Move> moveSet = new HashSet<>(constructMrXMoves(source, setup, board));
        return ImmutableSet.copyOf(moveSet);
    }

    private Set<Move> getDetectiveMoves(Board board, Piece detective, GameSetup setup) {
        if (!board.getWinner().isEmpty()) {
            return ImmutableSet.of();
        }
        Set<Move> moveSet = new HashSet<>(constructDetectiveMoves(board.getDetectiveLocation((Piece.Detective) detective).get(), detective, setup, board));
        return ImmutableSet.copyOf(moveSet);
    }

    private Set<Move> constructDetectiveMoves(int currentLocation, Piece detective, GameSetup setup, Board board) {
        Set<Move> playerMoves = new HashSet<>();

        for (int destination : setup.graph.adjacentNodes(currentLocation)) {
            if (!occupiedLocations.contains(destination)) {
                for (ScotlandYard.Transport transport : setup.graph.edgeValueOrDefault(currentLocation, destination, ImmutableSet.of())) {
                    if (board.getPlayerTickets(detective).get().getCount(transport.requiredTicket()) > 0) {
                        Move.SingleMove move = new Move.SingleMove(detective, currentLocation, transport.requiredTicket(), destination);
                        playerMoves.add(move);
                    }
                }
            }
        }
        return playerMoves;
    }

    private Set<Move> constructMrXMoves(int currentLocation, GameSetup setup, Board board) {
        Piece mrXPiece = null;
        for (Piece piece : players) {
            if (piece.isMrX()) {
                mrXPiece = piece;
            }
        }

        Set<Move> playerMoves = new HashSet<>();
        for (int destination : setup.graph.adjacentNodes(currentLocation)) {
            if (!occupiedLocations.contains(destination)) {
                for (ScotlandYard.Transport transport : setup.graph.edgeValueOrDefault(currentLocation, destination, ImmutableSet.of())) {
                    if (board.getPlayerTickets(mrXPiece).get().getCount(transport.requiredTicket()) > 0) {
                        Move.SingleMove move = new Move.SingleMove(mrXPiece, currentLocation, transport.requiredTicket(), destination);
                        playerMoves.add(move);
                    }
                }

            }
        }
        return playerMoves;
    }

    private double calculateMinDistance(int mrXLocation) {
        int minDistance = Integer.MAX_VALUE;
        for (int detectiveLocation : occupiedLocations) {
            int distance = shortestPaths[mrXLocation][detectiveLocation];
            minDistance = Math.min(distance, minDistance);
        }
        return minDistance;
    }

    private void precomputeShortestPaths(GameSetup setup) {
        int size = 200;
        shortestPaths = new int[size][size];

        for (int i = 0; i < size; i++) {
            shortestPaths[i][i] = 0;
        }

        for (int i = 1; i < size; i++) {
            for (int neighbor : setup.graph.adjacentNodes(i)) {
                shortestPaths[i][neighbor] = 1;
            }
        }

        // Floyd-Warshall Algorithm
        for (int k = 1; k < size; k++) {
            for (int i = 1; i < size; i++) {
                for (int j = 1; j < size; j++) {
                    shortestPaths[i][j] = Math.min(shortestPaths[i][j], shortestPaths[i][k] + shortestPaths[k][j]);
                }
            }
        }
    }

    private static class BoardProxy implements Board {
        private final Board originalBoard;

        public BoardProxy(Board board) {
            this.originalBoard = board;
        }

        @Nonnull
        @Override
        public GameSetup getSetup() {
            return originalBoard.getSetup();
        }

        @Nonnull
        @Override
        public ImmutableSet<Piece> getPlayers() {
            return players;
        }

        @Nonnull
        @Override
        public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
            return originalBoard.getDetectiveLocation(detective);
        }

        @Nonnull
        @Override
        public Optional<TicketBoard> getPlayerTickets(Piece piece) {
            return originalBoard.getPlayerTickets(piece);
        }

        @Nonnull
        @Override
        public ImmutableList<LogEntry> getMrXTravelLog() {
            return originalBoard.getMrXTravelLog();
        }

        @Nonnull
        @Override
        public ImmutableSet<Piece> getWinner() {
            return originalBoard.getWinner();
        }

        @Nonnull
        @Override
        public ImmutableSet<Move> getAvailableMoves() {
            return originalBoard.getAvailableMoves();
        }
    }
}


