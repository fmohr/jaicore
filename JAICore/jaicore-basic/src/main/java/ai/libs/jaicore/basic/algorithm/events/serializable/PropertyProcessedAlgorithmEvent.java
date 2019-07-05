package ai.libs.jaicore.basic.algorithm.events.serializable;

import java.io.Serializable;

import ai.libs.jaicore.basic.algorithm.events.AlgorithmEvent;

public interface PropertyProcessedAlgorithmEvent extends Serializable {

	public String getEventName();

	public String getCompleteOriginalEventName();

	public Object getProperty(String propertyName);

	public <N> N getProperty(String propertyName, Class<N> expectedClassToBeReturned) throws ClassCastException;

	public AlgorithmEvent getOriginalEvent();

	public boolean correspondsToEventOfClass(Class<?> eventClass);

	public long getTimestampOfEvent();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}