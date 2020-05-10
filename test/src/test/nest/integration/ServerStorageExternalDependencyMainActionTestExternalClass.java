package test.nest.integration;

//this class has its own file so file editing don't interfere with the hashes
//(modifying line numbers change the bytecode -> change the external dependency hash)
public class ServerStorageExternalDependencyMainActionTestExternalClass {
	//just a random uuid
	static final String PROPERTY_NAME = "8f0e5410-f540-48fb-88d4-cd07b40103a5";

	public static void main(String[] args) {
		System.setProperty(PROPERTY_NAME, args[0]);
	}
}