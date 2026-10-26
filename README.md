# SoaPay

> **Prototype expérimental : simulation uniquement.** Aucun paiement réel n’est initié et aucun opérateur Mobile Money n’est contacté. N’utilisez pas de vrais numéros, identifiants, données personnelles ou données financières.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

SoaPay est une API REST de démonstration pour simuler des paiements et remboursements, tester l’idempotence, les transitions d’état et l’isolation par client.

## Documentation

- [Architecture métier](docs/ARCHITECTURE_METIER.md) : couches, flux, états et invariants.
- [Cahier de charge](docs/CAHIER_DE_CHARGE.md) : périmètre, rôles, routes et critères d’acceptation.
- [Déploiement Render + Neon](docs/DEPLOYMENT_RENDER_NEON.md) : procédure manuelle et variables de production.
- [Contribution](CONTRIBUTING.md)

## Démarrage rapide

Pré-requis : JDK 25. Pour l’option PostgreSQL, Docker Engine et Docker Compose v2.

### H2 local

Le profil `local` et H2 en mémoire sont utilisés par défaut. Les données disparaissent à l’arrêt.

```bash
./mvnw spring-boot:run
```

API : `http://localhost:8080`. Comptes de démonstration : `demo-client` / `change-me-client` et `demo-admin` / `change-me-admin`.

### PostgreSQL avec Docker Compose

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

Compose démarre PostgreSQL puis l’API en profil `dev`. Les données PostgreSQL persistent dans `postgres_data`. L’arrêt standard conserve les données :

```bash
docker compose down
```

Les valeurs d’exemple sont réservées au développement local. Ne les réutilisez pas dans un environnement partagé.

## Profils

| Profil | Persistance | Authentification |
| --- | --- | --- |
| `local` (défaut) | H2 mémoire | HTTP Basic de démonstration |
| `test` | H2 mémoire | Comptes de test fixes |
| `dev` | PostgreSQL | HTTP Basic configuré par variables d’environnement |
| `prod` | PostgreSQL | JWT/OIDC via `OAUTH_ISSUER_URI` ; fournisseurs simulés indisponibles |

Le profil `prod` ne rend pas le projet apte à traiter des paiements réels ou à être exposé publiquement.

## API

Les routes métier sont sous `/api/v1`. Les fournisseurs disponibles sont `MVOLA_MOCK`, `ORANGE_MONEY_MOCK` et `AIRTEL_MONEY_MOCK`.

| Opération | Route | Rôle |
| --- | --- | --- |
| Lister les fournisseurs | `GET /api/v1/providers` | Authentifié |
| Créer un paiement simulé | `POST /api/v1/apis` | `CLIENT` |
| Consulter/lister ses paiements | `GET /api/v1/apis/{reference}` ; `GET /api/v1/apis?page=0&size=20` | `CLIENT` |
| Annuler un paiement autorisé | `POST /api/v1/apis/{reference}/cancel` | `CLIENT` |
| Confirmer un paiement en attente | `POST /api/v1/apis/{reference}/confirm` | `ADMIN`, `TEST_OPERATOR` |
| Créer/lister un remboursement | `/api/v1/apis/{reference}/refunds` | `CLIENT` |
| Consulter un remboursement | `GET /api/v1/refunds/{refundReference}` | `CLIENT` |

Les listes utilisent `page=0` et `size=20` par défaut ; `size` est plafonné à 100. Création de paiement et remboursement exigent `Idempotency-Key`. Les réponses sont simulées (`simulated: true`).

Après le démarrage :

- Swagger UI : `http://localhost:8080/swagger-ui.html`
- Spécification OpenAPI : `http://localhost:8080/v3/api-docs`
- Santé : `http://localhost:8080/actuator/health`

## Charger le jeu de données de démonstration

Les exemples de paiement et de remboursement sont dans [`examples/payments/`](examples/payments/) et [`examples/refunds/`](examples/refunds/). Après le démarrage de Compose, ce script envoie les trois paiements puis un remboursement partiel pour chacun, via l’API authentifiée :

```bash
bash examples/load-demo-data.sh
```

Les clés d’idempotence sont stables : relancer le script ne crée pas de doublons. Les identifiants HTTP Basic sont lus depuis le conteneur API et les données insérées restent simulées.

Pour afficher toutes les tables, toutes leurs colonnes, les lignes des tables métier et l’historique Flyway :

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < examples/database/inspect.sql
```

PostgreSQL contient les tables `api_transactions`, `api_refunds` et `flyway_schema_history`. Flyway crée et versionne le schéma ; les endpoints de paiement et de remboursement créent les données métier. `docker compose down` conserve le volume `postgres_data`; `docker compose down -v` le supprime ainsi que toutes les données.

## Tests

```bash
./mvnw verify
```

La suite couvre le domaine, les cas d’usage, la validation, les mocks et la sécurité HTTP. Un test de persistance PostgreSQL avec Testcontainers est exécuté si Docker est disponible ; sinon il est ignoré.

## Sécurité et limites

HTTP Basic et les comptes en mémoire sont réservés aux profils non-production. En `prod`, le JWT doit fournir le sujet client dans `sub` et ses rôles dans `roles`. Ne commitez pas `.env` et ne publiez pas les ports ou comptes de démonstration. Les besoins HTTPS, gestion des secrets, audit et revue de sécurité restent à traiter avant tout déploiement partagé.

Le projet ne fournit ni connecteur opérateur, ni règlement financier, ni traitement asynchrone, ni audit métier. Voir le [cahier de charge](docs/CAHIER_DE_CHARGE.md) pour le périmètre complet.

## Licence

Voir [LICENSE](LICENSE).
