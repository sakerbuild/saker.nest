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
package saker.nest.bundle.storage;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryImpl;
import saker.nest.bundle.AbstractNestRepositoryBundle;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleUtils;
import saker.nest.bundle.ContentVerifier;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.Hashes;
import saker.nest.bundle.JarNestRepositoryBundleImpl;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.exc.InvalidNestBundleException;
import saker.nest.exc.NestSignatureVerificationException;
import saker.nest.exc.OfflineStorageIOException;
import saker.nest.thirdparty.org.json.JSONArray;
import saker.nest.thirdparty.org.json.JSONException;
import saker.nest.thirdparty.org.json.JSONObject;
import saker.nest.thirdparty.org.json.JSONTokener;
import testing.saker.nest.TestFlag;

public class ServerBundleStorage extends AbstractBundleStorage {
	//XXX we should employ some sort of caching for index files. Like If-None-Match or Etag based caching.

	private static final String HTTP_USER_AGENT = "saker.nest/" + saker.nest.meta.Versions.VERSION_STRING_FULL
			+ " (saker.build " + saker.build.meta.Versions.VERSION_STRING_FULL + ")";

	private static final Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final String BUNDLE_SIGNATURE_ALGORITHM = "SHA256withRSA";

	/**
	 * 3 hours
	 */
	private static final int INDEX_INVALIDATION_TIME_MILLIS = 1000 * 60 * 60 * 3;

	//TODO set a proper default secondary url
	private static final String REPOSITORY_DEFAULT_SECONDARY_REMOTE_URL = null;

	public static class ServerStorageKey extends AbstractStorageKey implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String serverHost;
		protected transient SakerPath storageDirectory;
		protected transient String serverSecondaryHost;

		/**
		 * For {@link Externalizable}.
		 */
		public ServerStorageKey() {
		}

		private ServerStorageKey(Path storageDirectory, String serverHost, String serverSecondaryHost) {
			this.storageDirectory = SakerPath.valueOf(storageDirectory);
			this.serverHost = serverHost;
			this.serverSecondaryHost = serverSecondaryHost;
		}

		public static AbstractStorageKey create(NestRepositoryImpl repository, Map<String, String> userparams) {
			String serverhost = userparams.getOrDefault(ServerBundleStorageView.PARAMETER_URL,
					ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL);
			String secondaryhost = userparams.get(ServerBundleStorageView.PARAMETER_SECONDARY_URL);
			if (ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL.equals(serverhost)) {
				if (secondaryhost == null) {
					secondaryhost = REPOSITORY_DEFAULT_SECONDARY_REMOTE_URL;
				}
			}
			if ("".equals(secondaryhost) || "null".equals(secondaryhost)) {
				secondaryhost = null;
			}

			return new ServerStorageKey(
					repository.getRepositoryStorageDirectory().resolve(ServerBundleStorageView.DEFAULT_STORAGE_NAME)
							.resolve(ConfiguredRepositoryStorage.getSubDirectoryNameForServerStorage(serverhost)),
					serverhost, secondaryhost);
		}

		@Override
		public AbstractBundleStorage getStorage(NestRepositoryImpl repository) {
			return new ServerBundleStorage(this, repository);
		}

		public String getServerHost() {
			return serverHost;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(storageDirectory);
			out.writeObject(serverHost);
			out.writeObject(serverSecondaryHost);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			storageDirectory = (SakerPath) in.readObject();
			serverHost = (String) in.readObject();
			serverSecondaryHost = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((serverHost == null) ? 0 : serverHost.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ServerStorageKey other = (ServerStorageKey) obj;
			if (serverHost == null) {
				if (other.serverHost != null)
					return false;
			} else if (!serverHost.equals(other.serverHost))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ (storageDirectory != null ? "storageDirectory=" + storageDirectory + ", " : "")
					+ (serverHost != null ? "serverHost=" + serverHost : "") + "]";
		}

	}

	private static final String BUNDLE_STORAGE_DIRECTORY_NAME = "bundle_storage";
	private static final String BUNDLES_DIRECTORY_NAME = "bundles";
	private static final String BUNDLE_LIB_STORAGE_DIRECTORY_NAME = "bundle_lib_storage";

	private final String serverHost;
	private final Path storageDirectory;
	private final Path bundlesDirectory;

	private final ConcurrentSkipListMap<BundleIdentifier, Object> bundleLoadLocks = new ConcurrentSkipListMap<>();
	private final NavigableMap<BundleIdentifier, LoadedBundleState> loadedBundles = new ConcurrentSkipListMap<>();

	private final SecureRandom secureRandom = new SecureRandom();

	private volatile boolean closed = false;

	private final ServerStorageKey storageKey;

	private final IndexManager<NavigableMap<String, NavigableSet<BundleIdentifier>>> packageBundlesIndexManager;
	private final IndexManager<NavigableMap<String, NavigableSet<BundleIdentifier>>> tasksIndexManager;

	private static final int MAX_INCLUDED_BUNDLE_SIGNATURE_KEY_VERSION = 1;
	private final ConcurrentSkipListMap<Integer, PublicKey> bundleSignatureKeys = new ConcurrentSkipListMap<>();
	private final ConcurrentSkipListMap<Integer, Object> bundleSignatureKeyLoadLocks = new ConcurrentSkipListMap<>();

	private static final int VERIFICATION_STATE_VERIFYING = 1;
	private static final int VERIFICATION_STATE_VERIFIED = 2;
	private static final int VERIFICATION_STATE_FAILED = 3;
	private static final VerificationState SIGNATURE_VERIFIED = new VerificationState(VERIFICATION_STATE_VERIFIED,
			false);

	private final NestRepositoryImpl repository;

	private static class VerificationState {
		protected final int state;
		protected final boolean offline;
		protected final Throwable verificationFailCause;

		public VerificationState(int state, boolean offline) {
			this.state = state;
			this.offline = offline;
			this.verificationFailCause = null;
		}

		public VerificationState(Throwable verificationFailCause, boolean offline) {
			this.offline = offline;
			this.state = VERIFICATION_STATE_FAILED;
			this.verificationFailCause = verificationFailCause;
		}

	}

	private final class LoadedBundleState implements Closeable {
		protected final JarNestRepositoryBundleImpl bundle;
		protected final Map<BundleSignatureVerificationConfiguration, VerificationState> verifiedSignatures = new ConcurrentHashMap<>();

		public LoadedBundleState(BundleIdentifier bundleid, BundleSignatureHolder signatureholder, Path bundlepath,
				BundleSignatureVerificationConfiguration verifyconfig, boolean offline)
				throws IOException, BundleLoadingFailedException {
			bundle = createBundle(bundleid, bundlepath, channel -> {
				verifyBundleWithConfigImpl(verifyconfig, signatureholder, offline, bundleid, channel, bundlepath);
			});
		}

		@Override
		public void close() throws IOException {
			verifiedSignatures.clear();
			bundle.close();
		}

		public void verifyBundleWithConfig(BundleSignatureVerificationConfiguration verifyconfig, boolean offline)
				throws NestSignatureVerificationException {
			verifyBundleWithConfig(verifyconfig, null, offline);
		}

		public void verifyBundleWithConfig(BundleSignatureVerificationConfiguration verifyconfig,
				BundleSignatureHolder signatureholder, boolean offline) throws NestSignatureVerificationException {
			verifyBundleWithConfigImpl(verifyconfig, signatureholder, offline, bundle.getBundleIdentifier(), null,
					bundle.getJarPath());
		}

		private void verifyBundleWithConfigImpl(BundleSignatureVerificationConfiguration verifyconfig,
				BundleSignatureHolder signatureholder, boolean offline, BundleIdentifier bundleid,
				SeekableByteChannel channel, Path bundlepath) throws NestSignatureVerificationException {
			if (verifyconfig.canLoadWithoutSignature()) {
				return;
			}
			VerificationState nstate = new VerificationState(VERIFICATION_STATE_VERIFYING, offline);
			synchronized (nstate) {
				VerificationState prevverifstate = verifiedSignatures.putIfAbsent(verifyconfig, nstate);
				if (TestFlag.ENABLED) {
					if (!TestFlag.metric().allowCachedVerificationState(bundleid.toString())) {
						prevverifstate = null;
					}
				}
				if (prevverifstate != null) {
					if (prevverifstate == SIGNATURE_VERIFIED) {
						return;
					}
					if (prevverifstate.state == VERIFICATION_STATE_VERIFYING) {
						synchronized (prevverifstate) {
							//sync to wait the compiletion
						}
						prevverifstate = verifiedSignatures.get(verifyconfig);
					}
					if (prevverifstate.state == VERIFICATION_STATE_FAILED) {
						if (offline || !prevverifstate.offline) {
							throw new NestSignatureVerificationException("Failed to verify bundle.",
									prevverifstate.verificationFailCause);
						}
						//don't throw and try to verify again if we can do the verification online
					}
				}
				Path signaturefilepath = getBundleSignaturePathFromBundlePath(bundlepath);
				if (signatureholder == null) {
					signatureholder = BundleSignatureHolder.fromFile(signaturefilepath);
				}
				BundleSignatureHolder downloadedsignature = null;
				if (signatureholder == null || signatureholder.getVersion() < verifyconfig.getMinSignatureVersion()) {
					if (offline) {
						NestSignatureVerificationException exc = new NestSignatureVerificationException(
								"Failed to verify bundle, cannot download latest signature in offline mode for: "
										+ bundleid,
								new OfflineStorageIOException());
						verifiedSignatures.put(verifyconfig, new VerificationState(exc, offline));
						throw exc;
					}
					try {
						signatureholder = downloadBundleSignature(bundleid, offline);
						downloadedsignature = signatureholder;
					} catch (IOException e) {
						verifiedSignatures.put(verifyconfig, new VerificationState(e, offline));
						throw new NestSignatureVerificationException("Failed to verify bundle.", e);
					}
				}
				if (signatureholder == null) {
					NestSignatureVerificationException exc = new NestSignatureVerificationException(
							"Failed to verify bundle, missing signature for: " + bundleid);
					verifiedSignatures.put(verifyconfig, new VerificationState(exc, offline));
					throw exc;
				}
				if (signatureholder.getVersion() < verifyconfig.getMinSignatureVersion()) {
					NestSignatureVerificationException exc = new NestSignatureVerificationException(
							"Failed to verify bundle, invalid signature version for: " + bundleid);
					verifiedSignatures.put(verifyconfig, new VerificationState(exc, offline));
					throw exc;
				}
				try {
					if (channel == null) {
						channel = bundle.getChannel();
						synchronized (bundleid) {
							verifyBundleSignature(channel, bundleid, verifyconfig, signatureholder, offline);
						}
					} else {
						verifyBundleSignature(channel, bundleid, verifyconfig, signatureholder, offline);
					}
					verifiedSignatures.put(verifyconfig, SIGNATURE_VERIFIED);
				} catch (Throwable e) {
					verifiedSignatures.put(verifyconfig, new VerificationState(e, offline));
					throw e;
				}
				if (downloadedsignature != null) {
					try {
						downloadedsignature.writeTo(signaturefilepath);
					} catch (IOException e) {
						//ignoreable, as the bundle is alerady verified
					}
				}
				return;
			}
		}

	}

