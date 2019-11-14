package ai.libs.jaicore.search.algorithms.standard.mcts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.api4.java.ai.graphsearch.problem.IGraphSearchWithPathEvaluationsInput;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.NodeGoalTester;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.PathGoalTester;
import org.api4.java.algorithm.events.AlgorithmEvent;
import org.api4.java.algorithm.exceptions.AlgorithmException;
import org.api4.java.algorithm.exceptions.AlgorithmExecutionCanceledException;
import org.api4.java.algorithm.exceptions.AlgorithmTimeoutedException;
import org.api4.java.common.attributedobjects.IObjectEvaluator;
import org.api4.java.common.attributedobjects.ObjectEvaluationFailedException;
import org.api4.java.common.control.ILoggingCustomizable;
import org.api4.java.datastructure.graph.IPath;
import org.api4.java.datastructure.graph.implicit.IGraphGenerator;
import org.api4.java.datastructure.graph.implicit.NodeExpansionDescription;
import org.api4.java.datastructure.graph.implicit.RootGenerator;
import org.api4.java.datastructure.graph.implicit.SingleRootGenerator;
import org.api4.java.datastructure.graph.implicit.SuccessorGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

import ai.libs.jaicore.basic.IRandomizable;
import ai.libs.jaicore.basic.algorithm.EAlgorithmState;
import ai.libs.jaicore.basic.events.IEventEmitter;
import ai.libs.jaicore.basic.sets.SetUtil;
import ai.libs.jaicore.graph.LabeledGraph;
import ai.libs.jaicore.graphvisualizer.events.graph.GraphInitializedEvent;
import ai.libs.jaicore.graphvisualizer.events.graph.NodeTypeSwitchEvent;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.events.RolloutEvent;
import ai.libs.jaicore.search.core.interfaces.AOptimalPathInORGraphSearch;
import ai.libs.jaicore.search.core.interfaces.LazySuccessorGenerator;
import ai.libs.jaicore.search.model.other.EvaluatedSearchGraphPath;
import ai.libs.jaicore.search.model.other.SearchGraphPath;

/**
 * MCTS algorithm implementation.
 *
 * This implementation follows the description in Browne, Cb AND Powley, Edward - A survey of monte carlo tree search methods (2012)
 *
 * @author Felix Mohr
 */
public class MCTSPathSearch<I extends IGraphSearchWithPathEvaluationsInput<N, A, V>, N, A, V extends Comparable<V>> extends AOptimalPathInORGraphSearch<I, N, A, V> implements IPolicy<N, A, V> {

	private static final String NODESTATE_ROLLOUT = "or_rollout";

	private Logger logger = LoggerFactory.getLogger(MCTSPathSearch.class);
	private String loggerName;

	/* graph structure */
	protected final IGraphGenerator<N, A> graphGenerator;
	protected final RootGenerator<N> rootGenerator;
	protected final SuccessorGenerator<N, A> successorGenerator;
	protected final PathGoalTester<N, A> goalTester;
	protected final boolean useLazySuccessorGeneration;
	protected final Map<N, LazySuccessorGenerator<N, A>> lazySuccessorGenerators;

	protected final IPathUpdatablePolicy<N, A, V> treePolicy;
	protected final IPolicy<N, A, V> defaultPolicy;
	protected final IObjectEvaluator<IPath<N, A>, V> playoutSimulator;
	private final Map<IPath<N, A>, V> scoreCache = new HashMap<>();

	private final N root;
	// private final Collection<N> nodesExplicitlyAdded = new HashSet<>();
	private final Collection<N> visitedNodes = new HashSet<>();
	private final Collection<N> unexpandedNodes = new HashSet<>();
	protected final LabeledGraph<N, A> exploredGraph;
	private final Collection<N> fullyExploredNodes = new HashSet<>(); // set of nodes under which the tree is completely known
	//	private final Collection<N> deadLeafNodes = new HashSet<>();
	private final V penaltyForFailedEvaluation;

	private int numberOfPlayouts = 0;
	private IPath<N, A> enforcedPrefixPath = null;
	private boolean treePolicyReachedLeafs = false;
	private final Random random;
	private boolean logDoublePathsAsWarnings = false;

