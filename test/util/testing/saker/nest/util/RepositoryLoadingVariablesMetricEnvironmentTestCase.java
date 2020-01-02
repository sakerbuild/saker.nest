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
package testing.saker.nest.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

public abstract class RepositoryLoadingVariablesMetricEnvironmentTestCase extends VariablesMetricEnvironmentTestCase {
	protected Set<SakerPath> repositoryParameterBundlePaths = Collections.emptySet();

	@Override
	protected void setupParameters(ExecutionParametersImpl params) {
		super.setupParameters(params);

		Path repojarpath = Paths.get(testParameters.get("RepositoryJarPath")).toAbsolutePath();
		String parambundlepaths = testParameters.get("RepositoryParameterBundles");
		String[] parambundles = parambundlepaths.split("[;]+");

		ExecutionRepositoryConfiguration repoconf = ExecutionRepositoryConfiguration.builder()
				.add(new JarFileClassPathLocation(LocalFileProvider.getInstance().getPathKey(repojarpath)),
						new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class), "nest")
				.build();
		params.setRepositoryConfiguration(repoconf);

		Set<String> parambundlepathpaths = new LinkedHashSet<>();
		for (String pb : parambundles) {
			if (ObjectUtils.isNullOrEmpty(pb)) {
				continue;
			}
			parambundlepathpaths.add("//" + pb);
		}
		repositoryParameterBundlePaths = ImmutableUtils.makeImmutableLinkedHashSet(
				Stream.of(parambundles).filter(pb -> !pb.isEmpty()).map(SakerPath::valueOf).toArray(SakerPath[]::new));

		Map<String, String> nparams = new TreeMap<>(params.getUserParameters());
		nparams.put("nest.params.bundles", StringUtils.toStringJoin(";", parambundlepathpaths));
		nparams.put("nest.repository.storage.configuration", getRepositoryStorageConfiguration());
		params.setUserParameters(nparams);
	}

	protected String getRepositoryStorageConfiguration() {
		return "[:params]";
	}
}
