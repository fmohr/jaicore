package autofe.util;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import autofe.algorithm.hasco.evaluation.AbstractHASCOFEEvaluator;
import autofe.algorithm.hasco.evaluation.AbstractHASCOFENodeEvaluator;
import autofe.algorithm.hasco.filter.meta.FilterPipeline;
import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import de.upb.crc901.mlplan.multiclass.weka.MLPlanWekaClassifier;
import fantail.core.Correlation;
import jaicore.ml.WekaUtil;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.search.structure.core.Node;
import weka.attributeSelection.ReliefFAttributeEval;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LDA;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.lazy.IBk;
import weka.clusterers.ClusterEvaluation;
import weka.clusterers.FilteredClusterer;
import weka.clusterers.SimpleKMeans;
import weka.core.EuclideanDistance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Nystroem;
import weka.filters.unsupervised.attribute.Remove;

/**
 * Utility functions used for node and object evaluation classes.
 *
 * @author Julian Lienen
 *
 */
public final class EvaluationUtils {
	private static final Logger logger = LoggerFactory.getLogger(EvaluationUtils.class);

	private static double DOUBLE_ZERO_PREC = 0.0001;

	private EvaluationUtils() {
		// Utility class
	}

	/**
	 * Performs clustering using the given FilterPipeline <code>pipeline</code> and
	 * the data set <code>data</code>.
	 *
	 * @param pipeline
	 *            FilterPipeline which transforms the given data set
	 * @param data
	 *            DataSet object which is transformed by the pipeline
	 * @return Returns 1 - accuracy
	 * @throws Exception
	 */
	public static double performClustering(final FilterPipeline pipeline, final DataSet data) throws Exception {
		if (pipeline == null || data == null) {
			throw new IllegalArgumentException("Parameters 'pipeline' (" + (pipeline == null) + ") and 'data' ("
					+ (data == null) + ") must not be null!");
		}

		logger.debug("Applying and evaluating pipeline " + pipeline.toString());

		// Load vector class due to load failure exception
		@SuppressWarnings("unused")
		no.uib.cipr.matrix.Vector vector;

		DataSet dataSet = pipeline.applyFilter(data, true);
		System.out.println("Number of attributes: " + dataSet.getInstances().numAttributes());

		logger.debug("Applied pipeline.");

		return performClustering(dataSet.getInstances());
	}

	public static double performClustering(final Instances insts) throws Exception {
		logger.debug("Starting cluster evaluation...");

		FilteredClusterer clusterer = new FilteredClusterer();

		Remove filter = new Remove();
		filter.setAttributeIndices("" + (insts.classIndex() + 1));
		filter.setInputFormat(insts);
		Instances removedClassInstances = Filter.useFilter(insts, filter);

		// TODO: Kernel
		// // Nystroem kernelFilter = new Nystroem();
		// // Initialize kernel? (using data, cache size 250007, gamma 0.01)? =>
		// // Defaults
		//
		// // kernelFilter.setKernel(new RBFKernel(insts, 250007, 0.01)); // insts,
		// 250007,
		// // 0.01
		// // clusterer.setFilter(kernelFilter);
		((SimpleKMeans) clusterer.getClusterer())
				.setOptions(new String[] { "-N", String.valueOf(insts.classAttribute().numValues()) });

		// ((SimpleKMeans)
		// clusterer.getClusterer()).setNumClusters(insts.classAttribute().numValues());
		// ((weka.core.EuclideanDistance) ((SimpleKMeans)
		// clusterer.getClusterer()).getDistanceFunction())
		// .setDontNormalize(true);

		clusterer.buildClusterer(removedClassInstances);

		ClusterEvaluation clusterEval = new ClusterEvaluation();
		clusterEval.setClusterer(clusterer);
		clusterEval.evaluateClusterer(insts);

		double acc = predictAccuracy(insts, clusterEval.getClassesToClusters(), clusterEval.getClusterAssignments());

		return acc;
	}

