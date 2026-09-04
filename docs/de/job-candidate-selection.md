# Auswahl von Fahrerkandidaten für Aufträge

Die API bietet zwei Möglichkeiten, eine Rangliste von Fahrern zu berechnen:

- `GET /jobs/:id/candidates` verwendet einen vorhandenen, nicht zugewiesenen Auftrag aus der Datenbank.
- `POST /jobs/candidates` verwendet einen Ad-hoc-Auftrag aus dem Request-Body.

Das erste Element des zurückgegebenen Arrays ist der empfohlene Kandidat.

## Endpunktvarianten

### Vorhandener Datenbankauftrag

```http
GET /jobs/:id/candidates
```

Die Auftrags-ID muss eine UUID sein. Der Endpunkt lädt den Auftrag und lehnt die Anfrage ab, wenn der Auftrag:

- nicht vorhanden ist (`404`),
- bereits zugewiesen ist (`409`) oder
- bereits abgeschlossen ist (`409`).

### Ad-hoc-Auftrag

```http
POST /jobs/candidates
Content-Type: application/json

{
  "from": [48.2082, 16.3738],
  "to": [48.1947, 16.3122],
  "dueDate": "2026-08-05T12:00:00.000Z"
}
```

Die Ad-hoc-Anfrage akzeptiert den für die Routenplanung relevanten Teil eines Auftrags:

| Feld | Erforderlich | Bedeutung |
| --- | --- | --- |
| `from` | Ja | Abholort als `[Breitengrad, Längengrad]`. |
| `to` | Nein | Ziel als `[Breitengrad, Längengrad]`. |
| `dueDate` | Nein | Gewünschter Abholzeitpunkt. Standardwert ist der aktuelle Zeitpunkt. |

Der übermittelte Auftrag wird nie in die Datenbank geschrieben. Wird `dueDate` weggelassen, handelt es sich um einen Sofortauftrag.

Der POST-Endpunkt gibt `422` zurück, wenn die übermittelte Struktur oder die Koordinaten ungültig sind.
Beide Endpunktvarianten erfordern eine Authentifizierung und geben dasselbe Schema für Fahrerkandidaten zurück.

## Semantik der Auftragszustände

### Zugewiesen, nicht begonnen

```text
assignedDriverId !== null
startedAt === null
completedAt === null
```

Der Auftrag befindet sich im Rückstand des Fahrers. Der Fahrer hat die Fahrt zu diesem Abholort noch nicht begonnen. Ein Fahrer kann mehrere solcher Aufträge haben.

Nicht begonnene Aufträge bilden keinen unveränderlichen Routenpräfix. Sie werden nach `dueDate` sortiert, sodass ein neu zugewiesener Auftrag vor oder nach ihnen eingefügt werden kann.

### Begonnen, auf dem Weg zur Abholung

```text
startedAt !== null
completedAt === null
telemetry.state === "onTheWay"
```

`startedAt` wird gesetzt, sobald der Fahrer die Fahrt zum Abholort beginnt. Dieser Auftrag ist nun aktiv und bleibt fest am Anfang des Zeitplans.

### Begonnen, Fahrgast abgeholt

```text
startedAt !== null
completedAt === null
telemetry.state === "occupied"
```

Es gibt keinen separaten Zeitstempel für die Abholung. Der Wechsel der Echtzeittelemetrie von `onTheWay` zu `occupied` zeigt an, dass die Abholung erfolgt ist.

### Abgeschlossen

```text
completedAt !== null
```

Der Auftrag wirkt sich nicht mehr auf die Kandidatenberechnung aus.

## Planungsmodell

Der Zeitplan jedes Fahrers besteht aus zwei Teilen:

1. Höchstens einem aktiven Auftrag, erkennbar an `startedAt`.
2. Null oder mehr nicht begonnenen Aufträgen im Rückstand, sortiert nach `dueDate`.

Der aktive Auftrag kann nicht verschoben werden, da der Fahrer bereits mit seiner Ausführung begonnen hat.
Der Zielauftrag wird anhand seines Fälligkeitszeitpunkts in den Rückstand der nicht begonnenen Aufträge eingefügt.

Wenn inkonsistente Daten mehr als einen begonnenen, aber noch nicht abgeschlossenen Auftrag enthalten, gilt der Auftrag mit dem frühesten `startedAt` als aktiv.
Im Normalbetrieb sollte diese Situation nicht auftreten. Die Sortierung sorgt lediglich dafür, dass die Kandidatenberechnung in diesem Fall deterministisch bleibt.

