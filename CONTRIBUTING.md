# Contribuer

Les contributions doivent préserver le périmètre exclusivement simulé : aucun appel vers des opérateurs réels, aucun secret et aucune donnée financière réelle.

1. Créez une branche dédiée.
2. Ajoutez des tests pour toute règle ou endpoint modifié.
3. Vérifiez `./mvnw verify` et le format de réponse des erreurs.
4. Documentez les changements API incompatibles dans le README.
5. Ouvrez une pull request avec le contexte, les tests et les éventuels risques.

Respectez l'architecture ports/adapters : le domaine ne dépend ni de Spring, ni de JPA, ni d'un fournisseur.
