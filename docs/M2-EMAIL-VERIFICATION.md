# Member 2: approval and email verification

New Prosumer accounts register with their own password and remain `PendingActivation`.
Backoffice uses the existing `PATCH /api/v1/users/{nic}/activate` endpoint to approve
the account and send a verification email. The account remains pending until the
recipient explicitly confirms the link through `POST /api/v1/auth/verify-email`.
The body is `{ "nic": "...", "token": "..." }`. Success returns 204, without a JWT.
The recipient then signs in to Android normally. Web staff roles are unchanged.

The public web route `/verify-email` presents the confirmation button. The email
contains the NIC and opaque token in the URL fragment, not a query string. Opening
a link does not activate the account; a POST is required. Tokens expire after 24
hours, are single-use, and only their SHA-256 hashes are stored. Backoffice may
resend after one minute; each resend invalidates the previous link. A delivery
failure leaves the account pending and reports a retryable error. No fake delivery
or token logging is provided.

## SMTP configuration

Add these settings to the API's existing User Secrets (merge them with the existing
MongoDb/JWT settings). Do not commit SMTP credentials:

```json
"VerificationEmail": {
  "Host": "YOUR_SMTP_HOST",
  "Port": 587,
  "Username": "YOUR_SMTP_USERNAME",
  "Password": "YOUR_SMTP_PASSWORD_OR_APP_PASSWORD",
  "From": "YOUR_VERIFIED_SENDER_ADDRESS",
  "VerificationPageUrl": "http://localhost:5173/verify-email"
}
```

Use a provider supporting authenticated SMTP with STARTTLS (usually port 587).
Implicit TLS on port 465 is not supported by this adapter. TLS is required.
The sender address must be permitted by your provider. For IIS, supply the same
keys using environment variables such as `VerificationEmail__Host`.

For testing the email on your PC, localhost can work while the frontend is running.
For a recipient opening email on a phone or another computer, configure a reachable
HTTPS frontend URL ending in `/verify-email`. Configure its Vite API URL and API CORS
for that deployment. Configure the web host to serve the SPA on this route.
Missing SMTP settings block approval with a clear error but do not stop API startup.

## Editing and deactivation

NIC, role and password remain protected during contact edits. Changing a Prosumer's
email clears previous approval/verification and returns an active account to pending.
Backoffice must approve the new address and the recipient must verify it. Editing
name or phone alone leaves account status unchanged. A deactivated account stays
deactivated when edited. Deactivation revokes outstanding links and preserves history.
Only Backoffice can start reactivation, which again requires email verification.

Mongo account updates compare/increment `AccountVersion`; stale saves fail with 409
instead of overwriting newer account status. Reservation lock fields are preserved.
Old documents without the version are supported. Existing active accounts remain
active; they are not retrospectively treated as verified and are not migrated.

## Verification before handoff

- Register; verify pending login is refused and the account appears on Backoffice.
- Approve; verify email delivery and continued login refusal before confirmation.
- Confirm; verify login succeeds, and reuse of the link fails.
- Resend; verify the old link fails and the new one succeeds; check expiry.
- Deactivate before confirmation; verify the old link cannot reactivate the account.
- Edit email; verify old links and the previous active session no longer work.
- Race a profile edit against deactivation; verify the stale edit receives 409.
- Verify GridOperator cannot approve accounts and staff activation still works.

Automated tests use a captured email sender, never real recipients. Mongo integration
tests require a separately configured test database server; do not point tests at the
shared coursework database. Real SMTP delivery needs a manual check after configuration.
