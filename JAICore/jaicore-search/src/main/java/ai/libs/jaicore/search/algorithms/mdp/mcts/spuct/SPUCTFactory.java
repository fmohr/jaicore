package ai.libs.jaicore.search.algorithms.mdp.mcts.spuct;

import ai.libs.jaicore.search.algorithms.mdp.mcts.MCTS;
import ai.libs.jaicore.search.algorithms.mdp.mcts.MCTSBuilder;
import ai.libs.jaicore.search.algorithms.mdp.mcts.UniformRandomPolicy;
import ai.libs.jaicore.search.probleminputs.IMDP;

public class SPUCTFactory<N, A> extends MCTSBuilder<N, A, SPUCTFactory<N, A>> {

	private double bigD = 1000;

	public double getBigD() {
		return this.bigD;
	}

	public void setBigD(final double bigD) {
		this.bigD = bigD;
	}

	@Override
	public MCTS<N, A> getAlgorithm(final IMDP<N, A, Double> input) {
		return new SPUCT<>(input, new UniformRandomPolicy<>(this.getRandom()), this.bigD, this.getMaxIterations(), this.getGamma(), this.getEpsilon(), this.isTabooExhaustedNodes());
	}

}
