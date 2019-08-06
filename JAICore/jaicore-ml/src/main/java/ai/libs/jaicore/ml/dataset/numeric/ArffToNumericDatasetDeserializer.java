package ai.libs.jaicore.ml.dataset.numeric;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.api4.java.ai.ml.dataset.attribute.IAttributeType;
import org.api4.java.ai.ml.dataset.attribute.nominal.INominalAttributeType;
import org.api4.java.ai.ml.dataset.attribute.numeric.INumericAttributeType;
import org.api4.java.ai.ml.dataset.supervised.INumericFeatureSupervisedDataset;
import org.api4.java.ai.ml.dataset.supervised.classification.INumericFeatureSingleLabelClassificationInstance;

import ai.libs.jaicore.basic.OptionsParser;
import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.ml.core.dataset.attribute.nominal.NominalAttributeType;
import ai.libs.jaicore.ml.core.dataset.attribute.numeric.NumericAttributeType;
import ai.libs.jaicore.ml.dataset.IDatasetDeserializer;
import weka.core.UnsupportedAttributeTypeException;

public class ArffToNumericDatasetDeserializer implements IDatasetDeserializer<double[], INumericFeatureSupervisedDataset<String, INumericFeatureSingleLabelClassificationInstance>> {

	private static final String M_RELATION = "@relation";
	private static final String M_ATTRIBUTE = "@attribute";
	private static final String M_DATA = "@data";

	private static final String M_NUMERIC_ATT = "numeric";
	private static final String M_NOMINAL_ATT = "nominal";

	private static final String F_CLASS_INDEX = "C";
	private static final String F_MULTI_TARGET = "MT";
	private static final String F_DATASET_SIZE = "I";

	private static final String K_RELATION_NAME = "relationName";
	private static final String K_CLASS_INDEX = "classIndex";

	private static final String SEPARATOR_RELATIONNAME = ":";
	private static final String SEPARATOR_ATTRIBUTE_DESCRIPTION = " ";
	private static final String SEPARATOR_DENSE_INSTANCE_VALUES = ",";

	private List<IAttributeType> chooseTheList(final List<IAttributeType> instanceAttribute, final List<IAttributeType> targetAttribute, final int numAttributes, final int currentIndex, final int classIndex, final boolean multiTarget) {
		if (!multiTarget) {
			return (currentIndex == classIndex) ? targetAttribute : instanceAttribute;
		} else {
			if (classIndex < 0) {
				return (currentIndex < classIndex) ? targetAttribute : instanceAttribute;
			} else {
				return (currentIndex >= numAttributes + classIndex) ? targetAttribute : instanceAttribute;
			}
		}
	}

