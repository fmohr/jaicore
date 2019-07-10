package ai.libs.jaicore.basic.algorithm;

import org.api4.java.algorithm.IAlgorithmFactory;

public abstract class AAlgorithmFactory<I,O> implements IAlgorithmFactory<I, O> {

	private I input;

	public void setProblemInput(final I problemInput) {
		this.input = problemInput;
	}

	public I getInput() {
		return this.input;
	}
}
