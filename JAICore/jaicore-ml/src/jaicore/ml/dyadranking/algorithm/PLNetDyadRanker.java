package jaicore.ml.dyadranking.algorithm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration.ListBuilder;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.primitives.Pair;

import jaicore.basic.FileUtil;
import jaicore.ml.core.dataset.IDataset;
import jaicore.ml.core.dataset.IInstance;
import jaicore.ml.core.exception.ConfigurationException;
import jaicore.ml.core.exception.PredictionException;
import jaicore.ml.core.exception.TrainingException;
import jaicore.ml.core.predictivemodel.IOnlineLearner;
import jaicore.ml.core.predictivemodel.IPredictiveModelConfiguration;
import jaicore.ml.dyadranking.Dyad;
import jaicore.ml.dyadranking.dataset.DyadRankingDataset;
import jaicore.ml.dyadranking.dataset.DyadRankingInstance;
import jaicore.ml.core.predictivemodel.IPredictiveModelConfiguration;
import jaicore.ml.dyadranking.dataset.IDyadRankingInstance;

/**
 * A dyad ranker based on a Plackett-Luce network.
 * 
 * @author Helena Graf, Jonas Hanselle, Michael Braun
 *
 */
public class PLNetDyadRanker extends APLDyadRanker implements IOnlineLearner<IDyadRankingInstance> {

	private MultiLayerNetwork plNet;
	private IPLNetDyadRankerConfiguration configuration;
	private int epoch;
	private int iteration;
	private double currentBestScore;
	private MultiLayerNetwork currentBestModel;

	/**
	 * Constructor using a {@link IPLNetDyadRankerConfiguration} to create the
	 * {@link PLNetDyadRanker}.
	 * 
	 * @param configuration
	 */
	public PLNetDyadRanker() {
		this.configuration = ConfigFactory.create(IPLNetDyadRankerConfiguration.class);
	}
	
	public PLNetDyadRanker(IPLNetDyadRankerConfiguration config) {
		this.configuration = config;
	}

	@Override
	public void train(IDataset dataset) throws TrainingException {
		if (!(dataset instanceof DyadRankingDataset)) {
			throw new IllegalArgumentException(
					"Can only train the Plackett-Luce net dyad ranker with a dyad ranking dataset!");
		}
		DyadRankingDataset drDataset = (DyadRankingDataset) dataset;

		Collections.shuffle(drDataset);
		List<IInstance> drTrain = (List<IInstance>) drDataset.subList(0, (int) (0.8d * drDataset.size()));
		List<IInstance> drTest = (List<IInstance>) drDataset.subList((int) (0.8d * drDataset.size()), drDataset.size());

		if (this.plNet == null) {
			int dyadSize = ((DyadRankingInstance) drDataset.get(0)).getDyadAtPosition(0).getInstance().length()
					+ ((DyadRankingInstance) drDataset.get(0)).getDyadAtPosition(0).getAlternative().length();
			this.plNet = createNetwork(dyadSize);
		}

		currentBestScore = Double.POSITIVE_INFINITY;
		epoch = 0;
		iteration = 0;
		int patience = 0;
		int earlyStoppingCounter = 0;
		int maxEpochs = configuration.plNetMaxEpochs();

		while (patience < configuration.plNetEarlyStoppingPatience() 
				&& (epoch < maxEpochs || maxEpochs == 0)) {
			// Iterate through training data
			for (IInstance dyadRankingInstance : drTrain) {
				this.update(dyadRankingInstance);
			}
			//System.out.println(plNet.params());
			earlyStoppingCounter++;
			// Compute validation error
			if (earlyStoppingCounter == configuration.plNetEarlyStoppingInterval()) {
				double avgScore = computeAvgError(drTest);
				if (avgScore < currentBestScore) {
					currentBestScore = avgScore;
					currentBestModel = plNet.clone();
					System.out.println(currentBestScore);
					patience = 0;
				} else {
					patience++;
				}
				System.out.println("patience: " + patience);
				earlyStoppingCounter = 0;
			}
			epoch++;
		}
		plNet = currentBestModel;
	}
	
