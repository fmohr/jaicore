package ai.libs.jaicore.ml.core.filter.sampling.inmemory.stratified.sampling;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.api4.java.ai.ml.core.dataset.IDataset;
import org.api4.java.ai.ml.core.dataset.IInstance;
import org.api4.java.ai.ml.core.exception.DatasetCreationException;
import org.api4.java.algorithm.events.IAlgorithmEvent;
import org.api4.java.algorithm.exceptions.AlgorithmException;
import org.api4.java.common.control.ILoggingCustomizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.libs.jaicore.ml.core.dataset.DatasetDeriver;
import ai.libs.jaicore.ml.core.filter.sampling.SampleElementAddedEvent;
import ai.libs.jaicore.ml.core.filter.sampling.inmemory.ASamplingAlgorithm;
import ai.libs.jaicore.ml.core.filter.sampling.inmemory.SimpleRandomSampling;
import ai.libs.jaicore.ml.core.filter.sampling.inmemory.WaitForSamplingStepEvent;

/**
 * Implementation of Stratified Sampling: Divide dataset into strati and sample
 * from each of these.
 *
 * @author Lukas Brandt
 */
public class StratifiedSampling<D extends IDataset<?>> extends ASamplingAlgorithm<D> {
	private Logger logger = LoggerFactory.getLogger(StratifiedSampling.class);
	private IStratiAmountSelector stratiAmountSelector;
	private IStratiAssigner stratiAssigner;
	private Random random;
	private DatasetDeriver<D>[] stratiBuilder = null;
	private boolean allDatapointsAssigned = false;
	private boolean simpleRandomSamplingStarted;
	private int numSelectedPoints;
	private int dsHash;

	/**
	 * Constructor for Stratified Sampling.
	 *
	 * @param stratiAmountSelector
	 *            The custom selector for the used amount of strati.
	 * @param stratiAssigner
	 *            Custom logic to assign datapoints into strati.
	 * @param random
	 *            Random object for sampling inside of the strati.
	 */
	public StratifiedSampling(final IStratiAmountSelector stratiAmountSelector, final IStratiAssigner stratiAssigner, final Random random, final D input) {
		super(input);
		this.stratiAmountSelector = stratiAmountSelector;
		this.stratiAssigner = stratiAssigner;
		this.random = random;
	}

