# Ascon

Track manga and comic reading across any website. Android app plus a Go backend.

Start with [AGENTS.md](AGENTS.md) for architecture and decisions, and [design/](design/README.md) for the approved UI.

## Checks

Android, from `android/`, needs JDK 17 or newer and Android SDK platform 37:

```
./gradlew ktlintCheck detekt lint test assembleDebug
```

Backend, from `backend/`:

```
go vet ./...
go test -race ./...
go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.14.0 run
go run github.com/fe3dback/go-arch-lint@v1.19.0 check
```

CI runs the same commands on every pull request.
