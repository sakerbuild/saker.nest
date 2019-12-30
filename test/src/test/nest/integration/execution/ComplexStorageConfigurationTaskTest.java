package test.nest.integration.execution;

import saker.build.runtime.repository.RepositoryOperationException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ComplexStorageConfigurationTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));

		testConfig(":local");
		testConfig("local: local");
		testConfig("[p1:params, p2:params]");
		testConfig("[[p3:params, :local], p4:params]");
		testConfig("[p3:params, [:local], p4:params]");
		testConfig("[[p5:params, :local], [p6:params, :local]]");
		testConfig("[[p5:params, l:local], [p6:params, l:]]");
		testConfig("[[p5:params, l:], [p6:params, l:local]]");
		assertException(RepositoryOperationException.class,
				() -> testConfig("[[p5:params, :local], [p6:params, local:, px:params]]"));
		assertException(RepositoryOperationException.class,
				() -> testConfig("[[p5:params, :local], [p6:params, local:, :server]]"));
		testConfig("[[p5:params, :local], [p6:params, :local], px:params]");
		testConfig("[[p5:params, :local, px:params], [p6:params, :local, px:]]");
	}

	private void testConfig(String config) throws Throwable {
		NestIntegrationTestUtils.addUserParam(parameters, "nest.repository.storage.configuration", config);
		runScriptTask("build");
	}

}
