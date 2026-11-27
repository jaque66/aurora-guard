# Changelog / Historia zmian

Wersjonowanie / Versioning: 1.0 → 1.1 → 1.2 … (każda poprawka podbija wersję o 0.1; większa zmiana tylko na wyraźne polecenie właściciela / each fix bumps the version by 0.1; bigger jumps only when the owner decides).

## 2.7 – 2026-11-27

**PL**
- **Nowy układ**: ekran główny pokazuje tylko **Stan** i **Uprawnienia**; pozostałe sekcje (ikona, ochrona przed odinstalowaniem, tryb nocny, lokalizacja, połączenie z HA, aktualizacje, historia zmian) są w **Ustawieniach** – ikona ⚙ w prawym górnym rogu.
- **Większe okno PIN-u** (duże pole i cyfry).
- **Logi aplikacji** (Ustawienia → Logi aplikacji): zdarzenia usług, połączenia z HA i awarie, z kopiowaniem do schowka.
- **Usługa dostępności po aktualizacji**: błąd uruchamiania usługi pierwszoplanowej nie zabija już procesu (a z nim usługi dostępności); aplikacja wykrywa wyłączoną usługę i włącza ją sama (po jednorazowym `adb shell pm grant pl.aurora.guard android.permission.WRITE_SECURE_SETTINGS`) albo wysyła powiadomienie „włącz usługę dostępności”.

**EN**
- **New layout**: the main screen shows only **Status** and **Permissions**; all other sections (icon, uninstall protection, night mode, location, HA connection, updates, changelog) are under **Settings** – the ⚙ icon in the top right.
- **Bigger PIN dialog** (large field and digits).
- **App logs** (Settings → App logs): service events, HA connection and crashes, with copy to clipboard.
- **Accessibility service after an update**: a failing foreground-service start no longer kills the process (and the accessibility service with it); the app detects a disabled service and re-enables it itself (after a one-time `adb shell pm grant pl.aurora.guard android.permission.WRITE_SECURE_SETTINGS`) or posts a “turn on the accessibility service” notification.

## 2.6 – 2026-11-27

**PL**
- **Stabilniejsze raportowanie**: heartbeat budzony przez alarm systemowy (działa też przy wygaszonym ekranie i w trybie oszczędzania), z automatycznym wznowieniem połączenia z HA i usługi, gdy system je zatrzymał. Telefon nie powinien już „znikać” jako offline po kilkudziesięciu minutach bezczynności.
- Sprawdzanie adresu domowego nie wywołuje już w HA wpisów „invalid authentication”.
- Gdy HA odrzuci token, status w aplikacji mówi o tym wprost.

**EN**
- **More reliable reporting**: the heartbeat is driven by a system alarm (works with the screen off and in battery saver), and the HA connection and service are restarted if the system stopped them. The phone should no longer drop to offline after tens of minutes of inactivity.
- Home-address probing no longer causes “invalid authentication” entries in HA.
- When HA rejects the token, the app status says so explicitly.

## 2.5 – 2026-11-27

**PL**
- **Tryb nocny**: w ustawionych godzinach (domyślnie 22:00–06:00) aplikacja rozłącza się z Home Assistantem i nie raportuje, a rano łączy się ponownie. Blokady działają w nocy lokalnie na ostatnio pobranych regułach. Przed uśpieniem aplikacja informuje HA, że telefon śpi (w Pulse widać „śpi do 06:00”, a rodzic nie dostaje fałszywego alarmu o braku kontaktu). Godziny i włączenie ustawia się w aplikacji albo z Home Assistanta.

**EN**
- **Night mode**: during the configured hours (default 22:00–06:00) the app disconnects from Home Assistant and stops reporting, then reconnects in the morning. Blocking keeps working locally from the last rules. Before sleeping the app tells HA that the phone is asleep (Pulse shows “asleep until 06:00” and parents get no false “no contact” alert). The hours and the switch can be set in the app or from Home Assistant.

## 2.4 – 2026-11-27

