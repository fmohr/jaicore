package ai.libs.jaicore.graphvisualizer.plugin;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.api4.java.algorithm.events.serializable.IPropertyProcessedAlgorithmEvent;
import org.api4.java.common.control.ILoggingCustomizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.libs.jaicore.graphvisualizer.events.graph.bus.HandleAlgorithmEventException;
import ai.libs.jaicore.graphvisualizer.events.gui.GUIEvent;
import ai.libs.jaicore.graphvisualizer.plugin.controlbar.ResetEvent;
import ai.libs.jaicore.graphvisualizer.plugin.timeslider.GoToTimeStepEvent;

public abstract class ASimpleMVCPluginController<M extends ASimpleMVCPluginModel<?, ?>, V extends ASimpleMVCPluginView<?, ?, ?>> extends Thread implements IGUIPluginController, ILoggingCustomizable {

	protected static final Logger STATIC_LOGGER = LoggerFactory.getLogger(ASimpleMVCPluginController.class);
	protected Logger logger = LoggerFactory.getLogger("gui.control." + this.getClass().getName());
	private final Queue<IPropertyProcessedAlgorithmEvent> eventQueue;

	private final V view;
	private final M model;

	public ASimpleMVCPluginController(final M model, final V view) {
		super();
		this.model = model;
		this.view = view;
		this.eventQueue = new ConcurrentLinkedQueue<>();
	}

	public M getModel() {
		return this.model;
	}

	public V getView() {
		return this.view;
	}

	@Override
	public final void handleSerializableAlgorithmEvent(final IPropertyProcessedAlgorithmEvent algorithmEvent) throws HandleAlgorithmEventException {
		this.eventQueue.add(algorithmEvent);
	}

	@Override
	public void run() {
		while (true) {
			IPropertyProcessedAlgorithmEvent event = this.eventQueue.poll();
			if (event != null) {
				try {
					this.handleAlgorithmEventInternally(event);
				} catch (HandleAlgorithmEventException e) {
					STATIC_LOGGER.error("An error occurred while handling event {}.", event, e);
				}
			}
		}
	}

	protected abstract void handleAlgorithmEventInternally(IPropertyProcessedAlgorithmEvent algorithmEvent) throws HandleAlgorithmEventException;

	@Override
	public void handleGUIEvent(final GUIEvent guiEvent) {
		if (guiEvent instanceof ResetEvent || guiEvent instanceof GoToTimeStepEvent) {
			this.getModel().clear();
			this.getView().clear();
		}
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
