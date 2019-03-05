package jaicore.planning.hierarchical.problems.htn;

import jaicore.basic.algorithm.reduction.AlgorithmicProblemReduction;
import jaicore.planning.core.Plan;
import jaicore.search.model.other.SearchGraphPath;
import jaicore.search.probleminputs.GraphSearchInput;

public interface IHierarchicalPlanningGraphGeneratorDeriver<IPlanning extends IHTNPlanningProblem, N, A> extends AlgorithmicProblemReduction<IPlanning, Plan, GraphSearchInput<N, A>, SearchGraphPath<N, A>> {

}
