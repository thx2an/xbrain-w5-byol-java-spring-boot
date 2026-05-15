# Makefile for SAM build
.PHONY: build-SpringBootFunction

build-SpringBootFunction:
	mvn clean package -DskipTests -q
	cd "$(ARTIFACTS_DIR)" && jar xf "$(CURDIR)/target/byol-java-spring-boot.jar"
