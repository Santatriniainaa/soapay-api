# Déployer l’API sur Render avec Neon

Ce guide déploie le conteneur de l’API sur Render et utilise Neon comme PostgreSQL managé. Il concerne l’hébergement du prototype, pas la mise en service de paiements réels.

> **Limite fonctionnelle :** le profil `prod` désactive les fournisseurs mock et le dépôt ne contient aucun adaptateur Mobile Money réel. L’API peut démarrer, se connecter à Neon et authentifier des JWT, mais la création de paiement retourne `503 PROVIDER_UNAVAILABLE`. Ne présentez pas ce déploiement comme un service financier ou une passerelle de paiement.

## 1. Préparer la branche

Travaillez sur la branche de fonctionnalité, puis ouvrez une pull request vers `develop`. Après validation, faites promouvoir le changement vers `staging`, puis `main` conformément au [Git Flow](../CONTRIBUTING.md). Configurez Render pour déployer `main`, jamais une branche de fonctionnalité.

```bash
git switch features/render-neon-deployment
git pull --ff-only
./mvnw --batch-mode --no-transfer-progress verify
```

Une fois le code fusionné à `main`, Render pourra déployer les changements de cette branche.

## 2. Créer la base Neon

1. Dans la Neon Console, créez un projet PostgreSQL et choisissez une région proche de la région Render prévue.
2. Utilisez la branche de base du projet pour l’environnement de production ; ne reliez pas Render à une branche Neon de développement éphémère.
3. Créez ou sélectionnez une base et un rôle dédiés à l’application. Gardez le mot de passe dans un gestionnaire de secrets.
4. Cliquez sur **Connect** et sélectionnez la branche, la base et le rôle.
5. Activez **Connection pooling** et copiez les détails de connexion. L’hôte doit correspondre à l’endpoint pooled Neon, généralement suffixé par `-pooler`.
6. Notez séparément l’hôte, le nom de base, le rôle et le mot de passe. Ne mettez jamais le mot de passe dans Git, dans ce document ou dans une capture partagée.

La configuration Spring attend une URL JDBC et des identifiants séparés :

```text
DATABASE_URL=jdbc:postgresql://<NEON_POOLER_HOST>/<NEON_DATABASE>?sslmode=require
DATABASE_USERNAME=<NEON_ROLE>
DATABASE_PASSWORD=<NEON_PASSWORD>
DATABASE_POOL_MAX_SIZE=5
```

Remplacez les valeurs entre chevrons dans Render. Le protocole `jdbc:postgresql` et `sslmode=require` sont nécessaires pour pgJDBC et TLS vers Neon. N’ajoutez pas `jdbc:` à la valeur HTTP `postgresql://` copiée telle quelle : reconstruisez l’URL comme ci-dessus en utilisant l’hôte pooled fourni par Neon.

## 3. Configurer Keycloak OIDC

Le profil `prod` n’expose pas HTTP Basic ni les comptes de démonstration. Il exige un issuer Keycloak accessible publiquement en HTTPS et des JWT signés. L’application valide la signature, l’issuer et l’audience ; elle lit les rôles dans `realm_access.roles` et dans `resource_access.<KEYCLOAK_API_CLIENT_ID>.roles`. Le claim historique `roles` reste également accepté.

### 3.1 Créer le realm et le client ressource API

1. Dans la console d’administration Keycloak, créez un realm dédié, par exemple `soapay-prod`. N’utilisez pas le realm `master` pour l’application.
2. Dans **Clients → Create client**, choisissez **OpenID Connect**, puis créez le client `soapay-api`.
3. Ce client représente l’audience et héberge les rôles de l’API. SoaPay est un resource server : il ne fait pas de login Keycloak et ne nécessite pas de secret client pour vérifier les JWT.
4. Dans **Clients → soapay-api → Capability config**, désactivez les flows inutiles pour cette audience : Standard flow, Direct access grants et Service accounts.
5. Dans **Clients → soapay-api → Roles**, créez les rôles client `CLIENT`, `ADMIN` et `TEST_OPERATOR`.
6. Créez un client OIDC séparé pour le front-end ou l’application qui connectera les utilisateurs. Pour un navigateur/public client, activez Standard flow, désactivez Client authentication et exigez PKCE `S256`. Limitez les redirect URIs et web origins aux domaines nécessaires.

