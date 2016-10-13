/////////////////////////////////////////////////////////////////////////////////////
// Robert Wagner
// CISC 3410 Assignment #1
// 2016-09-15
// Solver.java 
/////////////////////////////////////////////////////////////////////////////////////
import java.util.Iterator;
import java.util.List;
import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Deque;
import java.util.Scanner;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.File;
import java.lang.OutOfMemoryError;
public class Solver {

    
        // instead of doing this with static variables
        // i could implement a class to hold configuration info,
        // but for just a few it isn't worth the time
    private static boolean skipUnsolvable = true;
    private static boolean showPrompt     = true;
    private static boolean outputCSV      = false;
    private static boolean outputMoves    = true;
    private static boolean outputSolution = true;
    private static boolean outputTotal    = false;
    private static boolean outputSummary  = false;
    private static long    total          = 0;
    private static Stats   best, worst;
    // sort methods:
    // AST - A* (regular)
    // ASD - A* (no visited memory)
    // IDA - IDA*
    // BFS - Breadth-First
    // DFS - Depth-First
    public enum Method {
        AST, ASD, IDA, BFS, DFS
    }

    // statistics:
    // 1. cost of path = moves
    // 2. number of nodes expanded
    // 3. maximum depth of queue
    // 4. memory requirement
    // 5. running time
    public enum Stat {
        COST, EXPANDED, DEPTH, TIME, MEMORY
    }

    // hold collected summary and stats information
    private static MultiBuffer summary;
    private static Map<String, Stats> statistics;

    // ANSI colors 
    private static final String ANSI_BOLD   = "\033[1m";   
    private static final String ANSI_RED    = "\033[31m";   
    private static final String ANSI_RESET  = "\033[0;0m";
    private static final boolean BEST       = true;
    private static final boolean WORST      = false;
    
    private static class Stats {
        public Stats() { this(true); }
        public Stats(long cost, long expanded, long depth, long time, long memory) {
            stats = new HashMap<Stat, Long>(5);
            sortBest = true;
            stats.put(Stat.COST,     cost    );
            stats.put(Stat.EXPANDED, expanded);
            stats.put(Stat.DEPTH,    depth   );
            stats.put(Stat.TIME,     time    );
            stats.put(Stat.MEMORY,   memory  );
        }

        public Stats(boolean sort) {
            this.sortBest = sort;
            stats = new HashMap<Stat, Long>(5);
            if (sort == Solver.BEST) {
                    // initial values high
                stats.put(Stat.COST,     Long.MAX_VALUE);
                stats.put(Stat.EXPANDED, Long.MAX_VALUE);
                stats.put(Stat.DEPTH,    Long.MAX_VALUE);
                stats.put(Stat.TIME,     Long.MAX_VALUE);
                stats.put(Stat.MEMORY,   Long.MAX_VALUE);
            } else {
                    // initial values low
                stats.put(Stat.COST,     -Long.MAX_VALUE);
                stats.put(Stat.EXPANDED, -Long.MAX_VALUE);
                stats.put(Stat.DEPTH,    -Long.MAX_VALUE);
                stats.put(Stat.TIME,     -Long.MAX_VALUE);
                stats.put(Stat.MEMORY,   -Long.MAX_VALUE);
            }
        }

        public void update(Stat which, long value) {
            Long prev = stats.get(which);
            if (prev == null) return;
            if (sortBest) {
                if (value < prev.longValue())
                    stats.put(which, value);
            } else {
                if (value > prev.longValue())
                    stats.put(which, value);
            }
        }
        
        public void update(Stats obs) {
            if (sortBest) {
                for (Stat s: Solver.Stat.values()) 
                    if (obs.recall(s) < stats.get(s)) stats.put(s, obs.recall(s));
            } else {
                for (Stat s: Solver.Stat.values())
                    if (obs.recall(s) > stats.get(s)) stats.put(s, obs.recall(s));
            }
        }

