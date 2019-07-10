package ai.libs.jaicore.ea.algorithm.moea.moeaframework.event;

import org.api4.java.algorithm.events.AAlgorithmEvent;

import ai.libs.jaicore.ea.algorithm.moea.moeaframework.MOEAFrameworkAlgorithmResult;

public class MOEAFrameworkAlgorithmResultEvent extends AAlgorithmEvent {

	private final MOEAFrameworkAlgorithmResult result;

	public MOEAFrameworkAlgorithmResultEvent(final String algorithmId, final MOEAFrameworkAlgorithmResult result) {
		super(algorithmId);
		this.result = result;
	}

	public MOEAFrameworkAlgorithmResult getResult() {
		return this.result;
	}

}