	public static double performKernelClustering(final Instances insts) throws Exception {
		logger.debug("Starting cluster evaluation...");

		// TODO: Kernel

		ExecutorService execService = Executors.newFixedThreadPool(4);
		Future<Double> clustering0 = execService.submit(() -> {
			return performClustering(insts);
		});
		Future<Double> clustering1 = execService.submit(() -> {
			FilteredClusterer clusterer = new FilteredClusterer();

			Remove filter = new Remove();
			filter.setAttributeIndices("" + (insts.classIndex() + 1));
			filter.setInputFormat(insts);

			Instances removedClassInstances = Filter.useFilter(insts, filter);
			Nystroem kernelFilter = new Nystroem();

			kernelFilter.setKernel(new RBFKernel(insts, 250007, 0.01)); // insts,
			clusterer.setFilter(kernelFilter);
			((SimpleKMeans) clusterer.getClusterer())
					.setOptions(new String[] { "-N", String.valueOf(insts.classAttribute().numValues()) });

			clusterer.buildClusterer(removedClassInstances);

			ClusterEvaluation clusterEval = new ClusterEvaluation();
			clusterEval.setClusterer(clusterer);
			clusterEval.evaluateClusterer(insts);

			return predictAccuracy(insts, clusterEval.getClassesToClusters(), clusterEval.getClusterAssignments());
		});
		Future<Double> clustering2 = execService.submit(() -> {
			FilteredClusterer clusterer = new FilteredClusterer();

			Remove filter = new Remove();
			filter.setAttributeIndices("" + (insts.classIndex() + 1));
			filter.setInputFormat(insts);

			Instances removedClassInstances = Filter.useFilter(insts, filter);
			Nystroem kernelFilter = new Nystroem();
			kernelFilter.setKernel(new PolyKernel(insts, 250007, 2, false));

			clusterer.setFilter(kernelFilter);
			((SimpleKMeans) clusterer.getClusterer())
					.setOptions(new String[] { "-N", String.valueOf(insts.classAttribute().numValues()) });

			clusterer.buildClusterer(removedClassInstances);

			ClusterEvaluation clusterEval = new ClusterEvaluation();
			clusterEval.setClusterer(clusterer);
			clusterEval.evaluateClusterer(insts);

			return predictAccuracy(insts, clusterEval.getClassesToClusters(), clusterEval.getClusterAssignments());
		});
		Future<Double> clustering3 = execService.submit(() -> {
			FilteredClusterer clusterer = new FilteredClusterer();

			Remove filter = new Remove();
			filter.setAttributeIndices("" + (insts.classIndex() + 1));
			filter.setInputFormat(insts);

			Instances removedClassInstances = Filter.useFilter(insts, filter);
			Nystroem kernelFilter = new Nystroem();
			kernelFilter.setKernel(new PolyKernel(insts, 250007, 3, false));

			clusterer.setFilter(kernelFilter);
			((SimpleKMeans) clusterer.getClusterer())
					.setOptions(new String[] { "-N", String.valueOf(insts.classAttribute().numValues()) });

			clusterer.buildClusterer(removedClassInstances);

			ClusterEvaluation clusterEval = new ClusterEvaluation();
			clusterEval.setClusterer(clusterer);
			clusterEval.evaluateClusterer(insts);

			return predictAccuracy(insts, clusterEval.getClassesToClusters(), clusterEval.getClusterAssignments());
		});

		execService.shutdown();
		execService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

		return Math.max(Math.max(Math.max(clustering0.get(), clustering1.get()), clustering2.get()), clustering3.get());
	}

