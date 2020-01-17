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

/**
 * <pre>
 * Displays the version information of the saker.nest repository.
 * </pre>
 */
public class VersionCommand {
	public void call() throws Exception {
		String ls = System.lineSeparator();
		System.out.print("Saker.nest repository" + ls + "Version: " + saker.nest.meta.Versions.VERSION_STRING_FULL + ls
				+ "Saker.build system version: " + saker.build.meta.Versions.VERSION_STRING_FULL + ls);
	}
}
