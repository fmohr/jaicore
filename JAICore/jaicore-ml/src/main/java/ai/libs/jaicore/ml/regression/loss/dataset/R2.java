package ai.libs.jaicore.ml.regression.loss.dataset;

import java.util.List;

/**
 * The R^2, aka. the coefficient of determination describes the proportion of the variance in the target variable and the predicted values.
 * The formula of R^2 is as follows:
 * (\sum_i (y^\hat_i - \bar{y})^2) / (\sum_i (y_i - \bar{y})^2)
 *
 *  For predictions not worse than prediting constantly the mean of the target variable, R^2 resides within the [0, 1] interval.
 *  Caution: For worse predictions the coefficient of determination becomes *negative*.
 *
 * @author mwever
 */
public class R2 extends ARegressionMeasure {

	public R2() {
		super();
		// nothing to do here
	}

	@Override
	public double score(final List<? extends Double> expected, final List<? extends Double> actual) {
		this.checkConsistency(expected, actual);
		double meanExpected = expected.stream().mapToDouble(x -> x).average().getAsDouble();
		double sumOfActualSquares = 0.0;
		double sumOfExpectedSquares = 0.0;

		for (int i = 0; i < actual.size(); i++) {
			sumOfActualSquares += Math.pow(actual.get(i) - meanExpected, 2);
			sumOfExpectedSquares += Math.pow(expected.get(i) - meanExpected, 2);
		}
		return sumOfActualSquares / sumOfExpectedSquares;
	}

}
