package ai.libs.jaicore.ml.classification.multilabel.evaluation.loss;

import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

import org.api4.java.ai.ml.classification.multilabel.IRelevanceOrderedLabelSet;

import ai.libs.jaicore.ml.core.evaluation.loss.F1Measure;

/**
 * Instance-wise F1 measure for multi-label classifiers.
 *
 * For reference see
 * Wu, Xi-Zhu; Zhou, Zhi-Hua: A Unified View of Multi-Label Performance Measures (ICML / JMLR 2017)
 *
 * @author mwever
 *
 */
public class InstanceWiseF1 extends AThresholdBasedMultiLabelClassificationMeasure {

	public InstanceWiseF1(final double threshold) {
		super(threshold);
	}

	public InstanceWiseF1() {
		super();
	}

	@Override
	public double loss(final List<IRelevanceOrderedLabelSet> actual, final List<IRelevanceOrderedLabelSet> expected) {
		return 1 - this.score(expected, actual);
	}

	@Override
	public double score(final List<IRelevanceOrderedLabelSet> expected, final List<IRelevanceOrderedLabelSet> actual) {
		this.checkConsistency(expected, actual);
		int[][] expectedMatrix = this.listToThresholdedRelevanceMatrix(expected);
		int[][] actualMatrix = this.listToThresholdedRelevanceMatrix(actual);

		F1Measure baseMeasure = new F1Measure(1);
		OptionalDouble res = IntStream.range(0, expectedMatrix.length).mapToDouble(x -> baseMeasure.score(expectedMatrix[x], actualMatrix[x])).average();
		if (!res.isPresent()) {
			throw new IllegalStateException("Could not determine average label-wise f measure.");
		} else {
			return res.getAsDouble();
		}
	}
}