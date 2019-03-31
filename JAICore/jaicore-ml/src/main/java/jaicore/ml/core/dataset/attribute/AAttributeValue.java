package jaicore.ml.core.dataset.attribute;

/**
 * An abstract class for attribute values implementing basic functionality to
 * store its value as well as getter and setters.
 *
 * @author wever
 *
 * @param <D>
 *            The domain of values.
 */
public abstract class AAttributeValue<D> implements IAttributeValue<D> {

	/** The value of this attribute. */
	private D value;

	/** The attribute type of this attribute value. */
	private final IAttributeType<D> type;

	/**
	 * Constructor creating a new attribute value for a certain type. The value
	 * remains unset.
	 *
	 * @param type
	 *            The type of the attribute value.
	 */
	protected AAttributeValue(final IAttributeType<D> type) {
		super();
		this.type = type;
	}

	/**
	 * Constructor creating a new attribute value for a certain type together with a
	 * value.
	 *
	 * @param type
	 *            The type of the attribute value.
	 * @param value
	 *            The value of this attribute.
	 */
	protected AAttributeValue(final IAttributeType<D> type, final D value) {
		this(type);
		this.setValue(value);
	}

	/**
	 * @return The attribute type of this attribute value.
	 */
	public IAttributeType<D> getType() {
		return this.type;
	}

	@Override
	public D getValue() {
		return this.value;
	}

	@Override
	public void setValue(final D value) {
		if (!this.type.isValidValue(value)) {
			throw new IllegalArgumentException(
					"The attribute value does not conform the domain of the attribute type.");
		}
		this.value = value;
	}
	
	
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;

		if (this == obj)
			return true;

		if (getClass() != obj.getClass())
			return false;

		IAttributeValue<?> other = (IAttributeValue<?>) obj;
		return getValue().equals(other.getValue());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AAttributeValue other = (AAttributeValue) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

}
