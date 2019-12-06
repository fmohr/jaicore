package ai.libs.jaicore.ml.core.evaluation.evaluator;

import java.util.ArrayList;
import java.util.List;

import org.api4.java.ai.ml.classification.IClassifierEvaluator;
import org.api4.java.ai.ml.core.dataset.splitter.SplitFailedException;
import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.api4.java.ai.ml.core.dataset.supervised.ILabeledInstance;
import org.api4.java.ai.ml.core.evaluation.execution.IDatasetSplitSet;
import org.api4.java.ai.ml.core.evaluation.execution.IFixedDatasetSplitSetGenerator;
import org.api4.java.ai.ml.core.evaluation.execution.ILearnerRunReport;
import org.api4.java.ai.ml.core.evaluation.execution.ISupervisedLearnerMetric;
import org.api4.java.ai.ml.core.evaluation.execution.LearnerExecutionFailedException;
import org.api4.java.ai.ml.core.learner.ISupervisedLearner;
import org.api4.java.common.attributedobjects.ObjectEvaluationFailedException;
import org.api4.java.common.control.ILoggingCustomizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainPredictionBasedClassifierEvaluator implements IClassifierEvaluator, ILoggingCustomizable {

	private Logger logger = LoggerFactory.getLogger(TrainPredictionBasedClassifierEvaluator.class);
	private final IFixedDatasetSplitSetGenerator<ILabeledDataset<? extends ILabeledInstance>> splitGenerator;
	private final SupervisedLearnerExecutor<ILabeledDataset<? extends ILabeledInstance>> executor = new SupervisedLearnerExecutor<>();
	private final ISupervisedLearnerMetric metric;

	public TrainPredictionBasedClassifierEvaluator(final IFixedDatasetSplitSetGenerator<ILabeledDataset<?>> splitGenerator, final ISupervisedLearnerMetric metric) {
		super();
		this.splitGenerator = splitGenerator;
		this.metric = metric;
	}

	@Override
	public Double evaluate(final ISupervisedLearner<ILabeledInstance, ILabeledDataset<? extends ILabeledInstance>> learner) throws InterruptedException, ObjectEvaluationFailedException {
		try {
			this.logger.info("Splitting the given data into two folds.");
			IDatasetSplitSet<ILabeledDataset<? extends ILabeledInstance>> splitSet = this.splitGenerator.nextSplitSet();
			if (splitSet.getNumberOfFoldsPerSplit() != 2) {
				throw new IllegalStateException("Number of folds for each split should be 2 but is " + splitSet.getNumberOfFoldsPerSplit() + "! Split generator: " + this.splitGenerator);
			}
			int n = splitSet.getNumberOfSplits();
			List<ILearnerRunReport> reports = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				List<ILabeledDataset<? extends ILabeledInstance>> folds = splitSet.getFolds(i);
				this.logger.debug("Executing learner on folds of sizes {} (train) and {} (test).", folds.get(0).size(), folds.get(1).size());
				ILearnerRunReport report = this.executor.execute(learner, folds.get(0), folds.get(1));
				reports.add(report);
				if (this.logger.isDebugEnabled()) {
					List<?> gt = report.getPredictionDiffList().getGroundTruthAsList();
					List<?> pr = report.getPredictionDiffList().getPredictionsAsList();
					int m = gt.size();
					int mistakes = 0;
					for (int j = 0; j < m; j++) {
						if (!pr.get(j).equals(gt.get(j))) {
							mistakes ++;
						}
					}
					if (m - mistakes == 0) {
						this.logger.warn("0 correct predictions seems suspicious. Here are the vectors: \n\tGround truth: {}\n\tPredictions: {}", report.getPredictionDiffList().getGroundTruthAsList(), report.getPredictionDiffList().getPredictionsAsList());
					}
					this.logger.debug("Execution completed. Classifier predicted {}/{} test instances correctly.", (m-mistakes), m);
				}
			}
			double score = this.metric.evaluate(reports);
			this.logger.info("Computed value for metric {} of {} executions. Metric value is: {}", this.metric, n, score);
			return score;
		} catch (LearnerExecutionFailedException | SplitFailedException e) {
			throw new ObjectEvaluationFailedException(e);
		}
	}

	@Override
	public String getLoggerName() {
		return this.logger.getName();
	}

	@Override
	public void setLoggerName(final String name) {
		this.logger = LoggerFactory.getLogger(name);
		if (this.splitGenerator instanceof ILoggingCustomizable) {
			((ILoggingCustomizable) this.splitGenerator).setLoggerName(name + ".splitgen");
			this.logger.info("Setting logger of split generator {} to {}", this.splitGenerator.getClass().getName(), name + ".splitgen");
		}
		else {
			this.logger.info("Split generator {} is not configurable for logging, so not configuring it.", this.splitGenerator.getClass().getName());
		}
		if (this.executor instanceof ILoggingCustomizable) {
			((ILoggingCustomizable) this.executor).setLoggerName(name + ".executor");
			this.logger.info("Setting logger of learner executor {} to {}", this.executor.getClass().getName(), name + ".executor");
		}
		else {
			this.logger.info("Learner executor {} is not configurable for logging, so not configuring it.", this.executor.getClass().getName());
		}
	}
}