Vorhandene Aufträge mit demselben Fälligkeitszeitpunkt werden vor dem neuen Auftrag einsortiert.

Für den Vergleich gilt daher:

```text
vorhandenes dueDate <= dueDate des Zielauftrags  -> vor dem Zielauftrag
vorhandenes dueDate > dueDate des Zielauftrags   -> nach dem Zielauftrag
```

Damit werden bestehende Zuweisungen bei identischen Fristen berücksichtigt.

### Beispiel: einen früher fälligen Auftrag einfügen

Angenommen, ein freier Fahrer hat bereits folgenden Auftragsrückstand:

```text
11:00 vorhandene Vorbestellung
```

Ein neuer Auftrag verlangt eine Abholung um `10:10`. Der Zeitplan sieht danach wie folgt aus:

```text
10:10 neuer Auftrag
11:00 vorhandene Vorbestellung
```

Der vorhandene Auftrag wird nicht blind zuerst bearbeitet, nur weil er zuerst zugewiesen wurde.
Der Algorithmus plant die Route über Abholort und Ziel des neuen Auftrags und prüft anschließend, ob der Fahrer den Abholort des vorhandenen Auftrags um `11:00` noch rechtzeitig erreichen kann.

Wenn die Fahrt nach der neuen Abholung nur 15 Minuten dauert, bleibt ausreichend Zeit und der Fahrer weiterhin ein guter Kandidat.
Würde sich die vorhandene Abholung verspäten, wird diese vorhergesagte Beeinträchtigung zurückgegeben und bei der Rangfolge berücksichtigt.

## Verarbeitung durch den Endpunkt

Die Anfrage wird wie folgt verarbeitet:

```text
GET /jobs/:id/candidates                 POST /jobs/candidates
    |                                        |
Zielauftrag laden und prüfen              Body prüfen und normalisieren
    |                                        |
    +-- fehlt -------------------> 404        +-- ungültige Struktur -> 422
    +-- zugewiesen --------------> 409        |
    +-- abgeschlossen ------------> 409        |
    |                                        |
    +-------------------- CandidateTarget <--+
                             |
                    Gemeinsamer Berechnungspfad
    |
Alle Fahrer aus trackCache lesen
    |
    +-- keine verfolgten Fahrer ----------> []
    |
Nicht abgeschlossene Aufträge und verfolgte Benutzer parallel laden
    |
Diese Aufträge nach Fahrer-ID gruppieren
    |
Für jeden Fahrer einen vorgeschlagenen Zeitplan erstellen und routen
    |
Kandidaten entfernen, deren Zeitplan nicht vorhergesagt werden kann
    |
Verbleibende Kandidaten sortieren
    |
Besten Kandidaten zuerst zurückgeben
```

Der Endpunkt erfasst `now` genau einmal. Jeder Fahrer wird relativ zum selben Zeitstempel bewertet.

Die Kandidatenrouten werden mit `Promise.all` parallel berechnet.
Die Routenberechnung ist der aufwendigste Teil des Vorgangs. Unabhängige Anfragen parallel auszuführen verhindert daher, dass sich die Latenz des Endpunkts mit der Anzahl der Fahrer multipliziert.

## Datenquellen

### Echtzeit-Cache

`trackCache` liefert:

- die Fahrer-ID über den Schlüssel der Map,
- den aktuellen Breiten- und Längengrad sowie
- den aktuellen Zustand: `free`, `onTheWay`, `occupied` oder `away`.

Nur verfolgte Fahrer werden berücksichtigt. Ohne aktuelle Position wären Route und geschätzte Ankunftszeit nicht zuverlässig.

Die Telemetrie ist maßgeblich für die Phase eines begonnenen Auftrags:

- `onTheWay` bedeutet, dass der Abholort noch angefahren werden muss.
- `occupied` bedeutet, dass die Abholung bereits erfolgt ist.

### Auftragsdatenbank

Die Datenbank liefert:

- den Zielauftrag,
- jeden nicht abgeschlossenen Auftrag, der einem verfolgten Fahrer zugewiesen ist,
- `startedAt` zur Bestimmung des aktiven Auftrags,
- `completedAt` zum Entfernen abgeschlossener Aufträge aus dem Zeitplan,
- `dueDate` zur Sortierung des Rückstands nicht begonnener Aufträge sowie
- die Koordinaten des Abholorts und des optionalen Ziels.

