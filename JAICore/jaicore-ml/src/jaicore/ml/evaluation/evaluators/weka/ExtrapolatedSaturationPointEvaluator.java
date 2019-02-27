package jaicore.ml.evaluation.evaluators.weka;

import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.basic.algorithm.exceptions.ObjectEvaluationFailedException;
import jaicore.ml.core.dataset.IDataset;
import jaicore.ml.core.dataset.IInstance;
import jaicore.ml.core.dataset.sampling.inmemory.ASamplingAlgorithm;
import jaicore.ml.core.dataset.sampling.inmemory.SubsamplingMethod;
import jaicore.ml.core.dataset.weka.WekaInstancesUtil;
import jaicore.ml.interfaces.LearningCurve;
import jaicore.ml.learningcurve.extrapolation.LearningCurveExtrapolationMethod;
import jaicore.ml.learningcurve.extrapolation.LearningCurveExtrapolator;
import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * For the classifier a learning curve will be extrapolated with a given set of
 * anchorpoints. This learning curve can predict a saturation point with a
 * tolerance epsilon. When a subsample is drawn at this saturation point it is
 * the optimal trade-off between a fast training (therefore fast classifier
 * evaluation) and dataset representability (therefore evaluation result
 * expressiveness).
 * 
 * @author Lukas Brandt
 */
public class ExtrapolatedSaturationPointEvaluator implements IClassifierEvaluator {

	private final static Logger logger = LoggerFactory.getLogger(ExtrapolatedSaturationPointEvaluator.class);

	// Configuration for the learning curve extrapolator.
	private int[] anchorpoints;
	private SubsamplingMethod subsamplingMethod;
	private IDataset<IInstance> train;
	private double trainSplitForAnchorpointsMeasurement;
	private LearningCurveExtrapolationMethod extrapolationMethod;
	private long seed;

	// Configuration for the measurement at the saturation point.
	private double epsilon;
	private IDataset<IInstance> test;

	/**
	 * Create a classifier evaluator with an accuracy measurement at the
	 * extrapolated learning curves saturation point.
	 * 
	 * @param anchorpoints                         Anchorpoints for the learning
	 *                                             curve extrapolation.
	 * @param subsamplingMethod                    Subsampling method to create
	 *                                             samples at the given
	 *                                             anchorpoints.
	 * @param train                                Dataset predict the learning
	 *                                             curve with and where the
	 *                                             subsample for the measurement is
	 *                                             drawn from.
	 * @param trainSplitForAnchorpointsMeasurement Ratio to split the subsamples at
	 *                                             the anchorpoints into train and
	 *                                             test.
	 * @param extrapolationMethod                  Method to extrapolate a learning
	 *                                             curve from the accuracy
	 *                                             measurements at the anchorpoints.
	 * @param seed                                 Random seed.
	 * @param epsilon                              Tolerance value for calculating
	 *                                             the saturation point.
	 * @param test                                 Test dataset to measure the
	 *                                             accuracy.
	 */
	public ExtrapolatedSaturationPointEvaluator(int[] anchorpoints, SubsamplingMethod subsamplingMethod,
			IDataset<IInstance> train, double trainSplitForAnchorpointsMeasurement,
			LearningCurveExtrapolationMethod extrapolationMethod, long seed, double epsilon, IDataset<IInstance> test) {
		super();
		this.anchorpoints = anchorpoints;
		this.subsamplingMethod = subsamplingMethod;
		this.train = train;
		this.trainSplitForAnchorpointsMeasurement = trainSplitForAnchorpointsMeasurement;
		this.extrapolationMethod = extrapolationMethod;
		this.seed = seed;
		this.epsilon = epsilon;
		this.test = test;
	}

	@Override
	public Double evaluate(Classifier classifier)
			throws TimeoutException, InterruptedException, ObjectEvaluationFailedException {
		// Create the learning curve extrapolator with the given configuration.
		LearningCurveExtrapolator extrapolator = new LearningCurveExtrapolator(this.extrapolationMethod, classifier,
				train, this.trainSplitForAnchorpointsMeasurement, this.subsamplingMethod.getSubsampler(this.seed),
				this.seed);
		try {
			// Create the extrapolator and calculate sample size of the saturation point
			// with the given epsilon
			LearningCurve learningCurve = extrapolator.extrapolateLearningCurve(this.anchorpoints);
			int optimalSampleSize = Math.min(this.train.size(), (int) learningCurve.getSaturationPoint(this.epsilon));

			// Create a subsample with this size
			ASamplingAlgorithm<IInstance> samplingAlgorithm = this.subsamplingMethod.getSubsampler(this.seed);
			samplingAlgorithm.setInput(this.train);
			samplingAlgorithm.setSampleSize(optimalSampleSize);
			IDataset<IInstance> saturationPointTrainSet = samplingAlgorithm.call();
			Instances saturationPointInstances = WekaInstancesUtil.datasetToWekaInstances(saturationPointTrainSet);

			// Measure the accuracy with this subsample
			Instances testInstances = WekaInstancesUtil.datasetToWekaInstances(this.test);
			FixedSplitClassifierEvaluator evaluator = new FixedSplitClassifierEvaluator(saturationPointInstances,
					testInstances);
			return evaluator.evaluate(classifier);
		} catch (Exception e) {
			logger.warn("Evaluation of classifier failed due Exception {} with message {}. Returning null.",
					e.getClass().getName(), e.getMessage());
			return null;
		}
	}

}
