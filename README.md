# Aurora Guard

**▶ Demo online / Live demo: https://jaque66.github.io/aurora-demo/**

🇵🇱 [Polski](#polski) · 🇬🇧 [English](#english) · [Changelog](CHANGELOG.md) · Licencja / License: MIT

---

## Polski

### Do czego służy
**Aurora Guard** to aplikacja na Androida (instalowana na telefonie dziecka), która **blokuje wybrane aplikacje albo cały telefon na polecenie z Home Assistanta**. Rodzic wybiera w dashboardzie Aurora Pulse, co ma być zablokowane i kiedy – ręcznie lub automatycznie (np. dopóki dziecko nie wykona zadań). Blokada jest **jawna**: dziecko widzi komunikat z kłódką i tekstem od rodzica.

### Możliwości
- **Blokada aplikacji** – zaznaczone aplikacje są zasłaniane pełnoekranową nakładką z komunikatem, a telefon wraca na ekran główny.
- **Blokada całego telefonu** – wszystko poza telefonem, SMS-ami, kontaktami i połączeniami alarmowymi jest zablokowane; osobny komunikat (np. „Pora na przerwę od telefonu”) i przyciski dozwolonych aplikacji. Dodatkowe dozwolone aplikacje wybierasz w Pulse.
- **Sterowanie z Home Assistanta**: przełączniki „Blokada aplikacji”, „Tryb auto” (blokada do wykonania zadań dziecka) i „Cały telefon”, lista aplikacji z wyszukiwarką.
- **Praca w tle** – usługa pierwszoplanowa z połączeniem WebSocket, wznawianie po restarcie i utracie sieci; ostatnio znane reguły działają także offline.
- **Status w HA** – połączenie, bateria, czy usługa dostępności jest włączona, ostatni kontakt; Pulse ostrzega, gdy telefon przestanie raportować.
- **Ekran ustawień w stylu Aurora**, lista kontrolna uprawnień, PIN rodzica, historia zmian w aplikacji.
- **Opcjonalnie Device Owner** – twarde zawieszanie aplikacji przez system (przez ADB, wymaga braku kont Google na telefonie).

### Jak to działa
Aplikacja łączy się z Home Assistantem tokenem długoterminowym (WebSocket + REST):
- odbiera stan `input_boolean.blokada_telefon_<id>` (blokada aplikacji), `input_boolean.blokada_telefon_<id>_caly` (cały telefon) oraz listy `todo.guard_<id>` (aplikacje do blokowania) i `todo.guard_<id>_dozwolone` (dozwolone przy blokadzie telefonu),
- wysyła `sensor.aurora_guard_<id>_apps` (zainstalowane aplikacje) i `sensor.aurora_guard_<id>_status`,
- egzekwuje blokadę usługą dostępności (nakładka + powrót na ekran główny).

`<id>` to identyfikator telefonu wpisany w aplikacji (np. `dziecko1`, `dziecko2`).

### Instalacja
1. Pobierz APK z zakładki *Releases* i zainstaluj na telefonie dziecka. Google Play Protect może zablokować instalację (aplikacja używa usługi dostępności) – na czas instalacji wyłącz skanowanie aplikacji w Play Protect albo zainstaluj przez ADB.
2. (Zalecane) W Home Assistant dodaj szablony webhooka dla czujników `sensor.aurora_guard_<id>_status` i `_apps` (plik `templates/aurora_guard.yaml`, wyzwalacz `webhook`) oraz czujniki reguł `sensor.aurora_guard_<id>_rules` – wtedy token w aplikacji może należeć do zwykłego użytkownika. W Home Assistant utwórz helpery: `input_boolean.blokada_telefon_<id>`, `input_boolean.blokada_telefon_<id>_caly`, `input_boolean.auto_blokada_telefonu_<id>` oraz listy zadań `todo.guard_<id>` i `todo.guard_<id>_dozwolone` (integracja Local To-do), a także długoterminowy token.
3. W aplikacji wpisz adres HA, token i identyfikator; włącz usługę dostępności (Android 13+: najpierw „Zezwól na ustawienia ograniczone”), powiadomienia i wyłącz optymalizację baterii.
4. W Aurora Pulse (widok *Aurora Guard*) zaznacz aplikacje do blokowania.

### Ograniczenia
- Bez Device Owner dziecko może wyłączyć usługę dostępności lub odinstalować aplikację; Home Assistant to zauważy (brak kontaktu / status), ale technicznie nie może temu zapobiec.
- Tryb awaryjny Androida i wyłączenie telefonu przyciskiem to obejścia, których aplikacja nie blokuje.
- Wersja `debug` nie jest podpisana kluczem produkcyjnym.

### Prywatność i bezpieczeństwo
- Aplikacja **nie ma telemetrii ani reklam**. Łączy się wyłącznie z Twoim Home Assistantem oraz (opcjonalnie) z GitHubem w celu sprawdzenia aktualizacji.
- Do Home Assistanta wysyła: listę zainstalowanych aplikacji (nazwy i pakiety), status (usługa dostępności, bateria, wersja) i odbiera reguły blokowania. Token i adres są zapisane tylko na telefonie.
- Używaj osobnego konta w HA **bez uprawnień administratora** i webhooka z długim losowym identyfikatorem; PIN rodzica chroni tylko ekran ustawień.
- Aplikacja jest jawna dla dziecka (komunikat o blokadzie). Używaj jej odpowiedzialnie i zgodnie z prawem.

### Budowanie własnej wersji
Fork + GitHub Actions budują APK bez żadnych sekretów (klucz debug). Aby wydania instalowały się na siebie, dodaj sekrety repozytorium `KEYSTORE_BASE64` (plik PKCS12 w base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` – workflow podpisze nimi APK i opublikuje wydanie. Nie commituj kluczy.

### Wersjonowanie
Zaczynamy od **1.0**; każda poprawka podbija wersję o 0.1 (1.1, 1.2…), większy skok tylko na decyzję właściciela. Zmiany: [CHANGELOG.md](CHANGELOG.md) (także w aplikacji: przycisk „Historia zmian”).

---

## English

### What it is
**Aurora Guard** is an Android app (installed on a child's phone) that **blocks selected apps or the whole phone on command from Home Assistant**. The parent chooses in the Aurora Pulse dashboard what is blocked and when – manually or automatically (e.g. until the child finishes their tasks). Blocking is **transparent**: the child sees a message with a lock and the parent's text.

### Features
- **App blocking** – selected apps are covered by a full-screen overlay with a message and the phone returns to the home screen.
- **Whole-phone lock** – everything except phone, SMS, contacts and emergency calls is blocked; its own message (e.g. “Time for a phone break”) and buttons for allowed apps. Extra allowed apps are chosen in Pulse.
- **Controlled from Home Assistant**: “App lock”, “Auto mode” (locked until the child's tasks are done) and “Whole phone” switches, plus a searchable app list.
- **Background operation** – foreground service with a WebSocket connection, resumes after reboot and network loss; the last known rules also work offline.
- **Status in HA** – connection, battery, whether the accessibility service is on, last contact; Pulse warns when the phone stops reporting.
- **Aurora-style settings screen**, permission checklist, parent PIN, in-app changelog.
- **Optional Device Owner** – hard app suspension by the system (via ADB, requires no Google accounts on the phone).

### How it works
The app connects to Home Assistant with a long-lived token (WebSocket + REST):
- it reads `input_boolean.blokada_telefon_<id>` (app lock), `input_boolean.blokada_telefon_<id>_caly` (whole phone) and the lists `todo.guard_<id>` (apps to block) and `todo.guard_<id>_dozwolone` (allowed during the phone lock),
- it publishes `sensor.aurora_guard_<id>_apps` (installed apps) and `sensor.aurora_guard_<id>_status`,
- it enforces the lock with an accessibility service (overlay + back to the home screen).

`<id>` is the phone identifier entered in the app (e.g. `child1`, `child2`).

### Installation
1. Download the APK from *Releases* and install it on the child's phone. Google Play Protect may block the install (the app uses an accessibility service) – turn off app scanning in Play Protect during installation, or install via ADB.
2. (Recommended) In Home Assistant add webhook-triggered template sensors for `sensor.aurora_guard_<id>_status` and `_apps` (`templates/aurora_guard.yaml`, `webhook` trigger) – then the token in the app can belong to a regular user. In Home Assistant create the helpers `input_boolean.blokada_telefon_<id>`, `input_boolean.blokada_telefon_<id>_caly`, `input_boolean.auto_blokada_telefonu_<id>`, the lists `todo.guard_<id>` and `todo.guard_<id>_dozwolone` (Local To-do integration) and a long-lived access token.
3. In the app enter the HA address, token and identifier; enable the accessibility service (Android 13+: first “Allow restricted settings”), notifications and disable battery optimisation.
4. In Aurora Pulse (*Aurora Guard* view) pick the apps to block.

### Limitations
- Without Device Owner a child can turn off the accessibility service or uninstall the app; Home Assistant will notice (no contact / status) but technically cannot prevent it.
- Android Safe Mode and powering the phone off are workarounds the app does not block.
- The `debug` build is not signed with a production key.

### Privacy and security
- The app has **no telemetry or ads**. It only talks to your Home Assistant and (optionally) GitHub to check for updates.
- It sends to Home Assistant: the list of installed apps (names and packages) and status (accessibility service, battery, version) and receives the blocking rules. The token and address are stored on the phone only.
- Use a dedicated HA account **without administrator rights** and a webhook with a long random id; the parent PIN only protects the settings screen.
- The app is transparent to the child (a lock message is shown). Use it responsibly and in accordance with the law.

### Building your own version
A fork + GitHub Actions builds the APK without any secrets (debug key). To make releases install over each other, add the repository secrets `KEYSTORE_BASE64` (PKCS12 file in base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` – the workflow then signs the APK and publishes a release. Never commit keys.

### Versioning
We start at **1.0**; every fix bumps the version by 0.1 (1.1, 1.2…), a bigger jump only when the owner decides. Changes: [CHANGELOG.md](CHANGELOG.md) (also in the app: “Changelog” button).
