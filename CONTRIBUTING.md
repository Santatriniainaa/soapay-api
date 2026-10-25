# Contribuer

Merci de contribuer à SoaPay. Le projet reste un prototype de simulation : les changements ne doivent ni contacter un opérateur Mobile Money réel, ni traiter de vrais moyens de paiement, secrets, numéros ou données financières.

## Prérequis

- JDK 25 et Maven Wrapper pour compiler et tester.
- Docker Engine et Docker Compose v2 pour vérifier le profil `dev` avec PostgreSQL.
- Une copie à jour du dépôt et une branche de travail dédiée.

## Git Flow du dépôt

Les branches permanentes et leur fonction sont :

| Branche | Rôle | Modifications acceptées |
| --- | --- | --- |
| `main` | Historique des versions stables | Promotions validées depuis `staging` uniquement. |
| `staging` | Validation d’une version candidate | Promotions depuis `develop` pour recette et validation avant livraison. |
| `develop` | Intégration du prochain développement | Pull requests de branches `features/*`. |
| `features/<ticket>-<slug>` | Travail isolé sur une fonctionnalité ou correction | Créée depuis `develop`, fusionnée vers `develop` par pull request. |

Flux normal : `features/*` → `develop` → `staging` → `main`. N’effectuez pas de push direct sur les branches permanentes. Les promotions entre branches permanentes se font aussi par pull request, afin de conserver les contrôles CI et la revue.

Créer une branche de fonctionnalité :

```bash
git switch develop
git pull --ff-only origin develop
git switch -c features/ABC-123-refund-idempotency
git push --set-upstream origin features/ABC-123-refund-idempotency
```

Après les validations locales, poussez la branche et ouvrez une pull request vers `develop`. Une fois intégrée, promouvoir `develop` vers `staging` par pull request ; après validation de la recette, promouvoir `staging` vers `main`.

## Développement et tests

Avant d’ouvrir une pull request, exécutez :

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Si le changement touche à la persistance ou au profil `dev`, vérifiez également l’application avec PostgreSQL dans Compose :

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

La CI GitHub Actions exécute le workflow Maven sur les pushes `main`, `staging`, `develop` et `features/**`, ainsi que sur les pull requests ciblant les branches permanentes. La CI valide les changements ; les règles de protection et les destinations autorisées doivent aussi être configurées dans les paramètres GitHub du dépôt.

## Exigences de changement

- Respectez l’architecture ports/adapters : le domaine ne dépend pas de Spring, de JPA ou d’un fournisseur.
- Ajoutez ou adaptez des tests pour chaque règle métier, endpoint, contrôle d’accès ou migration modifiés.
- Maintenez l’isolation par client, l’idempotence et les invariants transactionnels.
- Pour tout changement de contrat API, mettez à jour les exemples, la documentation OpenAPI concernée et le README.
- Pour toute évolution de schéma, ajoutez une migration Flyway versionnée ; ne modifiez pas une migration déjà appliquée sur un environnement partagé.
- N’ajoutez aucune donnée réelle, aucun secret, ni aucune intégration opérateur. Utilisez exclusivement les mocks et données fictives.
- Évitez les changements incompatibles non documentés et signalez explicitement leur impact.

## Pull request

Une pull request doit :

- cibler la branche conforme au flux ci-dessus ;
- expliquer le problème, le comportement attendu et la solution ;
- résumer les changements API ou de base de données ;
- indiquer les commandes et tests exécutés, ainsi que les résultats ;
- signaler les risques, limites et étapes de déploiement éventuelles.

Attendez la réussite de la CI et l’approbation requise avant fusion. Préférez le squash merge pour les branches `features/*` afin de garder un historique d’intégration lisible ; conservez des promotions identifiables entre `develop`, `staging` et `main`.
