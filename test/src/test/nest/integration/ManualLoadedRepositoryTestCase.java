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
package test.nest.integration;

import java.nio.file.Path;
import java.util.Map;

import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.util.NestIntegrationTestUtils;

public abstract class ManualLoadedRepositoryTestCase extends SakerTestCase {
	protected Map<String, String> parameters;

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		this.parameters = parameters;
		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(LocalFileProvider.getInstance(),
						NestIntegrationTestUtils.getTestParameterNestRepositoryJar(parameters)));

		ServiceLoaderClassPathServiceEnumerator<SakerRepositoryFactory> serviceloader = new ServiceLoaderClassPathServiceEnumerator<>(
				SakerRepositoryFactory.class);

		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(getStorageDirectory());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath())) {
			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				runTestOnRepo(repo);
			}
		}
	}

	protected Path getStorageDirectory() {
		return EnvironmentTestCase.getStorageDirectoryPath();
	}

	protected abstract void runTestOnRepo(SakerRepository repo) throws Exception;

}
