package jaicore.ml.core.dataset.sampling.inmemory;

import java.util.Random;

import jaicore.basic.algorithm.IAlgorithm;
import jaicore.ml.core.dataset.AILabeledAttributeArrayDataset;
import jaicore.ml.core.dataset.IInstance;
import jaicore.ml.core.dataset.sampling.inmemory.factories.LocalCaseControlSamplingFactory;

public class LocalCaseControlSamplingTester<I extends IInstance> extends GeneralSamplingTester<I> {

	private static long RANDOM_SEED = 1;
	private static double DEFAULT_SAMPLE_FRACTION = 0.1;
	private static double PRE_SAMPLING_FRACTION = 0.01;

	@Override
	public IAlgorithm<?, ?> getAlgorithm(Object problem) {
		@SuppressWarnings("unchecked")
		AILabeledAttributeArrayDataset<I> dataset = (AILabeledAttributeArrayDataset<I>) problem;
		LocalCaseControlSamplingFactory<I> factory = new LocalCaseControlSamplingFactory<>();
		if (dataset != null) {
			factory.setPreSampleSize((int) (PRE_SAMPLING_FRACTION * dataset.size()));
			int sampleSize = (int) (DEFAULT_SAMPLE_FRACTION * (double) dataset.size());
			return factory.getAlgorithm(sampleSize, dataset, new Random(RANDOM_SEED));
		}
		return null;
	}
}
