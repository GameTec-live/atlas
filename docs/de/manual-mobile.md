# Handbuch für die Atlas App

Die Atlas App wird von Fahrern und Disponenten während einer Schicht verwendet. Sie zeigt Live-Standorte an, verwaltet Aufträge, bietet Navigation und erstellt am Ende der Schicht den Fahrtenbucheintrag. Die App kann auf einem Android-Telefon und, während das Telefon mit einem kompatiblen Fahrzeug verbunden ist, über Android Auto verwendet werden.

## Vor der ersten Schicht

Stellen Sie vor der Verwendung der App sicher, dass:

- die Atlas App auf dem Telefon installiert ist
- ein Administrator ein Benutzerkonto für Sie erstellt hat
- der Atlas-Server vollständig konfiguriert ist und Kartendaten installiert sind
- das Telefon den Atlas-Server entweder über dasselbe Netzwerk oder über die konfigurierte Fernzugriffsmethode erreichen kann.

Die Serveradresse kann automatisch festgelegt werden, indem Sie den während des Atlas-Einrichtungsassistenten angezeigten Kopplungs-QR-Code scannen. Sie kann auch manuell auf dem Anmeldebildschirm eingegeben werden.

## Verbindung herstellen und anmelden

1. Öffnen Sie die Atlas App.
2. Prüfen Sie die Serveradresse am unteren Rand des Anmeldebildschirms. Um sie zu ändern, tippen Sie auf die Adresse, geben die vollständige HTTP- oder HTTPS-Adresse ein und tippen auf **Speichern**.
3. Geben Sie Ihren Benutzernamen und Ihr Passwort ein.
4. Tippen Sie auf **Anmelden**.

Die App speichert die Sitzung sicher und meldet Sie normalerweise automatisch wieder an, wenn sie erneut geöffnet wird.

> [!NOTE]
> Beim Ändern der Serveradresse wird der aktuelle Benutzer abgemeldet und die Felder für Benutzername und Passwort werden geleert.

Wenn die App keine Verbindung herstellen kann, prüfen Sie die Serveradresse und die Netzwerkverbindung des Telefons. Die Serveradresse muss vom Telefon aus erreichbar sein. Eine Adresse, die nur innerhalb des Firmennetzwerks funktioniert, ist nicht erreichbar, wenn sich das Telefon außerhalb dieses Netzwerks befindet.

## Schicht beginnen

Wählen Sie nach der Anmeldung die Rolle für die aktuelle Schicht aus:

- **Fahrer** bietet die Warteschlange der zugewiesenen Aufträge und die Bedienelemente zur Durchführung von Aufträgen.
- **Disponent** bietet dieselben Auftragsfunktionen. Zusätzlich können Sie Aufträge erstellen, nicht zugewiesene Aufträge zuweisen und aktive Fahrer überwachen.

Die Anzahl der Disponentenplätze ist durch die Systemeinstellungen begrenzt. Wenn alle Plätze belegt sind, wählen Sie **Fahrer** oder warten, bis ein Disponentenplatz verfügbar wird.

Die ausgewählte Rolle bleibt bis zum Ende des Tages aktiv.

Atlas benötigt während einer aktiven Schicht Zugriff auf den Standort. Wenn Android um die Berechtigung bittet, erlauben Sie den genauen oder ungefähren Standort. Für eine präzise Navigation und genaue Live-Kartenaktualisierungen wird der genaue Standort empfohlen. Erlauben Sie außerdem Benachrichtigungen, damit neue Aufträge sichtbar sind, während die App im Hintergrund läuft.

Während eine Schicht aktiv ist, zeigt Android die Benachrichtigung **Atlas-Schicht aktiv** an. In dieser Zeit bleibt Atlas verbunden, empfängt Aufträge und teilt den Live-Standort. Das Schließen der App beendet die Schicht nicht. Verwenden Sie **Abmelden** und füllen Sie die Schichtzusammenfassung aus, wenn Sie fertig sind.

## Hauptbildschirm

Der Hauptbildschirm besteht aus einer Karte und mehreren Bedienelementen.

- **Karte**
  
    Die Karte zeigt Ihre Position, die aktuelle Route und andere aktive Benutzer. Verschieben oder vergrößern Sie die Karte, um einen anderen Bereich anzusehen. Nachdem Sie die Karte verschoben haben, tippen Sie auf die Standortschaltfläche, um zu Ihrer aktuellen Position zurückzukehren.
  
    Andere Benutzer sind mit ihrem Namen beschriftet und entsprechend ihrem aktuellen Status eingefärbt:
  
  - Grün: frei
  - Blau: auf dem Weg zu einer Abholung
  - Orange: besetzt
  - Grau: abwesend

