# AtlasOS-Wiederherstellung

Wenn Ihr AtlasOS-Gerät schwerwiegende Fehlfunktionen aufweist oder nicht startet, können Sie das Gerät vollständig löschen und neu aufsetzen.

Nehmen Sie dazu einen USB-Stick oder den "Wiederherstellungs-USB-Stick" aus der Originalverpackung.

Wenn der "Wiederherstellungs-USB-Stick" verfügbar ist, überspringen Sie den folgenden Schritt.

Wenn der USB-Stick neu ist, formatieren Sie ihn entweder als FAT32 und entpacken Sie `atlas-recovery-usb.zip` in das Stammverzeichnis des USB-Laufwerks, oder verwenden Sie ein Werkzeug wie Rufus, Raspberry Pi Imager, dd oder Balena Etcher, um `atlas-recovery-usb.img.zst` auf den Stick zu schreiben. Der Download ist in zwei Varianten verfügbar: "Provision" und "Development". "Provision" ist die Standardinstallation, bei der der Flash-Speicher sicher verschlüsselt wird. "Development" ist ein einfacheres Abbild ohne Festplattenverschlüsselung, das hauptsächlich für die Entwicklung vorgesehen ist.

Sobald der USB-Stick vorbereitet ist, stecken Sie ihn in das Gerät und schalten es ein. Das Gerät startet vom USB-Stick und setzt sich neu auf (normalerweise drehen die Lüfter dabei mit voller Geschwindigkeit). ENTFERNEN SIE DEN USB-STICK NICHT!

Nach Abschluss des Vorgangs fährt das Gerät herunter und kann anschließend mit einer frischen Installation wieder eingeschaltet werden. Nach einigen Minuten sollten Sie erneut zum Einrichtungsassistenten gelangen.
