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

import java.util.ServiceLoader;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;

/**
 * The {@link SakerRepositoryFactory} implementation for the Nest repository.
 * <p>
 * This is the main entry point that is used to create a {@link SakerRepository} instance.
 */
@PublicApi
public final class NestRepositoryFactory implements SakerRepositoryFactory {
	/**
	 * The default identifier for the Nest repository.
	 */
	public static final String IDENTIFIER = "nest";

	/**
	 * Instantiates the repository factory.
	 * <p>
	 * As the repository factory is stateless, an instance is only required for the {@link ServiceLoader}
	 * implementation, and to call {@link #create(RepositoryEnvironment)}.
	 */
	public NestRepositoryFactory() {
	}

	@Override
	public SakerRepository create(RepositoryEnvironment environment) {
		return new NestRepositoryImpl(environment);
	}
}