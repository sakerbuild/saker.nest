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