### 3.2 Ajouter l’audience et les rôles API aux access tokens

1. Dans **Client scopes**, créez un scope dédié, par exemple `soapay-api-audience`.
2. Dans ce scope, ajoutez un mapper de type **Audience**.
3. Définissez **Included Client Audience** à `soapay-api` et activez **Add to access token**. L’audience doit figurer dans l’access token, pas uniquement dans l’ID token.
4. Vérifiez que le mapper des rôles client inclut les rôles de `soapay-api` dans l’access token. Keycloak les place habituellement sous `resource_access.soapay-api.roles`.
5. Attachez `soapay-api-audience` comme client scope **Default** au client de connexion des utilisateurs. L’access token doit alors contenir `"aud": ["soapay-api", ...]`.

### 3.3 Créer des utilisateurs et leur attribuer des rôles

1. Dans **Users → Add user**, créez un utilisateur de test. Dans **Credentials**, définissez son mot de passe et désactivez **Temporary** si vous souhaitez un compte de test durable.
2. Depuis **Users → <utilisateur> → Role mapping → Assign role**, sélectionnez **Filter by clients**, choisissez `soapay-api`, puis attribuez `CLIENT`.
3. Pour les opérations d’administration, créez un compte séparé auquel vous attribuez `ADMIN` et/ou `TEST_OPERATOR`. Ne donnez pas ces rôles au compte client habituel.
4. Connectez l’utilisateur par Authorization Code + PKCE, puis inspectez localement le payload de l’access token. Il doit ressembler à ceci :

   ```json
   {
     "iss": "https://<KEYCLOAK_HOST>/realms/soapay-prod",
     "sub": "<KEYCLOAK_USER_ID_STABLE>",
     "aud": ["soapay-api", "login-client"],
     "resource_access": {
       "soapay-api": {
         "roles": ["CLIENT"]
       }
     }
   }
   ```

   `sub` est l’identifiant utilisé par l’API pour isoler les données du client. Ne collez jamais un véritable access token dans Git, un ticket ou un log public.

### 3.4 Configurer l’issuer

L’issuer est l’URL HTTPS exacte du realm, par exemple `https://<KEYCLOAK_HOST>/realms/soapay-prod`. Copiez le claim `iss` du token ou vérifiez `<issuer>/.well-known/openid-configuration`. N’utilisez ni `localhost`, ni un nom réseau interne Docker, ni une URL d’administration inaccessible depuis Render.

Sans issuer valide ou si Keycloak n’est pas accessible au démarrage, l’application en profil `prod` ne démarre pas. Un jeton sans `aud` contenant `soapay-api` est rejeté en `401`; un jeton valide sans le rôle requis reçoit `403`.

## 4. Créer le Web Service Render

1. Dans le Render Dashboard, cliquez sur **New > Web Service** et connectez le dépôt GitHub.
2. Choisissez le dépôt SoaPay et la branche `main`.
3. Choisissez le runtime **Docker** et le `Dockerfile` à la racine du dépôt.
4. Choisissez une région aussi proche que possible de la région Neon.
5. Laissez la commande Docker vide pour utiliser l’`ENTRYPOINT` du Dockerfile.
6. Choisissez un plan adapté à la charge et aux besoins de disponibilité. Le plan gratuit peut suspendre le service après inactivité ; le premier appel suivant peut être lent.
7. Dans **Advanced**, configurez le health check path `/actuator/health`.
8. Configurez l’auto-déploiement **After CI Checks Pass** si cette option est disponible, ou désactivez l’auto-déploiement et déployez manuellement après validation de CI.

Render fournit la variable `PORT` et attend que le serveur écoute sur cette valeur et sur `0.0.0.0`. L’application la privilégie désormais ; en local, `SERVER_PORT` continue de définir le port si `PORT` n’est pas fourni. Ne forcez pas `PORT=8080` dans Render sans configurer le même port dans le service.

## 5. Configurer les variables Render

Dans le service, ouvrez **Environment** et ajoutez ces variables comme secrets ou valeurs privées :

