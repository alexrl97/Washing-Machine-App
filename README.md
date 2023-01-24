# Washing-Machine-App

Das Repository kann als Android Studio Projekt gecloned werden. Im Ordner "Arduino IDE Codes"sind die Codes für die verwendeten Mikrocontroller STM32 F401RE, ESP 8266 und den KY-018 Photoresistor hinterlegt. 

Dokumentation: https://drive.google.com/file/d/1gONTJvteFkwNaOlXPgGe35AOB9r1h0ST/view?usp=share_link

Video Demonstration: https://mediathek.htw-berlin.de/video/Video-Demonstration-Waschmachinen-Android-App/faeb9ae60998136d09585bd321a4ea8f

Projektbeschreibung: 

Das Projekt bildet eine funktionale Erweiterung für meine Waschmaschine mittels einer Android App. 
Die Waschmaschine verfügt über keine Zeitanzeige, sondern nur über eine LED an der sich ablesen 
lässt, ob die Waschmaschine an oder aus ist. Ungewiss ist beim Waschen stets, wie lange die 
Maschine noch laufen wird oder wie lange die einzelnen Waschgänge dauern.

Dieses Problem werde ich beheben, indem ich den KY-018 Photoresistor mit dem ESP 8266 verbinde.
Wenn die Waschmachine an oder ausgeht wird das vom Photoresistor anhand der Helligkeit der LED 
erkannt und vom ESP 8266, welcher mit meinem häuslichen Wlan verbunden ist, wird ein Event in 
die Datenbank hochgeladen. Zudem enthalten die Events bei denen die Maschine ausgeschaltet 
wurde eine Zeitmessung über die Laufzeit des Waschgangs.

In der App ist dann eine Statusanzeige über den aktuellen Zustand sowie ein Countdowntimer über 
die verbleibende Restzeit abhängig vom eingestellten Waschgang vorhanden. Die gemessenen Zeiten 
lassen sich für die vorhandenen oder hinzugefügten Waschgänge speichern, aktualisieren und 
löschen. Zudem soll die App unabhängig agieren, sodass man die App z.B. auch eine Stunde nach 
dem Start der Waschmaschine anschalten kann und sie dennoch die korrekte Restzeit über den 
gewählten Waschgang anzeigt. Neue Waschgänge lassen sich nach einer initialen Zeitmessung, die 
automatisch vorgenommen wird, ebenfalls verfolgen.

Zum Testen der App in der Entwicklung verwendete ich den Mikrocontroller STM32 F401RE, welcher 
mit einer roten LED verbunden ist. Die LED lässt sich über den User Button des Boards nach belieben 
ein- und ausschalten. Als rote LED verwende ich das rote Licht einer Arduino Ampel, weil diese 
bereits einen Widerstand verbaut hat.

