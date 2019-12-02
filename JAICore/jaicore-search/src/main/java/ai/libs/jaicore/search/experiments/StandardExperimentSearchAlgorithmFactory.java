package ai.libs.jaicore.search.experiments;

import java.util.Arrays;
import java.util.Random;

import org.api4.java.ai.graphsearch.problem.IGraphSearchWithPathEvaluationsInput;
import org.api4.java.ai.graphsearch.problem.IOptimalPathInORGraphSearch;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.NodeGoalTester;
import org.api4.java.ai.graphsearch.problem.pathsearch.pathevaluation.IEvaluatedPath;

import ai.libs.jaicore.basic.IOwnerBasedRandomConfig;
import ai.libs.jaicore.experiments.Experiment;
import ai.libs.jaicore.experiments.configurations.IAlgorithmNameConfig;
import ai.libs.jaicore.search.algorithms.standard.auxilliary.iteratingoptimizer.IteratingGraphSearchOptimizer;
import ai.libs.jaicore.search.algorithms.standard.auxilliary.iteratingoptimizer.IteratingGraphSearchOptimizerFactory;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.BestFirst;
import ai.libs.jaicore.search.algorithms.standard.dfs.DepthFirstSearchFactory;
import ai.libs.jaicore.search.algorithms.standard.mcts.SPUCTPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.UCBPolicy;
import ai.libs.jaicore.search.algorithms.standard.mcts.UCTPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.comparison.FixedCommitmentMCTSPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.comparison.PlackettLuceMCTSPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.comparison.PlackettLucePolicy;
import ai.libs.jaicore.search.algorithms.standard.mcts.comparison.preferencekernel.BootstrappingPreferenceKernel;
import ai.libs.jaicore.search.algorithms.standard.mcts.ensemble.EnsembleMCTSPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.tag.TAGMCTSPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.thompson.DNGMCTSPathSearch;
import ai.libs.jaicore.search.algorithms.standard.mcts.thompson.DNGPolicy;
import ai.libs.jaicore.search.algorithms.standard.random.RandomSearchFactory;
import ai.libs.jaicore.search.model.other.SearchGraphPath;
import ai.libs.jaicore.search.problemtransformers.GraphSearchProblemInputToGraphSearchWithSubpathEvaluationInputTransformerViaRDFS;
import ai.libs.jaicore.search.problemtransformers.GraphSearchWithPathEvaluationsInputToGraphSearchWithSubpathEvaluationViaUninformedness;

public class StandardExperimentSearchAlgorithmFactory<N, A, I extends IGraphSearchWithPathEvaluationsInput<N, A, Double>> {

	public IOptimalPathInORGraphSearch<I, ? extends IEvaluatedPath<N, A, Double>, N, A, Double> getAlgorithm(final Experiment experiment, final IGraphSearchWithPathEvaluationsInput<N, A, Double> input) {
		final int seed = Integer.parseInt(experiment.getValuesOfKeyFields().get(IOwnerBasedRandomConfig.K_SEED));
		final String algorithm = experiment.getValuesOfKeyFields().get(IAlgorithmNameConfig.K_ALGORITHM_NAME);
		switch (algorithm) {
		case "random":
			IteratingGraphSearchOptimizerFactory<I, N, A, Double> factory = new IteratingGraphSearchOptimizerFactory<I, N, A, Double>();
			factory.setBaseAlgorithmFactory(new RandomSearchFactory<>());
			IteratingGraphSearchOptimizer<I, N, A, Double> optimizer = factory.getAlgorithm((I)input);
			return optimizer;
		case "bf-uninformed":
			GraphSearchWithPathEvaluationsInputToGraphSearchWithSubpathEvaluationViaUninformedness<N, A> reducer = new GraphSearchWithPathEvaluationsInputToGraphSearchWithSubpathEvaluationViaUninformedness<>();
			IGraphSearchWithPathEvaluationsInput<N, A, Double> reducedProblem = reducer.encodeProblem(input);
			return new BestFirst<I, N, A, Double>((I)reducedProblem);
		case "bf-informed":
			GraphSearchProblemInputToGraphSearchWithSubpathEvaluationInputTransformerViaRDFS<N, A, Double> reducer2 = new GraphSearchProblemInputToGraphSearchWithSubpathEvaluationInputTransformerViaRDFS<>(n -> null,
					n -> false, seed, 3, 10000, 10000);
			return new BestFirst<>((I)reducer2.encodeProblem(input));
		case "uct":
			return new UCTPathSearch<I, N, A>((I)input, false, Math.sqrt(2), seed, 0.0);
		case "ensemble":

			DNGPolicy<N, A> dng0001 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, .001);
			DNGPolicy<N, A> dng001 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, .01);
			DNGPolicy<N, A> dng01 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, .1);
			DNGPolicy<N, A> dng1 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, 1.0);
			DNGPolicy<N, A> dng10 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, 10.0);
			DNGPolicy<N, A> dng100 = new DNGPolicy<>((NodeGoalTester<N, A>)((I)input).getGoalTester(), n -> ((I)input).getPathEvaluator().evaluate(new SearchGraphPath<>(n)), 0, 100.0);
			PlackettLucePolicy<N, A> pl = new PlackettLucePolicy<N, A>(new BootstrappingPreferenceKernel<N, A>(d -> d.getMean(), 1), new Random(seed));
			return new EnsembleMCTSPathSearch<I, N, A>((I)input, Arrays.asList(new UCBPolicy<>(), dng001, dng01, dng1, dng01, dng10, dng100), new Random(seed));
		case "sp-uct":
			return new SPUCTPathSearch<>((I)input, seed, 0.0, .5, 10000);
		case "pl-mcts-mean":
			return new PlackettLuceMCTSPathSearch<>((I)input, new BootstrappingPreferenceKernel<>(d -> d.getMean(), 1), new Random(seed), new Random(seed));
		case "pl-mcts-mean+std":
			return new PlackettLuceMCTSPathSearch<>((I)input, new BootstrappingPreferenceKernel<>(d -> d.getMean() + d.getStandardDeviation(), 1), new Random(seed), new Random(seed));
		case "pl-mcts-mean-std":
			return new PlackettLuceMCTSPathSearch<>((I)input, new BootstrappingPreferenceKernel<>(d -> d.getMean() - d.getStandardDeviation(), 1), new Random(seed), new Random(seed));
		case "pl-mcts-min":
			return new PlackettLuceMCTSPathSearch<>((I)input, new BootstrappingPreferenceKernel<>(d -> d.getMin(), 1), new Random(seed), new Random(seed));
		case "mcts-kfix-100-mean":
			return new FixedCommitmentMCTSPathSearch<>((I)input, 0.0, 100, d -> d.getMean());
		case "mcts-kfix-200-mean":
			return new FixedCommitmentMCTSPathSearch<>((I)input, 0.0, 200, d -> d.getMean());
		case "dng":
			return new DNGMCTSPathSearch<>((I)input, seed, 0.0, 1.0);
		case "tag":
			return new TAGMCTSPathSearch<>((I)input, seed, 0.0);
		case "dfs":
			IteratingGraphSearchOptimizerFactory<I, N, A, Double> dfsFactory = new IteratingGraphSearchOptimizerFactory<I, N, A, Double>();
			dfsFactory.setBaseAlgorithmFactory(new DepthFirstSearchFactory<>());
			return dfsFactory.getAlgorithm((I)input);
		default:
			throw new IllegalArgumentException("Unsupported algorithm " + algorithm);
		}
	}
}
