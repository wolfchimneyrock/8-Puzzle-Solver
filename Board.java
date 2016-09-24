/////////////////////////////////////////////////////////////////////////////////////
// Robert Wagner
// CISC 3410 Assignment #1
// 2016-09-11
// Board.java 
////////////////////////////////////////////////////////////////////////////////////
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Arrays;
import java.io.File;
import java.util.Scanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;
import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.CharSequence;
public class Board {
    
    public enum Heuristic {
        HAM, MAN, INT, NA
    }
    
    // constants:
    // directions
    public static final int UP         = 0;
    public static final int RIGHT      = 1;
    public static final int LEFT       = 2;
    public static final int DOWN       = 3;
    public static final int DIRECTIONS = 4;

    // ANSI colors 
    private static final String ANSI_BOLD   = "\033[1m";   
    private static final String ANSI_RESET  = "\033[0;0m";
    
    // according to Burns, et all 2012 the average best order for 
    // all solvable games of 3x3 and 4x4 is URLD
    // my own findings with a sample set of games confirms
    // (the most affected by order is DFS)
            
    private static int[] ORDER = { Board.UP, Board.RIGHT, Board.LEFT, Board.DOWN };

    // things that won't change per game
    private static Heuristic heuristic;
    private static int dim;
    private static boolean solvable;

    // lookup tables
    private static byte[][] manhattanTable;
    private static boolean[][] isValidMove; 
    private static byte[] rowOf;
    private static byte[] colOf;

    // read board state from a string
    // should only be performed for the initial game board
    public Board(String blocks, Heuristic h, int[] order) {
        Scanner s = new Scanner(blocks);
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        while (s.hasNextInt()) bs.write(s.nextInt());
        state = bs.toByteArray();
        for (int i = 0; i < state.length; i++) {
            if (state[i] == 0) zero = i;
        }
        Board.dim       = (int)Math.sqrt((double)state.length);
        if (state.length != Board.dim*Board.dim) {
            System.out.println("  Invalid input length.");
        }
        Board.heuristic = h;
        if (order != null)
            Board.ORDER     = order;
        // build lookup tables of rows and columns
        if (Board.rowOf == null || Board.rowOf.length != state.length) {
            Board.rowOf = new byte[state.length];
            Board.colOf = new byte[state.length];
        }
        for (int i = 0; i < state.length; i++) {
            Board.rowOf[i] = (byte)(i / Board.dim);
            Board.colOf[i] = (byte)(i % Board.dim);
        }
        // perform initial state cost analysis
        switch(Board.heuristic) {
            case HAM: dist  = hamming();   break;
            case INT: inter = conflicts();
            case MAN: // build lookup table for manhattan distance to avoid costly divide and modulus ops
                if (Board.manhattanTable == null || Board.manhattanTable[0].length != state.length) {
                    Board.manhattanTable = new byte[state.length][state.length];
                        for (int i = 1; i < state.length; i++) 
                            for (int n = 0; n < state.length; n++) 
                                Board.manhattanTable[i][n] = 
                                    (byte)(Math.abs(i % Board.dim - n % Board.dim) + 
                                           Math.abs(i / Board.dim - n / Board.dim));
                    }
                    dist = manhattan();
        }
        // build lookup table of valid moves
        if (Board.isValidMove == null || Board.isValidMove[0].length != state.length) {
            Board.isValidMove = new boolean[Board.DIRECTIONS][state.length];
            for (int n = 0; n < state.length; n++) {
                Board.isValidMove[Board.UP   ][n] = (Board.rowOf[n]     != 0        );
                Board.isValidMove[Board.RIGHT][n] = (Board.colOf[n] + 1 != Board.dim);
                Board.isValidMove[Board.LEFT ][n] = (Board.colOf[n]     != 0        );
                Board.isValidMove[Board.DOWN ][n] = (Board.rowOf[n] + 1 != Board.dim);
            }
        } 

        // check solvability
        // based on counting inversions
        // for odd dim, an even number of inversions is solvable, and odd is not
        // for even dim, the number of inversions correlates with row of zero position
        switch (Board.dim % 2) {
            case 1: Board.solvable = (inversions() % 2) == 0;                  break;
            case 0: Board.solvable = (inversions() % 2) == (zero/Board.dim)%2; break;
        }
    }

