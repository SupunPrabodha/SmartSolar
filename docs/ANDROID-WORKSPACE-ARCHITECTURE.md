# Android workspace architecture

The authenticated app now has **one WorkspaceActivity**, with one toolbar, one FragmentContainerView and one floating BottomNavigationView. Login remains the exported launcher. The workspace and transaction Activities are internal.

## Destinations

| Role | Bottom navigation | Content |
| --- | --- | --- |
| Prosumer | Home, Stations, Reservations, History, Account | HomeFragment, StationsFragment, BookingWorkspaceFragment, BookingListFragment (History), AccountFragment |
| GridOperator | Home, Stations, Scan, Bookings, Search | HomeFragment, StationsFragment, external scanner, BookingWorkspaceFragment, SearchBookingsFragment |

HomeFragment shares the layout and adjusts existing actions using the verified role. StationsFragment is shared by both roles. Prosumer Reservations contains **My reservations / Current / Pending / Search**. Operator Bookings contains **Current / Pending / History**. These are retained child fragments, not additional Activities.

## Responsibilities and state

WorkspaceActivity verifies the session, creates the role menu, owns system/keyboard insets, selects content and handles Back. WorkspaceFragment contains destination content only. BookingWorkspaceFragment owns secondary tabs. Repositories and the REST API retain their existing responsibilities.

Top-level and secondary destinations are created lazily with stable FragmentManager tags. Selection uses add/show/hide and max lifecycle RESUMED for the selected destination, STARTED for hidden siblings. Transactions do not enter a tab Back stack. Reselecting a tab is a no-op. This follows [Android's fragment transaction model](https://developer.android.com/guide/fragments/transactions).

Retained views preserve fields, scroll, map position and expanded cards across tab switches. The existing AndroidX ViewModel dependency holds only in-memory presentation snapshots and view-hierarchy state for configuration recreation. FragmentManager restores fragment identities; the Activity saves the selected destination and verified owner identity, and the booking parent saves its section. Account draft fields also use savedInstanceState. Successful Save discards the draft snapshot and repopulates the form from the returned server profile; fields are disabled during Save. Failure preserves the draft.

After process death, API result snapshots are intentionally not persisted. The workspace verifies /users/me before revealing content, then loads the visible destination as needed. Hidden restored destinations load when first used. No token or QR payload is placed in the UI ViewModel or saved-state bundle. SQLite remains the existing local profile cache.

## Refresh policy

| Event | Behavior |
| --- | --- |
| First usable destination | Load once after workspace verification |
| Switch/reselect tab | Reuse content; no automatic profile/data request |
| Explicit Refresh/Search/Retry | Request that destination's data |
| Create/update/cancel/complete success | Increment an in-process booking revision; reload visible affected data once, hidden affected views on next selection |
| Station Detail Back | Preserve Stations data, list position and map; no station-list reload |
| Foreground/deep-flow return | Revalidate the session for security; data only reloads when invalidated |
| Configuration recreation | Reuse verified in-memory session if unexpired; reuse completed data snapshots |
| Process restoration | Fresh authoritative session verification and visible data load |

Home counts, booking lists, My reservations and search results share the booking invalidation revision. A failed request waits for explicit retry rather than retrying on every tab switch. Interrupted requests may retry after view recreation. Local cutoff controls are re-evaluated without fetching or rebuilding reservation cards.

## Back and deep workflows

Back from a non-Home top-level destination returns to Home. Back from Home backgrounds the task. Secondary tabs are not browser-like Back history.

Login/Registration, Station Detail, Reservation Create/Review, Edit/Review, Summary, Transaction QR, Scanner and Verification/Completion remain Activities. Review and completion are states within their existing Activities. DeepScreenChrome supplies only the Back toolbar/insets, never a bottom bar.

Summary Done uses CLEAR_TOP plus SINGLE_TOP to return to the existing WorkspaceActivity and selected parent. Other deep-screen Back/Done actions naturally finish back to the caller. Scan leaves the previous bottom item selected and returns there. Starting New Reservation from Home first selects the Reservations parent.

## Session and Maps

The menu is built from the server-verified profile. Session verification gates content after foreground return; a transient verification failure shows retry, not cached authorization. Expiry uses the existing logout/cache-clear path. The Activity observes session-token preference changes, including matching 401 clearing by the unchanged interceptor, and clears the authenticated task. Owner/role mismatch on restoration starts a fresh workspace, preventing another account's fragment state from being reused.

Stations uses a child SupportMapFragment with view lifecycle enabled, getMapAsync and generation-guarded callbacks. The Maps fragment manages its own lifecycle; this is not a manually embedded MapView. Location work is cancelled when leaving the screen; API requests are cancelled when its view is destroyed. Map/camera and list state survive ordinary tab switching. Existing coordinates, nearby radius, permissions, markers and station-detail requests are unchanged. See [Google's SupportMapFragment reference](https://developers.google.cn/android/reference/com/google/android/gms/maps/SupportMapFragment).

No Navigation Component, Compose or new dependency was added. The resolved existing versions are Fragment 1.5.4 and lifecycle-viewmodel 2.6.2.

Run the [manual acceptance checklist](FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md) and [screenshot checklist](FINAL-UI-SCREENSHOT-CHECKLIST.md). Host tests do not certify lifecycle, rendering or device behavior.