- **Navigation**
  
    Nachdem ein Auftrag gestartet wurde, zeigt der Navigationsbereich an, ob Sie zum Abholort oder zum Ziel fahren. Er zeigt die nächste Anweisung, die verbleibende Entfernung und die geschätzte Zeit an. Tippen Sie im Hochformat auf die Zeile mit Entfernung und Zeit, um alle verbleibenden Routenschritte ein- oder auszublenden.

- **Aufträge**
  
    Der Auftragsbereich zeigt den aktuellen und den nächsten zugewiesenen Auftrag an. Tippen Sie darauf, um die vollständige Warteschlange ein- oder auszuklappen. Ziehen Sie den Bereich nach unten, um ihn zu aktualisieren. Im Querformat bleibt der Bereich kompakt.
  
    Über den Stift neben dem aktuellen Ziel wird die Adresssuche geöffnet. Geben Sie eine Adresse ein und wählen Sie einen der Vorschläge aus, um das Ziel zu aktualisieren. Die bloße Eingabe einer Adresse ohne Auswahl eines Vorschlags speichert die Adresse nicht.

- **Profil**
  
    Tippen Sie oben rechts auf die Profilschaltfläche, um den angemeldeten Benutzer und die ausgewählte Rolle anzuzeigen oder die Schicht mit **Abmelden** zu beenden.

## Arbeitsablauf für Fahrer

### Auftrag empfangen

Wenn ein Auftrag zugewiesen wird, erscheint für 10 Sekunden ein Banner mit Abholort, Ziel und optionaler Notiz. Der Auftrag ist bereits zugewiesen und wird automatisch zur Warteschlange hinzugefügt. Sie müssen nichts tun, um ihn zu behalten.

Tippen Sie nur dann auf **Ablehnen**, wenn der Auftrag storniert werden muss. Wenn Atlas im Hintergrund läuft, enthält die Android-Benachrichtigung dieselben Auftragsdetails und die Aktion **Ablehnen**.

### Nächsten Auftrag starten

Wenn kein aktueller Auftrag vorhanden ist und mindestens ein Auftrag wartet, tippen Sie auf **Nächster Auftrag**. Atlas startet den ersten Auftrag in der Warteschlange und berechnet eine Route vom aktuellen Standort zum Abholort.

Atlas benötigt einmal pro Schicht den Kilometerstand des Fahrzeugs zu Schichtbeginn. Wenn ein verbundenes Fahrzeug Kilometerdaten bereitstellt, werden diese automatisch erfasst. Andernfalls fragt die App beim Start des ersten Auftrags nach dem Kilometerstand. Geben Sie einen nicht negativen Wert ein und tippen Sie auf **Weiter**.

### Fahrgast abholen

Fahren Sie anhand der Route auf der Karte zum Abholort. Wenn sich der Fahrgast im Fahrzeug befindet, tippen Sie auf **Fahrgast abgeholt**. Atlas ändert den Fahrzeugstatus auf besetzt und berechnet die Route zum Ziel.

Wenn der Auftrag kein Ziel hat, wird die Adresssuche automatisch geöffnet. Geben Sie das Ziel ein und wählen Sie einen Vorschlag aus. Das Ziel ist optional, sodass Sie die Suchleiste schließen können, wenn Sie keines eingeben möchten. Das Ziel kann außerdem jederzeit über den Stift im Bereich des aktuellen Auftrags geändert werden.

> [!NOTE]
> Fügen Sie immer ein Ziel hinzu, wenn es bekannt ist. Ohne Ziel kann Atlas weder eine Navigation zum Ziel noch eine routenbasierte Planung für diesen und nachfolgende Aufträge bereitstellen.

### Auftrag abschließen oder stornieren

Tippen Sie am Ziel auf **Auftrag abgeschlossen**. Die Bestätigung zeigt die Gesamtstrecke, die mit dem Fahrgast zurückgelegte Strecke und den berechneten Preis an, sofern die erforderlichen Kilometer- und Preisdaten verfügbar sind. Prüfen Sie die Angaben und tippen Sie auf **Ja**, um den Auftrag abzuschließen. Atlas aktualisiert anschließend die Warteschlange, sodass der nächste wartende Auftrag gestartet werden kann.

Um einen Auftrag zu beenden, ohne ihn abzuschließen, tippen Sie neben den Auftragsbedienelementen auf die Schaltfläche **X** und bestätigen die Stornierung. Stornieren Sie einen Auftrag nur, wenn Sie ihn nicht abschließen können oder keine Zeit dafür haben. Ein stornierter Auftrag wird wieder als nicht zugewiesen geführt und muss von einem Disponenten erneut zugewiesen werden.

## Arbeitsablauf für Disponenten

