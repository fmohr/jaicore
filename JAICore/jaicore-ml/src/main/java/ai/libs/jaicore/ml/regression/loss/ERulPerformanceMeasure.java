package ai.libs.jaicore.ml.regression.loss;

import java.util.List;

import org.api4.java.ai.ml.core.evaluation.IPredictionAndGroundTruthTable;
import org.api4.java.ai.ml.core.evaluation.supervised.loss.IDeterministicHomogeneousPredictionPerformanceMeasure;

import ai.libs.jaicore.ml.regression.loss.dataset.AsymmetricLoss;

public enum ERulPerformanceMeasure implements IDeterministicHomogeneousPredictionPerformanceMeasure<Double> {
	ASYMMETRIC_LOSS(new AsymmetricLoss());

	private final IDeterministicHomogeneousPredictionPerformanceMeasure<Double> measure;

	private ERulPerformanceMeasure(final IDeterministicHomogeneousPredictionPerformanceMeasure<Double> measure) {
		this.measure = measure;
	}

	@Override
	public double loss(List<? extends Double> expected, List<? extends Double> actual) {
		return this.measure.loss(actual, expected);
	}

	@Override
	public double loss(IPredictionAndGroundTruthTable<? extends Double, ? extends Double> pairTable) {
		return this.measure.loss(pairTable);
	}

	@Override
	public double score(List<? extends Double> expected, List<? extends Double> actual) {
		return this.measure.score(actual, expected);
	}

	@Override
	public double score(IPredictionAndGroundTruthTable<? extends Double, ? extends Double> pairTable) {
		return this.measure.score(pairTable);
	}

}