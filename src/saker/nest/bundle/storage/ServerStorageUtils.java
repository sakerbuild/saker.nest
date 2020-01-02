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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import saker.apiextract.api.PublicApi;
import saker.build.file.StreamWritable;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.thirdparty.org.json.JSONException;
import saker.nest.thirdparty.org.json.JSONObject;
import saker.nest.thirdparty.org.json.JSONTokener;

/**
 * Utility class containing functions operating with the server storage.
 * 
 * @see ServerBundleStorageView
 */
@PublicApi
public class ServerStorageUtils {

	/**
	 * Contains information abount a bundle upload operation.
	 * <p>
	 * The interface represents a data container.
	 * <p>
	 * This interface is not to be implemented by clients.
	 * 
	 * @see ServerStorageUtils#uploadBundle(String, BundleIdentifier, StreamWritable, byte[], byte[], Boolean)
	 */
	public interface UploadResult {
		/**
		 * Gets the MD5 hash of the uploaded bundle bytes.
		 * 
		 * @return The MD5 hash. The returned array may or may not be copied.
		 */
		public byte[] getMD5Hash();

		/**
		 * Gets the SHA256 hash of the uploaded bundle bytes.
		 * 
		 * @return The SHA256 hash. The returned array may or may not be copied.
		 */
		public byte[] getSHA256Hash();
	}

