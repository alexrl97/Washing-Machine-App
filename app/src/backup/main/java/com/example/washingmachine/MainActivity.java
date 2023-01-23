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

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    DatabaseReference databaseReferenceReadings;
    DatabaseReference databaseReferenceModes;

    // Initialize Firebase Auth
    private FirebaseAuth mAuth;
    private int startTs = 0;
    private int runtimeSelectionPrevious = 0;
    private int previousRemainingSeconds = 0;
    private String runtimeMeasured = "0";
    private boolean runCountdown = false;
    private boolean countdownStopped = false;
    private boolean blockNextCountdownAdjust = false;
    private int remaningSeconds;
    private int runTimeDif;
    private CountDownTimer cTimer;
    private int latestStartTs;

    private TextView state;
    private TextView timer;
    private TextView measuredRuntime;
    private Spinner dropdown;
    private Button saveRuntime;
    private Button addMode;
    private Button deleteMode;
    private EditText userMode;

    private String redColor = "#db1304";
    private String greenColor = "#02bd05";

    private NotificationCompat.Builder builder;
    private NotificationManagerCompat managerCompat;

    private ArrayList<Mode> modesList = new ArrayList<>();
    private ArrayList<String> modeStrings = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        measuredRuntime = findViewById(R.id.textView3);
        state = findViewById(R.id.textView2);
        timer = findViewById(R.id.textView);
        //get the spinner from the xml.
        dropdown = findViewById(R.id.spinner1);
        saveRuntime = findViewById(R.id.button);
        addMode = findViewById(R.id.button2);
        deleteMode = findViewById(R.id.button3);
        userMode = findViewById(R.id.plain_text_input);


        databaseReferenceModes = FirebaseDatabase.getInstance().getReference("UsersData/6Z6TF0cmdkYWm8b7vFLjbreuRag1/modes");
        databaseReferenceReadings = FirebaseDatabase.getInstance().getReference("UsersData/6Z6TF0cmdkYWm8b7vFLjbreuRag1/readings");

        builder = new NotificationCompat.Builder(this, "State")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true).setSilent(true);

        managerCompat= NotificationManagerCompat.from(this);


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


        databaseReferenceModes.get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    Log.e("firebase", "Error getting data", task.getException());
                }
                else{
                    task.getResult().getChildren().forEach(dataSnapshot -> {

                        Log.d("modes", dataSnapshot.getKey());
                        modesList.add(new Mode(dataSnapshot.getKey().toString()
                                , dataSnapshot.child("name").getValue().toString()
                                , Integer.parseInt(dataSnapshot.child("runtime").getValue().toString())));
                    });

                    updateDropdown(true, false);

                    setTimer(modesList.get(0).getRuntime(), false);
                }
            }
        });



        //https://www.youtube.com/watch?v=Mare_muqF1c
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                int runtimeSelection = modesList.get(modeStrings.indexOf(dropdown.getSelectedItem().toString())).getRuntime();
                int runTimeDifInSeconds = runtimeSelectionPrevious - runtimeSelection;

                remaningSeconds = remaningSeconds - runTimeDifInSeconds;

                if(!saveRuntime.isEnabled()) {
                    if (runtimeSelection == 0) {
                        measuredRuntime.setText("No wash time measured yet\nIt will be measured in the first wash");
                    } else {
                        measuredRuntime.setText("");
                    }
                }

                if(runCountdown){
                    cTimer.cancel();
                    if(previousRemainingSeconds <= 0 && remaningSeconds >= 0){
                        setTimerToRemainingTimeOfCurrentSelection();
                    }
                    else if(remaningSeconds < 0){
                        setTimer(0,true);
                    }
                    else{
                        startCountdown(remaningSeconds);
                    }
                }
                else{
                    setTimer(runtimeSelection, false);
                }

                runtimeSelectionPrevious = runtimeSelection;
                previousRemainingSeconds = remaningSeconds;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        databaseReferenceReadings.get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    Log.e("firebase", "Error getting data", task.getException());
                }
                else {

                    ArrayList<DataSnapshot> list = new ArrayList<DataSnapshot>();

                    task.getResult().getChildren().forEach(dataSnapshot -> {
                                list.add(dataSnapshot);
                            }
                    );
                    startTs = Integer.parseInt(list.get(list.size() -1).getKey());

                    if(list.get(list.size() -1).child("mode").getValue().toString().equals("Off")){
                        state.setText("Washing Machine: Off");
                        state.setTextColor(Color.parseColor(redColor));
                    }
                    else {
                        latestStartTs = Integer.parseInt(list.get(list.size() -1).getKey());
                        setTimerToRemainingTimeOfCurrentSelection();
                        state.setText("Washing Machine: On");
                        state.setTextColor(Color.parseColor(greenColor));
                    }
                }
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("State","State", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }


        databaseReferenceReadings.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                if(snapshot.child("mode").getValue().toString().equals("Off") && startTs != 0 && startTs < Integer.parseInt(snapshot.getKey())){
                    state.setText("Washing Machine: Off");
                    state.setTextColor(Color.parseColor(redColor));
                    runCountdown = false;
                    builder.setContentTitle("Washing machine done!");
                    builder.setContentText("00:00:00");
                    managerCompat.notify(1, builder.build());

                    runtimeMeasured = snapshot.child("runtime").getValue().toString();
                    measuredRuntime.setText("Measured wash time: " + getRuntimeString(Integer.parseInt(runtimeMeasured)));
                    saveRuntime.setEnabled(true);
                    saveRuntime.setBackgroundColor(Color.parseColor(greenColor));

                }
                if(snapshot.child("mode").getValue().toString().equals("On") &&  startTs != 0 && startTs < Integer.parseInt(snapshot.getKey())){
                    state.setText("Washing Machine: On");
                    state.setTextColor(Color.parseColor(greenColor));
                    runCountdown = true;

                    measuredRuntime.setText("");

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

        saveRuntime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int index = modeStrings.indexOf(dropdown.getSelectedItem().toString());

                modesList.get(index).setRuntime(Integer.parseInt(runtimeMeasured));
                databaseReferenceModes.child(modesList.get(index).getKey()).child("runtime").setValue(runtimeMeasured);
                timer.setText(getRuntimeString(Integer.parseInt(runtimeMeasured)));
            }
        });

        addMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userInput = userMode.getText().toString();
                boolean nameGiven = false;

                if(modeStrings.contains(userInput))
                    nameGiven = true;

                if(userInput.length() > 0 && !userInput.contains("/") && !nameGiven){
                    String key = databaseReferenceModes.push().getKey();
                    databaseReferenceModes.child(key).child("name").setValue(userInput);
                    databaseReferenceModes.child(key).child("runtime").setValue("0");
                    modesList.add(new Mode(key, userInput, 0));
                    updateDropdown(false, false);
                    Toast.makeText(MainActivity.this, "Added", Toast.LENGTH_SHORT).show();
                    userMode.setText("");
                }
                else if(nameGiven){
                    Toast.makeText(MainActivity.this, "Mode already stored", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(MainActivity.this, "Mode cannot be empty or contain '/'", Toast.LENGTH_SHORT).show();
                }

            }
        });

        deleteMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(dropdown.getSelectedItem().toString() == modeStrings.get(0)){
                    Toast.makeText(MainActivity.this, "Default Mode can't be deleted", Toast.LENGTH_SHORT).show();
                }
                else {

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

    public String getRuntimeString(int seconds){
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void startCountdown(int startValue) {
        cTimer = new CountDownTimer(startValue*1000, 1000) {
            public boolean currentRun = true;
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

    void cancelTimer() {
        if(cTimer!=null) {
            cTimer.cancel();
        }
    }

    public void setTimerToRemainingTimeOfCurrentSelection(){
        int delay = 170;
        int runtimedif = ((int) ((System.currentTimeMillis()/1000) - latestStartTs) - delay);
        int selectedModeRuntime = modesList.get(modeStrings.indexOf(dropdown.getSelectedItem().toString())).getRuntime();
        int index = modeStrings.indexOf(dropdown.getSelectedItem().toString());

        if(runtimedif < selectedModeRuntime && runtimedif > 0) {
            runCountdown = true;
            startCountdown((selectedModeRuntime - runtimedif));
        }
    }

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
        ArrayAdapter adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);

        if(!initialFill && !deletion){
            dropdown.setSelection(modeStrings.size() -1);
        }
    }
}