        public long recall(Stat which) {
            Long value = stats.get(which);
            if (value != null) return value.longValue();
            return -1;
        } 

        boolean sortBest;
        Map<Stat, Long> stats;
    }


    private class Node implements Comparable {
        private int    moves;
        private Board  board;
        private Node   previous;
        private Method method;

        public Node(Board board, Node previous, Method method) {
            this.board      = board;
            this.previous   = previous;
            if (previous != null)
                 this.moves = previous.moves + 1;
            else this.moves = 0;
            this.method     = method;
        }

        public boolean isRegression(Board board) {
            if (this.previous == null) return false;
            switch(method) {
                case ASD:  // dumb version of A* that doesn't track where its been
                case IDA:  // and IDA: we only check that we're not the grand parent
                    if (this.previous.board.equals(board)) return true;
                    
                    break;
                default:   // everthing else uses a memory of prior locations.
                           // ByteBuffer.wrap() doesn't copy the data - it just gives a
                           // primitive byte[] a Comparable interface so that the HashSet
                           // can find it
                    if (visited.contains(ByteBuffer.wrap(board.getState()))) return true;
                    break;
            }
            return false;
        }

        @Override
        // Comparable interface
        // this is the comparator that the priority queue uses to ascertain order
        // minimum ordering of prior move count + heuristic distance
        public int compareTo(Object that) {
            if (that == null) throw new NullPointerException();
            int thisCost = this.moves + this.board.distance();
            int thatCost = ((Node)that).moves + ((Node)that).board.distance();
            return thisCost - thatCost;
        }

        // method added for DFS and IDA - prune the solution tree so that nodes can be
        // garbage collected when you reach a dead end and pop back up the stack
        public void pruneTo(Node limit) {
            if (this == limit) return;
            if (this.previous != null && this.previous != limit) this.previous.pruneTo(limit);
            this.previous = null;
        }

        public int   moves()   { return this.moves;    }
        public Board board()   { return this.board;    }
        public Node  next()    { return this.previous; }
    }

    private boolean             boardSolved; 
    private long                numExpanded;
    private long                maxDepth;
    private long                maxMem;
    private long                elapsedTime;
    private Set<ByteBuffer>     visited;
    private PriorityQueue<Node> boardPQ;
    private Deque<Node>         boardDQ;
    private Node                fringe;
    private Node                init;
    private Method              method;

    // put a game state onto the data structure.
    // depending on the search type, it is treated as
    // queue, stack, or priority queue
    private void put(Node node) {
        switch (this.method) {
            case BFS:
                boardDQ.addLast(node);
                break;
            case DFS:
            case IDA:
                boardDQ.addFirst(node);
                break;
            default:
                boardPQ.add(node);
                break;
        }
    } 
    // retrieve the next game state from the structure
    private Node get() {
        switch (this.method) {
            case DFS:
            case BFS:
            case IDA:
                if (!boardDQ.isEmpty())
                    return boardDQ.removeFirst();
                return null;
            default:
                if (!boardPQ.isEmpty())
                    return (Node)boardPQ.remove();
                return null;
        }
    }

    private boolean isEmpty() {
        switch (this.method) {
            case BFS:
            case DFS:
            case IDA:
                return boardDQ.isEmpty();
            default:
                return boardPQ.isEmpty();
        }
    }

    public long getElapsed() {
        return this.elapsedTime;
    }

