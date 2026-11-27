# Konfiguracja po stronie Home Assistanta / Home Assistant side

**PL**
1. Utwórz helpery: `input_boolean.blokada_telefon_<id>`, `input_boolean.blokada_telefon_<id>_caly`, `input_boolean.auto_blokada_telefonu_<id>` (opcjonalnie – tryb auto) oraz dwie listy Local To-do: `todo.guard_<id>` (aplikacje do blokowania) i `todo.guard_<id>_dozwolone` (dozwolone przy blokadzie całego telefonu).
2. Dodaj szablony z `aurora_guard_template.yaml` (podmień `CHILD` i `WEBHOOK_ID`).
3. Utwórz użytkownika dla aplikacji (zwykłe konto, bez administratora) i wygeneruj dla niego długoterminowy token dostępu.
4. W aplikacji wpisz adres HA, token, identyfikator `<id>` i identyfikator webhooka.
5. Aplikacje do blokowania zaznacza się, dodając pozycje `pakiet|Nazwa` do listy `todo.guard_<id>` (np. w dashboardzie Aurora Pulse – widok Aurora Guard).
Opcjonalnie automatyzacja trybu auto: blokada włączona, dopóki zadania dziecka nie są wykonane.

**EN**
1. Create the helpers: `input_boolean.blokada_telefon_<id>`, `input_boolean.blokada_telefon_<id>_caly`, optionally `input_boolean.auto_blokada_telefonu_<id>`, and two Local To-do lists: `todo.guard_<id>` (apps to block) and `todo.guard_<id>_dozwolone` (allowed during the whole-phone lock).
2. Add the templates from `aurora_guard_template.yaml` (replace `CHILD` and `WEBHOOK_ID`).
3. Create a user for the app (regular, non-admin) and generate a long-lived access token for it.
4. In the app enter the HA address, token, the `<id>` and the webhook id.
5. Apps to block are added as `package|Name` items in `todo.guard_<id>` (e.g. in the Aurora Pulse dashboard – Aurora Guard view).

---

## Funkcje dodatkowe (od 2.0) / Additional features (since 2.0)

**PL** – Helpery tworzone dla każdego dziecka (`<id>`), wszystkie opcjonalne: aplikacja używa ich tylko, jeśli istnieją.

| Encja | Do czego |
|---|---|
| `input_boolean.guard_lokalizacja_<id>`, `input_number.guard_lok_poza_<id>` / `guard_lok_dom_<id>` | lokalizacja: włączenie i częstotliwość (min) poza domem / w domowym Wi-Fi |
| `input_button.lokalizacja_telefon_<id>` | „Gdzie jest telefon?” – prośba o aktualną pozycję |
| `input_text.guard_komunikat_<id>` / `guard_komunikat_caly_<id>` | treść komunikatów blokady aplikacji / całego telefonu |
| `input_number.guard_bonus_<id>` | bonusowe minuty na dziś (dodawane do wszystkich limitów; zerowane o północy) |
| `input_text.guard_prosba_<id>` | ostatnia prośba dziecka o czas (`czas|aplikacja|pakiet|powód`) |
| `input_text.guard_wiadomosc_<id>` | wiadomość na ekran telefonu (`czas_unix|tekst`) |
| `input_button.guard_dzwon_<id>` | „Znajdź telefon” – głośny alarm |
| `schedule.guard_noc_<id>`, `schedule.guard_lekcje_<id>`, `input_boolean.guard_harmonogram_<id>` | cisza nocna (blokada całego telefonu) i czas lekcji (blokada aplikacji) |
| `counter.guard_zadania_<id>`, `guard_prosby_<id>`, `guard_sabotaz_<id>` | liczniki do raportu tygodniowego |

Limity czasu: dodaj do listy `todo.guard_<id>` pozycję `pakiet|Nazwa|minuty` (np. `com.google.android.youtube|YouTube|60`). Bez trzeciego pola aplikacja jest blokowana całkowicie. Aplikacja wysyła przez webhook zdarzenia `request` (prośba o czas), `tamper` (ingerencja), `usage` (statystyki) i `location`; obsługują je automatyzacje (powiadomienia do rodziców, raport tygodniowy).

**EN** – Optional per-child helpers (`<id>`): location on/off and frequency, “where is the phone” button, lock-screen messages, bonus minutes, time requests, messages to the phone screen, find-my-phone alarm, night/lesson schedules and weekly counters. Time limits are `package|Name|minutes` items in `todo.guard_<id>`. The app sends `request`, `tamper`, `usage` and `location` events through the webhook; automations turn them into notifications, a `device_tracker` and a weekly report.

**Lokalizacja (`device_tracker`)** – zamiast przestarzałej usługi `device_tracker.see` (usunięta w HA 2027.5) użyj szablonowego `device_tracker` zasilanego tym samym webhookiem (HA 2026.6+). Przykład jest w `aurora_guard_template.yaml` (blok z `device_tracker:`; encja `device_tracker.guard_<id>`). / Instead of the deprecated `device_tracker.see` (removed in HA 2027.5) use a template `device_tracker` fed by the same webhook (HA 2026.6+); see the example block in `aurora_guard_template.yaml`.
