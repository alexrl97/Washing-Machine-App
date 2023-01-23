package com.example.washingmachine;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    //Firebase Authentifikator & Referenzobjekte
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReferenceReadings;
    private DatabaseReference databaseReferenceModes;

    //Listen für das Dropdown Menü
    private ArrayList<Mode> modesList = new ArrayList<>();
    private ArrayList<String> modeStrings = new ArrayList<>();

    //Benötigte Int Werte
    private int latestEventTs = 0;
    private int latestStartTs;
    private int runtimeMeasured = 0;
    private int remaningSeconds;
    private int runtimeSelectionPrevious = 0;

    //Countdowntimer Entitäten
    private CountDownTimer cTimer;
    private boolean runCountdown = false;
    private String TIME_SERVER = "pool.ntp.org";
    private NTPUDPClient timeClient = new NTPUDPClient();

    //GUI Elemente
    private TextView state;
    private TextView timer;
    private TextView measuredRuntime;
    private Spinner dropdown;
    private Button saveRuntime;
    private Button addMode;
    private Button deleteMode;
    private EditText userMode;

    //Konstante Strings & Farben
    private final String redColor = "#db1304";
    private final String greenColor = "#02bd05";
    private final String washingMachineOn = "Washing Machine: On";
    private final String washingMachineOff = "Washing Machine: Off";

    //Notification Management
    private NotificationCompat.Builder builder;
    private NotificationManagerCompat managerCompat;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialisierung der GUI Elemente
        measuredRuntime = findViewById(R.id.textView3);
        state = findViewById(R.id.textView2);
        timer = findViewById(R.id.textView);
        dropdown = findViewById(R.id.spinner1);
        saveRuntime = findViewById(R.id.button);
        addMode = findViewById(R.id.button2);
        deleteMode = findViewById(R.id.button3);
        userMode = findViewById(R.id.plain_text_input);

        //Authentifizierungs & Datenreferenzobjecte für Firebase
        mAuth = FirebaseAuth.getInstance();
        databaseReferenceModes = FirebaseDatabase.getInstance().getReference("UsersData/6Z6TF0cmdkYWm8b7vFLjbreuRag1/modes");
        databaseReferenceReadings = FirebaseDatabase.getInstance().getReference("UsersData/6Z6TF0cmdkYWm8b7vFLjbreuRag1/readings");

        //Vorbereitung der Notification-Ausgabe
        builder = new NotificationCompat.Builder(this, "State")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true).setSilent(true);
        managerCompat= NotificationManagerCompat.from(this);

        //Login in Firebase DB
        mAuth.signInWithEmailAndPassword("alexruehle57@gmail.com", "123456")
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                        }
                    }
                });

        //Speichern der aktuellen Waschgänge und befüllen des Dropdown-Menüs
        databaseReferenceModes.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e("firebase", "Error getting data", task.getException());
            }
            else{
                task.getResult().getChildren().forEach(dataSnapshot -> {

                    Log.d("modes", dataSnapshot.getKey());
                    modesList.add(new Mode(dataSnapshot.getKey()
                            , Objects.requireNonNull(dataSnapshot.child("name").getValue()).toString()
                            , Integer.parseInt(Objects.requireNonNull(dataSnapshot.child("runtime").getValue()).toString())));
                });

                updateDropdown(true, false);

                setTimer(modesList.get(0).getRuntime(), false);
            }
        });



        //Regelt die Darstellung abhängig vom Status der Waschmaschine und der verbleibenden Restzeit
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                //Berechnung der verbleibenden Restzeit im aktuellen Waschgang
                int runtimeSelection = modesList.get(modeStrings.indexOf(dropdown.getSelectedItem().toString())).getRuntime();
                int runTimeDifInSeconds = runtimeSelectionPrevious - runtimeSelection;
                remaningSeconds = remaningSeconds - runTimeDifInSeconds;

                //Anzeige für Waschgänge ohne gespeicherte Runtim
                if(!saveRuntime.isEnabled()) {
                    if (runtimeSelection == 0) {
                        measuredRuntime.setText("No wash time measured yet\nIt will be measured in the first wash");
                    } else {
                        measuredRuntime.setText("");
                    }
                }

                //Setzt den Countdowntimer auf die verbleibende Restzeit
                if(runCountdown){
                    cTimer.cancel();
                    if(remaningSeconds < 0){
                        setTimer(0,true);
                    }
                    else{
                        setTimerToRemainingTimeOfCurrentSelection();
                    }
                }
                else{
                    setTimer(runtimeSelection, false);
                }

                runtimeSelectionPrevious = runtimeSelection;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Setzt den aktuellen Status in der App auf das letzte Event vom ESP 8266
        databaseReferenceReadings.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e("firebase", "Error getting data", task.getException());
            }
            else {

                ArrayList<DataSnapshot> list = new ArrayList<>();

                task.getResult().getChildren().forEach(list::add);
                latestEventTs = Integer.parseInt(Objects.requireNonNull(list.get(list.size() - 1).getKey()));

                //Waschmachine ist beim App-Start ausgeschaltet
                if(Objects.requireNonNull(list.get(list.size() - 1).child("mode").getValue()).toString().equals("Off")){
                    state.setText(washingMachineOff);
                    state.setTextColor(Color.parseColor(redColor));
                }
                //Waschmachine ist beim App-Start eingeschaltet
                else {
                    latestStartTs = Integer.parseInt(Objects.requireNonNull(list.get(list.size() - 1).getKey()));
                    setTimerToRemainingTimeOfCurrentSelection();
                    state.setText(washingMachineOn);
                    state.setTextColor(Color.parseColor(greenColor));
                }
            }
        });

        //Vorbereitung der Notification
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("State","State", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        //Regelt die Anzeige in der App wenn der ESP 8266 ein neues Status Event der Waschmaschine in die Datenbank lädt
        databaseReferenceReadings.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                //Waschmaschine wird im laufenden App betrieb ausgeschaltet
                if(Objects.requireNonNull(snapshot.child("mode").getValue()).toString().equals("Off") && latestEventTs != 0 && latestEventTs < Integer.parseInt(snapshot.getKey())){
                    state.setText(washingMachineOff);
                    state.setTextColor(Color.parseColor(redColor));
                    runCountdown = false;
                    builder.setContentTitle("Washing machine done!");
                    builder.setContentText("00:00:00");
                    managerCompat.notify(1, builder.build());
                    runtimeMeasured = Integer.parseInt(snapshot.child("runtime").getValue().toString());
                    measuredRuntime.setText("Measured wash time: " + getRuntimeString(runtimeMeasured));
                    saveRuntime.setEnabled(true);
                    saveRuntime.setBackgroundColor(Color.parseColor(greenColor));

                }
                //Waschmaschine wird im laufenden App betrieb eingeschaltet
                if(Objects.requireNonNull(snapshot.child("mode").getValue()).toString().equals("On") &&  latestEventTs != 0 && latestEventTs < Integer.parseInt(snapshot.getKey())){
                    state.setText(washingMachineOn);
                    state.setTextColor(Color.parseColor(greenColor));
                    runCountdown = true;
                    measuredRuntime.setText("");
                    latestStartTs = Integer.parseInt(snapshot.child("timestamp").getValue().toString());
                    startCountdown(modesList.get(modeStrings.indexOf(dropdown.getSelectedItem().toString())).getRuntime());
                    builder.setContentTitle("Washing machine started!");
                    managerCompat.notify(1, builder.build());
                }

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        //Regelt das Überschreiben der Runtime für den ausgeqwählten Waschgang aus dem Dropdown Menü in der Datenbank
        saveRuntime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int index = modeStrings.indexOf(dropdown.getSelectedItem().toString());

                modesList.get(index).setRuntime(runtimeMeasured);
                databaseReferenceModes.child(modesList.get(index).getKey()).child("runtime").setValue(String.valueOf(runtimeMeasured));
                timer.setText(getRuntimeString(runtimeMeasured));
            }
        });

        //Fügt einen neuen Waschmodus in der Datenbank hinzu, wenn der Button gedrückt wird und ein gültiger Name für den Waschgang angegeben wurde
        addMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userInput = userMode.getText().toString();
                boolean nameGiven = false;

                //Darf nicht leer sein
                if(modeStrings.contains(userInput))
                    nameGiven = true;

                //Darf keinen Schrägstrich enthalten
                if(userInput.length() > 0 && !userInput.contains("/") && !nameGiven){
                    String key = databaseReferenceModes.push().getKey();
                    databaseReferenceModes.child(key).child("name").setValue(userInput);
                    databaseReferenceModes.child(key).child("runtime").setValue("0");
                    modesList.add(new Mode(key, userInput, 0));
                    updateDropdown(false, false);
                    Toast.makeText(MainActivity.this, "Added", Toast.LENGTH_SHORT).show();
                    userMode.setText("");
                }
                //Den Waschgang darf es noch nicht geben
                else if(nameGiven){
                    Toast.makeText(MainActivity.this, "Mode already stored", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(MainActivity.this, "Mode cannot be empty or contain '/'", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //Regelt das Löschen eines Waschgangs in der Datenbank
        deleteMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Default Mode darf nicht gelöscht werden
                if(dropdown.getSelectedItem().toString() == modeStrings.get(0)){
                    Toast.makeText(MainActivity.this, "Default Mode can't be deleted", Toast.LENGTH_SHORT).show();
                }
                else {
                //Erfordert eine Bestätigung zur Löschung eines Waschgangs
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Deletion Confirmation")
                            .setMessage("Do you really want to delete mode '" + dropdown.getSelectedItem().toString() + "' ?")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {
                                    int index = modeStrings.indexOf(dropdown.getSelectedItem().toString());
                                    databaseReferenceModes.child(modesList.get(index).getKey()).removeValue();
                                    modesList.remove(index);
                                    modeStrings.remove(index);
                                    updateDropdown(false, true);
                                    Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();

                                    if(runCountdown){
                                        cTimer.cancel();
                                    }

                                }
                            })
                            .setNegativeButton(android.R.string.no, null).show();
                }
            }
        });
    }

    //Setzt den Timer in der App und optional auch in der Notification
    public void setTimer(int seconds, boolean setNotificationTimer){

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        timer.setText(timeString);
        if(setNotificationTimer) {
            builder.setContentText(timeString);
            managerCompat.notify(1, builder.build());
        }
    }

    //Gibt einen Zeitwert in Sekunden im Format HH:MM:SS als String für den Timer zurück
    public String getRuntimeString(int seconds){
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    //Startet den Countdown mit einem Zeitwert in Sekunden
    public void startCountdown(int startValue) {
        cTimer = new CountDownTimer(startValue*1000, 1000) {
            public void onTick(long millisUntilFinished) {
                    remaningSeconds = (int) (millisUntilFinished / 1000);
                    setTimer(remaningSeconds, true);
                if (!runCountdown){
                    setTimer(0, true);
                    cancelTimer();
                }
            }
            public void onFinish() {
            }

        };
        cTimer.start();
    }

    //Stoppt den Timer
    void cancelTimer() {
        if(cTimer!=null) {
            cTimer.cancel();
        }
    }

    //Berechnet die verbleibende Restzeit eines Waschgangs anhand des aktuellen timestamps und dem timestamp des letzten Waschmaschinenstarts
    public void setTimerToRemainingTimeOfCurrentSelection(){

        new Thread(() -> {
            try {
                InetAddress inetAddress = InetAddress.getByName(TIME_SERVER);
                TimeInfo timeInfo = timeClient.getTime(inetAddress);
                int returnTime = (int) (timeInfo.getMessage().getTransmitTimeStamp().getTime()/1000);
                int runtimedif = returnTime - latestStartTs;
                int selectedModeRuntime = modesList.get(modeStrings.indexOf(dropdown.getSelectedItem().toString())).getRuntime();

                if(runtimedif < selectedModeRuntime && runtimedif > 0) {
                    runCountdown = true;
                    MainActivity.this.runOnUiThread(()-> startCountdown((selectedModeRuntime - runtimedif)));
                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    //Synchronisiert die Darstellung der Waschgänge im Dropdown Menü mit den Einträgen in der Datenbank
    public void updateDropdown(boolean initialFill, boolean deletion){
        if(initialFill) {
            modesList.forEach((mode -> {
                modeStrings.add(mode.getName());
            }));
            runtimeSelectionPrevious = modesList.get(0).getRuntime();
        }
        else if(!deletion){
            modeStrings.add(modesList.get(modesList.size() -1).getName());
        }

        String[] items = modeStrings.toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);

        if(!initialFill && !deletion){
            dropdown.setSelection(modeStrings.size() -1);
        }
    }
}