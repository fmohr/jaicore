package ai.libs.jaicore.ml.core.dataset.serialization;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.api4.java.ai.ml.core.dataset.descriptor.IDatasetDescriptor;
import org.api4.java.ai.ml.core.dataset.descriptor.IFileDatasetDescriptor;
import org.api4.java.ai.ml.core.dataset.schema.ILabeledInstanceSchema;
import org.api4.java.ai.ml.core.dataset.schema.attribute.IAttribute;
import org.api4.java.ai.ml.core.dataset.serialization.DatasetDeserializationFailedException;
import org.api4.java.ai.ml.core.dataset.serialization.IDatasetDeserializer;
import org.api4.java.ai.ml.core.dataset.serialization.UnsupportedAttributeTypeException;
import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.api4.java.ai.ml.core.dataset.supervised.ILabeledInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.libs.jaicore.basic.OptionsParser;
import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.ml.core.dataset.Dataset;
import ai.libs.jaicore.ml.core.dataset.DenseInstance;
import ai.libs.jaicore.ml.core.dataset.SparseInstance;
import ai.libs.jaicore.ml.core.dataset.schema.LabeledInstanceSchema;
import ai.libs.jaicore.ml.core.dataset.schema.attribute.IntBasedCategoricalAttribute;
import ai.libs.jaicore.ml.core.dataset.schema.attribute.NumericAttribute;
import ai.libs.jaicore.ml.core.dataset.schema.attribute.StringAttribute;
import ai.libs.jaicore.ml.core.dataset.serialization.arff.EArffAttributeType;
import ai.libs.jaicore.ml.core.dataset.serialization.arff.EArffItem;