### Benutzerdatenbank

Die gemeinsame Berechnung lädt außerdem `user.id` und `user.name` für alle IDs der verfolgten Fahrer.

## Eignung von Fahrern

### Abwesend

Ein Fahrer mit dem Zustand `away` wird immer ausgeschlossen. Dieser Zustand erklärt ausdrücklich, dass der Fahrer nicht verfügbar ist, unabhängig vom Datenbankzustand.

### Frei

Ein Fahrer ohne begonnenen Auftrag muss den Zustand `free` melden, um berücksichtigt zu werden.

Der Fahrer kann trotzdem nicht begonnene Aufträge im Rückstand haben. Diese Aufträge versetzen ihn nicht in den aktuellen Zustand `onTheWay`; sie werden anhand ihres Fälligkeitszeitpunkts in den vorgeschlagenen Zeitplan eingefügt.

### Auf dem Weg oder besetzt

Ein Fahrer mit dem gemeldeten Zustand `onTheWay` oder `occupied` muss einen nicht abgeschlossenen Auftrag mit einem von null verschiedenen `startedAt` haben.
Andernfalls lässt sich die verbleibende Route nicht zuverlässig rekonstruieren und der Fahrer wird ausgeschlossen.

Wenn umgekehrt ein begonnener Auftrag existiert, die Telemetrie aber `free` meldet, widersprechen sich die Datenquellen hinsichtlich des aktiven Auftrags. Die konservative Entscheidung besteht darin, diesen Kandidaten auszuschließen, bis der Zustand wieder konsistent ist.

## Anforderungen an Ziele

Auftragsziele sind optional. Ein Ziel wird jedoch benötigt, sobald der Algorithmus einen späteren Standort vorhersagen muss.

Ein Kandidat wird ausgeschlossen, wenn:

- der aktive Auftrag kein Ziel hat, obwohl danach ein weiterer Auftrag folgen muss,
- ein Auftrag vor dem Zielauftrag kein Ziel hat,
- der Zielauftrag kein Ziel hat und der Fahrer spätere Aufträge im Rückstand hat oder
- ein späterer Auftrag im Rückstand kein Ziel hat und nicht der letzte Auftrag ist.

Der letzte Auftrag im simulierten Zeitplan benötigt kein Ziel, da nur sein Abholzeitpunkt vorhergesagt werden muss und danach nichts mehr geplant ist.

Diese Regel vermeidet es, einen zukünftigen Standort des Fahrers zu erfinden. Ein fehlendes Ziel ist zulässig, wenn es keine weitere Abholung beeinflussen kann, nicht aber, wenn die Route anschließend fortgesetzt werden muss.

## Routenerstellung

Auftragspunkte verwenden folgende Tupelkonvention:

```text
[Breitengrad, Längengrad]
```

### Aktiver Auftrag auf dem Weg

Wenn `startedAt` vorhanden ist und die Telemetrie `onTheWay` meldet:

```text
aktuelle Position
    -> active job.from
    -> active job.to
    -> nach Fälligkeit sortierter Rückstand und Zielauftrag
```

Der Abholort des aktiven Auftrags bleibt Teil der Route, da sich der Kunde noch nicht im Fahrzeug befindet.

### Aktiver Auftrag mit Fahrgast

Wenn `startedAt` vorhanden ist und die Telemetrie `occupied` meldet:

```text
aktuelle Position
    -> active job.to
    -> nach Fälligkeit sortierter Rückstand und Zielauftrag
```

Der Abholort des aktiven Auftrags wird ausgelassen, da die Telemetrie anzeigt, dass die Abholung bereits erfolgt ist.

### Kein aktiver Auftrag

Für einen freien Fahrer:

```text
aktuelle Position
    -> alle nicht begonnenen Aufträge und der Zielauftrag, nach dueDate sortiert
```

### Vollständige vorgeschlagene Route

Wenn nach dem Zielauftrag weitere Aufträge im Rückstand existieren, wird die Routinganfrage über den Zielauftrag hinaus fortgesetzt:

```text
[aktiver Auftrag, falls vorhanden]
    -> vor dem Zielauftrag fällige Aufträge
    -> Abholort des Zielauftrags
    -> Ziel des Zielauftrags
    -> nach dem Zielauftrag fällige Aufträge
```

