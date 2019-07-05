package ai.libs.jaicore.search.model.travesaltree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.libs.jaicore.search.core.interfaces.GraphGenerator;
import ai.libs.jaicore.search.structure.graphgenerator.GoalTester;
import ai.libs.jaicore.search.structure.graphgenerator.RootGenerator;
import ai.libs.jaicore.search.structure.graphgenerator.SuccessorGenerator;

/**
 * Graph generator that uses another graph generator as a basis by reducing the
 * graph generated by the basis generator so that it does not contain long
 * chains of nodes anymore, that is successors of a node are skipped while a
 * node only has 1 successor. Usage: Create a new ReducedGraphGenerator object
 * and swap all ocurrences of the graph generator it uses as a basis with the
 * reduced graph generator.
 *
 * @author Helena Graf
 *
 * @param <T>
 * @param <A>
 */
public class ReducedGraphGenerator<T, A> implements GraphGenerator<T, A> {

	private GraphGenerator<T, A> basis;

	/**
	 * Create a new ReducedGraphGenerator that uses the given graph generator as a
	 * basis.
	 *
	 * @param basis
	 *            the graph generator to use as a basis
	 */
	public ReducedGraphGenerator(final GraphGenerator<T, A> basis) {
		this.basis = basis;
	}

	@Override
	public RootGenerator<T> getRootGenerator() {
		return this.basis.getRootGenerator();
	}

	@Override
	public SuccessorGenerator<T, A> getSuccessorGenerator() {
		return new SuccessorGenerator<T, A>() {

			private SuccessorGenerator<T, A> generator = ReducedGraphGenerator.this.basis.getSuccessorGenerator();

			/**
			 * Expands the node recursively while it only has one successor, until the end
			 * of the branch or a split point in the graph is reached.
			 *
			 * @param node
			 *            The node to expand
			 * @return The fully refined node
			 * @throws InterruptedException
			 */
			public NodeExpansionDescription<T, A> reduce(final NodeExpansionDescription<T, A> node)
					throws InterruptedException {
				List<NodeExpansionDescription<T, A>> sucessors = this.generator.generateSuccessors(node.getTo());
				List<NodeExpansionDescription<T, A>> previous = Arrays.asList(node);
				while (sucessors.size() == 1) {
					previous = sucessors;
					sucessors = this.generator.generateSuccessors(sucessors.get(0).getTo());
				}
				return previous.get(0);
			}

			@Override
			public List<NodeExpansionDescription<T, A>> generateSuccessors(final T node) throws InterruptedException {
				List<NodeExpansionDescription<T, A>> successors = this.generator.generateSuccessors(node);
				// Skip through nodes with 1 successor to find
				while (successors.size() == 1) {
					List<NodeExpansionDescription<T, A>> previous = successors;
					successors = this.generator.generateSuccessors(successors.get(0).getTo());
					if (successors.isEmpty()) {
						return previous;
					}
				}

				List<NodeExpansionDescription<T, A>> reducedSuccessors = new ArrayList<>();
				for (NodeExpansionDescription<T, A> successor : successors) {
					reducedSuccessors.add(this.reduce(successor));
				}
				return reducedSuccessors;
			}
		};
	}

	@Override
	public GoalTester<T> getGoalTester() {
		return this.basis.getGoalTester();
	}
}
