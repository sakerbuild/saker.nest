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
package saker.nest.action.main;

import saker.build.thirdparty.saker.util.ArrayIterator;
import saker.nest.NestRepositoryImpl;
import sipka.cmdline.api.Command;
import sipka.cmdline.api.SubCommand;

@Command(className = ".ExecuteAction", helpCommand = { "help", "?" })
@SubCommand(name = "main", type = MainCommand.class)
@SubCommand(name = "local", type = LocalCommand.class)
@SubCommand(name = "server", type = ServerCommand.class)
public abstract class ExecuteActionCommand {
	protected NestRepositoryImpl repository;

	public abstract void callCommand() throws Exception;

	public final void callCommand(NestRepositoryImpl repository) throws Exception {
		this.repository = repository;
		callCommand();
	}

	public static void invoke(NestRepositoryImpl repository, String... arguments) throws Exception {
		ExecuteAction mainaction = ExecuteAction.parse(new ArrayIterator<>(arguments));
		mainaction.callCommand(repository);
	}
}
