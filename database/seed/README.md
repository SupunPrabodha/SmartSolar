# Seed data

DevelopmentDataSeeder creates the configured Backoffice account only in Development and only if absent. Credentials come from local User Secrets and are never committed. Changing seed configuration does not reset an existing password. Use the REST API to create disposable station/slot/reservation test data; do not seed mock business results into clients.

Production needs a privately reviewed initial-account provisioning procedure; see [IIS deployment](../../deployment/iis/README.md). Never enable Development in production to trigger the seed.
