package ai.libs.jaicore.ml.evaluation.evaluators.weka.factory;

import ai.libs.jaicore.ml.core.dataset.IInstance;
import ai.libs.jaicore.ml.core.dataset.sampling.inmemory.ASamplingAlgorithm;
import ai.libs.jaicore.ml.core.dataset.sampling.inmemory.factories.interfaces.ISamplingAlgorithmFactory;
import ai.libs.jaicore.ml.core.dataset.weka.WekaInstances;
import ai.libs.jaicore.ml.evaluation.evaluators.weka.IClassifierEvaluator;
import ai.libs.jaicore.ml.evaluation.evaluators.weka.LearningCurveExtrapolationEvaluator;
import ai.libs.jaicore.ml.learningcurve.extrapolation.LearningCurveExtrapolationMethod;
import weka.core.Instances;

public class LearningCurveExtrapolationEvaluatorFactory implements IClassifierEvaluatorFactory {

	private int[] anchorpoints;
	private ISamplingAlgorithmFactory<IInstance, ? extends ASamplingAlgorithm<IInstance>> subsamplingAlgorithmFactory;
	private double trainSplitForAnchorpointsMeasurement;
	private LearningCurveExtrapolationMethod extrapolationMethod;

	public LearningCurveExtrapolationEvaluatorFactory(final int[] anchorpoints, final ISamplingAlgorithmFactory<IInstance, ? extends ASamplingAlgorithm<IInstance>> subsamplingAlgorithmFactory, final double trainSplitForAnchorpointsMeasurement,
			final LearningCurveExtrapolationMethod extrapolationMethod) {
		super();
		this.anchorpoints = anchorpoints;
		this.subsamplingAlgorithmFactory = subsamplingAlgorithmFactory;
		this.trainSplitForAnchorpointsMeasurement = trainSplitForAnchorpointsMeasurement;
		this.extrapolationMethod = extrapolationMethod;
	}

	@Override
	public IClassifierEvaluator getIClassifierEvaluator(final Instances dataset, final long seed) {
		return new LearningCurveExtrapolationEvaluator(this.anchorpoints, this.subsamplingAlgorithmFactory, new WekaInstances(dataset), this.trainSplitForAnchorpointsMeasurement, this.extrapolationMethod, seed);
	}
}