    public Solver(Board initial, Method method) {
        if (initial == null) throw new NullPointerException();
        System.gc();
        Runtime runtime  = Runtime.getRuntime();
        long memBefore   = runtime.totalMemory() - runtime.freeMemory();
        this.method      = method;
        boardSolved      = false;
        numExpanded      = 0;
        maxDepth         = 0;
        maxMem           = 0;
        int currentDepth = 0;
        long endTime     = 0;
        long startTime   = System.currentTimeMillis();
        init             = new Node(initial, null, this.method);
        // tried a Fibonacci Heap - not any faster/smaller than JDK8 PQ
        boardPQ          = new PriorityQueue<Node>();
        boardDQ          = new ArrayDeque<Node>();
//////////////////////////////////////////////////////////////////////////////////////
//  Use various packages for the hashset
        visited          = new HashSet<ByteBuffer>();  // default jdk
//        visited          = new TreeSet<ByteBuffer>();  // jdk treeSet over 2x slower
//        visited          = new THashSet<ByteBuffer>();  // gnu trove - less memory ovh.
//        visited          = new ObjectOpenHashSet<ByteBuffer>();  // fastutil
//////////////////////////////////////////////////////////////////////////////////////        
        int childrenAdded;
        int maxCost = initial.distance();
        int absoluteMaxDFS = Integer.MAX_VALUE;
        // if DFS crashes, consider upping these values
        // making them too high makes DFS really stupid
        // and find terrible solutions
        switch (initial.dimension()) {
            case 3: absoluteMaxDFS = 50;  break;
            case 4: absoluteMaxDFS = 100; break;
            case 5: absoluteMaxDFS = 150; break;
        }
        // an experiment that didn't work well
        //currentDepth = (int)Math.sqrt(absoluteMaxDFS);

        if (Solver.skipUnsolvable && !initial.isSolvable()) {
            fringe = init;
            return;
        }
        if (method == Method.IDA) currentDepth = maxCost;
        this.put(init);
        
        if (method != Method.IDA && method != Method.ASD) visited.add(ByteBuffer.wrap(init.board.getState()));

        // the main loop
        while (!boardSolved && !this.isEmpty()) {
            long memNow = runtime.totalMemory() - runtime.freeMemory();
            if (memNow - memBefore > maxMem) 
                maxMem = memNow - memBefore;
            if (boardDQ.size() > maxDepth) 
                maxDepth = boardDQ.size();
            if (boardPQ.size() > maxDepth) {
                maxDepth = boardPQ.size();
            }

            // expand the next fringe node
            // and see if it won!
            fringe = this.get();
            numExpanded++;
            if (fringe.board.isGoal()) {
                boardSolved = true; 
                continue; 
            }

            // handle iterative deepening and add current to visited set
            switch(this.method) {
                case ASD: break; // do nothing at all
                case IDA:
                    // we have reached this depth limit, reset and start anew
                    if (this.isEmpty() && 
                        currentDepth < absoluteMaxDFS) {
                             this.put(init);
                             currentDepth = maxCost;
                    }
                    maxCost = 0;
                    break;
                case DFS:
                    // we have reached the end of the current depth possibilities,
                    // increase depth, reset visited for a new exploration
                    if (this.isEmpty() && 
                        currentDepth < absoluteMaxDFS) {
                            this.put(init);
                            visited.clear();
                            currentDepth = currentDepth + 1;
                    }
                default: // all methods except IDA
            //        visited.add(ByteBuffer.wrap(fringe.board.getState()));

            }

            // put the good children on the queue / stack
            childrenAdded = 0;
            if(method != Method.DFS || fringe.moves <= currentDepth)
                for (Board b: fringe.board().neighbors()) 
                    if (!fringe.isRegression(b)) {
                        if (method == Method.IDA && 
                            (b.distance() + fringe.moves > maxCost))
                                maxCost = b.distance() + fringe.moves;
                        if (method != Method.IDA || 
                            b.distance() + fringe.moves <= currentDepth) {
                                this.put(new Node(b, fringe, this.method));
                                if(method != Method.IDA && method != Method.ASD) visited.add(ByteBuffer.wrap(b.getState()));
                                childrenAdded++;
                        }
                    }

            // for stack based algorithms, prune the now exhausted branch
            if ((method == Method.DFS  ||
                 method == Method.IDA) && 
                 childrenAdded == 0) 
                     fringe.pruneTo(boardDQ.peekFirst().previous);

        }
        endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;
    }
    // this produce a list of moves to reach the goal state
    public String solutionMoves() {
        StringBuffer temp = new StringBuffer();
        int lastZero = init.board().getZero();
        for (Board b: this.solution()) {
            int diff = lastZero - b.getZero();
            switch (diff) {
               case  0:                                  break;
               case  1:               temp.append("L "); break;
               case -1:               temp.append("R "); break;
               default: if (diff > 0) temp.append("U "); else
                                      temp.append("D "); break;
            }
            lastZero = b.getZero();
        }
        temp.append("!");
        return temp.toString();
    }
    // CSV output to generate figures... csv file header should be:
    // id,method,heuristic,order,dim,moves,expanded,depth,time,memory
    public String toCSV() {
        StringBuffer temp = new StringBuffer();
        temp.append(init.board.hashCode());
        temp.append(',');
        temp.append(this.method.name());
        temp.append(',');
        if (!(this.method == Method.DFS || this.method == Method.BFS))
            temp.append(this.fringe.board.heuristic().name());
        temp.append(',');
        temp.append(Board.getOrder());
        temp.append(',');
        temp.append(this.fringe.board.dimension());
        temp.append(',');
        temp.append(this.fringe.moves);
        temp.append(',');
        temp.append(this.numExpanded);
        temp.append(',');
        temp.append(this.maxDepth);
        temp.append(',');
        temp.append(this.elapsedTime);
        temp.append(',');
        temp.append(this.maxMem);
        return temp.toString();
    }

