package game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.Pollack;
import gui.GUI;

/** The state of the game, including huntOrg and scram phases */
public class GameState implements HuntState, ScramState {

	private enum Stage {
		HUNT, SCRAM;
	}

	@SuppressWarnings("serial")
	private static class OutOfStepsException extends RuntimeException {}

	static boolean shouldPrint= true;

	/** Minimum number of rows */
	public static final int MIN_ROWS= 8;

	/** Maximum number of rows */
	public static final int MAX_ROWS= 25;

	/** Minimum number of columns */
	public static final int MIN_COLS= 12;

	/** Maximum number of columns */
	public static final int MAX_COLS= 40;

	/** Number of seconds before huntOrb times out */
	public static final long HU_TIMEOUT= 10;

	/** Number of seconds before scram times out */
	public static final long SC_TIMEOUT= 15;

	/** Minimum bonus multiplier. */
	public static final double MIN_BONUS= 1.0;

	/** Maximum bonus multiplier. */
	public static final double MAX_BONUS= 1.3;

	private static final double EXTRA_STEPS_FACTOR= 0.3;  // bigger is nicer - addition to total
														  // multiplier
	private static final double NO_BONUS_LENGTH= 3;

	private final Cavern huntCavern;
	private final Cavern scramCavern;

	private final Hunter hunter;
	private final Optional<GUI> gui;

	private final long seed;

	private Node position;
	private int stepsTaken;
	private int stepsRemaining;
	private int goldCollected;

	private Stage stage;
	private boolean huntSucceeded= false;
	private boolean scramSucceeded= false;
	private boolean huntErrored= false;
	private boolean scramErrored= false;
	private boolean huntTimedOut= false;
	private boolean scramTimedOut= false;

	private int minHuntDistance;
	private int minScramDistance;

	private int huntStepsLeft= 0;
	private int scramStepsLeft= 0;

	private int minStepsToHunt;

	/** Constructor: a new GameState object for hunter exp. <br>
	 * This constructor takes a path to files storing serialized caverns <br>
	 * and simply loads these caverns. */
	/* package */ GameState(Path huntCavernPath, Path scramCavernPath, Hunter exp)
		throws IOException {
		huntCavern= Cavern.deserialize(Files.readAllLines(huntCavernPath));
		minStepsToHunt= huntCavern.minPathLengthToTarget(huntCavern.getEntrance());
		scramCavern= Cavern.deserialize(Files.readAllLines(scramCavernPath));

		hunter= exp;

		position= huntCavern.getEntrance();
		stepsTaken= 0;
		stepsRemaining= Integer.MAX_VALUE;
		goldCollected= 0;

		seed= -1;

		stage= Stage.HUNT;
		gui= Optional.of(new GUI(huntCavern, position.getTile().getRow(),
			position.getTile().getColumn(), 0));
	}

	/** Constructor: a new random game instance with or without a GUI. */
	private GameState(boolean useGui, Hunter exp) {
		this(new Random().nextLong(), useGui, exp);
	}

	/** Constructor: a new game instance using seed seed with or without a GUI, <br>
	 * and with the hunter used to solve the game. */
	/* package */ GameState(long seed, boolean useGui, Hunter exp) {
		Random rand= new Random(seed);
		int ROWS= rand.nextInt(MAX_ROWS - MIN_ROWS + 1) + MIN_ROWS;
		int COLS= rand.nextInt(MAX_COLS - MIN_COLS + 1) + MIN_COLS;
		huntCavern= Cavern.digHuntCavern(ROWS, COLS, rand);
		minStepsToHunt= huntCavern.minPathLengthToTarget(huntCavern.getEntrance());
		Tile orbTile= huntCavern.getTarget().getTile();
		scramCavern= Cavern.digHuntCavern(ROWS, COLS, orbTile.getRow(), orbTile.getColumn(),
			rand);

		position= huntCavern.getEntrance();
		stepsTaken= 0;
		stepsRemaining= Integer.MAX_VALUE;
		goldCollected= 0;

		hunter= exp;
		stage= Stage.HUNT;

		this.seed= seed;

		if (useGui) {
			gui= Optional.of(new GUI(huntCavern, position.getTile().getRow(),
				position.getTile().getColumn(), seed));
		} else {
			gui= Optional.empty();
		}
	}

	/** Run through the game, one step at a time. <br>
	 * Will run scram() only if hunt() succeeds.<br>
	 * Will fail in case of timeout. */
	void runWithTimeLimit() {
		huntWithTimeLimit();
		if (!huntSucceeded) {
			huntStepsLeft= huntCavern.minPathLengthToTarget(position);
			scramStepsLeft= scramCavern.minPathLengthToTarget(scramCavern.getEntrance());
		} else {
			scramWithTimeLimit();
			if (!scramSucceeded) {
				scramStepsLeft= scramCavern.minPathLengthToTarget(position);
				return;
			}

		}
	}

