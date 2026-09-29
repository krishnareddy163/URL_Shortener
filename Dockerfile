# Self-contained runtime: the only host prerequisite is Docker.
# The image builds everything at image-build time, so the Maven cache the build gates use is already warm and
# demos run without network access.
FROM maven:3.9-eclipse-temurin-25

# Run as an unprivileged user: inside this image the gates compile and test generated code directly. UID 1000 is
# the first user on most Linux hosts, so files written to a mounted runs/ directory belong to that user.
RUN userdel --remove ubuntu 2> /dev/null; useradd --create-home --uid 1000 --user-group app
WORKDIR /app
RUN chown app:app /app
# Tells the engine it already runs in a container, so gates without a nested sandbox are reported accurately.
ENV AGENTIC_SDLC_CONTAINER=1 HOME=/home/app MAVEN_CONFIG=/home/app/.m2
COPY --chown=app:app . .
USER app

# Verify the shortener baseline (downloads everything the scenario gates need), then package the orchestrator
# through the full verify lifecycle with one fast test, so the test and analysis plugins are cached too. With one test
# the coverage floor cannot hold, so it is checked without halting here; `test` and CI enforce it.
RUN mvn -q -B -f shortener-service/pom.xml verify \
 && mvn -q -B verify -Dtest=PayloadTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.haltOnFailure=false \
 && chmod +x docker/entrypoint.sh scripts/*.sh

VOLUME ["/app/runs"]
ENTRYPOINT ["docker/entrypoint.sh"]
CMD ["help"]