Disponenten verwenden dieselbe Hauptkarte, Auftragswarteschlange und dieselben Auftragsbedienelemente wie oben beschrieben. Neben den Auftragsbedienelementen werden zwei zusätzliche Schaltflächen angezeigt:

- die Schaltfläche für nicht zugewiesene Aufträge mit einem Zähler für die aktuelle Anzahl nicht zugewiesener Aufträge
- die Schaltfläche **+** zum Erstellen eines neuen Auftrags

### Neuen Auftrag erstellen

1. Tippen Sie auf die Schaltfläche **+**.
2. Tippen Sie auf **Von**, geben Sie die Abholadresse ein und wählen Sie einen Vorschlag aus. Ein Abholort ist erforderlich.
3. Falls bekannt, tippen Sie auf **Nach**, geben das Ziel ein und wählen einen Vorschlag aus.
4. Tippen Sie auf das Datum und die Uhrzeit neben dem Abholort, um den Fälligkeitszeitpunkt des Auftrags zu ändern. Standardmäßig entspricht er dem aktuellen Zeitpunkt.
5. Geben Sie optional eine Notiz für den Fahrer ein. Notizen können bis zu 100 Zeichen enthalten.

Wenn beide Adressen ausgewählt sind, zeichnet Atlas die berechnete Route auf der Karte ein. Ziehen Sie auf einem Telefon im Hochformat den unteren Bereich nach oben, um die vollständige Fahrerliste anzuzeigen.

**Empfohlene Fahrer** werden anhand der Kandidatenberechnung sortiert. Unter **Alle Fahrer** werden die übrigen aktiven Fahrer aufgeführt, sodass die Empfehlung übersteuert werden kann. Wählen Sie einen Fahrer aus und bestätigen Sie mit **Erstellen**, um den Auftrag sofort zu erstellen und zuzuweisen.

Um den Auftrag ohne Fahrer zu speichern, tippen Sie auf **Nicht zugewiesen erstellen**, wählen Fälligkeitsdatum und -uhrzeit aus und bestätigen mit **Erstellen**. Der Auftrag erscheint anschließend in der Liste der nicht zugewiesenen Aufträge.

### Nicht zugewiesene Aufträge verwalten

Tippen Sie auf die Schaltfläche für nicht zugewiesene Aufträge, um die Liste zu öffnen. Aufträge werden nach Fälligkeitsdatum gruppiert und zeigen Abholort, Ziel, Fälligkeitszeit und Notiz an. Ziehen Sie die Liste nach unten, um sie zu aktualisieren.

- Tippen Sie auf eine Auftragskarte, um die Zuweisungsansicht zu öffnen.
- Tippen Sie auf die Papierkorb-Schaltfläche und bestätigen Sie mit **Löschen**, um einen nicht zugewiesenen Auftrag dauerhaft zu löschen.
- Tippen Sie auf den Zurück-Pfeil, um zur Disponentenkarte zurückzukehren.

In der Zuweisungsansicht kann der Abholort nicht geändert werden. Das Ziel und der Fälligkeitszeitpunkt können bearbeitet werden. Tippen Sie auf **Änderungen speichern**, um Änderungen zu übernehmen, ohne den Auftrag zuzuweisen. Alternativ können Sie einen empfohlenen Fahrer oder einen Eintrag unter **Alle Fahrer** auswählen und mit **Zuweisen** bestätigen. Nicht gespeicherte Änderungen an Ziel und Fälligkeitszeitpunkt werden automatisch gespeichert, wenn die Zuweisung bestätigt wird.

> [!CAUTION]
> Das Löschen eines nicht zugewiesenen Auftrags kann nicht rückgängig gemacht werden.

Wenn ein neuer nicht zugewiesener Auftrag eingeht, zeigt Atlas 10 Sekunden lang ein Banner an. Tippen Sie auf **Jetzt zuweisen**, um ihn direkt zu öffnen. Wenn das Banner verschwindet, bleibt der Auftrag in der Liste der nicht zugewiesenen Aufträge verfügbar.

## Fahrzeug und Android Auto verbinden

Verbinden Sie das Telefon über Android Auto mit dem Fahrzeug. Atlas auf Android Auto verwendet die Anmeldung, die Rolle und die aktive Schicht des Telefons. Wenn auf dem Fahrzeugdisplay **Auf dem Telefon fortfahren** angezeigt wird, öffnen Sie die Telefon-App, melden sich an und wählen eine Rolle aus.

