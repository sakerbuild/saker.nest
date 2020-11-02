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
package saker.nest.action.main;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.RepositoryException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleKey;
import saker.nest.bundle.NestRepositoryBundleClassLoader;
import saker.nest.bundle.lookup.BundleVersionLookupResult;
import saker.nest.exc.BundleLoadingFailedException;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.PositionalParameter;
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.InvalidArgumentValueException;
import sipka.cmdline.runtime.MissingArgumentException;
import sipka.cmdline.runtime.ParseUtil;
import sipka.cmdline.runtime.ParsingIterator;

/**
 * <pre>
 * Invokes the main method of a specified bundle.
 * 
 * The command will load the bundle with the given name
 * and invoke the main method of it. The command can be
 * configured similarly as you can configure the repository
 * for build executions.
 * 
 * Any extra arguments specified will be passed to the main
 * method of the bundle.
 * 
 * The command will search for the methods intMain(String[]) or
 * main(String[]). It will invoke the one it first finds. If the 
 * invoked method is declared to return an integer, then that will 
 * be used as an exit code of the process. Use the -system-exit flag 
 * to control how this value is interpreted.
 * </pre>
 */
public class MainCommand {

	private static final String PARAM_NAME_BUNDLE = "-bundle";
	private static final String PARAM_NAME_U = "-U";

	public static enum SystemExitConfiguration {
		ALWAYS,
		ON_EXCEPTION,
		FORWARD,
		NEVER,
	}

	/**
	 * <pre>
	 * Specifies the name of the class to invoke.
	 * 
	 * This parameter is used to specify the name of the Java
	 * class that is loaded and used to invoke its main method.
	 * 
	 * If this argument is not specified, the Main-Class attribute
	 * of the bundle manifest will be used.
	 * </pre>
	 */
	@Parameter("-class")
	public String mainClassName;

	/**
	 * <pre>
	 * The identifier of the bundle which should be invoked.
	 * 
	 * The bundle with the specified identifier will be loaded
	 * (possibly downloaded from the internet) and the main class
	 * of it will be invoked.
	 * 
	 * If the specified bundle identifier has no version qualifier,
	 * then it will be resolved to the most recent version of it.
	 * 
	 * If this parameter is not specified, the first value for the
	 * arguments parameter is used as bundle identifier.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_BUNDLE)
	@Converter(method = "toBundleIdentifier")
	public BundleIdentifier bundle;

	/**
	 * <pre>
	 * The arguments that are passed as the input to the main method
	 * of the invoked class.
	 * 
	 * Zero, one, or more strings that are directly passed as the
	 * String[] argument of the main method.
	 * 
	 * If no -bundle is specified, the bundle identifier is determined
	 * using the first value of this parameter. In that case that value
	 * will not be part of the input arguments.
	 * </pre>
	 */
	@Parameter("arguments...")
	@PositionalParameter(-1)
	@Converter(method = "parseRemainingCommand")
	public List<String> arguments = new ArrayList<>();

	private Map<String, String> userParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Specifies the identifier of the repository.
	 * 
	 * The identifier is used to properly determine the 
	 * configuration user parameters from the -U arguments.
	 * 
	 * It is "nest" by default.
	 * </pre>
	 */
	@Parameter("-repo-id")
	public String repositoryId = NestRepositoryFactory.IDENTIFIER;

	/**
	 * <pre>
	 * Specifies how and when the current process should be exited.
	 * 
	 * This parameter controls whether or not the current process 
	 * should be exited at the end of the main method invocation.
	 * 
	 * Exit codes are determined the following way:
	 *  -  0: if the main method invocation is successful
	 *  - -1: if an exception was thrown
	 *  - int: the value returned by the main method (if any)
	 *  - none: if there is no exit code
	 * 
	 * The values may be the following:
	 *  - always
	 *    The current process will always be exited.
	 *    0 exit code will be used if there was none.
	 *  - on_exception
	 *    The process will only exit, if there was an exception
	 *    thrown by the main method.
	 *    The exit code will be -1.
	 *  - forward
	 *    The process will exit, if there was an exception, or
	 *    the main method returns an integer.
	 *  - never
	 *    The command will never cause the current process to exit.
	 *    (Note that the main invocation may still do so.)
	 * </pre>
	 */
	@Parameter("-system-exit")
	public SystemExitConfiguration callSystemExit = SystemExitConfiguration.NEVER;

