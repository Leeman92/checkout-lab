-include .env

.PHONY: *

USE_TRAEFIK ?= 0
HOST_UID := $(shell id -u)
HOST_GID := $(shell id -g)
export HOST_UID HOST_GID

DOCKER_COMPOSE := docker compose
DOCKER_FILES := -f compose.yml

ifeq ($(USE_TRAEFIK),1)
	DOCKER_FILES += -f compose.traefik.yml
endif

FULL_DOCKER_COMPOSE := $(DOCKER_COMPOSE) $(DOCKER_FILES)
FULL_DOCKER_COMPOSE_APP_BASH := $(FULL_DOCKER_COMPOSE) exec app bash -c

# Start the development stack detached
up:
	${FULL_DOCKER_COMPOSE} up --build -d

# Start the development stack attached and watch for source changes
up-attached:
	${FULL_DOCKER_COMPOSE} up --build --watch

build-prod:
	docker build --build-arg USER_ID=${HOST_UID} --build-arg GROUP_ID=${HOST_GID} --target production -t checkout-lab .

# stops the container
stop:
	${FULL_DOCKER_COMPOSE} stop

# stops and removes the container
down:
	${FULL_DOCKER_COMPOSE} down

# Check the container logs
logs:
	${FULL_DOCKER_COMPOSE} logs -f

# Run test suite
test:
	${FULL_DOCKER_COMPOSE_APP_BASH} './mvnw test'

# Run the full verify suite
verify:
	${FULL_DOCKER_COMPOSE_APP_BASH} './mvnw verify'

# Run spotless
spotless:
	${FULL_DOCKER_COMPOSE_APP_BASH} './mvnw spotless:apply'

# Run spottless AND verify
fix-and-verify fix:
	$(MAKE) spotless
	$(MAKE) verify

# Run migrations
migrate:
	${FULL_DOCKER_COMPOSE_APP_BASH} './mvnw flyway:migrate'