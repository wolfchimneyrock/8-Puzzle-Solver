import edu.princeton.cs.algs4.In;
import edu.princeton.cs.algs4.StdOut;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
public class Board {
    public Board(int[][] blocks) {
        this.dim = blocks.length;
        this.state = new int[dim * dim];
        for (int n = 0; n < dim * dim; n++) {
            state[n] = blocks[n / dim][(n % dim)];
            if (state[n] == 0) zeroPosition = n;
        }
    }

    private Board(int [] blocks, int dim, int zeroPosition) {
        this.dim = dim;
        this.state = Arrays.copyOf(blocks, blocks.length);
        this.zeroPosition = zeroPosition;
    }

    private ArrayList<Board> findNeighbors() {
        ArrayList<Board> nb = new ArrayList<Board>();
        if (zeroPosition / dim != 0)       nb.add(new Board(swap(zeroPosition, zeroPosition - dim), dim, zeroPosition - dim));
        if (zeroPosition % dim != 0)       nb.add(new Board(swap(zeroPosition, zeroPosition - 1),   dim, zeroPosition - 1));
        if (zeroPosition % dim != dim - 1) nb.add(new Board(swap(zeroPosition, zeroPosition + 1),   dim, zeroPosition + 1));
        if (zeroPosition / dim != dim - 1) nb.add(new Board(swap(zeroPosition, zeroPosition + dim), dim, zeroPosition + dim));
        return nb;
    }

    public int dimension() { return this.dim; }

    public int hamming() { 
        int total = 0;
        for (int n = 0; n < state.length; n++) 
            if (state[n] != 0 && state[n] != (n + 1)) total++;
        return total;
    }

    public int manhattan() {
        int total = 0;
        for (int n = 0; n < state.length; n++)
            if (state[n] != (n+1) && state[n] != 0)
                total += Math.abs(((state[n] - 1) % dim) - (n % dim)) + Math.abs(((state[n] - 1) / dim) - (n / dim) );
        return total; 
    }

    public boolean isGoal() {
        for (int n = 0; n < state.length - 1; n++)
            if (state[n] != n + 1) return false;
        return true;
    }

    public Board twin() { 
        int a = (zeroPosition + 1) % dim;
        int b = dim + (zeroPosition + 1) % dim;
        return new Board(swap(a,b), dim, zeroPosition); 
    }

    public boolean equals(Object y) {
        if (this == y) return true;
        if (null == y) return false;
        if (y instanceof Board) {
            if (this.dim != ((Board)y).dim) return false;
            return Arrays.equals(state, ((Board)y).state);
        }
        return false;
        //for (int n = 0; n < state.length; n++) 
        //    if (state[n] != (int[])y[n]) return false;
        //return true;
    }

    public Iterable<Board> neighbors() {
        if (nb == null) nb = findNeighbors();
        return new Iterable<Board>() {
            @Override
            public Iterator<Board> iterator() {
                return new Iterator<Board>() {
                    //private int current = 0;
                    private Iterator<Board> it = nb.iterator();
                    
                    @Override
                    public boolean hasNext() {
			return it.hasNext();
                    }

                    @Override
                    public Board next() {
                        //++current;
                        return it.next();
                    }
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }                    
                };
            }
        };
    }

    public String toString() {
        StringBuffer temp = new StringBuffer();
        temp.append(dim);
        temp.append('\n');
        for (int n = 0; n < state.length; n++) {
            temp.append(' ');
            if (dim > 3 && state[n] <= 9) temp.append(' ');
            if (dim > 10 && state[n] <= 99) temp.append(' ');
            temp.append(state[n]);
            if ((n + 1) % dim == 0) temp.append('\n');
        }
        return temp.toString();
    }

    public static void main(String[] args) {

        In in = new In(args[0]);
        int N = in.readInt();
        int[][] blocks = new int[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                blocks[i][j] = in.readInt();
        Board initial = new Board(blocks);
        StdOut.println("Input Board:");
        StdOut.println(initial);
        StdOut.println("Manhattan: " + initial.manhattan());
        StdOut.println("Hamming:   " + initial.hamming());
        StdOut.println("isGoal():  " + initial.isGoal());
        StdOut.println("Neighbors: ");
        for (Board n: initial.neighbors()) StdOut.println(n.toString());        
        StdOut.println("Twin Board:");
        Board twin = initial.twin();
        StdOut.println(twin);
        StdOut.println("Manhattan: " + twin.manhattan());
        StdOut.println("Hamming:   " + twin.hamming());
        StdOut.println("isGoal():  " + twin.isGoal());
        StdOut.println("Twin's Neighbors:");
        for (Board n: twin.neighbors()) StdOut.println(n.toString());
        StdOut.println("initial.equals(twin) = " + initial.equals(twin));
        
    }
    
    private int[] swap(int a, int b) {
        int newBoard[] = Arrays.copyOf(state, state.length);
        newBoard[a] = state[b];
        newBoard[b] = state[a];
        return newBoard;
    }
    private ArrayList<Board> nb;
    private int[] state;
    private int dim;
    private int zeroPosition;
}
