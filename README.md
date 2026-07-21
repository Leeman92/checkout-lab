# Checkout Lab

Checkout Lab is a Java 25 and Spring Boot 4.1 development project for experimenting with a
PostgreSQL-backed checkout API. The Docker development stack contains the application, PostgreSQL,
and pgAdmin. You do not need Java, Maven, PostgreSQL, or pgAdmin installed on your computer when you
use the Docker workflow described here.

If this is your first time using the project, start **without Traefik**. Traefik is optional and is
only useful if you already have, or intentionally want to operate, a local Traefik reverse proxy.

## Current functionality

The application provides a product catalogue and an order/checkout API:

**Products**

- product list, lookup, and creation endpoints
- unique product SKUs (normalized to uppercase via a `Sku` value object)
- a development-only endpoint that inserts 12 sample products
- calculated formatted price and available stock on products loaded from the database

**Orders**

- order creation with one or more line items, each reserving stock
- prices captured at order time, so a later product price change never alters an existing order
- an order total computed from the line items, stored as exact integer cents (EUR) via a `Money`
  value object
- stock reservation that can never oversell, even under concurrent orders, enforced by an atomic
  conditional database update plus check constraints
- idempotent creation keyed on a client `Idempotency-Key` header: replaying the same request returns
  the existing order, while reusing the key for a different request is rejected as a conflict
- order lookup by id
- batched validation: a single response reports every invalid line (unknown SKU, inactive product,
  duplicate SKU) or every stock shortfall at once, rather than one error at a time

**Platform**

- PostgreSQL persistence through Spring Data JPA
- versioned Flyway migrations applied automatically on application startup; Hibernate validates the
  schema against the entity mappings on boot
- request IDs in the `X-Request-ID` response header, response bodies, and log context
- consistent metadata envelopes for successful JSON responses
- `application/problem+json` (RFC 7807) responses for all client and server errors, each with a
  stable machine-readable `type`
- console and size/time-rotated file logging

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
HOST_UID=1000
HOST_GID=1000
```

Set `HOST_UID` and `HOST_GID` to the values printed by `id -u` and `id -g`. The Make targets set
them automatically; these `.env` values are used when you invoke `docker compose` directly.

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

> The project does not define a page or API at `/`. A JSON error response at the base URL therefore
> still confirms that Spring Boot is reachable. Use `/products` to access the API.

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

The application connects to PostgreSQL as `postgres:5432` through Docker's internal network and
uses the database configured by `POSTGRES_DB_NAME`. PostgreSQL is not published on a host port;
pgAdmin reaches it through the same internal network.

## Product API

The examples below use direct-port mode. Replace `http://localhost:8080` with your configured URL
when using another port or Traefik.

| Method | Path | Purpose | Success status |
| --- | --- | --- | --- |
| `GET` | `/products` | List all products | `200 OK` |
| `GET` | `/products/{sku}` | Find one product; the path SKU is normalized to uppercase | `200 OK` |
| `POST` | `/products` | Create one product from JSON | `200 OK` |
| `POST` | `/products/testdata` | Insert the 12 development sample products | `200 OK` |

Both the path with and without a trailing slash are accepted. The `testdata` endpoint is only a
development convenience and should be removed or disabled before a production deployment. Because
the sample SKUs are fixed, calling it more than once returns `409 Conflict` unless the previous
sample rows were removed.

Create a product:

```shell
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{
    "sku": "TSHIRT-BLK-M",
    "name": "Basic T-Shirt Black M",
    "netPriceInCents": 1999,
    "active": true,
    "totalStock": 25,
    "reservedStock": 3
  }' \
  http://localhost:8080/products
```

Product JSON contains the persisted fields `id`, `sku`, `name`, `netPriceInCents`, `active`,
`totalStock`, and `reservedStock`. Products also expose the derived fields `netFormattedPrice` (a
string such as `"19.99"`) and `availableStock` (`totalStock - reservedStock`). Prices are persisted
as integer cents to avoid floating-point storage errors, and formatting is an exact base-100 split,
so no rounding ever occurs.

