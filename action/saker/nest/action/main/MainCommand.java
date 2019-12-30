package saker.nest.action.main;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.RepositoryException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.NestRepositoryBundleClassLoader;
import saker.nest.exc.BundleLoadingFailedException;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.PositionalParameter;
import sipka.cmdline.runtime.ParsingIterator;

public class MainCommand {
	public static enum SystemExitConfiguration {
		ALWAYS,
		ON_EXCEPTION,
		FORWARD,
		NEVER,
	}

	@Parameter("-repo-id")
	public String repositoryId = NestRepositoryFactory.IDENTIFIER;

	@Parameter("-class")
	public String mainClassName;

	@Parameter("-U")
	public Map<String, String> userParameters = new LinkedHashMap<>();

	@Parameter
	@PositionalParameter(-1)
	@Converter(method = "parseRemainingCommand")
	public List<String> arguments = new ArrayList<>();

	@Parameter("-bundle")
	@Converter(method = "toBundleIdentifier")
	public BundleIdentifier bundle;

	@Parameter("-system-exit")
	public SystemExitConfiguration callSystemExit = SystemExitConfiguration.NEVER;

	private Integer exitCode;

	public void call(ExecuteActionCommand execute) throws Exception {
		BundleIdentifier bundleid = getBundleIdentifier();
		try (ConfiguredRepositoryStorage configuredstorage = new ConfiguredRepositoryStorage(execute.repository,
				repositoryId, ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir"))),
				userParameters)) {
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
				IOUtils.printExc(e.getCause());
				System.exit(-1);
			}
			throw e;
		} finally {
			if (callSystemExit == SystemExitConfiguration.ALWAYS) {
				//we get here if there were no exception only, as if there was, we system exited before we reach here
				//if there is no exit code, exit with 0 to signal no error
				System.exit(exitCode == null ? 0 : exitCode);
				return;
			}
		}
	}

	public Integer getExitCode() {
		return exitCode;
	}

	private BundleIdentifier getBundleIdentifier() {
		BundleIdentifier bundleid;
		if (this.bundle == null) {
			if (arguments.isEmpty()) {
				throw new IllegalArgumentException("No arguments specified. Bundle identifier must be first.");
			}
			bundleid = BundleIdentifier.valueOf(arguments.remove(0));
		} else {
			bundleid = bundle;
		}
		return bundleid;
	}

	private Method getMainMethod(BundleIdentifier bundleid, ConfiguredRepositoryStorage configuredstorage)
			throws BundleLoadingFailedException, ClassNotFoundException, NoSuchMethodException {
		NestRepositoryBundleClassLoader bundlecl = configuredstorage.getBundleClassLoader(bundleid);
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

	public static BundleIdentifier toBundleIdentifier(ParsingIterator it) {
		return BundleIdentifier.valueOf(it.next());
	}

	public static List<String> parseRemainingCommand(Iterator<String> it) {
		ArrayList<String> result = new ArrayList<>();
		while (it.hasNext()) {
			result.add(it.next());
		}
		return result;
	}
}
