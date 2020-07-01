package ai.libs.mlplan.examples.multiclass.weka;

import java.io.File;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.api4.java.ai.ml.core.evaluation.execution.ILearnerRunReport;
import org.api4.java.algorithm.Timeout;

import ai.libs.hasco.gui.civiewplugin.TFDNodeAsCIViewInfoGenerator;
import ai.libs.hasco.gui.statsplugin.HASCOModelStatisticsPlugin;
import ai.libs.hasco.gui.statsplugin.HASCOSolutionCandidateRepresenter;
import ai.libs.jaicore.graphvisualizer.events.recorder.AlgorithmEventHistory;
import ai.libs.jaicore.graphvisualizer.events.recorder.AlgorithmEventHistorySerializer;
import ai.libs.jaicore.graphvisualizer.plugin.graphview.GraphViewPlugin;
import ai.libs.jaicore.graphvisualizer.plugin.nodeinfo.NodeInfoGUIPlugin;
import ai.libs.jaicore.graphvisualizer.plugin.solutionperformanceplotter.SolutionPerformanceTimelinePlugin;
import ai.libs.jaicore.graphvisualizer.window.AlgorithmVisualizationWindow;
import ai.libs.jaicore.ml.classification.loss.dataset.EClassificationPerformanceMeasure;
import ai.libs.jaicore.ml.core.dataset.serialization.ArffDatasetAdapter;
import ai.libs.jaicore.ml.core.evaluation.evaluator.SupervisedLearnerExecutor;
import ai.libs.jaicore.ml.core.filter.SplitterUtil;
import ai.libs.jaicore.ml.weka.classification.learner.IWekaClassifier;
import ai.libs.jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNodeInfoGenerator;
import ai.libs.jaicore.search.gui.plugins.rolloutboxplots.SearchRolloutBoxplotPlugin;
import ai.libs.jaicore.search.gui.plugins.rollouthistograms.SearchRolloutHistogramPlugin;
import ai.libs.jaicore.search.model.travesaltree.JaicoreNodeInfoGenerator;
import ai.libs.mlplan.core.MLPlan;
import ai.libs.mlplan.gui.outofsampleplots.OutOfSampleErrorPlotPlugin;
import ai.libs.mlplan.multiclass.wekamlplan.MLPlanWekaBuilder;

public class MLPlanGraphVisualizationExample {
	public static void main(final String[] args) throws Exception {

		// ILabeledDataset<?> ds = OpenMLDatasetReader.deserializeDataset(346);
		File datasetFile = new File("data/11.arff");
		System.out.println(datasetFile.getAbsolutePath());

		ILabeledDataset<?> ds = ArffDatasetAdapter.readDataset(datasetFile);

		List<ILabeledDataset<?>> split = SplitterUtil.getLabelStratifiedTrainTestSplit(ds, new Random(1), .7);

		/* initialize mlplan, and let it run for 1 hour */
		MLPlanWekaBuilder mlplanBuilder = new MLPlanWekaBuilder().withNumCpus(2).withTimeOut(new Timeout(120, TimeUnit.SECONDS)).withCandidateEvaluationTimeOut(new Timeout(30, TimeUnit.SECONDS))
				.withNodeEvaluationTimeOut(new Timeout(900, TimeUnit.SECONDS)).withDataset(split.get(0));
		MLPlan<IWekaClassifier> mlplan = mlplanBuilder.build();

		/* create visualization */
		AlgorithmVisualizationWindow window = new AlgorithmVisualizationWindow(mlplan);
		window.withMainPlugin(new GraphViewPlugin());
		window.withPlugin(new NodeInfoGUIPlugin(new JaicoreNodeInfoGenerator<>(new TFDNodeInfoGenerator())), new SearchRolloutHistogramPlugin(), new NodeInfoGUIPlugin(new TFDNodeAsCIViewInfoGenerator(mlplanBuilder.getComponents())),
				new SolutionPerformanceTimelinePlugin(new HASCOSolutionCandidateRepresenter()), new HASCOModelStatisticsPlugin(), new OutOfSampleErrorPlotPlugin(split.get(0), split.get(1)), new SearchRolloutBoxplotPlugin());

		try {
			long start = System.currentTimeMillis();
			IWekaClassifier optimizedClassifier = mlplan.call();
			long trainTime = (int) (System.currentTimeMillis() - start) / 1000;
			System.out.println("Finished build of the classifier. Training time was " + trainTime + "s.");

			/* evaluate solution produced by mlplan */
			SupervisedLearnerExecutor executor = new SupervisedLearnerExecutor();
			ILearnerRunReport report = executor.execute(optimizedClassifier, split.get(1));
			System.out.println("Error Rate of the solution produced by ML-Plan: " + EClassificationPerformanceMeasure.ERRORRATE.loss(report.getPredictionDiffList()));
		} catch (NoSuchElementException e) {
			System.out.println("Building the classifier failed: " + e.getMessage());
		}

		AlgorithmEventHistory history = window.getAlgorithmEventHistory();
		AlgorithmEventHistorySerializer serializer = new AlgorithmEventHistorySerializer();
		String s = serializer.serializeAlgorithmEventHistory(history);
		// Files.write(s, new File("data/keks.txt"), StandardCharsets.UTF_8);

	}

	// public static void main(final String[] args) throws Exception {
	// AlgorithmEventHistorySerializer serializer = new AlgorithmEventHistorySerializer();
	// AlgorithmEventHistory history = serializer.deserializeAlgorithmEventHistory(new File("data/keks.txt"));
	//
	// MLPlanWekaBuilder mlplanBuilder = new MLPlanWekaBuilder().withNumCpus(2).withTimeOut(new Timeout(120, TimeUnit.SECONDS)).withCandidateEvaluationTimeOut(new Timeout(30, TimeUnit.SECONDS))
	// .withNodeEvaluationTimeOut(new Timeout(900, TimeUnit.SECONDS));
	//
	// AlgorithmVisualizationWindow window = new AlgorithmVisualizationWindow(history);
	// window.withMainPlugin(new GraphViewPlugin());
	// window.withPlugin(new NodeInfoGUIPlugin(new JaicoreNodeInfoGenerator<>(new TFDNodeInfoGenerator())), new SearchRolloutHistogramPlugin(), new NodeInfoGUIPlugin(new TFDNodeAsCIViewInfoGenerator(mlplanBuilder.getComponents())),
	// new SolutionPerformanceTimelinePlugin(new HASCOSolutionCandidateRepresenter()));
	// }
}
