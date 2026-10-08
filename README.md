# Ascon

Track manga and comic reading across any website. Android app plus a Go backend.

Start with [AGENTS.md](AGENTS.md) for architecture and decisions, and [design/](design/README.md) for the approved UI.

## Checks

Android, from `android/`, needs JDK 17 or newer and Android SDK platform 37:

```
./gradlew ktlintCheck detekt lint test -Proborazzi.test.verify=true assembleDebug
```

Compose screens have golden screenshots in each feature's `src/test/screenshots/`, rendered at the mockups' 390×844 by Robolectric. After an intended UI change, record new ones and review the images in the diff:

```
./gradlew recordRoborazziDebug
```

`./run.sh` builds the app and opens it on an emulator. The screens run on fake data in `core/data/fake` until Room and the backend client exist.

Backend, from `backend/`:

```
go vet ./...
go test -race ./...
go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.14.0 run
go run github.com/fe3dback/go-arch-lint@v1.19.0 check
sqlc generate   # after changing migrations/ or queries/
```

## Running the backend

From `backend/`:

```
go run ./cmd/api                             # listens on :8080, data in ascon.db
go run ./cmd/devaccount -tier premium        # prints an account token
```

Settings come from environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `ASCON_ADDR` | `:8080` | Listen address |
| `ASCON_DB` | `ascon.db` | SQLite file |
| `ASCON_SIGNING_KEY` | throwaway key | Base64 Ed25519 seed that signs rules. Required outside development |
| `ASCON_SIGNING_KEY_ID` | `dev` | Key id the app pins |
| `ASCON_QUOTA_FREE` | `10` | AI detections per month, free accounts |
| `ASCON_QUOTA_PREMIUM` | `200` | AI detections per month, premium accounts |

`devaccount` stands in for sign-in, which is not designed yet. No LLM provider is configured, so AI rule generation is rejected and the quota refunded.

CI runs the same commands on every pull request.