| Variable | Valeur |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | URL JDBC de l’endpoint pooler Neon avec `?sslmode=require` |
| `DATABASE_USERNAME` | Rôle Neon dédié |
| `DATABASE_PASSWORD` | Mot de passe du rôle Neon |
| `DATABASE_POOL_MAX_SIZE` | `5` pour commencer, à adapter au plan et à la capacité Neon |
| `OAUTH_ISSUER_URI` | `https://<KEYCLOAK_HOST>/realms/soapay-prod` |
| `OIDC_AUDIENCE` | `soapay-api` ; doit apparaître dans `aud` |
| `KEYCLOAK_API_CLIENT_ID` | `soapay-api` ; identifie la section client dans `resource_access` |

Ne configurez pas `DEMO_CLIENT_PASSWORD` ou `DEMO_ADMIN_PASSWORD` comme solution de sécurité en production ; ces comptes n’existent pas sous le profil `prod`. `API_SIMULATION_RESULT` n’active pas les mocks en production.

Dans Keycloak, `OAUTH_ISSUER_URI` identifie le realm, `OIDC_AUDIENCE` vérifie que le jeton est destiné à SoaPay, et `KEYCLOAK_API_CLIENT_ID` permet de sélectionner les rôles client de l’API. Ces valeurs ont des fonctions différentes et doivent être toutes renseignées.

L’application exécute Flyway au démarrage et applique les migrations présentes dans `src/main/resources/db/migration`. Pour déployer des changements de schéma, assurez-vous que les migrations restent compatibles avec les versions d’application qui se chevauchent pendant un déploiement sans interruption.

## 6. Déployer et vérifier

1. Lancez le premier déploiement Render et suivez **Events** et **Logs**.
2. Confirmez que les logs indiquent le profil `prod`, une connexion PostgreSQL à l’hôte Neon et une application réussie des migrations Flyway.
3. Vérifiez `https://<SERVICE>.onrender.com/actuator/health`. La réponse attendue contient `"status":"UP"`.
4. Dans Neon SQL Editor, vérifiez les migrations :

   ```sql
   SELECT version, description, success
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```

5. Vérifiez qu’un endpoint protégé renvoie `401` sans bearer token.
6. Avec un access token Keycloak valide contenant `soapay-api` dans `aud` et `CLIENT` dans `resource_access.soapay-api.roles`, la route de lecture des fournisseurs doit répondre `200`.
7. Un token signé par le bon realm mais sans audience `soapay-api` doit répondre `401`; un token avec la bonne audience mais sans rôle autorisé doit répondre `403`.
8. Vérifiez qu’une création de paiement renvoie `503 PROVIDER_UNAVAILABLE` dans ce prototype en profil `prod`. C’est le comportement prévu tant qu’aucun fournisseur autorisé et réellement intégré n’existe.

Pour tester un endpoint de lecture protégé, obtenez un access token via le flux Authorization Code/PKCE de votre client, puis stockez-le temporairement dans la variable locale `ACCESS_TOKEN` :

```bash
curl --fail-with-body \
   --header "Authorization: Bearer ${ACCESS_TOKEN}" \
   https://<SERVICE>.onrender.com/api/v1/providers
```

Ne créez pas un token de production en activant le password grant uniquement pour faciliter ce test. Utilisez le flux PKCE ou un outil client OIDC approuvé par votre organisation.

## 7. Avant toute exposition publique

- Protégez `main` par pull request, CI obligatoire et approbation dans les règles GitHub.
- Restreignez l’accès au service, aux logs et aux secrets Render et Neon.
- Utilisez un compte Neon dédié avec privilèges minimaux et activez les options de sauvegarde/rétention adaptées.
- Désactivez OpenAPI et Swagger en production ; cette configuration est maintenant appliquée par `application-prod.yml`.
- Vérifiez que l’issuer OIDC, les audiences et les rôles correspondent à votre modèle d’identité.
- Ajoutez une revue de sécurité, un audit métier, des alertes, une politique de sauvegarde/restauration et un plan d’incident avant un usage partagé.
- N’activez jamais des paiements réels en réutilisant les mocks ou les identifiants de démonstration.

## Documentation fournisseur

- [Render : Web Services](https://render.com/docs/web-services)
- [Render : Docker](https://render.com/docs/docker)
- [Render : Health Checks](https://render.com/docs/health-checks)
- [Render : Environment Variables](https://render.com/docs/environment-variables)
- [Neon : Connect from any application](https://neon.com/docs/connect/connect-from-any-app)
- [Keycloak : Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Keycloak : Securing Applications and Services Guide](https://www.keycloak.org/docs/latest/securing_apps/)