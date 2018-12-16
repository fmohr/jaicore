package jaicore.search.algorithms.standard.awastar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

import jaicore.basic.algorithm.AlgorithmEvent;
import jaicore.basic.algorithm.AlgorithmFinishedEvent;
import jaicore.basic.algorithm.AlgorithmInitializedEvent;
import jaicore.basic.algorithm.AlgorithmState;
import jaicore.graphvisualizer.events.graphEvents.GraphInitializedEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeReachedEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeTypeSwitchEvent;
import jaicore.search.algorithms.standard.AbstractORGraphSearch;
import jaicore.search.algorithms.standard.bestfirst.events.EvaluatedSearchSolutionCandidateFoundEvent;
import jaicore.search.algorithms.standard.bestfirst.events.GraphSearchSolutionCandidateFoundEvent;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.ICancelableNodeEvaluator;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.INodeEvaluator;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.ISolutionReportingNodeEvaluator;
import jaicore.search.core.interfaces.GraphGenerator;
import jaicore.search.model.other.EvaluatedSearchGraphPath;
import jaicore.search.model.probleminputs.GeneralEvaluatedTraversalTree;
import jaicore.search.model.travesaltree.Node;
import jaicore.search.model.travesaltree.NodeExpansionDescription;
import jaicore.search.structure.graphgenerator.GoalTester;
import jaicore.search.structure.graphgenerator.NodeGoalTester;
import jaicore.search.structure.graphgenerator.PathGoalTester;
import jaicore.search.structure.graphgenerator.SingleRootGenerator;
import jaicore.search.structure.graphgenerator.SuccessorGenerator;

/**
 * This is a modified version of the AWA* algorithm for problems without admissible heuristic. Important differences are: - no early termination if a best-f-valued solution is found as f is not optimistic
 *
 * @author lbrandt2 and fmohr
 *
 * @param <T>
 * @param <A>
 * @param <V>
 */
public class AwaStarSearch<I extends GeneralEvaluatedTraversalTree<T, A, V>, T, A, V extends Comparable<V>> extends AbstractORGraphSearch<I, EvaluatedSearchGraphPath<T, A, V>, T, A, V, Node<T, V>, A> {

	private Logger logger = LoggerFactory.getLogger(AwaStarSearch.class);
	private String loggerName;

	private final SingleRootGenerator<T> rootNodeGenerator;
	private final SuccessorGenerator<T, A> successorGenerator;
	private final GoalTester<T> goalTester;
	private final INodeEvaluator<T, V> nodeEvaluator;
	private final Queue<Node<T, V>> closedList, suspendList, openList;
	private int currentLevel = -1;
	private int windowSize;
	private final List<EvaluatedSearchGraphPath<T, A, V>> unconfirmedSolutions = new ArrayList<>(); // these are solutions emitted on the basis of the node evaluator but whose solutions have not been found in the original graph yet
	private final List<EvaluatedSearchSolutionCandidateFoundEvent<T, A, V>> unreturnedSolutionEvents = new ArrayList<>();

	public AwaStarSearch(final I problem) {
		super(problem);
		this.rootNodeGenerator = (SingleRootGenerator<T>) problem.getGraphGenerator().getRootGenerator();
		this.successorGenerator = problem.getGraphGenerator().getSuccessorGenerator();
		this.goalTester = problem.getGraphGenerator().getGoalTester();
		this.nodeEvaluator = problem.getNodeEvaluator();

		this.closedList = new PriorityQueue<>();
		this.suspendList = new PriorityQueue<>();
		this.openList = new PriorityQueue<>();
		this.windowSize = 0;
		if (this.nodeEvaluator instanceof ISolutionReportingNodeEvaluator) {
			((ISolutionReportingNodeEvaluator) this.nodeEvaluator).registerSolutionListener(this);
		}
	}

