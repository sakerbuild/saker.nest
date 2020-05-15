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
package saker.nest.jdkutil;

import java.io.IOException;
import java.lang.ModuleLayer.Controller;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Modifier;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import com.sun.source.util.Trees;

public class JavaToolsModulePatcher {
	private static final String MODULE_NAME_JDK_COMPILER = "jdk.compiler";

	private JavaToolsModulePatcher() {
		throw new UnsupportedOperationException();
	}

	//throws IOException for compatibility with Java 8 version
	public static ClassLoader getJavaToolsClassLoader() throws IOException {
		ClassLoader result = JAVA_TOOLS_CLASSLOADER;
		if (result != null) {
			return result;
		}
		synchronized (JavaToolsModulePatcher.class) {
			result = JAVA_TOOLS_CLASSLOADER;
			if (result != null) {
				return result;
			}
			result = createJavaToolsClassLoader();
			JAVA_TOOLS_CLASSLOADER = result;
			return result;
		}
	}

	public static ClassLoader getJavaToolsClassLoaderIfLoaded() {
		return JAVA_TOOLS_CLASSLOADER;
	}

	public static boolean isDifferentFromDefaultJavaToolsClassLoader() {
		return true;
	}

	private static volatile ClassLoader JAVA_TOOLS_CLASSLOADER = createJavaToolsClassLoader();

	private static ClassLoader createJavaToolsClassLoader() throws AssertionError {
		//open module so everyone can access it
		ModuleDescriptor.Builder njdkbuilder = ModuleDescriptor.newModule(MODULE_NAME_JDK_COMPILER,
				Collections.singleton(Modifier.OPEN));
		ModuleDescriptor jdkcompilerdescriptor = Trees.class.getModule().getDescriptor();
		for (Provides p : jdkcompilerdescriptor.provides()) {
			njdkbuilder.provides(p);
		}
		njdkbuilder.packages(jdkcompilerdescriptor.packages());
		for (String u : jdkcompilerdescriptor.uses()) {
			njdkbuilder.uses(u);
		}
		for (Requires r : jdkcompilerdescriptor.requires()) {
			njdkbuilder.requires(r);
		}
		jdkcompilerdescriptor.mainClass().ifPresent(njdkbuilder::mainClass);
		jdkcompilerdescriptor.version().ifPresent(njdkbuilder::version);

		ResolvedModule jdkcompilerresolvedmodule = null;
		ModuleLayer bootlayer = ModuleLayer.boot();
		Configuration bootlayerconfig = bootlayer.configuration();
		for (ResolvedModule rm : bootlayerconfig.modules()) {
			if (MODULE_NAME_JDK_COMPILER.equals(rm.name())) {
				jdkcompilerresolvedmodule = rm;
				break;
			}
		}
		if (jdkcompilerresolvedmodule == null) {
			throw new AssertionError("Module not found: " + MODULE_NAME_JDK_COMPILER);
		}
		ModuleReference jdkcompilermoduleref = jdkcompilerresolvedmodule.reference();
		ModuleReference njdkmoduleref = new ModuleReference(njdkbuilder.build(),
				jdkcompilermoduleref.location().orElse(null)) {
			@Override
			public ModuleReader open() throws IOException {
				return jdkcompilermoduleref.open();
			}
		};
		ModuleFinder jdkcompilermodulefinder = new ModuleFinder() {
			@Override
			public Set<ModuleReference> findAll() {
				return Collections.singleton(njdkmoduleref);
			}

			@Override
			public Optional<ModuleReference> find(String name) {
				if (!MODULE_NAME_JDK_COMPILER.equals(name)) {
					return Optional.empty();
				}
				return Optional.of(njdkmoduleref);
			}
		};
		Configuration myconfig = Configuration.resolve(jdkcompilermodulefinder, Arrays.asList(bootlayerconfig),
				jdkcompilermodulefinder, Collections.singleton(MODULE_NAME_JDK_COMPILER));
		Controller controller = ModuleLayer.defineModulesWithOneLoader(myconfig, Arrays.asList(bootlayer), null);
		Module newjdkcompilermodule = controller.layer().findModule(MODULE_NAME_JDK_COMPILER).get();
		return newjdkcompilermodule.getClassLoader();
	}

}
