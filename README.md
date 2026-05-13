# Java + Spring Boot — BYOL starter (NOT YET SERVERLESS)

This is a plain Spring Boot 3 app. It runs locally as a normal Tomcat-backed
HTTP server. **It does not run on Lambda yet.** Your group's job is to make
it run on Lambda with the **minimum** code/config changes.

```
java-spring-boot/
├── src/main/java/dev/byol/
│   ├── Application.java                          ← @SpringBootApplication (Lambda-unaware)
│   └── controller/HelloController.java           ← @RestController (Lambda-unaware)
├── src/main/resources/application.properties     ← Spring config (banner off, server port)
├── pom.xml                                       ← Spring Boot only — no Lambda libs; comments
│                                                   point you to what to add
├── template.yaml                                 ← SAM scaffold — Handler is TODO
├── samconfig.toml                                ← stack name + region (us-west-2) pre-set
└── README.md                                     ← this file
```

## Step 0 — Confirm the app works in its current "non-serverless" form

**Option 1 — Native install (faster cold dev loop):**

```bash
mvn spring-boot:run
# → Tomcat started on port 8080

# in another terminal:
curl http://localhost:8080/
curl http://localhost:8080/api/hello/Lan
curl -X POST http://localhost:8080/api/echo -H 'Content-Type: application/json' -d '{"hi":"there"}'
```

(Maven 3.9+ and JDK 21+ required. `brew install maven openjdk@21` on macOS.)

**Option 2 — Docker (no Maven/JDK on host, but slower first boot):**

```bash
# One-shot — mounts the source folder, deps cached in Docker volume
docker run --rm -p 8080:8080 -v "$PWD":/app -w /app \
  maven:3.9-eclipse-temurin-21 mvn spring-boot:run

# Or build the dev image once, reuse:
docker build -f Dockerfile.dev -t byol-java-spring-boot:dev .
docker run --rm -p 8080:8080 byol-java-spring-boot:dev
```

First boot inside Docker downloads ~80 MB of Spring deps (~ 1-3 min). Once
warmed, subsequent boots are < 30 s.

## Step 1 — Pick your strategy

| # | Strategy | What you add | Code-change cost | Cold start estimate |
|---|----------|--------------|------------------|---------------------|
| A | `aws-serverless-java-container-springboot3` | new `StreamLambdaHandler` class + shade plugin in pom.xml | ~10 lines Java + ~30 lines pom | 5–10 s |
| B | **AWS Lambda Web Adapter** (Lambda Layer) | `run.sh` + edit `template.yaml` | 0 Java lines | ~6–11 s |
| C | Spring Cloud Function | refactor controllers to `Function<,>` beans | high (every route) | 3–6 s |
| D | Plain `aws-lambda-java-core` (manual routing) | new handler class + manual `APIGatewayV2HTTPEvent` parsing | 100+ lines | 4–8 s |

**A** is the canonical answer. **B** is the simplest. **C** is what Spring
preaches but rarely the pragmatic choice. **D** is "we don't trust libraries."

Document **why** you picked your option in `NOTES.md` (worksheet Q4.1 + Q4.6).

## Step 2 — Implement

The repo intentionally leaves you these blanks:

- `pom.xml` — only `spring-boot-starter-web` is declared. The comments tell
  you exactly which extra dep + plugin config you need per strategy.
- `template.yaml` — `Handler:` is `TODO_FILL_IN`. Replace based on strategy.
- *New file(s)* under `src/main/java/dev/byol/lambda/` (for strategy A) or
  `src/main/resources/run.sh` (strategy B). **Don't touch `HelloController`
  or `Application`.**

> **Hard rule:** `HelloController.java` and `Application.java` must NOT
> import anything from any Lambda library. Keep the framework layer clean.

## Step 3 — Build + deploy

```bash
sam build           # runs `mvn package` — first build downloads Maven deps (~3 min)
sam deploy --guided
# Subsequent:
sam deploy
```

No local Maven? Add `--use-container` to `sam build` — SAM compiles inside
an AWS-provided Docker image with Maven + JDK 21 pre-installed.

Region MUST be `us-west-2` if you're on the workshop participant account.

## Step 4 — Smoke-test the live URL

```bash
export API=$(aws cloudformation describe-stacks \
  --stack-name byol-java-spring-boot --region us-west-2 \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiUrl`].OutputValue' --output text)

curl $API
curl $API/api/hello/Lan
curl -X POST $API/api/echo -H 'Content-Type: application/json' -d '{"hi":"there"}'
```

The first request after deploy is the **cold start** — expect 5-10 seconds
of latency. Subsequent calls are 5-20 ms.

## Step 5 — Measure cold start

```bash
sam logs --stack-name byol-java-spring-boot --region us-west-2 -t
```

Find the `REPORT` line. `Init Duration` will likely be **5,000-10,000 ms**
without SnapStart. This is **the** lesson: Spring Boot's import graph is
massive. SnapStart drops it to 200-500 ms (see Bonus B in the worksheet).

## Teardown

```bash
sam delete --stack-name byol-java-spring-boot --region us-west-2
```

## Common pitfalls

| Symptom | Probably... |
|---------|-------------|
| Lambda crashes on first invoke with `ClassNotFoundException` for your handler | You're using Spring Boot's nested-JAR layout; Lambda needs a flat uber-jar — switch to maven-shade-plugin with `PropertiesMergingResourceTransformer` (only for strategy A) |
| `Could not initialize Spring Boot Lambda container` | Spring failed to bootstrap; usually missing `application.properties` from the shaded JAR — `ServicesResourceTransformer` must be in the shade plugin config |
| `sam build` takes 3+ minutes | First Maven build downloads deps; subsequent builds cache to `~/.m2` and are ~30s |
| Cold start "stuck" at 10 s | Expected, this is Java without SnapStart. Enable SnapStart in template (see Bonus B) |
| 502 Bad Gateway | Handler FQN in `template.yaml` doesn't match the class you wrote, or the class isn't in the shaded jar |
| AccessDenied on deploy | Wrong region — must be `us-west-2` on workshop role |

## Why this is the "hardest" of the 3 starters

- Spring Boot's classloading + nested-JAR layout is incompatible with how
  Lambda loads classes. The shade plugin transformers are non-obvious.
- Cold start is dominated by Spring's reflection + auto-configuration,
  not by your code. The fix is SnapStart, not code optimisation.
- Local dev with `mvn spring-boot:run` and Lambda runtime behave
  differently in subtle ways (e.g., `@Value` bean injection timing).

If you finish Strategy A and have time, **do Bonus B** — adding SnapStart
is what makes Java on Lambda actually viable in production.
