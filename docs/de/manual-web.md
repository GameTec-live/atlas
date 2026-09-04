# Handbuch für die Atlas Web-UI

Die Atlas Web-UI besteht aus verschiedenen Seiten, die die unterschiedlichen Funktionen des Systems bereitstellen. Im Folgenden wird jede Seite ausführlich beschrieben.

## Dashboard

Das Dashboard ist die Standardansicht direkt nach der Anmeldung. Es bietet einen Überblick über den aktuellen Zustand und besteht aus verschiedenen Widgets.

- Fahrer und Status
    
    Dieses Widget zeigt alle derzeit aktiven Fahrer, ihren Status und ihren aktuellen Auftrag an.
- Als Nächstes fällige Wartungen

    Dieses Widget zeigt die Fahrzeuge, deren Wartung als Nächstes fällig ist, nach Dringlichkeit sortiert an.
- Karte

    Dieses kleine Karten-Widget zeigt laufend aktualisierte Positionen an.
- Gefahrene Kilometer

    Dieses Widget zeigt eine aus den Fahrtenbucheinträgen berechnete Statistik der in den letzten fünf Tagen gefahrenen Kilometer.
- Nicht zugewiesene Aufträge

    Dieses Widget bietet einen Überblick über alle nicht zugewiesenen Aufträge. Durch Klicken auf einen Auftrag wird die Zuweisungsansicht geöffnet. Beim Löschen wird ein Auftrag dauerhaft entfernt. Durch Klicken auf "Neuer Auftrag" wird die Ansicht zum Erstellen eines neuen Auftrags geöffnet.

## Karte

Diese Seite besteht aus einer großen Karte. Jeder derzeit aktive Fahrer wird mit seinem aktuellen Status und Standort angezeigt. Wenn Sie den Mauszeiger über einen Fahrer bewegen, erscheint ein Tooltip mit weiteren Einzelheiten.
Unten rechts befindet sich eine Schaltfläche für einen neuen Auftrag, über die Sie schnell einen Auftrag erstellen können.

## Aufträge

Diese Seite listet alle jemals bearbeiteten oder zugewiesenen Aufträge auf. Durch Klicken auf einen Auftrag können Sie dessen Details einsehen. Über das Download-Symbol oben rechts kann ein Export heruntergeladen werden.

Die Seitenleiste für nicht zugewiesene Aufträge zeigt alle Aufträge, die noch einem Fahrer zugewiesen werden müssen. Nur nicht zugewiesene Aufträge können gelöscht werden. Wenn Sie auf einen nicht zugewiesenen Auftrag klicken, wird die Seite zur Auftragszuweisung mit weiteren Einzelheiten geöffnet. Oben rechts finden Sie erneut eine Exportoption sowie eine "ausgeblendete Seite", über die sich die Liste der nicht zugewiesenen Aufträge einfach teilen lässt.

### Auftrag erstellen/zuweisen

Diese Seite kann entweder durch Auswahl eines nicht zugewiesenen Auftrags oder durch Klicken auf "Neuer Auftrag" aufgerufen werden.

Wenn die Seite über einen nicht zugewiesenen Auftrag geöffnet wurde, sind das Feld "Von" und möglicherweise auch das Feld "Nach" bereits ausgefüllt. Das Feld "Von" kann bei nicht zugewiesenen Aufträgen nicht bearbeitet werden.

Auf dieser Seite können Sie einen Startort, ein optionales Ziel und einen Fälligkeitstermin eingeben.
Standardmäßig entspricht der Fälligkeitstermin dem aktuellen Zeitpunkt.
Zusätzlich kann eine Notiz mit weiteren Informationen für den Fahrer hinzugefügt werden.

Sobald sowohl "Von" als auch "Nach" eingegeben wurden, wird die vom Routingserver für diesen Auftrag ermittelte Route eingezeichnet.

> [!WARNING] 
> Es wird dringend empfohlen, immer sowohl "Von" als auch "Nach" einzugeben. Sie können "Nach" zwar leer lassen, dadurch funktionieren jedoch Routenplanung, Zeitprognose und Kandidatenberechnung für diesen und alle nachfolgenden Aufträge nicht mehr, bis ein Ziel festgelegt oder der Auftrag abgeschlossen wurde.

Wenn die Seite über einen nicht zugewiesenen Auftrag geöffnet wurde, können Sie Änderungen jederzeit speichern und die Seite verlassen. Wenn die Seite über die Schaltfläche "Neuer Auftrag" geöffnet wurde, muss der Auftrag entweder zugewiesen oder als nicht zugewiesen erstellt werden.

Auf der rechten Seite werden Fahrerkandidaten angezeigt. Anhand des Standorts und des Auftragsrückstands jedes Fahrers berechnet das System, welcher Fahrer am besten für den Auftrag geeignet ist. Der ideale Kandidat wird ganz oben aufgeführt, zusammen mit einer geschätzten Ankunftszeit und weiteren Einzelheiten dazu, wie die Rangfolge zustande kam.

Falls der Benutzer die Systemempfehlung übersteuern möchte, weil der gewünschte Kandidat nicht aufgeführt ist oder aus einem anderen Grund, werden unter "Alle Fahrer" sämtliche möglichen Fahrer angezeigt.

Durch das Zuweisen eines Auftrags wird nahezu sofort eine Benachrichtigung an den betreffenden Fahrer gesendet und die Datensätze werden aktualisiert.

## Fuhrpark

Auf dieser Seite können Sie Ihren Fuhrpark verwalten. Jedes Fahrzeug sollte hier registriert werden.

Kilometerstand und Kraftstoffstand müssen nicht manuell aktuell gehalten werden, da das System diese Werte nach Möglichkeit selbst aktualisiert.