	public static double performKernelLDA(final Instances instances) throws Exception {

		List<Instances> split = WekaUtil.getStratifiedSplit(instances, new Random(42), .7f);

		ExecutorService execService = Executors.newFixedThreadPool(4);
		Future<Double> result0 = execService.submit(() -> {
			Instances insts = new Instances(split.get(0));

			try {
				return performLDA(insts);
			} catch (Exception e) {
				logger.warn("Could the following error message in LDA execution (no kernel): " + e.getMessage());
				return 0d;
			}
		});
		Future<Double> result1 = execService.submit(() -> {
			Instances insts = new Instances(split.get(0));

			Nystroem kernelFilter = new Nystroem();
			kernelFilter.setInputFormat(insts);
			kernelFilter.setKernel(new RBFKernel(insts, 250007, 0.01));

			insts = Filter.useFilter(insts, kernelFilter);

			try {
				return performLDA(insts);
			} catch (Exception e) {
				logger.warn("Could the following error message in LDA execution (RBF kernel): " + e.getMessage());
				return 0d;
			}
		});
		Future<Double> result2 = execService.submit(() -> {
			Instances insts = new Instances(split.get(0));

			Nystroem kernelFilter = new Nystroem();
			kernelFilter.setInputFormat(insts);
			kernelFilter.setKernel(new PolyKernel(insts, 250007, 2, false));

			insts = Filter.useFilter(insts, kernelFilter);

			try {
				return performLDA(insts);
			} catch (Exception e) {
				logger.warn("Could the following error message in LDA execution (poly2 kernel): " + e.getMessage());
				return 0d;
			}
		});
		Future<Double> result3 = execService.submit(() -> {
			Instances insts = new Instances(split.get(0));

			Nystroem kernelFilter = new Nystroem();
			kernelFilter.setInputFormat(insts);
			kernelFilter.setKernel(new PolyKernel(insts, 250007, 3, false));

			insts = Filter.useFilter(insts, kernelFilter);

			try {
				return performLDA(insts);
			} catch (Exception e) {
				logger.warn("Could the following error message in LDA execution (poly3 kernel): " + e.getMessage());
				return 0d;
			}
		});

		execService.shutdown();
		execService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

		return Math.max(Math.max(Math.max(result0.get(), result1.get()), result2.get()), result3.get());
	}

	/**
	 * Prediction of the accuracy of the given <code>instances</code>, the mapping
	 * of classes to clusters <code>classesToClusters</code> and the assignments of
	 * the instances to the specific clusters <code>clusterAssignments</code>.
	 *
	 * @param instances
	 *            Instances which were assigned to clusters
	 * @param classesToClusters
	 *            Mapping of clusters to classes
	 * @param clusterAssignments
	 *            Assignments of the instances to the clusters
	 * @return
	 */
	public static double predictAccuracy(final Instances instances, final int[] classesToClusters,
			final double[] clusterAssignments) {
		if (instances.numInstances() != clusterAssignments.length) {
			throw new IllegalArgumentException("Amount of instances must be equal to cluster assignments.");
		}

		double correct = 0;
		double total = 0;
		for (int i = 0; i < instances.numInstances(); i++) {
			total++;
			Instance inst = instances.get(i);
			if (((int) inst.classValue()) == classesToClusters[(int) clusterAssignments[i]]) {
				correct++;
			}
		}
		return correct / total;
	}

	/**
	 * Penalizes high count of attributes (which leads to high run times of
	 * attribute selectors and classifier trainings).
	 *
	 * @param instances
	 * @return
	 */
	public static double calculateAttributeCountPenalty(final Instances instances) {
		// TODO: Which attribute number to use?
		return instances.numAttributes() / 15000;
	}

