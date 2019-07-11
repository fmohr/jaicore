package ai.libs.jaicore.search.testproblems.cannibals;

import java.util.ArrayList;
import java.util.List;

import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.IGraphGenerator;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.NodeExpansionDescription;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.NodeGoalTester;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.NodeType;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.RootGenerator;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.SingleRootGenerator;
import org.api4.java.ai.graphsearch.problem.implicit.graphgenerator.SuccessorGenerator;

import ai.libs.jaicore.testproblems.cannibals.CannibalProblem;

public class CannibalGraphGenerator implements IGraphGenerator<CannibalProblem, String> {

	private final CannibalProblem initState;

	public CannibalGraphGenerator(final CannibalProblem initState) {
		super();
		this.initState = initState;
	}

	@Override
	public RootGenerator<CannibalProblem> getRootGenerator() {
		return new SingleRootGenerator<CannibalProblem>() {

			@Override
			public CannibalProblem getRoot() {
				return CannibalGraphGenerator.this.initState;
			}
		};
	}

	@Override
	public SuccessorGenerator<CannibalProblem, String> getSuccessorGenerator() {
		return new SuccessorGenerator<CannibalProblem, String>() {

			@Override
			public List<NodeExpansionDescription<CannibalProblem, String>> generateSuccessors(final CannibalProblem node) throws InterruptedException {
				List<NodeExpansionDescription<CannibalProblem, String>> successors = new ArrayList<>();
				int ml = node.getMissionariesOnLeft();
				int mr = node.getMissionariesOnRight();
				int cl = node.getCannibalsOnLeft();
				int cr = node.getCannibalsOnRight();

				/* first consider the case that the boat is on the left */
				if (node.isBoatOnLeft()) {
					if (ml >= 2) {
						CannibalProblem candidate = new CannibalProblem(false, ml - 2, cl, mr + 2, cr);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "2m->", NodeType.OR));
						}
					}
					if (ml >= 1) {
						CannibalProblem candidate = new CannibalProblem(false, ml - 1, cl, mr + 1, cr);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1m->", NodeType.OR));
						}
					}
					if (cl >= 1) {
						CannibalProblem candidate = new CannibalProblem(false, ml, cl - 1, mr, cr + 1);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1c->", NodeType.OR));
						}
					}
					if (ml >= 1 && cl >= 1) {
						CannibalProblem candidate = new CannibalProblem(false, ml - 1, cl - 1, mr + 1, cr + 1);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1m1c->", NodeType.OR));
						}
					}
					if (cl >= 2) {
						CannibalProblem candidate = new CannibalProblem(false, ml, cl - 2, mr, cr + 2);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "2c->", NodeType.OR));
						}
					}
				}

				/* now consider the cases that the boat is on the right */
				else {
					if (mr >= 2) {
						CannibalProblem candidate = new CannibalProblem(true, ml + 2, cl, mr - 2, cr);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "2m<-", NodeType.OR));
						}
					}
					if (mr >= 1) {
						CannibalProblem candidate = new CannibalProblem(true, ml + 1, cl, mr - 1, cr);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1m<-", NodeType.OR));
						}
					}
					if (cr >= 1) {
						CannibalProblem candidate = new CannibalProblem(true, ml, cl + 1, mr, cr - 1);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1c<-", NodeType.OR));
						}
					}
					if (mr >= 1 && cr >= 1) {
						CannibalProblem candidate = new CannibalProblem(true, ml + 1, cl + 1, mr - 1, cr - 1);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "1m1c<-", NodeType.OR));
						}
					}
					if (cr >= 2) {
						CannibalProblem candidate = new CannibalProblem(true, ml, cl + 2, mr, cr - 2);
						CannibalGraphGenerator.this.checkThatNumberOfPeopleHasNotChanged(node, candidate);
						if (!candidate.isLost()) {
							successors.add(new NodeExpansionDescription<>(candidate, "2c<-", NodeType.OR));
						}
					}
				}
				return successors;
			}
		};
	}

	private void checkThatNumberOfPeopleHasNotChanged(final CannibalProblem a, final CannibalProblem b) {
		if (a.getTotalNumberOfPeople() != b.getTotalNumberOfPeople()) {
			throw new IllegalStateException("Number of people has changed from " + a.getTotalNumberOfPeople() + " to " + b.getTotalNumberOfPeople());
		}
	}

	@Override
	public NodeGoalTester<CannibalProblem, String> getGoalTester() {
		return CannibalProblem::isWon;
	}
}