Durch die Fortsetzung nach dem Zielauftrag kann der Endpunkt prüfen, ob bei Annahme der neuen Zuweisung weiterhin genug Zeit für bereits geplante spätere Abholungen bleibt.

## Integration der Routing-Engine

`requestRoute` akzeptiert eine geordnete Liste von Punkten und sendet eine einzige Mehrpunktanfrage an die konfigurierte Routing-Engine.

Pro geeignetem Fahrer wird eine Routinganfrage gestellt. Ein schemakonformes Ergebnis ohne mögliche Route schließt diesen Fahrer aus.
Ein Netzwerkfehler oder eine Antwort mit ungültigem Schema lässt hingegen den Endpunkt fehlschlagen, statt ein Infrastrukturproblem stillschweigend in eine leere Kandidatenliste umzuwandeln.

## Zeitsimulation

Die Routing-Engine gibt für jedes Paar aufeinanderfolgender Punkte einen Streckenabschnitt zurück. Nach der Routenberechnung durchläuft der Algorithmus diese Abschnitte in der Reihenfolge des Zeitplans.

Für jeden Streckenabschnitt gilt:

```text
arrivalTime = previousTime + legDrivingTime
```

An jedem Abholort gilt:

```text
pickupTime = max(arrivalTime, job.dueDate)
waitingTime = max(0, job.dueDate - arrivalTime)
lateness = max(0, arrivalTime - job.dueDate)
```

Trifft das Fahrzeug zu früh ein, wartet die Simulation bis zum Fälligkeitszeitpunkt, bevor sie mit dem nächsten Streckenabschnitt fortfährt. Das ist für Vorbestellungen wichtig: Eine Ankunft um 09:30 bei einer Abholung um 10:00 bedeutet nicht, dass die nächste Fahrt bereits um 09:30 beginnen kann.

Für einen Sofortauftrag, dessen Fälligkeitszeitpunkt bereits in der Vergangenheit liegt, lautet die effektive Frist:

```text
targetDeadline = max(now, target.dueDate)
```

Damit wird verhindert, dass das Alter einer überfälligen Anfrage den Vergleich zwischen Fahrern verzerrt. Bei einem Sofortauftrag ist entscheidend, wer ab jetzt am schnellsten eintreffen kann.

### Ankunft am Zielauftrag und tatsächliche Abholung

Die Antwort unterscheidet zwischen:

- `estimatedArrivalAt`: Zeitpunkt, zu dem der Fahrer den Abholort des Zielauftrags erreicht.
- `estimatedPickupAt`: Zeitpunkt, zu dem die Bedienung nach einer etwaigen Wartezeit für eine Vorbestellung beginnen kann.

Bei einer pünktlichen Vorbestellung kann `estimatedArrivalAt` vor `estimatedPickupAt` liegen. Bei einer verspäteten Abholung sind beide Zeitstempel identisch.

### Wartezeit

`waitingDurationSeconds` ist die aufsummierte Wartezeit an geplanten Abholorten bis einschließlich des Zielauftrags.
Wartezeit nach dem Zielauftrag ist nicht enthalten, da dieses Feld den Weg zum Zielauftrag beschreibt.

## Prüfung nachgelagerter Verpflichtungen

Für jeden nach dem Zielauftrag eingeordneten Auftrag im Rückstand wird ein geschätzter Abholzeitpunkt berechnet:

```json
{
  "jobId": "later-job-id",
  "estimatedPickupAt": "2026-08-05T11:00:00.000Z",
  "lateBySeconds": 0
}
```

Diese Schätzungen werden in `followingJobs` zurückgegeben.

`maximumFollowingLatenessSeconds` ist die größte vorhergesagte Verspätung unter diesen Aufträgen. Der Wert ist null, wenn es keine späteren Aufträge gibt oder alle späteren Abholungen pünktlich bleiben.

Statt Verzögerungen zu summieren, wird das Maximum verwendet, damit eine einzelne stark beeinträchtigte bestehende Verpflichtung sichtbar bleibt und nicht durch mehrere pünktliche Aufträge relativiert wird.

## Kandidatenmetriken

### Route zum Zielauftrag

