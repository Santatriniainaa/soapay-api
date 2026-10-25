# Cahier de charge

## 1. Présentation

SoaPay fournit une API REST versionnée permettant d’expérimenter un contrat de paiement Mobile Money sans appeler d’opérateur. Le périmètre livré est une simulation destinée au développement, aux tests et à l’apprentissage.

## 2. Objectifs

- Exposer des opérations cohérentes de création, consultation, annulation et confirmation de paiements simulés.
- Protéger les répétitions de requêtes par idempotence.
- Isoler les données par identité client et rôle.
- Simuler des remboursements partiels ou complets sans dépasser le montant payé.
- Persister les transactions et leurs remboursements avec des migrations reproductibles.
- Séparer les règles métier des détails HTTP, de la base et des mocks.

## 3. Acteurs et autorisations

| Acteur | Capacités |
| --- | --- |
| `CLIENT` | Créer et consulter ses paiements, annuler une transaction autorisée, créer et consulter ses remboursements, consulter les fournisseurs. |
| `ADMIN` | Consulter les fournisseurs et confirmer une transaction en attente. |
| `TEST_OPERATOR` | Mêmes autorisations API que `ADMIN` pour les opérations de confirmation et la consultation des fournisseurs. |
| Exploitant | Configurer les profils, la base, les identifiants locaux ou l’issuer OIDC ; consulter la santé du service. |

En `local`, `dev` et `test`, les rôles sont portés par des comptes HTTP Basic en mémoire. En `prod`, un JWT OIDC est requis ; `sub` identifie le client et le claim `roles` porte les rôles. L’accès aux données d’un autre client doit être refusé, y compris lors d’une consultation par référence.

## 4. Exigences fonctionnelles

### Paiements

- **F1 — Création :** accepter un fournisseur mock, un numéro fictif, un montant positif, une devise reconnue et une description facultative.
- **F2 — Idempotence :** exiger `Idempotency-Key` (1 à 128 caractères). Pour un même client, une clé associée au même contenu retourne le résultat antérieur ; un contenu différent produit `409 IDEMPOTENCY_KEY_REUSED`.
- **F3 — Simulation :** prendre en charge les résultats `SUCCESS`, `FAILED`, `PENDING` et `TIMEOUT`. `TIMEOUT` est représenté par un état `FAILED` et un code d’échec simulé.
- **F4 — Consultation et liste :** permettre au client de consulter une transaction qui lui appartient et de lister ses transactions avec pagination.
- **F5 — Annulation :** appliquer uniquement les transitions autorisées par le domaine.
- **F6 — Confirmation :** permettre à `ADMIN` ou `TEST_OPERATOR` de confirmer une transaction en attente ; refuser les états incompatibles.

### Fournisseurs

- **F7 — Catalogue :** exposer la liste et le détail des fournisseurs mock ainsi que leur disponibilité simulée.
- **F8 — Disponibilité :** signaler les mocks indisponibles en profil `prod` ; ne jamais remplacer cette simulation par un appel opérateur réel dans le périmètre actuel.

### Remboursements

- **F9 — Création :** permettre à un client de créer un remboursement pour une transaction réussie ou partiellement remboursée.
- **F10 — Idempotence :** exiger une clé et comparer la transaction, le montant et le motif lors d’une répétition.
- **F11 — Solde :** interdire un remboursement supérieur au solde restant ; sérialiser les opérations concurrentes sur la transaction.
- **F12 — Consultation :** lister les remboursements d’un paiement et consulter un remboursement appartenant au client.
- **F13 — Résultat :** traiter le remboursement de façon synchrone et mettre à jour le statut du paiement selon le montant total remboursé.

## 5. Contrat HTTP

Toutes les routes métier sont sous `/api/v1` et authentifiées.

