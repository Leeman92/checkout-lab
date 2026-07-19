# Shared Base Stage
FROM maven:3.9-eclipse-temurin-25 AS base

ARG USER_ID=1000
ARG GROUP_ID=1000

# The Ubuntu-based image already has an ubuntu:ubuntu account with UID/GID 1000.
# Reuse and rename matching IDs instead of failing when the host also uses 1000.
RUN set -eux; \
    existing_group="$(getent group "${GROUP_ID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_group}" ] && [ "${existing_group}" != "webrunner" ]; then \
        groupmod --new-name webrunner "${existing_group}"; \
    elif [ -z "${existing_group}" ]; then \
        groupadd --gid "${GROUP_ID}" webrunner; \
    fi; \
    existing_user="$(getent passwd "${USER_ID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_user}" ] && [ "${existing_user}" != "webrunner" ]; then \
        usermod --login webrunner --home /home/webrunner --move-home \
            --gid "${GROUP_ID}" --shell /bin/bash "${existing_user}"; \
    elif [ -z "${existing_user}" ]; then \
        useradd --create-home --uid "${USER_ID}" --gid "${GROUP_ID}" \
            --shell /bin/bash webrunner; \
    fi; \
    usermod --groups "" webrunner

WORKDIR /app
RUN mkdir -p /home/webrunner/.m2 /app/target && \
    chown -R webrunner:webrunner /home/webrunner /app

ENV HOME=/home/webrunner \
    MAVEN_CONFIG=/home/webrunner/.m2

COPY --chown=webrunner:webrunner pom.xml .
USER webrunner
RUN mvn dependency:go-offline

COPY --chown=webrunner:webrunner src ./src

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]

# Development Stage
FROM base AS development

COPY --chown=webrunner:webrunner --chmod=755 ./mvnw .
COPY --chown=webrunner:webrunner .mvn/ .mvn/

# Build stage
FROM base AS build

RUN mvn clean package -DskipTests


# Production stage
FROM eclipse-temurin:25-jre AS production

ARG USER_ID=1000
ARG GROUP_ID=1000

RUN set -eux; \
    existing_group="$(getent group "${GROUP_ID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_group}" ] && [ "${existing_group}" != "webrunner" ]; then \
        groupmod --new-name webrunner "${existing_group}"; \
    elif [ -z "${existing_group}" ]; then \
        groupadd --gid "${GROUP_ID}" webrunner; \
    fi; \
    existing_user="$(getent passwd "${USER_ID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_user}" ] && [ "${existing_user}" != "webrunner" ]; then \
        usermod --login webrunner --home /home/webrunner --move-home \
            --gid "${GROUP_ID}" --shell /bin/bash "${existing_user}"; \
    elif [ -z "${existing_user}" ]; then \
        useradd --create-home --uid "${USER_ID}" --gid "${GROUP_ID}" \
            --shell /bin/bash webrunner; \
    fi; \
    usermod --groups "" webrunner

WORKDIR /app

COPY --from=build --chown=webrunner:webrunner /app/target/*.jar app.jar

USER webrunner

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
