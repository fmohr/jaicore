package ai.libs.jaicore.ml.tsc.classifier.neighbors;

import org.api4.java.algorithm.events.AlgorithmEvent;
import org.api4.java.algorithm.exceptions.AlgorithmException;

import ai.libs.jaicore.basic.IOwnerBasedAlgorithmConfig;
import ai.libs.jaicore.ml.tsc.classifier.ASimplifiedTSCLearningAlgorithm;
import ai.libs.jaicore.ml.tsc.dataset.TimeSeriesDataset;

/**
 * Training algorithm for the nearest neighbors classifier.
 *
 * This algorithm just delegates the value matrix, timestamps and targets to the
 * classifier.
 *
 * @author fischor
 */
public class NearestNeighborLearningAlgorithm extends ASimplifiedTSCLearningAlgorithm<Integer, NearestNeighborClassifier> {

	protected NearestNeighborLearningAlgorithm(final IOwnerBasedAlgorithmConfig config, final NearestNeighborClassifier classifier, final TimeSeriesDataset input) {
		super(config, classifier, input);
	}

	@Override
	public NearestNeighborClassifier call() throws AlgorithmException {
		TimeSeriesDataset dataset = this.getInput();
		if (dataset == null) {
			throw new AlgorithmException("No input data set.");
		}
		if (dataset.isMultivariate()) {
			throw new UnsupportedOperationException("Multivariate datasets are not supported.");
		}

		// Retrieve data from dataset.
		double[][] values = dataset.getValuesOrNull(0);
		// Check data.
		if (values == null) {
			throw new AlgorithmException("Empty input data set.");
		}
		int[] targets = dataset.getTargets();
		if (targets == null) {
			throw new AlgorithmException("Empty targets.");
		}

		// Update model.
		NearestNeighborClassifier model = this.getClassifier();
		model.setValues(values);
		model.setTimestamps(dataset.getTimestampsOrNull(0));
		model.setTargets(targets);
		return model;
	}

	@Override
	public AlgorithmEvent nextWithException() {
		throw new UnsupportedOperationException();
	}
}