	public void call(ExecuteActionCommand execute) throws Exception {
		Thread currentthread = Thread.currentThread();
		Integer exitCode = null;
		BundleIdentifier bundleid = getBundleIdentifier();
		ClassLoader contextcl = currentthread.getContextClassLoader();
		currentthread.setContextClassLoader(null);
		try (ConfiguredRepositoryStorage configuredstorage = ConfiguredRepositoryStorage.forRepositoryAction(
				execute.repository, repositoryId,
				ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir"))), userParameters)) {
			Method method = getMainMethod(bundleid, configuredstorage);
			String[] mainargs = arguments.toArray(ObjectUtils.EMPTY_STRING_ARRAY);
			try {
				Object invokeres = method.invoke(null, (Object) mainargs);
				if (method.getReturnType() == int.class) {
					exitCode = (Integer) invokeres;
					if (exitCode != null) {
						if (callSystemExit == SystemExitConfiguration.FORWARD) {
							System.exit(exitCode);
						}
					}
				}
			} catch (InvocationTargetException e) {
				Throwable c = e.getCause();
				if (c == null) {
					//should not happen, but just in case
					throw new RepositoryException(e);
				}
				if (c instanceof Exception) {
					throw (Exception) c;
				}
				if (c instanceof Error) {
					throw (Error) c;
				}
				throw new RepositoryException(e);
			}
		} catch (Throwable e) {
			if (callSystemExit == SystemExitConfiguration.FORWARD
					|| callSystemExit == SystemExitConfiguration.ON_EXCEPTION
					|| callSystemExit == SystemExitConfiguration.ALWAYS) {
				try {
					IOUtils.printExc(e.getCause());
				} finally {
					System.exit(-1);
				}
			}
			throw e;
		} finally {
			if (callSystemExit == SystemExitConfiguration.ALWAYS) {
				//we get here if there were no exception only, as if there was, we system exited before we reach here
				//if there is no exit code, exit with 0 to signal no error
				System.exit(exitCode == null ? 0 : exitCode);
				return;
			}
			//restore context classloader
			currentthread.setContextClassLoader(contextcl);
		}
	}

	/**
	 * <pre>
	 * Specifies the user parameters for configuring the repository.
	 * 
	 * This string key-value pairs are interpreted the same way as the
	 * -U user parameters for the build execution.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_U)
	public void userParameter(String key, String value) {
		if (userParameters.containsKey(key)) {
			throw new InvalidArgumentValueException("User parameter specified multiple times: " + key, PARAM_NAME_U);
		}
		userParameters.put(key, value);
	}

	private BundleIdentifier getBundleIdentifier() {
		BundleIdentifier bundleid;
		if (this.bundle == null) {
			if (arguments.isEmpty()) {
				throw new MissingArgumentException(PARAM_NAME_BUNDLE,
						"No arguments or " + PARAM_NAME_BUNDLE
								+ " specified. Pass the bundle identifier as the first argument or use the "
								+ PARAM_NAME_BUNDLE + " parameter.");
			}
			bundleid = BundleIdentifier.valueOf(arguments.remove(0));
		} else {
			bundleid = bundle;
		}
		return bundleid;
	}

	private Method getMainMethod(BundleIdentifier bundleid, ConfiguredRepositoryStorage configuredstorage)
			throws BundleLoadingFailedException, ClassNotFoundException, NoSuchMethodException {
		NestRepositoryBundleClassLoader bundlecl;
		if (bundleid.getVersionQualifier() == null) {
			BundleVersionLookupResult versions = configuredstorage.getBundleLookup().lookupBundleVersions(bundleid);
			if (versions == null) {
				throw new BundleLoadingFailedException("No bundle found for identifier: " + bundleid);
			}
			bundleid = versions.getBundles().iterator().next();
			bundlecl = configuredstorage
					.getBundleClassLoader(BundleKey.create(versions.getStorageView().getStorageViewKey(), bundleid));
		} else {
			bundlecl = configuredstorage.getBundleClassLoader(bundleid);
		}
		String cn = this.mainClassName;
		if (cn == null) {
			BundleInformation bundleinfo = bundlecl.getBundle().getInformation();
			String mainc = bundleinfo.getMainClass();
			if (mainc == null) {
				throw new UnsupportedOperationException("No main class found in bundle: " + bundleid);
			}
			cn = mainc;
		}
		Class<?> mainclass = bundlecl.loadClassFromBundle(cn);
		try {
			return mainclass.getMethod("intMain", String[].class);
		} catch (NoSuchMethodException e) {
			try {
				return mainclass.getMethod("main", String[].class);
			} catch (NoSuchMethodException e2) {
				e.addSuppressed(e2);
				throw e;
			}
		}
	}

	public static BundleIdentifier toBundleIdentifier(String parametername, ParsingIterator it) {
		String n = ParseUtil.requireNextArgument(parametername, it);
		try {
			return BundleIdentifier.valueOf(n);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException("Invalid bundle identifier.", e, n);
		}
	}

	public static List<String> parseRemainingCommand(String parametername, Iterator<String> it) {
		List<String> result = new ArrayList<>();
		while (it.hasNext()) {
			result.add(it.next());
		}
		return result;
	}
}