    public String toString() {
        StringBuffer temp = new StringBuffer();
        temp.append("method:    ");
        temp.append(this.method.name());
        if (method != Method.BFS && method != Method.DFS) {
            temp.append(", heuristic: ");
            temp.append(this.fringe.board.heuristic().name());
        }
        temp.append(", order: ");
        temp.append(Board.getOrder());
        temp.append(", n: ");
        temp.append(this.fringe.board.dimension());
        temp.append(", solution moves: [ ");
        temp.append(ANSI_BOLD + this.fringe.moves + ANSI_RESET);
        temp.append(" ]\n  nExpanded: ");
        temp.append(this.numExpanded);
        temp.append(", maxDepth: ");
        temp.append(this.maxDepth);
        temp.append(", elapsed: ");
        temp.append(this.elapsedTime);
        temp.append("ms, approxMem: ");
        temp.append(this.maxMem/1024);
        temp.append("kb");
        return temp.toString();
    }

    public static String summaryOf(String method, Stats stats) {

        StringBuffer temp = new StringBuffer();
        temp.append(method);
        //if (method != Method.BFS && method != Method.DFS) 
        //    temp.append("-" + this.fringe.board.heuristic().name());
        temp.append("\n  cost:        ");
        long cost = stats.recall(Stat.COST);
        if (cost == Solver.best.recall(Stat.COST))
            temp.append(ANSI_BOLD);
        if (cost == Solver.worst.recall(Stat.COST))
            temp.append(ANSI_RED); 
        temp.append(cost + ANSI_RESET);
        temp.append("\n  numExpanded: ");
        long numExpanded = stats.recall(Stat.EXPANDED);
        if (numExpanded == Solver.best.recall(Stat.EXPANDED))
            temp.append(ANSI_BOLD);
        if (numExpanded == Solver.worst.recall(Stat.EXPANDED))
            temp.append(ANSI_RED); 
        temp.append(numExpanded + ANSI_RESET);
        temp.append("\n  maxDepth:    ");
        long maxDepth = stats.recall(Stat.DEPTH);
        if (maxDepth == Solver.best.recall(Stat.DEPTH))
            temp.append(ANSI_BOLD);
        if (maxDepth == Solver.worst.recall(Stat.DEPTH))
            temp.append(ANSI_RED); 
        temp.append(maxDepth + ANSI_RESET);
        temp.append("\n  elapsedTime: ");
        long elapsedTime = stats.recall(Stat.TIME);
        if (elapsedTime == Solver.best.recall(Stat.TIME))
            temp.append(ANSI_BOLD);
        if (elapsedTime == Solver.worst.recall(Stat.TIME))
            temp.append(ANSI_RED); 
        temp.append(elapsedTime);
        temp.append("ms"+ANSI_RESET+"\n  approxMem:   ");
        long maxMem = stats.recall(Stat.MEMORY);
        if (maxMem == Solver.best.recall(Stat.MEMORY))
            temp.append(ANSI_BOLD);
        if (maxMem == Solver.worst.recall(Stat.MEMORY))
            temp.append(ANSI_RED); 
        temp.append(maxMem/1024);
        // pad for nice even columns
        temp.append("kb"+ANSI_RESET+"  \n                          ");
        return temp.toString();
    }
    
