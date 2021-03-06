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
		System.out
				.println("BasicServerNestMetric.overrideServerBundleSignaturePublicKey() " + server + " - " + version);
		if ("https://testurl".equals(server) && version == bundleSigningVersion) {
			return bundleSigningKeyPair.getPublic();
		}
		return NestMetric.super.overrideServerBundleSignaturePublicKey(server, version);
	}

	@Override
	public Integer getServerRequestResponseCode(String method, String requesturl) throws IOException {
		System.out.println("BasicServerNestMetric.getServerRequestResponseCode() " + requesturl);
		if (("https://testurl/bundle_signature_key/" + bundleSigningVersion).equals(requesturl)) {
			return HttpURLConnection.HTTP_OK;
		}
		return HttpURLConnection.HTTP_NOT_FOUND;
	}

	@Override
	public InputStream getServerRequestResponseStream(String method, String requesturl) throws IOException {
		System.out.println("BasicServerNestMetric.getServerRequestResponseStream() " + requesturl);
		if (("https://testurl/bundle_signature_key/" + bundleSigningVersion).equals(requesturl)) {
			return new UnsyncByteArrayInputStream(bundleSigningKeyPair.getPublic().getEncoded());
		}
		throw new IOException("No input: " + requesturl);
	}

	protected void applyBundleDownloadSignatureResponseHeaders(String method, String requesturl,
			Map<String, String> result) {
		try {
			String baseurl = "https://testurl/bundle/download/";
			if (requesturl.startsWith(baseurl)) {
				Signature sig = Signature.getInstance("SHA256withRSA");
				sig.initSign(getBundleSigningPrivateKey());
				try (InputStream in = getBundleInputStream(method, requesturl.substring(baseurl.length()))) {
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

	protected InputStream getBundleInputStream(String method, String bundleid) throws IOException {
		return getServerRequestResponseStream(method, "https://testurl/bundle/download/" + bundleid);
	}

	protected PrivateKey getBundleSigningPrivateKey() {
		return bundleSigningKeyPair.getPrivate();
	}

	@Override
	public Map<String, String> getServerRequestResponseHeaders(String method, String requesturl) {
		System.out.println("BasicServerNestMetric.getServerRequestResponseHeaders() " + method + " " + requesturl);
		Map<String, String> result = new TreeMap<>();
		applyBundleDownloadSignatureResponseHeaders(method, requesturl, result);
		return result;
	}
}