**PL**
- **Mniejsze zużycie baterii.** Aplikacja nasłuchuje w Home Assistancie tylko własnych encji (wcześniej odbierała wszystkie zmiany stanów w całym domu), raport statusu wysyła co 10 minut zamiast co 5, utrzymuje połączenie rzadszym sygnałem „ping” (co 90 s) i liczy statystyki mniejszą liczbą zapytań.
- **Lokalizacja oszczędniej**: automatyczne raporty używają najpierw sieci (Wi‑Fi / komórkowa), a GPS włączają tylko, gdy dokładność jest słaba. Na prośbę rodzica („Gdzie jest telefon?”) nadal używany jest GPS.
- Sprawdzanie limitów czasu co 30 s zamiast co 20 s.

**EN**
- **Lower battery use.** The app now listens to Home Assistant for its own entities only (it used to receive every state change in the house), sends the status report every 10 minutes instead of 5, keeps the connection alive with a less frequent ping (every 90 s) and computes statistics with fewer queries.
- **Cheaper location**: automatic reports use the network first (Wi‑Fi / cellular) and turn GPS on only when the accuracy is poor. On a parent’s request (“Where is the phone?”) GPS is still used.
- Time-limit checks every 30 s instead of 20 s.

## 2.3 – 2026-11-26

**PL**
- **Historia użycia aplikacji**: telefon przekazuje do HA czas w aplikacjach z ostatnich 14 dni (top 8 dziennie), a HA gromadzi je dalej. W Pulse można cofać się dzień po dniu (wczoraj, tydzień temu).

**EN**
- **App usage history**: the phone reports app usage for the last 14 days (top 8 per day) to Home Assistant, which keeps accumulating it. In Pulse you can step back day by day (yesterday, a week ago).

## 2.2 – 2026-11-26

**PL**
- **Limity czasu** w aplikacjach (np. 60 min dziennie): po wyczerpaniu limitu (plus bonusowe minuty od rodzica) aplikacja się blokuje; 5 minut przed końcem dziecko dostaje ostrzeżenie. Wymaga „Dostępu do danych o użyciu aplikacji”.
- **Prośba o więcej czasu**: przycisk na ekranie blokady wysyła prośbę do rodzica (powiadomienie z przyciskami +15 / +30 min / Odmów albo odpowiedź w Pulse).
- **Statystyki**: czas w aplikacjach dziś i w ostatnich 7 dniach trafia do HA.
- **Znajdź telefon**: rodzic uruchamia głośny alarm na telefonie (nawet w trybie cichym), wyciszenie z powiadomienia.
- **Wiadomość od rodzica**: tekst wysłany z Pulse pojawia się na ekranie telefonu i w powiadomieniu.
- **Ostrzeżenia o ingerencji**: rodzic dostaje powiadomienie, gdy wyłączono usługę dostępności lub administratora urządzenia albo cofnięto uprawnienia lokalizacji / użycia.
- Przyciski „Poproś o czas” także na ekranie blokady całego telefonu.

**EN**
- **Time limits** per app (e.g. 60 min a day): when the limit (plus any bonus minutes from a parent) is used up the app is blocked; a warning appears 5 minutes before the end. Needs “Usage access”.
- **Ask for more time**: a button on the lock screen sends a request to the parent (notification with +15 / +30 min / Decline buttons, or reply in Pulse).
- **Statistics**: time in apps today and over the last 7 days is reported to HA.
- **Find my phone**: a parent triggers a loud alarm on the phone (even in silent mode), silenced from the notification.
- **Message from a parent**: text sent from Pulse is shown on the phone screen and as a notification.
- **Tamper warnings**: a parent is notified when the accessibility service or device admin is turned off, or the location / usage permissions are revoked.
- “Ask for time” button also on the whole-phone lock screen.

## 2.1 – 2026-11-25

**PL**
- **Ustawienia aplikacji sterowane z Home Assistanta**: częstotliwość lokalizacji poza domem i w domowym Wi-Fi, włączenie/wyłączenie lokalizacji oraz teksty komunikatów blokady (aplikacji i całego telefonu). Zmiana w HA działa od razu, bez dotykania telefonu. Jeśli odpowiednie encje (`input_*.guard_…_<id>`) nie istnieją, obowiązują ustawienia lokalne.

**EN**
- **App settings controlled from Home Assistant**: location frequency away from home and on the home Wi-Fi, location on/off and the lock-screen messages (apps and whole phone). A change in HA applies immediately, without touching the phone. If the matching entities (`input_*.guard_…_<id>`) do not exist, the local settings apply.

## 2.0 – 2026-11-25

