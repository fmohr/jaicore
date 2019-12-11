package ai.libs.mlplan.multilabel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ai.libs.hasco.exceptions.ComponentInstantiationFailedException;
import ai.libs.hasco.model.ComponentInstance;
import ai.libs.hasco.model.ComponentUtil;
import ai.libs.hasco.serialization.ComponentLoader;
import ai.libs.jaicore.basic.FileUtil;
import ai.libs.jaicore.ml.weka.classification.learner.IWekaClassifier;

public class MekaPipelineFactoryTest {
	private static final File SSC = FileUtil.getExistingFileWithHighestPriority(ML2PlanMekaPathConfig.RES_SSC, ML2PlanMekaPathConfig.FS_SSC);

	private static ComponentLoader cl;
	private static MekaPipelineFactory mpf;

	@BeforeClass
	public static void setup() throws IOException {
		cl = new ComponentLoader(SSC);
		mpf = new MekaPipelineFactory();
	}

	@Before
	public void init() {
	}

	@Test
	public void testRandomComponentInstantiation() throws ComponentInstantiationFailedException {
		Collection<ComponentInstance> algorithmSelections = ComponentUtil.getAllAlgorithmSelectionInstances("MLClassifier", cl.getComponents());
		List<ComponentInstance> list = new ArrayList<>(algorithmSelections);
		System.out.println(list.size());

		for (int i = 0; i < 10; i++) {
			ComponentInstance ci = list.get(new Random().nextInt(list.size()));
			System.out.println(ci);
			IWekaClassifier c = mpf.getComponentInstantiation(ci);
		}
	}

}