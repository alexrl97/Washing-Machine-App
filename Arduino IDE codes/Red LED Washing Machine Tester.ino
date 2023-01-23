/*

This code is used with the STM 32 F401RE Board with a connected Red LED
to the LED_PIN to simulate the baviour of the washing machine in my project

*/


//Set A0 PIN for LED
#define LED_PIN A0
//Set PC13 as user button pin
#define BUTTON_PIN PC13

//Set button unpressed and led turned off
byte lastButtonState = LOW;
byte ledState = LOW;

void setup() {
  //Setup for led&buttons pins
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT);
}
void loop() {
  //Changes Led on/off with every button press
  byte buttonState = digitalRead(BUTTON_PIN);
  if (buttonState != lastButtonState) {
    lastButtonState = buttonState;
    if (buttonState == LOW) {
      ledState = (ledState == HIGH) ? LOW: HIGH;
      digitalWrite(LED_PIN, ledState);
    }
  }
}