	public static double calculateCOCOForBatch(final Instances batch) {

		batch.setClassIndex(batch.numAttributes() - 1);

		final int D = batch.numAttributes() - 1;
		final int classes = batch.classAttribute().numValues();

		final double alpha = 10;

		// Calculate centroids
		int[] classCounts = new int[classes];
		INDArray c = Nd4j.zeros(classes, D);
		for (int i = 0; i < classes; i++) {
			for (Instance inst : batch) {
				if (Math.round(inst.classValue()) == i) {
					double[] instValues = Arrays.copyOfRange(inst.toDoubleArray(), 0, inst.toDoubleArray().length - 1);
					c.getRow(i).addiRowVector(Nd4j.create(instValues));
					classCounts[i]++;
				}
			}
		}
		for (int i = 0; i < classes; i++) {
			c.getRow(i).divi(classCounts[i] + 1);
			if (c.getRow(i).norm2Number().doubleValue() >= DOUBLE_ZERO_PREC
					&& c.getRow(i).norm2Number().doubleValue() >= -DOUBLE_ZERO_PREC) {
				c.getRow(i).divi(c.getRow(i).norm2Number());
			}
		}

		double loss = 0;
		for (int i = 0; i < batch.numInstances(); i++) {
			double[] instValues = Arrays.copyOfRange(batch.get(i).toDoubleArray(), 0,
					batch.get(i).toDoubleArray().length - 1);
			INDArray f_i = Nd4j.create(instValues);
			f_i.muli(alpha);
			if (f_i.norm2Number().doubleValue() >= DOUBLE_ZERO_PREC
					&& f_i.norm2Number().doubleValue() >= -DOUBLE_ZERO_PREC) {
				f_i.divi(f_i.norm2Number());
			}

			double lowerSum = 0;
			for (int j = 0; j < classes; j++) {

				// TODO: In paper, the case i != j is NOT excluded!
				// if (i != j) {
				INDArray tmp = f_i.mmul(c.getRow(j).transpose());
				lowerSum += Math.exp(tmp.getDouble(0));
				// }

			}
			INDArray c_k = c.getRow((int) Math.round(batch.get(i).classValue()));
			INDArray result = f_i.mmul(c_k.transpose());

			double expExpr = Math.exp(result.getDouble(0));

			loss += Math.log(expExpr / (lowerSum + 1));

			if (Double.isNaN(loss)) {
				logger.warn("Got NaN value for COCO batch score.");
				break;
			}
		}

		return (-1) * loss;
	}

	private static double calculateCosSim(final INDArray f1, final INDArray f2) {
		return Transforms.cosineSim(f1, f2);
	}

	public static double calculateCOEDForBatch(final Instances batch) {
		batch.setClassIndex(batch.numAttributes() - 1);

		final int D = batch.numAttributes() - 1;
		final int classes = batch.classAttribute().numValues();

		// final double alpha = 10;

		INDArray features = Nd4j.zeros(batch.numInstances(), D);
		INDArray labels = Nd4j.zeros(batch.numInstances(), 1);

		for (int i = 0; i < batch.numInstances(); i++) {
			double[] instValues = Arrays.copyOfRange(batch.get(i).toDoubleArray(), 0,
					batch.get(i).toDoubleArray().length - 1);
			INDArray f_i = Nd4j.create(instValues);
			// org.nd4j.linalg.dataset.DataSet tmpDataSet = new
			// org.nd4j.linalg.dataset.DataSet();
			// tmpDataSet.setFeatures(f_i);
			// dataSet.addRow(tmpDataSet, i);
			features.getRow(i).addiRowVector(f_i);
			labels.putScalar(i, batch.get(i).classValue());
		}
		org.nd4j.linalg.dataset.DataSet dataSet = new org.nd4j.linalg.dataset.DataSet(features, labels);
		dataSet.setFeatures(features);
		NormalizerStandardize scaler = new NormalizerStandardize();
		scaler.fit(dataSet);
		// scaler.transform(dataSet);
		scaler.preProcess(dataSet);

		// Calculate centroids
		int[] classCounts = new int[classes];
		INDArray c = Nd4j.zeros(classes, D);
		for (int i = 0; i < classes; i++) {
			for (Instance inst : batch) {
				if (Math.round(inst.classValue()) == i) {
					// double[] instValues = Arrays.copyOfRange(inst.toDoubleArray(), 0,
					// inst.toDoubleArray().length - 1);
					c.getRow(i).addiRowVector(dataSet.get(i).getFeatures());
					classCounts[i]++;
				}
			}
		}
		for (int i = 0; i < classes; i++) {
			c.getRow(i).divi(classCounts[i] + 1);
			// c.getRow(i).divi(c.getRow(i).norm2Number());
		}

		double loss = 0;
		for (int i = 0; i < batch.numInstances(); i++) {
			// double[] instValues = Arrays.copyOfRange(batch.get(i).toDoubleArray(), 0,
			// batch.get(i).toDoubleArray().length - 1);
			INDArray f_i = dataSet.get(i).getFeatures();
			// f_i.muli(alpha);
			// f_i.divi(f_i.norm2Number());

			double lowerSum = 0;
			for (int j = 0; j < classes; j++) {

				if (i != j) {
					// lowerSum += Math.exp(calculateEuclideanImageDistance(f_i, c.getRow(j)));
					lowerSum += Math.exp(calculateEuclideanImageDistance(f_i, c.getRow(j)));
				}
			}

			INDArray c_k = c.getRow((int) Math.round(batch.get(i).classValue()));
			double upperExp = Math.exp(calculateEuclideanImageDistance(f_i, c_k));

			// System.out.println(loss);

			loss += Math.log(upperExp / (lowerSum + 1));

			if (Double.isNaN(loss)) {
				logger.warn("Got NaN value for COED batch score.");
				break;
			}
		}

		return (-1) * loss;
	}

