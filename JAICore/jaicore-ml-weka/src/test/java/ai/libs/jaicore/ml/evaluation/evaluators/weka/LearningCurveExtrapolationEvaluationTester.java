package ai.libs.jaicore.ml.evaluation.evaluators.weka;

import java.io.File;

import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.junit.Assert;
import org.junit.Test;
import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.DataSetDescription;

import ai.libs.jaicore.ml.core.evaluation.evaluator.LearningCurveExtrapolationEvaluator;
import ai.libs.jaicore.ml.core.filter.sampling.inmemory.factories.SystematicSamplingFactory;
import ai.libs.jaicore.ml.functionprediction.learner.learningcurveextrapolation.ipl.InversePowerLawExtrapolationMethod;
import ai.libs.jaicore.ml.weka.dataset.IWekaInstance;
import ai.libs.jaicore.ml.weka.dataset.IWekaInstances;
import ai.libs.jaicore.ml.weka.dataset.WekaInstances;
import weka.classifiers.functions.SMO;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class LearningCurveExtrapolationEvaluationTester {

	@Test
	public void testClassifierEvaluationWithLearningCurveExtrapolation() throws Exception {
		// Load dataset from OpenML
		Instances dataset = null;
		OpenmlConnector client = new OpenmlConnector();
		DataSetDescription description = client.dataGet(42);
		File file = client.datasetGet(description);
		DataSource source = new DataSource(file.getCanonicalPath());
		dataset = source.getDataSet();
		dataset.setClassIndex(dataset.numAttributes() - 1);
		Attribute targetAttribute = dataset.attribute(description.getDefault_target_attribute());
		dataset.setClassIndex(targetAttribute.index());

		// Test classifier evaluation by learning curve extrapolation
		LearningCurveExtrapolationEvaluator evaluator = new LearningCurveExtrapolationEvaluator(new int[] { 8, 16, 64, 128 }, new SystematicSamplingFactory<IWekaInstance, IWekaInstances>(), (ILabeledDataset<?>) new WekaInstances(dataset),
				0.8d, new InversePowerLawExtrapolationMethod(), 123l);
		double evaluationResult = evaluator.evaluate(new SMO());
		Assert.assertTrue(evaluationResult > 0 && evaluationResult <= 100);
	}

}