	@SuppressWarnings("unchecked")
	@Override
	public IAlgorithmEvent nextWithException() throws InterruptedException, AlgorithmException {
		switch (this.getState()) {
		case CREATED:
			if (!this.allDatapointsAssigned) {
				this.dsHash = this.getInput().hashCode();
				this.stratiAmountSelector.setNumCPUs(this.getNumCPUs());
				this.stratiAssigner.setNumCPUs(this.getNumCPUs());

				/* create strati builder */
				this.stratiBuilder= (DatasetDeriver<D>[])Array.newInstance(new DatasetDeriver<>(this.getInput()).getClass(), this.stratiAmountSelector.selectStratiAmount(this.getInput()));
				for (int i = 0; i < this.stratiBuilder.length; i++) {
					this.stratiBuilder[i] = new DatasetDeriver<>(this.getInput());
				}
				if (this.stratiBuilder.length == 0) {
					throw new IllegalStateException("No strati have been defined.");
				}

				this.stratiAssigner.init(this.getInput(), this.stratiBuilder.length);
				if (this.getInput().hashCode() != this.dsHash) {
					throw new IllegalStateException("Original dataset has been modified!");
				}
			}
			this.simpleRandomSamplingStarted = false;
			this.logger.info("Stratified sampler initialized.");
			return this.activate();
		case ACTIVE:
			if (!this.allDatapointsAssigned) {

				/* sort all points into their respective stratum */
				D dataset = this.getInput();
				int n = dataset.size();
				for (int i = 0; i < n; i ++) {
					IInstance datapoint = dataset.get(i);
					this.logger.debug("Computing statrum for next data point {}", datapoint);
					int assignedStratum = this.stratiAssigner.assignToStrati(datapoint);
					if (assignedStratum < 0 || assignedStratum >= this.stratiBuilder.length) {
						throw new AlgorithmException("No existing strati for index " + assignedStratum);
					} else {
						this.stratiBuilder[assignedStratum].add(i); // adding i is MUCH more efficient than adding datapoint
					}
					this.logger.debug("Added data point {} to stratum {}. {} datapoints remaining.", datapoint, assignedStratum, n - i - 1);
				}

				/* check number of samples */
				this.allDatapointsAssigned = true;
				int totalItemsAssigned = 0;
				for (DatasetDeriver<D> d : this.stratiBuilder) {
					this.logger.debug("Elements in stratum: {}", d.currentSizeOfTarget());
					totalItemsAssigned += d.currentSizeOfTarget();
				}
				this.logger.info("Finished stratum assignments. Assigned {} data points in total.", totalItemsAssigned);
				if (totalItemsAssigned != this.getInput().size()) {
					throw new IllegalStateException("Not all data have been collected.");
				}
				return new SampleElementAddedEvent(this);
			} else {
				if (!this.simpleRandomSamplingStarted) {

					/* Simple Random Sampling has not started yet -> Initialize one sampling thread per stratum. */
					try {
						this.startSimpleRandomSamplingForStrati();
					} catch (DatasetCreationException e) {
						throw new AlgorithmException("Could not create sample from strati.", e);
					}
					this.simpleRandomSamplingStarted = true;
					return new WaitForSamplingStepEvent(this);
				} else {

					/* Check if all threads are finished. If yes finish Stratified Sampling, wait shortly in this step otherwise. */
					this.logger.info("Stratified sampling completed.");
					return this.terminate();
				}
			}
		case INACTIVE:
			if (this.sample.size() < this.sampleSize) {
				throw new AlgorithmException("Expected sample size was not reached before termination");
			} else {
				return this.terminate();
			}
		default:
			throw new IllegalStateException("Unknown algorithm state " + this.getState());
		}
	}

