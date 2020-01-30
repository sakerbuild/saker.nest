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
		return metric != null ? metric.getServerRequestResponseCode(requesturl)
				: NestMetric.super.getServerRequestResponseCode(requesturl);
	}

	@Override
	public InputStream getServerRequestResponseStream(String requesturl) throws IOException {
		return metric != null ? metric.getServerRequestResponseStream(requesturl)
				: NestMetric.super.getServerRequestResponseStream(requesturl);
	}

	@Override
	public InputStream getServerRequestResponseErrorStream(String requesturl) throws IOException {
		return metric != null ? metric.getServerRequestResponseErrorStream(requesturl)
				: NestMetric.super.getServerRequestResponseErrorStream(requesturl);
	}

	@Override
	public Map<String, String> getServerRequestResponseHeaders(String requesturl) {
		return metric != null ? metric.getServerRequestResponseHeaders(requesturl)
				: NestMetric.super.getServerRequestResponseHeaders(requesturl);
	}

	@Override
	public PublicKey overrideServerBundleSignaturePublicKey(String server, int version) {
		return metric != null ? metric.overrideServerBundleSignaturePublicKey(server, version)
				: NestMetric.super.overrideServerBundleSignaturePublicKey(server, version);
	}

	@Override
	public boolean allowCachedVerificationState(String bundleid) {
		return metric != null ? metric.allowCachedVerificationState(bundleid)
				: NestMetric.super.allowCachedVerificationState(bundleid);
	}

	@Override
	public FileHashResult overrideParameterBundlePerceivedHash(SakerFileProvider fp, SakerPath bundlepath,
			String algorithm) throws NoSuchAlgorithmException, IOException {
		return metric != null ? metric.overrideParameterBundlePerceivedHash(fp, bundlepath, algorithm)
				: NestMetric.super.overrideParameterBundlePerceivedHash(fp, bundlepath, algorithm);
	}

	@Override
	public boolean offerConfiguredRepositoryCache(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters, Object configuredstorage) {
		return metric != null
				? metric.offerConfiguredRepositoryCache(repositoryobj, repoid, pathconfig, userparameters,
						configuredstorage)
				: NestMetric.super.offerConfiguredRepositoryCache(repositoryobj, repoid, pathconfig, userparameters,
						configuredstorage);
	}

	@Override
	public Object retrieveCachedConfiguredRepository(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters) {
		return metric != null
				? metric.retrieveCachedConfiguredRepository(repositoryobj, repoid, pathconfig, userparameters)
				: NestMetric.super.retrieveCachedConfiguredRepository(repositoryobj, repoid, pathconfig,
						userparameters);
	}

	@Override
	public Boolean overrideServerUncacheRequestsValue() {
		return metric != null ? metric.overrideServerUncacheRequestsValue()
				: NestMetric.super.overrideServerUncacheRequestsValue();
	}

	@Override
	public Path overrideNativeLibraryPath(ClassLoader cl, Path libpath) {
		return metric != null ? metric.overrideNativeLibraryPath(cl, libpath)
				: NestMetric.super.overrideNativeLibraryPath(cl, libpath);
	}
}
