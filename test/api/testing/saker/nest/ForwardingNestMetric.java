package testing.saker.nest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;

public class ForwardingNestMetric implements NestMetric {
	protected final NestMetric metric;

	public ForwardingNestMetric(NestMetric metric) {
		this.metric = metric;
	}

	@Override
	public Integer getServerRequestResponseCode(String requesturl) throws IOException {
		return metric.getServerRequestResponseCode(requesturl);
	}

	@Override
	public InputStream getServerRequestResponseStream(String requesturl) throws IOException {
		return metric.getServerRequestResponseStream(requesturl);
	}

	@Override
	public InputStream getServerRequestResponseErrorStream(String requesturl) throws IOException {
		return metric.getServerRequestResponseErrorStream(requesturl);
	}

	@Override
	public Map<String, String> getServerRequestResponseHeaders(String requesturl) {
		return metric.getServerRequestResponseHeaders(requesturl);
	}

	@Override
	public PublicKey overrideServerBundleSignaturePublicKey(String server, int version) {
		return metric.overrideServerBundleSignaturePublicKey(server, version);
	}

	@Override
	public boolean allowCachedVerificationState(String bundleid) {
		return metric.allowCachedVerificationState(bundleid);
	}

	@Override
	public FileHashResult overrideParameterBundlePerceivedHash(SakerFileProvider fp, SakerPath bundlepath,
			String algorithm) throws NoSuchAlgorithmException, IOException {
		return metric.overrideParameterBundlePerceivedHash(fp, bundlepath, algorithm);
	}

	@Override
	public boolean offerConfiguredRepositoryCache(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters, Object configuredstorage) {
		return metric.offerConfiguredRepositoryCache(repositoryobj, repoid, pathconfig, userparameters,
				configuredstorage);
	}

	@Override
	public Object retrieveCachedConfiguredRepository(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters) {
		return metric.retrieveCachedConfiguredRepository(repositoryobj, repoid, pathconfig, userparameters);
	}

	@Override
	public Boolean overrideServerUncacheRequestsValue() {
		return metric.overrideServerUncacheRequestsValue();
	}

	@Override
	public Path overrideNativeLibraryPath(ClassLoader cl, Path libpath) {
		return metric.overrideNativeLibraryPath(cl, libpath);
	}
}