	public MCTSPathSearch(final I problem, final IPathUpdatablePolicy<N, A, V> treePolicy, final IPolicy<N, A, V> defaultPolicy, final V penaltyForFailedEvaluation) {
		super(problem);
		this.graphGenerator = problem.getGraphGenerator();
		this.rootGenerator = this.graphGenerator.getRootGenerator();
		this.successorGenerator = this.graphGenerator.getSuccessorGenerator();
		PathGoalTester<N, A> tmpGoalTester = problem.getGoalTester();
		if (!(tmpGoalTester instanceof NodeGoalTester)) {
			throw new IllegalArgumentException("MCTS must be run with a NodeGoalEvaluator!");
		}
		this.goalTester = tmpGoalTester;

		this.random = ((IRandomizable)defaultPolicy).getRandom();
		this.treePolicy = treePolicy;
		this.defaultPolicy = defaultPolicy;
		this.playoutSimulator = problem.getPathEvaluator();
		this.exploredGraph = new LabeledGraph<>();
		this.root = ((SingleRootGenerator<N>) this.rootGenerator).getRoot();
		this.unexpandedNodes.add(this.root);
		this.exploredGraph.addItem(this.root);
		this.penaltyForFailedEvaluation = penaltyForFailedEvaluation;

		/* configure lazy successor generators */
		this.useLazySuccessorGeneration = this.successorGenerator instanceof LazySuccessorGenerator;
		this.lazySuccessorGenerators = this.useLazySuccessorGeneration ? new HashMap<>() : null;

		/* configure the policies */
		if (treePolicy instanceof IGraphDependentPolicy) {
			((IGraphDependentPolicy) treePolicy).setGraph(this.exploredGraph);
		}

		if (treePolicy instanceof IEventEmitter) {
			((IEventEmitter) treePolicy).registerListener(new Object() {

				@Subscribe
				public void receiveEvent(final AlgorithmEvent e) {
					MCTSPathSearch.this.post(e); // forward the event
				}
			});
		}
	}

