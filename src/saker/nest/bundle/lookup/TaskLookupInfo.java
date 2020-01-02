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