    // default to iterative manhattan + interference and URLD order
    public Board(String blocks) { this(blocks, Heuristic.INT, null); }
    public Board(String blocks, Heuristic heuristic) {
        this(blocks, heuristic, null);
    }

    public static boolean setOrder(String order) {
        boolean isValid  = true;
        boolean hasUp    = false;
        boolean hasDown  = false;
        boolean hasRight = false;
        boolean hasLeft  = false;
        if (order.length() != 4) return false;
        order = order.toUpperCase();
        for (int i = 0; i < order.length(); i++)
            switch(order.charAt(i)) {
                case 'U': if (hasUp) isValid = false;
                          else Board.ORDER[i] = Board.UP;
                          hasUp = true;
                          break;
                case 'D': if (hasDown) isValid = false;
                          else Board.ORDER[i] = Board.DOWN;
                          hasDown = true;
                          break;
                case 'L': if (hasLeft) isValid = false;
                          else Board.ORDER[i] = Board.LEFT;
                          hasLeft = true;
                          break;
                case 'R': if (hasRight) isValid = false;
                          else Board.ORDER[i] = Board.RIGHT;
                          hasRight = true;
                          break;
                default : isValid = false;
            }
         return isValid;
    }
    public static String getOrder() {
        StringBuffer temp = new StringBuffer();
        for (int i: Board.ORDER)
           switch(i) {
               case Board.UP:    temp.append("U"); break;
               case Board.DOWN:  temp.append("D"); break;
               case Board.RIGHT: temp.append("R"); break;
               case Board.LEFT:  temp.append("L"); break;
           }
        return temp.toString();
    }
    // check if a board is valid
    // this should be done only once per game - exception catching is expensive
    public boolean isValid() {
        if (state.length != Board.dim*Board.dim) return false;
        if ((double)Board.dim != Math.sqrt((double)state.length)) return false;
        byte[] counts = new byte[state.length];
        try {
            for (int i = 0; i < counts.length; i++) counts[state[i]]++;
        } catch (ArrayIndexOutOfBoundsException e) { return false; }
        for (int i = 0; i < counts.length; i++) if (counts[i] != 1) return false;
        return true;
    }

    public int inversions() {
        int total = 0;
        for (int i = 0; i < state.length; i++)
            if (state[i] != 0)
                for( int j = i + 1; j < state.length; j++)
                    if (state[j] != 0 && state[i] > state[j]) total++;
        return total;
    }

    // This counts number of interferences for the entire board.
    // Should be run only for the initial game state,
    // swap() iteratively modifies it for successor states.
    public int conflicts() {
        int total = 0;
        int dist;
        if (!isValid()) return 0;
        for (int r = 0; r < Board.dim; r++) {
            int row = r*Board.dim; 
            for (int i = row; i < row + Board.dim; i++)
               for (int j = i + 1; j < row + Board.dim; j++) 
                   if (Board.rowOf[state[i]] == r && 
                       Board.rowOf[state[j]] == r &&
                       state[i] != 0 && state[j] != 0 &&
                       state[i] > state[j])
                           total = total + 1;
        }
        for (int col = 0; col < Board.dim; col++) 
           for (int i = col; i < state.length - Board.dim; i+= Board.dim) {
              for (int j = i + Board.dim; j < state.length; j+= Board.dim) 
                  if (Board.colOf[state[i]] == col &&
                      Board.colOf[state[j]] == col &&
                      state[i] != 0 && state[j] != 0 &&
                      state[i] > state[j])
                          total = total + 1;
           }  
        return total; 
    }
    
    // read board state explicitly - used for making neighbors
    // the swap() method is responsible for setting the other vars
    private Board(byte [] blocks) {
        this.state = Arrays.copyOf(blocks, blocks.length);
    }

    private ArrayList<Board> findNeighbors() {
        ArrayList<Board> nb = new ArrayList<Board>(4);
        for (int dir: Board.ORDER)
            if (Board.isValidMove[dir][zero]) nb.add(moveTo(dir));
        return nb;
    }