	/**
	 * This method produces a playout path in three phases. Starting with root, it always chooses one of the children of the currently considered node as the next current node.
	 *
	 * 1. selection: if all successors of the current node have been visited, use the tree policy to choose the next node 2. expansion: one (new) child node is added to expand the tree (here, this means to mark one of the successors as
	 * visited) 3. simulation: draw a path completion from the node added in step 2
	 *
	 * Note that we have two stages of node expansion in this algorithm. In a first step, node successors are always computed in a block, i.e. we compute all successors at once and attach them to the <code>exploredGraph</code> variable.
	 * However, the algorithm ignores such generated nodes until they become explicitly "generated" in step 2, which means that they are added to the <code>nodesExplicitlyAdded</code> variable.
	 *
	 * If this method hits a dead-end, it will draw a new playout automatically.
	 *
	 * @return
	 * @throws InterruptedException
	 * @throws AlgorithmExecutionCanceledException
	 * @throws TimeoutException
	 * @throws AlgorithmException
	 * @throws ActionPredictionFailedException
	 */
	private IPath<N, A> getPlayout() throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmTimeoutedException, AlgorithmException, ActionPredictionFailedException {
		long startPlayout = System.currentTimeMillis();
		this.logger.debug("Computing a new playout ...");
		this.numberOfPlayouts++;
		N current = this.root;
		List<N> pathNodes = new ArrayList<>();
		List<A> pathActions = new ArrayList<>();

		/* attach enforced prefix path if set */
		if (this.enforcedPrefixPath != null) {
			assert this.enforcedPrefixPath.getRoot().equals(this.root);
			for (N node : this.enforcedPrefixPath.getNodes()) {
				pathNodes.add(node);
				current = node;
			}
			for (A arc : this.enforcedPrefixPath.getArcs()) {
				pathActions.add(arc);
			}
			assert this.exploredGraph.hasPath(this.enforcedPrefixPath.getNodes());
			assert this.unexpandedNodes.contains(this.enforcedPrefixPath.getHead()) || !this.exploredGraph.getSuccessors(this.enforcedPrefixPath.getHead()).isEmpty();
			assert this.enforcedPrefixPath.getPathToParentOfHead().getNodes().stream().noneMatch(this.unexpandedNodes::contains);
			assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";
		} else {
			pathNodes.add(current);
		}
		IPath<N, A> path = new SearchGraphPath<>(pathNodes, pathActions);

		N next;
		Collection<N> childrenOfCurrent = this.unexpandedNodes.contains(current) ? null : this.exploredGraph.getSuccessors(current);

		/* the current path must be in the explored graph */
		assert this.exploredGraph.hasPath(pathNodes) : "The current path is not in the explored graph: " + pathNodes.stream().map(n -> "\n\t" + n).collect(Collectors.joining());
		assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";

		/* Step 1 (Selection): chooses next node with tree policy until a node is reached that has at least one node that has not been part of any playout */
		this.logger.debug("Step 1: Using tree policy to identify new path to not fully expanded node.");
		int level = 0;
		while (childrenOfCurrent != null && SetUtil.differenceEmpty(childrenOfCurrent, this.visitedNodes)) {
			assert this.exploredGraph.hasPath(pathNodes);
			assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";

			this.logger.debug("Step 1 (level {}): choose one of the {} successors {} of current node {}", level, childrenOfCurrent.size(), childrenOfCurrent, current);

			/* determine all actions applicable in this node that are not known to lead into a dead-end */
			this.checkAndConductTermination();
			List<A> availableActions = new ArrayList<>();
			Map<A, N> successorStates = new HashMap<>();
			for (N child : childrenOfCurrent) {
				//				if (this.deadLeafNodes.contains(child)) {
				//					this.logger.trace("Ignoring child {}, which is known to be a dead end", child);
				//					continue;
				//				} else
				if (this.fullyExploredNodes.contains(child)) {
					this.logger.trace("Ignoring child {}, which has been fully explored.", child);
					continue;
				}
				A action = this.exploredGraph.getEdgeLabel(current, child);
				if (action == null) {
					throw new IllegalStateException("MCTS does not accept NULL edge labels!");
				}
				assert this.exploredGraph.getItems().contains(child);
				assert !successorStates.containsKey(action) : "A successor state has already been defined for action \"" + action + "\" with hashCode " + action.hashCode();
				if (availableActions.contains(action)) {
					throw new IllegalStateException("Action " + action + " equals another action inside the already created actions: " + availableActions);
				}
				availableActions.add(action);
				successorStates.put(action, child);
				assert successorStates.keySet().size() == availableActions.size() : "We have generated " + availableActions.size() + " available actions but the map of successor states only contains " + successorStates.keySet().size()
						+ " item(s). Actions (by hash codes): \n\t" + availableActions.stream().map(a -> a.hashCode() + ": " + a.toString()).collect(Collectors.joining("\n\t"));
			}

			/* if every applicable action is known to yield a dead-end, mark this node to be a dead-end itself and return */
			if (availableActions.isEmpty()) {
				this.logger.debug("Node {} has only dead-end successors and hence is a dead-end itself. Adding it to the list of dead ends.", current);
				if (current == this.exploredGraph.getRoot()) {
					this.logger.info("No more action available in root node. Throwing exception.");
					throw new NoSuchElementException();
				}
				//				this.deadLeafNodes.add(current);
				return this.getPlayout();
			}

			/* choose the next action and determine the subsequent node. Also compute the children of the current. If it has not been expanded yet, set the list of successors to NULL, which will interrupt the loop */
			if (availableActions.size() != successorStates.size()) {
				throw new IllegalStateException("Number of available actions and successor states does not match! " + availableActions.size() + " available actions but " + successorStates.size() + " successor states. Actions: " + availableActions.stream().map(a -> "\n\t\t" + a + (a != null ? a.hashCode() : "")).collect(Collectors.joining()));
			}
			this.logger.debug("Now consulting tree policy. We have {} available actions of expanded node {}: {}. Corresponding {} successor states: {}", availableActions.size(), current, availableActions, successorStates.size(), successorStates);
			A chosenAction = this.treePolicy.getAction(current, successorStates);
			if (chosenAction == null) {
				throw new IllegalStateException("Chosen action must not be null!");
			}
			next = successorStates.get(chosenAction);
			if (next == null) {
				throw new IllegalStateException("Next action must not be null!");
			}
			this.logger.debug("Tree policy decides to expand {} taking action {} to {}", current, chosenAction, next);
			current = next;
			childrenOfCurrent = this.unexpandedNodes.contains(current) ? null : this.exploredGraph.getSuccessors(current);
			pathNodes.add(current);
			pathActions.add(chosenAction);

			/* if the current path is a goal path, return it */
			if (this.goalTester.isGoal(path)) {
				this.logger.debug("Constructed complete solution with tree policy within {}ms.", System.currentTimeMillis() - startPlayout);
				this.treePolicyReachedLeafs = true;
				this.propagateFullyKnownNodes(path.getHead());
				return new SearchGraphPath<>(pathNodes, pathActions);
			}
			this.post(new NodeTypeSwitchEvent<N>(this.getId(), next, NODESTATE_ROLLOUT));
			level++;
		}

		/* check current pointer */
		assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";

		/* the current path must be in the explored graph */
		assert this.exploredGraph.hasPath(pathNodes) : "The current path is not in the explored graph: " + pathNodes.stream().map(n -> "\n\t" + n).collect(Collectors.joining());

		/* children of current must either not have been generated or not be empty. This assertion should already be covered by the subsequent assertion, but it is still here for robustness reasons */
		assert childrenOfCurrent == null || !childrenOfCurrent.isEmpty() : "Set of children of current node must not be empty!";

		/* if the current node is not a leaf (of the traversal tree, i.e. has no children generated yet), it must have untried successors */
		assert childrenOfCurrent == null || SetUtil.differenceNotEmpty(childrenOfCurrent, this.visitedNodes) : "The current node has " + childrenOfCurrent.size()
		+ " successors and all of them have been considered in at least one playout. In spite of this, the tree policy has not been used to choose a child, but it should have been used.";

		/* if the current node has at least one child, all child nodes must have been marked as dead ends */
		//		assert childrenOfCurrent == null || SetUtil.differenceNotEmpty(childrenOfCurrent, this.deadLeafNodes) : "Flag that current node is dead end is set, but there are successors that are not yet marked as dead-ends.";
		this.logger.debug("Determined non-fully-expanded node {} of traversal tree using tree policy. Untried successors are: {}. Now selecting an untried successor.", current,
				childrenOfCurrent != null ? SetUtil.difference(childrenOfCurrent, this.visitedNodes) : "<not generated>");

		/* Step 2 (Expansion): Use default policy to select one of the unvisited successors of the current node. If necessary, generate the successors first. */
		this.checkAndConductTermination();
		assert this.exploredGraph.getSources().size() == 1;
		assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";

		/* determine the unvisited child nodes of this node */
		Map<A, N> untriedActionsAndTheirSuccessors = new HashMap<>();
		if (this.unexpandedNodes.contains(current)) {
			this.logger.trace("Step 2: This is the first time we visit this node, so compute its successors and add them to explicit graph model.");
			untriedActionsAndTheirSuccessors.putAll(this.expandNode(current, true));
			if (untriedActionsAndTheirSuccessors.isEmpty()) {
				System.out.println("FOUND LEAF");
			}
		} else {
			for (N child : SetUtil.difference(childrenOfCurrent, this.visitedNodes)) {
				A action = this.exploredGraph.getEdgeLabel(current, child);
				untriedActionsAndTheirSuccessors.put(action, child);
			}
		}
		assert this.exploredGraph.getSources().size() == 1;
		assert current == pathNodes.get(pathNodes.size() - 1) : "Current does not coincide with the head of the current path!";

		/* choose the node expansion with the default policy */
		this.logger.debug("Step 2: Using default policy to choose one of the {} untried actions {} of current node {}", untriedActionsAndTheirSuccessors.size(), untriedActionsAndTheirSuccessors.keySet(), current);
		if (!untriedActionsAndTheirSuccessors.isEmpty()) {
			this.logger.trace("Asking default policy for action to take in node {}", current);
			A chosenAction = this.defaultPolicy.getAction(current, untriedActionsAndTheirSuccessors);
			current = untriedActionsAndTheirSuccessors.get(chosenAction);
			assert this.unexpandedNodes.contains(current);
			assert this.exploredGraph.hasItem(current);
			this.visitedNodes.add(current);
			this.post(new NodeTypeSwitchEvent<N>(this.getId(), current, NODESTATE_ROLLOUT));
			assert this.exploredGraph.hasEdge(pathNodes.get(pathNodes.size() - 1), current) : "Exploration graph does not have the edge " + pathNodes.get(pathNodes.size() - 1) + " -> " + current + ".\nSuccessors of first are: "
			+ this.exploredGraph.getSuccessors(pathNodes.get(pathNodes.size() - 1)).stream().map(n -> "\n\t\t" + n).collect(Collectors.joining()) + ".\nPredecessors of second are: "
			+ this.exploredGraph.getPredecessors(current).stream().map(n -> "\n\t\t" + n).collect(Collectors.joining());
			pathNodes.add(current);
			pathActions.add(chosenAction);
			assert this.exploredGraph.hasPath(pathNodes) : "The current path is not in the explored graph after having added the latest edge: " + pathNodes.stream().map(n -> "\n\t" + n).collect(Collectors.joining());
			this.logger.debug("Selected {} as the untried action with successor state {}. Now completing rest playout from this situation.", chosenAction, current);
		} else {
			//			this.deadLeafNodes.add(current);
			this.logger.debug("Found leaf node {}. Adding to dead end list.", current);
			return this.getPlayout();
		}

		/* Step 3 (Simulation): use default policy to proceed to a goal node. Nodes won't be added to model variable here.
		 * The invariant of the following loop is that the current node is always the last unexpanded node, and the path variable contains all nodes from root to the current one (including it).
		 */
		this.logger.debug("Step 3: Using default policy to create full path under {}.", current);
		while (!this.goalTester.isGoal(path)) {
			// assert this.exploredGraph.hasPath(pathNodes) : "The current path is not in the explored graph: " + pathNodes.stream().map(n -> "\n\t\t" + n).collect(Collectors.joining()) + "\nThe missing edge is the one leaving from: \n\t\t"
			// + pathNodes.stream().filter(n -> !this.exploredGraph.hasEdge(n, pathNodes.get(pathNodes.indexOf(n) + 1))).findFirst().get();
			// assert this.unexpandedNodes.contains(current);
			this.checkAndConductTermination();

			//			Map<A, N> actionsAndTheirSuccessorStates = new HashMap<>();
			//			this.logger.trace("Determining possible moves for {}.", current);
			//
			//			actionsAndTheirSuccessorStates.putAll(this.expandNode(current, false));
			//
			//			/* if the default policy has led us into a state where we cannot do anything, stop playout */
			//			if (actionsAndTheirSuccessorStates.isEmpty()) {
			//				// this.deadLeafNodes.add(current);
			//				// this.propagateFullyKnownNodes(current);
			//				return new SearchGraphPath<>(pathNodes, pathActions);
			//			}

			/* instead of using the default policy, we only generate one successor right away (which SHOULD be random but is not here) */
			try {
				NodeExpansionDescription<N, A> ed;
				if (this.successorGenerator instanceof LazySuccessorGenerator) {
					ed = ((LazySuccessorGenerator<N,A>)this.successorGenerator).getRandomSuccessor(current, this.random);
				}
				else {
					ed = SetUtil.getRandomElement(this.successorGenerator.generateSuccessors(current), this.random.nextLong());
				}
				A chosenAction = ed.getAction();
				current = ed.getTo();

				if (!this.goalTester.isGoal(path)) {
					this.post(new NodeTypeSwitchEvent<>(this.getId(), current, NODESTATE_ROLLOUT));
				}
				pathNodes.add(current);
				pathActions.add(chosenAction);
				this.logger.debug("Step 3: Default policy chose action {} with successor node {}", chosenAction, current);
			}
			catch (NoSuchElementException e) {
				System.err.println("Node " + current + " does not have any successors even though it is not a goal!");
				System.exit(1);
			}
		}
		this.checkThatPathIsSolution(path);
		this.logger.debug("Drawn playout path after {}ms is: {}.", System.currentTimeMillis() - startPlayout, path);
		// this.propagateFullyKnownNodes(current);
		return path;
	}

