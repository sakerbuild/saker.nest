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
package saker.nest;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;

/**
 * Public interface for accessing Nest repository related features.
 * <p>
 * This interface is implemented by the loaded {@link SakerRepository} instance by the build system and other
 * operations. This interface doesn't extends {@link SakerRepository} in order to allow separate lifecycle management
 * independent from the build system. Instances of this interface may not be assignable to {@link SakerRepository}.
 * <p>
 * This interface is not to be implemented by clients.
 */
@PublicApi
public interface NestRepository {
	/**
	 * See {@link SakerRepository#executeAction(String...)}.
	 * <p>
	 * Executes an arbitrary action on this repository with the given parameters.
	 * <p>
	 * This method servers as a main method to the repository. Calling it will execute custom actions specified by the
	 * repository implementation.
	 * <p>
	 * Implementations are not recommended to call {@link System#exit(int)} after finishing the action, but they are
	 * allowed to do so. Make sure to document exit related behaviour for the callers.
	 * <p>
	 * The default implementation throws an {@link UnsupportedOperationException}.
	 * 
	 * @param arguments
	 *            The arguments for the action.
	 * @throws Exception
	 *             In case of any error occurring during execution of the action.
	 * @throws UnsupportedOperationException
	 *             If this repository action is not supported.
	 */
	public void executeAction(String... arguments) throws Exception, UnsupportedOperationException;

	/**
	 * Gets the {@link RepositoryEnvironment} that was used to initialize the repository.
	 * <p>
	 * See {@link SakerRepositoryFactory#create(RepositoryEnvironment)}.
	 * 
	 * @return The repository environment instance.
	 */
	public RepositoryEnvironment getRepositoryEnvironment();

}
