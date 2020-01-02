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
package test.nest.integration.execution;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.nest.NestRepositoryFactory;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.nest.util.NestIntegrationTestUtils;

public class NestExecutionTestUtils {
	private NestExecutionTestUtils() {
		throw new UnsupportedOperationException();
	}

	public static ExecutionRepositoryConfiguration createRepositoryConfiguration(Map<String, String> testParameters) {
		return ExecutionRepositoryConfiguration.builder()
				.add(new JarFileClassPathLocation(LocalFileProvider.getInstance()
						.getPathKey(NestIntegrationTestUtils.getTestParameterNestRepositoryJar(testParameters))),
						new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class),
						NestRepositoryFactory.IDENTIFIER)
				.build();
	}

	public static ExecutionRepositoryConfiguration createPrivateRepositoryConfiguration(
			Map<String, String> testParameters, UUID uuid) throws IOException {
		MemoryFileProvider memfp = new MemoryFileProvider(Collections.singleton("reporoot:"), uuid);
		ByteArrayRegion allbytes = LocalFileProvider.getInstance()
				.getAllBytes(NestIntegrationTestUtils.getTestParameterNestRepositoryJar(testParameters));
		SakerPath memfilepath = SakerPath.valueOf("reporoot:/nestrepo.jar");
		memfp.putFile(memfilepath, allbytes);
		return ExecutionRepositoryConfiguration.builder()
				.add(new JarFileClassPathLocation(new SimpleProviderHolderPathKey(memfp, memfilepath)),
						new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class),
						NestRepositoryFactory.IDENTIFIER)
				.build();
	}
}