	/** Run through the game, one step at a time. <br>
	 * Will run scram() only if hunt() succeeds. <br>
	 * Does not use a timeout and will wait as long as necessary. */
	void run() {
		hunt();
		if (!huntSucceeded) {
			huntStepsLeft= huntCavern.minPathLengthToTarget(position);
			scramStepsLeft= scramCavern.minPathLengthToTarget(scramCavern.getEntrance());
		} else {
			scram();
			if (!scramSucceeded) {
				scramStepsLeft= scramCavern.minPathLengthToTarget(position);
				return;
			}
			gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Scram Succeeded"));

		}
	}

	/** Run only the hunt mode. Uses timeout. */
	void runHuntWithTimeout() {
		huntWithTimeLimit();
		if (!huntSucceeded) {
			huntStepsLeft= huntCavern.minPathLengthToTarget(position);
		}
	}

	/** Run only the scram mode. Uses timeout. */
	void runScramWithTimeout() {
		scramWithTimeLimit();
		if (!scramSucceeded) {
			scramStepsLeft= scramCavern.minPathLengthToTarget(position);
			return;
		}
		gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Scram Succeeded"));

	}

	@SuppressWarnings("deprecation")
	/** Wraps a call to hunt() with the timeout functionality. */
	private void huntWithTimeLimit() {
		FutureTask<Void> ft= new FutureTask<>(new Callable<Void>() {
			@Override
			public Void call() {
				hunt();
				return null;
			}
		});

		Thread t= new Thread(ft);
		t.start();
		try {
			ft.get(HU_TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			t.stop();
			huntTimedOut= true;
		} catch (InterruptedException | ExecutionException e) {
			System.err.println("ERROR");
			// Shouldn't happen
		}
	}

	/** Run the hunter's hunt() function with no timeout. */
	/* package */ void hunt() {
		stage= Stage.HUNT;
		stepsTaken= 0;
		huntSucceeded= false;
		position= huntCavern.getEntrance();
		minHuntDistance= huntCavern.minPathLengthToTarget(position);
		gui.ifPresent((g) -> g.setLighting(false));
		gui.ifPresent((g) -> g.updateCavern(huntCavern, 0));
		gui.ifPresent((g) -> g.moveTo(position));

		try {
			hunter.huntOrb(this);
			// Verify that we returned at the correct location
			if (position.equals(huntCavern.getTarget())) {
				huntSucceeded= true;
			} else {
				errPrintln("Your solution to hunt returned at the wrong location.");
				gui.ifPresent((g) -> g
					.displayError("Your solution to hunt returned at the wrong location."));
			}
		} catch (Throwable t) {
			if (t instanceof ThreadDeath) return;
			errPrintln("Your code errored during the hunt phase.");
			gui.ifPresent((g) -> g.displayError(
				"Your code errored during the hunt phase. Please see console output."));
			errPrintln("Here is the error that occurred.");
			t.printStackTrace();
			huntErrored= true;
		}
	}

	@SuppressWarnings("deprecation")
	/** Wrap a call to scram() with the timeout functionality. */
	private void scramWithTimeLimit() {
		FutureTask<Void> ft= new FutureTask<>(new Callable<Void>() {
			@Override
			public Void call() {
				scram();
				return null;
			}
		});

		Thread t= new Thread(ft);
		t.start();
		try {
			ft.get(SC_TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			t.stop();
			scramTimedOut= true;
		} catch (InterruptedException | ExecutionException e) {
			System.err.println("ERROR");
			// Shouldn't happen
		}
	}

	/** Handle the logic for running the hunter's scram() procedure with no timeout. */
	/* package */ void scram() {
		stage= Stage.SCRAM;
		Tile orbTile= huntCavern.getTarget().getTile();
		position= scramCavern.getNodeAt(orbTile.getRow(), orbTile.getColumn());
		minScramDistance= scramCavern.minPathLengthToTarget(position);
		stepsRemaining= computeStepsToScram();
		gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Scramming"));
		gui.ifPresent((g) -> g.setLighting(true));
		gui.ifPresent((g) -> g.updateCavern(scramCavern, stepsRemaining));

		try {
			if (position.getTile().gold() > 0) grabGold();
			hunter.scram(this);
			// Verify that we returned at the correct location
			if (position.equals(scramCavern.getTarget())) {
				scramSucceeded= true;
				gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Scram Succeeded"));
			} else {
				errPrintln("Your solution to scram returned at the wrong location.");
				gui.ifPresent((g) -> g
					.displayError("Your solution to scram returned at the wrong location."));
			}
		} catch (OutOfStepsException e) {
			errPrintln("Your solution to scram ran out of steps before returning!");
			gui.ifPresent((g) -> g
				.displayError("Your solution to scram ran out of steps before returning!"));
		} catch (Throwable t) {
			if (t instanceof ThreadDeath) return;
			errPrintln("Your code errored during the scram phase.");
			gui.ifPresent((g) -> g.displayError(
				"Your code errored during the scram phase. Please see console output."));
			t.printStackTrace();
			scramErrored= true;
		}

		outPrintln("Gold collected   : " + getGoldCollected());
		DecimalFormat df= new DecimalFormat("#.##");
		outPrintln("Bonus multiplier : " + df.format(computeBonusFactor()));
		outPrintln("Score            : " + getScore());
	}

	/** Making sure the hunter always has the minimum steps needed to scram, <br>
	 * add a factor of extra steps proportional to the size of the cavern. */
	private int computeStepsToScram() {
		int minScamSteps= scramCavern.minPathLengthToTarget(position);
		return (int) (minScamSteps + EXTRA_STEPS_FACTOR *
			(Cavern.MAX_EDGE_WEIGHT + 1) * scramCavern.numOpenTiles() / 2);

	}

	/** Compare the hunter's performance on the hunt() stage to the <br>
	 * theoretical minimum, compute their bonus factor on a call from MIN_BONUS to MAX_BONUS. <br>
	 * Bonus should be minimum if take longer than NO_BONUS_LENGTH times optimal. */
	private double computeBonusFactor() {
		double huntDiff= (stepsTaken - minStepsToHunt) / (double) minStepsToHunt;
		if (huntDiff <= 0) return MAX_BONUS;
		double multDiff= MAX_BONUS - MIN_BONUS;
		return Math.max(MIN_BONUS, MAX_BONUS - huntDiff / NO_BONUS_LENGTH * multDiff);
	}

	/** See moveTo(Node&lt;TileData&gt; n)
	 *
	 * @param id The Id of the neighboring Node to move to */
	@Override
	public void moveTo(long id) {
		if (stage != Stage.HUNT) {
			throw new IllegalStateException("moveTo(ID) can only be called while exploring!");
		}

		for (Node n : position.getNeighbors()) {
			if (n.getId() == id) {
				position= n;
				stepsTaken++ ;
				gui.ifPresent((g) -> g.updateBonus(computeBonusFactor()));
				gui.ifPresent((g) -> g.moveTo(n));
				return;
			}
		}
		throw new IllegalArgumentException("moveTo: Node must be adjacent to position");
	}

	/** Return the unique id of the current location. */
	@Override
	public long currentLocation() {
		if (stage != Stage.HUNT) {
			throw new IllegalStateException("getLocation() can only be called while exploring!");
		}

		return position.getId();
	}

	/** Return a collection of NodeStatus objects that contain the <br>
	 * unique ID of the node and the distance from that node to the target. */
	@Override
	public Collection<NodeStatus> neighbors() {
		if (stage != Stage.HUNT) {
			throw new IllegalStateException("getNeighbors() can only be called while exploring!");
		}

		Collection<NodeStatus> options= new ArrayList<>();
		for (Node n : position.getNeighbors()) {
			int distance= computeDistanceToTarget(n.getTile().getRow(), n.getTile().getColumn());
			options.add(new NodeStatus(n.getId(), distance));
		}
		return options;
	}

	/** Return the Manhattan distance from (row, col) to the target */
	private int computeDistanceToTarget(int row, int col) {
		return Math.abs(row - huntCavern.getTarget().getTile().getRow()) +
			Math.abs(col - huntCavern.getTarget().getTile().getColumn());
	}

	/** Return the Manhattan distance from the current location to the <br>
	 * target location on the map. */
	@Override
	public int distanceToOrb() {
		if (stage != Stage.HUNT) {
			throw new IllegalStateException(
				"getDistanceToTarget() can only be called while exploring!");
		}

		return computeDistanceToTarget(position.getTile().getRow(), position.getTile().getColumn());
	}

	@Override
	public Node currentNode() {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException("getCurrentNode: Error, " +
				"current Node may not be accessed unless in SCRAM");
		}
		return position;
	}

	@Override
	public Node getExit() {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException("getEntrance: Error, " +
				"current Node may not be accessed unless in SCRAM");
		}
		return scramCavern.getTarget();
	}

