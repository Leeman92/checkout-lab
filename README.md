# Checkout Lab

Checkout Lab is a Spring Boot development project that runs in Docker. The development stack
contains the application, PostgreSQL, and pgAdmin. You do not need Java, Maven, PostgreSQL, or
pgAdmin installed on your computer when you use the Docker workflow described here.

If this is your first time using the project, start **without Traefik**. Traefik is optional and is
only useful if you already have, or intentionally want to operate, a local Traefik reverse proxy.

## Quick start for new developers

### 1. Install the required tools

You need:

- Docker with Docker Compose 2.32.2 or newer
- GNU Make

Confirm that the tools are available and that Docker is running:

```shell
docker --version
docker compose version
make --version
docker info
```

Docker Desktop includes Docker Compose. On Linux, make sure the Docker daemon is running and that
your user is allowed to use Docker.

### 2. Create your local configuration

From the project directory, copy the example environment file:

```shell
cp .env.example .env
```

Do not run this command again if you already have a configured `.env`, because it would overwrite
your local settings. Keep `USE_TRAEFIK=0` for the simplest setup:

```dotenv
APP_PORT=8080
PGADMIN_PORT=8081
APP_URL=springboot.localhost
PG_ADMIN_URL=pgadmin.localhost
TRAEFIK_NETWORK=traefik
USE_TRAEFIK=0
```

The `.env` file is local configuration. Do not commit passwords or other secrets stored in it.

### 3. Start the development stack

```shell
make up
```

The first start takes longer because Docker must download images and Maven dependencies. The
containers start in the background, so the command returns your terminal when they are ready. Check
their output at any time with:

```shell
make logs
```

Press `Ctrl+C` to leave the log view; the detached containers continue running.

With `USE_TRAEFIK=0`, open:

- Application: <http://localhost:8080>
- pgAdmin: <http://localhost:8081>

If you changed `APP_PORT` or `PGADMIN_PORT`, use the corresponding port from `.env` instead.

> The project does not currently define a page or API at `/`. A JSON error response at the base URL
> therefore still confirms that Spring Boot is reachable. It does not necessarily mean Docker
> failed to start.

When you finish working, stop the detached containers with:

```shell
make stop
```

This keeps the stopped containers and project network. Run `make up` when you want to start them
again. To stop and remove the containers and project network instead, use:

```shell
make down
```

Detached mode does not enable Compose Watch because Docker Compose does not allow `--watch` and `-d`
to be combined. If you want automatic restart and rebuild actions, use `make up-attached` instead.
It displays logs and stays attached to the current terminal; press `Ctrl+C` to stop it.

## What gets started?

| Service | Purpose | Address without Traefik |
| --- | --- | --- |
| `app` | Spring Boot application | `http://localhost:${APP_PORT}` (port `8080` by default) |
| `postgres` | PostgreSQL database | Only reachable by other containers as `postgres:5432` |
| `pgadmin` | Browser-based PostgreSQL administration | `http://localhost:${PGADMIN_PORT}` (port `8081` by default) |

PostgreSQL and pgAdmin run even though the application does not currently have a datasource
configured. PostgreSQL itself is not published on a host port; pgAdmin reaches it through Docker's
internal network.

## Everyday development commands

Run `make up` before commands that execute inside the `app` container.

| Command | What it does |
| --- | --- |
| `make up` | Builds and starts the development stack in the background without Compose Watch |
| `make up-attached` | Builds and starts in the foreground with logs and file watching |
| `make stop` | Stops the containers without removing them |
| `make down` | Stops and removes the stack's containers and network |
| `make logs` | Follows logs from all services |
| `make test` | Runs the Maven test suite in the app container |
| `make verify` | Runs tests, formatting checks, SpotBugs, and the other Maven verification steps |
| `make spotless` | Formats Java source files; this changes files in your working tree |
| `make fix` | Runs `spotless` followed by `verify` |
| `make build-prod` | Builds the production Docker image named `checkout-lab` |

The project directory is mounted into the development container at `/app`. Source changes made on
your computer are therefore visible inside the container, and formatting performed in the container
updates your local files. Maven's generated `target` directory is kept in the Docker volume
`app_target` instead of your working tree.

`make up-attached` enables Compose Watch:

- A change below `src` restarts the application container.
- A change to `pom.xml` or `Dockerfile` rebuilds the development image.

## Environment variables

The Makefile reads `.env` and uses `USE_TRAEFIK` to decide whether to include
`compose.traefik.yml`. Docker Compose reads the same file for service configuration.