	private static double calculateEuclideanImageDistance(final INDArray inst1, final INDArray inst2) {
		// double sum = 0;
		// for (int i = 0; i < inst1.length(); i++) {
		// sum += Math.pow((inst1.getDouble(i) - inst2.getDouble(i)), 2);
		// }
		// return sum / inst1.length();
		return inst1.distance2(inst2) / inst1.length();
	}

	// private static double calculateExpEuclideanImageDistance(final INDArray
	// inst1, final INDArray inst2) {
	//// double sum = 1;
	//// for (int i = 0; i < inst1.length(); i++) {
	//// sum *= Math.exp(Math.pow((inst1.getDouble(i) - inst2.getDouble(i)), 2));
	//// }
	//// return sum; // / inst1.length();
	// }

	private static double calculateEuclideanImageDistance(final Instance inst1, final Instance inst2) {
		// double sum = 0;
		// for (int i = 0; i < inst1.numAttributes() - 1; i++) {
		// sum += Math.pow((inst1.value(i) - inst2.value(i)), 2);
		// }
		// return sum;
		EuclideanDistance euclDist = new EuclideanDistance();
		return euclDist.distance(inst1, inst2);
	}

	public static double performLDA(final Instances instances) throws Exception {
		List<Instances> split = WekaUtil.getStratifiedSplit(instances, new Random(42), .7f);

		LDA lda = new LDA();
		// FLDA lda = new FLDA();
		lda.buildClassifier(split.get(0));

		Evaluation eval = new Evaluation(split.get(0));
		eval.evaluateModel(lda, split.get(1));

		return eval.pctCorrect() / 100.0;
	}

	public static double performEnsemble(Instances instances) throws Exception {
		List<Instances> subsample = WekaUtil.getStratifiedSplit(instances, new Random(42), .05f);
		instances = subsample.get(0);

		/* Relief */
		ReliefFAttributeEval relief = new ReliefFAttributeEval();
		relief.buildEvaluator(instances);
		double attEvalSum = 0;
		for (int i = 0; i < instances.numAttributes() - 1; i++) {
			attEvalSum += relief.evaluateAttribute(i);
		}
		attEvalSum /= instances.numAttributes();

		/* Variance */
		double varianceMean = 0;
		int totalNumericCount = 0;
		for (int i = 0; i < instances.numAttributes() - 1; i++) {
			if (instances.attribute(i).isNumeric()) {
				instances.attributeStats(i).numericStats.calculateDerived();
				varianceMean += Math.pow(instances.attributeStats(i).numericStats.stdDev, 2);
				totalNumericCount++;
			}
		}
		varianceMean /= totalNumericCount;

		/* KNN */
		List<Instances> split = WekaUtil.getStratifiedSplit(instances, new Random(42), .7f);
		IBk knn = new IBk(10);
		knn.buildClassifier(split.get(0));
		Evaluation eval = new Evaluation(split.get(0));
		eval.evaluateModel(knn, split.get(1));
		double knnResult = eval.pctCorrect() / 100d;

		return 1 - (0.33 * attEvalSum + 0.33 * knnResult + 0.33 * varianceMean);
	}

