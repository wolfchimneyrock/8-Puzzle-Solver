import edu.princeton.cs.algs4.MinPQ;
import java.util.Iterator;
import edu.princeton.cs.algs4.In;
import edu.princeton.cs.algs4.StdOut;
import java.util.ArrayDeque;
public class Solver {
    private class Node implements Comparable {
        private int moves;
        private Board board;
        private Node previous;

        public Node(Board board, int moves, Node previous) {
            this.board = board;
            this.previous = previous;
            this.moves = moves;
        }

        public boolean isRegression(Board board) {
            if (this.previous == null) return false;
            if (this.previous.board.equals(board)) return true;
            return false;
        }

        @Override
        public int compareTo(Object that) {
            if (that == null) throw new NullPointerException();
            int thisCost = this.moves + this.board.manhattan();
            int thatCost = ((Node)that).moves + ((Node)that).board.manhattan();
            return thisCost - thatCost;
        }

        public int moves() { return this.moves; }

        public Board board() { return this.board; }
        public Node next() { return this.previous; }
    }

    private boolean boardSolved;
    private boolean twinSolved;

    private MinPQ<Node> boardQ;
    private MinPQ<Node> twinQ; 
    private Node boardC;
    private Node twinC; 
    public Solver(Board initial) {
        if (initial == null) throw new NullPointerException();
        boardSolved = false;
        twinSolved = false;
        Node twin = new Node(initial.twin(), 0, null);
        Node init = new Node(initial, 0, null);
        boardQ = new MinPQ<Node>();
        twinQ  = new MinPQ<Node>();
        // enqueue first generation onto minpq
        for (Board c: init.board().neighbors()) {
            boardQ.insert(new Node(c, 1, init));
        }
        for (Board c: twin.board().neighbors()) {
            twinQ.insert(new Node(c, 1, twin));
        }
        // test initial and twin first
        if (initial.isGoal()) { boardSolved = true; boardC = init; }
        if (twin.board().isGoal()) { twinSolved = true; twinC = twin; }

        while (!boardSolved && !twinSolved) {
            boardC = boardQ.delMin();
            twinC  = twinQ.delMin();
            if (boardC.board.isGoal()) { boardSolved = true; continue; }
            if (twinC.board.isGoal())  { twinSolved  = true; continue; }
            for (Board b: boardC.board().neighbors()) 
                if (!boardC.isRegression(b))
                    boardQ.insert(new Node(b, 1 + boardC.moves(), boardC));
            for (Board b: twinC.board().neighbors())
                if (!twinC.isRegression(b))
                    twinQ.insert(new Node(b, 1 + twinC.moves(), twinC));

        }
    }

    public boolean isSolvable() { return boardSolved || !twinSolved; }

    public int moves() { return this.isSolvable() ? boardC.moves() : -1; }

    public Iterable<Board> solution() {
        if (!isSolvable()) return null;
        return new Iterable<Board>() {
            @Override
            public Iterator<Board> iterator() {
                final ArrayDeque<Board> stack = new ArrayDeque<Board>(1 + boardC.moves());
                Node current = boardC;
                while (current != null) {
                    stack.addFirst(current.board());
                    current = current.next();
                }
                return new Iterator<Board>() {
                    
                    @Override
                    public boolean hasNext() {
                        return !stack.isEmpty();
                    }

                    @Override
                    public Board next() {
                        return stack.removeFirst();
                    }
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public static void main(String[] args) {
      // create initial board from file
        In in = new In(args[0]);
        int N = in.readInt();
        int[][] blocks = new int[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                blocks[i][j] = in.readInt();
        Board initial = new Board(blocks);

      // solve the puzzle
        Solver solver = new Solver(initial);

      // print solution to standard output
        if (!solver.isSolvable())
            StdOut.println("No solution possible");
        else {
            StdOut.println("Minimum number of moves = " + solver.moves());
            for (Board board : solver.solution())
                StdOut.println(board);
        }
    }
}