	private void closePath(final IPath<N, A> path) {
		//		int n = path.getNumberOfNodes();
		//		for (int i = n - 2; i > 0; i--) { // don't color the root and the leaf!
		//			N node = path.getNodes().get(i);
		//			//			this.post(new NodeTypeSwitchEvent<N>(this.getId(), node, this.fullyExploredNodes.contains(node) ? "or_exhausted" : "or_closed"));
		//		}
	}

	/**
	 * This method does NOT change the state of nodes via an event since it is assumed that closePath will be called later and assumes this task.
	 *
	 * @param node
	 */
	private void propagateFullyKnownNodes(final N node) {
		if (this.fullyExploredNodes.containsAll(this.exploredGraph.getSuccessors(node))) {
			this.fullyExploredNodes.add(node);
			assert this.exploredGraph.getPredecessors(node).size() <= 1;
			if (this.exploredGraph.getPredecessors(node).isEmpty()) {
				return;
			}
			this.propagateFullyKnownNodes(this.exploredGraph.getPredecessors(node).iterator().next());
		}
	}

	/**
	 * This creates all successors of a node AND adds them to the exploration graph.
	 *
	 * @param node
	 * @return
	 * @throws InterruptedException
	 * @throws AlgorithmExecutionCanceledException
	 * @throws AlgorithmTimeoutedException
	 * @throws AlgorithmException
	 */
	private Map<A, N> expandNode(final N node, final boolean attachChildrenToExploredGraph) throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmTimeoutedException, AlgorithmException {
		long startExpansion = System.currentTimeMillis();
		this.logger.trace("Starting expansion of node {}", node);
		this.checkAndConductTermination();
		if (attachChildrenToExploredGraph) {
			if (!this.unexpandedNodes.contains(node)) {
				throw new IllegalArgumentException();
			}
			this.logger.trace("Situation {} has never been analyzed before, expanding the graph at the respective point.", node);
			this.unexpandedNodes.remove(node);
		}
		Collection<NodeExpansionDescription<N, A>> availableActions;
		try {
			availableActions = this.computeTimeoutAware(() -> this.successorGenerator.generateSuccessors(node), "Successor generation", true);
		} catch (ExecutionException e) {
			throw new AlgorithmException("Could not compute available actions.", e.getCause());
		}
		long successorGenerationRuntime = System.currentTimeMillis() - startExpansion;
		if (successorGenerationRuntime > 5) {
			this.logger.warn("The successor generation runtime was {}ms for {} nodes.", successorGenerationRuntime, availableActions.size());
		}

		assert availableActions.stream().map(NodeExpansionDescription::getAction).collect(Collectors.toList()).size() == availableActions.stream().map(NodeExpansionDescription::getAction).collect(Collectors.toSet())
				.size() : "The actions under this node don't have unique names. Action names: \n\t" + availableActions.stream().map(d -> d.getAction().toString()).collect(Collectors.joining("\n\t"));
				Map<A, N> successorStates = new HashMap<>();
				long startAttachment = System.currentTimeMillis();
				int i = 0;
				for (NodeExpansionDescription<N, A> d : availableActions) {
					N to = d.getTo();
					A action = d.getAction();
					if ((i++) % 10 == 0) {
						this.checkAndConductTermination();
					}
					successorStates.put(action, to);
					this.logger.trace("Adding edge {} -> {} with label {}", node, to, action);
					if (attachChildrenToExploredGraph) {
						this.exploredGraph.addItem(to);
						this.unexpandedNodes.add(to);
						this.exploredGraph.addEdge(node, to, action);
					}
					// this.post(new NodeAddedEvent<>(this.getId(), node, to, this.isGoal(to) ? "or_solution" : "or_open"));
				}
				long expansionRuntime = System.currentTimeMillis() - startAttachment;
				if (expansionRuntime > 10) {
					this.logger.warn("The attachment runtime for {} nodes was {}ms.", availableActions.size(), expansionRuntime);
				}
				this.logger.debug("Node expansion finished. Returning {} successor states.", successorStates.size());
				return successorStates;
	}