	public static Function<Instances, Double> getBenchmarkFunctionByName(final String name) {
		switch (name) {
		case "Cluster":
			return (data) -> {
				try {
					return performClustering(data);
				} catch (Exception e1) {
					logger.error("Could not perform clustering benchmark. Reason: " + e1.getMessage());
					return 0d;
				}
			};
		case "KernelCluster":
			return (data) -> {
				try {
					return performKernelClustering(data);
				} catch (Exception e1) {
					logger.error("Could not perform kernel clustering benchmark. Reason: " + e1.getMessage());
					return 0d;
				}
			};
		case "COCO":
			return (data) -> (-1) * calculateCOCOForBatch(data);
		case "COED":
			return (data) -> (-1) * calculateCOEDForBatch(data);
		case "LDA":
			return (data) -> {
				try {
					return performLDA(data);
				} catch (Exception e) {
					logger.error("Could not perform LDA benchmark. Reason: " + e.getMessage());
					return 0d;
				}
			};
		case "KernelLDA":
			return (data) -> {
				try {
					return performKernelLDA(data);
				} catch (Exception e) {
					logger.error("Could not perform cluster LDA benchmark. Reason: " + e.getMessage());
					return 0d;
				}
			};
		case "Ensemble":
			return (data) -> {
				try {
					return performEnsemble(data);
				} catch (Exception e) {
					logger.error("Could not perform ensemble benchmark. Reason: " + e.getMessage());
					return 0d;
				}
			};
		// case "Random":
		default:
			return (data) -> new Random().nextDouble();
		}
	}

	public static AbstractHASCOFENodeEvaluator getRandomNodeEvaluator(final int maxPipelineSize) {
		return new AbstractHASCOFENodeEvaluator(maxPipelineSize) {

			@Override
			public Double f(final Node<TFDNode, ?> node) throws Throwable {
				if (node.getParent() == null) {
					return null;
				}

				// If pipeline is too deep, assign worst value
				if (node.path().size() > this.maxPipelineSize) {
					return AbstractHASCOFEEvaluator.MAX_EVAL_VALUE;
				}

				return null;
				// return new Random(42).nextDouble();
			}
		};
	}

	public static double rankKendallsTau(final double[] ranking1, final double[] ranking2) {
		return Correlation.rankKendallTauBeta(ranking1, ranking2);
	}

	public static double evaluateMLPlan(final int timeout, final Instances training, final Instances test,
			final int seed, final Logger logger, final boolean enableVisualization, final int numCores)
			throws Exception {

		logger.debug("Starting ML-Plan execution. Training on " + training.numInstances() + " instances with "
				+ training.numAttributes() + " attributes.");

		/* Initialize MLPlan using WEKA components */
		MLPlanWekaClassifier mlplan = new MLPlanWekaClassifier();
		mlplan.setRandom(seed);
		mlplan.setNumberOfCPUs(numCores);
		mlplan.setLoggerName("mlplan");
		// Timeout in seconds
		mlplan.setTimeout(timeout);
		mlplan.setPortionOfDataForPhase2(.1f);
		if (enableVisualization) {
			mlplan.enableVisualization(true);
		}
		mlplan.buildClassifier(training);

		if (mlplan.getSelectedClassifier() == null
				|| ((MLPipeline) mlplan.getSelectedClassifier()).getBaseClassifier() == null) {
			logger.warn("Could not find a model using ML-Plan. Returning -1...");
			return -1;
		}

		String solutionString = ((MLPipeline) mlplan.getSelectedClassifier()).getBaseClassifier().getClass().getName()
				+ " | " + ((MLPipeline) mlplan.getSelectedClassifier()).getPreprocessors();
		logger.debug("Selected classifier: " + solutionString);

		/* evaluate solution produced by mlplan */
		Evaluation eval = new Evaluation(training);
		eval.evaluateModel(mlplan, test);

		return eval.pctCorrect();
	}

	public static double evaluateMLPlan(final int timeout, final Instances training, final Instances test,
			final int seed, final Logger logger, final boolean enableVisualization) throws Exception {
		return evaluateMLPlan(timeout, training, test, seed, logger, enableVisualization, 1);
	}

	public static double evaluateMLPlan(final int timeout, final Instances instances, final double trainRatio,
			final int seed, final Logger logger, final boolean enableVisualization, final int numCores)
			throws Exception {

		List<Instances> split = WekaUtil.getStratifiedSplit(instances, new Random(seed), trainRatio);

		return evaluateMLPlan(timeout, split.get(0), split.get(1), seed, logger, enableVisualization, numCores);
	}

}