    public boolean isSolvable() { return boardSolved; }

    public int moves() { return this.isSolvable() ? fringe.moves() : -1; }

    public Iterable<Board> solution() {
        // i removed this during testing so that i could watch partial solutions as they
        // grow, but there is no harm now in re-enabling this check
        //if (!isSolvable()) return null;
        return new Iterable<Board>() {
            @Override
            public Iterator<Board> iterator() {
                final ArrayDeque<Board> stack = new ArrayDeque<Board>(1 + fringe.moves());
                Node current = fringe;
                while (current != null) {
                    stack.addFirst(current.board());
                    current = current.next();
                }
                return stack.iterator();
            }
        };
    }
    public static void displayHelp() {

        System.out.println("Sliding Puzzle Solver");
        System.out.println("Robert Wagner CISC 3410");
        System.out.println();
        System.out.println("USEAGE:");
        System.out.println("    java -jar Solver.jar [options] [input filename]");
        System.out.println();
        System.out.println("The default solver is IDA* with Manhattan + Interference heuristic (IDA, INT)");
        System.out.println("In Up, Right, Left, Down order (URLD)");
        System.out.println();
        System.out.println("From the command line the following options are available:");
        System.out.println();
        System.out.println("Output options:");
        System.out.println(" -csv     : Output in comma-separated values format.");
        System.out.println(" -nosol   : Suppress output of solution sequences.");
        System.out.println(" -noskip  : Try to solve unsolvable puzzles.");
        System.out.println(" -nomove  : Suppress output of board move diagrams.");
        System.out.println(" -total   : Show total execution time of batches.");
        System.out.println(" -summary : Display a summary of comparison statistics at the end of each puzzle.");
        System.out.println(" -help    : Show this help message.");
        System.out.println();
        System.out.println("Algorithm options (In order from least to most efficient):");
        System.out.println(" -all : attempt to use all known algorithms");
        System.out.println();
        System.out.println(" -bfs : BFS (breadth - first search)  [ uninformed, guaranteed optimal solutions.   ]");
        System.out.println(" -dfs : DFS (depth - first search)    [ uninformed, non-optimal solutions possible. ]");
        System.out.println(" -asd : A* (dumb version)             [ informed, no prior knowledge, optimal.      ]");
        System.out.println(" -ast : A* (standard version)         [ informed, uses prior knowledge, optimal.    ]");
        System.out.println(" -ida : IDA* (Iterative Deepening A*) [ informed, no prior knowledge, optimal.      ]");
        System.out.println();
        System.out.println("Heuristic options for A* searches (In order from least to most efficient);");
        System.out.println(" -ham : Hamming distance              [ naively counts out-of-place cells..         ]");
        System.out.println(" -man : Manhattan distance            [ discrete sum of x and y offsets.            ]");
        System.out.println(" -int : Manhattan + Interference dist.[ same as MAN plus obstacle detours           ]");
        System.out.println();
        System.out.println("Fringe exploration order options:");
        System.out.println(" --URLD, --DRUL, etc...  any permutation of the directions following '--'");
    }


