package de.upb.crc901.mlplan.bigdata;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.upb.crc901.mlplan.core.MLPlan;
import de.upb.crc901.mlplan.core.MLPlanBuilder;
import jaicore.basic.ILoggingCustomizable;
import jaicore.basic.TimeOut;
import jaicore.basic.algorithm.AAlgorithm;
import jaicore.basic.algorithm.AlgorithmExecutionCanceledException;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.basic.algorithm.exceptions.AlgorithmException;
import jaicore.basic.algorithm.exceptions.AlgorithmTimeoutedException;
import jaicore.ml.core.dataset.sampling.infiles.ReservoirSampling;
import jaicore.ml.core.dataset.sampling.inmemory.factories.SimpleRandomSamplingFactory;
import jaicore.ml.learningcurve.extrapolation.ipl.InversePowerLawExtrapolationMethod;
import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * This is a version of ML-Plan that tries to cope with medium sized data in the sense of big files.
 * That is, the data is still enough to be organized in a single file such that no streaming is required.
 * The data is, however, in general too large to be entirely loaded into memory.
 *
 * We use simple sampling to create a relatively small subset of the data, then run info gain, and then ML-Plan with
 * learning curve prediction.
 *
 * @author fmohr
 *
 */
public class MLPlan4BigFileInput extends AAlgorithm<File, Classifier> implements ILoggingCustomizable {

	private Logger logger = LoggerFactory.getLogger(MLPlan4BigFileInput.class);

	private MLPlan mlplan;

	public MLPlan4BigFileInput(final File input) {
		super(input);
	}

	@Override
	public AlgorithmEvent nextWithException() throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmTimeoutedException, AlgorithmException {
		switch (this.getState()) {
		case created:

			/* read in down-sampled input data */
			ReservoirSampling sampler = new ReservoirSampling(new Random(0), this.getInput());
			File downsampledFile = new File("testrsc/sampled/" + this.getInput().getName());
			Instances data;
			try {
				this.logger.info("Starting sampler {} for data source {}", sampler.getClass().getName(), this.getInput().getAbsolutePath());
				sampler.setOutputFileName(downsampledFile.getAbsolutePath());
				sampler.setSampleSize(1000);
				sampler.call();
				this.logger.info("Reduced dataset size to {}", 1000);
				data = new Instances(new FileReader(downsampledFile));
				data.setClassIndex(data.numAttributes() - 1);
				this.logger.info("Loaded {}x{} dataset", data.size(), data.numAttributes());
			} catch (IOException e) {
				throw new AlgorithmException(e, "Could not create a sub-sample of the given data.");
			}

			/* reduce dimensionality */
			// Collection<List<String>> preprocessors = WekaUtil.getAdmissibleSearcherEvaluatorCombinationsForAttributeSelection();
			// Map<String, int[]> selectedAttributes = new HashMap<>();
			// preprocessors.parallelStream().forEach(l -> {
			// try {
			// AttributeSelection p = new AttributeSelection();
			// p.setSearch(ASSearch.forName(l.get(0), new String[] {}));
			// p.setEvaluator(ASEvaluation.forName(l.get(1), new String[] {}));
			// this.logger.info("Starting feature selection with {}", l);
			// p.SelectAttributes(data);
			// selectedAttributes.put(p.getClass().getName(), p.selectedAttributes());
			// this.logger.info("Finished feature selection with {}. Selected {}/{} attributes", l, p.selectedAttributes().length, data.numAttributes());
			// } catch (Exception e) {
			// e.printStackTrace();
			// }
			// });
			// selectedAttributes.entrySet().forEach(e -> {
			// this.logger.info("Attributes selected by {}: {}", e.getKey(), e.getValue());
			// });
			// System.exit(0);

			// try {
			// AttributeSelection as = new AttributeSelection();
			// as.setSearch(new GreedyStepwise());
			// as.setEvaluator(new CfsSubsetEval());
			// as.SelectAttributes(data);
			// int featuresBefore = data.numAttributes();
			// data = as.reduceDimensionality(data);
			// this.logger.info("Reduced number of features from {} to {}. Dataset is now {}x{}", featuresBefore, data.numAttributes(), data.size(), data.numAttributes());
			// }
			// catch (Exception e) {
			// throw new AlgorithmException(e, "Could not reduce dimensionality of down-sampled data");
			// }

			/* apply ML-Plan to reduced data */
			MLPlanBuilder builder;
			try {
				builder = new MLPlanBuilder().withAutoWEKAConfiguration().withRandomCompletionBasedBestFirstSearch();
				builder.withLearningCurveExtrapolationEvaluation(new int[] { 8, 16, 64, 128 }, new SimpleRandomSamplingFactory<>(), .7, new InversePowerLawExtrapolationMethod());
				builder.withTimeoutForNodeEvaluation(new TimeOut(15, TimeUnit.MINUTES));
				builder.withTimeoutForSingleSolutionEvaluation(new TimeOut(5, TimeUnit.MINUTES));
				this.mlplan = new MLPlan(builder, data);
				this.mlplan.setLoggerName(this.getLoggerName() + ".mlplan");
				this.mlplan.setTimeout(new TimeOut(5, TimeUnit.MINUTES));
				this.mlplan.setNumCPUs(8);
				this.logger.info("ML-Plan initialized, activation finished!");
				return this.activate();
			} catch (IOException e) {
				throw new AlgorithmException(e, "Could not initialize ML-Plan!");
			}
		case active:
			this.logger.info("Starting ML-Plan.");
			this.mlplan.call();
			this.logger.info("ML-Plan has finished. Selected classifier is {} with observed internal performance {}. Now training on full data", this.mlplan.getSelectedClassifier(), this.mlplan.getInternalValidationErrorOfSelectedClassifier());
			try {
				Instances completeData = new Instances(new FileReader(this.getInput()));
				completeData.setClassIndex(completeData.numAttributes() - 1);
				this.mlplan.getSelectedClassifier().buildClassifier(completeData);
			} catch (Exception e) {
				throw new AlgorithmException(e, "Could not train the final classifier with the full data.");
			}
			return this.terminate();
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public Classifier call() throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmTimeoutedException, AlgorithmException {
		while (this.hasNext()) {
			this.next();
		}
		return this.mlplan.getSelectedClassifier();
	}

	@Override
	public void setLoggerName(final String loggerName) {
		this.logger = LoggerFactory.getLogger(loggerName);
	}

	@Override
	public String getLoggerName() {
		return this.logger.getName();
	}
}