    public void      setOrder(int[] order) { Board.ORDER = order; }
    public boolean   isSolvable() { return solvable; }
    public int       dimension()  { return Board.dim;}
    public Heuristic heuristic()  { return Board.heuristic;}
    public int       distance()   {
        switch(Board.heuristic) { 
            case INT: return dist + 2*inter;
            default:  return dist;
        }
    }

    // simple heuristic - count cells out of place
    public int hamming() { 
        int total = 0;
        for (int n = 0; n < state.length; n++) 
            if (state[n] != 0 && state[n] != n) total++;
        return total;
    }

    // manhattan distance heuristic
    // used to calc distance for original board only.
    // table lookup results in about 25% performance improvement
    public int manhattan() {
        int total = 0;
        if (!isValid()) return 0;
        for (int n = 0; n < state.length; n++)
            total = total + Board.manhattanTable[state[n]][n];
        return total; 
    }

    // swaps the zero position to create a new neighbor, and 
    // incrementally updates manhattan distance and 
    // interference if we're using them
    private Board moveTo(int direction) {
        Board newBoard = new Board(this.state);
        int oZ = this.zero;
        int nZ = oZ;
        switch (direction) {
            case Board.LEFT:  nZ = oZ - 1;         break;
            case Board.RIGHT: nZ = oZ + 1;         break;
            case Board.UP:    nZ = oZ - Board.dim; break;
            case Board.DOWN:  nZ = oZ + Board.dim; break;
            default: return null;
        }
        newBoard.state[oZ] = state[nZ];
        newBoard.state[nZ] = 0;
        newBoard.zero      = nZ;

        // update heuristic iteratively except for MAN
        switch(Board.heuristic) {
            case HAM:
                if      (state[nZ] == oZ) newBoard.dist = this.dist - 1;
                else if (state[nZ] == nZ) newBoard.dist = this.dist + 1;
                else                      newBoard.dist = this.dist;
                break;
            case INT:  // iterative update the interference
                       // this is the most logically complicated code I have
                       // done in a while
                int moved = state[nZ];
                int oC;
                newBoard.inter = inter;
                switch (direction) {
                   case Board.LEFT:
                   case Board.RIGHT:              // LEFT AND RIGHT
                       if (Board.colOf[state[nZ]] == Board.colOf[nZ]) {
                           // we've replaced a piece on its col with a zero
                           // interference can only decrease since we know
                           // the piece didn't belong to its old column
                           //System.out.println("Took " + state[nZ] + " out of its col");
                           for (int i = Board.colOf[nZ]; i < nZ; i+=Board.dim)
                               if (Board.colOf[state[i]] == Board.colOf[nZ] &&
                                   state[i] > state[nZ])
                                       newBoard.inter--;
                           for (int i = nZ + Board.dim; i < state.length; i+=Board.dim)
                               if (Board.colOf[state[i]] == Board.colOf[nZ] &&
                                   state[i] < state[nZ])
                                       newBoard.inter--;
                          
                       } else if (Board.colOf[state[nZ]] == Board.colOf[oZ]) {
                           // we've put a piece on its col that was a zero
                           // interference can only increase since we know
                           // the piece didn't below to its old column
                           //System.out.println("Put  " + state[nZ] + " into its col");
                           for (int i = Board.colOf[oZ]; i < oZ; i+=Board.dim)
                               if (Board.colOf[state[i]] == Board.colOf[oZ] &&
                                   state[i] > state[nZ])
                                       newBoard.inter++;
                           for (int i = oZ + Board.dim; i < state.length; i+=Board.dim)
                               if (Board.colOf[state[i]] == Board.colOf[oZ] &&
                                   state[i] < state[nZ])
                                       newBoard.inter++;
                       }
                       break;
                   default:                // UP AND DOWN
                       if (Board.rowOf[state[nZ]] == Board.rowOf[nZ]) {
                           // we've replaced a piece on its row with a zero
                           // interference can only decrease since we know
                           // the piece didn't belong to its old row
                           //System.out.println("Took " + state[nZ] + " out of its row");
                           for (int i = Board.dim*Board.rowOf[nZ]; i < nZ; i++)
                               if (Board.rowOf[state[i]] == Board.rowOf[nZ] &&
                                   state[i] > state[nZ])
                                       newBoard.inter--;
                           for (int i = nZ+1; i < Board.dim*(Board.rowOf[nZ]+1); i++)
                               if (Board.rowOf[state[i]] == Board.rowOf[nZ] &&
                                   state[i] < state[nZ])
                                       newBoard.inter--;
                       } else if (Board.rowOf[state[nZ]] == Board.rowOf[oZ]) {
                           // we've put a piece on its row that was a zero
                           // interference can only increase since we know
                           // the piece didn't belong to its old row
                           //System.out.println("Put  " + state[nZ] + " into its row");
                           for (int i = Board.dim*Board.rowOf[oZ]; i < oZ; i++)
                               if (Board.rowOf[state[i]] == Board.rowOf[oZ] &&
                                   state[i] > state[nZ])
                                       newBoard.inter++;
                           for (int i = oZ+1; i < Board.dim*(Board.rowOf[oZ]+1); i++)
                               if (Board.rowOf[state[i]] == Board.rowOf[oZ] &&
                                   state[i] < state[nZ])
                                       newBoard.inter++;
                       }
                }  
            default: // update the manhattan distance
                newBoard.dist  = this.dist -  
                Board.manhattanTable[state[nZ]][nZ] +
                Board.manhattanTable[state[nZ]][oZ];
        }
        return newBoard;
    }