	@Override
	public A getAction(final N node, final Map<A, N> actionsWithSuccessors) throws ActionPredictionFailedException {

		try {
			/* compute next solution */
			this.nextSolutionCandidate();

			/* choose action in root that has best reward */
			return this.treePolicy.getAction(this.root, actionsWithSuccessors);
		} catch (Exception e) {
			throw new ActionPredictionFailedException(e);
		}
	}

	private void checkThatPathIsSolution(final IPath<N, A> path) {
		N current = this.exploredGraph.getRoot();
		List<N> pathNodes = path.getNodes();
		assert current.equals(pathNodes.get(0)) : "The root of the path does not match the root of the graph!";
		for (int i = 1; i < pathNodes.size(); i++) {
			assert this.exploredGraph.getSuccessors(current).contains(pathNodes.get(i)) : "Invalid path. The " + i + "-th entry " + pathNodes.get(i) + " of the path " + path + " is not a successor of the " + (i - 1)
			+ "-th node whose successors are " + this.exploredGraph.getSuccessors(current) + "!";
			current = pathNodes.get(i);
		}
		assert !pathNodes.isEmpty() : "Solution paths cannot be empty!";
		assert this.goalTester.isGoal(path) : "The head of a solution path must be a goal node, but this is not the case for this path: \n\t" + pathNodes.stream().map(N::toString).collect(Collectors.joining("\n\t"));
	}