    private static void solve(Board initial, Method method) {
                Solver solver;
                try {
                        solver = new Solver(initial, method);
                } catch (OutOfMemoryError e) {
                    if (Solver.showPrompt || !Solver.outputCSV) 
                        System.out.println("  method:   "+method.name() + " failed, out of memory.");
                    else System.out.println("# Out of memory: " + method.name());  
                    return;
                }
                if (Solver.outputSummary){
                    Stats s = new Stats(solver.fringe.moves,
                                        solver.numExpanded,
                                        solver.maxDepth,
                                        solver.elapsedTime,
                                        solver.maxMem);
                    Solver.best.update(s);
                    Solver.worst.update(s);
                    String key;
                    if (method == Method.BFS || method == Method.DFS)
                        key = method.name();
                    else key = method.name() + "-" + initial.heuristic().name();
                    Solver.statistics.put(key,s);
                    /*
                    Solver.best.update (Stat.COST,     solver.fringe.moves);
                    Solver.worst.update(Stat.COST,     solver.fringe.moves);
                    Solver.best.update (Stat.EXPANDED, solver.numExpanded);
                    Solver.worst.update(Stat.EXPANDED, solver.numExpanded);
                    Solver.best.update (Stat.DEPTH,    solver.maxDepth);
                    Solver.worst.update(Stat.DEPTH,    solver.maxDepth);
                    Solver.best.update (Stat.TIME,     solver.elapsedTime);
                    Solver.worst.update(Stat.TIME,     solver.elapsedTime);
                    Solver.best.update (Stat.MEMORY,   solver.maxMem);
                    Solver.worst.update(Stat.MEMORY,   solver.maxMem);
                    */
                    //Solver.summary.add(solver.toSummary());
                }
                if (Solver.outputTotal) Solver.total = total + solver.getElapsed();
                if (Solver.outputCSV) System.out.println(solver.toCSV());
                else                  System.out.println("  " + solver);
                if (Solver.outputSolution) {
                    if (!solver.isSolvable())
                         System.out.println("  No Obvious Solution.  use -noskip option to try anyway.");
                    else System.out.println("  solution:  " + solver.solutionMoves());
                }
                if (Solver.outputMoves && solver.isSolvable()) {
                    MultiBuffer mb = new MultiBuffer();
                    System.out.println();
                    String moves = solver.solutionMoves();
                    int i = 0;
                    for (Board b: solver.solution()) {
                        mb.add(b.toString());
                        mb.add("\n " + moves.charAt(2*i) + "->\n");
                        i++;
                    }
                    mb.add("\n"+ANSI_BOLD+" WIN!"+ANSI_RESET);
                    System.out.print(mb);
                }
    } 

