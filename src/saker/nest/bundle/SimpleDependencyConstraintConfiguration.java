package saker.nest.bundle;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class SimpleDependencyConstraintConfiguration implements DependencyConstraintConfiguration, Externalizable {
	private static final long serialVersionUID = 1L;

	private Integer jreMajorVersion;
	private String repositoryVersion;
	private String buildSystemVersion;
	private String nativeArchitecture;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleDependencyConstraintConfiguration() {
	}

	public SimpleDependencyConstraintConfiguration(Integer jreMajorVersion, String repositoryVersion,
			String buildSystemVersion, String nativeArchitecture) {
		this.jreMajorVersion = jreMajorVersion;
		this.repositoryVersion = repositoryVersion;
		this.buildSystemVersion = buildSystemVersion;
		this.nativeArchitecture = nativeArchitecture;
	}

	@Override
	public Integer getJreMajorVersion() {
		return jreMajorVersion;
	}

	@Override
	public String getRepositoryVersion() {
		return repositoryVersion;
	}

	@Override
	public String getBuildSystemVersion() {
		return buildSystemVersion;
	}

	@Override
	public String getNativeArchitecture() {
		return nativeArchitecture;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(jreMajorVersion);
		out.writeObject(repositoryVersion);
		out.writeObject(buildSystemVersion);
		out.writeObject(nativeArchitecture);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		jreMajorVersion = (Integer) in.readObject();
		repositoryVersion = (String) in.readObject();
		buildSystemVersion = (String) in.readObject();
		nativeArchitecture = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildSystemVersion == null) ? 0 : buildSystemVersion.hashCode());
		result = prime * result + ((jreMajorVersion == null) ? 0 : jreMajorVersion.hashCode());
		result = prime * result + ((nativeArchitecture == null) ? 0 : nativeArchitecture.hashCode());
		result = prime * result + ((repositoryVersion == null) ? 0 : repositoryVersion.hashCode());
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
		SimpleDependencyConstraintConfiguration other = (SimpleDependencyConstraintConfiguration) obj;
		if (buildSystemVersion == null) {
			if (other.buildSystemVersion != null)
				return false;
		} else if (!buildSystemVersion.equals(other.buildSystemVersion))
			return false;
		if (jreMajorVersion == null) {
			if (other.jreMajorVersion != null)
				return false;
		} else if (!jreMajorVersion.equals(other.jreMajorVersion))
			return false;
		if (nativeArchitecture == null) {
			if (other.nativeArchitecture != null)
				return false;
		} else if (!nativeArchitecture.equals(other.nativeArchitecture))
			return false;
		if (repositoryVersion == null) {
			if (other.repositoryVersion != null)
				return false;
		} else if (!repositoryVersion.equals(other.repositoryVersion))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[jreMajorVersion=" + jreMajorVersion + ", repositoryVersion="
				+ repositoryVersion + ", buildSystemVersion=" + buildSystemVersion + ", "
				+ (nativeArchitecture != null ? "nativeArchitecture=" + nativeArchitecture : "") + "]";
	}

}
