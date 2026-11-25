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