**PL**
- **Lokalizacja telefonu** (opcjonalna, przełącznik „Lokalizacja” w aplikacji): telefon przekazuje pozycję do Home Assistanta przez webhook – poza domem co 15 minut, w domowym Wi-Fi co godzinę oraz na prośbę rodzica („Gdzie jest telefon?” w Pulse). Wymaga uprawnienia „Zezwalaj cały czas”. Włączona lokalizacja jest widoczna w powiadomieniu aplikacji. Pozycja trafia do `device_tracker` w HA (strefy i automatyzacje).

**EN**
- **Phone location** (optional, “Location” switch in the app): the phone reports its position to Home Assistant through the webhook – every 15 minutes away from home, hourly on the home Wi-Fi and on demand from a parent (“Where is the phone?” in Pulse). Needs the “Allow all the time” permission. When enabled it is shown in the app notification. The position ends up in a Home Assistant `device_tracker` (zones and automations).

## 1.9 – 2026-11-25

**PL**
- **Połączenie wewnętrzne / zewnętrzne**: nowe pola „Adres wewnętrzny” i „Nazwa domowego Wi-Fi (SSID)”. W domowej sieci aplikacja łączy się z HA adresem wewnętrznym, poza domem zewnętrznym; przełącza się sama przy zmianie sieci. Odczyt nazwy Wi-Fi wymaga uprawnienia lokalizacji (Android tak wymaga); bez niego, albo przy pustej nazwie, aplikacja sprawdza, czy adres wewnętrzny odpowiada. Na ekranie głównym widać aktualny tryb.

**EN**
- **Internal / external connection**: new “Internal address” and “Home Wi-Fi name (SSID)” fields. On the home network the app uses the internal address, elsewhere the external one, switching automatically when the network changes. Reading the Wi-Fi name needs the location permission (an Android requirement); without it, or with an empty name, the app checks whether the internal address responds. The current mode is shown on the main screen.

## 1.8 – 2026-11-25

**PL**
- **Ochrona przed odinstalowaniem**: nowy przełącznik „Administrator urządzenia”. Gdy jest włączony, w Ustawieniach przycisk „Odinstaluj” jest nieaktywny, dopóki administrator nie zostanie wyłączony. Z uprawnieniami Device Owner (ADB) odinstalowanie jest blokowane na stałe.
- Ukryta ikona: jeśli launcher nadal pokazuje starą ikonę (pamięć podręczna), zrestartuj telefon lub launcher; po ukryciu aplikacja nie ma już żadnej aktywności startowej.

**EN**
- **Uninstall protection**: new “Device administrator” switch. While on, the “Uninstall” button in Settings is disabled until the administrator is turned off. With Device Owner rights (ADB) uninstalling is blocked permanently.
- Hidden icon: if the launcher still shows a stale icon (cache), restart the phone or launcher; once hidden the app has no launcher activity at all.

## 1.7 – 2026-11-25

**PL**
- **Ukrywanie ikony aplikacji** (ustawienie „Ikona aplikacji” na ekranie głównym aplikacji): ikona znika z ekranu głównego i listy aplikacji. Ustawienia otworzysz z powiadomienia „Aurora Guard działa”, z Ustawienia → Dostępność → Aurora Guard (ikona ⚙) albo kodem `*#*#4827#*#*` wpisanym w aplikacji Telefon (nie działa na każdym telefonie). Usługa dalej działa w tle.
- **Blokada całego telefonu**: większe odstępy i większe przyciski Telefon, Wiadomości, Kontakty.

**EN**
- **Hide the app icon** (“App icon” setting on the main screen): the icon disappears from the home screen and the app list. Open the settings from the “Aurora Guard działa” notification, from Settings → Accessibility → Aurora Guard (⚙ icon) or with the code `*#*#4827#*#*` dialled in the Phone app (not supported on every phone). The service keeps running in the background.
- **Whole-phone lock**: larger spacing and bigger Phone, Messages, Contacts buttons.

## 1.6 – 2026-11-25

**PL**
- **Wydanie publiczne**: kod i wydania są dostępne publicznie, bez danych osobowych i bez kluczy. Klucz podpisujący jest przechowywany wyłącznie w sekretach CI, a repozytorium zawiera dokumentację konfiguracji po stronie Home Assistanta (`homeassistant/`). Podpis aplikacji jest taki sam jak w 1.4–1.5, więc aktualizacja instaluje się na wierzch.
- Brak telemetrii: aplikacja łączy się tylko z Twoim Home Assistantem i (opcjonalnie) z GitHubem w celu sprawdzenia aktualizacji.