| Variable | Default | Meaning |
| --- | --- | --- |
| `APP_PORT` | `8080` | Host port for the app when Traefik is disabled |
| `PGADMIN_PORT` | `8081` | Host port for pgAdmin when Traefik is disabled |
| `APP_URL` | `springboot.localhost` | Hostname matched by the Traefik app router |
| `PG_ADMIN_URL` | `pgadmin.localhost` | Hostname matched by the Traefik pgAdmin router |
| `TRAEFIK_NETWORK` | `traefik` | Existing external Docker network shared with Traefik |
| `USE_TRAEFIK` | `0` | Set to `1` to apply `compose.traefik.yml`; otherwise use `0` |

The Compose file also accepts these optional variables. Add them to `.env` if you need values other
than the development defaults:

```dotenv
POSTGRES_USER=postgres
POSTGRES_PASSWORD=changeme
PGADMIN_DEFAULT_EMAIL=pgadmin4@pgadmin.org
PGADMIN_DEFAULT_PASSWORD=admin
```

The default credentials are for local development only. Change them before sharing or exposing the
environment.

## Using Traefik

### What is Traefik, and do I need it?

Traefik is a reverse proxy. It receives requests on a shared HTTP or HTTPS port, looks at the
requested hostname, and forwards each request to the correct Docker service. It lets this project
use names such as `springboot.localhost` and `pgadmin.localhost` instead of separate host ports.

You do **not** need Traefik to develop the Spring Boot application. This repository does not start or
configure Traefik itself; `compose.traefik.yml` only tells an already-running Traefik instance how to
route to this project's containers.

### What must already exist?

Before setting `USE_TRAEFIK=1`, all of the following must be true:

1. A Traefik container is running separately.
2. Traefik's Docker provider is enabled and can read Docker labels.
3. Traefik has an entrypoint named exactly `websecure`, normally listening on host port `443`.
4. TLS is enabled for that entrypoint. For trusted local HTTPS, Traefik must also have a suitable
   certificate or certificate resolver; otherwise your browser may show a certificate warning.
5. Traefik and this stack share the external Docker network named by `TRAEFIK_NETWORK`.
6. Host ports required by Traefik, normally `80` and `443`, are not already occupied.

Traefik commonly needs read access to the Docker socket to discover containers. Access to that
socket is highly privileged, so mount it read-only, do not expose the Traefik dashboard publicly,
and use this setup only in an environment you trust.

### Enable the project-side Traefik configuration

Create the shared network once, if it does not already exist:

```shell
docker network inspect traefik
docker network create traefik
```

The first command succeeds when the network already exists. Only run the second command when the
first reports that the network was not found. If you choose another network name, set the same name
in `TRAEFIK_NETWORK` and connect the Traefik container to it.

Check which containers are connected to the default network:

```shell
docker network inspect traefik
```

Then edit `.env`:

```dotenv
APP_URL=springboot.localhost
PG_ADMIN_URL=pgadmin.localhost
TRAEFIK_NETWORK=traefik
USE_TRAEFIK=1
```

`APP_URL` and `PG_ADMIN_URL` must contain hostnames only—do not include `https://`, a port, a path,
or a trailing slash.

Start the stack normally in the background:

```shell
make up
```

With the default values, open:

- Application: <https://springboot.localhost>
- pgAdmin: <https://pgadmin.localhost>

The `.localhost` domain normally resolves to the local computer without a hosts-file entry. If you
use custom names instead, configure local DNS or add those names to your hosts file so they resolve
to `127.0.0.1`.

When Traefik is enabled, the direct `${APP_PORT}` and `${PGADMIN_PORT}` host mappings are removed.
That means `http://localhost:8080` and `http://localhost:8081` are no longer the expected routes;
use the HTTPS hostnames through Traefik. Resetting both mappings also avoids exposing the same
service through Traefik and a direct host port at the same time.

### Switching between Traefik and direct-port mode

Stop and remove the current stack before changing modes:

```shell
make down
```

Change `USE_TRAEFIK` in `.env`, then run `make up` again. Keeping the setting in `.env` ensures that
`up`, `logs`, `test`, and `down` all use the same Compose-file combination.

### Important Traefik considerations

- The router and service names in `compose.traefik.yml` are `springboot` and `pgadmin`. Running
  multiple copies of this project behind the same Traefik instance can cause name collisions unless
  those labels and hostnames are made unique.
- Both app and pgAdmin must remain connected to the external Traefik network. A router can exist but
  return `502 Bad Gateway` if Traefik cannot reach the target container on the selected network.
- The labels expect an entrypoint named `websecure`. A differently named Traefik entrypoint will not
  work until the labels are updated.
- `traefik.http.routers.*.tls=true` enables TLS routing but does not by itself create a locally trusted
  certificate.
- The external Traefik network is shared infrastructure. `make down` does not and should not remove
  it.
- Do not expose pgAdmin with its default credentials on a public or shared network.

## Connecting pgAdmin to PostgreSQL

