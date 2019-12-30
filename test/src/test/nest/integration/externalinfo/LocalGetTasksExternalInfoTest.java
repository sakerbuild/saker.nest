package test.nest.integration.externalinfo;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.integration.execution.ServerStorageTaskTest.SimpleTask;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class LocalGetTasksExternalInfoTest extends ExternalScriptInformationTestCase {
	@Override
	protected void runOnInfoProvider(ExternalScriptInformationProvider infoprovider) {
		assertEquals(infoprovider.getTasks(null).keySet(),
				setOf(TaskName.valueOf("simple.task"), TaskName.valueOf("six.task")));
		assertEquals(infoprovider.getTasks("si").keySet(),
				setOf(TaskName.valueOf("simple.task"), TaskName.valueOf("six.task")));
		assertEquals(infoprovider.getTasks("simple").keySet(), setOf(TaskName.valueOf("simple.task")));
		assertEquals(infoprovider.getTasks("six").keySet(), setOf(TaskName.valueOf("six.task")));
	}

	@Override
	protected void initRepository(SakerRepository repo) throws Exception {
		super.initRepository(repo);
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(getWorkingDirectory()).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("local", "install", "-Unest.server.offline=true", bundleoutdir + "/*.jar");
	}

	@Override
	protected Map<String, String> getUserConfigurationUserParameters() throws Exception {
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:local]");
		return userparams;
	}

}
