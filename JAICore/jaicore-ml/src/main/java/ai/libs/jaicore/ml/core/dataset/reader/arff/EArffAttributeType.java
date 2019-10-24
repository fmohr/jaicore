package ai.libs.jaicore.ml.core.dataset.reader.arff;

public enum EArffAttributeType {

	NOMINAL("nominal"), NUMERIC("numeric");

	private final String name;

	private EArffAttributeType(final String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

}
