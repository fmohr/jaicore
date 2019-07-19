package ai.libs.jaicore.ml.core.dataset.sampling.inmemory;

import java.util.Random;

import org.api4.java.ai.ml.core.dataset.IOrderedLabeledAttributeArrayDataset;
import org.api4.java.algorithm.IAlgorithm;

import ai.libs.jaicore.ml.core.dataset.sampling.IClusterableInstances;
import ai.libs.jaicore.ml.core.dataset.sampling.inmemory.factories.OSMACSamplingFactory;

public class OSMACSamplingTester extends GeneralSamplingTester<Object> {

	private static final long RANDOM_SEED = 1;
	private static final double DEFAULT_SAMPLE_FRACTION = 0.1;
	private static final double PRE_SAMPLING_FRACTION = 0.01;

	@Override
	public IAlgorithm<?, ?> getAlgorithm(final IOrderedLabeledAttributeArrayDataset<IClusterableInstances<Object>, Object> dataset) {
		OSMACSamplingFactory<IClusterableInstances<Object>, IOrderedLabeledAttributeArrayDataset<IClusterableInstances<Object>, Object>> factory = new OSMACSamplingFactory<>();
		if (dataset != null) {
			factory.setPreSampleSize((int) (PRE_SAMPLING_FRACTION * dataset.size()));
			int sampleSize = (int) (DEFAULT_SAMPLE_FRACTION * dataset.size());
			return factory.getAlgorithm(sampleSize, dataset, new Random(RANDOM_SEED));
		}
		return null;
	}
}
