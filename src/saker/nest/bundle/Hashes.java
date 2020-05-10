package saker.nest.bundle;

public class Hashes {
	public final String sha256;
	public final String sha1;
	public final String md5;

	public Hashes(String sha256, String sha1, String md5) {
		this.sha256 = sha256;
		this.sha1 = sha1;
		this.md5 = md5;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((md5 == null) ? 0 : md5.hashCode());
		result = prime * result + ((sha1 == null) ? 0 : sha1.hashCode());
		result = prime * result + ((sha256 == null) ? 0 : sha256.hashCode());
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
		Hashes other = (Hashes) obj;
		if (md5 == null) {
			if (other.md5 != null)
				return false;
		} else if (!md5.equals(other.md5))
			return false;
		if (sha1 == null) {
			if (other.sha1 != null)
				return false;
		} else if (!sha1.equals(other.sha1))
			return false;
		if (sha256 == null) {
			if (other.sha256 != null)
				return false;
		} else if (!sha256.equals(other.sha256))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Hashes[" + (sha256 != null ? "sha256=" + sha256 + ", " : "")
				+ (sha1 != null ? "sha1=" + sha1 + ", " : "") + (md5 != null ? "md5=" + md5 : "") + "]";
	}

}