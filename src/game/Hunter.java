package game;

/** An abstract class representing what methods a hunter<br>
 * must implement in order to be used in solving the game. */
public abstract class Hunter {

	/** Hunt through the cavern for the orb in as few steps as possible. <br>
	 * Once the orb is found, you return from huntOrb in order to pick it up. <br>
	 * If you continue to move after finding the orb rather than returning, it will not count. <br>
	 * If you return from this function while not standing on top of the orb, <br>
	 * it will count as a failure.
	 *
	 * There is no limit to how many steps you can take, but you will <br>
	 * receive a score bonus multiplier for finding the orb in fewer steps.
	 *
	 * At every step, you know only your current tile's ID and the ID of all <br>
	 * open neighbor tiles, as well as the distance to the orb at each of these <br>
	 * tiles (ignoring walls and obstacles).
	 *
	 * In order to get information about the current state, use functions <br>
	 * getCurrentLocation(), getNeighbors(), and getDistanceToTarget() in huntState.<br>
	 * You know you are standing on the orb when getDistanceToTarget() is 0.
	 *
	 * Use function moveTo(long id) in huntState to move to a neighboring tile by its ID. <br>
	 * Doing this will change state to reflect your new position.
	 *
	 * A suggested first implementation that will always find the orb, but likely <br>
	 * won't receive a large bonus multiplier, is a depth-first walk.
	 *
	 * @param state the information available at the current state */
	public abstract void huntOrb(HuntState state);

	/** Get out of the cavern before the ceiling collapses, trying to collect as much <br>
	 * gold as possible along the way. <br>
	 * Your solution must ALWAYS get out before time runs out, and this should be <br>
	 * prioritized above collecting gold.
	 *
	 * You now have access to the entire underlying graph, which can be accessed <br>
	 * through ScramState. <br>
	 * currentNode() and getExit() returns Node objects of interest, and <br>
	 * getNodes() returns a collection of all nodes on the graph.
	 *
	 * Time is measured entirely in the number of steps taken. <br>
	 * For each step the time remaining is decremented by the weight of the edge taken. <br>
	 * Use getTimeRemaining() to get the time still remaining, <br>
	 * pickUpGold() to pick up any gold on your current tile <br>
	 * (this will fail if no such gold exists), and <br>
	 * moveTo() to move to a destination node adjacent to your current node.
	 *
	 * You must return from this function while standing at the exit. <br>
	 * Failing to do so before time runs out or returning from the wrong <br>
	 * location will be considered a failed run.
	 *
	 * You will always have enough time to get out using the shortest path from <br>
	 * the starting position to the exit, although this will not collect much gold. <br>
	 * But for this reason, using Dijkstra's to plot the shortest path to the exit <br>
	 * is a good starting solution.
	 *
	 * @param state the information available at the current state */
	public abstract void scram(ScramState state);
}
