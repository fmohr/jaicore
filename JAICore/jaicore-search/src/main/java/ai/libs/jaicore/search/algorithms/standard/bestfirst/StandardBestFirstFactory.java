package ai.libs.jaicore.search.algorithms.standard.bestfirst;

import java.util.HashMap;
import java.util.Map;

import org.api4.java.common.control.ILoggingCustomizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.libs.jaicore.logging.ToJSONStringUtil;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.nodeevaluation.AlternativeNodeEvaluator;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.nodeevaluation.INodeEvaluator;
import ai.libs.jaicore.search.core.interfaces.GraphGenerator;
import ai.libs.jaicore.search.probleminputs.GraphSearchWithSubpathEvaluationsInput;

public class StandardBestFirstFactory<N, A, V extends Comparable<V>> extends BestFirstFactory<GraphSearchWithSubpathEvaluationsInput<N, A, V>, N, A, V> implements ILoggingCustomizable {

	private Logger logger = LoggerFactory.getLogger(StandardBestFirstFactory.class);
	private INodeEvaluator<N, V> preferredNodeEvaluator;

	public void setNodeEvaluator(final INodeEvaluator<N, V> nodeEvaluator) {
		this.setProblemInput(new GraphSearchWithSubpathEvaluationsInput<>(this.getInput() != null ? this.getInput().getGraphGenerator() : null, nodeEvaluator));
	}

	public void setGraphGenerator(final GraphGenerator<N, A> graphGenerator) {
		this.setProblemInput(new GraphSearchWithSubpathEvaluationsInput<>(graphGenerator, this.getInput() != null ? this.getInput().getNodeEvaluator() : null));
	}

	public INodeEvaluator<N, V> getPreferredNodeEvaluator() {
		return this.preferredNodeEvaluator;
	}

	public void setPreferredNodeEvaluator(final INodeEvaluator<N, V> preferredNodeEvaluator) {
		this.preferredNodeEvaluator = preferredNodeEvaluator;
	}

	@Override
	public BestFirst<GraphSearchWithSubpathEvaluationsInput<N, A, V>, N, A, V> getAlgorithm() {
		if (this.getInput().getGraphGenerator() == null) {
			throw new IllegalStateException("Cannot produce BestFirst searches before the graph generator is set in the problem.");
		}
		if (this.getInput().getNodeEvaluator() == null) {
			throw new IllegalStateException("Cannot produce BestFirst searches before the node evaluator is set.");
		}

		/* determine search problem */
		GraphSearchWithSubpathEvaluationsInput<N, A, V> problem = this.getInput();
		if (this.preferredNodeEvaluator != null) {
			problem = new GraphSearchWithSubpathEvaluationsInput<N, A, V>(problem.getGraphGenerator(), new AlternativeNodeEvaluator<>(this.preferredNodeEvaluator, problem.getNodeEvaluator()));
		}
		this.logger.debug("Created algorithm input with\n\tgraph generator: {}\n\tnode evaluator: {}", problem.getGraphGenerator(), problem.getNodeEvaluator());
		BestFirst<GraphSearchWithSubpathEvaluationsInput<N, A, V>, N, A, V> search = new BestFirst<>(problem);
		search.setTimeoutForComputationOfF(this.getTimeoutForFInMS(), this.getTimeoutEvaluator());
		if (this.getLoggerName() != null && this.getLoggerName().length() > 0) {
			search.setLoggerName(this.getLoggerName());
		}
		return search;
	}

	@Override
	public String toString() {
		Map<String, Object> fields = new HashMap<>();
		fields.put("preferredNodeEvaluator", this.preferredNodeEvaluator);
		return ToJSONStringUtil.toJSONString(this.getClass().getSimpleName(), fields);
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