Obwohl die Routinganfrage über spätere Aufträge fortgesetzt werden kann, umfassen `routeDurationSeconds` und `routeDistanceKilometers` nur die Streckenabschnitte bis zum Abholort des Zielauftrags.

Sie beantworten damit die Frage:

> Wie viel Fahrt ist erforderlich, bevor der neue Kunde erreicht wird?

### Letzte Anfahrt

`approachDurationSeconds` und `approachDistanceKilometers` beschreiben nur den letzten Streckenabschnitt zum Abholort des Zielauftrags.

Beispiel:

```text
previous job.to -> target job.from
```

oder, wenn dem Zielauftrag nichts vorausgeht:

```text
current position -> target job.from
```

Die letzte Anfahrt dient als Maß für die Leerfahrt, sobald alle Fristen eingehalten werden.

### Verspätung beim Zielauftrag

```text
lateBySeconds = max(0, estimatedArrivalAt - targetDeadline)
```

Null bedeutet, dass der Zielauftrag pünktlich bedient werden kann. Ein positiver Wert ist die vorhergesagte Verzögerung.

## Exakter Algorithmus für die Rangfolge

Die Kandidaten werden der Reihe nach nach folgenden Regeln sortiert:

1. Ein Kandidat, bei dem alle später zugewiesenen Aufträge pünktlich bleiben, wird vor einem Kandidaten eingeordnet, der einen späteren Auftrag verspätet.

2. Wenn beide Kandidaten spätere Aufträge beeinträchtigen, wird der Kandidat mit dem kleineren Wert für `maximumFollowingLatenessSeconds` zuerst eingeordnet.

3. Eine pünktliche Abholung des Zielauftrags wird vor einer verspäteten Abholung eingeordnet.

4. Wenn beide Abholungen des Zielauftrags verspätet sind, wird der Kandidat mit dem früheren `estimatedPickupAt` zuerst eingeordnet.

5. Wenn beide Abholungen des Zielauftrags pünktlich sind, wird die kürzere letzte Anfahrt bevorzugt.

6. Sind die letzten Anfahrten gleich lang, wird der Kandidat mit dem früheren `estimatedArrivalAt` zuerst eingeordnet.

## Herleitung der Rangfolge

Jeder zurückgegebene Kandidat enthält einen `rankingTrace`. Da die Rangfolge relativ ist, vergleicht diese Herleitung einen Kandidaten mit einem benachbarten Kandidaten in der endgültig sortierten Liste:

- Rang 1 wird mit Rang 2 verglichen und erklärt, warum der Kandidat davor liegt.
- Jeder weitere Rang wird mit dem unmittelbar davor liegenden Kandidaten verglichen und erklärt, warum er dahinter liegt.
- Gibt es nur einen geeigneten Kandidaten, findet kein Vergleich statt und er wird als einziger geeigneter Fahrer beschrieben.

Die Herleitung wird von derselben Vergleichsfunktion erzeugt, die auch das Array sortiert.

Jeder Schritt der Herleitung entspricht einer vom Comparator ausgewerteten Regel.
Die Regeln erscheinen in Richtlinienreihenfolge und besitzen aus Sicht des aktuellen Kandidaten ein `outcome` von `better`, `equal` oder `worse`.
Die Auswertung endet genau wie die Sortierung bei der ersten Regel, deren Ergebnis nicht gleich ist.
`decisiveCriterion` stellt diesen letzten Schritt direkt bereit, sodass eine Benutzeroberfläche ihn nicht aus dem Schrittarray ableiten muss.

Zwei pünktliche Kandidaten für eine Vorbestellung könnten beispielsweise Folgendes erzeugen:

```json
{
  "rank": 1,
  "summaryCode": "rankedAhead",
  "summaryValues": {
    "rank": 1,
    "comparedToDriverId": "free-driver",
    "comparedToDriverName": "Free Driver",
    "decisiveCriterion": "approachDuration"
  },
  "summary": "Ranked ahead Free Driver. Final approach takes 50 seconds, shorter than Free Driver at 300 seconds.",
  "comparedTo": {
    "driverId": "free-driver",
    "driverName": "Free Driver",
    "relation": "ahead"
  },
  "decisiveCriterion": "approachDuration",
  "steps": [
    {
      "criterion": "followingJobDisruption",
      "outcome": "equal",
      "code": "followingJobDisruption.equal",
      "values": {
        "candidate": false,
        "comparedTo": false,
        "unit": "boolean"
      },
      "message": "Like Free Driver, keeps all following jobs on time."
    },
    {
      "criterion": "maximumFollowingLateness",
      "outcome": "equal",
      "code": "maximumFollowingLateness.equal",
      "values": {
        "candidate": 0,
        "comparedTo": 0,
        "unit": "seconds"
      },
      "message": "Worst following-job delay matches Free Driver at 0 seconds."
    },
    {
      "criterion": "targetLateness",
      "outcome": "equal",
      "code": "targetLateness.equal",
      "values": {
        "candidate": false,
        "comparedTo": false,
        "unit": "boolean"
      },
      "message": "Like Free Driver, can pick up the target job on time."
    },
    {
      "criterion": "approachDuration",
      "outcome": "better",
      "code": "approachDuration.better",
      "values": {
        "candidate": 50,
        "comparedTo": 300,
        "unit": "seconds"
      },
      "message": "Final approach takes 50 seconds, shorter than Free Driver at 300 seconds."
    }
  ]
}
```

`summaryCode` wählt die lokalisierte Zusammenfassungsvorlage aus und `summaryValues` enthält deren unverarbeitete Interpolationswerte.
Jeder Schritt stellt ebenso einen stabilen `code` und unverarbeitete `values` bereit.
Ein Frontend kann daher Übersetzungsschlüssel wie `ranking.approachDuration.better` verwenden, ohne englische Texte analysieren oder Zahlen aus Sätzen extrahieren zu müssen.

`summary` und `message` jedes Schritts bleiben direkt anzeigbare englische Fallback-Texte.
Die strukturierten Felder bilden den sprachunabhängigen Vertrag für UI-Logik, Symbole, Formatierung und Lokalisierung.

Unverarbeitete Schrittwerte besitzen eine explizite Einheit:

- `boolean` wird von `followingJobDisruption` und `targetLateness` verwendet.
  `true` bedeutet, dass der Kandidat mindestens einen nachfolgenden Auftrag beeinträchtigt beziehungsweise beim Zielauftrag verspätet ist.
- `seconds` wird für die maximale Verspätung nachfolgender Aufträge und die Dauer der Anfahrt verwendet.
- `dateTime` ist eine ISO-8601-Zeichenfolge für geschätzte Abhol- und Ankunftszeiten.

Wenn alle Kriterien der Rangfolge gleich sind, wird `decisiveCriterion` weggelassen, `comparedTo.relation` lautet `tied` und die Zusammenfassung erklärt, dass die Listenreihenfolge nur der Darstellung dient.
Der Algorithmus erfindet keinen geschäftlichen Grund für einen ansonsten willkürlichen Gleichstand.

### Warum spätere Verpflichtungen Vorrang haben

Spätere Aufträge im Rückstand sind bereits zugewiesen und können nicht ohne Weiteres neu vergeben werden.
Ein neuer Auftrag sollte nicht dazu führen, dass eine bereits zugesagte Abholung verspätet erfolgt, wenn ein anderer Fahrer die neue Arbeit ohne diese Beeinträchtigung übernehmen kann.

Das bedeutet, dass ein Fahrer, der den neuen Kunden etwas später erreicht, vor einem Fahrer eingeordnet werden kann, der den neuen Kunden zwar pünktlich erreichen würde, aber eine bestehende Vorbestellung stark verzögert.

### Sofortaufträge

Wenn keine zukünftigen Verpflichtungen beeinträchtigt werden, reduziert sich die Rangfolge bei Sofortaufträgen auf die frühestmögliche Abholung.
Alle Kandidaten werden vom selben `now` aus gemessen. Daher entspricht die Sortierung verspäteter Sofortabholungen nach Zeitstempel einer Sortierung nach der simulierten Fahrtdauer bis zum Kunden.

### Vorbestellungen

Bei Vorbestellungen schützt der Algorithmus zunächst spätere Verpflichtungen und bevorzugt anschließend Fahrer, die den neuen Abholort pünktlich erreichen. Unter den pünktlichen Kandidaten wird die letzte Leerfahrt minimiert.

Dadurch kann ein besetzter Fahrer ein guter Kandidat sein, wenn seine aktuelle Fahrt in der Nähe des neuen Abholorts endet und der verbleibende Zeitplan weiterhin eingehalten werden kann.

## Antwortschema