    public static void main(String ... args) {
        boolean              finished      = false;
        Set<Method>          useMethods    = new LinkedHashSet<Method>();
        Set<Board.Heuristic> useHeuristics = new LinkedHashSet<Board.Heuristic>();
        Scanner              s             = new Scanner(System.in);
        if (args.length > 0) {
            for (String arg: args) {
                if (arg.charAt(0) == '-') {
                    if (arg.length() == 6 && arg.charAt(1) == '-') {
                        if (!Board.setOrder(arg.substring(2))) {
                            System.out.println("Invalid order " + arg.substring(2));
                            System.exit(1);
                        }
                    }
                    else
                    switch(arg.substring(1).toUpperCase()) {
                        case "ALL"     : useMethods.add(Method.BFS);
                                         useMethods.add(Method.DFS);
                                         useMethods.add(Method.ASD);
                                         useMethods.add(Method.AST);
                                         useMethods.add(Method.IDA);
                                         useHeuristics.add(Board.Heuristic.HAM);
                                         useHeuristics.add(Board.Heuristic.MAN);
                                         useHeuristics.add(Board.Heuristic.INT);
                                         break;
                        case "HELP"    : displayHelp(); System.exit(0); break;
                        case "CSV"     : Solver.outputCSV      = true;
                                         Solver.outputMoves    = false;
                                         Solver.outputSolution = false;
                                         break;
                        case "NOSOL"   : Solver.outputSolution = false; break;
                        case "NOSKIP"  : Solver.skipUnsolvable = false; break;
                        case "NOMOVE"  : Solver.outputMoves    = false; break;
                        case "BFS"     : useMethods.add(Method.BFS);    break;
                        case "DFS"     : useMethods.add(Method.DFS);    break;
                        case "AST"     : useMethods.add(Method.AST);    break;
                        case "ASD"     : useMethods.add(Method.ASD);    break;                
                        case "IDA"     : useMethods.add(Method.IDA);    break;
                        case "TOTAL"   : Solver.outputTotal   = true;   break;
                        case "SUMMARY" : Solver.outputSummary = true;   break;
                        case "HAM"     : useHeuristics.add(Board.Heuristic.HAM); break;
                        case "MAN"     : useHeuristics.add(Board.Heuristic.MAN); break;
                        case "INT"     : useHeuristics.add(Board.Heuristic.INT); break;
                        default:
                            displayHelp();  
                            System.out.println("\nInvalid option " + arg);
                            System.exit(1);
                    }
                } else
                try {
                    s = new Scanner(new File(arg));
                    showPrompt = false;
                } catch (IOException e) {};
            }
        }
        if (useMethods.isEmpty())       useMethods.add(Method.IDA);
        if (useHeuristics.isEmpty()) useHeuristics.add(Board.Heuristic.INT);

        if (showPrompt) {
            displayHelp();
            System.out.println("Enter initial puzzle state on a line i.e:");
            System.out.println("    8 7 6 5 4 3 2 1 0");
            System.out.println("Enter a blank line to exit.");
        }
        while (!finished) {
            if (showPrompt) System.out.print(">> ");
            if (!s.hasNextLine()) { finished = true; continue; }
            String line = s.nextLine();
            if (line.length() < 1) { 
                if (showPrompt) finished = true; 
                continue;
            }
            if (line.charAt(0) == '#') continue;
            if (!showPrompt && !outputCSV) System.out.println("[ " + line + " ]");
            Board initial = new Board(line, Board.Heuristic.INT);
            if (initial == null || !initial.isValid()) {
                if (showPrompt || !outputCSV) 
                    System.out.println("  Board is not valid, skipping.");
                    continue;
            }
            if (!Solver.skipUnsolvable && !initial.isSolvable()) 
                if (showPrompt || !outputCSV) {
                    System.out.println("  Board is not solvable, skipping.  use -noskip option to try anyway.");
                    continue;
                }
            if (outputSummary) { 
                Solver.summary    = new MultiBuffer();
                Solver.statistics = new HashMap<String, Stats>();
                Solver.best       = new Stats(Solver.BEST);
                Solver.worst      = new Stats(Solver.WORST);
            }
          // solve the puzzle in each method
            for (Method method:  useMethods) {
               switch(method) {
                   case BFS:
                   case DFS:
                       solve(initial, method);
                       break;
                   default:
                       for (Board.Heuristic heuristic: useHeuristics) {
                           initial = new Board(line, heuristic);
                           solve(initial, method);
                       }
               }
            }
            if (outputSummary) {
                System.out.println("SUMMARY - Best results are " +
                                   ANSI_BOLD + "BOLD" + ANSI_RESET + 
                                   ", and worst results are " +
                                   ANSI_RED + "RED" + ANSI_RESET);
                for (Map.Entry<String, Stats> e: Solver.statistics.entrySet()) 
                    Solver.summary.add(Solver.summaryOf(e.getKey(), e.getValue()));
                System.out.println(Solver.summary.toString());
            }
        }
        if (outputTotal) System.out.println("Total time: " + total + "ms.");
    }
}