	@Override
	public void update(IInstance instance) throws TrainingException {
		if (!(instance instanceof IDyadRankingInstance)) {
			throw new IllegalArgumentException(
					"Can only update the Plackett-Luce net dyad ranker with a dyad ranking instance!");
		}

		DyadRankingInstance drInstance = (DyadRankingInstance) instance;
		// init weight update vector
		INDArray dyadMatrix;
		List<INDArray> dyadList = new ArrayList<INDArray>(drInstance.length());
		for (Dyad dyad : drInstance) {
			INDArray instanceOfDyad = Nd4j.create(dyad.getInstance().asArray());
			INDArray alternativeOfDyad = Nd4j.create(dyad.getAlternative().asArray());
			INDArray dyadVector = Nd4j.hstack(instanceOfDyad, alternativeOfDyad);
			dyadList.add(dyadVector);
		}
		dyadMatrix = Nd4j.vstack(dyadList);
		List<INDArray> activations = plNet.feedForward(dyadMatrix);
		INDArray output = activations.get(activations.size() - 1);
		System.out.println();
		output = output.transpose();
		INDArray deltaW = Nd4j.zeros(plNet.params().length());
		Gradient deltaWk = null;
		MultiLayerNetwork plNetClone = plNet.clone();
		for (int k = 0; k < drInstance.length(); k++) {
			// compute derivative of loss w.r.t. k
			plNetClone.setInput(dyadList.get(k));
			plNetClone.feedForward(true, false);
			INDArray lossGradient = PLNetLoss.computeLossGradient(output, k);
			// compute backprop gradient for weight updates w.r.t. k
			Pair<Gradient, INDArray> p = plNetClone.backpropGradient(lossGradient, null);
			deltaWk = p.getFirst();
			plNet.getUpdater().update(plNet, deltaWk, iteration, epoch, 1, LayerWorkspaceMgr.noWorkspaces());
			deltaW.addi(deltaWk.gradient());
		}
		plNet.params().subi(deltaW);
		iteration++;
	}

	@Override
	public void update(Set<IInstance> instances) throws TrainingException {
		for (IInstance instance : instances)
			this.update(instance);
	}

	@Override
	public IDyadRankingInstance predict(IInstance instance) throws PredictionException {
		if (!(instance instanceof IDyadRankingInstance)) {
			throw new IllegalArgumentException(
					"Can only make prediction for dyad ranking instances using the Plackett-Luce net dyad ranker!");
		}
		DyadRankingInstance drInstance = (DyadRankingInstance) instance;
		List<Pair<Dyad, Double>> dyadUtilityPairs = new ArrayList<Pair<Dyad, Double>>(drInstance.length());
		for (Dyad dyad : drInstance) {
			INDArray instanceOfDyad = Nd4j.create(dyad.getInstance().asArray());
			INDArray alternativeOfDyad = Nd4j.create(dyad.getAlternative().asArray());
			INDArray plNetInput = Nd4j.hstack(instanceOfDyad, alternativeOfDyad);
			double plNetOutput = plNet.output(plNetInput).getDouble(0);
			dyadUtilityPairs.add(new Pair<Dyad, Double>(dyad, plNetOutput));
		}
		// sort the instance in descending order of utility values
		Collections.sort(dyadUtilityPairs, Comparator.comparing(p -> -p.getRight()));
		List<Dyad> ranking = new ArrayList<Dyad>();
		for (Pair<Dyad, Double> pair : dyadUtilityPairs)
			ranking.add(pair.getLeft());
		return new DyadRankingInstance(ranking);
	}

	@Override
	public List<IDyadRankingInstance> predict(IDataset dataset) throws PredictionException {
		if (!(dataset instanceof DyadRankingDataset)) {
			throw new IllegalArgumentException(
					"Can only make predictions for dyad ranking datasets using the Plackett-Luce net dyad ranker!");
		}
		DyadRankingDataset drDataset = (DyadRankingDataset) dataset;
		List<IDyadRankingInstance> results = new ArrayList<IDyadRankingInstance>(dataset.size());
		for (IInstance instance : drDataset) {
			results.add(this.predict(instance));
		}
		return results;
	}
	
	/**
	 * Computes the average error on a set of dyad rankings in terms on the negative log likelihood (NLL).
	 * 
	 * @param drTest
	 * @return
	 */
	private double computeAvgError(List<IInstance> drTest) {
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (IInstance dyadRankingInstance : drTest) {
			DyadRankingInstance drInstance = (DyadRankingInstance) dyadRankingInstance;
			List<INDArray> dyadList = new ArrayList<INDArray>(drInstance.length());
			for (Dyad dyad : drInstance) {
				INDArray instanceOfDyad = Nd4j.create(dyad.getInstance().asArray());
				INDArray alternativeOfDyad = Nd4j.create(dyad.getAlternative().asArray());
				INDArray dyadVector = Nd4j.hstack(instanceOfDyad, alternativeOfDyad);
				dyadList.add(dyadVector);
			}
			INDArray dyadMatrix;
			dyadMatrix = Nd4j.vstack(dyadList);
			INDArray outputs = plNet.output(dyadMatrix);
			outputs = outputs.transpose();
			double score = PLNetLoss.computeLoss(outputs).getDouble(0);
			stats.addValue(score);
		}
		return stats.getMean();		
	}