```json
[
  {
    "driverId": "driver-123",
    "driverName": "Jane Driver",
    "state": "onTheWay",
    "latitude": 48.2082,
    "longitude": 16.3738,
    "currentJobId": "active-job-id",
    "precedingJobIds": ["active-job-id", "earlier-job-id"],
    "followingJobs": [
      {
        "jobId": "later-job-id",
        "estimatedPickupAt": "2026-08-05T11:00:00.000Z",
        "lateBySeconds": 0
      }
    ],
    "estimatedArrivalAt": "2026-08-05T10:07:00.000Z",
    "estimatedPickupAt": "2026-08-05T10:10:00.000Z",
    "routeDurationSeconds": 900,
    "waitingDurationSeconds": 180,
    "routeDistanceKilometers": 9,
    "approachDurationSeconds": 180,
    "approachDistanceKilometers": 2,
    "lateBySeconds": 0,
    "maximumFollowingLatenessSeconds": 0,
    "rankingTrace": {
      "rank": 1,
      "summaryCode": "onlyEligibleDriver",
      "summaryValues": {
        "rank": 1
      },
      "summary": "Only eligible driver.",
      "steps": []
    }
  }
]
```

`currentJobId` ist nur vorhanden, wenn ein begonnener Auftrag aktiv ist.

| Feld | Bedeutung |
| --- | --- |
| `driverId` | Für die Zuweisung zu verwendender Fahrer. |
| `driverName` | Aktueller Wert von `user.name` für den Fahrer. |
| `state` | Echtzeitzustand zur Interpretation des aktiven Auftrags. |
| `latitude`, `longitude` | Zuletzt zwischengespeicherte Position des Fahrers. |
| `currentJobId` | Begonnener Auftrag, der gegebenenfalls fest am Anfang der Route steht. |
| `precedingJobIds` | Vor dem Zielauftrag geplante vorhandene Aufträge. Umfasst den aktiven Auftrag. |
| `followingJobs` | Geschätzte Abholzeitpunkte für vorhandene Aufträge nach dem Zielauftrag. |
| `estimatedArrivalAt` | Zeitpunkt, zu dem der Fahrer den Abholort des Zielauftrags erreicht. |
| `estimatedPickupAt` | Zeitpunkt, zu dem die Abholung des Zielauftrags nach dem Warten beginnen kann. |
| `routeDurationSeconds` | Fahrzeit bis einschließlich des Abholorts des Zielauftrags. |
| `waitingDurationSeconds` | Wartezeit vor und am Zielauftrag. |
| `routeDistanceKilometers` | Fahrstrecke bis einschließlich des Abholorts des Zielauftrags. |
| `approachDurationSeconds` | Fahrzeit des letzten Streckenabschnitts zum Zielauftrag. |
| `approachDistanceKilometers` | Entfernung des letzten Streckenabschnitts zum Zielauftrag. |
| `lateBySeconds` | Verspätung bei der Abholung des Zielauftrags. |
| `maximumFollowingLatenessSeconds` | Größte vorhergesagte Verspätung unter späteren vorhandenen Aufträgen. |
| `rankingTrace` | Strukturierte Erklärung der Position dieses Kandidaten in der zurückgegebenen Liste. |

### Felder der Herleitung der Rangfolge

| Feld | Bedeutung |
| --- | --- |
| `rank` | Einsbasierte Position im Antwortarray. |
| `summaryCode` | Stabiler Lokalisierungsschlüssel: `onlyEligibleDriver`, `rankedAhead`, `rankedBehind` oder `tied`. |
| `summaryValues` | Unverarbeitete Werte zum Erstellen der lokalisierten Zusammenfassung. |
| `summary` | Kurze, direkt anzeigbare englische Erklärung. |
| `comparedTo` | Benachbarter Fahrer, mit dem diese Position erklärt wird. Wird bei nur einem Kandidaten weggelassen. |
| `comparedTo.relation` | Gibt an, ob dieser Kandidat relativ zu diesem Fahrer `ahead`, `behind` oder `tied` ist. |
| `decisiveCriterion` | Erste Rangfolgeregel mit ungleichem Ergebnis. Wird bei vollständigem Gleichstand oder einem einzigen Kandidaten weggelassen. |
| `steps` | Tatsächlich ausgewertete Rangfolgeregeln bis einschließlich der entscheidenden Regel, in ihrer Reihenfolge. |
| `steps[].criterion` | Stabiler, maschinenlesbarer Bezeichner der Rangfolgeregel. |
| `steps[].outcome` | Aus Sicht dieses Kandidaten `better`, `equal` oder `worse`. |
| `steps[].code` | Stabiler Lokalisierungsschlüssel der Form `<criterion>.<outcome>`. |
| `steps[].values.candidate` | Unverarbeiteter Wert des aktuellen Kandidaten für diese Regel. |
| `steps[].values.comparedTo` | Unverarbeiteter Wert des Vergleichsfahrers für diese Regel. |
| `steps[].values.unit` | Werttyp beziehungsweise Einheit: `boolean`, `seconds` oder `dateTime`. |
| `steps[].message` | Direkt anzeigbare englische Erklärung dieses Vergleichsschritts. |