public class ArffDatasetAdapter implements IDatasetDeserializer<ILabeledDataset<ILabeledInstance>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArffDatasetAdapter.class);

	public static final String K_RELATION_NAME = "relationName";
	public static final String K_CLASS_INDEX = "classIndex";

	private static final String F_CLASS_INDEX = "C";

	private static final String SEPARATOR_RELATIONNAME = ":";
	private static final String SEPARATOR_ATTRIBUTE_DESCRIPTION = " ";
	private static final String SEPARATOR_DENSE_INSTANCE_VALUES = ",";

	private final boolean sparseMode;
	private IDatasetDescriptor datasetDescriptor = null;

	public ArffDatasetAdapter(final boolean sparseMode, final IDatasetDescriptor datasetDescriptor) {
		this(sparseMode);
		this.datasetDescriptor = datasetDescriptor;
	}

	public ArffDatasetAdapter(final boolean sparseMode) {
		this.sparseMode = sparseMode;
	}

	public ArffDatasetAdapter() {
		this(false);
	}

	public ILabeledDataset<ILabeledInstance> deserializeDataset(final IFileDatasetDescriptor datasetFile, final String nameOfClassAttribute) throws DatasetDeserializationFailedException, InterruptedException {
		Objects.requireNonNull(datasetFile, "No dataset has been configured.");
		/* read the file until the class parameter is found and count the params */
		int numAttributes = 0;
		try (BufferedReader br = Files.newBufferedReader(datasetFile.getDatasetDescription().toPath())) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.toLowerCase().startsWith(EArffItem.ATTRIBUTE.getValue().toLowerCase())) {
					IAttribute att = parseAttribute(line);
					if (att.getName().equals(nameOfClassAttribute)) {
						break;
					}
					numAttributes++;
				}
			}
		} catch (Exception e) {
			throw new DatasetDeserializationFailedException(e);
		}

		LOGGER.info("Successfully identified class attribute index {} for attribute with name {}", numAttributes, nameOfClassAttribute);
		return this.deserializeDataset(datasetFile, numAttributes);
	}

	public ILabeledDataset<ILabeledInstance> deserializeDataset(final IFileDatasetDescriptor datasetDescriptor, final int columnWithClassIndex) throws DatasetDeserializationFailedException, InterruptedException {
		Objects.requireNonNull(datasetDescriptor, "No dataset has been configured.");
		return readDataset(this.sparseMode, datasetDescriptor.getDatasetDescription(), columnWithClassIndex);
	}

	@Override
	public ILabeledDataset<ILabeledInstance> deserializeDataset(final IDatasetDescriptor datasetDescriptor) throws DatasetDeserializationFailedException, InterruptedException {
		if (!(datasetDescriptor instanceof IFileDatasetDescriptor)) {
			throw new DatasetDeserializationFailedException("Cannot handle dataset descriptor of type " + datasetDescriptor.getClass().getName());
		}
		return this.deserializeDataset((IFileDatasetDescriptor) datasetDescriptor, -1);
	}

	public ILabeledDataset<ILabeledInstance> deserializeDataset() throws InterruptedException, DatasetDeserializationFailedException {
		return this.deserializeDataset(this.datasetDescriptor);
	}

	/**
	 * Extracts meta data about a relation from a string.
	 *
	 * @param line The line which is to be parsed to extract the necessary information from the relation name.
	 * @return A KVStore containing the parsed meta data.
	 */
	protected static KVStore parseRelation(final String line) {
		KVStore metaData = new KVStore();

		// cut off relation tag
		String relationDescription = line.substring(EArffItem.RELATION.getValue().length()).trim();

		if (relationDescription.startsWith("'") && relationDescription.endsWith("'")) {
			String[] relationNameAndOptions = line.substring(line.indexOf('\'') + 1, line.lastIndexOf('\'')).split(SEPARATOR_RELATIONNAME);
			metaData.put(K_RELATION_NAME, relationNameAndOptions[0].trim());
			if (relationNameAndOptions.length > 1) {
				OptionsParser optParser = new OptionsParser(relationNameAndOptions[1]);
				metaData.put(K_CLASS_INDEX, optParser.get(F_CLASS_INDEX));
			}
		} else {
			metaData.put(K_RELATION_NAME, relationDescription);
		}

		return metaData;
	}

	protected static IAttribute parseAttribute(final String line) throws UnsupportedAttributeTypeException {
		String attributeDefinitionSplit = line.replaceAll("\\t", " ").substring(EArffItem.ATTRIBUTE.getValue().length() + 1);
		String name = attributeDefinitionSplit.substring(0, attributeDefinitionSplit.indexOf(SEPARATOR_ATTRIBUTE_DESCRIPTION));
		String type = attributeDefinitionSplit.substring(name.length() + 1);
		if ((name.startsWith("'") && name.endsWith("'")) || (name.startsWith("\"") && name.endsWith("\""))) {
			name = name.substring(1, name.length() - 1);
		}

		EArffAttributeType attType;
		String[] values = null;
		if (type.startsWith("{") && type.endsWith("}")) {
			values = type.substring(1, type.length() - 1).split(SEPARATOR_DENSE_INSTANCE_VALUES);
			attType = EArffAttributeType.NOMINAL;
		} else {
			try {
				attType = EArffAttributeType.valueOf(type.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new UnsupportedAttributeTypeException("The attribute type " + type.toUpperCase() + " is not supported in the EArffAttributeType ENUM.");
			}
		}

		switch (attType) {
		case NUMERIC:
		case REAL:
		case INTEGER:
			return new NumericAttribute(name);
		case STRING:
			return new StringAttribute(name);
		case NOMINAL:
			if (values != null) {
				return new IntBasedCategoricalAttribute(name,
						Arrays.stream(values).map(String::trim).map(x -> (((x.startsWith("'") && x.endsWith("'")) || x.startsWith("\"") && x.endsWith("\"")) ? x.substring(1, x.length() - 1) : x)).collect(Collectors.toList()));
			} else {
				throw new IllegalStateException("Identified a nominal attribute but it seems to have no values.");
			}
		default:
			throw new UnsupportedAttributeTypeException("Can not deal with attribute type " + type);
		}
	}

	protected static Object parseInstance(final boolean sparseData, final List<IAttribute> attributes, final int targetIndex, final String line) {
		if (line.trim().startsWith("%")) {
			throw new IllegalArgumentException("Cannot create object for commented line!");
		}
		String[] lineSplit = line.split(",");

		boolean sparseMode = sparseData;
		if (lineSplit.length < attributes.size()) {
			sparseMode = true;
		}

		if (!sparseMode) {
			if (lineSplit.length != attributes.size()) {
				throw new IllegalArgumentException("Cannot parse instance as this is not a sparse instance but has less columns than there are attributes defined. Expected values: " + attributes.size() + ". Actual number of values: "
						+ lineSplit.length + ". Values: " + Arrays.toString(lineSplit));
			}
			Object[] parsedDenseInstance = new Object[lineSplit.length - 1];
			Object target = null;
			int cI = 0;
			for (int i = 0; i < lineSplit.length; i++) {
				if (i == targetIndex) {
					target = attributes.get(i).deserializeAttributeValue(lineSplit[i]);
				} else {
					parsedDenseInstance[cI++] = attributes.get(i).deserializeAttributeValue(lineSplit[i]);
				}
			}
			return Arrays.asList(parsedDenseInstance, target);
		} else {
			Map<Integer, Object> parsedSparseInstance = new HashMap<>();
			for (String sparseValue : lineSplit) {
				int indexOfFirstSpace = sparseValue.indexOf(' ');
				int indexOfAttribute = Integer.parseInt(sparseValue.substring(0, indexOfFirstSpace));
				String attributeValue = sparseValue.substring(indexOfFirstSpace + 1);
				parsedSparseInstance.put(indexOfAttribute, attributes.get(indexOfAttribute).deserializeAttributeValue(attributeValue));
			}
			return parsedSparseInstance;
		}
	}

	protected static ILabeledDataset<ILabeledInstance> createDataset(final KVStore relationMetaData, final List<IAttribute> attributes) {
		if (!relationMetaData.containsKey(K_CLASS_INDEX) || relationMetaData.getAsInt(K_CLASS_INDEX) < 0) {
			throw new IllegalArgumentException("No (valid) class index given!");
		}
		List<IAttribute> attributeList = new ArrayList<>(attributes);
		IAttribute labelAttribute = attributeList.remove((int) relationMetaData.getAsInt(K_CLASS_INDEX));
		ILabeledInstanceSchema schema = new LabeledInstanceSchema(relationMetaData.getAsString(K_RELATION_NAME), attributeList, labelAttribute);
		return new Dataset(schema);
	}

	public static ILabeledDataset<ILabeledInstance> readDataset(final File datasetFile) throws DatasetDeserializationFailedException, InterruptedException {
		return readDataset(false, datasetFile);
	}

	public static ILabeledDataset<ILabeledInstance> readDataset(final boolean sparseMode, final File datasetFile) throws DatasetDeserializationFailedException, InterruptedException {
		return readDataset(sparseMode, datasetFile, -1);
	}

	public static ILabeledDataset<ILabeledInstance> readDataset(final boolean sparseMode, final File datasetFile, final int columnWithClassIndex) throws DatasetDeserializationFailedException, InterruptedException {
		try (BufferedReader br = Files.newBufferedReader(datasetFile.toPath())) {
			ILabeledDataset<ILabeledInstance> dataset = null;
			KVStore relationMetaData = new KVStore();
			List<IAttribute> attributes = new ArrayList<>();

			boolean instanceReadMode = false;
			String line;
			long lineCounter = 1;

			while ((line = br.readLine()) != null) {
				if (!instanceReadMode) {
					if (line.toLowerCase().startsWith(EArffItem.RELATION.getValue())) {
						// parse relation meta data
						relationMetaData = parseRelation(line);
						if (columnWithClassIndex >= 0) {
							if (relationMetaData.containsKey(K_CLASS_INDEX) && (relationMetaData.getAsInt(K_CLASS_INDEX) != columnWithClassIndex)) {
								throw new IllegalArgumentException("Cannot overwrite the class index, because it is already defined in the relation of the ARFF file.");
							}
							relationMetaData.put(K_CLASS_INDEX, columnWithClassIndex);
						}
					} else if (line.toLowerCase().startsWith(EArffItem.ATTRIBUTE.getValue())) {
						// parse attribute meta data
						attributes.add(parseAttribute(line));
					} else if (line.toLowerCase().startsWith(EArffItem.DATA.getValue())) {
						// switch to instance read mode
						if (!line.toLowerCase().trim().equals(EArffItem.DATA.getValue())) {
							throw new IllegalArgumentException(
									"Error while parsing arff-file on line " + lineCounter + ": There is more in this line than just the data declaration " + EArffItem.DATA.getValue() + ", which is not supported");
						}
						instanceReadMode = true;
						try {
							dataset = createDataset(relationMetaData, attributes);
						} catch (IllegalArgumentException e) {
							LOGGER.warn("Invalid class index in the dataset's meta data (" + relationMetaData.get(K_CLASS_INDEX) + "): Assuming last column to be the target attribute!");
							relationMetaData.put(K_CLASS_INDEX, attributes.size() - 1);
							dataset = createDataset(relationMetaData, attributes);
						}
					}
				} else {
					line = line.trim();
					if (!line.isEmpty() && !line.startsWith("%")) { // ignore empty and comment lines
						Object parsedInstance = parseInstance(sparseMode, attributes, relationMetaData.getAsInt(K_CLASS_INDEX), line);
						ILabeledInstance newI;
						if (parsedInstance instanceof List<?>) {
							newI = new DenseInstance((Object[]) ((List<?>) parsedInstance).get(0), ((List<?>) parsedInstance).get(1));
						} else if (parsedInstance instanceof Map) {
							@SuppressWarnings("unchecked")
							Map<Integer, Object> parsedSparseInstance = (Map<Integer, Object>) parsedInstance;
							Object label = parsedSparseInstance.remove(relationMetaData.getAsInt(K_CLASS_INDEX));
							newI = new SparseInstance(attributes.size(), parsedSparseInstance, label);
						} else {
							throw new IllegalStateException("Severe Error: The format of the parsed instance is not as expected.");
						}
						dataset.add(newI);
					}
				}
			}
			lineCounter++;
			return dataset;
		} catch (Exception e) {
			e.printStackTrace();
			throw new DatasetDeserializationFailedException("Could not deserialize dataset from ARFF file.", e);
		}
	}

}
