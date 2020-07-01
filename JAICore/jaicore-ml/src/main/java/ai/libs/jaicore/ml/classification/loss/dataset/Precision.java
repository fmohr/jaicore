package ai.libs.jaicore.ml.classification.loss.dataset;

import java.util.List;

public class Precision extends AHomogeneousPredictionPerformanceMeasure<Object> {

	private final TruePositives tp;
	private final FalsePositives fp;

	public Precision(final Object positiveClass) {
		this.tp = new TruePositives(positiveClass);
		this.fp = new FalsePositives(positiveClass);
	}

	@Override
	public double score(final List<?> expected, final List<?> predicted) {
		double truePositives = this.tp.score(expected, predicted);
		double denominator = (truePositives + this.fp.score(expected, predicted));
		return denominator == 0.0 ? 0 : truePositives / denominator;
	}

}