	@Override
	public Collection<Node> allNodes() {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException("getVertices: Error, " +
				"Vertices may not be accessed unless in SCRAM");
		}
		return Collections.unmodifiableSet(scramCavern.getGraph());
	}

	/** Attempt to move the hunter from the current position to the Node n.<br>
	 * Throws an IllegalArgumentException if n is not neighboring. <br>
	 * Increment the steps taken if successful. */
	@Override
	public void moveTo(Node n) {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException("moveTo(Node) can only be called when scramming!");
		}
		int distance= position.getEdge(n).length;
		if (stepsRemaining - distance < 0) { throw new OutOfStepsException(); }

		if (position.getNeighbors().contains(n)) {
			position= n;
			stepsRemaining-= distance;
			gui.ifPresent((g) -> g.updateStepsLeft(stepsRemaining));
			gui.ifPresent((g) -> g.moveTo(n));
			if (position.getTile().gold() > 0) grabGold();
		} else {
			throw new IllegalArgumentException("moveTo: Node must be adjacent to position");
		}
	}

	@Override
	public void grabGold() {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException("pickUpGold() can only be called while scramming!");
		} else if (position.getTile().gold() <= 0) {
			throw new IllegalStateException("pickUpGold: Error, no gold on this tile");
		}
		goldCollected+= position.getTile().takeGold();
		gui.ifPresent((g) -> g.updateCoins(goldCollected, getScore()));
	}

	@Override
	public int stepsLeft() {
		if (stage != Stage.SCRAM) {
			throw new IllegalStateException(
				"getStepsRemaining() can be called only while scramming!");
		}
		return stepsRemaining;
	}

	/* package */ int getGoldCollected() {
		return goldCollected;
	}

	/** Return the player's current score. */
	/* package */ int getScore() {
		return (int) (computeBonusFactor() * goldCollected);
	}

	/* package */ boolean getHuntSucceeded() {
		return huntSucceeded;
	}

	/* package */ boolean getScramSucceeded() {
		return scramSucceeded;
	}

	/* package */ boolean getHuntErrored() {
		return huntErrored;
	}

	/* package */ boolean getScramErrored() {
		return scramErrored;
	}

	/* package */ boolean getHuntTimeout() {
		return huntTimedOut;
	}

	/* package */ boolean getScramTimeout() {
		return scramTimedOut;
	}

	/* package */ int getMinHuntDistance() {
		return minHuntDistance;
	}

	/* package */ int getMinScramDistance() {
		return minScramDistance;
	}

	/* package */ int getHuntStepsLeft() {
		return huntStepsLeft;
	}

	/* package */ int getScramStepsLeft() {
		return scramStepsLeft;
	}

	/** Given seed, whether or not to use the GUI, and an instance <br>
	 * of a solution to use, run the game. */
	public static int runNewGame(long seed, boolean useGui, Hunter solution) {
		GameState state;
		if (seed != 0) {
			state= new GameState(seed, useGui, solution);
		} else {
			state= new GameState(useGui, solution);
		}
		outPrintln("Seed : " + state.seed);
		state.run();
		return state.getScore();
	}

	/** Run program in headless mode. args are explained elsewhere. */
	public static void main(String[] args) throws IOException {
		List<String> argList= new ArrayList<>(Arrays.asList(args));
		int repeatNumberIndex= argList.indexOf("-n");
		int numTimesToRun= 1;
		if (repeatNumberIndex >= 0) {
			try {
				numTimesToRun= Math.max(Integer.parseInt(argList.get(repeatNumberIndex + 1)), 1);
			} catch (Exception e) {
				// numTimesToRun = 1
			}
		}
		int seedIndex= argList.indexOf("-s");
		long seed= 0;
		if (seedIndex >= 0) {
			try {
				seed= Long.parseLong(argList.get(seedIndex + 1));
			} catch (NumberFormatException e) {
				errPrintln("Error, -s must be followed by a numerical seed");
				return;
			} catch (ArrayIndexOutOfBoundsException e) {
				errPrintln("Error, -s must be followed by a seed");
				return;
			}
		}

		int totalScore= 0;
		for (int i= 0; i < numTimesToRun; i++ ) {
			totalScore+= runNewGame(seed, false, new Pollack());
			if (seed != 0) seed= new Random(seed).nextLong();
			outPrintln("");
		}

		outPrintln("Average score : " + totalScore / numTimesToRun);
	}

	static void outPrintln(String s) {
		if (shouldPrint) System.out.println(s);
	}

	static void errPrintln(String s) {
		if (shouldPrint) System.err.println(s);
	}
}
