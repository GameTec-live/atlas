# Atlas - Erste Schritte

1. Packen Sie Ihr Atlas-Gerät aus

    Lieferumfang:
    
    - Atlas-Gerät
    - Netzteil
    - Wiederherstellungs-USB-Stick

    Bewahren Sie den Wiederherstellungs-USB-Stick an einem sicheren Ort auf. Sie benötigen ihn, um Ihr Gerät bei schwerwiegenden Problemen vollständig neu aufzusetzen.

2. Schließen Sie Strom und Ethernet an
   
   Verbinden Sie Ihr Atlas-Gerät mit dem Netzwerk und der Stromversorgung.

   Warten Sie. Das Gerät benötigt einige Minuten, bis es vollständig gestartet und einsatzbereit ist.

3. Rufen Sie [https://atlas.local](https://atlas.local) in einem Webbrowser auf, um den Einrichtungsassistenten zu starten

    Alternativ können Sie den Einrichtungsassistenten starten, indem Sie die IP-Adresse des Geräts in einem Webbrowser öffnen oder einen Monitor, eine Maus und eine Tastatur an das Gerät anschließen und in der Konsole `ENTER` drücken.

## Der Einrichtungsassistent

Der Einrichtungsassistent führt Sie in zehn einfachen Schritten durch die Einrichtung Ihres Atlas-Geräts und des Atlas-Systems. Diese Schritte werden im Folgenden ausführlich beschrieben.

### Willkommen

Herzlichen Glückwunsch: Sie haben Ihr Atlas-Gerät erfolgreich in Betrieb genommen und eine Verbindung hergestellt. Großartige Dinge liegen vor Ihnen.

### Einstellungen

Wählen Sie Ihre bevorzugte Sprache und Ihr bevorzugtes Design. Diese Einstellungen werden in Ihrem Browser gespeichert und können jederzeit über die obere Navigationsleiste geändert werden.

### Administratorkonto erstellen

Um Atlas zu verwalten und abzusichern, müssen Sie ein Administratorkonto erstellen.

Geben Sie eine E-Mail-Adresse ein (keine Sorge, Sie erhalten keine E-Mails), wählen Sie einen Benutzernamen und einen Namen und geben Sie ein sicheres Passwort ein.

Nachdem Sie auf "Erstellen" geklickt haben, ist Ihr Administratorkonto einsatzbereit.

### Zeitzone

Einige Funktionen, etwa das Ablaufen einer Rolle (wann ein Disponent wieder verfügbar wird usw.), sind zeitabhängig. Stellen Sie hier die korrekte lokale Zeitzone ein.

### Netzwerkverbindung

Betreiben Sie eine erweiterte Installation? Haben Sie besondere Netzwerkanforderungen? Konfigurieren Sie hier statische Adressen und weitere Einstellungen für die Netzwerkschnittstelle.

Die meisten Benutzer müssen in diesem Menü nichts ändern.

### Fernzugriff

Atlas ist dafür ausgelegt und in der Lage, vollständig offline zu arbeiten. Möglicherweise möchten Sie Atlas jedoch auch außerhalb Ihres Netzwerks verfügbar machen.

AtlasOS bietet verschiedene Einstellungen, um einen solchen Fernzugriff zu ermöglichen.

Wenn Sie Atlas über Tailscale oder Cloudflare Tunnels verfügbar machen möchten, startet AtlasOS diese Dienste automatisch, sobald gültige Anmeldedaten vorliegen.

> [!NOTE]  
> Bei Cloudflare Tunnels müssen Sie den Tunnel auf https://localhost richten.

Wenn Sie stattdessen einfach Portweiterleitung auf Ihrem Router aktivieren oder mehrere externe Adressen auf Ihre Installation verweisen lassen, vergessen Sie nicht, diese Adressen den zusätzlichen externen Ursprüngen hinzuzufügen. Andernfalls kann die Authentifizierung fehlschlagen.

### Allgemeine Einstellungen

Konfigurieren Sie hier das Verhalten und die allgemeinen Einstellungen. Passen Sie das auf Mobilgeräten angezeigte Logo an, legen Sie die maximale Anzahl gleichzeitig verfügbarer Disponenten sowie den Preis pro Kilometer fest.

Die Standardsprache wird nur für Routenführung und Navigation verwendet, und auch nur dann, wenn keine andere Sprache angegeben wurde. Normalerweise wird die Sprache des Endgeräts verwendet (Telefon, Web-UI).

### App verbinden

Laden Sie die Atlas-App auf alle Mobilgeräte herunter, die Fahrer und Disponenten verwenden werden. Geben Sie auf dem Anmeldebildschirm entweder die URL des Atlas-Geräts manuell ein oder scannen Sie den Kopplungs-QR-Code, um sie automatisch festzulegen.

### Benutzer

Erstellen Sie hier Konten für zusätzliche Administratoren sowie für Ihre Fahrer und Disponenten. Sie melden sich mit Benutzername und Passwort an.

### Kartendaten

Laden Sie einen oder mehrere Datensätze herunter, die für Routenführung, Suche und Karten verwendet werden. Beachten Sie, dass Download und Verarbeitung längere Zeit dauern können. Sie können die Einrichtung abschließen, die Seite verlassen oder später zurückkehren. Der Download wird im Hintergrund verarbeitet; Atlas ist jedoch erst vollständig funktionsfähig, nachdem mindestens ein Datensatz heruntergeladen und initialisiert wurde.

Das war's! Ihre Atlas-Installation ist einsatzbereit. Weitere Einzelheiten zur Verwendung finden Sie im [Handbuch für die Web-UI](./manual-web.md) und im [Handbuch für die mobile App](./manual-mobile.md).
