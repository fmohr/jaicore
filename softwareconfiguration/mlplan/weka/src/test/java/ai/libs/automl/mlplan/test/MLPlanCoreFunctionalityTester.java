package ai.libs.automl.mlplan.test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.api4.java.algorithm.IAlgorithm;
import org.api4.java.algorithm.TimeOut;

import ai.libs.automl.AutoMLAlgorithmCoreFunctionalityTester;
import ai.libs.jaicore.ml.weka.classification.learner.IWekaClassifier;
import ai.libs.mlplan.core.MLPlan;
import ai.libs.mlplan.multiclass.wekamlplan.MLPlanWekaBuilder;

public class MLPlanCoreFunctionalityTester extends AutoMLAlgorithmCoreFunctionalityTester {

	@Override
	public IAlgorithm<ILabeledDataset<?>, IWekaClassifier> getAutoMLAlgorithm(final ILabeledDataset<?> data) {
		try {
			MLPlanWekaBuilder builder = new MLPlanWekaBuilder().withTinyWekaSearchSpace();
			builder.withNodeEvaluationTimeOut(new TimeOut(10, TimeUnit.SECONDS));
			builder.withCandidateEvaluationTimeOut(new TimeOut(5, TimeUnit.SECONDS));
			builder.withNumCpus(1);
			builder.withTimeOut(new TimeOut(5, TimeUnit.SECONDS));
			MLPlan<IWekaClassifier> mlplan = builder.withDataset(data).build();
			mlplan.setRandomSeed(1);
			mlplan.setPortionOfDataForPhase2(0f);
			mlplan.setLoggerName(TESTEDALGORITHM_LOGGERNAME);
			return mlplan;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