	@Override
	public AlgorithmEvent nextWithException() throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmException, AlgorithmTimeoutedException {
		switch (this.getState()) {
		case CREATED:
			this.post(new GraphInitializedEvent<N>(this.getId(), this.root));
			this.logger.info("Starting MCTS with node class {}", this.root.getClass().getName());
			return this.activate();

		case ACTIVE:
			if (this.playoutSimulator == null) {
				throw new IllegalStateException("no simulator has been set!");
			}
			this.logger.debug("Next algorithm iteration. Number of unexpanded nodes: {}", this.unexpandedNodes.size());
			try {
				this.registerActiveThread();
				while (this.getState() == EAlgorithmState.ACTIVE) {
					this.checkAndConductTermination();

					/* if the whole graph has been expanded and paths must not be visited more than once, terminate */
					if (this.unexpandedNodes.isEmpty()) {
						AlgorithmEvent finishEvent = this.terminate();
						this.logger.info("Finishing MCTS as all nodes have been expanded; the search graph has been exhausted.");
						return finishEvent;
					}

					/* if the tree policy has touched a leaf node, quit */
					else if (this.fullyExploredNodes.contains(this.root)) {
						AlgorithmEvent finishEvent = this.terminate();
						this.logger.info("Finishing MCTS as all nodes have been expanded; the search graph has been exhausted.");
						return finishEvent;
					}

					/* otherwise, compute a playout and, if the path is a solution, compute its score and update the path */
					else {
						this.logger.debug("There are {} known unexpanded nodes. Starting computation of next playout path.", this.unexpandedNodes.size());
						IPath<N, A> path = this.getPlayout();
						IPath<N, A> subPathToLatestRegistered = this.getSubPathUpToDeepestRegisteredNode(path); // we back-propagate the observations only to nodes contained in the current model for the tree-policy
						assert path != null : "The playout must never be null!";
						assert this.exploredGraph.hasPath(path.getNodes()) : "Invalid playout, path not contained in explored graph!";
						if (!this.scoreCache.containsKey(path)) {
							this.logger.debug("Obtained path {}. Now starting computation of the score for this playout.", path);
							try {
								V playoutScore = this.playoutSimulator.evaluate(path);
								boolean isSolutionPlayout = this.goalTester.isGoal(path);
								this.logger.info("Determined playout score {}. Is goal: {}. Now updating the path of tree policy {}.", playoutScore, isSolutionPlayout, this.treePolicy);
								this.scoreCache.put(path, playoutScore);
								this.post(new RolloutEvent<>(this.getId(), path.getNodes(), this.scoreCache.get(path)));
								this.treePolicy.updatePath(subPathToLatestRegistered, playoutScore);
								if (isSolutionPlayout) {
									return this.registerSolution(new EvaluatedSearchGraphPath<>(path.getNodes(), path.getArcs(), playoutScore));
								}
							} catch (InterruptedException e) { // don't forward this directly since this could come indirectly through a cancel. Rather invoke checkTermination
								Thread.interrupted(); // reset interrupt field
								this.checkAndConductTermination();
								throw e; // if we get here (no exception for timeout or cancel has been thrown in check), we really have been interrupted
							} catch (ObjectEvaluationFailedException e) {
								this.scoreCache.put(path, this.penaltyForFailedEvaluation);
								this.post(new RolloutEvent<>(this.getId(), path.getNodes(), this.scoreCache.get(path)));
								this.post(new NodeTypeSwitchEvent<>(this.getId(), path.getHead(), "or_ffail"));
								this.treePolicy.updatePath(subPathToLatestRegistered, this.penaltyForFailedEvaluation);
								this.logger.warn("Could not evaluate playout {}", e);
							} finally {
								this.closePath(path); // visualize that path rollout has been completed
							}
						} else {
							//							if (this.forbidDoublePaths) {
							//								throw new IllegalStateException("Second time path " + path.getArcs() + " has been generated even though double paths are forbidden!");
							//							}
							V playoutScore = this.scoreCache.get(path);
							String logMessage = "Drawn path {} a repeated time. Looking up the score in the cache {}. This should occur rarely, or the graph is so small that MCTS does not make sense.";
							if (this.logDoublePathsAsWarnings) {
								this.logger.warn(logMessage, path, playoutScore);
							}
							else {
								this.logger.debug(logMessage, path, playoutScore);
							}
							this.treePolicy.updatePath(subPathToLatestRegistered, playoutScore);
							this.closePath(path); // visualize that path rollout has been completed
							this.post(new RolloutEvent<>(this.getId(), path.getNodes(), this.scoreCache.get(path)));
						}
					}

				}
			} catch (NoSuchElementException e) {
				this.logger.info("No more playouts exist. Terminating.");
				return this.terminate();
			} catch (ActionPredictionFailedException e) {
				throw new AlgorithmException("Step failed due to an exception in predicting an action when computing the playout.", e);
			} finally {
				this.unregisterActiveThread();
			}

			/* the algorithm should never come here */
			throw new IllegalStateException("The algorithm has reached the end of the active-block, which shall never happen.");

		default:
			throw new UnsupportedOperationException("Cannot do anything in state " + this.getState());
		}
	}