	@Override
	public void setConfiguration(IPredictiveModelConfiguration configuration) throws ConfigurationException {
		if (!(configuration instanceof IPLNetDyadRankerConfiguration)) {
			throw new IllegalArgumentException("The configuration is no PLNetDyadRankerConfiguration!");
		}
		this.configuration = (IPLNetDyadRankerConfiguration) configuration;
	}

	@Override
	public IPredictiveModelConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Creates a simple feed-forward {@link MultiLayerNetwork} that can be used as a
	 * PLNet for dyad-ranking.
	 * 
	 * @param numInputs  The number of inputs to the network, i.e. the number of features of a dyad.
	 * @return New {@link MultiLayerNetwork}
	 */
	private MultiLayerNetwork createNetwork(int numInputs) {
		if (this.configuration.plNetHiddenNodes().isEmpty())
			throw new IllegalArgumentException(
					"There must be at least one hidden layer in specified in the config file!");
		int numOutputs = 1;
		int inputsFirstHiddenLayer = configuration.plNetHiddenNodes().get(0);
		ListBuilder configBuilder = new NeuralNetConfiguration.Builder()
				.seed(configuration.plNetSeed())
				// Gradient descent updater: SGD
				.updater(new Sgd(configuration.plNetLearningRate()))
				.list();
		
		// Build hidden layers
		String activation = configuration.plNetActivationFunction();
		configBuilder.layer(0, new DenseLayer.Builder()
				.nIn(numInputs)
				.nOut(inputsFirstHiddenLayer)
				.weightInit(WeightInit.XAVIER)
				.activation(Activation.fromString(activation)).build());
		List<Integer> hiddenNodes = configuration.plNetHiddenNodes();

		for (int i = 0; i < hiddenNodes.size() - 1; i++) {
			int numIn = hiddenNodes.get(i);
			int numOut = hiddenNodes.get(i + 1);
			configBuilder.layer(i + 1, new DenseLayer.Builder()
					.nIn(numIn)
					.nOut(numOut)
					.weightInit(WeightInit.XAVIER)
					.activation(Activation.fromString(activation)).build());
		}
		
		// Build output layer. Since we are using an external error for training, 
		// this is a regular layer instead of an OutputLayer
		configBuilder.layer(hiddenNodes.size(),
				new DenseLayer.Builder()
				.nIn(hiddenNodes.get(hiddenNodes.size() - 1))
				.nOut(1)
				.weightInit(WeightInit.XAVIER)
				.activation(Activation.IDENTITY).build());

		MultiLayerConfiguration multiLayerConfig = configBuilder.build();
		return new MultiLayerNetwork(multiLayerConfig);
	}

	/**
	 * Creates a simple feed-forward {@link MultiLayerNetwork} using the json
	 * representation of a {@link MultiLayerConfiguration} in the file .
	 * 
	 * @param configFile {@link File} containing the json representation of the
	 *                   {@link MultiLayerConfiguration}
	 * @return a new {@link MultiLayerNetwork} based on the
	 *         {@link MultiLayerConfiguration}
	 */
	private MultiLayerNetwork createNetworkFromConfigFile(File configFile) {
		String json = "";
		try {
			json = FileUtil.readFileAsString(configFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MultiLayerConfiguration config = MultiLayerConfiguration.fromJson(json);
		MultiLayerNetwork network = new MultiLayerNetwork(config);

		return network;
	}

	public void setConfiguration(IPLNetDyadRankerConfiguration configuration) {
		this.configuration = configuration;
	}
	
	/**
	 * Save a trained model at a given file path. Note that the produced file is a zip file and a ".zip" ending is added.
	 * 
	 * @param filePath	The file path to save to.
	 * @throws IOException
	 */
	public void saveModelToFile(String filePath) throws IOException {
		if (plNet == null) {
			throw new IllegalStateException("Cannot save untrained model.");
		}
		File locationToSave = new File("filePath" + ".zip");
		ModelSerializer.writeModel(plNet, locationToSave, true);
	}
	
	/**
	 * Restore a trained model from a given file path. Warning: does not check whether the loaded model is
	 * a valid PLNet or conforms to the configuration of the object.
	 * 
	 * @param filePath	The file to load from.
	 * @throws IOException
	 */
	public void loadModelFromFile(String filePath) throws IOException {
		MultiLayerNetwork restored = ModelSerializer.restoreMultiLayerNetwork(filePath);
		plNet = restored;
	}

}
