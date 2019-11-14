package ai.libs.jaicore.search.algorithms.standard.mcts.comparison.preferencekernel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.api4.java.datastructure.graph.IPath;

import ai.libs.jaicore.graph.LabeledGraph;
import ai.libs.jaicore.search.algorithms.standard.mcts.comparison.IPreferenceKernel;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class BootstrappingPreferenceKernel<N, A> implements IPreferenceKernel<N, A> {

	private LabeledGraph<N, A> explorationGraph;
	private final Map<N, DoubleList> observations = new HashMap<>();
	private final IBootstrappingParameterComputer bootstrapParameterComputer;

	private final int maxNumSamplesInHistory = 1000; // 100 worked quite well
	private final int maxNumSamplesInBootstrap = 10; // 20 worked quite well
	private final int numBootstrapsPerChild = 10; // 20 worked quite well
	private final Random random = new Random(0);
	private final Map<N, List<List<N>>> rankingsForNodes = new HashMap<>();
	private final int minSamplesToCreateRankings;

	public BootstrappingPreferenceKernel(final IBootstrappingParameterComputer bootstrapParameterComputer, final int minSamplesToCreateRankings) {
		super();
		this.bootstrapParameterComputer = bootstrapParameterComputer;
		this.minSamplesToCreateRankings = minSamplesToCreateRankings;
	}

	@Override
	public void signalNewScore(final IPath<N, A> path, final double newScore) {

		/* add the observation to all stats on the path */
		List<N> nodes = path.getNodes();
		int l = nodes.size();
		for (int i = l - 1; i >= 0; i --) {
			N node = nodes.get(i);
			DoubleList list = this.observations.computeIfAbsent(node, n -> new DoubleArrayList());
			list.add(newScore);
			if (list.size() > this.maxNumSamplesInHistory) {
				list.removeDouble(0);
			}
		}
	}

	/**
	 * Computes new rankings from a fresh bootstrap
	 *
	 * @param node
	 * @param parameterComputer
	 * @return
	 */
	public List<List<N>> drawNewRankingsForChildrenOfNode(final N node, final IBootstrappingParameterComputer parameterComputer) {
		Collection<N> children = this.explorationGraph.getSuccessors(node);
		int numChildren = children.size();
		List<List<N>> rankings = new ArrayList<>(this.numBootstrapsPerChild * numChildren);
		Map<N, DoubleList> observationsPerChild = new HashMap<>();
		for (N child : children) {
			if (!this.observations.containsKey(child)) {
				return null;
			}
			observationsPerChild.put(child, this.observations.get(child));
		}

		int numBootstraps = this.numBootstrapsPerChild * numChildren;
		for (int bootstrap = 0; bootstrap < numBootstraps; bootstrap++) {
			Map<N, Double> scorePerChild = new HashMap<>();
			for (N child : children) {
				DoubleList observedScoresForChild = observationsPerChild.get(child);
				DescriptiveStatistics statsForThisChild = new DescriptiveStatistics();
				for (int sample = 0; sample < this.maxNumSamplesInBootstrap; sample++) {
					statsForThisChild.addValue(observedScoresForChild.getDouble(this.random.nextInt(observedScoresForChild.size())));
				}
				scorePerChild.put(child, parameterComputer.getParameter(statsForThisChild));
			}
			List<N> ranking = children.stream().sorted((c1,c2) -> Double.compare(scorePerChild.get(c1), scorePerChild.get(c2))).collect(Collectors.toList());
			rankings.add(ranking);
		}
		return rankings;
	}

	@Override
	public List<List<N>> getRankingsForChildrenOfNode(final N node) {
		this.rankingsForNodes.put(node,this.drawNewRankingsForChildrenOfNode(node, this.bootstrapParameterComputer));
		return this.rankingsForNodes.get(node);
	}

	@Override
	public void setExplorationGraph(final LabeledGraph<N, A> graph) {
		this.explorationGraph = graph;
	}

	@Override
	public boolean canProduceReliableRankings(final N node) {
		int minObservations = Integer.MAX_VALUE;
		for (N child : this.explorationGraph.getSuccessors(node)) {
			minObservations = Math.min(minObservations, this.observations.get(child).size());
		}
		return minObservations > this.minSamplesToCreateRankings;
	}
}
