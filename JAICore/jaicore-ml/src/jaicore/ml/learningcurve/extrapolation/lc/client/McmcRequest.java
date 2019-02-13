package jaicore.ml.learningcurve.extrapolation.lc.client;

import java.util.List;

/**
 * This class describes the request that is sent to the McmcService. It contains
 * the x- and y-values of the anchor points.
 * 
 * @author Felix Weiland
 *
 */
public class McmcRequest {

	private List<Integer> xValues;

	private List<Double> yValues;

	public List<Integer> getxValues() {
		return xValues;
	}

	public void setxValues(List<Integer> xValues) {
		this.xValues = xValues;
	}

	public List<Double> getyValues() {
		return yValues;
	}

	public void setyValues(List<Double> yValues) {
		this.yValues = yValues;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((xValues == null) ? 0 : xValues.hashCode());
		result = prime * result + ((yValues == null) ? 0 : yValues.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		McmcRequest other = (McmcRequest) obj;
		if (xValues == null) {
			if (other.xValues != null)
				return false;
		} else if (!xValues.equals(other.xValues))
			return false;
		if (yValues == null) {
			if (other.yValues != null)
				return false;
		} else if (!yValues.equals(other.yValues))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "McmcRequest [xValues=" + xValues + ", yValues=" + yValues + "]";
	}

}