Successful JSON responses are wrapped with metadata:

```json
{
  "timestamp": "2026-07-19T10:15:30.123Z",
  "requestId": "59edc107-a937-40e0-b387-3d342053a238",
  "data": {
    "id": 1,
    "sku": "TSHIRT-BLK-M",
    "name": "Basic T-Shirt Black M",
    "netPriceInCents": 1999,
    "active": true,
    "totalStock": 25,
    "reservedStock": 3,
    "netFormattedPrice": "19.99",
    "availableStock": 22
  }
}
```

You may send a UUID in the `X-Request-ID` request header. The application returns a canonical copy
in the response header and JSON body. A missing or invalid value is replaced with a generated UUID.

Errors use Spring's Problem Details representation. A missing product returns `404 Not Found` with
the requested SKU, while a unique-SKU conflict returns `409 Conflict`. Each problem contains
`type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `requestId`; unexpected exceptions
return a generic `500 Internal Server Error` detail without leaking internal implementation data.

## Order API

| Method | Path | Purpose | Success status |
| --- | --- | --- | --- |
| `POST` | `/orders` | Create an order and reserve stock | `201 Created` (or `200 OK` on replay) |
| `GET` | `/orders/{id}` | Fetch one order with its line items | `200 OK` |

Creating an order requires an `Idempotency-Key` request header (any stable, unique string such as a
UUID). The body lists one or more line items; each item needs a `sku` and a positive integer
`quantity`.

```shell
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: 715bcf40-0502-4cbe-ba2b-6eb531478c1a' \
  --data '{ "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }' \
  http://localhost:8080/orders
```

A successful response carries the order under the usual metadata envelope:

```json
{
  "timestamp": "2026-07-22T10:15:30.123Z",
  "requestId": "59edc107-a937-40e0-b387-3d342053a238",
  "data": {
    "id": 1,
    "status": "RESERVED",
    "currency": "EUR",
    "totalNetInCents": 3998,
    "createdAt": "2026-07-22T10:15:30.100Z",
    "items": [
      { "sku": "TSHIRT-BLK-M", "quantity": 2, "unitNetPriceInCents": 1999, "lineNetInCents": 3998 }
    ]
  }
}
```

**Idempotency.** Sending the same request again with the same `Idempotency-Key` returns the existing
order with `200 OK` and does not reserve stock a second time. Reusing the key with a different body
returns `409 Conflict` (`urn:problem:idempotency-conflict`) and changes nothing.

**Error responses.** All order errors use `application/problem+json`. The two batched types list
every offending line in an `errors` array, so a client can fix all problems in one pass:

| Situation | Status | `type` |
| --- | --- | --- |
| Malformed body (empty items, quantity ≤ 0, blank SKU) | `400 Bad Request` | `urn:problem:validation-error` |
| Unknown SKU, inactive product, or duplicate SKU (batched) | `422 Unprocessable Entity` | `urn:problem:order-validation` |
| Not enough stock for one or more lines (batched) | `409 Conflict` | `urn:problem:insufficient-stock` |
| Idempotency key reused with different content | `409 Conflict` | `urn:problem:idempotency-conflict` |
| Unknown order id | `404 Not Found` | `urn:problem:order-not-found` |

For example, an order that references an unknown SKU, an inactive product, and a repeated SKU is
rejected in a single `422` response:

```json
{
  "type": "urn:problem:order-validation",
  "title": "Order validation failed",
  "status": 422,
  "errors": [
    { "sku": "NOPE", "reason": "UNKNOWN_SKU" },
    { "sku": "BELT-BRN-100", "reason": "INACTIVE_PRODUCT" },
    { "sku": "TSHIRT-BLK-M", "reason": "DUPLICATE_SKU" }
  ],
  "requestId": "…",
  "timestamp": "…"
}
```

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
| `make migrate` | Runs Flyway migrations via the Maven plugin (the app also migrates on startup) |
| `make build-prod` | Builds the production Docker image named `checkout-lab` |

The project directory is mounted into the development container at `/app`. Source changes made on
your computer are therefore visible inside the container, and formatting performed in the container
updates your local files. Maven's generated `target` directory is kept in the Docker volume
`app_target` instead of your working tree.

`make up-attached` enables Compose Watch:

- A change below `src` restarts the application container.
- A change to `pom.xml` or `Dockerfile` rebuilds the development image.

### Tests and verification

`make test` runs focused unit and web-layer tests: the `Money` and `Sku` value objects, the product
and order services (including stock reservation, price snapshotting, idempotent replay/conflict, and
batched validation), the product and order controllers, the request-ID filter, the API response
envelope, and Problem Details errors. These tests use hand-written in-memory fakes, a fixed `Clock`
for deterministic timestamps, and `MockMvc`, so the test cases themselves do not modify the
development database.

`make verify` additionally checks Java formatting with Spotless, runs SpotBugs, and generates the
JaCoCo coverage report under `target/site/jacoco` inside the app target volume. If Java 25 is
installed locally, the Maven wrapper can run the same lifecycle without the app container:

```shell
./mvnw test
./mvnw verify
```

## Environment variables

The Makefile reads `.env` and uses `USE_TRAEFIK` to decide whether to include
`compose.traefik.yml`. Docker Compose reads the same file for service configuration.

| Variable | Default | Meaning |
| --- | --- | --- |
| `APP_NAME` | `checkout-lab` | Spring application name and file-log basename |
| `APP_PORT` | `8080` | Host port for the app when Traefik is disabled |
| `PGADMIN_PORT` | `8081` | Host port for pgAdmin when Traefik is disabled |
| `APP_URL` | `springboot.localhost` | Hostname matched by the Traefik app router |
| `PG_ADMIN_URL` | `pgadmin.localhost` | Hostname matched by the Traefik pgAdmin router |
| `TRAEFIK_NETWORK` | `traefik` | Existing external Docker network shared with Traefik |
| `USE_TRAEFIK` | `0` | Set to `1` to apply `compose.traefik.yml`; otherwise use `0` |
| `HOST_UID` | `1000` | UID used by the non-root application user inside the container |
| `HOST_GID` | `1000` | GID used by the non-root application user inside the container |
| `POSTGRES_USER` | `postgres` | PostgreSQL user used by the database and application |
| `POSTGRES_PASSWORD` | `changeme` | PostgreSQL password used by the database and application |
| `POSTGRES_DB_NAME` | `checkout_lab` | Application database created by PostgreSQL |
| `PGADMIN_DEFAULT_EMAIL` | `pgadmin4@pgadmin.org` | Local pgAdmin login email |
| `PGADMIN_DEFAULT_PASSWORD` | `admin` | Local pgAdmin login password |
| `LOG_LEVEL` | `WARN` | Log level for Spring's `web` logger in `.env.example` |
| `LOG_PATH` | `var/log` | Directory for the active and rotated application log files |
| `CONSOLE_LOG_PATTERN` | See `.env.example` | Logback console pattern; includes the request ID from MDC |
| `FILE_LOG_PATTERN` | See `.env.example` | Logback file pattern; includes the request ID from MDC |

The values in `.env.example` are development defaults. A minimal database and pgAdmin configuration
looks like this:

```dotenv
POSTGRES_USER=postgres
POSTGRES_PASSWORD=changeme
POSTGRES_DB_NAME=checkout_lab
PGADMIN_DEFAULT_EMAIL=pgadmin4@pgadmin.org
PGADMIN_DEFAULT_PASSWORD=admin
```

The default credentials are for local development only. Change them before sharing or exposing the
environment.

### Logging

Logback writes to the console and to `${LOG_PATH}/${APP_NAME}.log`. Files rotate daily and whenever
they reach 10 MB; up to 30 days and 1 GB of compressed history are retained. With the default Docker
bind mount, `var/log/checkout-lab.log` is visible in the project directory and ignored by Git.

The filter stores the current request UUID under the MDC key `requestId`. Keep `%X{requestId}` in
custom log patterns if request correlation should remain visible. `LOG_LEVEL` controls Spring's
`web` logger; the root logger remains at `INFO` in `logback-spring.xml`.

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
