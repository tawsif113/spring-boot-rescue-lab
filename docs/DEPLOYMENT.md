# Deployment

The repository is prepared for a public portfolio deployment on Railway using the root `Dockerfile` and `railway.toml`.

Railway automatically detects the Dockerfile. The application listens on the platform-provided `PORT`, and the deployment health check targets:

```text
/actuator/health/readiness
```

## Required services

Provision three services/resources:

1. Spring Boot application from this GitHub repository.
2. PostgreSQL.
3. Redis.

RabbitMQ is optional for a reduced public demo if event publishing/consumption is disabled. For the full Rescue Lab behavior, provision RabbitMQ as well.

## Application variables

Map the managed-service connection values into:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USER
RABBITMQ_PASSWORD
```

For a public read-only demo without RabbitMQ, use:

```text
OUTBOX_PUBLISHER_ENABLED=false
ORDER_EVENTS_CONSUMER_ENABLED=false
```

Use non-default values for:

```text
ALICE_PASSWORD
BOB_PASSWORD
ADMIN_PASSWORD
```

## Public demo recommendation

Expose the public catalog and documentation surfaces:

- `GET /api/catalog/products/{id}`
- `/swagger-ui.html`
- `/v3/api-docs`
- `/actuator/health`

Do not publish the default local passwords.

## Health model

Railway waits for the readiness endpoint before activating a deployment. In this lab readiness includes PostgreSQL but intentionally excludes Redis and RabbitMQ. That reflects the service design: PostgreSQL is authoritative, while Redis can fail open and RabbitMQ outages can be buffered by the outbox.

## Notes

This is a portfolio environment, not a production hosting prescription. The production checklist remains the source of truth for TLS, OIDC/OAuth2, secrets, broker topology, database backups, observability, retention, and scaling concerns.
