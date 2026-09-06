# So empfiehlt Atlas Fahrer

Wenn ein Disponent einen Auftrag erstellt oder zuweist, vergleicht Atlas die verfügbaren Fahrer und zeigt sie in einer empfohlenen Reihenfolge an. Der erste Fahrer ist anhand der aktuellen Zeitplan- und Routeninformationen am besten geeignet.

> [!NOTE]
> Die Empfehlung ist eine Planungshilfe und keine Einschränkung. Ein Disponent kann einen anderen verfügbaren Fahrer auswählen, wenn Ortskenntnisse oder betriebliche Anforderungen dafür sprechen.

## Was Atlas berücksichtigt

Atlas verwendet die Informationen, die bestimmen, ob ein Fahrer die neue Abholung durchführen kann, ohne bestehende Aufträge zu beeinträchtigen:

- den aktuellen Standort und die Verfügbarkeit des Fahrers
- den Fortschritt des aktiven Auftrags, falls vorhanden
- bereits zugewiesene Aufträge und deren Abholzeiten
- Abholort, Ziel und Fälligkeitszeitpunkt des neuen Auftrags
- Fahrzeit und Entfernung zwischen den erforderlichen Stopps

Nur Fahrer mit einem aktuellen Standort werden berücksichtigt. Dadurch verhindert Atlas, dass eine Ankunftszeit auf Basis eines unbekannten Ausgangspunkts angezeigt wird.

## So wird der vorgeschlagene Zeitplan erstellt

Ein aktiver Auftrag bleibt immer an erster Stelle, da der Fahrer ihn bereits begonnen hat. Noch nicht begonnene Aufträge werden nach ihrem Fälligkeitszeitpunkt sortiert. Atlas fügt den neuen Auftrag an der passenden Stelle in diese Warteschlange ein, statt ihn einfach ans Ende zu setzen.

Anschließend simuliert Atlas die vollständige Route. Wenn ein Fahrer eine Vorbestellung zu früh erreicht, wartet die Simulation bis zum geplanten Abholzeitpunkt, bevor sie fortfährt. Sie prüft auch Aufträge nach dem neuen Auftrag. So wird verhindert, dass eine scheinbar gute Zuweisung zu einer verspäteten Abholung eines späteren Kunden führt.

## Prioritäten der Rangfolge

Atlas wendet die folgenden Prioritäten der Reihe nach an:

1. Bereits zugewiesene Aufträge schützen. Ein Fahrer, bei dem spätere Abholungen pünktlich bleiben, wird einem Fahrer vorgezogen, der sie verspäten würde.
2. Wenn jede Möglichkeit spätere Aufträge verzögert, wird der Fahrer mit der geringsten maximalen Verzögerung bevorzugt.
3. Ein Fahrer, der den neuen Abholort pünktlich erreichen kann, wird bevorzugt.
4. Wenn alle Fahrer verspätet wären, wird die frühestmögliche Abholung bevorzugt.
5. Wenn die neue Abholung pünktlich ist und spätere Aufträge nicht gefährdet sind, wird die kürzeste letzte Leerfahrt zum Kunden bevorzugt.
6. Sind die letzten Anfahrten gleich lang, wird die frühere Ankunft bevorzugt.

Bei einem Sofortauftrag ohne beeinträchtigte spätere Verpflichtungen empfiehlt Atlas normalerweise den Fahrer, der den Kunden zuerst erreichen kann. Bei einer Vorbestellung schützt Atlas zunächst spätere Verpflichtungen und minimiert anschließend die Leerfahrt unter den Fahrern, die pünktlich eintreffen können.

## Warum ein Fahrer möglicherweise nicht erscheint

Ein Fahrer wird weggelassen, wenn Atlas keine zuverlässige Vorhersage treffen kann. Häufige Gründe sind:

- der Fahrer ist als abwesend markiert
- es ist kein aktueller Standort verfügbar
- der gemeldete Fahrerstatus stimmt nicht mit dem aktiven Auftrag überein
- ein erforderliches Ziel fehlt
- der Routingdienst kann keine Route finden

Ein Ziel ist besonders wichtig, wenn ein weiterer Auftrag folgt. Ohne Ziel weiß Atlas nicht, wo sich der Fahrer zu Beginn der nächsten Fahrt befinden wird. Ein fehlendes Ziel kann nur beim letzten Auftrag eines Zeitplans unproblematisch sein.

## Empfehlung verstehen

In der Kandidatenliste steht der empfohlene Fahrer an erster Stelle. Die angezeigten Einzelheiten können Folgendes umfassen:

- wann der Fahrer voraussichtlich den Abholort erreicht
- wann die Abholung nach einer möglichen Wartezeit bei einer Vorbestellung tatsächlich beginnen kann
- Entfernung und Fahrzeit bis zum neuen Kunden
- ob die Annahme des Auftrags voraussichtlich spätere Abholungen verzögert

Atlas erklärt außerdem, welcher Faktor für die Reihenfolge entscheidend war. Dadurch erkennt der Disponent, ob eine Empfehlung auf dem Schutz einer späteren Buchung, dem Vermeiden einer Verspätung oder einer kürzeren letzten Leerfahrt beruht.

## Zuverlässige Empfehlungen erhalten

Für ein möglichst zuverlässiges Ergebnis:

- geben Sie Abholort und Ziel an, sobald sie bekannt sind
- legen Sie das korrekte Fälligkeitsdatum und die korrekte Uhrzeit fest, insbesondere bei Vorbestellungen
- lassen Sie den Standortzugriff des Fahrers während der Schicht aktiviert
- stellen Sie sicher, dass aktive Aufträge in der App zum richtigen Zeitpunkt gestartet, abgeholt und abgeschlossen werden
- prüfen Sie, warum Kandidaten fehlen, statt bei einer leeren Liste davon auszugehen, dass keine Fahrer vorhanden sind

## Aktuelle Einschränkungen

Die Empfehlung konzentriert sich auf Zeitpläne und Routen. Sie berücksichtigt derzeit keine gleichmäßige Arbeitsverteilung, geografische Flottenabdeckung, Kraftstoffstände, zusätzliche Zeit für Gepäck oder Bezahlung und unerwartete Zieländerungen. Verkehr fließt nur ein, soweit der Routingdienst ihn unterstützt. Bereits vorhandene Verspätungen können das Ergebnis beeinflussen, selbst wenn der neue Auftrag kaum zusätzliche Verzögerung verursacht.
