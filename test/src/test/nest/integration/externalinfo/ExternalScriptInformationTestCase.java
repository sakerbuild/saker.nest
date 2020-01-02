/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package test.nest.integration.externalinfo;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.EnvironmentParameters;
import saker.build.runtime.environment.ForwardingImplSakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.SimpleRepositoryBuildEnvironment;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.nest.NestRepositoryFactory;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.util.NestIntegrationTestUtils;

public abstract class ExternalScriptInformationTestCase extends SakerTestCase {
	protected SakerEnvironmentImpl environment;

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(LocalFileProvider.getInstance(),
						NestIntegrationTestUtils.getTestParameterNestRepositoryJar(parameters)));

		ServiceLoaderClassPathServiceEnumerator<SakerRepositoryFactory> serviceloader = new ServiceLoaderClassPathServiceEnumerator<>(
				SakerRepositoryFactory.class);

		try (SakerEnvironmentImpl environment = new SakerEnvironmentImpl(
				EnvironmentParameters.builder(EnvironmentTestCase.getSakerJarPath())
						.setStorageDirectory(EnvironmentTestCase.getStorageDirectoryPath()).build())) {
			this.environment = environment;
			initEnvironment(environment);

			try (SakerRepository repo = environment.getRepositoryManager().loadRepository(repoloc, serviceloader)) {
				initRepository(repo);
				Map<String, String> userparams = getUserConfigurationUserParameters();
				ExecutionPathConfiguration pathconfig = ExecutionPathConfiguration
						.local(SakerPath.valueOf(EnvironmentTestCase.getTestingBaseWorkingDirectory()
								.resolve(getClass().getName().replace('.', '/'))));
				try (BuildRepository buildrepo = repo.createBuildRepository(new SimpleRepositoryBuildEnvironment(
						new ForwardingImplSakerEnvironment(environment), new ClassLoaderResolverRegistry(), userparams,
						pathconfig, NestRepositoryFactory.IDENTIFIER))) {
					ExternalScriptInformationProvider infoprovider = buildrepo.getScriptInformationProvider();
					assertNonNull(infoprovider);
					runOnInfoProvider(infoprovider);
				}
			}

		}
	}

	protected void initEnvironment(SakerEnvironmentImpl environment) {
	}

	protected void initRepository(SakerRepository repo) throws Exception {
	}

	protected Path getBuildDirectory() {
		Path testcontetbasebuilddir = EnvironmentTestCase.getTestingBaseBuildDirectory();
		if (testcontetbasebuilddir == null) {
			return null;
		}
		return testcontetbasebuilddir.resolve(getClass().getName().replace('.', '/'));
	}

	protected Path getWorkingDirectory() {
		Path testcontentbaseworkingdir = EnvironmentTestCase.getTestingBaseWorkingDirectory();
		if (testcontentbaseworkingdir == null) {
			return null;
		}
		return testcontentbaseworkingdir.resolve(getClass().getName().replace('.', '/'));
	}

	protected Map<String, String> getUserConfigurationUserParameters() throws Exception {
		return new TreeMap<>();
	}

	protected abstract void runOnInfoProvider(ExternalScriptInformationProvider infoprovider) throws Exception;
}
