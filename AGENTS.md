# AGENTS.md — iLoppis LoppisKassan (Desktop Java)

## Syfte
Detta dokument beskriver hur kodagenter (”agents”) ska arbeta i detta repo. Målet är konsekventa, säkra och reproducerbara förändringar.

## Snabbstart (måste följas)
1. **Installera lokal OpenAPI‑klient** innan build:
   ```bash
   make install-client
   make build
   make run
   make verify
   make security
   ```
