"""Textos compartidos con Android y etiquetas específicas de NVDA."""
import json
from pathlib import Path

STRINGS=json.loads(Path(__file__).with_name('openevv_es_ui.json').read_text(encoding='utf8'))
# Each row follows the same five languages as the engine's interface.
EXTRA={
 'symbolPronunciation':('Punctuation and symbol pronunciation (NVDA)','Verbalización de símbolos y signos de puntuación (NVDA)','Satzzeichen und Symbole vorlesen (NVDA)','Prononciation des symboles et de la ponctuation (NVDA)','Pronuncia di simboli e punteggiatura (NVDA)'),
 'symbolShared':('Uses the current NVDA profile setting, shared with other synthesizers. Apply or OK saves; Cancel discards. Customize individual symbols in NVDA’s Punctuation/symbol pronunciation dialog.','Usa el ajuste del perfil actual de NVDA, compartido con otros sintetizadores. Aplicar o Aceptar guarda; Cancelar descarta. Personaliza cada signo en el diálogo Pronunciación de símbolos de NVDA.','Verwendet die Einstellung des aktuellen NVDA-Profils, auch für andere Synthesizer. Anwenden oder OK speichert; Abbrechen verwirft. Einzelne Zeichen im NVDA-Dialog Symbolaussprache anpassen.','Utilise le réglage du profil NVDA actuel, partagé avec les autres synthétiseurs. Appliquer ou OK enregistre ; Annuler abandonne. Personnalisez chaque signe dans le dialogue Prononciation des symboles de NVDA.','Usa l’impostazione del profilo NVDA corrente, condivisa con gli altri sintetizzatori. Applica o OK salva; Annulla scarta. Personalizza ogni segno nel dialogo Pronuncia simboli di NVDA.'),
 'prediction':('Predict phrase boundaries','Predecir límites de frases','Satzgrenzen vorhersagen','Prédire les limites des phrases','Prevedi i confini delle frasi'),
 'export':('Export WAV with saved settings','Exportar WAV con ajustes guardados','WAV mit gespeicherten Einstellungen exportieren','Exporter en WAV avec les réglages enregistrés','Esporta WAV con le impostazioni salvate'),
 'cancel':('Cancel export','Cancelar exportación','Export abbrechen','Annuler l’exportation','Annulla esportazione'),
 'exported':('WAV exported','WAV exportado','WAV exportiert','WAV exporté','WAV esportato'),
 'failed':('Export failed','Error de exportación','Export fehlgeschlagen','Échec de l’exportation','Esportazione non riuscita'),
 'cancelled':('Export cancelled','Exportación cancelada','Export abgebrochen','Exportation annulée','Esportazione annullata'),
 'tags':('Allow engine annotations (advanced)','Permitir anotaciones del motor (avanzado)','Motoranweisungen erlauben (erweitert)','Autoriser les annotations du moteur (avancé)','Consenti annotazioni del motore (avanzato)'),
 'panel':('OpenEVV: voice and sound','OpenEVV: voz y sonido','OpenEVV: Stimme und Klang','OpenEVV : voix et son','OpenEVV: voce e suono'),
 'select':('Select OpenEVV in Speech first.','Selecciona OpenEVV primero en Voz.','Zuerst OpenEVV unter Sprache auswählen.','Sélectionnez OpenEVV dans Parole.','Seleziona prima OpenEVV in Voce.'),
 'basic':('Voices, rate, pitch, inflection and volume: NVDA Speech settings.','Voces, velocidad, tono, entonación y volumen: ajustes de Voz de NVDA.','Stimmen, Tempo, Tonhöhe, Intonation und Lautstärke: NVDA-Sprachausgabe.','Voix, débit, hauteur, intonation et volume : réglages Parole de NVDA.','Voci, velocità, tono, intonazione e volume: impostazioni Voce di NVDA.'),
 'break':('Explicit NVDA pauses (%)','Pausas explícitas de NVDA (%)','Explizite NVDA-Pausen (%)','Pauses explicites NVDA (%)','Pause esplicite NVDA (%)'),
 'lockRate':('Keep configured rate','Mantener la velocidad configurada','Eingestelltes Tempo beibehalten','Conserver le débit configuré','Mantieni la velocità configurata'),
 'lockPitch':('Keep configured pitch','Mantener el tono configurado','Eingestellte Tonhöhe beibehalten','Conserver la hauteur configurée','Mantieni il tono configurato'),
 'lockVolume':('Keep configured volume','Mantener el volumen configurado','Eingestellte Lautstärke beibehalten','Conserver le volume configuré','Mantieni il volume configurato'),
 'fixes':('Normalize text','Normalizar texto','Text normalisieren','Normaliser le texte','Normalizza il testo'),
 'plain':('No level enhancement','Sin mejora de nivel','Ohne Pegelanpassung','Sans correction de niveau','Senza correzione del livello'),
 'left':('Left','Izquierda','Links','Gauche','Sinistra'),
 'center':('Center','Centro','Mitte','Centre','Centro'),
 'right':('Right','Derecha','Rechts','Droite','Destra'),
 'balance':('TTS balance','Balance del TTS','TTS-Balance','Balance TTS','Bilanciamento TTS'),
 'awake':('Keep audio output awake (global NVDA setting)','Mantener la salida de audio activa (ajuste global de NVDA)','Audioausgabe aktiv halten (globale NVDA-Einstellung)','Maintenir la sortie audio active (réglage global NVDA)','Mantieni attiva l’uscita audio (impostazione globale NVDA)'),
 'noise':('White noise intensity (%)','Intensidad de ruido blanco (%)','Weißes Rauschen (%)','Intensité du bruit blanc (%)','Intensità del rumore bianco (%)'),
 'off':('Off','Desactivado','Aus','Désactivé','Disattivato'),
 'nativeText':('Speech dictionaries, individual symbol pronunciations and CLDR emoji descriptions are customized in NVDA and applied before the engine.','Los diccionarios de voz, la pronunciación individual de cada símbolo y las descripciones CLDR de emojis se personalizan en NVDA y se aplican antes del motor.','NVDA-Sprachwörterbücher, die Aussprache einzelner Symbole und CLDR-Emoji-Beschreibungen werden in NVDA angepasst und vor dem Motor angewendet.','Les dictionnaires vocaux, la prononciation de chaque symbole et les descriptions CLDR des emojis sont personnalisés dans NVDA et appliqués avant le moteur.','I dizionari vocali, la pronuncia di ogni simbolo e le descrizioni CLDR delle emoji si personalizzano in NVDA e si applicano prima del motore.'),
 'test':('Preview saved settings','Probar ajustes guardados','Gespeicherte Einstellungen testen','Tester les réglages enregistrés','Prova le impostazioni salvate'),
 'preview':('Hello. This is OpenEVV. Verification code 492801.','Hola. Esta es la voz de OpenEVV. Código de verificación 492801.','Hallo. Dies ist OpenEVV. Bestätigungscode 492801.','Bonjour. Voici OpenEVV. Code de vérification 492801.','Ciao. Questa è la voce di OpenEVV. Codice di verifica 492801.'),
}

EXTRA.update(json.loads(Path(__file__).with_name('openevv_es_extra.json').read_text(encoding='utf8')))

def currentLocale():
    try:
        from languageHandler import getLanguage
        return getLanguage()
    except ImportError:
        return 'en'

def uiText(key):
    return translator(currentLocale())(key)

def translator(locale):
    language=(locale or 'en').split('_')[0].split('-')[0]
    if language not in STRINGS: language='en'
    def text(key):
        if key in EXTRA: return EXTRA[key][('en','es','de','fr','it').index(language)]
        return STRINGS[language].get(key,STRINGS['en'].get(key,key))
    return text