Open <http://localhost:8081> without Traefik or <https://pgadmin.localhost> with Traefik, adjusting
the configured port or hostname if necessary. Log in with `PGADMIN_DEFAULT_EMAIL` and
`PGADMIN_DEFAULT_PASSWORD`. When registering a server in pgAdmin, use:

| Field | Value |
| --- | --- |
| Host name/address | `postgres` |
| Port | `5432` |
| Maintenance database | `postgres` |
| Username | Value of `POSTGRES_USER` (`postgres` by default) |
| Password | Value of `POSTGRES_PASSWORD` (`changeme` by default) |

Use `postgres`, not `localhost`, as the host. Inside the pgAdmin container, `localhost` means the
pgAdmin container itself. Docker's internal DNS resolves the service name `postgres` to the database
container.

## Troubleshooting and common questions

### `make up` says it cannot connect to the Docker daemon

Start Docker Desktop or the Docker daemon. On Linux, also check whether your user has permission to
access Docker by running `docker info`.

### Port `8080` or `8081` is already in use

Choose free host ports in `.env`, for example `APP_PORT=9080` or `PGADMIN_PORT=9081`, then restart
the stack. These variables have no effect on routing when Traefik is enabled because the Traefik
override removes both direct port mappings.

### Compose says the external network `traefik` was not found

The Traefik override refers to an external network, so Compose will not create it. Create it with
`docker network create traefik`, or make `TRAEFIK_NETWORK` match the external network used by your
Traefik container.

### Traefik returns `404 Not Found`

Confirm that `USE_TRAEFIK=1`, that the requested hostname exactly matches `APP_URL` or
`PG_ADMIN_URL`, and that Traefik's Docker provider sees the container labels. Also confirm that the
router uses an existing entrypoint named `websecure`.

### Traefik returns `502 Bad Gateway`

Confirm that Traefik and the target container are attached to the network named by
`TRAEFIK_NETWORK`. Inspect it with `docker network inspect <network-name>`. The app listens on
container port `8080`; pgAdmin listens on container port `80`.

### The browser reports an HTTPS certificate error

Routing and certificate trust are separate concerns. Configure Traefik with a certificate trusted by
your machine, or accept the warning only if you understand and trust the local development setup.
Public certificate authorities generally do not issue certificates for `.localhost` names.

### Source changes are not picked up

Compose Watch is only enabled by `make up-attached`; Docker Compose does not allow `--watch` and `-d`
to be combined. Stop a detached stack with `make stop`, then run `make up-attached`. Check the output
or `make logs` for a restart or rebuild. Changes to `src` restart the app, while changes to `pom.xml`
or `Dockerfile` trigger a slower image rebuild.

### `make test`, `make verify`, or `make spotless` says the app service is not running

These commands execute inside the development container. Start it with `make up` first. If you used
`make up-attached`, run the command from another terminal.

### What is the difference between `stop` and `down`?

`make stop` stops the containers but keeps them and the project network. It is convenient when you
expect to resume development with `make up`. `make down` stops and removes the containers and project
network, but still preserves the PostgreSQL and pgAdmin volumes. Neither command removes the shared
external Traefik network.

### Will `make down` delete my database?

No. PostgreSQL and pgAdmin data are stored in named Docker volumes, and `make down` leaves those
volumes intact. To intentionally reset all project data, stop the stack and remove its volumes:

```shell
docker compose -f compose.yml down --volumes
```

This permanently deletes the local PostgreSQL and pgAdmin data. Do not run it if you need that data.

### I changed a database or pgAdmin password in `.env`, but the old login still applies

The PostgreSQL and pgAdmin images use these variables when they initialize an empty data volume.
Changing the variables later does not rewrite users stored in an existing database or pgAdmin
volume. Either change the password through PostgreSQL or pgAdmin, or—if the saved data is disposable—
remove the volumes as described above and let the services initialize again.

### Do I need Java or Maven installed locally?

Not for the documented Docker workflow. The development image supplies Java and Maven, and the
Makefile runs Maven commands inside the app container.

### Why can the containers use names such as `postgres`?

Compose connects the services to an internal network named `checkout_lab`. Docker provides DNS on
that network, so containers address one another by service name. Those names are not normally
available from your host computer.

### Where should I look when something fails?

Start with:

```shell
make logs
docker compose -f compose.yml ps
```

When Traefik is enabled, also inspect the Traefik logs and its shared Docker network. The earliest
error in the logs is usually more useful than later follow-on errors.

## Production image

Build the production image with:

```shell
make build-prod
```

This is equivalent to:

```shell
docker build --target production -t checkout-lab .
```

The resulting image contains the packaged Spring Boot application only. It does not include or start
PostgreSQL, pgAdmin, or Traefik, and the development Compose setup is not a production deployment.