Der Endpunkt stellt konkrete Zeitplanmetriken statt einer undurchsichtigen Punktzahl bereit, damit ein Disponent die Reihenfolge verstehen und überprüfen kann.

## Komplexität und Leistung

Für `D` verfolgte Fahrer und `J` nicht abgeschlossene, zugewiesene Aufträge gilt:

- Eine Abfrage lädt den Zielauftrag.
- Eine Abfrage lädt alle relevanten nicht abgeschlossenen Aufträge.
- Eine Abfrage lädt die Namen der verfolgten Fahrer. Sie läuft parallel zur Abfrage der nicht abgeschlossenen Aufträge.
- Das Gruppieren kostet `O(J)`.
- Das Sortieren des Rückstands jedes Fahrers kostet `O(Jd log Jd)`, wobei `Jd` der kleine Rückstand dieses Fahrers ist.
- Die Rangfolgebildung kostet `O(D log D)`.
- Pro geeignetem Fahrer wird höchstens eine Routinganfrage gestellt.

Die zugrunde liegende Annahme ist, dass jeder Fahrer nur einen kleinen Auftragsrückstand hat.

## Bewusste Vereinfachungen

Der Algorithmus berücksichtigt derzeit nicht:

- Fairness zwischen Fahrern oder Verteilung der Arbeitslast.
- Geografische Abdeckung durch den Fuhrpark.
- Kraftstoffstand oder andere Fahrzeugtelemetrie als Standort und Zustand.
- Zeitaufschläge für Abholung und Absetzen.
- Historische Zuverlässigkeit von Zielangaben.
- Einen festen Unsicherheitspuffer.
- Wahrscheinliche Änderungen des Kundenziels.
- Verkehrskorrekturen über die Routing-Engine hinaus.

Diese Faktoren wurden weggelassen, um die anfängliche Richtlinie deterministisch und einfach zu halten.

## Bekannte Einschränkungen

### Absolute gegenüber zusätzlicher nachgelagerter Verspätung

Der Endpunkt erstellt die Rangfolge anhand der vorhergesagten nachgelagerten Verspätung nach dem Einfügen des Zielauftrags.
Er berechnet den ursprünglichen Zeitplan des Fahrers ohne den Zielauftrag nicht separat.
Dadurch kann ein Fahrer, dessen vorhandener Zeitplan bereits verspätet war, benachteiligt werden, selbst wenn der neue Auftrag nur wenig zusätzliche Verzögerung verursacht.

Für eine genaue Berechnung der zusätzlichen Beeinträchtigung wäre pro Fahrer eine weitere Basisroute oder ein zwischengespeicherter Zeitplan erforderlich. -> Ressourcenaufwand und Komplexität

### Unsicherheit des Ziels

Wenn ein Ziel vorhanden ist, wird es als korrekt angenommen. Fahrgäste können ihr Ziel dennoch ändern oder früher aussteigen. Derzeit gibt es keinen Unsicherheitspuffer.

### Keine Bedienzeit

Simuliert werden nur Fahrzeit und fälligkeitsbedingte Wartezeit. Zeit für das Auffinden eines Kunden, das Verladen von Gepäck, die Unterstützung von Fahrgästen oder den Abschluss der Zahlung ist nicht enthalten.

### Inkonsistente Zustände werden ausgeschlossen

Kurze Synchronisationsfenster können dazu führen, dass ein begonnener Datenbankauftrag und der Telemetriezustand `free` einander widersprechen. Der Endpunkt schließt diesen Fahrer aus, statt zu raten, ob die Abholung erfolgt ist.