Die Fuhrparkverwaltung bietet eine einfache Möglichkeit, Wartungseinträge nachzuverfolgen. Klicken Sie auf das Schraubenschlüssel-Symbol, um einen neuen Eintrag zu erstellen. Klicken Sie auf einen Eintrag unter "Letzte Wartung" oder "Nächste Wartung", um den Verlauf aufzurufen.

Der Fingerabdruck dient dazu, das Fahrzeug gegenüber der mobilen Anwendung zu identifizieren. Er muss nicht manuell festgelegt werden. Wenn sich ein Administrator zum ersten Mal in der mobilen Anwendung anmeldet und ein neues Fahrzeug verbindet, erscheint ein Dialog, über den der Fingerabdruck des neuen Fahrzeugs einem Fahrzeug in der Fuhrparkdatenbank zugeordnet werden kann.

> [!TIP]
> Nehmen Sie ein Telefon und melden Sie sich als Administrator an. Nachdem Sie die Fahrzeuge in der Web-UI angelegt haben, gehen Sie zu jedem Fahrzeug, schließen das Telefon an, stellen eine Android-Auto-Verbindung her und ordnen das Fahrzeug seinem Fingerabdruck zu. Einfach und schnell.

## Fahrtenbuch

Auf der Fahrtenbuchseite können Sie das Fahrtenbuch jedes Fahrers einsehen und als CSV-Datei herunterladen.

Ein Fahrtenbucheintrag kann nicht gelöscht, sondern nur für ungültig erklärt werden. Ein ungültiger Eintrag wird ausgegraut und von Berechnungen ausgeschlossen.

Nach jeder Schicht in der mobilen Anwendung wird ein Fahrtenbucheintrag erstellt.

## Einstellungen

### Kurznamen

Um die Adresseingabe für wiederkehrende Kunden zu erleichtern, bietet Atlas Kurznamen für Adressen.

Klicken Sie einfach in die Zelle "Neuer Kurzname" und geben Sie den Kurznamen sowie anschließend die zugehörige Adresse ein. Die Adresssuche löst den Kurznamen danach in die tatsächliche Adresse auf.

Um einen Kurznamen zu löschen, klicken Sie einfach in die Zelle und entfernen den Kurznamen.

### Allgemein

Diese Seite enthält die zentralen Systemeinstellungen, von denen die meisten bereits im Einrichtungsassistenten konfiguriert wurden.

- Benutzer
    Erstellen Sie hier Konten für zusätzliche Administratoren sowie für Ihre Fahrer und Disponenten. Sie melden sich mit Benutzername und Passwort an.
- Allgemeine Einstellungen
    Konfigurieren Sie hier das Verhalten und die allgemeinen Einstellungen. Passen Sie das auf Mobilgeräten angezeigte Logo an, legen Sie die maximale Anzahl gleichzeitig verfügbarer Disponenten sowie den Preis pro Kilometer fest.

    Die Standardsprache wird nur für Routenführung und Navigation verwendet, und auch nur dann, wenn keine andere Sprache angegeben wurde. Normalerweise wird die Sprache des Endgeräts verwendet (Telefon, Web-UI).
- System
    Wenn Atlas über Docker oder nicht auf AtlasOS bereitgestellt wurde, zeigt diese Karte nur einige Informationen zur API-Version.

    Diese Karte zeigt detaillierte Informationen und bietet Optionen zur Systemverwaltung.

    Unter "Detaillierte Informationen" können Sie verschiedene Angaben wie exakte Versionsnummern und den Systemzustand einsehen.

    Unter "Verbindungen verwalten" können Sie die Netzwerkverbindung und die Optionen für den Fernzugriff verwalten.
    - Netzwerkverbindung

        Betreiben Sie eine erweiterte Installation? Haben Sie besondere Netzwerkanforderungen? Konfigurieren Sie hier statische Adressen und weitere Einstellungen für die Netzwerkschnittstelle.

        Die meisten Benutzer müssen in diesem Menü nichts ändern.

    - Fernzugriff

        Atlas ist dafür ausgelegt und in der Lage, vollständig offline zu arbeiten. Möglicherweise möchten Sie Atlas jedoch auch außerhalb Ihres Netzwerks verfügbar machen.

        AtlasOS bietet verschiedene Einstellungen, um einen solchen Fernzugriff zu ermöglichen.

        Wenn Sie Atlas über Tailscale oder Cloudflare Tunnels verfügbar machen möchten, startet AtlasOS diese Dienste automatisch, sobald gültige Anmeldedaten vorliegen.

        > [!NOTE]  
        > Bei Cloudflare Tunnels müssen Sie den Tunnel auf https://localhost richten.

        Wenn Sie stattdessen einfach Portweiterleitung auf Ihrem Router aktivieren oder mehrere externe Adressen auf Ihre Installation verweisen lassen, vergessen Sie nicht, diese Adressen den zusätzlichen externen Ursprüngen hinzuzufügen. Andernfalls kann die Authentifizierung fehlschlagen.
    
    Abschließend gibt es die Möglichkeit, die Zeitzone des Systems zu ändern (diese wirkt sich auf zeitgesteuerte Aktionen wie das Zurücksetzen der Fahrerrolle aus) sowie das Gerät auszuschalten, neu zu starten oder auf Werkseinstellungen zurückzusetzen.
- Kartendaten
    Laden Sie einen oder mehrere Datensätze herunter, die für Routenführung, Suche und Karten verwendet werden. Beachten Sie, dass Download und Verarbeitung längere Zeit dauern können. Sie können die Einrichtung abschließen, die Seite verlassen oder später zurückkehren. Der Download wird im Hintergrund verarbeitet; Atlas ist jedoch erst vollständig funktionsfähig, nachdem mindestens ein Datensatz heruntergeladen und initialisiert wurde.
