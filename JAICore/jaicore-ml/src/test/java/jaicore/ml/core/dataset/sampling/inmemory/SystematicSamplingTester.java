package jaicore.ml.core.dataset.sampling.inmemory;

import java.util.Random;

import jaicore.basic.algorithm.IAlgorithm;
import jaicore.ml.core.dataset.AILabeledAttributeArrayDataset;
import jaicore.ml.core.dataset.IInstance;
import jaicore.ml.core.dataset.sampling.inmemory.factories.SystematicSamplingFactory;

public class SystematicSamplingTester<I extends IInstance> extends GeneralSamplingTester<I> {

	private static final long RANDOM_SEED = 1;

	private static final double DEFAULT_SAMPLE_FRACTION = 0.1;

	@Override
	public IAlgorithm<?, ?> getAlgorithm(Object problem) {
		@SuppressWarnings("unchecked")
		AILabeledAttributeArrayDataset<I> dataset = (AILabeledAttributeArrayDataset<I>) problem;
		SystematicSamplingFactory<I> factory = new SystematicSamplingFactory<>();
		if (dataset != null) {
			int sampleSize = (int) (DEFAULT_SAMPLE_FRACTION * (double) dataset.size());
			return factory.getAlgorithm(sampleSize, dataset, new Random(RANDOM_SEED));
		}
		return null;
	}

}
