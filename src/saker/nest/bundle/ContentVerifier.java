package saker.nest.bundle;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import saker.nest.exc.NestSignatureVerificationException;

public interface ContentVerifier {
	public void verify(SeekableByteChannel channel) throws IOException, NestSignatureVerificationException;
}