**EN**
- **Public release**: the code and releases are public, with no personal data and no keys. The signing key lives only in CI secrets, and the repository ships the Home Assistant side setup docs (`homeassistant/`). The app signature is the same as in 1.4–1.5, so it installs over the existing version.
- No telemetry: the app only talks to your Home Assistant and (optionally) GitHub to check for updates.

## 1.5 – 2026-11-25

**PL**
- **Aktualizacje w osobnym oknie** (przycisk „Aktualizacje” na ekranie głównym): sprawdzanie i instalacja nowej wersji, opis zmian wydania, ustawienia aktualizatora (repozytorium, token GitHub, automatyczne sprawdzanie raz na dobę – można wyłączyć). Okno jest chronione PIN-em rodzica.
- **Zmiana identyfikatora telefonu automatycznie zmienia encje** potrzebne do działania (blokada, blokada całego telefonu, listy todo, czujnik reguł). Ręczne pola encji usunięto; pod identyfikatorem widać na żywo nazwy encji, których aplikacja używa. Stare, ręcznie wpisane nazwy są czyszczone przy zapisie.

**EN**
- **Updates in a separate window** (“Updates” button on the main screen): checking and installing a new version, release notes, updater settings (repository, GitHub token, automatic daily check – can be turned off). The window is protected by the parent PIN.
- **Changing the phone identifier automatically changes the entities** the app needs (lock, whole-phone lock, todo lists, rules sensor). The manual entity fields were removed; below the identifier you see live which entities the app uses. Old manually entered names are cleared on save.

## 1.4 – 2026-11-25

**PL**
- **Aktualizacja bez odinstalowywania**: wersje są podpisywane stałym kluczem, więc każda następna instaluje się na poprzednią (jednorazowo przy przejściu z 1.3 na 1.4 trzeba jeszcze odinstalować starą wersję – wcześniejsze wersje miały losowy podpis).
- **Wbudowany aktualizator**: sekcja „Aktualizacje” w aplikacji sprawdza najnowsze wydanie na GitHubie, pobiera plik APK i uruchamia instalację (wymaga zgody systemu na instalowanie z tego źródła). Raz na dobę strażnik sprawdza wydania w tle i pokazuje powiadomienie o nowej wersji. Dla repozytorium prywatnego trzeba wpisać token GitHub (uprawnienie Contents: read); dla publicznego nie.
- Każda zbudowana wersja jest publikowana jako wydanie GitHub (APK + opis zmian).

**EN**
- **Update without uninstalling**: builds are signed with a fixed key, so each version installs over the previous one (one-time exception when moving from 1.3 to 1.4 – earlier builds had a random signature).
- **Built-in updater**: the “Updates” section checks the latest GitHub release, downloads the APK and starts the installer (needs the system permission to install from this source). Once a day the watchdog checks releases in the background and shows a notification about a new version. A private repository needs a GitHub token (Contents: read); a public one does not.
- Every build is published as a GitHub release (APK + release notes).

## 1.3 – 2026-11-25

**PL**
- **Samoczynne wznawianie połączenia**: po aktualizacji aplikacji lub zabiciu usługi przez system (oszczędzanie baterii) połączenie z Home Assistantem wraca samo – bez otwierania aplikacji i klikania „Zapisz i uruchom”. Strażnik działa co ok. 15 minut i przeżywa restart telefonu; usługa dostępności również ją wznawia przy aktywności na telefonie.

**EN**
- **Automatic reconnection**: after an app update or when the system kills the service (battery saving), the connection to Home Assistant returns by itself – no need to open the app and tap “Save and start”. A watchdog runs about every 15 minutes and survives reboots; the accessibility service also restarts the service on activity.

## 1.2 – 2026-11-25

**PL**
- **Poprawka: blokada nie działała na koncie zwykłego użytkownika** – lista aplikacji do blokowania była odczytywana poleceniem, które wymaga uprawnień administratora, więc telefon dostawał włączenie blokady, ale pustą listę. Reguły są teraz odczytywane z czujnika `sensor.aurora_guard_<id>_rules` (atrybuty `blocked` i `allowed`), dostępnego dla każdego użytkownika.

