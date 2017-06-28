package util.planning.model.task.rtn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.logic.Literal;
import util.planning.model.ceoc.CEOCOperation;
import util.planning.model.task.ceocstn.CEOCSTNPlanningProblem;
import util.planning.model.task.ceocstn.OCMethod;
import util.planning.model.task.stn.TaskNetwork;

public class StandardProblemFactory {
	
	public static RTNPlanningProblem getNestedDichotomyCreationProblem(String rootClusterName, List<String> classes) {
		
		/* get problem on the basis of the standard CEOC STN problem */
		CEOCSTNPlanningProblem problem = util.planning.model.task.ceocstn.StandardProblemFactory.getNestedDichotomyCreationProblem(rootClusterName, classes);
		
		/* now derive RTN methods from the CEOC STN methods */
		Collection<CEOCOperation> operations = problem.getDomain().getOperations();
		Collection<RTNMethod> methods = new ArrayList<>();
		for (OCMethod method : problem.getDomain().getMethods()) {
			Map<Literal,StateReducer> reducers = new HashMap<>();
			
			RTaskNetwork network = new RTaskNetwork(method.getNetwork(), reducers);
			RTNMethod rtnmethod = new RTNMethod(method.getName(), method.getParameters(), method.getTask(), method.getPrecondition(), network, method.isLonely(), method.getOutputs());
			methods.add(rtnmethod);
		}
		RTNPlanningDomain domain = new RTNPlanningDomain(operations, methods);
		RTaskNetwork network = new RTaskNetwork(new TaskNetwork("refine('" + rootClusterName + "')"), new HashMap<>());
		return new RTNPlanningProblem(domain, problem.getInit(), network);
	}
}
