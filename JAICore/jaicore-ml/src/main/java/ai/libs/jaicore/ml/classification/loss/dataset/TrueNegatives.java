package ai.libs.jaicore.ml.classification.loss.dataset;

import java.util.List;
import java.util.stream.IntStream;

public class TrueNegatives extends AHomogeneousPredictionPerformanceMeasure<Object> {

	private final Object positiveClass;

	public TrueNegatives(final Object positiveClass) {
		this.positiveClass = positiveClass;
	}

	@Override
	public double score(final List<?> expected, final List<?> predicted) {
		return IntStream.range(0, expected.size()).filter(i -> !expected.get(i).equals(this.positiveClass) && expected.get(i).equals(predicted.get(i))).map(x -> 1).sum();
	}

}