	private AlgorithmEvent processUntilNextEvent() throws Exception {

		this.logger.info("Searching for next solution.");

		/* return pending solutions if there are any */
		while (this.unreturnedSolutionEvents.isEmpty()) {

			/* check whether execution shoud be halted */
			this.checkTermination();

			/* if the current graph has been exhausted, add all suspended nodes to OPEN and increase window size */
			if (this.openList.isEmpty()) {
				if (this.suspendList.isEmpty()) {
					this.logger.info("The whole graph has been exhausted. No more solutions can be found!");
					return new AlgorithmFinishedEvent();
				} else {
					this.logger.info("Search with window size {} is exhausted. Reactivating {} suspended nodes and incrementing window size.", this.windowSize, this.suspendList.size());
					this.openList.addAll(this.suspendList);
					this.suspendList.clear();
					this.windowSize++;
					this.currentLevel = -1;
				}
			}
			this.logger.info("Running core algorithm with window size {} and current level {}. {} items are in OPEN", this.windowSize, this.currentLevel, this.openList.size());
			this.windowAStar();
		}
		EvaluatedSearchSolutionCandidateFoundEvent<T, A, V> toReturn = this.unreturnedSolutionEvents.get(0);
		this.unreturnedSolutionEvents.remove(0);
		return toReturn;
	}

	private void windowAStar() throws Exception {
		while (!this.openList.isEmpty()) {
			this.checkTermination();
			if (!this.unreturnedSolutionEvents.isEmpty()) {
				this.logger.info("Not doing anything because there are still unreturned solutions.");
				return;
			}
			Node<T, V> n = this.openList.peek();
			this.openList.remove(n);
			this.closedList.add(n);
			if (!n.isGoal()) {
				this.post(new NodeTypeSwitchEvent<>(n, "or_closed"));
			}

			/* check whether this node is outside the window and suspend it */
			int nLevel = n.externalPath().size() - 1;
			if (nLevel <= (this.currentLevel - this.windowSize)) {
				this.closedList.remove(n);
				this.suspendList.add(n);
				this.logger.info("Suspending node {} with level {}, which is lower than {}", n, nLevel, this.currentLevel - this.windowSize);
				this.post(new NodeTypeSwitchEvent<>(n, "or_suspended"));
				continue;
			}

			/* if the level should even be increased, do this now */
			if (nLevel > this.currentLevel) {
				this.logger.info("Switching level from {} to {}", this.currentLevel, nLevel);
				this.currentLevel = nLevel;
			}
			this.checkTermination();

			/* compute successors of the expanded node */
			Collection<NodeExpansionDescription<T, A>> successors = this.successorGenerator.generateSuccessors(n.getPoint());
			this.logger.info("Expanding {}. Identified {} successors.", n.getPoint(), successors.size());
			for (NodeExpansionDescription<T, A> expansionDescription : successors) {
				this.checkTermination();
				Node<T, V> nPrime = new Node<>(n, expansionDescription.getTo());
				if (this.goalTester instanceof NodeGoalTester<?>) {
					nPrime.setGoal(((NodeGoalTester<T>) this.goalTester).isGoal(nPrime.getPoint()));
				} else if (this.goalTester instanceof PathGoalTester<?>) {
					nPrime.setGoal(((PathGoalTester<T>) this.goalTester).isGoal(nPrime.externalPath()));
				}
				V nPrimeScore = this.nodeEvaluator.f(nPrime);

				/* ignore nodes whose value cannot be determined */
				if (nPrimeScore == null) {
					this.logger.debug("Discarding node {} for which no f-value could be computed.", nPrime);
					continue;
				}

				/* determine whether this is a goal node */
				if (nPrime.isGoal()) {
					List<T> newSolution = nPrime.externalPath();
					EvaluatedSearchGraphPath<T, A, V> solution = new EvaluatedSearchGraphPath<>(newSolution, null, nPrimeScore);
					this.registerNewSolutionCandidate(solution);
				}

				if (!this.openList.contains(nPrime) && !this.closedList.contains(nPrime) && !this.suspendList.contains(nPrime)) {
					nPrime.setParent(n);
					nPrime.setInternalLabel(nPrimeScore);
					if (!nPrime.isGoal()) {
						this.openList.add(nPrime);
					}
					this.post(new NodeReachedEvent<>(n, nPrime, nPrime.isGoal() ? "or_solution" : "or_open"));
				} else if (this.openList.contains(nPrime) || this.suspendList.contains(nPrime)) {
					V oldScore = nPrime.getInternalLabel();
					if (oldScore != null && oldScore.compareTo(nPrimeScore) > 0) {
						nPrime.setParent(n);
						nPrime.setInternalLabel(nPrimeScore);
					}
				} else if (this.closedList.contains(nPrime)) {
					V oldScore = nPrime.getInternalLabel();
					if (oldScore != null && oldScore.compareTo(nPrimeScore) > 0) {
						nPrime.setParent(n);
						nPrime.setInternalLabel(nPrimeScore);
					}
					if (!nPrime.isGoal()) {
						this.openList.add(nPrime);
					}
				}
			}
		}
	}