    public boolean isGoal() { return dist == 0; }

    public boolean equals(Object y) {
        if (this == y) return true;
        if (null == y) return false;
        if (y instanceof Board) 
            return Arrays.equals(state, ((Board)y).state);
        return false;
    }

    public Iterable<Board> neighbors() { return findNeighbors(); }

    public String toString() {
        StringBuffer temp = new StringBuffer();
        for (int n = 0; n < state.length; n++) {
            temp.append(' ');
            if (Board.dim >  3 && state[n] <=  9) temp.append(' ');
            if (Board.dim > 10 && state[n] <= 99) temp.append(' ');
            if (state[n] == 0)
                temp.append(ANSI_BOLD + "0" + ANSI_RESET);
            else temp.append(state[n]);
            if ((n + 1) % Board.dim == 0) temp.append('\n');
        }
        return temp.toString();
    }

    public int    getZero()  { return this.zero; }
    public byte[] getState() { return this.state;}

    // hash code is used only as a unique identifier for a given game for statistics
    // written to CSV to be visualized in R + ggplot and inserted into tex document
    // it is way slower than wrapping the game state with ByteBuffer
    // to test when a game state is visited
    public int hashCode() {
        return state.hashCode();
    }

    public static void main(String[] args) {

        Scanner s = new Scanner(System.in);
        if (args.length > 0) {
            try {
                s = new Scanner(new File(args[0]));
            } catch (IOException e) {};
        }
        System.out.println("Board unit test.  Enter board(s) per line, Control-C to exit.");
        System.out.print(">> ");
        while (s.hasNextLine()) {
            Board b = new Board(s.nextLine());
            b.unitTest();
            System.out.print(">> ");
        }
    }

    private void unitTest() {
        System.out.println("Input Board:");
        System.out.println(this.toString());
        System.out.println("isValid():     " + this.isValid());
        System.out.println("isSovable():   " + this.solvable);
        System.out.println("isGoal():      " + this.isGoal());
        System.out.println("Hamming:       " + this.hamming());
        System.out.println("Manhattan:     " + this.manhattan());
        System.out.println("Conflicts:     " + this.conflicts());
        System.out.println("Zero Position: " + this.zero);
        System.out.println("Neighbors: ");
        for (Board n: this.neighbors()) {
                System.out.println("Iterative " + Board.heuristic.name()+ " Distance: " + n.distance());
                if (Board.heuristic == Heuristic.INT)
                    System.out.println("Iterative Conflicts: " + n.inter);
                System.out.println("Iterative Manhattan: " + n.dist);
                System.out.println(n.toString());    
        }    
    }

    // private variables
    private byte[] state;
    private int    zero;
    private int    dist;
    private int    inter;
}