| Méthode | Route | Rôle | Fonction |
| --- | --- | --- | --- |
| `GET` | `/api/v1/providers` | Authentifié | Lister les fournisseurs mock. |
| `GET` | `/api/v1/providers/{providerCode}` | Authentifié | Consulter un fournisseur. |
| `GET` | `/api/v1/providers/{providerCode}/health` | Authentifié | Consulter sa disponibilité simulée. |
| `POST` | `/api/v1/apis` | `CLIENT` | Créer un paiement simulé. |
| `GET` | `/api/v1/apis?page=0&size=20` | `CLIENT` | Lister les paiements du client. |
| `GET` | `/api/v1/apis/{reference}` | `CLIENT` | Consulter un paiement du client. |
| `POST` | `/api/v1/apis/{reference}/cancel` | `CLIENT` | Annuler un paiement si la transition est permise. |
| `POST` | `/api/v1/apis/{reference}/confirm` | `ADMIN`, `TEST_OPERATOR` | Confirmer un paiement en attente. |
| `POST` | `/api/v1/apis/{reference}/refunds` | `CLIENT` | Créer un remboursement simulé. |
| `GET` | `/api/v1/apis/{reference}/refunds?page=0&size=20` | `CLIENT` | Lister les remboursements du paiement. |
| `GET` | `/api/v1/refunds/{refundReference}` | `CLIENT` | Consulter un remboursement du client. |

Les listes ont une taille par défaut de 20 et plafonnée à 100. Les valeurs négatives de page sont ramenées à 0 ; les tailles inférieures à 1 sont ramenées à 1. L’état de santé `/actuator/health` et les routes OpenAPI/Swagger sont publics.

## 6. Règles et contraintes métier

- La clé d’idempotence est obligatoire et ne dépasse pas 128 caractères.
- Le numéro de paiement suit le format fictif `03[234][0-9]{7}`.
- Les montants sont strictement positifs, limités à deux décimales et douze chiffres entiers.
- La devise est un code à trois lettres majuscules reconnu comme devise ISO 4217 par le runtime.
- Les remboursements reprennent la devise du paiement et leur cumul ne peut dépasser le montant initial.
- Les statuts et transitions sont contrôlés par le domaine ; les transitions invalides retournent un conflit HTTP.
- Les réponses de paiement et remboursement signalent qu’elles sont simulées.

## 7. Exigences techniques et non fonctionnelles

- Java 25 et Spring Boot 4.1.2 ; build et tests via Maven Wrapper.
- H2 en mémoire pour `local` et `test`, PostgreSQL pour `dev` et `prod`.
- Évolutions de schéma versionnées avec Flyway ; schéma validé par Hibernate.
- HTTP Basic limité aux profils non-production ; JWT/OIDC pour `prod`.
- Aucun secret réel, numéro réel ou donnée financière réelle dans les exemples, tests ou dépôts.
- Docker Compose fournit un environnement de développement PostgreSQL ; les tests PostgreSQL utilisent Testcontainers et peuvent être ignorés sans Docker.

## 8. Critères d’acceptation

- `./mvnw verify` termine avec succès dans un environnement de test pris en charge.
- Un client peut créer puis relire son paiement simulé ; un autre client ne peut pas accéder à cette référence.
- Une répétition identique avec la même clé ne crée pas de doublon ; une clé réutilisée avec un contenu différent est rejetée.
- Les résultats de simulation mappent aux statuts attendus, y compris `TIMEOUT` vers `FAILED`.
- Les remboursements idempotents sont retournés sans double débit simulé et le total ne dépasse jamais le paiement, y compris pour des demandes concurrentes.
- Les accès de confirmation respectent le rôle et l’état de la transaction.
- Les migrations s’appliquent sur H2 et, lorsque Docker est disponible, sur PostgreSQL Testcontainers.

## 9. Hors périmètre

Les paiements et remboursements réels, les intégrations opérateur, la gestion de fonds, les traitements asynchrones, les webhooks, l’audit métier, la conformité réglementaire et la préparation à une exposition publique ne sont pas inclus. Ces sujets nécessitent une définition et une revue distinctes avant toute évolution.