**EN**
- **Fix: blocking did not work with a regular (non-admin) user** – the list of apps to block was read with a command that requires administrator rights, so the phone got the lock switch but an empty list. Rules are now read from `sensor.aurora_guard_<id>_rules` (attributes `blocked` and `allowed`), available to any user.

## 1.1 – 2026-11-25

**PL**
- Raporty (lista aplikacji i status) wysyłane przez **webhook** Home Assistanta – token w aplikacji może należeć do **zwykłego użytkownika (bez uprawnień administratora)**. Pole „Identyfikator webhooka” w ustawieniach; puste = dotychczasowy tryb przez REST (wymaga administratora).
- Ekran stanu pokazuje, którym trybem aplikacja raportuje.

**EN**
- Reports (app list and status) are sent through a Home Assistant **webhook** – the token in the app can belong to a **regular (non-admin) user**. New “Webhook ID” setting; empty = the previous REST mode (admin required).
- The status card shows which mode is used for reporting.

## 1.0 – 2026-11-25

**PL – pierwsze wydanie**
- Blokowanie wybranych aplikacji na telefonie dziecka, sterowane z Home Assistanta (ręcznie przełącznikiem albo trybem auto – blokada do wykonania zadań dziecka).
- Blokada całego telefonu z osobnym komunikatem (np. „Pora na przerwę od telefonu”); dozwolone zawsze: telefon, SMS, kontakty i połączenia alarmowe oraz dodatkowa lista aplikacji wybierana w Home Assistancie. Ekran blokady ma przyciski dozwolonych aplikacji.
- Jawna dla dziecka nakładka z kłódką, nazwą aplikacji i komunikatem rodzica.
- Lista zainstalowanych aplikacji telefonu jest wysyłana do Home Assistanta – blokowane aplikacje zaznaczasz w Aurora Pulse (widok Aurora Guard), z wyszukiwarką.
- Praca w tle: usługa pierwszoplanowa z połączeniem WebSocket, automatyczne wznawianie po restarcie i utracie sieci; reguły działają też offline (ostatnio znane).
- Status w HA: `sensor.aurora_guard_<id>_status` (połączenie, bateria, czy usługa dostępności jest włączona, ostatni kontakt) – alert w Pulse, gdy usługa zostanie wyłączona lub telefon przestanie raportować.
- Ekran ustawień w stylu Aurora, lista kontrolna uprawnień (usługa dostępności, ustawienia ograniczone, powiadomienia, bateria), opcjonalny PIN rodzica.
- Ikona aplikacji (tarcza z kłódką w gradiencie Aurora) i historia zmian w aplikacji.
- Opcjonalnie: tryb Device Owner (twarde zawieszanie aplikacji przez system, przez ADB).

**EN – first release**
- Blocks selected apps on a child's phone, controlled from Home Assistant (manually or with auto mode – locked until the child's tasks are done).
- Whole-phone lock with its own message (e.g. “Time for a phone break”); always allowed: phone, SMS, contacts and emergency calls plus an extra allow-list chosen in Home Assistant. The lock screen has buttons for the allowed apps.
- A child-visible overlay with a lock, the app name and the parent's message.
- The phone's installed apps are sent to Home Assistant – you pick blocked apps in Aurora Pulse (Aurora Guard view), with search.
- Background operation: foreground service with a WebSocket connection, auto-resume after reboot and network loss; rules also work offline (last known).
- Status in HA: `sensor.aurora_guard_<id>_status` (connection, battery, whether the accessibility service is on, last contact) – Pulse warns when the service is turned off or the phone stops reporting.
- Aurora-style settings screen, permission checklist (accessibility, restricted settings, notifications, battery), optional parent PIN.
- App icon (shield with a lock in the Aurora gradient) and an in-app changelog.
- Optional: Device Owner mode (hard app suspension by the system, via ADB).

### Historia przed wersją 1.0 / Pre-1.0 history (internal builds)
- 0.3.0 – blokada całego telefonu / whole-phone lock.
- 0.2.0 – interfejs Aurora, ikona, nowa nakładka / Aurora UI, icon, new overlay.
- 0.1.1 – poprawka zapisu encji blokady / fix for stored lock entities.
- 0.1.0 – szkielet aplikacji / initial skeleton.
