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
package saker.nest.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Base class for implementing {@linkplain TaskFactory task factories} which are loaded by the Nest repository.
 * <p>
 * A front-end task factory doesn't have any fields or other data that specify their execution, but take parameters via
 * the created {@link ParameterizableTask} instance.
 * <p>
 * The class <code>final</code> implements {@link #hashCode()}, {@link #equals(Object)} and serialization methods.
 * <p>
 * If used with the Nest repository, subclasses are instantiated using the public no-arg constructor.
 * 
 * @param <R>
 *            The task return type.
 */
@PublicApi
public abstract class FrontendTaskFactory<R> implements TaskFactory<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance. Also used by {@link Externalizable}.
	 */
	public FrontendTaskFactory() {
	}

	@Override
	public abstract ParameterizableTask<? extends R> createTask(ExecutionContext executioncontext);

	@Override
	public final void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public final int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}
}