	public IPath<N, A> getSubPathUpToDeepestRegisteredNode(final IPath<N, A> path) {
		SearchGraphPath<N, A> subPath = new SearchGraphPath<>(path);
		while (!this.exploredGraph.hasItem(subPath.getHead())) {
			subPath = subPath.getPathToParentOfHead();
		}
		return subPath;
	}

	@Override
	public String getLoggerName() {
		return this.loggerName;
	}

	@Override
	public void setLoggerName(final String name) {
		this.logger.info("Switching logger from {} to {}", this.logger.getName(), name);
		this.loggerName = name;
		this.logger = LoggerFactory.getLogger(name);
		this.logger.info("Activated logger {} with name {}", name, this.logger.getName());
		super.setLoggerName(this.loggerName + "._orgraphsearch");

		/* set logger of graph generator */
		if (this.graphGenerator instanceof ILoggingCustomizable) {
			this.logger.info("Setting logger of graph generator to {}.graphgenerator", name);
			((ILoggingCustomizable) this.graphGenerator).setLoggerName(name + ".graphgenerator");
		} else {
			this.logger.info("Not setting logger of graph generator");
		}

		/* set logger of tree policy */
		if (this.treePolicy instanceof ILoggingCustomizable) {
			this.logger.info("Setting logger of tree policy to {}.treepolicy", name);
			((ILoggingCustomizable) this.treePolicy).setLoggerName(name + ".treepolicy");
		} else {
			this.logger.info("Not setting logger of tree policy, because {} is not customizable.", this.treePolicy.getClass().getName());
		}

		/* set logger of default policy */
		if (this.defaultPolicy instanceof ILoggingCustomizable) {
			this.logger.info("Setting logger of default policy to {}.defaultpolicy", name);
			((ILoggingCustomizable) this.defaultPolicy).setLoggerName(name + ".defaultpolicy");
		} else {
			this.logger.info("Not setting logger of default policy, because {} is not customizable.", this.defaultPolicy.getClass().getName());
		}
	}

	public int getNumberOfPlayouts() {
		return this.numberOfPlayouts;
	}

	public IPathUpdatablePolicy<N, A, V> getTreePolicy() {
		return this.treePolicy;
	}

	public IPolicy<N, A, V> getDefaultPolicy() {
		return this.defaultPolicy;
	}

	public void enforcePrefixPathOnAllRollouts(final IPath<N, A> path) {
		if (!path.getRoot().equals(this.root)) {
			throw new IllegalArgumentException("Illegal prefix, since root does not coincide with algorithm root. Proposed root is: " + path.getRoot());
		}
		this.enforcedPrefixPath = path;
		this.exploredGraph.addPath(path);
		N last = null;
		for (N node : path.getNodes()) {
			if (last != null) {
				this.unexpandedNodes.remove(last);
				this.unexpandedNodes.add(node);
			}
			last = node;
		}
	}

	public int getNumberOfNodesInMemory() {
		return this.exploredGraph.getItems().size();
	}
}