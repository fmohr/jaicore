package ai.libs.jaicore.ml.weka.dataset.splitter;

import java.util.List;
import java.util.Random;

import ai.libs.jaicore.ml.weka.WekaUtil;
import ai.libs.jaicore.ml.weka.dataset.IWekaInstance;
import ai.libs.jaicore.ml.weka.dataset.IWekaInstances;

/**
 * Generates a purely random split of the dataset depending on the seed and on the portions provided.
 *
 * @author mwever
 */
public class ArbitrarySplitter implements IDatasetSplitter<IWekaInstance, IWekaInstances> {

	@Override
	public List<IWekaInstances> split(final IWekaInstances data, final long seed, final double portions) {
		return WekaUtil.realizeSplit(data, WekaUtil.getArbitrarySplit(data, new Random(seed), portions));
	}

}