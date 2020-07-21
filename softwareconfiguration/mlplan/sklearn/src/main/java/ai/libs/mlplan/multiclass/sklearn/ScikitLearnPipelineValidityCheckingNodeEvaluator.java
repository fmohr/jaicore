package ai.libs.mlplan.multiclass.sklearn;

import java.util.Arrays;
import java.util.Collection;

import org.api4.java.ai.ml.core.dataset.supervised.ILabeledDataset;
import org.api4.java.common.control.ILoggingCustomizable;
import org.api4.java.datastructure.graph.ILabeledPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.libs.hasco.core.HASCOUtil;
import ai.libs.jaicore.components.model.Component;
import ai.libs.jaicore.components.model.ComponentInstance;
import ai.libs.jaicore.ml.weka.dataset.WekaInstances;
import ai.libs.jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNode;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.exceptions.ControlledNodeEvaluationException;
import ai.libs.mlplan.core.PipelineValidityCheckingNodeEvaluator;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class ScikitLearnPipelineValidityCheckingNodeEvaluator extends PipelineValidityCheckingNodeEvaluator implements ILoggingCustomizable {

	private Logger logger = LoggerFactory.getLogger(ScikitLearnPipelineValidityCheckingNodeEvaluator.class);

	/* the predicates of the dataset */
	private boolean propertiesDetermined = false;
	private boolean binaryClass;
	private boolean multiClass;
	private boolean regression;
	private boolean multiValuedNominalAttributes;
	private boolean containsNegativeValues;

	public ScikitLearnPipelineValidityCheckingNodeEvaluator() {
		super();
	}

	public ScikitLearnPipelineValidityCheckingNodeEvaluator(final Collection<Component> components, final ILabeledDataset<?> data) {
		super(components, data);
	}

	private boolean multiValuedNominalAttributesExist() {
		Instances data = this.getData().getInstances();
		for (int i = 0; i < data.numAttributes(); i++) {
			Attribute att = data.attribute(i);
			if (att != data.classAttribute() && att.isNominal() && att.numValues() > 2) {
				return true;
			}
		}
		return false;
	}

	private synchronized void extractDatasetProperties() {
		if (!this.propertiesDetermined) {

			if (this.getComponents() == null) {
				throw new IllegalStateException("Components not defined!");
			}

			/* compute binary class predicate */
			Instances data = this.getInstancesInWekaFormat();
			this.binaryClass = data.classAttribute().isNominal() && data.classAttribute().numValues() == 2;
			this.multiClass = data.classAttribute().isNominal() && data.classAttribute().numValues() > 2;
			this.regression = data.classAttribute().isNumeric();

			/* determine whether the dataset is multi-valued nominal */
			this.multiValuedNominalAttributes = this.multiValuedNominalAttributesExist();

			this.containsNegativeValues = false;
			for (Instance i : data) {
				this.containsNegativeValues = this.containsNegativeValues || Arrays.stream(i.toDoubleArray()).anyMatch(x -> x < 0);
			}

			this.propertiesDetermined = true;
		}
	}

	@Override
	public Double evaluate(final ILabeledPath<TFDNode, String> path) throws ControlledNodeEvaluationException {
		if (!this.propertiesDetermined) {
			this.extractDatasetProperties();
		}

		/* get partial component */
		ComponentInstance instance = HASCOUtil.getSolutionCompositionFromState(this.getComponents(), path.getHead().getState(), false);
		if (instance != null) {
			/* check invalid classifiers for this kind of dataset */
			ComponentInstance classifier;
			if (instance.getComponent().getName().toLowerCase().contains("pipeline")) {
				classifier = instance.getSatisfactionOfRequiredInterfaces().get("classifier");
			} else {
				classifier = instance;
			}

			if (classifier != null) {
				this.checkValidity(classifier);
			}
		}
		return null;
	}

	private void checkValidity(final ComponentInstance classifier) throws ControlledNodeEvaluationException {
		String classifierName = classifier.getComponent().getName().toLowerCase();

		if (this.containsNegativeValues && classifierName.matches("(.*)(multinomialnb)(.*)")) {
			throw new ControlledNodeEvaluationException("Negative numeric attribute values are not supported by the classifier.");
		}
	}

	@Override
	public WekaInstances getData() {
		return (WekaInstances) super.getData();
	}

	public Instances getInstancesInWekaFormat() {
		return this.getData().getInstances();
	}

	@Override
	public String getLoggerName() {
		return this.logger.getName();
	}

	@Override
	public void setLoggerName(final String name) {
		this.logger = LoggerFactory.getLogger(name);
	}
}