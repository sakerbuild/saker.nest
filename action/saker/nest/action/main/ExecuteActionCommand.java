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