	public ServerBundleStorage(ServerStorageKey storagekey, NestRepositoryImpl repository) {
		this.storageKey = storagekey;
		this.repository = repository;
		this.serverHost = storagekey.serverHost;
		this.storageDirectory = LocalFileProvider.toRealPath(storagekey.storageDirectory);
		this.bundlesDirectory = storageDirectory.resolve(BUNDLES_DIRECTORY_NAME);
		this.packageBundlesIndexManager = new BundlesIndexManager(this.storageDirectory.resolve("index/bundles"),
				createAppendedUrlOrNull(serverHost, "/bundles/index"),
				createAppendedUrlOrNull(storagekey.serverSecondaryHost, "/bundles/index"));
		this.tasksIndexManager = new TasksIndexManager(this.storageDirectory.resolve("index/tasks"),
				createAppendedUrlOrNull(serverHost, "/tasks/index"),
				createAppendedUrlOrNull(storagekey.serverSecondaryHost, "/tasks/index"));
	}

	private PublicKey getBundleSignatureKey(int version, boolean offline) throws IOException {
		if (TestFlag.ENABLED) {
			PublicKey testkey = TestFlag.metric().overrideServerBundleSignaturePublicKey(serverHost, version);
			if (testkey != null) {
				return testkey;
			}
		}
		if (version < 1) {
			System.err.println("Invalid bundle signature key version: " + version);
			return null;
		}
		PublicKey presentkey = bundleSignatureKeys.get(version);
		if (presentkey != null) {
			return presentkey;
		}
		//lock so the keys aren't loaded concurrently. it would be unnecessary
		synchronized (bundleSignatureKeyLoadLocks.computeIfAbsent(version, Functionals.objectComputer())) {
			presentkey = bundleSignatureKeys.get(version);
			if (presentkey != null) {
				return presentkey;
			}
			byte[] keybytes;
			if (version <= MAX_INCLUDED_BUNDLE_SIGNATURE_KEY_VERSION
					&& ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL.equals(serverHost)) {
				try (InputStream in = ServerBundleStorage.class.getClassLoader()
						.getResourceAsStream("bundle_signature_key/" + version)) {
					if (in == null) {
						//this shouldn't ever happen, but do not throw, as that would distrupt operations more severly
						System.err.println("Failed to retrieve Nest repository built-in resource: "
								+ "bundle_signature_key/" + version);
						return null;
					}

					keybytes = StreamUtils.readStreamFully(in).copyOptionally();
				}

			} else {
				if (offline) {
					throw new OfflineStorageIOException(
							"Cannot fetch bundle signing key for offline storage: " + serverHost);
				}
				// fetch from server
				ByteArrayRegion retrievedkeybytes = makeServerRequest(FLAG_REQUEST_FOLLOW_REDIRECTS,
						serverHost + "/bundle_signature_key/" + version, "GET", (url, rc, in, err, headerfunc) -> {
							if (rc == HttpURLConnection.HTTP_OK) {
								return StreamUtils.readStreamFully(in.get());
							}
							String errstr = "";
							IOException errexc = null;
							try {
								errstr = readErrorStreamOrEmpty(err);
							} catch (IOException e) {
								errexc = e;
							}
							throw IOUtils.addExc(new IOException("Failed to retrive bundle signing key, response code: "
									+ rc + " with error: " + errstr), errexc);
						});
				keybytes = retrievedkeybytes.copyOptionally();
			}
			try {
				KeyFactory kf = KeyFactory.getInstance("RSA");
				X509EncodedKeySpec keyspec = new X509EncodedKeySpec(keybytes);
				PublicKey result = kf.generatePublic(keyspec);
				bundleSignatureKeys.put(version, result);
				return result;
			} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
				System.err.println("Failed to generate Nest repository bundle signing key: " + e);
				return null;
			}
		}
	}

	private static String createAppendedUrlOrNull(String host, String append) {
		if (host == null) {
			return null;
		}
		return host + append;
	}

	private static BundleSignatureVerificationConfiguration createSignatureVerificationConfiguration(
			Map<String, String> userparams) {
		boolean loadwithoutsignature = "false"
				.equalsIgnoreCase(userparams.get(ServerBundleStorageView.PARAMETER_SIGNATURE_VERIFICATION));
		int minsignatureversion;
		String minsigversionparam = userparams.get(ServerBundleStorageView.PARAMETER_MIN_SIGNATURE_VERSION);
		if (minsigversionparam != null) {
			try {
				minsignatureversion = Integer.parseInt(minsigversionparam);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid " + ServerBundleStorageView.PARAMETER_MIN_SIGNATURE_VERSION
						+ " value: " + minsigversionparam, e);
			}
		} else {
			minsignatureversion = MAX_INCLUDED_BUNDLE_SIGNATURE_KEY_VERSION;
		}
		BundleSignatureVerificationConfiguration verificationconfiguration = new BundleSignatureVerificationConfiguration(
				loadwithoutsignature, minsignatureversion);
		return verificationconfiguration;
	}

	@Override
	public String getType() {
		return ConfiguredRepositoryStorage.STORAGE_TYPE_SERVER;
	}

	@Override
	public AbstractStorageKey getStorageKey() {
		return storageKey;
	}

	@Override
	public Path getBundleStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException {
		return BundleUtils.getVersionedBundleJarPath(storageDirectory.resolve(BUNDLE_STORAGE_DIRECTORY_NAME),
				bundle.getBundleIdentifier());
	}

	@Override
	public Path getBundleLibStoragePath(NestRepositoryBundle bundle) {
		return BundleUtils.getVersionedBundleJarPath(storageDirectory.resolve(BUNDLE_LIB_STORAGE_DIRECTORY_NAME),
				bundle.getBundleIdentifier());
	}

	public String getServerHost() {
		return serverHost;
	}

	@Override
	public AbstractBundleStorageView newStorageView(Map<String, String> userparameters,
			ExecutionPathConfiguration pathconfig) {
		boolean uncacherequests = getUncacheRequestsValue(userparameters);
		boolean offline = Boolean.parseBoolean(userparameters.get(ServerBundleStorageView.PARAMETER_OFFLINE));
		return new ServerBundleStorageViewImpl(offline, createSignatureVerificationConfiguration(userparameters),
				uncacherequests);
	}

	private static boolean getUncacheRequestsValue(Map<String, String> userparameters) {
		String uncacheparam = userparameters.get(ServerBundleStorageView.PARAMETER_REQUESTS_UNCACHE);
		if (uncacheparam != null) {
			return Boolean.parseBoolean(uncacheparam);
		}
		if (TestFlag.ENABLED) {
			Boolean override = TestFlag.metric().overrideServerUncacheRequestsValue();
			if (override != null) {
				return override;
			}
		}
		String cival = System.getenv("CI");
		if (cival != null) {
			return Boolean.parseBoolean(cival);
		}
		String tfbuildval = System.getenv("TF_BUILD");
		if (tfbuildval != null) {
			return Boolean.parseBoolean(tfbuildval);
		}
		return false;
	}

	@Override
	public void close() throws IOException {
		closed = true;
		IOException exc = null;
		while (!bundleLoadLocks.isEmpty()) {
			Entry<BundleIdentifier, Object> fe = bundleLoadLocks.pollFirstEntry();
			if (fe == null) {
				break;
			}
			synchronized (fe.getValue()) {
				exc = IOUtils.closeExc(exc, loadedBundles.remove(fe.getKey()));
			}
		}
		IOUtils.throwExc(exc);
	}

	private String getBundleDownloadURL(BundleIdentifier bundleid) throws UnsupportedEncodingException {
		return getBundleDownloadURL(serverHost, bundleid);
	}

	private static String getBundleDownloadURL(String serverHost, BundleIdentifier bundleid)
			throws UnsupportedEncodingException {
		return serverHost + "/bundle/download/" + URLEncoder.encode(bundleid.toString(), "UTF-8");
	}

	private static String downloadSpeedToString(double bytespersec) {
		if (bytespersec < 1024) {
			return String.format(Locale.US, "%.1f B/sec", bytespersec);
		}
		if (bytespersec < 1024 * 1024) {
			return String.format(Locale.US, "%.1f KiB/sec", bytespersec / 1024d);
		}
		return String.format(Locale.US, "%.1f MiB/sec", bytespersec / (1024d * 1024d));
	}

	protected NavigableSet<BundleIdentifier> getBundlesForTaskName(TaskName taskname, int indexflags)
			throws IOException {
		IndexOperationOptions operationoptions = new IndexOperationOptions(indexflags);
		return getBundlesForTaskName(taskname, operationoptions);
	}

	protected NavigableSet<BundleIdentifier> getBundlesForTaskName(TaskName taskname,
			IndexOperationOptions operationoptions) throws IOException {
		String tasknamestr = taskname.getName();
		NavigableMap<String, NavigableSet<BundleIdentifier>> index = tasksIndexManager.getIndexForName(operationoptions,
				tasknamestr);
		NavigableSet<BundleIdentifier> result = ObjectUtils.getMapValue(index, tasknamestr);
		if (ObjectUtils.isNullOrEmpty(result)) {
			return Collections.emptyNavigableSet();
		}
		return result;
	}

	private static final int FLAG_REQUEST_OFFLINE = 1 << 0;
	private static final int FLAG_REQUEST_NO_DISCONNECT_ON_200 = 1 << 1;
	private static final int FLAG_REQUEST_FOLLOW_REDIRECTS = 1 << 2;

	private static <T> T makeServerRequest(int flags, String requesturl, String method, ServerRequestHandler<T> handler)
			throws IOException {
		if (((flags & FLAG_REQUEST_OFFLINE) == FLAG_REQUEST_OFFLINE)) {
			throw new OfflineStorageIOException("Failed to make request in offline mode. (" + requesturl + ")");
		}
		URL url = new URL(requesturl);
		return makeURLRequest(flags, url, method, handler);
	}

	private static <T> T makeURLRequest(int flags, URL url, String method, ServerRequestHandler<T> handler)
			throws IOException, ProtocolException, ServerConnectionFailedIOException {
		if (((flags & FLAG_REQUEST_OFFLINE) == FLAG_REQUEST_OFFLINE)) {
			throw new OfflineStorageIOException("Failed to make request in offline mode. (" + url + ")");
		}
		try {
			if (TestFlag.ENABLED) {
				Integer rc = TestFlag.metric().getServerRequestResponseCode(url.toString());
				if (rc != null) {
					return handler.handle(url, rc,
							() -> TestFlag.metric().getServerRequestResponseStream(url.toString()),
							() -> TestFlag.metric().getServerRequestResponseErrorStream(url.toString()),
							(header) -> TestFlag.metric().getServerRequestResponseHeaders(url.toString()).get(header));
				}
			}
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			//set some timeout for the request not to deadlock the build
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(30000);
			connection.setRequestProperty("Accept-Encoding", "gzip");
			connection.setRequestProperty("User-Agent", HTTP_USER_AGENT);
			if (method != null) {
				connection.setRequestMethod(method);
			}
			connection.setInstanceFollowRedirects(
					(flags & FLAG_REQUEST_FOLLOW_REDIRECTS) == FLAG_REQUEST_FOLLOW_REDIRECTS);
			int rc;
			try {
				connection.connect();
				rc = connection.getResponseCode();
			} catch (IOException e) {
				throw new ServerConnectionFailedIOException("Failed to connect to: " + url, e);
			}
			try {
				return handler.handle(url, rc, () -> unGzipizeInputStream(connection, connection.getInputStream()),
						() -> unGzipizeInputStream(connection, connection.getErrorStream()),
						connection::getHeaderField);
			} finally {
				if (rc != 200 || ((flags & FLAG_REQUEST_NO_DISCONNECT_ON_200) != FLAG_REQUEST_NO_DISCONNECT_ON_200)) {
					connection.disconnect();
				}
			}
		} catch (IOException e) {
			throw new IOException("Failed to execute server request. (" + url + ")", e);
		}
	}

	private static InputStream unGzipizeInputStream(HttpURLConnection connection, InputStream is) throws IOException {
		if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
			return new GZIPInputStream(is);
		}
		return is;
	}

	private static class BundleSignatureHolder {
		//XXX store other information like deprecation notice and other things too
		private byte[] signatureBytes;
		private int version;

		public static BundleSignatureHolder fromHeaders(String signature, String version) {
			if (signature == null || version == null) {
				return null;
			}
			try {
				int intversion = Integer.parseInt(version);
				byte[] sigbytes = Base64.getUrlDecoder().decode(signature);
				return new BundleSignatureHolder(sigbytes, intversion);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		public static BundleSignatureHolder fromFile(Path filepath) {
			try (BufferedReader reader = Files.newBufferedReader(filepath, StandardCharsets.UTF_8)) {
				Object obj = new JSONTokener(reader).nextValue();
				if (!(obj instanceof JSONObject)) {
					return null;
				}
				JSONObject jsonobj = (JSONObject) obj;
				String sig = jsonobj.optString("signature", null);
				if (sig == null) {
					return null;
				}
				int version = jsonobj.optInt("version", 0);
				if (version == 0) {
					return null;
				}
				return new BundleSignatureHolder(Base64.getUrlDecoder().decode(sig), version);
			} catch (IOException | IllegalArgumentException | JSONException e) {
				return null;
			}
		}

		private BundleSignatureHolder(byte[] signatureBytes, int version) {
			this.signatureBytes = signatureBytes;
			this.version = version;
		}

		public byte[] getSignatureBytes() {
			return signatureBytes;
		}

		public int getVersion() {
			return version;
		}

		public void writeTo(Path filepath) throws IOException {
			JSONObject obj = new JSONObject();
			obj.put("signature", BASE64_ENCODER.encodeToString(signatureBytes));
			obj.put("version", version);
			Files.write(filepath, obj.toString().getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public String toString() {
			return "BundleSignatureHolder[version=" + version + ", "
					+ (signatureBytes != null ? "signatureBytes=" + BASE64_ENCODER.encodeToString(signatureBytes) : "")
					+ "]";
		}
	}

	private static class BundleSignatureVerificationConfiguration implements Externalizable {
		private static final long serialVersionUID = 1L;

		private boolean loadWithoutSignature;
		private int minSignatureVersion;

		public BundleSignatureVerificationConfiguration() {
			this.loadWithoutSignature = false;
			this.minSignatureVersion = MAX_INCLUDED_BUNDLE_SIGNATURE_KEY_VERSION;
		}

		public BundleSignatureVerificationConfiguration(boolean loadWithoutSignature, int minSignatureVersion) {
			this.loadWithoutSignature = loadWithoutSignature;
			this.minSignatureVersion = minSignatureVersion;
		}

		public boolean canLoadWithoutSignature() {
			return loadWithoutSignature;
		}

		public int getMinSignatureVersion() {
			return minSignatureVersion;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeBoolean(loadWithoutSignature);
			out.writeInt(minSignatureVersion);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			loadWithoutSignature = in.readBoolean();
			minSignatureVersion = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (loadWithoutSignature ? 1231 : 1237);
			result = prime * result + minSignatureVersion;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BundleSignatureVerificationConfiguration other = (BundleSignatureVerificationConfiguration) obj;
			if (loadWithoutSignature != other.loadWithoutSignature)
				return false;
			if (minSignatureVersion != other.minSignatureVersion)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[loadWithoutSignature=" + loadWithoutSignature
					+ ", minSignatureVersion=" + minSignatureVersion + "]";
		}

	}

	private void verifyBundleSignature(SeekableByteChannel channel, BundleIdentifier bundleid,
			BundleSignatureVerificationConfiguration verificationconfiguration, BundleSignatureHolder signatureholder,
			boolean offline) throws NestSignatureVerificationException {
		if (verificationconfiguration.canLoadWithoutSignature()) {
			//no need to verify
			return;
		}
		if (signatureholder == null) {
			throw new NestSignatureVerificationException("Failed to verify, missing bundle signature: " + bundleid);
		}
		if (signatureholder.getVersion() < verificationconfiguration.getMinSignatureVersion()) {
			throw new NestSignatureVerificationException("Failed to verify, unaccepted bundle signature version: "
					+ bundleid + " with " + signatureholder.getVersion() + " minimum: "
					+ verificationconfiguration.getMinSignatureVersion());
		}
		if (channel == null) {
			throw new NestSignatureVerificationException(
					"Failed to verify bundle: " + bundleid + ", no file channel available.");
		}
		try {
			Signature signature;
			//synchronize on the bundle so the channel is not accessed by concurrent verification threads
			PublicKey signaturekey = getBundleSignatureKey(signatureholder.version, offline);
			if (signaturekey == null) {
				throw new BundleLoadingFailedException(
						"Failed to verify bundle signature, missing public key: " + bundleid);
			}
			channel.position(0);
			//don't close the created input stream, as that would close the channel
			InputStream in = Channels.newInputStream(channel);
			signature = Signature.getInstance(BUNDLE_SIGNATURE_ALGORITHM);
			signature.initVerify(signaturekey);
			StreamUtils.copyStream(in, signature);
			boolean verified = signature.verify(signatureholder.signatureBytes);
			if (!verified) {
				throw new BundleLoadingFailedException("Failed to verify, invalid bundle signature: " + bundleid);
			}
			return;
		} catch (Exception e) {
			throw new NestSignatureVerificationException("Failed to verify bundle signature: " + bundleid, e);
		}
	}

	private BundleSignatureHolder downloadBundleSignature(BundleIdentifier bundleid, boolean offline)
			throws IOException {
		String requesturl = getBundleDownloadURL(bundleid);
		//don't follow redirects
		return makeServerRequest((offline ? FLAG_REQUEST_OFFLINE : 0), requesturl, "HEAD",
				(url, rc, ins, errs, headerfunc) -> {
					BundleSignatureHolder sig = getSignatureFromHeaders(headerfunc);
					if (sig != null) {
						return sig;
					}
					//XXX maybe handle redirect
					return null;
				});
	}

	private static class DownloadedBundle {
		protected Path bundle;
		protected BundleSignatureHolder signature;

		public DownloadedBundle(Path bundle, BundleSignatureHolder signature) {
			this.bundle = bundle;
			this.signature = signature;
		}
	}

	private DownloadedBundle downloadBundle(BundleIdentifier bundleid, Path resultjarpath, boolean offline)
			throws BundleLoadingFailedException {
		try {
			String requesturl = getBundleDownloadURL(bundleid);
			int requestflags = offline ? FLAG_REQUEST_OFFLINE : 0;
			return makeServerRequest(requestflags, requesturl, "GET", new ServerRequestHandler<DownloadedBundle>() {
				private BundleSignatureHolder signatureHolder;
				private int redirectCount = 0;

				@Override
				public DownloadedBundle handle(URL url, int rc, IOSupplier<? extends InputStream> ins,
						IOSupplier<? extends InputStream> errs, Function<? super String, ? extends String> headerfunc)
						throws IOException {
					if (rc >= 300 && rc < 400) {
						//redirection
						if (signatureHolder == null) {
							signatureHolder = getSignatureFromHeaders(headerfunc);
						}
						String location = headerfunc.apply("Location");
						if (location == null) {
							throw new IOException("No Location header for redirection. (" + rc + ")");
						}
						if (++redirectCount > 10) {
							throw new IOException("Too many redirections for downloading bundle: " + bundleid
									+ " (next: " + location + ")");
						}
						return makeURLRequest(requestflags, new URL(url, location), "GET", this);
					}
					if (rc == HttpURLConnection.HTTP_OK) {
						//HTTP OK
						//persist to a temporary file, then move that file to the target

						if (signatureHolder == null) {
							signatureHolder = getSignatureFromHeaders(headerfunc);
						}

						byte[] randbytes = new byte[8];
						secureRandom.nextBytes(randbytes);
						Path tempfilepath = resultjarpath.resolveSibling(resultjarpath.getFileName().toString()
								+ ".temp_" + StringUtils.toHexString(randbytes) + ".jar");
						Files.createDirectories(resultjarpath.getParent());
						try {
							try (InputStream is = ins.get();
									OutputStream os = Files.newOutputStream(tempfilepath,
											StandardOpenOption.CREATE_NEW)) {
								StreamUtils.copyStream(is, os);
							}
							try {
								Files.move(tempfilepath, resultjarpath);
							} catch (IOException e) {
								//the moving of the file failed
								//this can happen if some other process concurrently downloads the file
								// and opens it without allowing us to overwrite
								//we can continue execution, as we verify the contents of the JAR before opening it
							}
							return new DownloadedBundle(resultjarpath, signatureHolder);
						} finally {
							try {
								Files.deleteIfExists(tempfilepath);
							} catch (IOException e) {
								//ignoreable
							}
						}
					}
					String errstr = "";
					IOException errexc = null;
					try {
						errstr = readErrorStreamOrEmpty(errs);
					} catch (IOException e) {
						errexc = e;
					}
					throw ObjectUtils
							.sneakyThrow(IOUtils.addExc(
									new BundleLoadingFailedException("Failed to download bundle: " + bundleid
											+ " with HTTP response code: " + rc + " with error payload: " + errstr),
									errexc));
				}

			});
		} catch (IOException e) {
			throw new BundleLoadingFailedException("Failed to download bundle: " + bundleid, e);
		}
	}

	protected static BundleSignatureHolder getSignatureFromHeaders(
			Function<? super String, ? extends String> headerfunc) {
		BundleSignatureHolder result = BundleSignatureHolder.fromHeaders(headerfunc.apply("Nest-Bundle-Signature"),
				headerfunc.apply("Nest-Bundle-Signature-Version"));
		if (result != null) {
			return result;
		}
		//support getting the signatures from the x-goog-meta- headers if the bundle is served from google storage
		result = BundleSignatureHolder.fromHeaders(headerfunc.apply("X-Goog-Meta-Nest-Bundle-Signature"),
				headerfunc.apply("X-Goog-Meta-Nest-Bundle-Signature-Version"));
		return result;
	}

	private JarNestRepositoryBundleImpl createBundle(BundleIdentifier bundleid, Path resultjarpath,
			ContentVerifier verifier) throws IOException, BundleLoadingFailedException {
		//require that all external dependencies have sha-256 defined for them

		JarNestRepositoryBundleImpl result;
		try {
			result = JarNestRepositoryBundleImpl.create(this, resultjarpath, verifier);
		} catch (NestSignatureVerificationException e) {
			throw new BundleLoadingFailedException("Failed to load bundle: " + bundleid, e);
		}
		try {
			BundleInformation info = result.getInformation();
			ExternalDependencyInformation extdeps = info.getExternalDependencyInformation();
			for (Entry<URI, Hashes> entry : BundleUtils.getExternalDependencyInformationHashes(extdeps).entrySet()) {
				if (entry.getValue().sha256 == null) {
					throw new BundleLoadingFailedException("Bundle " + info.getBundleIdentifier()
							+ " declares external dependency without SHA-256 hash value: " + entry.getKey());
				}
			}
		} catch (Throwable e) {
			//close the jar in case of exceptions
			IOUtils.addExc(e, IOUtils.closeExc(result));
			throw e;
		}
		return result;
	}

	private static String readErrorStreamOrEmpty(IOSupplier<? extends InputStream> errstream) throws IOException {
		if (errstream == null) {
			return "";
		}
		try (InputStream is = errstream.get()) {
			return readErrorStreamOrEmpty(is);
		}
	}

	private static Path getBundleSignaturePathFromBundlePath(Path bundlepath) {
		return bundlepath.resolveSibling(bundlepath.getFileName() + ".sig");
	}

	private static String readErrorStreamOrEmpty(InputStream is) throws IOException {
		if (is == null) {
			return "";
		}
		return StreamUtils.readStreamStringFully(is, StandardCharsets.UTF_8);
	}

	private static boolean hasOfflineStorageIOExceptionCause(Throwable e) {
		while (true) {
			e = e.getCause();
			if (e == null) {
				return false;
			}
			if (e instanceof OfflineStorageIOException) {
				return true;
			}
		}
	}

	private static class OfflineStorageIndexIOException extends OfflineStorageIOException {
		private static final long serialVersionUID = 1L;

		private String additonalUrl;
		private Path indexFilePath;

		public OfflineStorageIndexIOException(String additionalurl, Path indexfilepath) {
			this.additonalUrl = additionalurl;
			this.indexFilePath = indexfilepath;
		}

		public String getAdditonalUrl() {
			return additonalUrl;
		}

		public Path getIndexFilePath() {
			return indexFilePath;
		}
	}

	private interface ServerRequestHandler<T> {
		public T handle(URL url, int responsecode, IOSupplier<? extends InputStream> inputsupplier,
				IOSupplier<? extends InputStream> errorsupplier,
				Function<? super String, ? extends String> headersupplier) throws IOException;
	}

	private static class ServerConnectionFailedIOException extends IOException {
		private static final long serialVersionUID = 1L;

		public ServerConnectionFailedIOException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static class IndexFileCorruptedIOException extends IOException {
		private static final long serialVersionUID = 1L;

		private String base;
		private String additionalUrl;

		public IndexFileCorruptedIOException(String base, String additionalUrl) {
			this.base = base;
			this.additionalUrl = additionalUrl;
		}

		public IndexFileCorruptedIOException(Throwable cause, String base, String additionalUrl) {
			super(cause);
			this.base = base;
			this.additionalUrl = additionalUrl;
		}

		@Override
		public String getMessage() {
			String superm = super.getMessage();
			if (superm == null) {
				superm = "";
			}
			return superm + "(" + base + " : " + additionalUrl + ")";
		}
	}

	private static class IndexOperationOptions {
		public static final IndexOperationOptions NOFLAGS = new IndexOperationOptions(0);
		protected final int flags;

		public IndexOperationOptions(int flags) {
			this.flags = flags;
		}

		public void indexFileOfflineReused(String additionalurl, Path indexfilepath) {
			//ignore, subclass may override
		}

		public void missingIndexFile(String additionalurl, Path indexfilepath) {
			//ignore, subclass may override
		}
	}

	private static class AsyncDownloadStartingIndexOperationOptions extends IndexOperationOptions {
		private final BiConsumer<String, Path> starter;

		public AsyncDownloadStartingIndexOperationOptions(int flags, BiConsumer<String, Path> starter) {
			super(flags);
			this.starter = starter;
		}

		@Override
		public void indexFileOfflineReused(String additionalurl, Path indexfilepath) {
			FileTime lastmodtime;
			try {
				lastmodtime = Files.getLastModifiedTime(indexfilepath);
			} catch (IOException e) {
				starter.accept(additionalurl, indexfilepath);
				return;
			}
			long currenttime = System.currentTimeMillis();
			long modtime = lastmodtime.toMillis();
			if (modtime > currenttime || modtime + INDEX_INVALIDATION_TIME_MILLIS < currenttime) {
				//the index file is considered to be expired
				starter.accept(additionalurl, indexfilepath);
			}
		}

		@Override
		public void missingIndexFile(String additionalurl, Path indexfilepath) {
			starter.accept(additionalurl, indexfilepath);
		}

	}

	private static abstract class IndexManager<T> {
		public static final int FLAG_OFFLINE = 1 << 0;
		public static final int FLAG_MISSING_INDEX_ACCEPTABLE = 1 << 1;
		public static final int FLAG_REQUESTS_UNCACHE = 1 << 2;
		public static final int FLAG_NO_LOADING_FROM_FILE = 1 << 3;

		private static final String INDEX_TYPE_LOOKUP = "lookup";
		private static final String INDEX_TYPE_INDEX = "index";

		private static class Index<T> {
			protected final String identity;
			protected final String base;
			protected final String type;
			protected final T data;
			protected final NavigableMap<String, String> nextDataMap;
			protected final int queryFlags;

			public Index(String identity, String base, String type, T data, NavigableMap<String, String> nextDataMap,
					int queryFlags) {
				this.identity = identity;
				this.base = base;
				this.type = type;
				this.data = data;
				this.nextDataMap = nextDataMap;
				this.queryFlags = queryFlags;
			}
		}

		private final Path rootDirectory;
		private final String indexPrimaryRootUrl;
		private String indexSecondaryRootUrl;

		private final ConcurrentSkipListMap<String, Object> indexLocks = new ConcurrentSkipListMap<>();
		private final ConcurrentSkipListMap<String, Index<T>> indexes = new ConcurrentSkipListMap<>();

		public IndexManager(Path rootDirectory, String indexPrimaryRootUrl, String indexSecondaryRootUrl) {
			this.rootDirectory = rootDirectory;
			this.indexPrimaryRootUrl = indexPrimaryRootUrl;
			this.indexSecondaryRootUrl = indexSecondaryRootUrl;
		}

		public T getIndexForName(IndexOperationOptions options, String name) throws IOException {
			return getIndexDataForName(options, name, "", "");
		}

		private T getIndexDataForName(IndexOperationOptions options, String name, String additionalurl,
				String expectedbase) throws IOException {
			Index<T> idx = getIndexForName(options, additionalurl, expectedbase);
			if (idx == null) {
				return null;
			}
			finish_loop:
			while (true) {
				switch (idx.type) {
					case INDEX_TYPE_LOOKUP: {
						return idx.data;
					}
					case INDEX_TYPE_INDEX: {
						if (!ObjectUtils.isNullOrEmpty(idx.nextDataMap)) {
							List<T> datas = new ArrayList<>(idx.nextDataMap.size());
							for (Entry<String, String> entry : idx.nextDataMap.entrySet()) {
								String nextbase = entry.getKey();
								if (!nextbase.startsWith(name)) {
									continue;
								}
								String nextadditionalurl = entry.getValue();
								try {
									T subidxdata = getIndexDataForName(options, name, nextadditionalurl, nextbase);
									datas.add(subidxdata);
								} catch (IndexFileCorruptedIOException e) {
									//requery the index if we find a sub corrupted one
									if (nextadditionalurl.equals(e.additionalUrl) && nextbase.equals(e.base)) {
										synchronized (getIndexLock(additionalurl)) {
											Index<T> cidx = indexes.get(additionalurl);
											if (cidx != idx) {
												//the index in the map was changed meanwhile
												if (cidx != null) {
													idx = cidx;
													if (!expectedbase.equals(idx.base)) {
														throw new IndexFileCorruptedIOException(e, expectedbase,
																additionalurl);
													}
													continue finish_loop;
												}
												//the current index is null
											}
											try {
												JSONObject indexobj = makeIndexRequest(options, additionalurl,
														getIndexFilePath(additionalurl));
												Index<T> reqindex = parseIndexJSON(options, indexobj, additionalurl,
														expectedbase);
												indexes.put(additionalurl, reqindex);
												if (Objects.equals(idx.identity, reqindex.identity)) {
													//no modifications were made to the index, and the sub index was found to be corrupted
													throw new IndexFileCorruptedIOException(e, expectedbase,
															additionalurl);
												}
												idx = reqindex;
											} catch (JSONException je) {
												throw new IndexFileCorruptedIOException(e, expectedbase, additionalurl);
											}
											indexes.put(additionalurl, idx);
											if (!expectedbase.equals(idx.base)) {
												throw new IndexFileCorruptedIOException(e, expectedbase, additionalurl);
											}
											continue finish_loop;
										}
									}
									//else throw the exception further
									if (((options.flags
											& FLAG_MISSING_INDEX_ACCEPTABLE) == FLAG_MISSING_INDEX_ACCEPTABLE)) {
										options.missingIndexFile(nextadditionalurl,
												getIndexFilePath(nextadditionalurl));
										continue;
									}
									throw e;
								} catch (IOException e) {
									if (((options.flags
											& FLAG_MISSING_INDEX_ACCEPTABLE) == FLAG_MISSING_INDEX_ACCEPTABLE)) {
										options.missingIndexFile(nextadditionalurl,
												getIndexFilePath(nextadditionalurl));
										continue;
									}
									throw e;
								}
							}
							if (datas.size() == 1) {
								return datas.get(0);
							}
							if (datas.isEmpty()) {
								return mergeData(Collections.emptyList());
							}
							return mergeData(datas);
						}
						// empty data
						return mergeData(Collections.emptyList());
					}
					default: {
						break;
					}
				}
				return null;
			}
		}

		private Index<T> parseIndexJSON(IndexOperationOptions options, JSONObject indexobj, String additionalurl,
				String expectedbase) throws IOException {
			if (indexobj == null) {
				throw new IndexFileCorruptedIOException(expectedbase, additionalurl);
			}
			String base = indexobj.optString("base");
			if (!expectedbase.equals(base)) {
				throw new IndexFileCorruptedIOException(expectedbase, additionalurl);
			}
			String indextype = indexobj.getString("type");
			switch (indextype) {
				case INDEX_TYPE_LOOKUP: {
					JSONObject nextmap = indexobj.optJSONObject("next");
					NavigableMap<String, String> nextdatamap;
					if (nextmap == null) {
						nextdatamap = Collections.emptyNavigableMap();
					} else {
						nextdatamap = new TreeMap<>();
						Iterator<String> kit = nextmap.keys();
						while (kit.hasNext()) {
							String nkey = kit.next();
							if (ObjectUtils.isNullOrEmpty(nkey)) {
								continue;
							}
							String nindexurl;
							if (nkey.startsWith("/")) {
								nindexurl = nkey;
							} else {
								nindexurl = additionalurl + '/' + nkey;
							}
							String lookupidentity = nextmap.getString(nkey);
							String prev = nextdatamap.putIfAbsent(nindexurl, lookupidentity);
							if (prev != null) {
								throw new IndexFileCorruptedIOException(expectedbase, additionalurl);
							}
						}
					}
					//XXX might paralellize
					List<JSONObject> splits = new ArrayList<>(nextdatamap.size() + 1);
					splits.add(indexobj);
					for (Entry<String, String> entry : nextdatamap.entrySet()) {
						String expectedidentity = entry.getValue();
						String splitfileadditionalurl = entry.getKey();
						JSONObject splitjson;
						Path splitfileindexpath = getIndexFilePath(splitfileadditionalurl);
						try {
							splitjson = makeIndexRequestOrLoadFromFile(options, splitfileadditionalurl,
									splitfileindexpath, expectedidentity);
						} catch (IOException e) {
							if (((options.flags & FLAG_MISSING_INDEX_ACCEPTABLE) == FLAG_MISSING_INDEX_ACCEPTABLE)) {
								options.missingIndexFile(splitfileadditionalurl, splitfileindexpath);
								continue;
							}
							throw e;
						}
						if (!INDEX_TYPE_LOOKUP.equals(splitjson.optString("type", null))) {
							throw new IndexFileCorruptedIOException(expectedbase, additionalurl);
						}
						if (!expectedidentity.equals(splitjson.optString("identity", null))) {
							throw new IndexFileCorruptedIOException(expectedbase, additionalurl);
						}
						splits.add(splitjson);
					}
					T data = generateData(splits);
					return new Index<>(indexobj.getString("identity"), base, INDEX_TYPE_LOOKUP, data,
							Collections.emptyNavigableMap(), options.flags);
				}
				case INDEX_TYPE_INDEX: {
					return parseIndexJSONType(indexobj, additionalurl, base, options);
				}
				default: {
					break;
				}
			}
			return new Index<>(null, base, indextype, null, Collections.emptyNavigableMap(), options.flags);
		}

		private static boolean shouldAttemptIndexReload(Index<?> idx, IndexOperationOptions options) {
			//if the index was loaded in an offline way, then if aren't offline anymore then attempt reload 
			if (((idx.queryFlags & FLAG_OFFLINE) == FLAG_OFFLINE)) {
				if (!((options.flags & FLAG_OFFLINE) == FLAG_OFFLINE)) {
					return true;
				}
			}
			return false;
		}

		private Index<T> getIndexForName(IndexOperationOptions options, String additionalurl, String expectedbase)
				throws IOException {
			Index<T> gotidx = indexes.get(additionalurl);
			index_retriever:
			while (true) {
				if (gotidx != null && expectedbase.equals(gotidx.base) && !shouldAttemptIndexReload(gotidx, options)) {
					return gotidx;
				}
				synchronized (getIndexLock(additionalurl)) {
					Index<T> curidx = indexes.get(additionalurl);
					if (curidx != gotidx) {
						//concurrently changed, test again
						gotidx = curidx;
						continue index_retriever;
					}
					try {
						JSONObject indexobj = makeIndexRequestOrLoadFromFile(options, additionalurl,
								getIndexFilePath(additionalurl), null);
						gotidx = parseIndexJSON(options, indexobj, additionalurl, expectedbase);
					} catch (JSONException e) {
						throw new IndexFileCorruptedIOException(e, expectedbase, additionalurl);
					}
					indexes.put(additionalurl, gotidx);
					return gotidx;
				}
			}
		}

		protected Object getIndexLock(String additionalurl) {
			return indexLocks.computeIfAbsent(additionalurl, Functionals.objectComputer());
		}

		private Path getIndexFilePath(String additionalurl) {
			//use Paths.get instead of resolving, because the url is prefixed by a slash, and it would resolve to a
			//   completely other directory
			//e.g. resolving /url against c:/some/directory yields in c:/url
			return Paths.get(rootDirectory + additionalurl + "/index.json");
		}

		private Index<T> parseIndexJSONType(JSONObject indexobj, String additionalurl, String base,
				IndexOperationOptions options) {
			JSONObject nextmap = indexobj.optJSONObject("next");
			NavigableMap<String, String> nextdatamap;
			if (nextmap != null) {
				nextdatamap = new TreeMap<>();

				Iterator<String> kit = nextmap.keys();
				while (kit.hasNext()) {
					String nkey = kit.next();
					//found the next index file to check
					String nurl = nextmap.optString(nkey, null);
					if (ObjectUtils.isNullOrEmpty(nurl)) {
						//some invalid index entry, ignore
						continue;
					}
					if ("..".equals(nurl) || ".".equals(nurl) || nurl.startsWith("./") || nurl.startsWith("../")
							|| nurl.endsWith("/..") || nurl.endsWith("/.") || nurl.contains("/../")
							|| nurl.contains("/./")) {
						//the url contains reserved path names
						continue;
					}
					String nindexurl;
					if (nurl.startsWith("/")) {
						nindexurl = nurl;
					} else {
						nindexurl = additionalurl + '/' + nurl;
					}
					String nbase = base + nkey;
					nextdatamap.putIfAbsent(nbase, nindexurl);
				}
			} else {
				//consider the index to be empty
				nextdatamap = Collections.emptyNavigableMap();
			}
			return new Index<>(indexobj.getString("identity"), base, INDEX_TYPE_INDEX, null, nextdatamap,
					options.flags);
		}

		//this method is synchronized externally
		private JSONObject makeIndexRequestOrLoadFromFile(IndexOperationOptions options, String additionalurl,
				Path indexfilepath, String expectedidentity) throws IOException {
			if (((options.flags & FLAG_NO_LOADING_FROM_FILE) != FLAG_NO_LOADING_FROM_FILE)) {
				file_reader:
				try {
					boolean acceptcached = (options.flags & FLAG_OFFLINE) == FLAG_OFFLINE;
					if (expectedidentity == null) {
						if (!((options.flags & FLAG_OFFLINE) == FLAG_OFFLINE)) {
							//only check validation of the file if we can make requests
							//if we are offline, always consider the file valid
							//we an expected identity was specified, then don't check the expiration, and always read the file and check the identity
							//    in that case the expiration is checked on the parent index file
							FileTime lastmodtime = Files.getLastModifiedTime(indexfilepath);
							long currenttime = System.currentTimeMillis();
							long modtime = lastmodtime.toMillis();
							if (modtime > currenttime || modtime + INDEX_INVALIDATION_TIME_MILLIS < currenttime) {
								//the index file is considered to be expired
								break file_reader;
							}
							acceptcached = true;
						}
					}
					try (InputStream is = Files.newInputStream(indexfilepath);
							InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
						JSONObject result = new JSONObject(new JSONTokener(reader));
						//we accept the cached file if
						//    we're offline (or cache not expired) and expect no identity
						//    or the identity is the same as the expected
						if (expectedidentity == null) {
							if (acceptcached) {
								//notify the options about the non-query reuse
								//the operation initiator may issue async load request if appropriate
								options.indexFileOfflineReused(additionalurl, indexfilepath);
								return result;
							}
						} else if (expectedidentity.equals(result.getString("identity"))) {
							return result;
						}
						//can't accept cached file
					}
				} catch (IOException | JSONException e) {
					//the index file doesn't exist or cant be read
				}
			}
			return makeIndexRequest(options, additionalurl, indexfilepath);
		}

		//locked by the caller
		private JSONObject makeIndexRequest(IndexOperationOptions options, String additionalurl, Path indexfilepath)
				throws IOException {
			boolean offline = (options.flags & FLAG_OFFLINE) == FLAG_OFFLINE;
			if (offline) {
				throw new OfflineStorageIndexIOException(additionalurl, indexfilepath);
			}
			JSONObject resultjson;
			if (indexSecondaryRootUrl != null && ((options.flags & FLAG_REQUESTS_UNCACHE) != FLAG_REQUESTS_UNCACHE)) {
				try {
					resultjson = makeJsonIndexRequest(options, additionalurl, indexSecondaryRootUrl);
				} catch (ServerConnectionFailedIOException e) {
					//try to connect to the primary server
					//disable further connection to the secondary server
					indexSecondaryRootUrl = null;
					try {
						resultjson = makeJsonIndexRequest(options, additionalurl, indexPrimaryRootUrl);
					} catch (IOException e2) {
						e2.addSuppressed(e);
						throw e2;
					}
				} catch (JSONException e) {
					//shouldnt really happen, but in this case fall back to the primary url
					resultjson = null;
				} catch (IOException e) {
					// fall back to primary url
					resultjson = null;
				}
				if (resultjson == null) {
					resultjson = makeJsonIndexRequest(options, additionalurl, indexPrimaryRootUrl);
				}
			} else {
				resultjson = makeJsonIndexRequest(options, additionalurl, indexPrimaryRootUrl);
			}
			if (resultjson != null) {
				Path tempsibling = indexfilepath.resolveSibling(UUID.randomUUID() + ".temp");
				try {
					Files.createDirectories(indexfilepath.getParent());
					try (OutputStream os = Files.newOutputStream(tempsibling, StandardOpenOption.CREATE_NEW);
							OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
						resultjson.write(writer);
					}
					//we can replace the existing file
					Files.move(tempsibling, indexfilepath, StandardCopyOption.REPLACE_EXISTING);
					//remove from the index so it can be refreshed
					indexes.remove(additionalurl);
				} catch (IOException e) {
					//failed to write the index file, shouldn't happen, only if the index has a path with "index.json"
				} finally {
					try {
						Files.deleteIfExists(tempsibling);
					} catch (IOException e) {
					}
				}
			}
			return resultjson;
		}

		private static JSONObject makeJsonIndexRequest(IndexOperationOptions options, String additionalurl,
				String rooturl) throws IOException {
			boolean offline = (options.flags & FLAG_OFFLINE) == FLAG_OFFLINE;
			String url = rooturl + additionalurl;
			if (((options.flags & FLAG_REQUESTS_UNCACHE) == FLAG_REQUESTS_UNCACHE)) {
				url += "?uncache-" + UUID.randomUUID();
			}
			return makeServerRequest((offline ? FLAG_REQUEST_OFFLINE : 0) | FLAG_REQUEST_FOLLOW_REDIRECTS, url, "GET",
					(requrl, rc, ins, errs, headerfunc) -> {
						if (rc == HttpURLConnection.HTTP_OK) {
							try (InputStream is = ins.get();
									InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
								return new JSONObject(new JSONTokener(reader));
							}
						}
						throw new IOException("Unexpected response code from request: " + rc);
					});
		}

		protected abstract T generateData(Collection<JSONObject> lookups);

		protected abstract T mergeData(Collection<? extends T> datas);

		public void updateIndexFiles() throws IOException {
			getIndexForName(new IndexOperationOptions(FLAG_NO_LOADING_FROM_FILE | FLAG_REQUESTS_UNCACHE), "");
		}
	}

	private static final class TasksIndexManager
			extends IndexManager<NavigableMap<String, NavigableSet<BundleIdentifier>>> {
		public TasksIndexManager(Path rootDirectory, String indexPrimaryRootUrl, String indexSecondaryRootUrl) {
			super(rootDirectory, indexPrimaryRootUrl, indexSecondaryRootUrl);
		}

		@Override
		protected NavigableMap<String, NavigableSet<BundleIdentifier>> generateData(Collection<JSONObject> lookups) {
			NavigableMap<String, NavigableSet<BundleIdentifier>> result = new TreeMap<>();
			for (JSONObject lookup : lookups) {
				if (lookup == null) {
					continue;
				}
				JSONObject tasks = lookup.optJSONObject("tasks");
				if (tasks == null) {
					continue;
				}
				Iterator<String> kit = tasks.keys();
				while (kit.hasNext()) {
					String tn = kit.next();
					try {
						//sanitize task name
						tn = TaskName.valueOf(tn).getName();
					} catch (IllegalArgumentException e) {
						continue;
					}
					JSONArray bundlesarray;
					try {
						bundlesarray = tasks.getJSONArray(tn);
					} catch (JSONException e) {
						continue;
					}
					NavigableSet<BundleIdentifier> bundlesset = result.computeIfAbsent(tn,
							Functionals.treeSetComputer());
					Iterator<Object> bit = bundlesarray.iterator();
					while (bit.hasNext()) {
						String nbundlestr = bit.next().toString();
						BundleIdentifier bundleid;
						try {
							//sanitize package name
							bundleid = BundleIdentifier.valueOf(nbundlestr);
						} catch (IllegalArgumentException e) {
							continue;
						}
						if (bundleid.getVersionQualifier() == null) {
							continue;
						}
						bundlesset.add(bundleid);
					}
				}
			}
			return result;
		}

		@Override
		protected NavigableMap<String, NavigableSet<BundleIdentifier>> mergeData(
				Collection<? extends NavigableMap<String, NavigableSet<BundleIdentifier>>> datas) {
			if (ObjectUtils.isNullOrEmpty(datas)) {
				return Collections.emptyNavigableMap();
			}
			NavigableMap<String, NavigableSet<BundleIdentifier>> result = new TreeMap<>();
			for (NavigableMap<String, NavigableSet<BundleIdentifier>> data : datas) {
				for (Entry<String, NavigableSet<BundleIdentifier>> entry : data.entrySet()) {
					result.computeIfAbsent(entry.getKey(), Functionals.treeSetComputer()).addAll(entry.getValue());
				}
			}
			return result;
		}
	}

	private static final class BundlesIndexManager
			extends IndexManager<NavigableMap<String, NavigableSet<BundleIdentifier>>> {
		public BundlesIndexManager(Path rootDirectory, String indexPrimaryRootUrl, String indexSecondaryRootUrl) {
			super(rootDirectory, indexPrimaryRootUrl, indexSecondaryRootUrl);
		}

		@Override
		protected NavigableMap<String, NavigableSet<BundleIdentifier>> generateData(Collection<JSONObject> lookups) {
			NavigableMap<String, NavigableSet<BundleIdentifier>> result = new TreeMap<>();
			for (JSONObject lookup : lookups) {
				if (lookup == null) {
					continue;
				}
				JSONArray bundlesarray = lookup.optJSONArray("bundles");
				if (bundlesarray == null) {
					continue;
				}
				int len = bundlesarray.length();
				for (int i = 0; i < len; i++) {
					String item;
					try {
						item = bundlesarray.getString(i);
					} catch (JSONException e) {
						continue;
					}
					BundleIdentifier bid;
					try {
						bid = BundleIdentifier.valueOf(item);
					} catch (IllegalArgumentException e) {
						continue;
					}
					if (bid.getVersionQualifier() == null) {
						continue;
					}
					result.computeIfAbsent(bid.getName(), Functionals.treeSetComputer()).add(bid);
				}
			}
			return result;
		}

		@Override
		protected NavigableMap<String, NavigableSet<BundleIdentifier>> mergeData(
				Collection<? extends NavigableMap<String, NavigableSet<BundleIdentifier>>> datas) {
			if (ObjectUtils.isNullOrEmpty(datas)) {
				return Collections.emptyNavigableMap();
			}
			NavigableMap<String, NavigableSet<BundleIdentifier>> result = new TreeMap<>();
			for (NavigableMap<String, NavigableSet<BundleIdentifier>> data : datas) {
				for (Entry<String, NavigableSet<BundleIdentifier>> entry : data.entrySet()) {
					result.computeIfAbsent(entry.getKey(), Functionals.treeSetComputer()).addAll(entry.getValue());
				}
			}
			return result;
		}
	}

	private static final class ServerStorageViewKeyImpl implements StorageViewKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private AbstractStorageKey storageKey;
		private boolean offline;
		private BundleSignatureVerificationConfiguration signatureVerificationConfiguration;

		/**
		 * For {@link Externalizable}.
		 */
		public ServerStorageViewKeyImpl() {
		}

		public ServerStorageViewKeyImpl(AbstractStorageKey storageKey, boolean offline,
				BundleSignatureVerificationConfiguration signatureVerificationConfiguration) {
			this.storageKey = storageKey;
			this.offline = offline;
			this.signatureVerificationConfiguration = signatureVerificationConfiguration;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(storageKey);
			out.writeBoolean(offline);
			out.writeObject(signatureVerificationConfiguration);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			storageKey = (AbstractStorageKey) in.readObject();
			offline = in.readBoolean();
			signatureVerificationConfiguration = (BundleSignatureVerificationConfiguration) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (offline ? 1231 : 1237);
			result = prime * result + ((signatureVerificationConfiguration == null) ? 0
					: signatureVerificationConfiguration.hashCode());
			result = prime * result + ((storageKey == null) ? 0 : storageKey.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ServerStorageViewKeyImpl other = (ServerStorageViewKeyImpl) obj;
			if (offline != other.offline)
				return false;
			if (signatureVerificationConfiguration == null) {
				if (other.signatureVerificationConfiguration != null)
					return false;
			} else if (!signatureVerificationConfiguration.equals(other.signatureVerificationConfiguration))
				return false;
			if (storageKey == null) {
				if (other.storageKey != null)
					return false;
			} else if (!storageKey.equals(other.storageKey))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (storageKey != null ? "storageKey=" + storageKey + ", " : "")
					+ "offline=" + offline + ", "
					+ (signatureVerificationConfiguration != null
							? "signatureVerificationConfiguration=" + signatureVerificationConfiguration
							: "")
					+ "]";
		}
	}

	private class ServerBundleStorageViewImpl extends AbstractServerBundleStorageView {
		private final StorageViewKey storageViewKey;

		private final boolean offline;
		private final BundleSignatureVerificationConfiguration signatureVerificationConfiguration;
		private final int uncacheRequestsIndexFlag;

		private ConcurrentSkipListSet<String> bundleInformationProviderAsyncDownloadedIndexes = new ConcurrentSkipListSet<>();
		private ConcurrentSkipListSet<String> taskInformationProviderAsyncDownloadedIndexes = new ConcurrentSkipListSet<>();
		private ConcurrentSkipListSet<BundleIdentifier> informationProviderAsyncDownloadedBundles = new ConcurrentSkipListSet<>();

		public ServerBundleStorageViewImpl(boolean offline,
				BundleSignatureVerificationConfiguration signatureVerificationConfiguration, boolean uncacherequests) {
			this.signatureVerificationConfiguration = signatureVerificationConfiguration;
			this.storageViewKey = new ServerStorageViewKeyImpl(ServerBundleStorage.this.getStorageKey(), offline,
					signatureVerificationConfiguration);
			this.offline = offline;
			this.uncacheRequestsIndexFlag = uncacherequests ? IndexManager.FLAG_REQUESTS_UNCACHE : 0;
		}

		@Override
		public void updateBundleIndexFiles() throws IOException {
			if (offline) {
				throw new OfflineStorageIOException("Cannot update index files in offline mode.");
			}
			packageBundlesIndexManager.updateIndexFiles();
		}

		@Override
		public void updateTaskIndexFiles() throws IOException {
			if (offline) {
				throw new OfflineStorageIOException("Cannot update index files in offline mode.");
			}
			tasksIndexManager.updateIndexFiles();
		}

		@Override
		public StorageViewKey getStorageViewKey() {
			return storageViewKey;
		}

		@Override
		public boolean isOffline() {
			return offline;
		}

		@Override
		public String getServerHost() {
			return ServerBundleStorage.this.getServerHost();
		}

		@Override
		public Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
				ExternalDependencyInformation depinfo)
				throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
			return repository.loadExternalArchives(depinfo, this);
		}

		@Override
		public InputStream openExternalDependencyURI(URI uri, Hashes expectedhashes) throws IOException {
			if (expectedhashes == null || expectedhashes.sha256 == null) {
				throw new FileNotFoundException("Failed to download external dependency without SHA-256: " + uri);
			}
			String url = serverHost + "/external/mirror/" + BundleUtils.sha256(uri) + "/" + expectedhashes.sha256;
			return makeServerRequest((offline ? FLAG_REQUEST_OFFLINE : 0) | FLAG_REQUEST_NO_DISCONNECT_ON_200
					| FLAG_REQUEST_FOLLOW_REDIRECTS, url, "GET", (requrl, rc, ins, errs, headerfunc) -> {
						if (rc == HttpURLConnection.HTTP_OK) {
							//HTTP OK
							return ins.get();
						}
						IOException ee = null;
						String errcontent = "";
						try {
							errcontent = StreamUtils.readStreamStringFully(errs.get());
						} catch (IOException e) {
							ee = e;
						}
						StringBuilder sb = new StringBuilder();
						sb.append("Failed to download external dependency: ");
						sb.append(uri);
						sb.append(" from ");
						sb.append(url);
						sb.append(" Response code: ");
						sb.append(rc);
						if (!errcontent.isEmpty()) {
							sb.append(" Response content: ");
							sb.append(errcontent);
						}
						IOException exc = new IOException(sb.toString());
						IOUtils.addExc(exc, ee);
						throw exc;
					});
		}

		@Override
		public void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid,
				String storagename) {
			userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_OFFLINE, Boolean.toString(offline));
			if (serverHost != null) {
				userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_URL, serverHost);
			}
			if (storageKey.serverSecondaryHost != null) {
				userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_SECONDARY_URL,
						storageKey.serverSecondaryHost);
			}
			if (signatureVerificationConfiguration.loadWithoutSignature) {
				userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_SIGNATURE_VERIFICATION, "false");
			}
			userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_MIN_SIGNATURE_VERSION,
					Integer.toString(signatureVerificationConfiguration.minSignatureVersion));
		}

		@Override
		public void updateStorageViewHash(MessageDigest digest) {
			digest.update((ConfiguredRepositoryStorage.STORAGE_TYPE_SERVER + ":(" + serverHost + ")")
					.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public NestRepositoryBundle lookupTaskBundle(TaskName taskname) throws TaskNotFoundException, IOException {
			NavigableSet<BundleIdentifier> bundlesfortask = getBundlesForTaskName(taskname,
					this.uncacheRequestsIndexFlag | (offline ? IndexManager.FLAG_OFFLINE : 0));
			BundleIdentifier chosenbundle = BundleUtils.selectAppropriateBundleIdentifierForTask(taskname,
					bundlesfortask);
			if (chosenbundle == null) {
				throw new TaskNotFoundException("No bundle found for task. ", taskname);
			}
			try {
				return getBundle(chosenbundle);
			} catch (BundleLoadingFailedException e) {
				throw new TaskNotFoundException("Task not found in bundle: " + chosenbundle, e, taskname);
			}
		}

		@Override
		public Set<BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException {
			Objects.requireNonNull(bundleid, "bundle identifier");
			String packagename = bundleid.getName();
			try {
				NavigableMap<String, NavigableSet<BundleIdentifier>> packages = packageBundlesIndexManager
						.getIndexForName(
								new IndexOperationOptions(
										this.uncacheRequestsIndexFlag | (offline ? IndexManager.FLAG_OFFLINE : 0)),
								packagename);
				NavigableSet<BundleIdentifier> bundles = ObjectUtils.getMapValue(packages, packagename);
				if (ObjectUtils.isNullOrEmpty(bundles)) {
					return Collections.emptySet();
				}
				NavigableMap<String, BundleIdentifier> lookupres = new TreeMap<>(
						Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
				for (BundleIdentifier b : bundles) {
					if (b.getBundleQualifiers().equals(bundleid.getBundleQualifiers())) {
						String vnum = b.getVersionNumber();
						if (vnum != null) {
							lookupres.put(vnum, b);
						}
					}
				}
				return new LinkedHashSet<>(lookupres.values());
			} catch (IOException e) {
				// XXX how to handle this exception?
				e.printStackTrace();
				return Collections.emptySet();
			}
		}

		@Override
		public Map<String, ? extends Set<? extends BundleIdentifier>> lookupBundleIdentifiers(String bundlename)
				throws NullPointerException, IllegalArgumentException {
			Objects.requireNonNull(bundlename, "bundle name");
			if (!BundleIdentifier.isValidBundleName(bundlename)) {
				throw new IllegalArgumentException("Invalid bundle name: " + bundlename);
			}

			try {
				NavigableMap<String, NavigableSet<BundleIdentifier>> packages = packageBundlesIndexManager
						.getIndexForName(
								new IndexOperationOptions(
										this.uncacheRequestsIndexFlag | (offline ? IndexManager.FLAG_OFFLINE : 0)),
								bundlename);
				NavigableSet<BundleIdentifier> bundles = ObjectUtils.getMapValue(packages, bundlename);
				if (ObjectUtils.isNullOrEmpty(bundles)) {
					return Collections.emptyNavigableMap();
				}
				Map<String, Set<BundleIdentifier>> result = new TreeMap<>(
						Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
				for (BundleIdentifier b : bundles) {
					String vnum = b.getVersionNumber();
					if (vnum != null) {
						result.computeIfAbsent(vnum, Functionals.treeSetComputer()).add(b);
					}
				}
				return result;
			} catch (IOException e) {
				// XXX how to handle this exception?
				e.printStackTrace();
				return Collections.emptyNavigableMap();
			}
		}

		@Override
		public AbstractBundleStorage getStorage() {
			return ServerBundleStorage.this;
		}

		@Override
		public AbstractNestRepositoryBundle getBundle(BundleIdentifier bundleid) throws BundleLoadingFailedException {
			return getBundleImpl(bundleid, this.offline);
		}

		private AbstractNestRepositoryBundle getBundleImpl(BundleIdentifier bundleid, boolean offline)
				throws BundleLoadingFailedException {
			Objects.requireNonNull(bundleid, "bundleid");
			if (bundleid.getVersionQualifier() == null) {
				throw new BundleLoadingFailedException("Failed to load bundle without version qualifier: " + bundleid);
			}
			try {
				LoadedBundleState got = loadedBundles.get(bundleid);
				if (got != null) {
					got.verifyBundleWithConfig(signatureVerificationConfiguration, offline);
					return got.bundle;
				}
				synchronized (bundleLoadLocks.computeIfAbsent(bundleid, Functionals.objectComputer())) {
					got = loadedBundles.get(bundleid);
					if (got != null) {
						got.verifyBundleWithConfig(signatureVerificationConfiguration, offline);
						return got.bundle;
					}
					if (closed) {
						throw new BundleLoadingFailedException("Storage closed.");
					}
					//load the bundle ourselves
					Path bundlejarpath = BundleUtils.getVersionedBundleJarPath(bundlesDirectory, bundleid);
					if (Files.isRegularFile(bundlejarpath)) {
						try {
							got = new LoadedBundleState(bundleid, null, bundlejarpath,
									signatureVerificationConfiguration, offline);
							loadedBundles.put(bundleid, got);
							if (!bundleid.equals(got.bundle.getBundleIdentifier())) {
								throw new BundleLoadingFailedException("Bundle identifier mismatch: "
										+ got.bundle.getBundleIdentifier() + " with expected: " + bundleid);
							}
							got.verifyBundleWithConfig(signatureVerificationConfiguration, offline);
							return got.bundle;
						} catch (IOException e) {
							//XXX tell that deleting it might help
							throw new BundleLoadingFailedException(
									"Failed to load bundle: " + bundleid + " from storage: " + bundlejarpath, e);
						} catch (InvalidNestBundleException e) {
							throw new BundleLoadingFailedException(
									"Failed to load bundle: " + bundleid + " from storage: " + bundlejarpath, e);
						}
					}
					//download the bundle
					DownloadedBundle downloadres = downloadBundle(bundleid, bundlejarpath, offline);
					try {
						got = new LoadedBundleState(bundleid, null, downloadres.bundle,
								signatureVerificationConfiguration, offline);
					} catch (IOException e) {
						throw new BundleLoadingFailedException(
								"Failed to load bundle: " + bundleid + " from storage: " + bundlejarpath, e);
					}
					loadedBundles.put(bundleid, got);
					got.verifyBundleWithConfig(signatureVerificationConfiguration, downloadres.signature, offline);
					return got.bundle;
				}
			} catch (NestSignatureVerificationException e) {
				throw new BundleLoadingFailedException("Failed to load bundle: " + bundleid, e);
			}
		}

		@Override
		public Object detectChanges(ExecutionPathConfiguration pathconfig) {
			//XXX maybe detect any index file deletions? so if the user deletes the index files while we're loaded, we reload the database
			//no change detection for a server storage
			return null;
		}

		@Override
		public void handleChanges(ExecutionPathConfiguration pathconfig, Object detetedchanges) {
		}

		@Override
		public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
			NavigableSet<TaskName> result = new TreeSet<>();
			try {
				IndexOperationOptions opoptions = new AsyncDownloadStartingIndexOperationOptions(
						this.uncacheRequestsIndexFlag | IndexManager.FLAG_MISSING_INDEX_ACCEPTABLE
								| IndexManager.FLAG_OFFLINE,
						this::startTaskIndexAsyncDownload);
				NavigableMap<String, NavigableSet<BundleIdentifier>> taskbundles = tasksIndexManager
						.getIndexForName(opoptions, "");
				if (!ObjectUtils.isNullOrEmpty(taskbundles)) {
					NavigableSet<String> qualifierbuffer = new TreeSet<>();
					for (Entry<String, NavigableSet<BundleIdentifier>> entry : taskbundles.entrySet()) {
						String tn = entry.getKey();
						for (BundleIdentifier bundle : entry.getValue()) {
							qualifierbuffer.clear();
							qualifierbuffer.addAll(bundle.getBundleQualifiers());
							qualifierbuffer.addAll(bundle.getMetaQualifiers());
							result.add(TaskName.valueOf(tn, qualifierbuffer));
						}
					}
				}
			} catch (OfflineStorageIndexIOException e) {
				if (!offline) {
					startTaskIndexAsyncDownload(e);
				}
			} catch (IOException e) {
				e.printStackTrace();
				//no need to print the stack trace if we have an exception for information provider
			}
			return result;
		}

		//XXX start index download for missing index files and handle gracefully
		@Override
		public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
			NavigableSet<BundleIdentifier> result = new TreeSet<>();
			try {
				IndexOperationOptions opoptions = new AsyncDownloadStartingIndexOperationOptions(
						this.uncacheRequestsIndexFlag | IndexManager.FLAG_MISSING_INDEX_ACCEPTABLE
								| IndexManager.FLAG_OFFLINE,
						this::startBundleIndexAsyncDownload);
				NavigableMap<String, NavigableSet<BundleIdentifier>> bundlepackages = packageBundlesIndexManager
						.getIndexForName(opoptions, "");
				if (!ObjectUtils.isNullOrEmpty(bundlepackages)) {
					bundlepackages.values().forEach(result::addAll);
				}
			} catch (OfflineStorageIndexIOException e) {
				if (!offline) {
					startBundleIndexAsyncDownload(e);
				}
			} catch (IOException e) {
				//no need to print the stack trace if we have an exception for information provider
			}
			return result;
		}

		@Override
		public NestRepositoryBundle lookupTaskBundleForInformationProvider(TaskName taskname) {
			try {
				IndexOperationOptions opoptions = new AsyncDownloadStartingIndexOperationOptions(
						this.uncacheRequestsIndexFlag | IndexManager.FLAG_MISSING_INDEX_ACCEPTABLE
								| IndexManager.FLAG_OFFLINE,
						this::startTaskIndexAsyncDownload);
				NavigableSet<BundleIdentifier> bundlesfortask = getBundlesForTaskName(taskname, opoptions);
				BundleIdentifier chosenbundle = BundleUtils.selectAppropriateBundleIdentifierForTask(taskname,
						bundlesfortask);
				if (chosenbundle == null) {
					return null;
				}
				try {
					return getBundleImpl(chosenbundle, true);
				} catch (BundleLoadingFailedException e) {
					if (!offline && hasOfflineStorageIOExceptionCause(e)) {
						startBundleAsyncDownload(chosenbundle);
					}
					return null;
				}
			} catch (OfflineStorageIndexIOException e) {
				if (!offline) {
					startTaskIndexAsyncDownload(e);
				}
			} catch (IOException e) {
			}
			return null;
		}

		private void startBundleAsyncDownload(BundleIdentifier chosenbundle) {
			if (!informationProviderAsyncDownloadedBundles.add(chosenbundle)) {
				return;
			}
			Runnable runnable = () -> {
				try {
					getBundleImpl(chosenbundle, false);
				} catch (BundleLoadingFailedException ee) {
					//ignored
				} catch (Exception ee) {
					//ignore, but print it as it is unexpected
					ee.printStackTrace();
				} catch (LinkageError ee) {
					//the repository may have been unloaded meanwhile
				}
			};
			if (TestFlag.ENABLED) {
				runnable.run();
			} else {
				ThreadUtils.startDaemonThread("Bundle downloader", runnable);
			}
		}

		private void startTaskIndexAsyncDownload(OfflineStorageIndexIOException e) {
			startTaskIndexAsyncDownload(e.getAdditonalUrl(), e.getIndexFilePath());
		}

		private void startTaskIndexAsyncDownload(String additionalurl, Path indexfilepath) {
			if (!taskInformationProviderAsyncDownloadedIndexes.add(additionalurl)) {
				return;
			}
			Runnable runnable = () -> {
				try {
					synchronized (tasksIndexManager.getIndexLock(additionalurl)) {
						tasksIndexManager.makeIndexRequest(IndexOperationOptions.NOFLAGS, additionalurl, indexfilepath);
					}
				} catch (IOException ee) {
					//failed to complete the requiest. ignoreable
				} catch (Exception ee) {
					//ignore, but print it as it is unexpected
					ee.printStackTrace();
				} catch (LinkageError ee) {
					//the repository may have been unloaded meanwhile
				}
			};
			if (TestFlag.ENABLED) {
				runnable.run();
			} else {
				ThreadUtils.startDaemonThread("Task index downloader", runnable);
			}
		}

		private void startBundleIndexAsyncDownload(OfflineStorageIndexIOException e) {
			startBundleIndexAsyncDownload(e.getAdditonalUrl(), e.getIndexFilePath());
		}

		private void startBundleIndexAsyncDownload(String additionalurl, Path indexfilepath) {
			if (!bundleInformationProviderAsyncDownloadedIndexes.add(additionalurl)) {
				return;
			}
			Runnable runnable = () -> {
				try {
					synchronized (packageBundlesIndexManager.getIndexLock(additionalurl)) {
						packageBundlesIndexManager.makeIndexRequest(IndexOperationOptions.NOFLAGS, additionalurl,
								indexfilepath);
					}
				} catch (IOException ee) {
					//failed to complete the requiest. ignoreable
				} catch (Exception ee) {
					//ignore, but print it as it is unexpected
					ee.printStackTrace();
				} catch (LinkageError ee) {
					//the repository may have been unloaded meanwhile
				}
			};
			if (TestFlag.ENABLED) {
				runnable.run();
			} else {
				ThreadUtils.startDaemonThread("Bundle index downloader", runnable);
			}
		}

	}

}