Bei der ersten Verbindung wird möglicherweise um Zugriff auf verbundene Bluetooth-Geräte, den Kraftstoffstand und den Kilometerstand gebeten. Stellen Sie das Fahrzeug ab, tippen Sie auf **Fahrzeugdaten zulassen** und genehmigen Sie die angeforderten Berechtigungen. Atlas funktioniert weiterhin, wenn einige Fahrzeugdaten nicht verfügbar sind. Automatische Werte für Kraftstoffstand oder Kilometerstand können dann jedoch fehlen. Das Telefon fragt nach einem manuellen Kilometerstand, wenn dieser benötigt wird.

### Neues Fahrzeug koppeln

Jedes Fahrzeug muss zuerst auf der Fuhrparkseite der Web-UI erstellt werden. Wenn ein nicht registriertes Fahrzeug zum ersten Mal verbunden wird, erhält ein in der Telefon-App angemeldeter Administrator den Dialog **Dieses Fahrzeug koppeln**.

Wählen Sie den passenden Fahrzeugnamen und das passende Kennzeichen aus. Atlas speichert den Fingerabdruck des Fahrzeugs, sodass es bei späteren Verbindungen automatisch erkannt wird. Wenn die Liste nicht geladen werden kann, tippen Sie auf **Erneut versuchen**. Ein Benutzer ohne Administratorrechte kann ein unbekanntes Fahrzeug nicht koppeln.

> [!IMPORTANT]
> Prüfen Sie Fahrzeugname und Kennzeichen sorgfältig. Wenn Sie das falsche Fahrzeug auswählen, werden zukünftige Telemetrie- und Fahrtenbuchdaten dem falschen Fuhrparkeintrag zugeordnet.

## Schicht beenden und abmelden

1. Öffnen Sie das Profil auf dem Hauptbildschirm und tippen Sie auf **Abmelden**.
2. Wenn Atlas den Kilometerstand am Schichtende nicht aus dem verbundenen Fahrzeug auslesen kann, geben Sie ihn manuell ein. Er darf nicht niedriger als der Anfangswert sein.
3. Prüfen Sie die Kilometerleistung, das ausgewählte Fahrzeug und die Schichtzeit. Wenn kein Fahrzeug automatisch erkannt wurde, wählen Sie das für die Schicht verwendete Fahrzeug aus.
4. Geben Sie die gesamten Bareinnahmen der Schicht ein. Geben Sie `0` ein, wenn keine Bareinnahmen erzielt wurden.
5. Aktivieren Sie **Ich bestätige, dass diese Angaben korrekt sind**.
6. Tippen Sie auf **Abmelden**.

Atlas übermittelt die Schicht als neuen Fahrtenbucheintrag und meldet den Benutzer anschließend ab. Tippen Sie vor dem Absenden auf den Zurück-Pfeil, um zur aktiven Schicht zurückzukehren, ohne sich abzumelden.

> [!IMPORTANT]
> Schließen Sie nicht einfach die App, anstatt sich abzumelden. Eine Schicht bleibt aktiv, bis die Schichtzusammenfassung erfolgreich übermittelt wurde.

## Häufige Probleme

- **Die App kann keine Verbindung zum Server herstellen**
  
    Prüfen Sie die vollständige Serveradresse, die Netzwerkverbindung des Telefons und ob ein Fernzugriff erforderlich ist. Melden Sie sich erneut an, wenn die Adresse geändert wurde.

- **Karte, Adresssuche oder Route werden nicht geladen**
  
    Prüfen Sie die Verbindung zum Server und bitten Sie einen Administrator zu bestätigen, dass der Download und die Verarbeitung der Kartendaten abgeschlossen sind.

- **Der Hauptbildschirm wird nach dem Ablehnen des Standortzugriffs nicht geöffnet**
  
    Tippen Sie auf **Standort zulassen**. Wenn Android den Berechtigungsdialog nicht mehr anzeigt, tippen Sie auf **Einstellungen öffnen**, aktivieren die Standortberechtigung für Atlas und kehren zur App zurück.

- **Benachrichtigungen über neue Aufträge werden außerhalb der App nicht angezeigt**
  
    Aktivieren Sie in den Android-Einstellungen Benachrichtigungen für Atlas. Aufträge werden weiterhin zur Warteschlange oder zur Liste der nicht zugewiesenen Aufträge hinzugefügt. Sie können sie finden, indem Sie die App öffnen und aktualisieren.

- **Die Disponentenrolle kann nicht ausgewählt werden**
  
    Alle konfigurierten Disponentenplätze sind derzeit belegt. Warten Sie, bis sich ein Disponent abmeldet, oder wählen Sie die Fahrerrolle.

- **Eine Auftrags- oder Fahrtenbuchaktion schlägt fehl**
  
    Lassen Sie die App geöffnet, vergewissern Sie sich, dass der Server erreichbar ist, und versuchen Sie es erneut. Atlas beendet die Schicht erst, nachdem das Fahrtenbuch erfolgreich übermittelt wurde.
