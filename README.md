# Ascon

Track manga and comic reading across any website. Android app plus a Go backend.

Start with [AGENTS.md](AGENTS.md) for architecture and decisions, and [design/](design/README.md) for the approved UI.

## Checks

Android, from `android/`, needs JDK 17 or newer, Android SDK platform 37 and NDK 28.2.13676358:

```
./gradlew ktlintCheck detekt lint test -Proborazzi.test.verify=true assembleDebug
```

The ad blocker is Brave's adblock-rust, built from `engine/adblock/rust` by Gradle. It needs rustup with the Android targets and cargo-ndk. A distribution's own Rust can't build for Android. Gradle uses `~/.cargo/bin/cargo` when it exists, so rustup needs no PATH change:

```
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path
~/.cargo/bin/rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
~/.cargo/bin/rustup component add clippy rustfmt
~/.cargo/bin/cargo install cargo-ndk --locked
```

EasyList and EasyPrivacy ship in `engine/adblock/src/main/assets/adblock`. `tools/update-filter-lists.sh` refreshes them; installed apps also download newer copies weekly.

The Rust crate has its own checks, from `engine/adblock/rust`: `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings` and `cargo test`.

Compose screens have golden screenshots in each feature's `src/test/screenshots/`, rendered at the mockups' 390×844 by Robolectric. After an intended UI change, record new ones and review the images in the diff:

```
./gradlew recordRoborazziDebug
```

`./run.sh` builds the app and opens it on an emulator. Debug builds start with the sample library from `core/data/fake`.

UI flows run on an emulator with [Maestro](https://maestro.mobile.dev). They find elements by their text and report pass or fail. `tools/emulator-setup.sh` starts an emulator if none is connected. It also locks portrait, turns off animations and turns off stylus handwriting. Then install the debug app and run the flows:

```
tools/emulator-setup.sh
./gradlew installDebug
maestro/run.sh                    # every flow in maestro/flows
maestro/run.sh flows/smoke.yaml   # one flow
```

`maestro/run.sh` serves `maestro/site` on port 8765 while the flows run. The emulator reaches it at `http://10.0.2.2:8765`, and at `http://127.0.0.1:8765` through `adb reverse`, which flows use as a second site. Only debug builds may load these over plain http.

`tools/webview.py` runs JavaScript in the debug app's WebView on the connected device, to inspect a real site as Ascon loads it. It needs `pip install websocket-client`.

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
| `ASCON_LLM_BASE_URL` | none | OpenAI-compatible API that writes rules, ending before `/chat/completions` |
| `ASCON_LLM_API_KEY` | none | Its key |
| `ASCON_LLM_MODEL` | none | Its model, such as `deepseek-v4-flash` |
| `ASCON_LLM_ATTEMPTS` | `2` | Rules the model may offer per request. Each retry is told why the last one failed |

The server also reads a `.env` file in the directory it runs from, for development settings such as the LLM key. Real environment variables win over it. `.env` is ignored by git; never commit a key.

`devaccount` stands in for sign-in, which is not built yet. Without the `ASCON_LLM_*` settings, AI rule generation is rejected and the quota refunded.

To try generation without the app, give `devgenerate` saved chapter pages with the URLs they came from. It prints each rule the model offers and the server's verdict:

```sh
go run ./cmd/devgenerate 'https://site.example/a/ch-1=ch1.html' 'https://site.example/a/ch-2=ch2.html'
```

### With the debug app

Debug builds ask `http://127.0.0.1:8080/v1/` for rules and trust only the development key from `contracts/fixtures/signed-rule.json`. Start the backend with that key, forward the port, and store a rule to test with, since generation doesn't work yet:

```
ASCON_SIGNING_KEY=BVJXFv+SVtoLhp3etgbTU5mGcjZkZuduNYD7aotBkYk= go run ./cmd/api
adb reverse tcp:8080 tcp:8080
go run ./cmd/devrule -domain 10.0.2.2 rule.json   # stored as the domain's next version
```

Without the backend the app still works: lookups fail quietly and it uses its kept and built-in rules. Release builds have no backend address until the server is deployed.

CI runs the same commands on every pull request.
