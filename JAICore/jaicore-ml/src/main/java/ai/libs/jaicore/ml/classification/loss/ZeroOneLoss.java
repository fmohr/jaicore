package ai.libs.jaicore.ml.classification.loss;

import org.api4.java.ai.ml.loss.classification.IClassificationLossFunction;

/**
 *
 * @author mwever
 */
public class ZeroOneLoss implements IClassificationLossFunction {

	@Override
	public double loss(final String expected, final String actual) {
		return expected.equals(actual) ? 0.0 : 1.0;
	}

}
