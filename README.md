# saker.nest

![Build status](https://img.shields.io/azure-devops/build/sakerbuild/8cec740c-3729-415e-92e5-b7af8142fb75/7/master)

The default package and build task repository for the [saker.build system](https://saker.build). The repository is loaded automatically by the build system and allows accessing the build tasks provided by it.

The saker.nest repository organizes packages into bundles which are stored in the configured location of theirs. The project is backed by the community driven plugin repository available at [https://nest.saker.build](https://nest.saker.build).

See the [documentation](https://saker.build/saker.nest/doc/) for more information.

## Build instructions

The project uses the [saker.build system](https://saker.build) for building. Use the following command to build the project:

```
java -jar path/to/saker.build.jar -bd build compile saker.build
```

## License

The source code for the project is licensed under *GNU General Public License v3.0 only*.

Short identifier: [`GPL-3.0-only`](https://spdx.org/licenses/GPL-3.0-only.html).

Official releases of the project (and parts of it) may be licensed under different terms. See the particular releases for more information.
