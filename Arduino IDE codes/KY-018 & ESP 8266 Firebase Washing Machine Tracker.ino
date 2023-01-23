#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Wire.h>
#include <NTPClient.h>
#include <WiFiUdp.h>

// Provide the token generation process info.
#include "addons/TokenHelper.h"
// Provide the RTDB payload printing info and other helper functions.
#include "addons/RTDBHelper.h"

// Insert your network credentials
#define WIFI_SSID "Vodafone-7B4C"//"motorola one action 1229"//"Vodafone-7B4C"
#define WIFI_PASSWORD "hGqTcMMRcd6tQHEC"//"00000000"//"hGqTcMMRcd6tQHEC"

// Insert Firebase project API Key
#define API_KEY "AIzaSyAmKU4QfruJGYFui8cglHPCY3huvCFZ4KQ"

// Insert Authorized Email and Corresponding Password
#define USER_EMAIL "alexruehle57@gmail.com"
#define USER_PASSWORD "123456"

// Insert RTDB URLefine the RTDB URL
#define DATABASE_URL "https://washingmachine-c2ce1-default-rtdb.europe-west1.firebasedatabase.app/"

// Define Firebase objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Variable to save USER UID
String uid;

// Database main path (to be updated in setup with the user UID)
String databasePath;
// Database child nodes
String modePath = "/mode";
String timePath = "/timestamp";
String runtimePath ="/runtime";

// Define 1sec refresh time and 500 as trigger value for led state changes
int refreshTime = 1000;
int triggerValue = 500;
int runtime = 0;

// Define previous & current sensor value & timestamp for comparisation
int previousValue = 0;
int currentValue = 0;
int previousTimestamp = 0;

// Define washing maschine states
String maschineOn = "On";
String maschineOff = "Off";

// Parent Node (to be updated in every loop)
String parentPath;

FirebaseJson json;

// Define NTP Client to get time
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pool.ntp.org");

// Variable to save current epoch time
int timestamp;

String mode;

// Initialize WiFi
void initWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi ..");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print('.');
    delay(3000);
  }
  Serial.println(WiFi.localIP());
  Serial.println();
}

// Function that gets current epoch time
unsigned long getTime() {
  timeClient.update();
  unsigned long now = timeClient.getEpochTime();
  return now;
}

void setup(){
  Serial.begin(115200);
  // Set A0 as analog input Pin
  pinMode(A0, INPUT);

  // Init Wifi and start timeclient
  initWiFi();
  timeClient.begin();

  // Assign the api key (required)
  config.api_key = API_KEY;

  // Assign the user sign in credentials
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  // Assign the RTDB URL (required)
  config.database_url = DATABASE_URL;

  Firebase.reconnectWiFi(true);
  fbdo.setResponseSize(4096);

  // Assign the callback function for the long running token generation task */
  config.token_status_callback = tokenStatusCallback; //see addons/TokenHelper.h

  // Assign the maximum retry of token generation
  config.max_token_generation_retry = 5;

  // Initialize the library with the Firebase authen and config
  Firebase.begin(&config, &auth);

  // Getting the user UID might take a few seconds
  Serial.println("Getting User UID");
  while ((auth.token.uid) == "") {
    Serial.print('.');
    delay(1000);
  }
  // Print user UID
  uid = auth.token.uid.c_str();
  Serial.print("User UID: ");
  Serial.println(uid);

  // Update database path
  databasePath = "/UsersData/" + uid + "/readings";
}

void loop(){

  // Send new readings to database
  if (Firebase.ready()){
    //Get current timestamp
    timestamp = getTime();

    // Set parent path and get current sensor value
    parentPath= databasePath + "/" + String(timestamp);
    currentValue = analogRead(A0);

        //If led gets turned on or is turned on the first run
        if((currentValue <= triggerValue && previousValue > triggerValue) || (currentValue <= triggerValue && previousValue == 0)){
            //Set json for DB event
            json.set(modePath, maschineOn);
            json.set(timePath, String(timestamp));
            json.set(runtimePath, String(0));
            //Save timestamp for runtime calculation in turn off event
            previousTimestamp = timestamp;
            //Upload event and print error if not successful
            Serial.printf("Set json... %s\n", Firebase.RTDB.setJSON(&fbdo, parentPath.c_str(), &json) ? "ok" : fbdo.errorReason().c_str());
        }

        //If led gets turned on or is turned on the first run
        if((previousValue <= triggerValue && currentValue > triggerValue) || (currentValue > triggerValue && previousValue == 0)){
            if(previousTimestamp > 0){
              runtime = timestamp - previousTimestamp;
            }
            json.set(modePath, maschineOff);
            json.set(timePath, String(timestamp));
            json.set(runtimePath, String(runtime));
            Serial.printf("Set json... %s\n", Firebase.RTDB.setJSON(&fbdo, parentPath.c_str(), &json) ? "ok" : fbdo.errorReason().c_str());
        }

    // save value for next loop to compare
    previousValue = currentValue;
    // wait one second
    delay(refreshTime);
  }
}
