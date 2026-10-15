# Music AI

Application Android (Kotlin + Jetpack Compose) pour créer une musique et la mixer avec une IA.

## Fonctionnement

1. Tu écris un prompt (style, ambiance, tempo…).
2. Tu importes une voix (mp3, m4a, wav, ogg…) ou tu l'enregistres au micro (optionnel).
3. L'IA (Ollama, `gpt-oss:120b-cloud`) compose le morceau en JSON :
   tempo, tonalité, accords, batterie, volumes, reverb/delay de la voix, entrée de la voix.
4. Le moteur audio intégré (synthé basse / accords / mélodie / batterie) génère l'instrumentale,
   place la voix, applique les effets et un ducking automatique, puis produit un WAV.
5. Tu écoutes le résultat et tu l'exportes dans `Musique/MusicAI`.

L'URL et le modèle sont modifiables dans l'écran (section « Serveur Ollama »).

## Build

Ouvrir le dossier dans Android Studio (Koala ou plus récent, JDK 17) : il génère le wrapper Gradle
et télécharge les dépendances. Puis Run sur un appareil ou émulateur (Android 10+, minSdk 29).

## Notes

- Le trafic HTTP en clair est autorisé (`usesCleartextTraffic`) car le serveur Ollama est en `http://`.
  Si possible, passe par HTTPS ou un VPN : l'IP et le port sont exposés sur Internet sans authentification.
- Le mix est mono 44,1 kHz 16 bits.
