package ai.libs.jaicore.ml.classification.multilabel.loss;

import java.util.List;
import java.util.stream.IntStream;

import org.api4.java.ai.ml.classification.multilabel.evaluation.IMultiLabelClassification;
import org.api4.java.ai.ml.classification.multilabel.evaluation.IMultiLabelClassificationPredictionAndGroundTruthTable;
import org.api4.java.ai.ml.classification.multilabel.evaluation.loss.IMultiLabelClassificationMeasure;

import ai.libs.jaicore.ml.core.evaluation.loss.AMeasure;

public abstract class AMultiLabelClassificationMeasure extends AMeasure<IMultiLabelClassification, IMultiLabelClassificationPredictionAndGroundTruthTable> implements IMultiLabelClassificationMeasure {

	private static final double DEFAULT_THRESHOLD = 0.5;

	private final double threshold;

	protected AMultiLabelClassificationMeasure(final double threshold) {
		super();
		this.threshold = threshold;
	}

	protected AMultiLabelClassificationMeasure() {
		super();
		this.threshold = DEFAULT_THRESHOLD;
	}

	public double getThreshold() {
		return this.threshold;
	}

	protected double[][] listToRelevanceMatrix(final List<IMultiLabelClassification> classificationList) {
		double[][] matrix = new double[classificationList.size()][];
		IntStream.range(0, classificationList.size()).forEach(x -> {
			matrix[x] = classificationList.get(x).getLabelRelevanceVector();
		});

		return matrix;
	}

	protected int[][] listToThresholdedRelevanceMatrix(final List<IMultiLabelClassification> classificationList) {
		int[][] matrix = new int[classificationList.size()][];
		IntStream.range(0, classificationList.size()).forEach(x -> {
			matrix[x] = classificationList.get(x).getLabelRelevanceVector(this.threshold);
		});
		return matrix;
	}

	protected int[][] transposeMatrix(final int[][] matrix) {
		int[][] out = new int[matrix[0].length][];
		for (int i = 0; i < matrix[0].length; i++) {
			out[i] = new int[matrix.length];
			for (int j = 0; j < matrix.length; j++) {
				out[i][j] = matrix[j][i];
			}
		}
		return out;
	}

	protected double[][] transposeMatrix(final double[][] matrix) {
		double[][] out = new double[matrix[0].length][];
		for (int i = 0; i < matrix[0].length; i++) {
			out[i] = new double[matrix.length];
			for (int j = 0; j < matrix.length; j++) {
				out[i][j] = matrix[j][i];
			}
		}
		return out;
	}

}