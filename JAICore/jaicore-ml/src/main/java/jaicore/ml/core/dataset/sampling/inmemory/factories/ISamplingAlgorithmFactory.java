package jaicore.ml.core.dataset.sampling.inmemory.factories;

import java.util.Random;

import jaicore.ml.core.dataset.IDataset;
import jaicore.ml.core.dataset.IInstance;
import jaicore.ml.core.dataset.sampling.inmemory.ASamplingAlgorithm;

/**
 * Interface for a factory, which creates a sampling algorithm.
 * 
 * @author Lukas Brandt
 * @param <I>
 *            Type of the dataset instances.
 */
public interface ISamplingAlgorithmFactory<I extends IInstance> {

	/**
	 * After the neccessary config is done, this method returns a fully configured
	 * instance of a sampling algorithm.
	 * 
	 * @param sampleSize
	 *            Desired size of the sample that will be created.
	 * @param inputDataset
	 *            Dataset where the sample will be drawn from.
	 * @param random
	 *            Random object to make samples reproducible.
	 * @return Configured sampling algorithm object.
	 */
	public ASamplingAlgorithm<I> getAlgorithm(int sampleSize, IDataset<I> inputDataset, Random random);

}