	/**
	 * Uploads a Nest bundle to the server specified at the given URL.
	 * <p>
	 * The method will upload the bundle specified by the bundle data argument to the server at the given URL. The
	 * upload uses the <a href="https://saker.build/saker.nest/doc/websiteguide/repository_api.html">protocol</a> for
	 * the Nest server.
	 * <p>
	 * The API key and API secret byte array arguments are used for authorization of the upload. These are the binary
	 * representation of the keys, not in any Base64 or other encoded formats. The keys for the upload can be retrieved
	 * from the associated web page of the server. (From the bundle management page at:
	 * <a href="https://nest.saker.build/user/packages" title="Manage packages |
	 * saker.nest">https://nest.saker.build/user/packages</a>)
	 * <p>
	 * The overwrite attribute specifies whether the upload should overwrite any previously uploaded bundle with the
	 * same bundle identifier. If <code>null</code>, it will be unspecified and the server defaults are used.
	 * 
	 * @param server
	 *            The server to upload the bundle to. (Shouldn't contain a trailing slash (<code>/</code>).)
	 * @param bundleid
	 *            The bundle identifier of the bundle that is being uploaded.
	 * @param bundledata
	 *            The handle to the bundle data.
	 * @param apikey
	 *            The API key to use for authorization.
	 * @param apisecret
	 *            The API secret to use for authorization.
	 * @param overwrite
	 *            Whether or not to specify the overwrite flag during upload. If <code>null</code>, no overwrite
	 *            argument is issued to the server.
	 * @return The result information about the upload.
	 * @throws NullPointerException
	 *             If any of the arguments except <code>overwrite</code> is <code>null</code>.
	 * @throws IOException
	 *             If the upload fails for some reason.
	 * @see ServerBundleStorageView#REPOSITORY_DEFAULT_SERVER_URL
	 */
	public static UploadResult uploadBundle(String server, BundleIdentifier bundleid, StreamWritable bundledata,
			byte[] apikey, byte[] apisecret, Boolean overwrite) throws NullPointerException, IOException {
		Objects.requireNonNull(bundleid, "bundle identifier");
		Objects.requireNonNull(bundledata, "bundle data");
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(apikey, "apikey");
		Objects.requireNonNull(apisecret, "apisecret");

		String apikeybase64 = BASE64_ENCODER.encodeToString(apikey);

		Mac sha256_HMAC;
		try {
			sha256_HMAC = Mac.getInstance(SERVER_REQUEST_CONTENTS_MAC_ALGORITHM);
			sha256_HMAC.init(new SecretKeySpec(apisecret, SERVER_REQUEST_CONTENTS_MAC_ALGORITHM));
		} catch (NoSuchAlgorithmException e) {
			//XXX reify exceptions
			throw new IOException(e);
		} catch (InvalidKeyException e) {
			throw new IOException(e);
		}
		String allocateurl = getBundleUploadAllocateURL(server, bundleid, overwrite);

		sha256_HMAC.update("POST".getBytes(StandardCharsets.UTF_8));
		sha256_HMAC.update(allocateurl.getBytes(StandardCharsets.UTF_8));
		sha256_HMAC.update(apikeybase64.getBytes(StandardCharsets.UTF_8));
		//no contents of the request.
		byte[] macbytes = sha256_HMAC.doFinal();

		String uploadurl = allocateUploadURL(bundleid, apikeybase64, allocateurl, macbytes);

		String boundary = UUID.randomUUID().toString();
		HttpURLConnection connection = (HttpURLConnection) new URL(uploadurl).openConnection();
		connection.setInstanceFollowRedirects(false);
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		connection.connect();

		MessageDigest md5digest;
		MessageDigest sha256digest;
		try {
			md5digest = MessageDigest.getInstance("MD5");
			sha256digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Message digest algorithm not found.", e);
		}

		try {
			try (OutputStream connout = connection.getOutputStream()) {
				connout.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
				connout.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + bundleid + ".jar\"\r\n")
						.getBytes(StandardCharsets.UTF_8));
				connout.write("Content-Type: application/java-archive\r\n".getBytes(StandardCharsets.UTF_8));
				connout.write("Content-Transfer-Encoding: binary\r\n\r\n".getBytes(StandardCharsets.UTF_8));

				bundledata.writeTo(new OutputStream() {
					@Override
					public void write(int b) throws IOException {
						connout.write(b);
						md5digest.update((byte) b);
						sha256digest.update((byte) b);
					}

					@Override
					public void write(byte[] b) throws IOException {
						connout.write(b);
						md5digest.update(b);
						sha256digest.update(b);
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						connout.write(b, off, len);
						md5digest.update(b, off, len);
						sha256digest.update(b, off, len);
					}
				});

				connout.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			}
			int rc = connection.getResponseCode();
			if (rc != HttpURLConnection.HTTP_OK) {
				throw new IOException("Failed to upload bundle: " + bundleid + " with response code: " + rc
						+ " with response: " + readErrorStreamOrEmpty(connection.getErrorStream()));
			}
		} finally {
			connection.disconnect();
		}
		byte[] md5bytes = md5digest.digest();
		byte[] sha256bytes = sha256digest.digest();
		return new SimpleUploadResult(md5bytes, sha256bytes);
	}

	private static String allocateUploadURL(BundleIdentifier bundleid, String apikeybase64, String allocateurl,
			byte[] macbytes) throws IOException, MalformedURLException, ProtocolException {
		//loop for retry
		retry_loop:
		while (true) {
			HttpURLConnection allocateconnection = (HttpURLConnection) new URL(allocateurl).openConnection();
			allocateconnection.setRequestMethod("POST");
			allocateconnection.setRequestProperty("NestAPIKey", apikeybase64);
			allocateconnection.setRequestProperty("NestRequestMAC", BASE64_ENCODER.encodeToString(macbytes));
			allocateconnection.setInstanceFollowRedirects(false);
			//somewhy it doesnt work if we dont do output
			//    not bothered enough to actually research why
			//    probably because POST method has some different defaults
			allocateconnection.setDoOutput(true);
			//set the content length so we dont have to open and close the stream
			allocateconnection.setFixedLengthStreamingMode(0);
			allocateconnection.connect();
			JSONObject allocatereponse;
			try {
				int allocateresponsecode = allocateconnection.getResponseCode();
				if (allocateresponsecode != HttpURLConnection.HTTP_OK) {
					String errstreamcontent = readErrorStreamOrEmpty(allocateconnection.getErrorStream());
					if (allocateresponsecode == HttpURLConnection.HTTP_UNAVAILABLE) {
						try {
							JSONObject errobj = new JSONObject(errstreamcontent);
							if ("try_again".equals(errobj.optString("error", null))) {
								int seconds = getRetrySeconds(allocateconnection, errobj);
								try {
									Thread.sleep(seconds * 1000);
									//continue the retry loop
									continue retry_loop;
								} catch (InterruptedException e) {
									//reinterrupt so the caller can handle
									Thread.currentThread().interrupt();
									throw new IOException("Upload interrupted.", e);
								}
							}
						} catch (JSONException e) {
						}
					}
					throw new IOException("Failed to upload bundle: " + bundleid + " with response code: "
							+ allocateresponsecode + " with response: " + errstreamcontent);
				}
				allocatereponse = new JSONObject(new JSONTokener(
						new InputStreamReader(allocateconnection.getInputStream(), StandardCharsets.UTF_8)));
			} finally {
				allocateconnection.disconnect();
			}
			String uploadurl = allocatereponse.getString("uploadurl");
			if (uploadurl == null) {
				throw new IOException(
						"Failed to retrieve upload URL. (format error in response: \"" + allocatereponse + "\")");
			}
			return uploadurl;
		}
	}

	private static int getRetrySeconds(HttpURLConnection allocateconnection, JSONObject errobj) {
		int seconds = errobj.optInt("seconds", -1);
		if (seconds > 0) {
			return seconds;
		}
		String retafterhdr = allocateconnection.getHeaderField("Retry-After");
		if (retafterhdr != null) {
			try {
				seconds = Integer.parseInt(retafterhdr);
				if (seconds > 0) {
					return seconds;
				}
			} catch (NumberFormatException e) {
			}
		}
		return 1;
	}

	private static final Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final String SERVER_REQUEST_CONTENTS_MAC_ALGORITHM = "HmacSHA256";

	private ServerStorageUtils() {
		throw new UnsupportedOperationException();
	}

	private static String getBundleUploadAllocateURL(String serverHost, BundleIdentifier bundleid, Boolean overwrite)
			throws UnsupportedEncodingException {
		StringBuilder sb = new StringBuilder();
		sb.append(serverHost);
		sb.append("/bundle/upload/allocate?bundleid=");
		sb.append(URLEncoder.encode(bundleid.toString(), "UTF-8"));
		if (overwrite != null) {
			sb.append("&overwrite=");
			sb.append(overwrite.toString());
		}
		return sb.toString();
	}

	private static String readErrorStreamOrEmpty(InputStream is) throws IOException {
		if (is == null) {
			return "";
		}
		return StreamUtils.readStreamStringFully(is, StandardCharsets.UTF_8);
	}

	private static final class SimpleUploadResult implements UploadResult {
		private byte[] md5;
		private byte[] sha256;

		public SimpleUploadResult(byte[] md5, byte[] sha256) {
			this.md5 = md5;
			this.sha256 = sha256;
		}

		@Override
		public byte[] getMD5Hash() {
			return md5;
		}

		@Override
		public byte[] getSHA256Hash() {
			return sha256;
		}
	}
}
