package ai.libs.jaicore.search.algorithms.standard.mcts.uuct;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.api4.java.datastructure.graph.ILabeledPath;

import ai.libs.jaicore.basic.sets.SetUtil;
import ai.libs.jaicore.search.algorithms.standard.mcts.ActionPredictionFailedException;
import ai.libs.jaicore.search.algorithms.standard.mcts.IPathUpdatablePolicy;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class UUCBPolicy<N, A> implements IPathUpdatablePolicy<N, A, Double> {
	private static final double ALPHA = 3;
	private final IUCBUtilityFunction utilityFunction;
	private final double a;
	private final double b;
	private final double q;
	private final Map<N, Map<A, DoubleList>> observations = new HashMap<>();
	private int t = 0;

	public UUCBPolicy(final IUCBUtilityFunction utilityFunction) {
		super();
		this.utilityFunction = utilityFunction;
		this.a = utilityFunction.getA();
		this.b = utilityFunction.getB();
		this.q = utilityFunction.getQ();
	}

	@Override
	public A getAction(final N node, final Collection<A> possibleActions) throws ActionPredictionFailedException {

		/* compute score of each successor (eq 16 in paper and 15 in extended) */
		double bestScore = Double.MAX_VALUE * -1;
		A bestAction = null;
		Map<A, DoubleList> observationsForActions = this.observations.get(node);
		if (observationsForActions == null) {
			return SetUtil.getRandomElement(possibleActions, new Random().nextLong());
		}
		//		System.out.println("Compare arms: ");
		for (A succ : possibleActions) {
			DoubleList observationsOfChild = observationsForActions.get(succ);
			if (observationsOfChild == null) {
				continue;
			}
			double utility = this.utilityFunction.getUtility(observationsOfChild);
			double phiInverse = this.phiInverse((ALPHA * Math.log(this.t)) / observationsOfChild.size());
			//			System.out.println("utility of " + succ.getKey() + ": " + utility);
			double score = utility + phiInverse;
			//			System.out.println(score + " vs " + bestScore);
			if (score > bestScore) {
				bestScore = score;
				bestAction = succ;
			}
		}
		return bestAction;
	}

	private double phiInverse(final double x) {
		return Math.max(2 * this.b * Math.sqrt(x / this.a), 2 * this.b * Math.pow(x / this.a, this.q / 2));
	}

	@Override
	public void updatePath(final ILabeledPath<N, A> path, final Double playoutScore) {
		//		path.getPathToParentOfHead().getNodes().forEach(n -> this.observations.computeIfAbsent(n, node -> new HashMap<>()).computeIfAbsent(path.getOutArc(n), a -> new DoubleArrayList()).add((double) playoutScore));
		double s = playoutScore;
		path.getPathToParentOfHead().getNodes().forEach(n -> {
			DoubleList obs = this.observations.computeIfAbsent(n, node -> new HashMap<>()).computeIfAbsent(path.getOutArc(n), a -> new DoubleArrayList());
			int size = obs.size();
			if (size == 0) {
				obs.add(s);
			}
			else if (s <= obs.getDouble(0)) {
				obs.add(0, s);
			}
			else {
				double last = obs.getDouble(0);
				double next;
				for (int i = 1; i < size; i++) {
					next = obs.getDouble(i);
					if (playoutScore >= last && playoutScore <= next) {
						obs.add(i, s);
						return;
					}
					last = next;
				}
			}
		});
		this.t++;
	}
}
