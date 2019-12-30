package saker.nest.bundle.lookup;

import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.BundleStorageView;

public class TaskLookupInfo {
	protected final NestRepositoryBundle bundle;
	protected final String taskClassName;
	protected final AbstractBundleLookup lookupConfiguration;
	protected final BundleStorageView storageView;

	public TaskLookupInfo(NestRepositoryBundle bundle, String taskClassName, AbstractBundleLookup lookupConfiguration,
			BundleStorageView storageView) {
		this.bundle = bundle;
		this.taskClassName = taskClassName;
		this.lookupConfiguration = lookupConfiguration;
		this.storageView = storageView;
	}

	public NestRepositoryBundle getBundle() {
		return bundle;
	}

	public String getTaskClassName() {
		return taskClassName;
	}

	public AbstractBundleLookup getLookupConfiguration() {
		return lookupConfiguration;
	}

	public BundleStorageView getStorageView() {
		return storageView;
	}
}
