package test.nest.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.nest.NestMetric;
import testing.saker.nest.util.NestIntegrationTestUtils;

public class BasicServerNestMetric implements NestMetric {
	protected KeyPair bundleSigningKeyPair = NestIntegrationTestUtils.generateRSAKeyPair();
	protected int bundleSigningVersion = 1;

	public BasicServerNestMetric() {
	}

	@Override
	public PublicKey overrideServerBundleSignaturePublicKey(String server, int version) {
		if ("https://testurl".equals(server) && version == bundleSigningVersion) {
			return bundleSigningKeyPair.getPublic();
		}
		return NestMetric.super.overrideServerBundleSignaturePublicKey(server, version);
	}

	@Override
	public Integer getServerRequestResponseCode(String requesturl) throws IOException {
		if (("https://testurl/bundle_signature_key/" + bundleSigningVersion).equals(requesturl)) {
			return HttpURLConnection.HTTP_OK;
		}
		return HttpURLConnection.HTTP_NOT_FOUND;
	}

	@Override
	public InputStream getServerRequestResponseStream(String requesturl) throws IOException {
		if (("https://testurl/bundle_signature_key/" + bundleSigningVersion).equals(requesturl)) {
			return new UnsyncByteArrayInputStream(bundleSigningKeyPair.getPublic().getEncoded());
		}
		throw new IOException("No input: " + requesturl);
	}

	protected void applyBundleDownloadSignatureResponseHeaders(String requesturl, Map<String, String> result) {
		System.out.println("BasicServerNestMetric.applyBundleDownloadSignatureResponseHeaders() " + requesturl + " -> "
				+ bundleSigningVersion);
		try {
			if (requesturl.startsWith("https://testurl/bundle/download/")) {
				Signature sig = Signature.getInstance("SHA256withRSA");
				sig.initSign(getBundleSigningPrivateKey());
				try (InputStream in = getServerRequestResponseStream(requesturl)) {
					StreamUtils.copyStream(in, sig);
				}
				byte[] signaturebytes = sig.sign();
				result.put("Nest-Bundle-Signature", Base64.getUrlEncoder().encodeToString(signaturebytes));
				result.put("Nest-Bundle-Signature-Version", bundleSigningVersion + "");
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	protected PrivateKey getBundleSigningPrivateKey() {
		return bundleSigningKeyPair.getPrivate();
	}

	@Override
	public Map<String, String> getServerRequestResponseHeaders(String requesturl) {
		Map<String, String> result = new TreeMap<>();
		applyBundleDownloadSignatureResponseHeaders(requesturl, result);
		return result;
	}
}
