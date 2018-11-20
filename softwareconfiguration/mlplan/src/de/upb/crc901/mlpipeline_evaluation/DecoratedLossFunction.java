package de.upb.crc901.mlpipeline_evaluation;

import java.util.List;

import com.google.common.base.Optional;

import hasco.model.ComponentInstance;
import jaicore.basic.aggregate.IAggregateFunction;
import jaicore.ml.WekaUtil;
import jaicore.ml.cache.ReproducibleInstances;
import jaicore.ml.evaluation.BasicMLEvaluator;
import jaicore.ml.evaluation.measures.IMeasure;
import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * A decorator for a basic loss-function ({@link BasicMLEvaluator}). This
 * decorator will check the given performance database before an evaluation and
 * will only compute an evaluation if the database has no such entry.
 * 
 * @author mirko
 *
 * @param <I> the input type
 * @param <O> the output type
 */
public class DecoratedLossFunction<I, O> implements IMeasure<I, O> {

	/**
	 * The decorated loss function
	 */
	private IMeasure<I, O> lossFunction;

	/**
	 * The component instance that will be evaluated,
	 */
	private ComponentInstance evaluatedComponent;

	/**
	 * The database that will be queried for performance evaluations.
	 */
	private PerformanceDBAdapter performanceDBAdapter;

	public DecoratedLossFunction(IMeasure<I, O> toDecorate, PerformanceDBAdapter performanceDBAdapter) {
		this.lossFunction = toDecorate;
		this.performanceDBAdapter = performanceDBAdapter;
	}

	@Override
	public double getErrorRateForRandomSplit(Classifier c, Instances data, double splitSize) throws Exception {
		List<Instances> split = WekaUtil.getStratifiedSplit(data, rand, splitSize);
		Instances train = split.get(0);
		Instances test = split.get(1);
		return getErrorRateForSplit(c, train, test);
	}

	public void setComponentInstance(ComponentInstance c) {
		this.evaluatedComponent = c;
	}

	/**
	 * The split has to be done by
	 * {@link WekaUtil#getStratifiedSplit(Instances, java.util.Random, double...)}
	 * 
	 */
	@Override
	public double getErrorRateForSplit(Classifier c, Instances train, Instances test) throws Exception {
		if (train instanceof ReproducibleInstances) {
			// check in the cache if the result exists already
			Optional<Double> potentialCache = performanceDBAdapter.exists(evaluatedComponent,
					(ReproducibleInstances) train);
			if (potentialCache.isPresent()) {
				return potentialCache.get();
			} else {
				// query the underlying loss function
				double performance = lossFunction.getErrorRateForSplit(c, train, test);
				// cache it
				if (train instanceof ReproducibleInstances) {
					performanceDBAdapter.store(evaluatedComponent, (ReproducibleInstances) train, performance);
				}
				return performance;
			}
		} else {
			return lossFunction.getErrorRateForSplit(c, train, test);
		}
	}

	@Override
	public O calculateMeasure(I actual, I expected) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<O> calculateMeasure(List<I> actual, List<I> expected) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public O calculateMeasure(List<I> actual, List<I> expected, IAggregateFunction<O> aggregateFunction) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public O calculateAvgMeasure(List<I> actual, List<I> expected) {
		// TODO Auto-generated method stub
		return null;
	}

}