	@Subscribe
	public void receiveSolutionEvent(final EvaluatedSearchSolutionCandidateFoundEvent<T, A, V> solutionEvent) {
		this.registerNewSolutionCandidate(solutionEvent.getSolutionCandidate());
		this.unconfirmedSolutions.add(solutionEvent.getSolutionCandidate());
	}

	@SuppressWarnings("unchecked")
	public EvaluatedSearchSolutionCandidateFoundEvent<T, A, V> registerNewSolutionCandidate(final EvaluatedSearchGraphPath<T, A, V> solution) {
		EvaluatedSearchSolutionCandidateFoundEvent<T, A, V> event = this.registerSolution(solution);
		this.unreturnedSolutionEvents.add(event);
		return event;
	}

	@Override
	public AlgorithmEvent nextWithException() throws Exception {
		// logger.info("Next step in {}. State is {}", this, getState());
		this.checkTermination();
		switch (this.getState()) {
		case created: {
			this.activateTimeoutTimer("AWA*-Timeouter");
			T externalRootNode = this.rootNodeGenerator.getRoot();
			Node<T, V> rootNode = new Node<T, V>(null, externalRootNode);
			this.logger.info("Initializing graph and OPEN with {}.", rootNode);
			this.openList.add(rootNode);
			this.post(new GraphInitializedEvent<>(rootNode));
			rootNode.setInternalLabel(this.nodeEvaluator.f(rootNode));
			this.switchState(AlgorithmState.active);
			AlgorithmEvent e = new AlgorithmInitializedEvent();
			this.post(e);
			return e;
		}
		case active: {
			AlgorithmEvent event;
			try {
				event = this.processUntilNextEvent();
				if (event instanceof AlgorithmFinishedEvent) {
					super.unregisterThreadAndShutdown();
				}
			} catch (TimeoutException e) {
				super.unregisterThreadAndShutdown();
				event = new AlgorithmFinishedEvent();
			}
			if (!(event instanceof GraphSearchSolutionCandidateFoundEvent)) { // solution events are sent directly over the event bus
				this.post(event);
			}
			return event;
		}
		default:
			throw new IllegalStateException("Cannot do anything in state " + this.getState());
		}
	}

	@Override
	protected void shutdown() {

		if (this.isShutdownInitialized()) {
			return;
		}

		/* set state to inactive*/
		this.logger.info("Invoking shutdown routine ...");

		super.shutdown();

		/* cancel node evaluator */
		if (this.nodeEvaluator instanceof ICancelableNodeEvaluator) {
			this.logger.info("Canceling node evaluator.");
			((ICancelableNodeEvaluator) this.nodeEvaluator).cancel();
		}

	}

	@Override
	public void setNumCPUs(final int numberOfCPUs) {
		this.logger.warn("Currently no support for parallelization");
	}

	@Override
	public int getNumCPUs() {
		return 1;
	}

	@Override
	public GraphGenerator<T, A> getGraphGenerator() {
		return this.getInput().getGraphGenerator();
	}

	@Override
	public EvaluatedSearchGraphPath<T, A, V> getSolutionProvidedToCall() {
		return this.getBestSeenSolution();
	}

	@Override
	public void setLoggerName(final String name) {
		this.logger.info("Switching logger to {}", name);
		this.loggerName = name;
		this.logger = LoggerFactory.getLogger(name);
		this.logger.info("Switched to logger {}", name);
		super.setLoggerName(this.loggerName + "._orgraphsearch");
	}

	@Override
	public String getLoggerName() {
		return this.loggerName;
	}
}
