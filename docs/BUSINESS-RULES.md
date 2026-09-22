# Business Rules

Authoritative validation must be implemented in the C# service layer.

## Implemented common foundation

- Registration creates a Prosumer in PendingActivation; Backoffice activation is required before login.
- Only Active accounts receive tokens. Every authenticated request checks current stored status/role.
- Backoffice controls activation/reactivation and staff creation. Prosumer self-deactivation is preserved from the starter.
- NIC is immutable and normalized; normalized email and NIC must be unique. Passwords are stored only as salted one-way hashes.
- Staff role is explicitly required and limited to Backoffice or GridOperator. User profile changes cannot change identity, role or account status.

## Assignment rules reserved for later feature packages

The table below preserves the starter contract. Station deactivation, reservation timing, Maps/QR and transaction behavior are **not implemented or certified in Phase 0**.

| Rule | Server responsibility |
|---|---|
| Prosumer identity | NIC is the business identifier. |
| Reactivation | Deactivated accounts can only be reactivated by Backoffice. |
| Node deactivation | Reject deactivation when active energy reservations exist. |
| Reservation horizon | A reservation must be scheduled within 7 days. |
| Reservation update | Require at least 12 hours' notice. |
| Reservation cancellation | Require at least 12 hours' notice. |
| QR | Only approved reservations may produce a transaction QR. |
| Transaction completion | Operator QR data must be verified by the server before completion. |