	/**
	 * Calculates the necessary sample sizes and start a Simple Random Sampling
	 * Thread for each stratum.
	 * @throws DatasetCreationException
	 * @throws InterruptedException
	 */
	private void startSimpleRandomSamplingForStrati() throws InterruptedException, DatasetCreationException {

		if (this.sampleSize == -1) {
			throw new IllegalStateException("No valid sample size specified");
		}

		/* Calculate the amount of datapoints that will be used from each strati.
		 * First, floor all fractional numbers. Then, distribute the remaining samples randomly among the strati */
		this.logger.info("Now drawing simple random elements in each stratum.");
		int[] sampleSizeForStrati = new int[this.stratiBuilder.length];
		int numSamplesTotal = 0;
		List<Integer> fillupStrati = new ArrayList<>();
		double totalInputSize = this.getInput().size();
		for (int i = 0; i < this.stratiBuilder.length; i++) {
			if (this.stratiBuilder[i].currentSizeOfTarget() < 0) {
				throw new IllegalStateException("Builder for stratum " + i + " has a negative current target size: " +  this.stratiBuilder[i].currentSizeOfTarget());
			}
			int totalNumberOfElementsInStratum = this.stratiBuilder[i].currentSizeOfTarget();
			sampleSizeForStrati[i] = (int)Math.floor(totalNumberOfElementsInStratum * (this.sampleSize / totalInputSize));
			if (sampleSizeForStrati[i] < 0) {
				throw new IllegalStateException("Determined negative stratum size " + sampleSizeForStrati[i] + " for " + i + "-th stratum.");
			}
			numSamplesTotal += sampleSizeForStrati[i];
			fillupStrati.add(i);
		}
		while (numSamplesTotal < this.sampleSize) {
			Collections.shuffle(fillupStrati, this.random);
			int indexForNextFillUp = fillupStrati.remove(0);
			sampleSizeForStrati[indexForNextFillUp] ++;
			numSamplesTotal ++;
		}
		if (numSamplesTotal != this.sampleSize) {
			throw new IllegalStateException("Number of samples is " + numSamplesTotal + " where it should be " + this.sampleSize);
		}
		int stratiSumCheck = 0;
		for (int i = 0; i < this.stratiBuilder.length; i++) {
			stratiSumCheck += sampleSizeForStrati[i];
		}
		if (stratiSumCheck != this.sampleSize) {
			throw new IllegalStateException("The total number of samples assigned within the strati is " + stratiSumCheck + ", but it should be " + this.sampleSize + ".");
		}

		/* conduct a Simple Random Sampling for each stratum */
		DatasetDeriver<D> sampleDeriver = new DatasetDeriver<>(this.getInput());
		for (int i = 0; i < this.stratiBuilder.length; i++) {
			final DatasetDeriver<D> stratumBuilder = this.stratiBuilder[i];
			D stratum = stratumBuilder.build();
			if (stratum.isEmpty()) {
				this.logger.warn("{}-th stratum is empty!", i);
			}
			else if (sampleSizeForStrati[i] == 0) {
				this.logger.warn("No samples for stratum {}", i);
			}
			else if (sampleSizeForStrati[i] == stratum.size()) {
				sampleDeriver.addIndices(stratumBuilder.getIndicesOfNewInstancesInOriginalDataset()); // add the complete stratum
			}
			else {
				SimpleRandomSampling<D> simpleRandomSampling = new SimpleRandomSampling<>(this.random, stratum);
				simpleRandomSampling.setSampleSize(sampleSizeForStrati[i]);
				this.logger.info("Setting sample size for {}-th stratus to {}", i, sampleSizeForStrati[i]);
				try {
					this.logger.debug("Calling SimpleRandomSampling");
					simpleRandomSampling.call();
					this.logger.debug("SimpleRandomSampling finished");
				} catch (Exception e) {
					this.logger.error("Unexpected exception during simple random sampling!", e);
				}
				if (simpleRandomSampling.getChosenIndices().size() != sampleSizeForStrati[i]) {
					throw new IllegalStateException("Number of samples drawn for stratum " + i + " is " + simpleRandomSampling.getChosenIndices().size() + ", but it should be " + sampleSizeForStrati[i]);
				}
				sampleDeriver.addIndices(stratumBuilder.getIndicesOfNewInstancesInOriginalDataset(simpleRandomSampling.getChosenIndices())); // this is MUCH faster than adding the instances
			}
		}
		if (sampleDeriver.currentSizeOfTarget() != this.sampleSize) {
			throw new IllegalStateException("The deriver says that the target has " + sampleDeriver.currentSizeOfTarget() + " elements, but it should have been configured for " + this.sampleSize);
		}
		this.logger.info("Strati sub-samples completed, building the final sample and shuffling it.");
		this.sample = sampleDeriver.build();
		if (this.sample.size() != numSamplesTotal) {
			throw new IllegalStateException("The sample deriver has produced a sample with " + this.sample.size() + " elements while it should have " + numSamplesTotal);
		}
		Collections.shuffle(this.sample, this.random); // up to here, instances have been ordered by their class. We now mix instances of the classes again.
		this.logger.info("Overall stratified shuffled sample completed.");
	}

	@Override
	public void setLoggerName(final String loggername) {
		this.logger = LoggerFactory.getLogger(loggername);
		if (this.stratiAssigner instanceof ILoggingCustomizable) {
			((ILoggingCustomizable) this.stratiAssigner).setLoggerName(loggername + ".assigner");
		}
		if (this.stratiAmountSelector instanceof ILoggingCustomizable) {
			if (this.stratiAmountSelector != this.stratiAssigner) {
				((ILoggingCustomizable) this.stratiAmountSelector).setLoggerName(loggername + ".stratiamountselector");
			} else {
				this.logger.info("Strati assigner and amount selector are the same object. Using .assigner for logging.");
			}
		}
	}

	@Override
	public String getLoggerName() {
		return this.logger.getName();
	}
}
