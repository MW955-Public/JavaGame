package app;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import game.Edge;
import game.HuntState;
import game.Hunter;
import game.Node;
import game.NodeStatus;
import game.ScramState;

/** A solution with huntOrb optimized and scram getting out as fast as possible. */
public class Pollack extends Hunter {

    /** Get to the orb in as few steps as possible. <br>
     * Once you get there, you must return from the function in order to pick it up. <br>
     * If you continue to move after finding the orb rather than returning, it will not count.<br>
     * If you return from this function while not standing on top of the orb, it will count as a
     * failure.
     *
     * There is no limit to how many steps you can take, but you will receive<br>
     * a score bonus multiplier for finding the orb in fewer steps.
     *
     * At every step, you know only your current tile's ID and the ID of all<br>
     * open neighbor tiles, as well as the distance to the orb at each of <br>
     * these tiles (ignoring walls and obstacles).
     *
     * In order to get information about the current state, use functions<br>
     * currentLocation(), neighbors(), and distanceToOrb() in HuntState.<br>
     * You know you are standing on the orb when distanceToOrb() is 0.
     *
     * Use function moveTo(long id) in HuntState to move to a neighboring<br>
     * tile by its ID. Doing this will change state to reflect your new position.
     *
     * A suggested first implementation that will always find the orb, but <br>
     * likely won't receive a large bonus multiplier, is a depth-first search. <br>
     * Some modification is necessary to make the search better, in general. */
    @Override
    public void huntOrb(HuntState state) {
        // TODO 1: Get the orb
        Set<Long> visited= new HashSet<>();
        dfs(state, visited);

    }

    /** Visit every node reachable along a path of unvisited tiles from tile t. <br>
     * Precondition: t is not visited. */
    public void dfs1(HuntState t, Set<Long> visited) {
        long id= t.currentLocation();
        visited.add(id);
        if (t.distanceToOrb() == 0) { return; }

        for (NodeStatus w : t.neighbors()) {
            long wid= w.getId();
            if (!visited.contains(wid)) {
                t.moveTo(wid);
                dfs1(t, visited);
                if (t.distanceToOrb() == 0) { return; }
                t.moveTo(id);
            }
        }
        return;

    }

    public void dfs(HuntState t, Set<Long> visited) {
        long id= t.currentLocation();
        visited.add(id);
        if (t.distanceToOrb() == 0) { return; }

        for (NodeStatus w : t.neighbors()) {
            long wid= 0;
            int shortestDist= Integer.MAX_VALUE;
            for (NodeStatus w2 : t.neighbors()) {
                if (!visited.contains(w2.getId())) {
                    if (w2.getDistanceToTarget() < shortestDist) {
                        shortestDist= w2.getDistanceToTarget();
                        wid= w2.getId();
                    }
                }
            }

            if (wid == 0) {
                return;
            } else {
                t.moveTo(wid);
                dfs(t, visited);
                if (t.distanceToOrb() == 0) { return; }
                t.moveTo(id);
            }
        }
        return;
    }

    /** Get out the cavern before the ceiling collapses, trying to collect as <br>
     * much gold as possible along the way. Your solution must ALWAYS get out <br>
     * before time runs out, and this should be prioritized above collecting gold.
     *
     * You now have access to the entire underlying graph, which can be accessed <br>
     * through ScramState. <br>
     * currentNode() and getExit() will return Node objects of interest, and <br>
     * getNodes() will return a collection of all nodes on the graph.
     *
     * Note that the cavern will collapse in the number of steps given by <br>
     * getStepsRemaining(), and for each step this number is decremented by the <br>
     * weight of the edge taken. <br>
     * Use getStepsRemaining() to get the time still remaining, <br>
     * pickUpGold() to pick up any gold on your current tile <br>
     * (this will fail if no such gold exists), and <br>
     * moveTo() to move to a destination node adjacent to your current node.
     *
     * You must return from this function while standing at the exit. <br>
     * Failing to do so before time runs out or returning from the wrong <br>
     * location will be considered a failed run.
     *
     * You will always have enough time to scram using the shortest path from the <br>
     * starting position to the exit, although this will not collect much gold. <br>
     * For this reason, using Dijkstra's to plot the shortest path to the exit <br>
     * is a good starting solution */
    @Override
    public void scram(ScramState state) {
        // TODO 2: Get out of the cavern before it collapses, picking up gold along the way
        Node start= state.currentNode();
        Node exit= state.getExit();
        List<Node> path= new LinkedList<>();

        path= shortest(start, exit);

        path.remove(0);
        for (Node i : path) {
            state.moveTo(i);
        }

    }

    /** Return the shortest path from node v to node end <br>
     * ---or the empty list if a path does not exist. <br>
     * Note: The empty list is NOT "null"; it is a list with 0 elements. */
    public static List<Node> shortest(Node v, Node end) {

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