	@Override
	public INumericFeatureSupervisedDataset<String, INumericFeatureSingleLabelClassificationInstance> deserializeDataset(final File datasetFile) {
		try (BufferedReader br = Files.newBufferedReader(datasetFile.toPath())) {
			KVStore relationMetaData = new KVStore();
			NumericDataset<String> dataset = null;
			List<IAttributeType> attributeList = new LinkedList<>();
			List<IAttributeType> instanceAttribute = new LinkedList<>();
			List<IAttributeType> targetAttribute = new LinkedList<>();

			boolean instanceReadMode = false;
			String line;
			long lineCounter = 1;

			while ((line = br.readLine()) != null) {
				if (line.startsWith(M_RELATION)) {
					relationMetaData = this.parseRelation(line);
				} else if (line.startsWith(M_ATTRIBUTE)) {
					attributeList.add(this.parseAttributeMetaData(line));
				} else if (line.startsWith(M_DATA)) {
					if (!line.trim().equals(M_DATA)) {
						throw new IllegalArgumentException("Error while parsing arff-file on line " + lineCounter + ": There is more in this line than just the data declaration " + M_DATA + ", which is not supported");
					}
					instanceReadMode = true;

					Integer classIndex = null;
					if (relationMetaData != null) {
						classIndex = relationMetaData.getAsInt(K_CLASS_INDEX);
					}
					if (classIndex == null) {
						classIndex = attributeList.size() - 1;
					}

					for (int i = 0; i < attributeList.size(); i++) {
						List<IAttributeType> listToAddAttributeTo = this.chooseTheList(instanceAttribute, targetAttribute, attributeList.size(), i, classIndex, relationMetaData.getAsBoolean(F_MULTI_TARGET));
						listToAddAttributeTo.add(attributeList.get(i));
					}

					dataset = new NumericDataset<>((relationMetaData != null) ? relationMetaData.getAsString(K_RELATION_NAME) : "unnamed", instanceAttribute, targetAttribute);

				} else if (instanceReadMode && !line.trim().isEmpty() && !instanceAttribute.isEmpty() && !targetAttribute.isEmpty()) {
					double[] instance = this.parseInstance(line, attributeList);
					double[] x = new double[instanceAttribute.size()];
					double[] y = new double[targetAttribute.size()];

					for (int i = 0; i < attributeList.size(); i++) {
						int instanceAttributeIndex = instanceAttribute.indexOf(attributeList.get(i));

						if (instanceAttributeIndex >= 0) {
							x[instanceAttributeIndex] = instance[i];
						} else {
							y[targetAttribute.indexOf(attributeList.get(i))] = instance[i];
						}
					}
					dataset.add(x, y);
				}
				lineCounter++;
			}

			return dataset;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (UnsupportedAttributeTypeException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Extracts meta data about a relation from a string.
	 *
	 * @param line The line which is to be parsed to extract the necessary information from the relation name.
	 * @return A KVStore containing the parsed meta data.
	 */
	private KVStore parseRelation(final String line) {
		KVStore metaData = new KVStore();
		String[] relationNameAndOptions = line.substring(line.indexOf('\'') + 1, line.lastIndexOf('\'')).split(SEPARATOR_RELATIONNAME);

		metaData.put(K_RELATION_NAME, relationNameAndOptions[0].trim());
		if (relationNameAndOptions.length > 1) {
			OptionsParser optParser = new OptionsParser(relationNameAndOptions[1]);
			metaData.put(K_CLASS_INDEX, optParser.get(F_CLASS_INDEX));
		}

		return metaData;
	}

	private IAttributeType parseAttributeMetaData(final String line) throws UnsupportedAttributeTypeException {
		String[] attributeDefinitionSplit = line.substring(M_ATTRIBUTE.length() + 1).split(SEPARATOR_ATTRIBUTE_DESCRIPTION);
		String name = attributeDefinitionSplit[0].trim();
		if (name.startsWith("'") && name.endsWith("'")) {
			name = name.substring(1, name.length() - 1);
		}
		String type = attributeDefinitionSplit[1].trim();

		String[] values = null;
		if (type.startsWith("{") && type.endsWith("}")) {
			values = type.substring(1, type.length() - 1).split(SEPARATOR_DENSE_INSTANCE_VALUES);
			type = M_NOMINAL_ATT;
		}

		switch (type) {
		case M_NUMERIC_ATT:
			return new NumericAttributeType(name);
		case M_NOMINAL_ATT:
			if (values != null) {
				return new NominalAttributeType(name, Arrays.stream(values).map(String::trim).collect(Collectors.toList()));
			} else {
				throw new IllegalStateException("Identified a nominal attribute but it seems to have no values.");
			}
		default:
			throw new UnsupportedAttributeTypeException("Can not deal with attribute type " + type);
		}
	}

	/**
	 * Parse a string into a double vector which can then be transformed into an instance description.
	 *
	 * @param line The string describing the instance.
	 * @param dataset
	 * @return
	 * @throws UnsupportedAttributeTypeException
	 */
	private double[] parseInstance(final String line, final List<IAttributeType> attributes) throws UnsupportedAttributeTypeException {
		String[] instanceValueSplit = line.split(SEPARATOR_DENSE_INSTANCE_VALUES);
		double[] instanceDescription = new double[instanceValueSplit.length];

		for (int i = 0; i < instanceValueSplit.length; i++) {
			if (attributes.get(i) instanceof INumericAttributeType) {
				instanceDescription[i] = Double.valueOf(instanceValueSplit[i]);
			} else if (attributes.get(i) instanceof INominalAttributeType) {
				instanceDescription[i] = ((INominalAttributeType) attributes.get(i)).encodeToDouble(instanceValueSplit[i]);
			} else {
				throw new UnsupportedAttributeTypeException("Cannot parse the value of the attribute type " + attributes.get(i).getClass().getName());
			}
		}
		return instanceDescription;
	}

}
