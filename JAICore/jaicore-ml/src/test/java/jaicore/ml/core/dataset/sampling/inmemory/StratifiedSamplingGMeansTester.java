package jaicore.ml.core.dataset.sampling.inmemory;

import java.util.Random;

import jaicore.basic.algorithm.IAlgorithm;
import jaicore.ml.core.dataset.INumericLabeledAttributeArrayInstance;
import jaicore.ml.core.dataset.IOrderedLabeledAttributeArrayDataset;
import jaicore.ml.core.dataset.sampling.inmemory.factories.StratifiedSamplingFactory;
import jaicore.ml.core.dataset.sampling.inmemory.stratified.sampling.GMeansStratiAmountSelectorAndAssigner;

public class StratifiedSamplingGMeansTester extends GeneralSamplingTester {

	private static final int RANDOM_SEED = 1;

	private static final double DEFAULT_SAMPLE_FRACTION = 0.1;

	@Override
	public IAlgorithm<?, ?> getAlgorithm(IOrderedLabeledAttributeArrayDataset<INumericLabeledAttributeArrayInstance> dataset) {
		GMeansStratiAmountSelectorAndAssigner<INumericLabeledAttributeArrayInstance, IOrderedLabeledAttributeArrayDataset<INumericLabeledAttributeArrayInstance>> g = new GMeansStratiAmountSelectorAndAssigner<>(RANDOM_SEED);
		StratifiedSamplingFactory<INumericLabeledAttributeArrayInstance, IOrderedLabeledAttributeArrayDataset<INumericLabeledAttributeArrayInstance>> factory = new StratifiedSamplingFactory<>(g, g);
		if (dataset != null) {
			int sampleSize = (int) (DEFAULT_SAMPLE_FRACTION * (double) dataset.size());
			return factory.getAlgorithm(sampleSize, dataset, new Random(RANDOM_SEED));
		}
		return null;
	}

}