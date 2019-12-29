package game;

import java.util.Collection;

/** Scram means to get out, to run away.<br>
 * A ScramState provides all the information necessary to<br>
 * get out of the cavern and collect gold on the way.
 *
 * This interface provides access to the complete graph of the cavern,<br>
 * which will allow computation of the path.<br>
 * Once you have determined how Indiana should get out, call<br>
 * moveTo(Node) repeatedly to move to each node and grabGold() to <br>
 * collect gold on the way out. */
public interface ScramState {
	/** Return the Node corresponding to Indiana's location in the graph. */
	Node currentNode();

	/** Return the Node associated with the exit from the cavern. <br>
	 * Indiana has to move to this Node in order to get out. */
	Node getExit();

	/** Return a collection containing all the nodes in the graph. <br>
	 * They in no particular order. */
	Collection<Node> allNodes();

	/** Change Indiana's location to n. <br>
	 * Throw an IllegalArgumentException if n is not directly connected to Indiana's location. */
	void moveTo(Node n);

	/** Pick up the gold on the current tile. <br>
	 * Throw an IllegalStateException if there is no gold at the current location, <br>
	 * either because there never was any or because it was already picked up. */
	void grabGold();

	/** Return the steps remaining to get out of the cavern. <br>
	 * This value will change with every call to moveTo(Node), and <br>
	 * if it reaches 0 before you get out, you have failed to get out. */
	int stepsLeft();
}
