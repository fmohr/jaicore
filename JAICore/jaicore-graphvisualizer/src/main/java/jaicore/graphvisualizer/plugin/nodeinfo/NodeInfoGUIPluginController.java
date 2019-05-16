package jaicore.graphvisualizer.plugin.nodeinfo;

import jaicore.basic.algorithm.events.serializable.PropertyProcessedAlgorithmEvent;
import jaicore.graphvisualizer.events.graph.bus.HandleAlgorithmEventException;
import jaicore.graphvisualizer.events.gui.GUIEvent;
import jaicore.graphvisualizer.plugin.IGUIPluginController;
import jaicore.graphvisualizer.plugin.graphview.NodeClickedEvent;

public class NodeInfoGUIPluginController implements IGUIPluginController {

	private NodeInfoGUIPluginModel model;

	public NodeInfoGUIPluginController(NodeInfoGUIPluginModel model) {
		this.model = model;
	}

	@Override
	public void handleSerializableAlgorithmEvent(PropertyProcessedAlgorithmEvent algorithmEvent) throws HandleAlgorithmEventException {
		Object rawNodeDisplayInfoProperty = algorithmEvent.getProperty(NodeDisplayInfoAlgorithmEventPropertyComputer.NODE_DISPLAY_INFO_PROPERTY_NAME);
		Object rawNodeInfoProperty = algorithmEvent.getProperty(NodeInfoAlgorithmEventPropertyComputer.NODE_INFO_PROPERTY_NAME);
		if (rawNodeDisplayInfoProperty != null && rawNodeInfoProperty != null) {
			NodeInfo nodeInfo = (NodeInfo) rawNodeInfoProperty;
			String nodeInfoText = (String) rawNodeDisplayInfoProperty;
			model.addNodeIdToNodeInfoMapping(nodeInfo.getMainNodeId(), nodeInfoText);
		}
	}

	@Override
	public void handleGUIEvent(GUIEvent guiEvent) {
		if (NodeClickedEvent.class.isInstance(guiEvent)) {
			NodeClickedEvent nodeClickedEvent = (NodeClickedEvent) guiEvent;
			String searchGraphNodeCorrespondingToClickedViewGraphNode = nodeClickedEvent.getSearchGraphNode();
			model.setCurrentlySelectedNode(searchGraphNodeCorrespondingToClickedViewGraphNode);
		}
	}

}
