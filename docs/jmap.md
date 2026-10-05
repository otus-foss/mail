# JMAP

JMAP lives in the `:jmap` Gradle module (`com.otus.jmap`): a provider-independent client for
RFC 8620 (core) plus login storage. It has no UI and no app code, so Otus Mail and Otus Calendar can
both use it. Application-specific models, account screens, and inbox integration are separate work.

## Module rules

- Kotlin explicit API mode: every public declaration is deliberate. Depends only on Ktor client
  core, kotlinx.serialization, and coroutines. Apps supply the HTTP engine (OkHttp on Android, Darwin
  on iOS).
- The architecture check enforces: package root `com.otus.jmap`; no Compose or lifecycle; no
  references to `com.otus.mail` or `com.otus.calendar`; no platform APIs in common code. File length,
  suppression, wildcard-import, detekt, and ktlint rules apply as everywhere else.
- The module is included independently in this repository. Application wiring and cross-repository
  distribution are separate decisions; see [Reuse in Calendar](#reuse-in-calendar).

## API

- `JmapClient(engine, policy)`: one per process. `session(url, auth, trustedOrigins)` discovers the
  session; `request { … }`, `send { … }`, and calls on a session run method calls.
- `JmapConnection`: one signed-in server. Caches the session, refetches it when a response reports
  a different `sessionState` (RFC 8620, section 3.4), and drops it after a transport failure.
  `call(method)` for one call, `request { …; call(x) }` returning the last call's typed result, and
  `send { … }` for several results.
- `JmapConnections(client, logins)`: one connection per saved login; `forget(id)` after it changes.
  Credential loading and cache insertion share the invalidation lock, including concurrent first access.
- `JmapResponse[call]` decodes the explicit response and validates its method name.
  `responses(call)` exposes every response in wire order, including implicit calls and errors;
  an implicit failure does not replace the explicit method's result.
- Typed methods for any data type (RFC 8620, section 5): `GetMethod`, `QueryMethod`, `ChangesMethod`,
  with `JmapType(name, capability, serializer)`. Result references (section 3.7) chain calls in one
  request: `JmapIds.Result(query.resultOf("/ids"))`. Subclass `JmapMethod` for anything else.
- Before sending, the client checks every capability against the session and each call's account,
  and throws `Unsupported` instead of sending a request the server cannot serve.
- Logins (`com.otus.jmap.login`): `JmapLogin` identifies a server and user, not a JMAP account.
  Each app resolves its own account per capability with `JmapSession.accountFor`, so one login can
  serve mail and calendars. `LoginStore` keeps secrets apart from the list.

## Security model

| Threat | Mitigation |
| --- | --- |
| Credentials sent in clear | Every URL must be https: the session URL, each redirect, and `apiUrl`. `JmapPolicy.allowInsecureHttp` exists only for local test servers. |
| Credentials leaked by a redirect | Credentials go only to the origin the user entered and origins the user approved. A redirect elsewhere stops with `RedirectedElsewhere` before credentials are sent there; callers must obtain approval and retry with the trusted origin. The same rule applies to `apiUrl`. |
| Hostile or broken responses | Responses are capped at 16 MiB. Every envelope element is validated; any malformed data becomes `NotJmap`. Nothing a server sends makes the client throw anything other than `JmapException`. |
| Secrets in logs or crash reports | `JmapAuth` and `LoginSecret` redact secrets. Secrets are not readable through public API; only headers are built from them. |
| Secrets at rest (Android) | AES-256-GCM with a non-exportable Android Keystore key; fresh IV per value; one synchronous commit per change; app backup disabled. |
| Secrets at rest (iOS) | Keychain, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`; replacement uses `SecItemUpdate`, and deletion errors preserve account metadata. |
| Unreadable stored data | A list that cannot be decoded is copied aside before it could be overwritten. An undecryptable secret is reported as `CredentialsUnavailable`, never as a server rejection. |

Not covered: certificate pinning, user-installed CAs and self-signed certificates (platform trust
only), OAuth, and sharing logins between the two installed apps.

## RFC 8620 conformance

| Section | Behavior | Tests |
| --- | --- | --- |
| 2, 2.2 | Session resource; `/.well-known/jmap` with redirects (relative `Location` included); relative URLs resolved | `JmapDiscoveryTest` |
| 3.2–3.4 | Request envelope with `using`, invocations, `sessionState` | `JmapRequestTest`, `JmapConnectionTest` |
| 3.6.1 | Request-level problem details become `RequestFailed(type)` | `JmapDiscoveryTest` |
| 3.6.2 | Method-level errors become `MethodError(type)` for that call only | `JmapRequestTest` |
| 3.7 | Result references | `JmapRequestTest` |
| 5.1, 5.2, 5.5 | `/get`, `/changes`, `/query` | `JmapRequestTest`, `JmapFailureTest` |
| 6.2 | Bounded authenticated blob downloads through the advertised URI template, with trusted-origin checks | `JmapDownloadTest` |
| RFC 7617 | Basic credentials; a colon in the username is rejected | `JmapLoginTest` |

Not implemented yet: `/set`, `/queryChanges`, `/copy`, uploads, push (section 7),
and request limits such as `maxCallsInRequest`.

## Application integration

Applications supply the platform HTTP engine and define protocol objects using `JmapType`.
Use `accountFor` for the needed capability, and handle missing capabilities explicitly.
UI, mail-specific models, synchronization, and account connection screens are not part of this module.

## Reuse in Calendar

Calendar can use the same client, connections, logins, and stores by defining `CalendarEvent`
types with `JmapType` and the `urn:ietf:params:jmap:calendars` capability, and resolves its account
with `accountFor`. Distributing the module across repositories is a pending decision: a composite
build from a sibling checkout, or a published library from a separate repository. Until then it
lives here. Installed apps keep separate login storage.

## Checks

`./gradlew :jmap:testAndroidHostTest` runs protocol tests with Ktor's mock engine, concurrent
connection-cache tests, and Android preference-store tests with an injected cipher. It is required
by `checkAll` and Android CI. Tests cover explicit and implicit responses, method-name mismatches,
network failures and cancellation, capability checks, changes, account replacement, storage failures,
and corrupt-list preservation.

`./gradlew :jmap:iosSimulatorArm64Test` runs in macOS CI. The iOS store tests inject the native
Keychain boundary to exercise failed updates/additions/deletions while checking preserved metadata
and secrets. These tests do not prove device Keychain or Android Keystore behavior; real-device
security-storage validation remains necessary.
`ServerIntegrationTest` runs against a real server only when configured; otherwise JUnit reports it as skipped:

```sh
OTUS_JMAP_TEST_URL=http://127.0.0.1:8080/.well-known/jmap OTUS_JMAP_TEST_USER=admin \
OTUS_JMAP_TEST_PASSWORD=… OTUS_JMAP_TEST_INSECURE=1 ./gradlew :jmap:testAndroidHostTest --rerun-tasks
```

The opt-in integration tests verify session discovery, mail access, and wrong-password rejection.
They are not configured in CI. iOS code and test sources compile to klibs on Linux; linking and
running simulator tests, including the Keychain adapter tests, require macOS/Xcode.
