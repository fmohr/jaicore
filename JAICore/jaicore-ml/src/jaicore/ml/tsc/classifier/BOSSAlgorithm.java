package jaicore.ml.tsc.classifier;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import jaicore.basic.TimeOut;
import jaicore.basic.algorithm.IAlgorithmConfig;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.ml.core.dataset.TimeSeriesDataset;
import jaicore.ml.core.dataset.attribute.categorical.CategoricalAttributeType;
import jaicore.ml.core.dataset.attribute.categorical.CategoricalAttributeValue;

public class BOSSAlgorithm
		extends ATSCAlgorithm<CategoricalAttributeType, CategoricalAttributeValue, TimeSeriesDataset, BOSSClassifier> {

	@Override
	public Iterator<AlgorithmEvent> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasNext() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public AlgorithmEvent next() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BOSSClassifier call() {
		// TODO Auto-generated method stub

		// Training on model

		return this.model;
	}

	@Override
	public void cancel() {
		// TODO Auto-generated method stub

	}

	// TODO: Change to TimeSeriesDataset when available
	@Override
	public TimeSeriesDataset getInput() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerListener(Object listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setNumCPUs(int numberOfCPUs) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getNumCPUs() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setTimeout(long timeout, TimeUnit timeUnit) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTimeout(TimeOut timeout) {
		// TODO Auto-generated method stub

	}

	@Override
	public TimeOut getTimeout() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AlgorithmEvent nextWithException() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IAlgorithmConfig getConfig() {
		// TODO Auto-generated method stub
		return null;
	}

}
