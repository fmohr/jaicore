package ai.libs.jaicore.ml.core.evaluation.splitsetgenerator;

import org.api4.java.ai.ml.classification.execution.IDatasetSplitSet;
import org.api4.java.ai.ml.classification.execution.IFixedDatasetSplitSetGenerator;
import org.api4.java.ai.ml.core.dataset.IDataset;
import org.api4.java.ai.ml.core.dataset.IInstance;
import org.api4.java.ai.ml.core.dataset.splitter.SplitFailedException;

import ai.libs.jaicore.ml.core.dataset.splitter.DatasetSplitSet;

public class ConstantSplitSetGenerator<I extends IInstance, D extends IDataset<I>> implements IFixedDatasetSplitSetGenerator<D> {

	DatasetSplitSet<D> set;

	public ConstantSplitSetGenerator(final IDatasetSplitSet<D> set) {
		this.set = new DatasetSplitSet<>(set);
	}

	@Override
	public int getNumSplitsPerSet() {
		return this.set.getNumberOfSplits();
	}

	@Override
	public int getNumFoldsPerSplit() {
		return this.set.getNumberOfFoldsPerSplit();
	}

	@Override
	public IDatasetSplitSet<D> nextSplitSet() throws InterruptedException, SplitFailedException {
		return this.set;
	}

	@Override
	public D getDataset() {
		throw new UnsupportedOperationException("The dataset has already been composed here.");
	}

}