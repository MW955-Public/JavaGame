
/** NetId(s): wz366, mw955
 * Name(s):Wenren Zhou, Mengxue Wang
 *
 * What I thought about this assignment:
 * Learned a lot! We love CS2110
 *
 */
package app;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import game.Edge;
import game.Node;

/** This class contains the shortest-path algorithm and other methods<br>
 * for a undirected graph. */
public class Path {

    /** Replace "-1" by the time you spent on A6 in hours.<br>
     * Example: for 3 hours 15 minutes, use 3.25<br>
     * Example: for 4 hours 30 minutes, use 4.50<br>
     * Example: for 5 hours, use 5 or 5.0 */
    public static double timeSpent= 10;

    /** Return the shortest path from node v to node end <br>
     * ---or the empty list if a path does not exist. <br>
     * Note: The empty list is NOT "null"; it is a list with 0 elements. */
    public static List<Node> shortest(Node v, Node end) {
        /* TODO Implement this method.
         * Read the A6 assignment handout for all details and
         * be aware of changes announced on pinned Piazza note for Assignment A6.
         * Remember, the graph is undirected. */

        Heap<Node> F= new Heap<>(false);

        /** map contains an entry for each node in F or S. <br>
         * ... Thus, |map| = |F| + |S|.<br>
         * ... For each such node, map contains the shortest known distance<br>
         * ... to the node and the node's Backpointer on that shortest path. */
        HashMap<Node, SF> map= new HashMap<>();

        F.add(v, 0);
        map.put(v, new SF(0, null));
        // inv: See the A6 handout, together with def of F and map.
        while (F.size() != 0) {
            Node f= F.poll();

            if (f == end) { return getPath(map, end); }
            int fDist= map.get(f).dist;

            for (Edge e : f.getExits()) {// for each neighbor w of f
                Node w= e.getOther(f);
                int newWdist= fDist + e.length;
                SF wDB= map.get(w);
                if (wDB == null) { // if w not in F or S
                    map.put(w, new SF(newWdist, f));
                    F.add(w, newWdist);
                } else if (newWdist < wDB.dist) {
                    wDB.dist= newWdist;
                    wDB.bkptr= f;
                    F.updatePriority(w, newWdist);
                }
            }
        }

        // no path from v to end
        return new LinkedList<>();
    }

    /** An instance contains information about a node: <br>
     * the Distance of this node from the start node and <br>
     * its Backpointer: the previous node on a shortest path <br>
     * from the start node to this node. */
    private static class SF {
        /** shortest known distance from the start node to this one. */
        private int dist;
        /** backpointer on path (with shortest known distance) from start node to this one */
        private Node bkptr;

        /** Constructor: an instance with dist d from the start node and<br>
         * backpointer p. */
        private SF(int d, Node p) {
            dist= d;     // Distance from start node to this one.
            bkptr= p;    // Backpointer on the path (null if start node)
        }

        /** return a representation of this instance. */
        @Override
        public String toString() {
            return "dist " + dist + ", bckptr " + bkptr;
        }
    }

    /** Return the path from the start node to node end.<br>
     * Precondition: SFdata contains all the necessary information about<br>
     * ............. the path. */
    public static List<Node> getPath(HashMap<Node, SF> SFdata, Node end) {
        List<Node> path= new LinkedList<>();
        Node p= end;
        // invariant: All the nodes from p's successor to the end are in
        // path, in reverse order.
        while (p != null) {
            path.add(0, p);
            p= SFdata.get(p).bkptr;
        }
        return path;
    }

    /** Return the sum of the weights of the edges on path pa. <br>
     * Precondition: pa contains at least 1 node. <br>
     * If 1 node, it's a path of length 0, i.e. with no edges. */
    public static int pathSum(List<Node> pa) {
        synchronized (pa) {
            Node v= null;
            int sum= 0;
            // invariant: if v is null, n is the first node of the path.<br>
            // ......... if v is not null, v is the predecessor of n on the path.
            // sum = sum of weights on edges from first node to v
            for (Node n : pa) {
                if (v != null) {
                    sum= sum + v.getEdge(n).length;
                }
                v= n;
            }
            return sum;
        }
    }

}
