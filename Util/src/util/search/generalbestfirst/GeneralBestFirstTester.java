package util.search.generalbestfirst;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import util.graph.Graph;
import util.graphvisualizer.SimpleGraphVisualizationWindow;
import util.search.core.GraphGenerator;
import util.search.core.Node;
import util.search.core.NodeEvaluator;
import util.search.core.NodeExpansionDescription;
import util.search.core.NodeType;
import util.search.core.OrNode;
import util.search.graphgenerator.GoalTester;
import util.search.graphgenerator.RootGenerator;
import util.search.graphgenerator.SuccessorGenerator;

public class GeneralBestFirstTester {
	
	private static final boolean VISUALIZE = true;

	static class GameNode {
		
		final boolean active;
		final int remaining;
		public GameNode(boolean active, int remaining) {
			super();
			this.active = active;
			this.remaining = remaining;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (active ? 1231 : 1237);
			result = prime * result + remaining;
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
			GameNode other = (GameNode) obj;
			if (active != other.active)
				return false;
			if (remaining != other.remaining)
				return false;
			return true;
		}
		
		@Override
		public String toString() {
			return "GameNode [active=" + active + ", remaining=" + remaining + "]";
		}
	}
	
	static class GameAction {
		final String name;

		public GameAction(String name) {
			super();
			this.name = name;
		}
	}

	@Test
	public void test() {
		
		GraphGenerator<GameNode, GameAction> gen = new GraphGenerator<GeneralBestFirstTester.GameNode, GeneralBestFirstTester.GameAction>() {

			@Override
			public RootGenerator<GameNode> getRootGenerator() {
				return () -> Arrays.asList(new GameNode[]{new GameNode(true, 20)});
			}

			@Override
			public SuccessorGenerator<GameNode, GameAction> getSuccessorGenerator() {
				return n -> {
					
					if (n instanceof OrNode) { 
						List<NodeExpansionDescription<GameNode,GameAction>> successors = new ArrayList<>();
						GameNode g = n.getPoint();
						for (int i = 0; i < 4; i++)
							if (g.remaining > i)
								successors.add(new NodeExpansionDescription<>(n.getPoint(), new GameNode(false, g.remaining - i - 1), new GameAction("Take " + (i + 1)), NodeType.AND));
						return successors;
					}
					else {
						List<NodeExpansionDescription<GameNode,GameAction>> successors = new ArrayList<>();
						GameNode g = n.getPoint();
						for (int i = 0; i < 2; i++)
							if (g.remaining > i)
								successors.add(new NodeExpansionDescription<>(n.getPoint(), new GameNode(true, g.remaining - i - 1), new GameAction("Enemy takes " + (i + 1)), NodeType.OR));
						return successors;						
					}
				};
			}

			@Override
			public GoalTester<GameNode> getGoalTester() {
				return l -> l.getPoint().active && l.getPoint().remaining == 0;
			}
		};
		
		NodeEvaluator<GameNode, Integer> evaluator = new NodeEvaluator<GameNode,Integer>() {
			@Override
			public Integer f(Node<GameNode,Integer> path) {
				return 0;
			}
		};

		GeneralBestFirst<GameNode,GameAction> gbf = new GeneralBestFirst<GameNode,GameAction>(gen, map -> new ArrayList<>(map.keySet()), l -> 0, evaluator);
		
		
		/* find solution */
		if (VISUALIZE) {
			new SimpleGraphVisualizationWindow<>(gbf.getEventBus());
		}
		Graph<GameNode> solutionGraph = gbf.getSolution();
		assertNotNull(solutionGraph);
		System.out.println("Generated " + gbf.getCreatedCounter() + " nodes.");
		if (VISUALIZE) {
			new SimpleGraphVisualizationWindow<GameNode>(solutionGraph);
			int j = 0;
			int i = 0;
			while (j >= 0)
				i  = i + 1;
		}
	}

}
